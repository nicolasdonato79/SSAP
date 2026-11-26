package com.stockautopartes.sync;

import com.stockautopartes.importacion.CargaStock;
import com.stockautopartes.importacion.FilaStock;
import com.stockautopartes.importacion.Importacion;
import com.stockautopartes.importacion.ImportacionRepository;
import com.stockautopartes.importacion.ProcesadorFilas;
import com.stockautopartes.importacion.ResultadoLectura;
import com.stockautopartes.inventario.ItemStockForm;
import com.stockautopartes.inventario.ItemStockService;
import com.stockautopartes.inventario.StockWriter;
import com.stockautopartes.matching.MatchingService;
import com.stockautopartes.solicitud.EstadoSolicitud;
import com.stockautopartes.solicitud.SolicitudException;
import com.stockautopartes.solicitud.SolicitudService;
import com.stockautopartes.sync.SyncDtos.Operacion;
import com.stockautopartes.sync.SyncDtos.PushRespuesta;
import com.stockautopartes.sync.SyncDtos.ResultadoOperacion;
import com.stockautopartes.usuario.Usuario;
import com.stockautopartes.usuario.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Subida: aplica, en orden, los cambios que el equipo hizo sin conexión.
 * <p>
 * Reglas:
 * <ul>
 *   <li><b>Reintentar no duplica:</b> cada operación trae un opId. La primera vez se guarda en
 *       sync_operacion junto con el resultado; si vuelve a llegar se contesta lo guardado.</li>
 *   <li><b>Cada operación en su propia transacción:</b> si una falla, las demás siguen.</li>
 *   <li><b>Stock (solo lo edita su dueño):</b> gana el último cambio. Si el ítem cambió desde que el equipo lo vio
 *       (otro equipo del mismo vendedor), se compara la hora del cambio, corregida por la diferencia de reloj.</li>
 *   <li><b>Pedidos:</b> gana el que llega primero al servidor. La operación trae la versión del pedido que vio el
 *       usuario; si el pedido cambió mientras tanto no se aplica (CONFLICTO) y el equipo baja el estado actual.
 *       Así nadie acepta una oferta que no vio. La máquina de estados la sigue validando Solicitud.</li>
 * </ul>
 */
@Service
public class SyncPushService {

    private static final Logger log = LoggerFactory.getLogger(SyncPushService.class);

    static final int MAX_OPERACIONES = 200;
    static final int MAX_FILAS_EXCEL = 20_000;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final UsuarioRepository usuarios;
    private final ItemStockService itemStock;
    private final SolicitudService solicitudes;
    private final CargaStock carga;
    private final ImportacionRepository importaciones;
    private final MatchingService matching;
    private final Clock clock;

    public SyncPushService(JdbcTemplate jdbc, PlatformTransactionManager txManager, UsuarioRepository usuarios,
                           ItemStockService itemStock, SolicitudService solicitudes, CargaStock carga,
                           ImportacionRepository importaciones, MatchingService matching, Clock clock) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(txManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.usuarios = usuarios;
        this.itemStock = itemStock;
        this.solicitudes = solicitudes;
        this.carga = carga;
        this.importaciones = importaciones;
        this.matching = matching;
        this.clock = clock;
    }

    /** La operación no se puede aplicar y reintentarla no cambia nada. */
    static class Rechazo extends RuntimeException {
        final Resultado resultado;

        Rechazo(Resultado resultado, String mensaje) {
            super(mensaje);
            this.resultado = resultado;
        }
    }

    /** Lo que devuelve cada operación aplicada. {@code despues}: lo que va después del commit (matching). */
    private record Hecho(Resultado resultado, String mensaje, Long version, Runnable despues) {
        Hecho(Resultado resultado, String mensaje, Long version) {
            this(resultado, mensaje, version, null);
        }
    }

    public PushRespuesta subir(DispositivoService.Sesion sesion, Instant relojCliente, List<Operacion> operaciones) {
        Instant ahora = clock.instant();
        // Diferencia de reloj del equipo: hechaEn + desfase = hora del servidor
        Duration desfase = relojCliente == null ? Duration.ZERO : Duration.between(relojCliente, ahora);
        List<ResultadoOperacion> resultados = new ArrayList<>();
        for (Operacion op : operaciones) {
            resultados.add(procesar(sesion, op, desfase, ahora));
        }
        return new PushRespuesta(clock.instant(), resultados);
    }

    private ResultadoOperacion procesar(DispositivoService.Sesion sesion, Operacion op, Duration desfase, Instant ahora) {
        if (op.opId() == null) {
            return new ResultadoOperacion(null, Resultado.RECHAZADA.name(), "Falta el opId.", op.uuid(), null, false);
        }
        TipoOperacion tipo = TipoOperacion.de(op.tipo()).orElse(null);
        if (tipo == null) {
            return guardarRechazo(sesion, op, "Tipo de operación desconocido: " + op.tipo());
        }
        Instant hechaEn = op.hechaEn() == null ? ahora : op.hechaEn().plus(desfase);
        if (hechaEn.isAfter(ahora)) hechaEn = ahora;
        final Instant cuando = hechaEn;

        try {
            Object[] salida = tx.execute(status -> {
                int nueva = jdbc.update("""
                        INSERT INTO sync_operacion (op_id, usuario_id, dispositivo_id, tipo, entidad_uuid, resultado, hecha_en)
                        VALUES (?, ?, ?, ?, ?, 'EN_PROCESO', ?)
                        ON CONFLICT (op_id) DO NOTHING
                        """, op.opId(), sesion.usuarioId(), sesion.dispositivoId(), tipo.name(), op.uuid(),
                        Timestamp.from(cuando));
                if (nueva == 0) {
                    return new Object[]{guardada(sesion, op)};
                }
                Hecho h = ejecutar(sesion, tipo, op, cuando);
                jdbc.update("UPDATE sync_operacion SET resultado = ?, mensaje = ?, version = ? WHERE op_id = ?",
                        h.resultado.name(), recortar(h.mensaje), h.version, op.opId());
                return new Object[]{new ResultadoOperacion(op.opId(), h.resultado.name(), h.mensaje, op.uuid(),
                        h.version, false), h.despues};
            });
            if (salida.length > 1 && salida[1] != null) {
                try {
                    ((Runnable) salida[1]).run();
                } catch (Exception e) {
                    log.warn("Falló lo posterior a la operación {}", op.opId(), e);
                }
            }
            return (ResultadoOperacion) salida[0];
        } catch (Rechazo r) {
            return guardarRechazo(sesion, op, r.getMessage(), r.resultado);
        } catch (SolicitudException e) {
            return guardarRechazo(sesion, op, e.getMessage());
        } catch (ResponseStatusException e) {
            return guardarRechazo(sesion, op, "No se encontró (o no es tuyo).");
        } catch (com.stockautopartes.usuario.UsuarioNoRegistradoException e) {
            return guardarRechazo(sesion, op, "Completá tu perfil (teléfono, estado y ciudad) antes de publicar o responder.");
        } catch (Exception e) {
            // Falla nuestra (base caída, bug): no se registra, así el equipo la reintenta.
            log.error("Falló la operación {} ({}) del usuario {}", op.opId(), op.tipo(), sesion.usuarioId(), e);
            return new ResultadoOperacion(op.opId(), Resultado.ERROR.name(),
                    "No se pudo aplicar ahora; se va a reintentar.", op.uuid(), null, false);
        }
    }

    private ResultadoOperacion guardarRechazo(DispositivoService.Sesion sesion, Operacion op, String mensaje) {
        return guardarRechazo(sesion, op, mensaje, Resultado.RECHAZADA);
    }

    /** Guarda el rechazo en una transacción aparte (la de la operación se deshizo). */
    private ResultadoOperacion guardarRechazo(DispositivoService.Sesion sesion, Operacion op, String mensaje,
                                              Resultado resultado) {
        if (op.opId() == null) {
            return new ResultadoOperacion(null, resultado.name(), mensaje, op.uuid(), null, false);
        }
        return tx.execute(status -> {
            Long version = versionActual(op);
            int nueva = jdbc.update("""
                    INSERT INTO sync_operacion (op_id, usuario_id, dispositivo_id, tipo, entidad_uuid, resultado, mensaje, version, hecha_en)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (op_id) DO NOTHING
                    """, op.opId(), sesion.usuarioId(), sesion.dispositivoId(), recortar(op.tipo(), 40), op.uuid(),
                    resultado.name(), recortar(mensaje), version,
                    op.hechaEn() == null ? null : Timestamp.from(op.hechaEn()));
            if (nueva == 0) return guardada(sesion, op);
            return new ResultadoOperacion(op.opId(), resultado.name(), mensaje, op.uuid(), version, false);
        });
    }

    /** Resultado de una operación que ya había llegado. */
    private ResultadoOperacion guardada(DispositivoService.Sesion sesion, Operacion op) {
        return jdbc.query("""
                SELECT resultado, mensaje, version, usuario_id FROM sync_operacion WHERE op_id = ?
                """, rs -> {
            if (!rs.next() || rs.getLong("usuario_id") != sesion.usuarioId()) {
                return new ResultadoOperacion(op.opId(), Resultado.RECHAZADA.name(), "opId repetido.", op.uuid(),
                        null, false);
            }
            return new ResultadoOperacion(op.opId(), rs.getString("resultado"), rs.getString("mensaje"), op.uuid(),
                    (Long) rs.getObject("version"), true);
        }, op.opId());
    }

    // ---------- Cada tipo de operación ----------

    private Hecho ejecutar(DispositivoService.Sesion sesion, TipoOperacion tipo, Operacion op, Instant hechaEn) {
        Map<String, Object> datos = op.datos() == null ? Map.of() : op.datos();
        return switch (tipo) {
            case ITEM_GUARDAR -> itemGuardar(sesion, op, datos, hechaEn);
            case ITEM_BORRAR -> itemBorrar(sesion, op, hechaEn);
            case STOCK_REEMPLAZAR -> stockReemplazar(sesion, datos, hechaEn);
            default -> pedido(sesion, tipo, op, datos);
        };
    }

    /** Fila de item_stock que importa para decidir. */
    private record EstadoItem(long id, long usuarioId, long version, Instant actualizadoEn) {
    }

    private EstadoItem item(UUID uuid) {
        if (uuid == null) throw new Rechazo(Resultado.RECHAZADA, "Falta el uuid del repuesto.");
        List<EstadoItem> filas = jdbc.query(
                "SELECT id, usuario_id, version, actualizado_en FROM item_stock WHERE uuid = ? FOR UPDATE",
                (rs, i) -> new EstadoItem(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getTimestamp(4).toInstant()),
                uuid);
        return filas.isEmpty() ? null : filas.get(0);
    }

    private boolean borradoAntes(UUID uuid, long usuarioId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM sync_borrado WHERE uuid = ? AND usuario_id = ?",
                Integer.class, uuid, usuarioId);
        return n != null && n > 0;
    }

    private Hecho itemGuardar(DispositivoService.Sesion sesion, Operacion op, Map<String, Object> datos, Instant hechaEn) {
        Usuario yo = conPerfil(sesion);
        ItemStockForm form = new ItemStockForm();
        form.setCodigo(texto(datos, "codigo"));
        form.setDescripcion(texto(datos, "descripcion"));
        form.setVehiculo(texto(datos, "vehiculo"));
        form.setCantidad(texto(datos, "cantidad"));
        form.setPrecio(texto(datos, "precio"));
        form.setMarca(texto(datos, "marcaId"));

        EstadoItem actual = item(op.uuid());
        if (actual == null) {
            if (borradoAntes(op.uuid(), sesion.usuarioId())) {
                throw new Rechazo(Resultado.CONFLICTO, "Ese repuesto se borró desde otro equipo; no se volvió a crear.");
            }
            ItemStockService.Resultado r = itemStock.crear(yo, form, op.uuid());
            if (!r.ok()) throw new Rechazo(Resultado.RECHAZADA, String.join(" ", r.errores()));
            return new Hecho(Resultado.APLICADA, null, versionDeItem(op.uuid()));
        }
        if (actual.usuarioId != sesion.usuarioId()) throw new Rechazo(Resultado.RECHAZADA, "No se encontró (o no es tuyo).");

        Resultado resultado = Resultado.APLICADA;
        String mensaje = null;
        if (op.baseVersion() == null || op.baseVersion() != actual.version) {
            // Cambió desde que el equipo lo vio: gana el último cambio
            if (!hechaEn.isAfter(actual.actualizadoEn)) {
                throw new Rechazo(Resultado.CONFLICTO,
                        "Había un cambio más nuevo de este repuesto (hecho desde otro equipo); quedó ese.");
            }
            resultado = Resultado.APLICADA_CON_CONFLICTO;
            mensaje = "Este cambio reemplazó uno anterior hecho desde otro equipo.";
        }
        ItemStockService.Resultado r = itemStock.modificar(yo, actual.id, form);
        if (!r.ok()) throw new Rechazo(Resultado.RECHAZADA, String.join(" ", r.errores()));
        return new Hecho(resultado, mensaje, versionDeItem(op.uuid()));
    }

    private Hecho itemBorrar(DispositivoService.Sesion sesion, Operacion op, Instant hechaEn) {
        Usuario yo = conPerfil(sesion);
        EstadoItem actual = item(op.uuid());
        if (actual == null) {
            if (borradoAntes(op.uuid(), sesion.usuarioId())) {
                return new Hecho(Resultado.APLICADA, "Ya estaba borrado.", null);
            }
            throw new Rechazo(Resultado.RECHAZADA, "No se encontró (o no es tuyo).");
        }
        if (actual.usuarioId != sesion.usuarioId()) throw new Rechazo(Resultado.RECHAZADA, "No se encontró (o no es tuyo).");
        if ((op.baseVersion() == null || op.baseVersion() != actual.version) && !hechaEn.isAfter(actual.actualizadoEn)) {
            throw new Rechazo(Resultado.CONFLICTO,
                    "Este repuesto se modificó después desde otro equipo, así que no se borró.");
        }
        itemStock.borrar(yo, actual.id);
        return new Hecho(Resultado.APLICADA, null, null);
    }

    /** Encabezados que entiende ProcesadorFilas (ver Campo): así la carga sin conexión valida igual que la web. */
    private static final String[] COLUMNAS = {"codigo", "descripcion", "marca", "vehiculo", "cantidad", "precio",
            "reemplaza", "equivalentes"};

    private Hecho stockReemplazar(DispositivoService.Sesion sesion, Map<String, Object> datos, Instant hechaEn) {
        Usuario yo = conPerfil(sesion);
        Object filasCrudas = datos.get("filas");
        if (!(filasCrudas instanceof List<?> filas) || filas.isEmpty()) {
            throw new Rechazo(Resultado.RECHAZADA, "El Excel no trae filas.");
        }
        if (filas.size() > MAX_FILAS_EXCEL) {
            throw new Rechazo(Resultado.RECHAZADA, "El Excel tiene más de " + MAX_FILAS_EXCEL + " filas.");
        }

        // Mismas validaciones que la carga desde la web: se pasan las filas por ProcesadorFilas
        ProcesadorFilas procesador = new ProcesadorFilas();
        Map<Integer, String> encabezado = new HashMap<>();
        for (int c = 0; c < COLUMNAS.length; c++) encabezado.put(c, COLUMNAS[c]);
        procesador.fila(0, encabezado);
        int numero = 1;
        // uuid que la app ya le dio a cada fila (para que el ítem tenga el mismo id acá y en el equipo)
        java.util.IdentityHashMap<FilaStock, UUID> uuids = new java.util.IdentityHashMap<>();
        for (Object f : filas) {
            Map<Integer, String> celdas = new HashMap<>();
            UUID uuid = null;
            if (f instanceof Map<?, ?> m) {
                for (int c = 0; c < COLUMNAS.length; c++) {
                    Object v = m.get(COLUMNAS[c]);
                    celdas.put(c, v instanceof Collection<?> lista ? String.join(" / ", lista.stream().map(String::valueOf).toList())
                            : v == null ? null : String.valueOf(v));
                }
                uuid = uuidDe(m.get("uuid"));
            }
            int antes = procesador.resultado().filasOk().size();
            procesador.fila(numero++, celdas);
            List<FilaStock> hasta = procesador.resultado().filasOk();
            if (uuid != null && hasta.size() > antes) uuids.put(hasta.get(hasta.size() - 1), uuid);
        }
        ResultadoLectura lectura = procesador.resultado();
        List<FilaStock> ok = lectura.filasOk();
        if (ok.isEmpty()) {
            // Nunca vaciar el stock por un archivo que no se pudo leer
            throw new Rechazo(Resultado.RECHAZADA, "No se cargó nada: "
                    + String.join(" ", lectura.errores().stream().limit(5).toList()));
        }

        String nombre = texto(datos, "nombreArchivo");
        Importacion imp = importaciones.save(new Importacion(yo.getId(),
                recortar((nombre.isBlank() ? "Excel" : nombre) + " (sin conexión)", 255)));
        StockWriter.Resumen resumen = carga.aplicar(yo.getId(), ok, hechaEn, hechaEn, uuids::get);
        imp.terminar(ok.size(), lectura.errores().size(), String.join("\n", lectura.errores().stream().limit(200).toList()));

        StringBuilder msj = new StringBuilder("Se cargaron " + ok.size() + " repuestos ("
                + resumen.insertados() + " nuevos, " + resumen.actualizados() + " cambiados, "
                + resumen.borrados() + " borrados).");
        if (!lectura.errores().isEmpty()) {
            msj.append(' ').append(lectura.errores().size()).append(" filas con error: ")
                    .append(String.join(" ", lectura.errores().stream().limit(3).toList()));
        }
        Resultado resultado = Resultado.APLICADA;
        if (resumen.conservados() > 0) {
            resultado = Resultado.APLICADA_CON_CONFLICTO;
            msj.append(' ').append(resumen.conservados())
                    .append(" repuestos quedaron como estaban porque tenían cambios más nuevos que esta carga.");
        }
        long vendedorId = yo.getId();
        Set<String> codigos = CargaStock.codigos(ok);
        return new Hecho(resultado, msj.toString(), null, () -> matching.buscarDemandasPara(vendedorId, codigos));
    }

    private Hecho pedido(DispositivoService.Sesion sesion, TipoOperacion tipo, Operacion op, Map<String, Object> datos) {
        if (op.uuid() == null) throw new Rechazo(Resultado.RECHAZADA, "Falta el uuid del pedido.");
        if (op.baseVersion() == null) throw new Rechazo(Resultado.RECHAZADA, "Falta la versión del pedido.");
        List<Object[]> filas = jdbc.query(
                "SELECT id, version, estado FROM solicitud WHERE uuid = ? AND (comprador_id = ? OR vendedor_id = ?) FOR UPDATE",
                (rs, i) -> new Object[]{rs.getLong(1), rs.getLong(2), rs.getString(3)},
                op.uuid(), sesion.usuarioId(), sesion.usuarioId());
        if (filas.isEmpty()) throw new Rechazo(Resultado.RECHAZADA, "No se encontró el pedido.");
        long id = (Long) filas.get(0)[0];
        long version = (Long) filas.get(0)[1];
        EstadoSolicitud estado = EstadoSolicitud.valueOf((String) filas.get(0)[2]);

        if (version != op.baseVersion()) {
            // Los dos hicieron lo mismo (ej. los dos lo cancelaron): no hay nada que avisar
            boolean sinPrecio = tipo != TipoOperacion.PEDIDO_OFERTAR && tipo != TipoOperacion.PEDIDO_CONTRAOFERTAR;
            if (sinPrecio && estado == tipo.destino()) {
                return new Hecho(Resultado.APLICADA, "Ya estaba así.", version);
            }
            throw new Rechazo(Resultado.CONFLICTO, "El pedido cambió mientras no tenías conexión: ahora está "
                    + legible(estado) + ". Revisalo antes de volver a responder.");
        }

        Usuario yo = usuarios.findById(sesion.usuarioId()).orElseThrow();
        String precio = texto(datos, "precio");
        Consumer<Long> accion = switch (tipo) {
            case PEDIDO_OFERTAR -> x -> solicitudes.ofertar(yo, x, precio);
            case PEDIDO_SIN_STOCK -> x -> solicitudes.sinStock(yo, x);
            case PEDIDO_ACEPTAR_CONTRAOFERTA -> x -> solicitudes.aceptarContraoferta(yo, x);
            case PEDIDO_RECHAZAR_CONTRAOFERTA -> x -> solicitudes.rechazarContraoferta(yo, x);
            case PEDIDO_ACEPTAR_OFERTA -> x -> solicitudes.aceptarOferta(yo, x);
            case PEDIDO_CONTRAOFERTAR -> x -> solicitudes.contraofertar(yo, x, precio);
            case PEDIDO_CANCELAR -> x -> solicitudes.cancelar(yo, x);
            default -> throw new IllegalStateException(tipo.name());
        };
        accion.accept(id);
        return new Hecho(Resultado.APLICADA, null, versionDePedido(op.uuid()));
    }

    // ---------- Apoyo ----------

    private Usuario conPerfil(DispositivoService.Sesion sesion) {
        Usuario yo = usuarios.findById(sesion.usuarioId()).orElseThrow();
        if (!yo.isPerfilCompleto()) throw new com.stockautopartes.usuario.UsuarioNoRegistradoException();
        return yo;
    }

    /** La versión se lee después de un flush para ver lo que puso el trigger. */
    private Long versionDeItem(UUID uuid) {
        flush();
        List<Long> v = jdbc.queryForList("SELECT version FROM item_stock WHERE uuid = ?", Long.class, uuid);
        return v.isEmpty() ? null : v.get(0);
    }

    private Long versionDePedido(UUID uuid) {
        flush();
        List<Long> v = jdbc.queryForList("SELECT version FROM solicitud WHERE uuid = ?", Long.class, uuid);
        return v.isEmpty() ? null : v.get(0);
    }

    /** Versión actual de lo que tocaba la operación (para que el equipo sepa con qué quedó el servidor). */
    private Long versionActual(Operacion op) {
        if (op.uuid() == null) return null;
        List<Long> v = jdbc.queryForList("""
                SELECT version FROM item_stock WHERE uuid = ?
                UNION ALL SELECT version FROM solicitud WHERE uuid = ?
                """, Long.class, op.uuid(), op.uuid());
        return v.isEmpty() ? null : v.get(0);
    }

    private jakarta.persistence.EntityManager em;

    @jakarta.persistence.PersistenceContext
    void setEntityManager(jakarta.persistence.EntityManager em) {
        this.em = em;
    }

    private void flush() {
        em.flush();
    }

    private static String legible(EstadoSolicitud e) {
        return switch (e) {
            case PENDIENTE -> "esperando la respuesta del vendedor";
            case OFERTADA -> "con oferta del vendedor";
            case CONTRAOFERTA -> "con contraoferta del comprador";
            case ACEPTADA -> "aceptado";
            case RECHAZADA -> "rechazado";
            case CANCELADA -> "cancelado";
        };
    }

    private static UUID uuidDe(Object v) {
        if (v == null) return null;
        try {
            return UUID.fromString(String.valueOf(v));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String texto(Map<String, Object> datos, String clave) {
        Object v = datos.get(clave);
        return v == null ? "" : String.valueOf(v);
    }

    private static String recortar(String s) {
        return recortar(s, 500);
    }

    private static String recortar(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}

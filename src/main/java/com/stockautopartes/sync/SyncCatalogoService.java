package com.stockautopartes.sync;

import com.stockautopartes.web.Privacidad;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Bajada del stock de los OTROS vendedores, para buscar sin conexión.
 * <p>
 * Alcance: lo elige el equipo (por defecto las marcas que trabaja el usuario, en todo el país). Los ítems sin
 * marca detectada se incluyen siempre, porque igual aparecen al buscar por palabras. Si el equipo cambia el
 * alcance, tiene que empezar de nuevo sin cursor.
 * <p>
 * Privacidad: no se baja el nombre real ni el teléfono del vendedor, solo el nombre tapado ("******pe"),
 * su ciudad y estado (para ordenar por cercanía) y un alias para agrupar sus ítems. El alias sale de un
 * secreto del servidor + quién baja + el vendedor: es distinto para cada usuario.
 * <p>
 * Cómo pagina: una "vuelta" baja lo cambiado entre {@code desde} y {@code hasta} (la transacción abierta más
 * vieja al empezar, como en SyncPullService). La primera página trae además todos los borrados y las
 * relaciones entre códigos de esa vuelta (son pocos); las siguientes, solo ítems.
 * Cursor: "N" = vuelta terminada hasta N; "v.desde.hasta.xid.uuid" = a mitad de una vuelta.
 */
@Service
public class SyncCatalogoService {

    static final int LIMITE_MAXIMO = 5000;

    private final NamedParameterJdbcTemplate jdbc;
    private final Privacidad privacidad;
    private final Clock clock;

    public SyncCatalogoService(NamedParameterJdbcTemplate jdbc, Privacidad privacidad, Clock clock) {
        this.jdbc = jdbc;
        this.privacidad = privacidad;
        this.clock = clock;
    }

    public record CatalogoRespuesta(String cursor, boolean masDatos, boolean resyncCompleto, Instant servidorEn,
                                    List<ItemOtroDto> items, List<UUID> borrados, List<RelacionDto> relaciones) {
    }

    /** Un ítem de otro vendedor. cantidad 0 = ya no tiene (no mostrarlo); null = a consultar. */
    public record ItemOtroDto(UUID uuid, long version, String codigo, String descripcion, String marcaRepuesto,
                              String vehiculo, Integer marcaId, Integer cantidad, BigDecimal precioUsd,
                              Instant cargadoEn, String vendedor, String vendedorNombre, String estado,
                              String ciudad) {
    }

    /** Códigos ya normalizados. REEMPLAZO: quien busca A puede usar B. EQUIVALENTE: los dos sentidos. */
    public record RelacionDto(String codigoA, String codigoB, String tipo) {
    }

    private record Vuelta(long desde, long hasta, Long xid, UUID uuid) {

        static Vuelta leer(String texto) {
            if (texto == null || texto.isBlank()) return new Vuelta(0, -1, null, null);
            String[] p = texto.strip().split("\\.");
            try {
                if (p.length == 1) {
                    long n = Long.parseLong(p[0]);
                    if (n < 0) throw new IllegalArgumentException("Cursor inválido");
                    return new Vuelta(n, -1, null, null);
                }
                if (p.length == 5 && p[0].equals("v")) {
                    return new Vuelta(Long.parseLong(p[1]), Long.parseLong(p[2]), Long.parseLong(p[3]),
                            UUID.fromString(p[4]));
                }
            } catch (NumberFormatException e) {
                // cae abajo
            }
            throw new IllegalArgumentException("Cursor inválido");
        }

        boolean empezando() {
            return hasta < 0;
        }
    }

    @Transactional(readOnly = true)
    public CatalogoRespuesta bajar(long usuarioId, String cursorTexto, int limite, List<Integer> marcas,
                                   List<String> estados) {
        Vuelta v = Vuelta.leer(cursorTexto);
        int n = Math.max(1, Math.min(limite, LIMITE_MAXIMO));
        Instant ahora = clock.instant();

        long purgado = jdbc.getJdbcTemplate().queryForObject(
                "SELECT purgado_hasta_xid::text::bigint FROM sync_estado WHERE id = 1", Long.class);
        if (v.desde > 0 && v.desde <= purgado) {
            return new CatalogoRespuesta("", false, true, ahora, List.of(), List.of(), List.of());
        }

        boolean sinMarcas = marcas == null || marcas.isEmpty();
        boolean sinEstados = estados == null || estados.isEmpty();
        long hasta = v.empezando()
                ? jdbc.getJdbcTemplate().queryForObject("SELECT pg_snapshot_xmin(pg_current_snapshot())::text::bigint", Long.class)
                : v.hasta;
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("yo", usuarioId)
                .addValue("desde", Long.toString(v.desde))
                .addValue("hasta", Long.toString(hasta))
                .addValue("sinMarcas", sinMarcas)
                .addValue("marcas", sinMarcas ? List.of(-1) : marcas)
                .addValue("sinEstados", sinEstados)
                .addValue("estados", sinEstados ? List.of("") : estados)
                .addValue("x", v.xid == null ? null : Long.toString(v.xid))
                .addValue("uuid", v.uuid)
                .addValue("limite", n);

        List<UUID> borrados = List.of();
        List<RelacionDto> relaciones = List.of();
        if (v.empezando()) {
            borrados = jdbc.queryForList("""
                    SELECT DISTINCT uuid FROM sync_borrado
                    WHERE entidad = 'item' AND usuario_id <> :yo
                      AND sync_xid >= CAST(:desde AS xid8) AND sync_xid < CAST(:hasta AS xid8)
                      AND (:sinMarcas OR marca_id IN (:marcas) OR marca_id IS NULL)
                    """, p, UUID.class);
            relaciones = jdbc.query("""
                    SELECT codigo_a, codigo_b, tipo FROM relacion_codigo
                    WHERE sync_xid >= CAST(:desde AS xid8) AND sync_xid < CAST(:hasta AS xid8)
                    ORDER BY id
                    """, p, (rs, i) -> new RelacionDto(rs.getString(1), rs.getString(2), rs.getString(3)));
        }

        String secreto = jdbc.getJdbcTemplate().queryForObject("SELECT alias_secreto FROM sync_estado WHERE id = 1",
                String.class);
        p.addValue("secreto", secreto);
        List<Object[]> filas = jdbc.query("""
                SELECT i.uuid, i.sync_xid::text AS x, i.version, i.codigo_original, i.descripcion, i.marca_repuesto,
                       i.vehiculo, i.marca_id, i.cantidad, i.precio_usd, i.cargado_en,
                       substr(md5(:secreto || ':' || :yo || ':' || u.id), 1, 12) AS alias,
                       u.nombre, u.estado, u.ciudad
                FROM item_stock i JOIN usuario u ON u.id = i.usuario_id
                WHERE i.sync_xid >= CAST(:desde AS xid8) AND i.sync_xid < CAST(:hasta AS xid8)
                  AND i.usuario_id <> :yo
                  AND (:sinMarcas OR i.marca_id IN (:marcas) OR i.marca_id IS NULL)
                  AND (:sinEstados OR u.estado IN (:estados))
                  AND (CAST(:x AS text) IS NULL OR (i.sync_xid, i.uuid) > (CAST(:x AS xid8), CAST(:uuid AS uuid)))
                ORDER BY i.sync_xid, i.uuid
                LIMIT :limite
                """, p, (rs, i) -> {
            Timestamp cargado = rs.getTimestamp("cargado_en");
            ItemOtroDto dto = new ItemOtroDto(rs.getObject("uuid", UUID.class), rs.getLong("version"),
                    rs.getString("codigo_original"), rs.getString("descripcion"), rs.getString("marca_repuesto"),
                    rs.getString("vehiculo"), (Integer) rs.getObject("marca_id"), (Integer) rs.getObject("cantidad"),
                    rs.getBigDecimal("precio_usd"), cargado == null ? null : cargado.toInstant(),
                    rs.getString("alias"), privacidad.ocultar(rs.getString("nombre")),
                    rs.getString("estado"), rs.getString("ciudad"));
            return new Object[]{dto, rs.getString("x")};
        });

        List<ItemOtroDto> items = filas.stream().map(f -> (ItemOtroDto) f[0]).toList();
        boolean masDatos = filas.size() == n;
        String cursor;
        if (masDatos) {
            Object[] ultima = filas.get(filas.size() - 1);
            cursor = "v." + v.desde + "." + hasta + "." + ultima[1] + "." + ((ItemOtroDto) ultima[0]).uuid();
        } else {
            cursor = Long.toString(hasta);
        }
        return new CatalogoRespuesta(cursor, masDatos, false, ahora, items, borrados, relaciones);
    }
}

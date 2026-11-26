package com.stockautopartes.sync;

import com.stockautopartes.importacion.Campo;
import com.stockautopartes.sync.SyncDtos.Catalogos;
import com.stockautopartes.sync.SyncDtos.CiudadDto;
import com.stockautopartes.sync.SyncDtos.Config;
import com.stockautopartes.sync.SyncDtos.Encabezados;
import com.stockautopartes.sync.SyncDtos.EstadoDto;
import com.stockautopartes.sync.SyncDtos.LoginPedido;
import com.stockautopartes.sync.SyncDtos.LoginRespuesta;
import com.stockautopartes.sync.SyncDtos.MarcaDto;
import com.stockautopartes.sync.SyncDtos.ModeloDto;
import com.stockautopartes.sync.SyncDtos.Ping;
import com.stockautopartes.sync.SyncDtos.PullRespuesta;
import com.stockautopartes.sync.SyncDtos.PushPedido;
import com.stockautopartes.sync.SyncDtos.PushRespuesta;
import com.stockautopartes.sync.SyncDtos.UsuarioDto;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * API de la app sin conexión (issue #9). JSON, sin sesión: el equipo manda "Authorization: Bearer &lt;token&gt;".
 * <pre>
 * POST /api/auth/dispositivo   login del equipo (usuario o email + contraseña) → token
 * POST /api/auth/salir         cerrar sesión en este equipo
 * GET  /api/sync/ping          ¿hay conexión de verdad? (sin token)
 * GET  /api/sync/pull          bajar lo que cambió desde el cursor (lo mío: stock, pedidos, demandas)
 * GET  /api/sync/catalogo      bajar el stock de los otros vendedores (para buscar sin conexión)
 * POST /api/sync/push          subir los cambios hechos sin conexión
 * POST /api/sync/pedido-nuevo  pedir un repuesto (solo con conexión)
 * GET  /api/sync/catalogos     marcas, modelos, ciudades, estados y encabezados del Excel (con ETag)
 * </pre>
 */
@RestController
@RequestMapping("/api")
public class SyncController {

    private final DispositivoService dispositivos;
    private final SyncPullService pull;
    private final SyncPushService push;
    private final SyncCatalogoService catalogo;
    private final com.stockautopartes.solicitud.SolicitudService solicitudes;
    private final com.stockautopartes.usuario.UsuarioActual usuarioActual;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final Config config;

    public SyncController(DispositivoService dispositivos, SyncPullService pull, SyncPushService push,
                          SyncCatalogoService catalogo, com.stockautopartes.solicitud.SolicitudService solicitudes,
                          com.stockautopartes.usuario.UsuarioActual usuarioActual, JdbcTemplate jdbc, Clock clock,
                          @Value("${app.sync.dias-sin-sincronizar:3}") int diasSinSincronizar,
                          @Value("${app.sync.dias-token:30}") int diasToken) {
        this.dispositivos = dispositivos;
        this.pull = pull;
        this.push = push;
        this.catalogo = catalogo;
        this.solicitudes = solicitudes;
        this.usuarioActual = usuarioActual;
        this.jdbc = jdbc;
        this.clock = clock;
        this.config = new Config(diasSinSincronizar, diasToken);
    }

    // ---------- Login ----------

    @PostMapping("/auth/dispositivo")
    public LoginRespuesta login(@RequestBody LoginPedido pedido) {
        DispositivoService.Alta alta = dispositivos.registrar(pedido.usuario(), pedido.password(),
                pedido.dispositivoId(), pedido.nombreDispositivo());
        var u = alta.usuario();
        return new LoginRespuesta(alta.token(), pedido.dispositivoId(),
                new UsuarioDto(u.getUsername(), u.getNombre(), u.isAdmin(), u.isPerfilCompleto()), config);
    }

    @PostMapping("/auth/salir")
    public ResponseEntity<Void> salir(HttpServletRequest req) {
        DispositivoService.Sesion s = sesion(req);
        dispositivos.revocar(s.dispositivoId(), s.usuarioId());
        return ResponseEntity.noContent().build();
    }

    // ---------- Sincronización ----------

    @GetMapping("/sync/ping")
    public ResponseEntity<Ping> ping() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Ping(clock.instant(), config));
    }

    @GetMapping("/sync/pull")
    public ResponseEntity<PullRespuesta> bajar(HttpServletRequest req,
                                               @RequestParam(required = false) String cursor,
                                               @RequestParam(defaultValue = "500") int limite) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(pull.bajar(sesion(req).usuarioId(), cursor, limite));
    }

    /**
     * Stock de los otros vendedores para buscar sin conexión. marcas/estados: alcance (ej. marcas=12,40).
     * Sin marcas = todas. Si el equipo cambia el alcance, tiene que empezar sin cursor.
     */
    @GetMapping("/sync/catalogo")
    public ResponseEntity<SyncCatalogoService.CatalogoRespuesta> bajarCatalogo(
            HttpServletRequest req,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "2000") int limite,
            @RequestParam(required = false) List<Integer> marcas,
            @RequestParam(required = false) List<String> estados) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(catalogo.bajar(sesion(req).usuarioId(), cursor, limite, marcas, estados));
    }

    @PostMapping("/sync/push")
    public PushRespuesta subir(HttpServletRequest req, @RequestBody PushPedido pedido) {
        if (pedido.operaciones() == null || pedido.operaciones().isEmpty()) {
            return new PushRespuesta(clock.instant(), List.of());
        }
        if (pedido.operaciones().size() > SyncPushService.MAX_OPERACIONES) {
            throw new IllegalArgumentException("Mandá como mucho " + SyncPushService.MAX_OPERACIONES
                    + " operaciones por vez.");
        }
        return push.subir(sesion(req), pedido.relojCliente(), pedido.operaciones());
    }

    public record PedidoNuevo(java.util.UUID itemUuid, Integer cantidad) {
    }

    public record PedidoCreado(java.util.UUID uuid) {
    }

    /**
     * Pedido nuevo desde la búsqueda de la app. Solo con conexión: el stock del vendedor tiene que estar al día
     * (mismas reglas que la web: SolicitudService.crear).
     */
    @PostMapping("/sync/pedido-nuevo")
    public ResponseEntity<?> pedidoNuevo(@RequestBody PedidoNuevo pedido) {
        if (pedido.itemUuid() == null) throw new IllegalArgumentException("Falta el repuesto.");
        List<Long> ids = jdbc.queryForList("SELECT id FROM item_stock WHERE uuid = ?", Long.class, pedido.itemUuid());
        if (ids.isEmpty()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new SyncDtos.Error("YA_NO_ESTA",
                    "Ese repuesto ya no está publicado."));
        }
        try {
            var s = solicitudes.crear(usuarioActual.obtener(), ids.get(0), pedido.cantidad() == null ? 1 : pedido.cantidad());
            return ResponseEntity.ok(new PedidoCreado(s.getUuid()));
        } catch (com.stockautopartes.solicitud.SolicitudException e) {
            return ResponseEntity.badRequest().body(new SyncDtos.Error("PEDIDO", e.getMessage()));
        } catch (com.stockautopartes.usuario.UsuarioNoRegistradoException e) {
            return ResponseEntity.badRequest().body(new SyncDtos.Error("PERFIL",
                    "Completá tu perfil (teléfono, estado y ciudad) antes de pedir."));
        }
    }

    /** Catálogos chicos (~1 MB sin comprimir). Con If-None-Match y la misma versión contesta 304 sin datos. */
    @GetMapping("/sync/catalogos")
    public ResponseEntity<Catalogos> catalogos(
            @RequestHeader(value = "If-None-Match", required = false) String siTiene) {
        String version = jdbc.queryForObject("""
                SELECT md5(concat_ws('|',
                    (SELECT string_agg(id || nombre || tipo || activa, ',' ORDER BY id) FROM marca),
                    (SELECT string_agg(marca_id || nombre, ',' ORDER BY id) FROM modelo),
                    (SELECT string_agg(estado || nombre, ',' ORDER BY id) FROM ciudad),
                    (SELECT string_agg(estado || lat || lon, ',' ORDER BY estado) FROM estado_ubicacion)))
                """, String.class) + "-" + Campo.values().length;
        String etag = "\"" + version + "\"";
        if (etag.equals(siTiene)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
        }
        List<MarcaDto> marcas = jdbc.query("SELECT id, nombre, tipo, activa FROM marca ORDER BY nombre",
                (rs, i) -> new MarcaDto(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getBoolean(4)));
        List<ModeloDto> modelos = jdbc.query("SELECT marca_id, nombre FROM modelo ORDER BY nombre",
                (rs, i) -> new ModeloDto(rs.getInt(1), rs.getString(2)));
        List<CiudadDto> ciudades = jdbc.query("SELECT estado, nombre FROM ciudad ORDER BY estado, nombre",
                (rs, i) -> new CiudadDto(rs.getString(1), rs.getString(2)));
        List<EstadoDto> estados = jdbc.query("SELECT estado, lat, lon FROM estado_ubicacion ORDER BY estado",
                (rs, i) -> new EstadoDto(rs.getString(1), rs.getDouble(2), rs.getDouble(3)));
        Map<String, Encabezados> encabezados = new LinkedHashMap<>();
        for (Campo c : Campo.values()) {
            encabezados.put(c.name(), new Encabezados(c.obligatorio(), c.nombres(), c.prefijos()));
        }
        return ResponseEntity.ok().eTag(etag).cacheControl(CacheControl.noCache())
                .body(new Catalogos(version, marcas, modelos, ciudades, estados, encabezados));
    }

    // ---------- Errores ----------

    @ExceptionHandler(DispositivoService.LoginException.class)
    public ResponseEntity<SyncDtos.Error> loginFallido(DispositivoService.LoginException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new SyncDtos.Error("LOGIN", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<SyncDtos.Error> pedidoInvalido(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(new SyncDtos.Error("PEDIDO_INVALIDO", e.getMessage()));
    }

    private static DispositivoService.Sesion sesion(HttpServletRequest req) {
        Object s = req.getAttribute(TokenDispositivoFilter.ATRIBUTO_SESION);
        if (s == null) throw new IllegalStateException("Sin sesión de equipo"); // no pasa: Security pide token
        return (DispositivoService.Sesion) s;
    }
}

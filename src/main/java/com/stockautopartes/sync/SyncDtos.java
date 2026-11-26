package com.stockautopartes.sync;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Lo que viaja en la API de sincronización (JSON). */
public final class SyncDtos {

    private SyncDtos() {
    }

    // ---------- Login del equipo ----------

    public record LoginPedido(String usuario, String password, UUID dispositivoId, String nombreDispositivo) {
    }

    public record LoginRespuesta(String token, UUID dispositivoId, UsuarioDto usuario, Config config) {
    }

    public record UsuarioDto(String username, String nombre, boolean admin, boolean perfilCompleto) {
    }

    /** Reglas que el equipo aplica sin conexión. */
    public record Config(int diasSinSincronizar, int diasToken) {
    }

    public record Error(String error, String mensaje) {
    }

    public record Ping(Instant servidorEn, Config config) {
    }

    // ---------- Bajada ----------

    /**
     * {@code cursor}: lo que el equipo manda en el próximo pull. {@code masDatos}: hay otra página, pedirla ya.
     * {@code resyncCompleto}: el equipo tiene que borrar lo que bajó y pedir de nuevo sin cursor.
     */
    public record PullRespuesta(String cursor, boolean masDatos, boolean resyncCompleto, Instant servidorEn,
                                PerfilDto perfil, List<ItemDto> items, List<PedidoDto> pedidos,
                                List<DemandaDto> demandas, List<BorradoDto> borrados) {
    }

    public record PerfilDto(String username, String nombre, String telefono, String estado, String ciudad,
                            boolean perfilCompleto, Instant ultimaCarga, List<Integer> marcas) {
    }

    /** cantidad/precioUsd null = a consultar. marcaId = marca del vehículo (null = no se sabe). */
    public record ItemDto(UUID uuid, long version, String codigo, String descripcion, String marcaRepuesto,
                          String vehiculo, Integer marcaId, Integer cantidad, BigDecimal precioUsd,
                          Instant cargadoEn, Instant actualizadoEn) {
    }

    /**
     * rol: VENDEDOR (me lo pidieron) o COMPRADOR (lo pedí yo). La contraparte trae teléfono solo si el pedido
     * está ACEPTADO, igual que en la web.
     */
    public record PedidoDto(UUID uuid, long version, String rol, String codigo, String descripcion, int cantidad,
                            BigDecimal precioUsd, BigDecimal precioOferta, BigDecimal precioContraoferta,
                            String estado, Instant creadaEn, Instant actualizadaEn, ContraparteDto contraparte) {
    }

    public record ContraparteDto(String nombre, String ciudad, String estado, String telefono) {
    }

    public record DemandaDto(UUID uuid, long version, String texto, String estado, Instant creadaEn) {
    }

    /** entidad: "item" o "demanda". */
    public record BorradoDto(String entidad, UUID uuid) {
    }

    // ---------- Subida ----------

    /** relojCliente: hora del equipo al mandar (para corregir la diferencia de reloj). */
    public record PushPedido(Instant relojCliente, List<Operacion> operaciones) {
    }

    /**
     * Un cambio hecho en el equipo. opId: UUID único de la operación (reintentar con el mismo opId no duplica).
     * uuid: el ítem o pedido. baseVersion: la versión que tenía el equipo cuando se hizo el cambio (null = alta).
     * hechaEn: hora del equipo en que se hizo. datos: depende del tipo (ver {@link TipoOperacion}).
     */
    public record Operacion(UUID opId, String tipo, UUID uuid, Long baseVersion, Instant hechaEn,
                            Map<String, Object> datos) {
    }

    public record PushRespuesta(Instant servidorEn, List<ResultadoOperacion> resultados) {
    }

    /**
     * resultado: ver {@link Resultado}. duplicada: ya se había recibido (es el resultado de la primera vez).
     * version: la versión que quedó en el servidor (para que el equipo la use como base del próximo cambio).
     */
    public record ResultadoOperacion(UUID opId, String resultado, String mensaje, UUID uuid, Long version,
                                     boolean duplicada) {
    }

    // ---------- Catálogos ----------

    public record Catalogos(String version, List<MarcaDto> marcas, List<ModeloDto> modelos, List<CiudadDto> ciudades,
                            List<EstadoDto> estados, Map<String, Encabezados> encabezados) {
    }

    public record MarcaDto(int id, String nombre, String tipo, boolean activa) {
    }

    public record ModeloDto(int marcaId, String nombre) {
    }

    public record CiudadDto(String estado, String nombre) {
    }

    public record EstadoDto(String estado, double lat, double lon) {
    }

    /** Sinónimos de encabezados del Excel, ya normalizados, para que la app lea el Excel igual que el servidor. */
    public record Encabezados(boolean obligatorio, List<String> nombres, List<String> prefijos) {
    }
}

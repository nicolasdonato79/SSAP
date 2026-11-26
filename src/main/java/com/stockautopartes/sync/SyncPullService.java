package com.stockautopartes.sync;

import com.stockautopartes.marca.Marca;
import com.stockautopartes.solicitud.EstadoSolicitud;
import com.stockautopartes.sync.SyncDtos.BorradoDto;
import com.stockautopartes.sync.SyncDtos.ContraparteDto;
import com.stockautopartes.sync.SyncDtos.DemandaDto;
import com.stockautopartes.sync.SyncDtos.ItemDto;
import com.stockautopartes.sync.SyncDtos.PedidoDto;
import com.stockautopartes.sync.SyncDtos.PerfilDto;
import com.stockautopartes.sync.SyncDtos.PullRespuesta;
import com.stockautopartes.usuario.Usuario;
import com.stockautopartes.usuario.UsuarioRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Bajada: lo que cambió para este usuario desde el cursor del equipo (su stock, sus pedidos como comprador y
 * como vendedor, sus "avisarme cuando aparezca" y las marcas de borrado).
 * <p>
 * Cómo no saltear cambios: cada fila guarda la transacción que la cambió (sync_xid, ver V15). Se devuelve lo
 * de las transacciones entre el cursor y la más vieja que todavía está abierta ({@code pg_snapshot_xmin}):
 * todo lo anterior a esa ya terminó, así que no puede aparecer después una fila con un número menor.
 * Una transacción larga que sigue abierta solo demora la bajada de lo posterior; no se pierde nada.
 * Una fila puede llegar dos veces (si cambió de nuevo): el equipo la guarda por uuid, no pasa nada.
 */
@Service
public class SyncPullService {

    static final int LIMITE_MAXIMO = 2000;

    /** Clave de cada cambio: (transacción, entidad, uuid). El prefijo a_/b_/... solo fija el orden. */
    private static final String CLAVES = """
            WITH c AS (
                SELECT sync_xid, 'a_item' AS ent, uuid FROM item_stock WHERE usuario_id = :u
                UNION ALL
                SELECT sync_xid, 'b_pedido', uuid FROM solicitud WHERE comprador_id = :u OR vendedor_id = :u
                UNION ALL
                SELECT sync_xid, 'c_demanda', uuid FROM demanda WHERE usuario_id = :u
                UNION ALL
                SELECT sync_xid, 'd_borrado', uuid FROM sync_borrado WHERE usuario_id = :u
            )
            SELECT sync_xid::text AS x, ent, uuid FROM c
            WHERE sync_xid >= CAST(:desde AS xid8) AND sync_xid < CAST(:hasta AS xid8)
              AND (CAST(:ent AS text) IS NULL
                   OR (sync_xid, ent, uuid) > (CAST(:desde AS xid8), CAST(:ent AS text), CAST(:uuid AS uuid)))
            ORDER BY sync_xid, ent, uuid
            LIMIT :limite
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final UsuarioRepository usuarios;
    private final Clock clock;

    public SyncPullService(NamedParameterJdbcTemplate jdbc, UsuarioRepository usuarios, Clock clock) {
        this.jdbc = jdbc;
        this.usuarios = usuarios;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PullRespuesta bajar(long usuarioId, String cursorTexto, int limite) {
        SyncCursor cursor = SyncCursor.leer(cursorTexto);
        int n = Math.max(1, Math.min(limite, LIMITE_MAXIMO));
        Instant ahora = clock.instant();
        PerfilDto perfil = perfil(usuarioId);

        long purgado = jdbc.getJdbcTemplate().queryForObject(
                "SELECT purgado_hasta_xid::text::bigint FROM sync_estado WHERE id = 1", Long.class);
        if (cursor.xid() > 0 && cursor.xid() <= purgado) {
            // Hace mucho que no sincroniza: ya se purgaron marcas de borrado que no vio.
            return new PullRespuesta("", false, true, ahora, perfil, List.of(), List.of(), List.of(), List.of());
        }

        // Primero el límite: todo lo de transacciones anteriores a esta ya terminó.
        long hasta = jdbc.getJdbcTemplate().queryForObject(
                "SELECT pg_snapshot_xmin(pg_current_snapshot())::text::bigint", Long.class);

        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("u", usuarioId)
                .addValue("desde", Long.toString(cursor.xid()))
                .addValue("hasta", Long.toString(hasta))
                .addValue("ent", cursor.entidad())
                .addValue("uuid", cursor.uuid())
                .addValue("limite", n);
        List<Clave> claves = jdbc.query(CLAVES, p, (rs, i) ->
                new Clave(Long.parseLong(rs.getString("x")), rs.getString("ent"), rs.getObject("uuid", UUID.class)));

        List<UUID> items = new ArrayList<>();
        List<UUID> pedidos = new ArrayList<>();
        List<UUID> demandas = new ArrayList<>();
        List<UUID> borrados = new ArrayList<>();
        for (Clave c : claves) {
            switch (c.ent) {
                case "a_item" -> items.add(c.uuid);
                case "b_pedido" -> pedidos.add(c.uuid);
                case "c_demanda" -> demandas.add(c.uuid);
                default -> borrados.add(c.uuid);
            }
        }

        boolean masDatos = claves.size() == n;
        String siguiente;
        if (masDatos) {
            Clave ultima = claves.get(claves.size() - 1);
            siguiente = new SyncCursor(ultima.xid, ultima.ent, ultima.uuid).toString();
        } else {
            siguiente = Long.toString(hasta);
        }
        return new PullRespuesta(siguiente, masDatos, false, ahora, perfil,
                items(usuarioId, items), pedidos(usuarioId, pedidos), demandas(usuarioId, demandas),
                borrados(usuarioId, borrados));
    }

    private record Clave(long xid, String ent, UUID uuid) {
    }

    // ---------- Detalle de cada entidad ----------

    private PerfilDto perfil(long usuarioId) {
        Usuario u = usuarios.findById(usuarioId).orElseThrow();
        return new PerfilDto(u.getUsername(), u.getNombre(), u.getTelefono(), u.getEstado(), u.getCiudad(),
                u.isPerfilCompleto(), u.getUltimaCarga(),
                u.getMarcasOrdenadas().stream().map(Marca::getId).toList());
    }

    private List<ItemDto> items(long usuarioId, List<UUID> uuids) {
        if (uuids.isEmpty()) return List.of();
        return jdbc.query("""
                SELECT uuid, version, codigo_original, descripcion, marca_repuesto, vehiculo, marca_id, cantidad,
                       precio_usd, cargado_en, actualizado_en
                FROM item_stock WHERE usuario_id = :u AND uuid IN (:uuids)
                """, new MapSqlParameterSource("u", usuarioId).addValue("uuids", uuids), (rs, i) -> new ItemDto(
                rs.getObject("uuid", UUID.class), rs.getLong("version"), rs.getString("codigo_original"),
                rs.getString("descripcion"), rs.getString("marca_repuesto"), rs.getString("vehiculo"),
                (Integer) rs.getObject("marca_id"), (Integer) rs.getObject("cantidad"), rs.getBigDecimal("precio_usd"),
                instante(rs, "cargado_en"), instante(rs, "actualizado_en")));
    }

    private List<PedidoDto> pedidos(long usuarioId, List<UUID> uuids) {
        if (uuids.isEmpty()) return List.of();
        return jdbc.query("""
                SELECT s.uuid, s.version, s.codigo, s.descripcion, s.cantidad, s.precio_usd, s.precio_oferta,
                       s.precio_contraoferta, s.estado, s.creada_en, s.actualizada_en,
                       (s.vendedor_id = :u) AS soy_vendedor,
                       o.nombre AS o_nombre, o.ciudad AS o_ciudad, o.estado AS o_estado, o.telefono AS o_telefono
                FROM solicitud s
                JOIN usuario o ON o.id = CASE WHEN s.vendedor_id = :u THEN s.comprador_id ELSE s.vendedor_id END
                WHERE (s.comprador_id = :u OR s.vendedor_id = :u) AND s.uuid IN (:uuids)
                """, new MapSqlParameterSource("u", usuarioId).addValue("uuids", uuids), (rs, i) -> {
            String estado = rs.getString("estado");
            boolean aceptado = EstadoSolicitud.ACEPTADA.name().equals(estado);
            return new PedidoDto(rs.getObject("uuid", UUID.class), rs.getLong("version"),
                    rs.getBoolean("soy_vendedor") ? "VENDEDOR" : "COMPRADOR",
                    rs.getString("codigo"), rs.getString("descripcion"), rs.getInt("cantidad"),
                    rs.getBigDecimal("precio_usd"), rs.getBigDecimal("precio_oferta"),
                    rs.getBigDecimal("precio_contraoferta"), estado,
                    instante(rs, "creada_en"), instante(rs, "actualizada_en"),
                    new ContraparteDto(rs.getString("o_nombre"), rs.getString("o_ciudad"), rs.getString("o_estado"),
                            aceptado ? rs.getString("o_telefono") : null));
        });
    }

    private List<DemandaDto> demandas(long usuarioId, List<UUID> uuids) {
        if (uuids.isEmpty()) return List.of();
        return jdbc.query("""
                SELECT uuid, version, texto, estado, creada_en FROM demanda
                WHERE usuario_id = :u AND uuid IN (:uuids)
                """, new MapSqlParameterSource("u", usuarioId).addValue("uuids", uuids), (rs, i) -> new DemandaDto(
                rs.getObject("uuid", UUID.class), rs.getLong("version"), rs.getString("texto"),
                rs.getString("estado"), instante(rs, "creada_en")));
    }

    private List<BorradoDto> borrados(long usuarioId, List<UUID> uuids) {
        if (uuids.isEmpty()) return List.of();
        return jdbc.query("""
                SELECT DISTINCT entidad, uuid FROM sync_borrado WHERE usuario_id = :u AND uuid IN (:uuids)
                """, new MapSqlParameterSource("u", usuarioId).addValue("uuids", uuids), (rs, i) ->
                new BorradoDto(rs.getString("entidad"), rs.getObject("uuid", UUID.class)));
    }

    private static Instant instante(ResultSet rs, String columna) throws SQLException {
        Timestamp t = rs.getTimestamp(columna);
        return t == null ? null : t.toInstant();
    }
}

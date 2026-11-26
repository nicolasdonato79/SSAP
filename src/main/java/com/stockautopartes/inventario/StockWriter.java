package com.stockautopartes.inventario;

import com.stockautopartes.importacion.FilaStock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/**
 * Reemplaza el stock completo de un vendedor con lo que trae un Excel.
 * <p>
 * Para el vendedor es lo mismo que antes (queda exactamente lo del Excel), pero en lugar de borrar todo y
 * volver a insertar compara por código (+ marca del repuesto): actualiza lo que cambió, inserta lo nuevo y
 * borra lo que ya no vino. Así cada ítem conserva su uuid y los equipos sin conexión bajan solo las
 * diferencias (issue #9), no 500 borrados + 500 altas por cada carga.
 * <p>
 * {@code corte}: los ítems que se modificaron en el servidor después de esa hora no se tocan ni se borran
 * (gana el último cambio). Una carga hecha en la web usa "ahora", así que no conserva nada; una carga hecha
 * sin conexión usa la hora en que se cargó en el equipo.
 * <p>
 * Inserts/updates en batch (JPA con IDENTITY no hace batch y un Excel puede traer miles de filas).
 * Debe llamarse dentro de una transacción.
 */
@Component
public class StockWriter {

    private static final String INSERT = """
            INSERT INTO item_stock (usuario_id, codigo_original, codigo_normalizado, descripcion,
                                    marca_repuesto, vehiculo, cantidad, precio_usd, cargado_en, marca_id, uuid)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String UPDATE = """
            UPDATE item_stock SET codigo_original = ?, codigo_normalizado = ?, descripcion = ?, marca_repuesto = ?,
                                  vehiculo = ?, cantidad = ?, precio_usd = ?, cargado_en = ?, marca_id = ?
            WHERE id = ?
            """;

    private final JdbcTemplate jdbc;

    public StockWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Qué hizo la carga. {@code conservados}: no se tocaron porque tenían un cambio más nuevo que la carga. */
    public record Resumen(int insertados, int actualizados, int borrados, int sinCambios, int conservados) {
    }

    /** Carga desde la web: el Excel manda sobre todo lo que hay. */
    public Resumen reemplazarStock(long usuarioId, List<FilaStock> filas, Instant cargadoEn,
                                   Function<FilaStock, Integer> marcaDe) {
        return reemplazarStock(usuarioId, filas, cargadoEn, cargadoEn, marcaDe);
    }

    /**
     * @param cargadoEn fecha de "stock confirmado" que queda en los ítems
     * @param corte     los ítems cambiados en el servidor después de esta hora se conservan como están
     * @param marcaDe   marca de vehículo de cada fila (null = sin marca)
     */
    public Resumen reemplazarStock(long usuarioId, List<FilaStock> filas, Instant cargadoEn, Instant corte,
                                   Function<FilaStock, Integer> marcaDe) {
        return reemplazarStock(usuarioId, filas, cargadoEn, corte, marcaDe, f -> null);
    }

    /**
     * @param uuidDe uuid para las filas nuevas (lo manda la app sin conexión, que ya las creó con ese uuid);
     *               null o uno que ya existe = se genera otro
     */
    public Resumen reemplazarStock(long usuarioId, List<FilaStock> filas, Instant cargadoEn, Instant corte,
                                   Function<FilaStock, Integer> marcaDe, Function<FilaStock, UUID> uuidDe) {
        Timestamp ts = Timestamp.from(cargadoEn);

        // Lo que hay hoy, agrupado por clave (puede haber códigos repetidos: se emparejan en orden)
        Map<String, Deque<Existente>> actuales = new HashMap<>();
        jdbc.query("""
                SELECT id, codigo_original, codigo_normalizado, descripcion, marca_repuesto, vehiculo, cantidad,
                       precio_usd, marca_id, actualizado_en
                FROM item_stock WHERE usuario_id = ? ORDER BY id
                FOR UPDATE
                """, (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
            Existente e = new Existente(rs.getLong("id"), rs.getString("codigo_original"),
                    rs.getString("descripcion"), rs.getString("marca_repuesto"), rs.getString("vehiculo"),
                    (Integer) rs.getObject("cantidad"), rs.getBigDecimal("precio_usd"),
                    (Integer) rs.getObject("marca_id"), rs.getTimestamp("actualizado_en").toInstant());
            actuales.computeIfAbsent(clave(rs.getString("codigo_normalizado"), e.marcaRepuesto),
                    k -> new ArrayDeque<>()).add(e);
        }, usuarioId);

        List<Object[]> inserts = new ArrayList<>();
        List<Object[]> updates = new ArrayList<>();
        int sinCambios = 0;
        int conservados = 0;

        for (FilaStock f : filas) {
            String normalizado = CodigoNormalizer.normalizar(f.codigo());
            Integer marcaId = marcaDe.apply(f);
            Deque<Existente> mismos = actuales.get(clave(normalizado, f.marca()));
            Existente e = mismos == null ? null : mismos.poll();
            if (e == null) {
                inserts.add(new Object[]{usuarioId, f.codigo(), normalizado, f.descripcion(), f.marca(), f.vehiculo(),
                        f.cantidad(), f.precioUsd(), ts, marcaId, uuidDe.apply(f)});
            } else if (e.actualizadoEn.isAfter(corte)) {
                conservados++;
            } else if (e.igualA(f, marcaId)) {
                sinCambios++;
            } else {
                updates.add(new Object[]{f.codigo(), normalizado, f.descripcion(), f.marca(), f.vehiculo(),
                        f.cantidad(), f.precioUsd(), ts, marcaId, e.id});
            }
        }

        usarUuidsLibres(inserts);

        // Lo que no vino en el Excel se borra (salvo que tenga un cambio más nuevo que la carga)
        List<Object[]> borrar = new ArrayList<>();
        for (Deque<Existente> quedan : actuales.values()) {
            for (Existente e : quedan) {
                if (e.actualizadoEn.isAfter(corte)) conservados++;
                else borrar.add(new Object[]{e.id});
            }
        }

        // Todo lo que está en el Excel queda como "stock confirmado hoy". No cuenta como cambio para la
        // sincronización (ver V15), así que no hace bajar de nuevo los ítems que quedaron iguales.
        jdbc.update("UPDATE item_stock SET cargado_en = ? WHERE usuario_id = ? AND actualizado_en <= ?",
                ts, usuarioId, Timestamp.from(corte));
        if (!borrar.isEmpty()) jdbc.batchUpdate("DELETE FROM item_stock WHERE id = ?", borrar);
        if (!updates.isEmpty()) batch(UPDATE, updates);
        if (!inserts.isEmpty()) batch(INSERT, inserts);
        return new Resumen(inserts.size(), updates.size(), borrar.size(), sinCambios, conservados);
    }

    /** El uuid propuesto se usa solo si no está repetido ni existe ya (ni borrado); si no, uno nuevo. */
    private void usarUuidsLibres(List<Object[]> inserts) {
        int col = 10;
        List<UUID> propuestos = new ArrayList<>();
        for (Object[] fila : inserts) {
            if (fila[col] instanceof UUID u) propuestos.add(u);
        }
        java.util.Set<UUID> ocupados = new java.util.HashSet<>();
        for (int desde = 0; desde < propuestos.size(); desde += 1000) {
            List<UUID> lote = propuestos.subList(desde, Math.min(desde + 1000, propuestos.size()));
            String marcas = String.join(",", java.util.Collections.nCopies(lote.size(), "?"));
            ocupados.addAll(jdbc.queryForList("SELECT uuid FROM item_stock WHERE uuid IN (" + marcas + ")"
                    + " UNION SELECT uuid FROM sync_borrado WHERE uuid IN (" + marcas + ")", UUID.class,
                    java.util.stream.Stream.concat(lote.stream(), lote.stream()).toArray()));
        }
        java.util.Set<UUID> usados = new java.util.HashSet<>();
        for (Object[] fila : inserts) {
            UUID u = fila[col] instanceof UUID x ? x : null;
            if (u == null || ocupados.contains(u) || !usados.add(u)) fila[col] = UUID.randomUUID();
        }
    }

    private void batch(String sql, List<Object[]> filas) {
        for (int desde = 0; desde < filas.size(); desde += 500) {
            jdbc.batchUpdate(sql, filas.subList(desde, Math.min(desde + 500, filas.size())));
        }
    }

    static String clave(String codigoNormalizado, String marcaRepuesto) {
        String marca = marcaRepuesto == null ? "" : marcaRepuesto.strip().toLowerCase(Locale.ROOT);
        return codigoNormalizado + '\u0000' + marca;
    }

    private record Existente(long id, String codigoOriginal, String descripcion, String marcaRepuesto,
                             String vehiculo, Integer cantidad, BigDecimal precioUsd, Integer marcaId,
                             Instant actualizadoEn) {

        boolean igualA(FilaStock f, Integer otraMarca) {
            return Objects.equals(codigoOriginal, f.codigo())
                    && Objects.equals(descripcion, f.descripcion())
                    && Objects.equals(marcaRepuesto, f.marca())
                    && Objects.equals(vehiculo, f.vehiculo())
                    && Objects.equals(cantidad, f.cantidad())
                    && mismoPrecio(precioUsd, f.precioUsd())
                    && Objects.equals(marcaId, otraMarca);
        }

        private static boolean mismoPrecio(BigDecimal a, BigDecimal b) {
            if (a == null || b == null) return a == b;
            return a.compareTo(b) == 0;
        }
    }
}

package com.stockautopartes.inventario;

import com.stockautopartes.marca.DetectorMarca;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Asigna la marca de vehículo a los ítems de stock ya cargados (con {@link DetectorMarca}).
 * Se usa cuando el vendedor cambia las marcas que trabaja, y al arrancar para los ítems que no tienen.
 */
@Component
public class AsignadorMarca {

    private static final Logger log = LoggerFactory.getLogger(AsignadorMarca.class);

    private final JdbcTemplate jdbc;
    private final DetectorMarca detector;

    public AsignadorMarca(JdbcTemplate jdbc, DetectorMarca detector) {
        this.jdbc = jdbc;
        this.detector = detector;
    }

    /** Marcas que el vendedor tiene guardadas en la base. */
    public Set<Integer> marcasDe(long usuarioId) {
        return new HashSet<>(jdbc.queryForList(
                "SELECT marca_id FROM usuario_marca WHERE usuario_id = ?", Integer.class, usuarioId));
    }

    /**
     * Recalcula la marca de los ítems del vendedor. Debe llamarse dentro de una transacción si
     * {@code marcasVendedor} todavía no se guardó (ej. desde la configuración).
     * @return cuántos ítems quedaron con marca.
     */
    public int recalcular(long usuarioId, Set<Integer> marcasVendedor, boolean soloSinMarca) {
        String sql = "SELECT id, vehiculo, marca_repuesto, descripcion FROM item_stock WHERE usuario_id = ?"
                + (soloSinMarca ? " AND marca_id IS NULL" : "");
        List<Object[]> cambios = jdbc.query(sql, (rs, i) -> new Object[]{
                detector.detectar(rs.getString(2), rs.getString(3), rs.getString(4), marcasVendedor),
                rs.getLong(1)}, usuarioId);
        if (soloSinMarca) cambios.removeIf(c -> c[0] == null);
        jdbc.batchUpdate("UPDATE item_stock SET marca_id = ? WHERE id = ?", cambios);
        return (int) cambios.stream().filter(c -> c[0] != null).count();
    }

    /** Al arrancar: completa la marca de los ítems que no tienen (por ejemplo, los cargados antes de esta función). */
    @EventListener(ApplicationReadyEvent.class)
    public void completarFaltantes() {
        try {
            List<Long> vendedores = jdbc.queryForList(
                    "SELECT DISTINCT usuario_id FROM item_stock WHERE marca_id IS NULL", Long.class);
            int total = 0;
            for (Long id : vendedores) {
                total += recalcular(id, marcasDe(id), true);
            }
            if (total > 0) log.info("Marca asignada a {} ítems de stock que no tenían", total);
        } catch (RuntimeException e) {
            log.warn("No se pudo completar la marca de los ítems de stock", e);
        }
    }
}

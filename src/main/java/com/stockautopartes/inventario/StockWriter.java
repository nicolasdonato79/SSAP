package com.stockautopartes.inventario;

import com.stockautopartes.importacion.FilaStock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * Reemplaza el stock completo de un vendedor con inserts en batch
 * (JPA con IDENTITY no hace batch y un Excel puede traer miles de filas).
 * Debe llamarse dentro de una transacción.
 */
@Component
public class StockWriter {

    private static final String INSERT = """
            INSERT INTO item_stock (usuario_id, codigo_original, codigo_normalizado, descripcion,
                                    marca_repuesto, vehiculo, cantidad, precio_usd, cargado_en)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbc;

    public StockWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void reemplazarStock(long usuarioId, List<FilaStock> filas, Instant cargadoEn) {
        jdbc.update("DELETE FROM item_stock WHERE usuario_id = ?", usuarioId);
        Timestamp ts = Timestamp.from(cargadoEn);
        jdbc.batchUpdate(INSERT, filas, 500, (ps, f) -> {
            ps.setLong(1, usuarioId);
            ps.setString(2, f.codigo());
            ps.setString(3, CodigoNormalizer.normalizar(f.codigo()));
            ps.setString(4, f.descripcion());
            ps.setString(5, f.marca());
            ps.setString(6, f.vehiculo());
            ps.setObject(7, f.cantidad(), java.sql.Types.INTEGER);
            ps.setObject(8, f.precioUsd(), java.sql.Types.NUMERIC);
            ps.setTimestamp(9, ts);
        });
    }
}

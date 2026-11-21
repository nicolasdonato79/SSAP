package com.stockautopartes.importacion;

import java.math.BigDecimal;
import java.util.List;

/**
 * Una fila válida del Excel de stock. El código es obligatorio.
 * {@code cantidad} null = "consultar". {@code precioUsd} null = sin precio (a consultar).
 * {@code reemplaza}: códigos anteriores que este reemplaza. {@code equivalentes}: intercambiables.
 */
public record FilaStock(String codigo,
                        String descripcion,
                        String marca,
                        String vehiculo,
                        Integer cantidad,
                        BigDecimal precioUsd,
                        List<String> reemplaza,
                        List<String> equivalentes) {

    public FilaStock(String codigo, String descripcion, String marca, String vehiculo, Integer cantidad, BigDecimal precioUsd) {
        this(codigo, descripcion, marca, vehiculo, cantidad, precioUsd, List.of(), List.of());
    }
}

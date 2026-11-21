package com.stockautopartes.importacion;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Lee números escritos a mano en un Excel: "35,50", "1.234,56", "1,234.56", "$ 40", "10 und".
 * Regla para un único separador seguido de exactamente 3 dígitos ("1.500", "2,000"): se toma como miles,
 * que es como se escribe habitualmente en Venezuela.
 */
public final class NumeroParser {

    private NumeroParser() {
    }

    public static Optional<BigDecimal> parsear(String texto) {
        if (texto == null) return Optional.empty();
        String s = texto.replaceAll("[^0-9,.\\-]", "");
        if (s.isEmpty() || s.equals("-")) return Optional.empty();

        int ultimaComa = s.lastIndexOf(',');
        int ultimoPunto = s.lastIndexOf('.');
        String normal;
        if (ultimaComa >= 0 && ultimoPunto >= 0) {
            // El que aparece último es el decimal
            char decimal = ultimaComa > ultimoPunto ? ',' : '.';
            char miles = decimal == ',' ? '.' : ',';
            normal = s.replace(String.valueOf(miles), "").replace(decimal, '.');
        } else if (ultimaComa >= 0 || ultimoPunto >= 0) {
            char sep = ultimaComa >= 0 ? ',' : '.';
            long cantidad = s.chars().filter(ch -> ch == sep).count();
            int digitosDespues = s.length() - s.lastIndexOf(sep) - 1;
            if (cantidad > 1 || digitosDespues == 3) {
                normal = s.replace(String.valueOf(sep), "");
            } else {
                normal = s.replace(sep, '.');
            }
        } else {
            normal = s;
        }
        try {
            return Optional.of(new BigDecimal(normal));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** Entero no negativo, o vacío si no se puede leer como tal. */
    public static Optional<Integer> parsearCantidad(String texto) {
        return parsear(texto).flatMap(n -> {
            try {
                int v = n.stripTrailingZeros().intValueExact();
                return v >= 0 ? Optional.of(v) : Optional.empty();
            } catch (ArithmeticException e) {
                return Optional.empty();
            }
        });
    }
}

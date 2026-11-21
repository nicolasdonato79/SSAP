package com.stockautopartes.inventario;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Deja los códigos de repuesto en una forma comparable:
 * "04465-02220", "0446502220" y "04465 02220" terminan iguales.
 */
public final class CodigoNormalizer {

    private CodigoNormalizer() {
    }

    public static String normalizar(String codigo) {
        if (codigo == null) {
            return "";
        }
        String sinAcentos = Normalizer.normalize(codigo, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sinAcentos.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }
}

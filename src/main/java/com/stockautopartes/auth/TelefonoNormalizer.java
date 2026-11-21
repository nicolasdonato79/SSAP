package com.stockautopartes.auth;

import java.util.Optional;

/**
 * Lleva un teléfono al formato internacional +58XXXXXXXXXX.
 * Acepta "0412-1234567", "412 1234567", "+58 412 1234567", "58 4121234567".
 * Un número que empieza con "+" y otro código de país se acepta tal cual (solo dígitos).
 */
public final class TelefonoNormalizer {

    private TelefonoNormalizer() {
    }

    public static Optional<String> normalizar(String entrada) {
        if (entrada == null || entrada.isBlank()) {
            return Optional.empty();
        }
        String limpio = entrada.trim();
        boolean conMas = limpio.startsWith("+");
        String digitos = limpio.replaceAll("\\D", "");

        if (digitos.startsWith("58") && digitos.length() == 12) {
            return Optional.of("+" + digitos);
        }
        if (!conMas && digitos.startsWith("0") && digitos.length() == 11) {
            return Optional.of("+58" + digitos.substring(1));
        }
        if (!conMas && digitos.length() == 10 && (digitos.startsWith("4") || digitos.startsWith("2"))) {
            return Optional.of("+58" + digitos);
        }
        if (conMas && digitos.length() >= 10 && digitos.length() <= 15) {
            return Optional.of("+" + digitos);
        }
        return Optional.empty();
    }
}

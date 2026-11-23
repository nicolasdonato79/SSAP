package com.stockautopartes.web;

import org.springframework.stereotype.Component;

/**
 * Datos que no se muestran a quien no entró: así no se puede contactar al vendedor por fuera del sistema.
 * En las vistas: {@code @privacidad.ocultar(nombre)}.
 */
@Component("privacidad")
public class Privacidad {

    /** Asteriscos fijos (no dejan adivinar el largo del nombre). */
    private static final String TAPADO = "******";

    /** "Repuestos Pepe" → "******pe". Solo quedan las dos últimas letras. */
    public String ocultar(String nombre) {
        if (nombre == null || nombre.isBlank()) return TAPADO;
        String s = nombre.strip();
        return s.length() <= 2 ? TAPADO : TAPADO + s.substring(s.length() - 2);
    }
}

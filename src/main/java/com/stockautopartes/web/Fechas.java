package com.stockautopartes.web;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Formato de fechas para las vistas, en hora de Venezuela. Uso: ${@fechas.formatear(x)} */
@Component("fechas")
public class Fechas {

    private static final DateTimeFormatter FORMATO =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("America/Caracas"));

    public String formatear(Instant instante) {
        return instante == null ? "" : FORMATO.format(instante);
    }
}

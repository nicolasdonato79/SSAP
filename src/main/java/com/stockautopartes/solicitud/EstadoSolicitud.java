package com.stockautopartes.solicitud;

/**
 * PENDIENTE    → el vendedor responde: OFERTADA (tiene stock, con precio) o RECHAZADA (no lo tiene)
 * OFERTADA     → el comprador: ACEPTADA, CONTRAOFERTA o CANCELADA
 * CONTRAOFERTA → el vendedor: ACEPTADA o RECHAZADA
 */
public enum EstadoSolicitud {
    PENDIENTE,
    OFERTADA,
    CONTRAOFERTA,
    ACEPTADA,
    RECHAZADA,
    CANCELADA;

    public boolean esFinal() {
        return this == ACEPTADA || this == RECHAZADA || this == CANCELADA;
    }
}

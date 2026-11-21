package com.stockautopartes.solicitud;

/** Error de negocio que se le muestra tal cual al usuario. */
public class SolicitudException extends RuntimeException {

    public SolicitudException(String mensaje) {
        super(mensaje);
    }
}

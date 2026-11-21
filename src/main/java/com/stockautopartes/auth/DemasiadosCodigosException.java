package com.stockautopartes.auth;

public class DemasiadosCodigosException extends RuntimeException {

    public DemasiadosCodigosException() {
        super("Pediste demasiados códigos. Esperá unos minutos y probá de nuevo.");
    }
}

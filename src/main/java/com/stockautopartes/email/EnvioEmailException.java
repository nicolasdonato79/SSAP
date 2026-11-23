package com.stockautopartes.email;

public class EnvioEmailException extends RuntimeException {

    public EnvioEmailException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}

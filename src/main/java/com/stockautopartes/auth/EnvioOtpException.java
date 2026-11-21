package com.stockautopartes.auth;

public class EnvioOtpException extends RuntimeException {

    public EnvioOtpException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}

package com.stockautopartes.auth;

/** Manda el código de acceso al teléfono del usuario. */
public interface EnviadorOtp {

    /** @throws EnvioOtpException si no se pudo mandar */
    void enviar(String telefono, String codigo);

    /** true si el código no sale del servidor (desarrollo): se puede mostrar en pantalla. */
    default boolean esDePrueba() {
        return false;
    }
}

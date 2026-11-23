package com.stockautopartes.email;

/** Manda emails (activación de cuenta, recuperar contraseña). */
public interface EnviadorEmail {

    /** @throws EnvioEmailException si no se pudo mandar */
    void enviar(String para, String asunto, String texto);

    /** true si los emails no salen del servidor (modo prueba): el link se puede mostrar en pantalla. */
    default boolean esDePrueba() {
        return false;
    }
}

package com.stockautopartes.registro;

import java.util.List;

/** Error del alta que se le muestra al usuario. Deshace lo que se haya guardado en ese paso. */
public class RegistroException extends RuntimeException {

    private final List<String> errores;

    public RegistroException(String mensaje) {
        this(List.of(mensaje));
    }

    public RegistroException(List<String> errores) {
        super(String.join(" ", errores));
        this.errores = errores;
    }

    public List<String> getErrores() {
        return errores;
    }
}

package com.stockautopartes.usuario;

/** El usuario entró pero todavía no completó teléfono, estado y ciudad. */
public class UsuarioNoRegistradoException extends RuntimeException {

    public UsuarioNoRegistradoException() {
        super("Falta completar el perfil");
    }
}

package com.stockautopartes.usuario;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Único punto que sabe quién es el usuario logueado (el "username" de la sesión). */
@Component
public class UsuarioActual {

    private final UsuarioRepository usuarios;

    public UsuarioActual(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    /** Usuario de la sesión, si hay alguien logueado. */
    public Optional<String> username() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        return Optional.of(auth.getName());
    }

    /** El usuario logueado, tenga o no el perfil completo. */
    public Optional<Usuario> buscar() {
        return username().flatMap(usuarios::findByUsernameIgnoreCase);
    }

    /** El usuario logueado con perfil completo; si le falta teléfono, estado o ciudad, se lo manda a /perfil. */
    public Usuario obtener() {
        return buscar().filter(Usuario::isPerfilCompleto).orElseThrow(UsuarioNoRegistradoException::new);
    }
}

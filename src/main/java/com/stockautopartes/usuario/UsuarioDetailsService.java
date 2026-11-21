package com.stockautopartes.usuario;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** Spring Security busca acá al usuario cuando alguien entra con usuario y contraseña. */
@Service
public class UsuarioDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarios;

    public UsuarioDetailsService(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        Usuario u = usuarios.findByUsernameIgnoreCase(username.trim())
                .filter(x -> x.getPasswordHash() != null)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));
        return User.withUsername(u.getUsername())
                .password(u.getPasswordHash())
                .roles(u.getRol().name())
                .disabled(!u.isActivo())
                .build();
    }
}

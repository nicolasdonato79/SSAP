package com.stockautopartes.sync;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Para /api/**: lee "Authorization: Bearer &lt;token&gt;" y, si el token del equipo vale, deja logueado al
 * usuario solo para esta llamada (sin sesión). Así UsuarioActual y los servicios de siempre funcionan igual.
 * El equipo queda en el atributo {@link #ATRIBUTO_SESION}.
 * <p>
 * No es un @Component a propósito: si lo fuera, Spring Boot lo registraría también para toda la web.
 */
public class TokenDispositivoFilter extends OncePerRequestFilter {

    public static final String ATRIBUTO_SESION = "sync.sesion";

    private final DispositivoService dispositivos;

    public TokenDispositivoFilter(DispositivoService dispositivos) {
        this.dispositivos = dispositivos;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            dispositivos.autenticar(header.substring(7)).ifPresent(s -> {
                var auth = new UsernamePasswordAuthenticationToken(s.username(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + s.rol())));
                SecurityContext ctx = SecurityContextHolder.createEmptyContext();
                ctx.setAuthentication(auth);
                SecurityContextHolder.setContext(ctx);
                req.setAttribute(ATRIBUTO_SESION, s);
            });
        }
        chain.doFilter(req, res);
    }
}

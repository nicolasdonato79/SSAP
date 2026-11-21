package com.stockautopartes.config;

import com.stockautopartes.usuario.Usuario;
import com.stockautopartes.usuario.UsuarioRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.RequestCache;

import java.io.IOException;

/**
 * Después de cada login:
 * - Perfil incompleto (falta teléfono, estado o ciudad): va al inicio y se marca la sesión
 *   para mostrar el aviso una vez (ver HomeController).
 * - Perfil completo: sigue a la página que había pedido, o al inicio.
 */
public class LoginExitosoHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    public static final String AVISO_PERFIL = "avisoPerfil";

    private final UsuarioRepository usuarios;
    private final RequestCache requestCache;

    public LoginExitosoHandler(UsuarioRepository usuarios, RequestCache requestCache) {
        this.usuarios = usuarios;
        this.requestCache = requestCache;
        setRequestCache(requestCache);
        setDefaultTargetUrl("/");
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        boolean completo = usuarios.findByUsernameIgnoreCase(authentication.getName())
                .map(Usuario::isPerfilCompleto)
                .orElse(true);
        if (!completo) {
            request.getSession().setAttribute(AVISO_PERFIL, Boolean.TRUE);
            requestCache.removeRequest(request, response);
            clearAuthenticationAttributes(request);
            getRedirectStrategy().sendRedirect(request, response, "/");
            return;
        }
        super.onAuthenticationSuccess(request, response, authentication);
    }
}

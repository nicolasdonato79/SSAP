package com.stockautopartes.config;

import com.stockautopartes.usuario.UsuarioRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;

import java.time.Clock;

/**
 * Login con usuario y contraseña (formulario estándar de Spring Security) y sesión del servidor.
 * Los usuarios salen de la tabla usuario (UsuarioDetailsService). /admin/** solo para ADMIN.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http,
                                    SecurityContextRepository contextRepository,
                                    RequestCache requestCache,
                                    UsuarioRepository usuarios) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/buscar", "/ciudades", "/plantilla.xlsx", "/login", "/error", "/favicon.ico", "/css/**", "/img/**",
                                 "/registro", "/registro/**", "/recuperar", "/recuperar/**").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .securityContext(ctx -> ctx.securityContextRepository(contextRepository))
            .requestCache(cache -> cache.requestCache(requestCache))
            .exceptionHandling(ex -> ex.authenticationEntryPoint((req, res, e) -> {
                if (req.getHeader("HX-Request") != null) {
                    // HTMX no sigue bien un 302 a otra página: le pedimos que navegue al login
                    res.setHeader("HX-Redirect", "/login");
                    res.setStatus(401);
                } else {
                    res.sendRedirect("/login");
                }
            }))
            .formLogin(form -> form
                .loginPage("/login")
                // perfil incompleto: al inicio con aviso; completo: a la página que pidió, o al inicio
                .successHandler(new LoginExitosoHandler(usuarios, requestCache))
                // Distingue: registro sin verificar, cuenta desactivada, o datos incorrectos
                .failureHandler((req, res, ex) -> res.sendRedirect(
                        ex instanceof LockedException ? "/login?pendiente"
                                : ex instanceof DisabledException ? "/login?desactivado"
                                : "/login?error"))
                .permitAll())
            .httpBasic(basic -> basic.disable())
            .logout(logout -> logout.logoutUrl("/logout").logoutSuccessUrl("/login?logout"));
        return http.build();
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /** Recuerda la página que pidió el usuario antes del login (solo GET normales, no fragmentos HTMX). */
    @Bean
    RequestCache requestCache() {
        HttpSessionRequestCache cache = new HttpSessionRequestCache();
        cache.setRequestMatcher(req -> "GET".equals(req.getMethod()) && req.getHeader("HX-Request") == null);
        return cache;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}

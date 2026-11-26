package com.stockautopartes.config;

import com.stockautopartes.sync.DispositivoService;
import com.stockautopartes.sync.TokenDispositivoFilter;
import com.stockautopartes.usuario.UsuarioRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
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

    /**
     * API de la app sin conexión (issue #9): sin sesión ni cookies, con el token del equipo en
     * "Authorization: Bearer". Como no usa cookies no hace falta CSRF. Sin token válido: 401 en JSON.
     */
    @Bean
    @Order(1)
    SecurityFilterChain apiFilterChain(HttpSecurity http, DispositivoService dispositivos) throws Exception {
        http
            .securityMatcher("/api/**")
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/dispositivo", "/api/sync/ping").permitAll()
                .anyRequest().authenticated())
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .requestCache(cache -> cache.disable())
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            .logout(logout -> logout.disable())
            .addFilterBefore(new TokenDispositivoFilter(dispositivos), UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(ex -> ex.authenticationEntryPoint((req, res, e) -> {
                res.setStatus(401);
                res.setContentType("application/json;charset=UTF-8");
                res.getWriter().write("{\"error\":\"SIN_SESION\",\"mensaje\":\"Tenés que volver a entrar con tu usuario y contraseña.\"}");
            }));
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain filterChain(HttpSecurity http,
                                    SecurityContextRepository contextRepository,
                                    RequestCache requestCache,
                                    UsuarioRepository usuarios) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/buscar", "/ciudades", "/plantilla.xlsx", "/login", "/error", "/favicon.ico", "/css/**", "/img/**",
                                 "/registro", "/registro/**", "/recuperar", "/recuperar/**",
                                 "/app", "/app/**").permitAll()   // app sin conexión (PWA): archivos estáticos
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

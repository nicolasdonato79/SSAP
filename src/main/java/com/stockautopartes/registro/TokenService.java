package com.stockautopartes.registro;

import com.stockautopartes.usuario.Usuario;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Tokens de un solo uso para links por email. El token (32 bytes al azar) viaja solo en el link;
 * en la base queda su SHA-256, así que una copia de la base no sirve para usar los links.
 */
@Service
public class TokenService {

    /** Máximo de links del mismo tipo por usuario y por hora (evita llenar la casilla de alguien). */
    static final int MAX_POR_HORA = 3;

    private final TokenUsuarioRepository tokens;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public TokenService(TokenUsuarioRepository tokens, Clock clock) {
        this.tokens = tokens;
        this.clock = clock;
    }

    /** @return el token para poner en el link, o vacío si ya se mandaron demasiados en la última hora. */
    @Transactional
    public Optional<String> crear(Usuario usuario, TokenUsuario.Tipo tipo, Duration vigencia) {
        Instant ahora = clock.instant();
        if (tokens.countByUsuario_IdAndTipoAndCreadoEnAfter(usuario.getId(), tipo, ahora.minus(Duration.ofHours(1)))
                >= MAX_POR_HORA) {
            return Optional.empty();
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.save(new TokenUsuario(usuario, tipo, hash(token), ahora.plus(vigencia)));
        return Optional.of(token);
    }

    /** Si el token es válido y está vigente, lo marca usado y devuelve su usuario. */
    @Transactional
    public Optional<Usuario> consumir(String token, TokenUsuario.Tipo tipo) {
        if (token == null || token.isBlank() || token.length() > 100) {
            return Optional.empty();
        }
        Instant ahora = clock.instant();
        return tokens.findByTokenHashAndTipo(hash(token), tipo)
                .filter(t -> t.vigente(ahora))
                .map(t -> {
                    t.usar(ahora);
                    return t.getUsuario();
                });
    }

    /** Sin marcarlo usado: para mostrar el formulario de contraseña nueva antes de enviarlo. */
    @Transactional(readOnly = true)
    public boolean esValido(String token, TokenUsuario.Tipo tipo) {
        if (token == null || token.isBlank() || token.length() > 100) return false;
        return tokens.findByTokenHashAndTipo(hash(token), tipo).filter(t -> t.vigente(clock.instant())).isPresent();
    }

    @Scheduled(cron = "0 30 4 * * *")
    @Transactional
    public void limpiarVencidos() {
        tokens.deleteByExpiraEnBefore(clock.instant().minus(Duration.ofDays(2)));
    }

    static String hash(String token) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

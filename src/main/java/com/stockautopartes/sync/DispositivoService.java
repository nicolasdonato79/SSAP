package com.stockautopartes.sync;

import com.stockautopartes.usuario.Usuario;
import com.stockautopartes.usuario.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Login de los equipos (celular, PC del local) para la app sin conexión.
 * <p>
 * El primer login es con conexión: usuario (o email) y contraseña. El servidor devuelve un token largo y
 * al azar, atado a ese equipo, y guarda solo su hash SHA-256 (como token_usuario). El token se renueva solo
 * con el uso: vence a los {@code dias-token} días sin usarse. Revocarlo (cerrar sesión, desactivar al
 * usuario) corta el acceso en la próxima llamada: no hace falta esperar a que venza.
 * <p>
 * Es un token opaco y no un JWT a propósito: cada llamada lo busca en la base (con índice), así que
 * revocar es inmediato y no hace falta otra librería.
 */
@Service
public class DispositivoService {

    private static final SecureRandom RANDOM = new SecureRandom();
    /** No escribir en la base en cada llamada: la fecha de uso se actualiza como mucho una vez por hora. */
    private static final Duration ACTUALIZAR_USO_CADA = Duration.ofHours(1);
    private static final int MAX_FALLIDOS = 10;
    private static final Duration VENTANA_FALLIDOS = Duration.ofMinutes(15);

    private final JdbcTemplate jdbc;
    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final Duration vigencia;
    private final ConcurrentHashMap<String, Fallidos> fallidos = new ConcurrentHashMap<>();

    public DispositivoService(JdbcTemplate jdbc, UsuarioRepository usuarios, PasswordEncoder passwordEncoder,
                              Clock clock, @Value("${app.sync.dias-token:30}") int diasToken) {
        this.jdbc = jdbc;
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.vigencia = Duration.ofDays(diasToken);
    }

    /** Quién llama a la API: el usuario y el equipo. */
    public record Sesion(long usuarioId, String username, String rol, UUID dispositivoId) {
    }

    public record Alta(String token, Usuario usuario) {
    }

    /** Problema con el login: el mensaje se muestra tal cual en el equipo. */
    public static class LoginException extends RuntimeException {
        public LoginException(String mensaje) {
            super(mensaje);
        }
    }

    /**
     * Login del equipo. Si el equipo ya tenía sesión (de este u otro usuario), se reemplaza.
     * Mismos mensajes que la web: no revela si el usuario existe.
     */
    @Transactional
    public Alta registrar(String usuario, String password, UUID dispositivoId, String nombreEquipo) {
        String dato = usuario == null ? "" : usuario.strip();
        if (dato.isEmpty() || password == null || password.isEmpty() || dispositivoId == null) {
            throw new LoginException("Poné tu usuario y tu contraseña.");
        }
        String claveIntentos = dato.toLowerCase();
        if (bloqueado(claveIntentos)) {
            throw new LoginException("Demasiados intentos. Probá de nuevo en 15 minutos.");
        }
        Usuario u = usuarios.findByUsernameIgnoreCase(dato)
                .or(() -> dato.contains("@") ? usuarios.findByEmailIgnoreCase(dato) : Optional.empty())
                .filter(x -> x.getPasswordHash() != null && passwordEncoder.matches(password, x.getPasswordHash()))
                .orElse(null);
        if (u == null) {
            sumarFallido(claveIntentos);
            throw new LoginException("Usuario o contraseña incorrectos.");
        }
        if (u.isPendienteVerificacion()) {
            throw new LoginException("Todavía no confirmaste tu cuenta: abrí el link que te mandamos por email.");
        }
        if (!u.isActivo()) {
            throw new LoginException("Tu cuenta está desactivada. Escribile al administrador.");
        }
        fallidos.remove(claveIntentos);

        String token = nuevoToken();
        Instant ahora = clock.instant();
        String nombre = nombreEquipo == null ? null
                : nombreEquipo.strip().substring(0, Math.min(100, nombreEquipo.strip().length()));
        jdbc.update("""
                INSERT INTO dispositivo (id, usuario_id, nombre, token_hash, creado_en, ultimo_uso, expira_en)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET usuario_id = EXCLUDED.usuario_id, nombre = EXCLUDED.nombre,
                    token_hash = EXCLUDED.token_hash, creado_en = EXCLUDED.creado_en,
                    ultimo_uso = EXCLUDED.ultimo_uso, expira_en = EXCLUDED.expira_en, revocado_en = NULL
                """, dispositivoId, u.getId(), nombre, hash(token), Timestamp.from(ahora), Timestamp.from(ahora),
                Timestamp.from(ahora.plus(vigencia)));
        return new Alta(token, u);
    }

    /** El usuario y el equipo dueños del token, si el token sigue valiendo y el usuario puede entrar. */
    @Transactional
    public Optional<Sesion> autenticar(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        Instant ahora = clock.instant();
        List<Object[]> filas = jdbc.query("""
                SELECT d.id, u.id, u.username, u.rol, d.ultimo_uso
                FROM dispositivo d JOIN usuario u ON u.id = d.usuario_id
                WHERE d.token_hash = ? AND d.revocado_en IS NULL AND d.expira_en > ?
                  AND u.activo AND NOT u.pendiente_verificacion
                """, (rs, n) -> new Object[]{rs.getObject(1, UUID.class), rs.getLong(2), rs.getString(3),
                rs.getString(4), rs.getTimestamp(5).toInstant()}, hash(token.strip()), Timestamp.from(ahora));
        if (filas.isEmpty()) return Optional.empty();
        Object[] f = filas.get(0);
        UUID dispositivoId = (UUID) f[0];
        if (((Instant) f[4]).isBefore(ahora.minus(ACTUALIZAR_USO_CADA))) {
            jdbc.update("UPDATE dispositivo SET ultimo_uso = ?, expira_en = ? WHERE id = ?",
                    Timestamp.from(ahora), Timestamp.from(ahora.plus(vigencia)), dispositivoId);
        }
        return Optional.of(new Sesion((Long) f[1], (String) f[2], (String) f[3], dispositivoId));
    }

    /** Cerrar sesión en este equipo. */
    @Transactional
    public void revocar(UUID dispositivoId, long usuarioId) {
        jdbc.update("UPDATE dispositivo SET revocado_en = ? WHERE id = ? AND usuario_id = ? AND revocado_en IS NULL",
                Timestamp.from(clock.instant()), dispositivoId, usuarioId);
    }

    /** Cerrar sesión en todos los equipos de un usuario (ej. al desactivarlo o cambiarle la contraseña). */
    @Transactional
    public void revocarTodos(long usuarioId) {
        jdbc.update("UPDATE dispositivo SET revocado_en = ? WHERE usuario_id = ? AND revocado_en IS NULL",
                Timestamp.from(clock.instant()), usuarioId);
    }

    // ---------- Apoyo ----------

    private record Fallidos(int cantidad, Instant desde) {
    }

    private boolean bloqueado(String clave) {
        Fallidos f = fallidos.get(clave);
        if (f == null) return false;
        if (f.desde.isBefore(clock.instant().minus(VENTANA_FALLIDOS))) {
            fallidos.remove(clave);
            return false;
        }
        return f.cantidad >= MAX_FALLIDOS;
    }

    private void sumarFallido(String clave) {
        Instant ahora = clock.instant();
        fallidos.compute(clave, (k, f) -> f == null || f.desde.isBefore(ahora.minus(VENTANA_FALLIDOS))
                ? new Fallidos(1, ahora) : new Fallidos(f.cantidad + 1, f.desde));
        if (fallidos.size() > 10_000) fallidos.clear(); // no crecer sin límite
    }

    private static String nuevoToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
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

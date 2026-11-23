package com.stockautopartes.registro;

import com.stockautopartes.auth.DemasiadosCodigosException;
import com.stockautopartes.auth.EnvioOtpException;
import com.stockautopartes.auth.OtpService;
import com.stockautopartes.auth.TelefonoNormalizer;
import com.stockautopartes.email.EnviadorEmail;
import com.stockautopartes.email.EnvioEmailException;
import com.stockautopartes.usuario.Usuario;
import com.stockautopartes.usuario.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.*;

/**
 * Alta automática (issue #1) y recuperación de contraseña.
 *
 *  1. registrar: crea el usuario "pendiente" y manda un código por SMS al teléfono.
 *  2. verificarTelefono + reenviarLinkActivacion: con el código correcto, manda el link de activación al email.
 *  3. confirmarEmail: con el link, la cuenta queda activa y ya puede entrar.
 *
 * Con app.registro.verificar-telefono=false se saltea el paso 2: registrar manda directamente el link por email
 * y la cuenta se activa al abrirlo (el teléfono queda sin verificar, para una etapa posterior).
 *
 * Un registro pendiente se puede pisar volviendo a registrarse con el mismo usuario, teléfono o email
 * (por si se equivocó o no le llegó nada), y se borra solo a las 48 horas.
 */
@Service
public class AutoregistroService {

    private static final Logger log = LoggerFactory.getLogger(AutoregistroService.class);

    public static final int MIN_PASSWORD = 8;
    private static final String PATRON_USUARIO = "[a-zA-Z0-9._-]{3,50}";
    private static final String PATRON_EMAIL = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";
    private static final Duration VIGENCIA_ACTIVACION = Duration.ofHours(24);
    private static final Duration VIGENCIA_RECUPERACION = Duration.ofHours(1);
    private static final Duration PLAZO_PENDIENTES = Duration.ofHours(48);

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final OtpService otp;
    private final TokenService tokens;
    private final EnviadorEmail email;
    private final Clock clock;
    private final String urlPublica;
    private final boolean mostrarEnPantalla;
    private final boolean verificarTelefono;

    public AutoregistroService(UsuarioRepository usuarios, PasswordEncoder encoder, OtpService otp,
                               TokenService tokens, EnviadorEmail email, Clock clock,
                               @Value("${app.url-publica}") String urlPublica,
                               @Value("${app.otp.mostrar-en-pantalla}") boolean mostrarEnPantalla,
                               @Value("${app.registro.verificar-telefono:false}") boolean verificarTelefono) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.otp = otp;
        this.tokens = tokens;
        this.email = email;
        this.clock = clock;
        this.urlPublica = urlPublica.endsWith("/") ? urlPublica.substring(0, urlPublica.length() - 1) : urlPublica;
        this.mostrarEnPantalla = mostrarEnPantalla;
        this.verificarTelefono = verificarTelefono;
    }

    /** true si el alta pide verificar el teléfono por SMS además del email. */
    public boolean exigeTelefono() {
        return verificarTelefono;
    }

    /** Resultado de un paso: el usuario y, solo en modo prueba, el código o link para mostrar en pantalla. */
    public record Paso(Usuario usuario, String paraMostrar) {
    }

    // ---------- 1. Crear cuenta ----------

    @Transactional
    public Paso registrar(RegistroForm f) {
        List<String> errores = new ArrayList<>();
        String nombre = f.getNombre().trim();
        String username = f.getUsername().trim();
        String mail = f.getEmail().trim().toLowerCase(Locale.ROOT);
        Optional<String> telefono = TelefonoNormalizer.normalizar(f.getTelefono());

        if (nombre.length() < 2 || nombre.length() > 150) errores.add("Poné tu nombre o el de tu negocio.");
        if (!username.matches(PATRON_USUARIO))
            errores.add("El usuario tiene que tener entre 3 y 50 letras, números, punto, guion o guion bajo.");
        if (telefono.isEmpty())
            errores.add("El teléfono no parece válido. Ej: 0412-1234567, o +54 9 11 1234-5678 si no es de Venezuela.");
        if (mail.length() > 150 || !mail.matches(PATRON_EMAIL)) errores.add("El email no parece válido.");
        if (f.getPassword().length() < MIN_PASSWORD)
            errores.add("La contraseña tiene que tener al menos " + MIN_PASSWORD + " caracteres.");
        else if (!f.getPassword().equals(f.getRepetida())) errores.add("Las contraseñas no coinciden.");
        if (!errores.isEmpty()) throw new RegistroException(errores);

        // Lo que ya existe: si es otro registro pendiente se pisa; si es una cuenta activa, no se puede usar.
        Set<Usuario> pendientesAPisar = new HashSet<>();
        revisar(usuarios.findByUsernameIgnoreCase(username), "Ese usuario ya existe. Elegí otro.", pendientesAPisar, errores);
        revisar(usuarios.findByTelefono(telefono.get()),
                "Ese teléfono ya tiene una cuenta. Si es tuya, entrá o recuperá la contraseña.", pendientesAPisar, errores);
        revisar(usuarios.findByEmailIgnoreCase(mail),
                "Ese email ya tiene una cuenta. Si es tuya, entrá o recuperá la contraseña.", pendientesAPisar, errores);
        if (!errores.isEmpty()) throw new RegistroException(errores);
        if (!pendientesAPisar.isEmpty()) {
            usuarios.deleteAll(pendientesAPisar);
            usuarios.flush();
        }

        Usuario u = usuarios.save(Usuario.autoregistro(username.toLowerCase(Locale.ROOT),
                encoder.encode(f.getPassword()), nombre, telefono.get(), mail));
        return new Paso(u, verificarTelefono ? mandarCodigo(u) : mandarLinkActivacion(u));
    }

    private static void revisar(Optional<Usuario> existente, String mensaje, Set<Usuario> aPisar, List<String> errores) {
        existente.ifPresent(e -> {
            if (e.isPendienteVerificacion()) aPisar.add(e);
            else errores.add(mensaje);
        });
    }

    // ---------- 2. Teléfono ----------

    @Transactional
    public Paso reenviarCodigo(long usuarioId) {
        Usuario u = pendiente(usuarioId);
        return new Paso(u, mandarCodigo(u));
    }

    /**
     * Con el código correcto marca el teléfono como verificado. El link por email se manda aparte
     * (reenviarLinkActivacion), así un problema con el email no obliga a pedir otro código.
     */
    @Transactional
    public Usuario verificarTelefono(long usuarioId, String codigo) {
        Usuario u = pendiente(usuarioId);
        if (u.isTelefonoVerificado()) return u;
        if (!otp.verificar(u.getTelefono(), codigo == null ? "" : codigo.trim())) {
            throw new RegistroException("El código no es correcto o ya venció. Pedí uno nuevo si hace falta.");
        }
        u.marcarTelefonoVerificado();
        return u;
    }

    private String mandarCodigo(Usuario u) {
        try {
            return otp.solicitar(u.getTelefono()).orElse(null);
        } catch (DemasiadosCodigosException e) {
            throw new RegistroException(e.getMessage());
        } catch (EnvioOtpException e) {
            throw new RegistroException("No pudimos mandar el SMS a ese número. Revisalo e intentá de nuevo.");
        }
    }

    // ---------- 3. Email ----------

    @Transactional
    public Paso reenviarLinkActivacion(long usuarioId) {
        Usuario u = pendiente(usuarioId);
        if (verificarTelefono && !u.isTelefonoVerificado()) throw new RegistroException("Primero verificá el teléfono.");
        return new Paso(u, mandarLinkActivacion(u));
    }

    /** @return el usuario activado, o vacío si el link no es válido o venció. */
    @Transactional
    public Optional<Usuario> confirmarEmail(String token) {
        return tokens.consumir(token, TokenUsuario.Tipo.VERIFICAR_EMAIL).map(u -> {
            u.marcarEmailVerificado();
            if (!verificarTelefono) u.activarConEmailVerificado();
            return u;
        });
    }

    private String mandarLinkActivacion(Usuario u) {
        String token = tokens.crear(u, TokenUsuario.Tipo.VERIFICAR_EMAIL, VIGENCIA_ACTIVACION)
                .orElseThrow(() -> new RegistroException(
                        "Ya te mandamos varios emails en la última hora. Revisá también la carpeta de spam."));
        String link = urlPublica + "/registro/confirmar?token=" + token;
        mandarEmail(u.getEmail(), "Activá tu cuenta de Stock Multimarca",
                "Hola " + u.getNombre() + ":\n\n"
                        + "Para activar tu cuenta de Stock Multimarca, entrá a este link:\n" + link + "\n\n"
                        + "El link vence en 24 horas. Si no creaste una cuenta, ignorá este email.\n\n"
                        + "Stock Multimarca");
        return mostrar() ? link : null;
    }

    // ---------- Recuperar contraseña ----------

    /**
     * Manda el link para elegir una contraseña nueva. Por seguridad la pantalla dice siempre lo mismo,
     * exista o no la cuenta. @return el link solo en modo prueba.
     */
    @Transactional
    public Optional<String> pedirRecuperacion(String usuarioOEmail) {
        String dato = usuarioOEmail == null ? "" : usuarioOEmail.trim();
        if (dato.isEmpty()) return Optional.empty();
        Optional<Usuario> encontrado = usuarios.findByUsernameIgnoreCase(dato)
                .or(() -> usuarios.findByEmailIgnoreCase(dato))
                .filter(u -> u.isActivo() && !u.isPendienteVerificacion() && u.getEmail() != null);
        if (encontrado.isEmpty()) {
            log.info("Recuperación pedida para '{}': no hay cuenta activa con email", dato);
            return Optional.empty();
        }
        Usuario u = encontrado.get();
        Optional<String> token = tokens.crear(u, TokenUsuario.Tipo.RECUPERAR_PASSWORD, VIGENCIA_RECUPERACION);
        if (token.isEmpty()) return Optional.empty(); // demasiados pedidos: no se avisa para no dar pistas
        String link = urlPublica + "/recuperar/nueva?token=" + token.get();
        mandarEmail(u.getEmail(), "Elegí una contraseña nueva",
                "Hola " + u.getNombre() + ":\n\n"
                        + "Pediste cambiar la contraseña de tu cuenta de Stock Multimarca (usuario: " + u.getUsername() + ").\n"
                        + "Para elegir una nueva, entrá a este link:\n" + link + "\n\n"
                        + "El link vence en 1 hora y sirve una sola vez. Si no lo pediste, ignorá este email: "
                        + "tu contraseña no cambia.\n\nStock Multimarca");
        return mostrar() ? Optional.of(link) : Optional.empty();
    }

    public boolean linkDeRecuperacionValido(String token) {
        return tokens.esValido(token, TokenUsuario.Tipo.RECUPERAR_PASSWORD);
    }

    /** @return error para mostrar, o vacío si la contraseña quedó cambiada. */
    @Transactional
    public Optional<String> cambiarPassword(String token, String nueva, String repetida) {
        if (nueva == null || nueva.length() < MIN_PASSWORD) {
            return Optional.of("La contraseña tiene que tener al menos " + MIN_PASSWORD + " caracteres.");
        }
        if (!nueva.equals(repetida)) return Optional.of("Las contraseñas no coinciden.");
        Optional<Usuario> u = tokens.consumir(token, TokenUsuario.Tipo.RECUPERAR_PASSWORD);
        if (u.isEmpty()) return Optional.of("El link venció o ya se usó. Pedí uno nuevo.");
        u.get().cambiarPassword(encoder.encode(nueva));
        u.get().marcarEmailVerificado(); // abrió el link: el email es suyo
        return Optional.empty();
    }

    // ---------- Apoyo ----------

    private Usuario pendiente(long usuarioId) {
        return usuarios.findById(usuarioId)
                .filter(Usuario::isPendienteVerificacion)
                .orElseThrow(() -> new RegistroException("Ese registro ya no está pendiente. Entrá con tu usuario."));
    }

    private void mandarEmail(String para, String asunto, String texto) {
        try {
            email.enviar(para, asunto, texto);
        } catch (EnvioEmailException e) {
            throw new RegistroException("No pudimos mandar el email. Revisá la dirección e intentá de nuevo.");
        }
    }

    private boolean mostrar() {
        return email.esDePrueba() && mostrarEnPantalla;
    }

    /** Borra los registros que no se terminaron de verificar en 48 horas. */
    @Scheduled(cron = "0 15 * * * *")
    @Transactional
    public void limpiarPendientes() {
        List<Usuario> viejos = usuarios.findByPendienteVerificacionTrueAndCreadoEnBefore(
                clock.instant().minus(PLAZO_PENDIENTES));
        if (!viejos.isEmpty()) {
            usuarios.deleteAll(viejos);
            log.info("Borrados {} registros sin verificar", viejos.size());
        }
    }
}

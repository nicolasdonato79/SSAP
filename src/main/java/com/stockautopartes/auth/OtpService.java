package com.stockautopartes.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Genera y verifica códigos de 6 dígitos.
 * - El código se guarda hasheado, nunca en texto plano.
 * - Vence a los pocos minutos y permite pocos intentos.
 * - Limita cuántos códigos se piden por teléfono (evita spam y costos de WhatsApp).
 */
@Service
public class OtpService {

    private final CodigoOtpRepository codigos;
    private final EnviadorOtp enviador;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    private final Duration vigencia;
    private final int maxIntentos;
    private final int maxCodigosPorVentana;
    private final Duration ventana;
    private final boolean mostrarEnPantalla;

    public OtpService(CodigoOtpRepository codigos,
                      EnviadorOtp enviador,
                      PasswordEncoder encoder,
                      Clock clock,
                      @Value("${app.otp.vigencia-minutos}") int vigenciaMinutos,
                      @Value("${app.otp.max-intentos}") int maxIntentos,
                      @Value("${app.otp.max-codigos-por-ventana}") int maxCodigosPorVentana,
                      @Value("${app.otp.ventana-minutos}") int ventanaMinutos,
                      @Value("${app.otp.mostrar-en-pantalla}") boolean mostrarEnPantalla) {
        this.codigos = codigos;
        this.enviador = enviador;
        this.encoder = encoder;
        this.clock = clock;
        this.vigencia = Duration.ofMinutes(vigenciaMinutos);
        this.maxIntentos = maxIntentos;
        this.maxCodigosPorVentana = maxCodigosPorVentana;
        this.ventana = Duration.ofMinutes(ventanaMinutos);
        this.mostrarEnPantalla = mostrarEnPantalla;
    }

    /**
     * Genera y manda un código nuevo.
     * @param telefono ya normalizado con {@link TelefonoNormalizer}
     * @return el código, solo en modo prueba con app.otp.mostrar-en-pantalla=true; si no, vacío
     * @throws EnvioOtpException si no se pudo mandar (el código no queda guardado)
     */
    @Transactional
    public Optional<String> solicitar(String telefono) {
        Instant ahora = clock.instant();
        if (codigos.countByTelefonoAndCreadoEnAfter(telefono, ahora.minus(ventana)) >= maxCodigosPorVentana) {
            throw new DemasiadosCodigosException();
        }
        String codigo = String.format("%06d", random.nextInt(1_000_000));
        codigos.save(new CodigoOtp(telefono, encoder.encode(codigo), ahora.plus(vigencia)));
        enviador.enviar(telefono, codigo);
        return (enviador.esDePrueba() && mostrarEnPantalla) ? Optional.of(codigo) : Optional.empty();
    }

    /** true si el código es el último pedido, no venció, no se usó y no se agotaron los intentos. */
    @Transactional
    public boolean verificar(String telefono, String codigo) {
        if (codigo == null || !codigo.matches("\\d{6}")) {
            return false;
        }
        Optional<CodigoOtp> encontrado = codigos.findFirstByTelefonoAndUsadoFalseOrderByCreadoEnDesc(telefono);
        if (encontrado.isEmpty()) {
            return false;
        }
        CodigoOtp otp = encontrado.get();
        if (otp.vencido(clock.instant()) || otp.getIntentos() >= maxIntentos) {
            return false;
        }
        otp.sumarIntento();
        if (encoder.matches(codigo, otp.getCodigoHash())) {
            otp.marcarUsado();
            return true;
        }
        return false;
    }

    /** Borra códigos viejos todos los días a las 4 AM. */
    @Scheduled(cron = "0 0 4 * * *")
    @Transactional
    public void limpiarViejos() {
        codigos.deleteByCreadoEnBefore(clock.instant().minus(Duration.ofDays(1)));
    }
}

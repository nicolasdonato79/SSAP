package com.stockautopartes.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Modo prueba (app.otp.canal=prueba, SMS/WhatsApp sin configurar): el código no se manda, sale en la consola
 * y, si app.otp.mostrar-en-pantalla=true, también en la pantalla de login.
 * NUNCA usar en producción: cualquiera podría entrar con cualquier teléfono.
 */
@Component
@ConditionalOnProperty(name = "app.otp.canal", havingValue = "prueba", matchIfMissing = true)
public class LogEnviadorOtp implements EnviadorOtp {

    private static final Logger log = LoggerFactory.getLogger(LogEnviadorOtp.class);

    @Override
    public void enviar(String telefono, String codigo) {
        log.info(">>> Código de acceso para {}: {}", telefono, codigo);
    }

    @Override
    public boolean esDePrueba() {
        return true;
    }
}

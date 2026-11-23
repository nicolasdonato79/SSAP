package com.stockautopartes.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Modo prueba (correo sin configurar): el email no sale, se escribe en la consola. */
@Component
@ConditionalOnProperty(name = "app.email.habilitado", havingValue = "false", matchIfMissing = true)
public class LogEnviadorEmail implements EnviadorEmail {

    private static final Logger log = LoggerFactory.getLogger(LogEnviadorEmail.class);

    @Override
    public void enviar(String para, String asunto, String texto) {
        log.info(">>> Email (modo prueba) para {} — {}\n{}", para, asunto, texto);
    }

    @Override
    public boolean esDePrueba() {
        return true;
    }
}

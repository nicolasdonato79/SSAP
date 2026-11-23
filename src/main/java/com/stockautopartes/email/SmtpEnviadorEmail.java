package com.stockautopartes.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Manda los emails por SMTP (Gmail con contraseña de aplicación, Brevo, Resend, etc.).
 * Se activa con app.email.habilitado=true y los datos de spring.mail.* (variables MAIL_*).
 */
@Component
@ConditionalOnProperty(name = "app.email.habilitado", havingValue = "true")
public class SmtpEnviadorEmail implements EnviadorEmail {

    private static final Logger log = LoggerFactory.getLogger(SmtpEnviadorEmail.class);

    private final JavaMailSender mail;
    private final String remitente;

    public SmtpEnviadorEmail(JavaMailSender mail, @Value("${app.email.remitente}") String remitente) {
        this.mail = mail;
        this.remitente = remitente;
    }

    @Override
    public void enviar(String para, String asunto, String texto) {
        SimpleMailMessage m = new SimpleMailMessage();
        m.setFrom(remitente);
        m.setTo(para);
        m.setSubject(asunto);
        m.setText(texto);
        try {
            mail.send(m);
            log.info("Email '{}' enviado a {}", asunto, enmascarar(para));
        } catch (MailException e) {
            log.error("No se pudo mandar el email '{}' a {}", asunto, enmascarar(para), e);
            throw new EnvioEmailException("No se pudo mandar el email", e);
        }
    }

    private static String enmascarar(String email) {
        int arroba = email.indexOf('@');
        return arroba <= 1 ? "***" + email.substring(Math.max(arroba, 0)) : email.charAt(0) + "***" + email.substring(arroba);
    }
}

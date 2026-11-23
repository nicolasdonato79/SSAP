package com.stockautopartes.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Manda el código de verificación por SMS con Twilio (API de Messages).
 * Se activa con app.otp.canal=sms. Necesita:
 *   app.twilio.account-sid   (TWILIO_ACCOUNT_SID)
 *   app.twilio.auth-token    (TWILIO_AUTH_TOKEN)
 *   app.twilio.remitente     (TWILIO_FROM): número Twilio (+1...) o el SID de un Messaging Service (MG...)
 */
@Component
@ConditionalOnProperty(name = "app.otp.canal", havingValue = "sms")
public class TwilioSmsEnviadorOtp implements EnviadorOtp {

    private static final Logger log = LoggerFactory.getLogger(TwilioSmsEnviadorOtp.class);

    private final RestClient http;
    private final String remitente;
    private final int vigenciaMinutos;

    public TwilioSmsEnviadorOtp(@Value("${app.twilio.account-sid}") String accountSid,
                                @Value("${app.twilio.auth-token}") String authToken,
                                @Value("${app.twilio.remitente}") String remitente,
                                @Value("${app.otp.vigencia-minutos}") int vigenciaMinutos) {
        if (accountSid.isBlank() || authToken.isBlank() || remitente.isBlank()) {
            throw new IllegalStateException("Faltan TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN o TWILIO_FROM");
        }
        this.remitente = remitente;
        this.vigenciaMinutos = vigenciaMinutos;
        String credenciales = Base64.getEncoder()
                .encodeToString((accountSid + ":" + authToken).getBytes(StandardCharsets.UTF_8));
        this.http = RestClient.builder()
                .baseUrl("https://api.twilio.com/2010-04-01/Accounts/" + accountSid)
                .defaultHeader("Authorization", "Basic " + credenciales)
                .build();
    }

    @Override
    public void enviar(String telefono, String codigo) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("To", telefono); // ya viene en formato internacional (+58...)
        form.add(remitente.startsWith("MG") ? "MessagingServiceSid" : "From", remitente);
        form.add("Body", "Stock Multimarca: tu código es " + codigo + ". Vence en " + vigenciaMinutos
                + " minutos. No se lo pases a nadie.");
        try {
            http.post().uri("/Messages.json")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Código enviado por SMS a {}", enmascarar(telefono));
        } catch (RestClientResponseException e) {
            log.error("Twilio rechazó el SMS a {}: {} {}", enmascarar(telefono),
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new EnvioOtpException("No se pudo mandar el SMS", e);
        } catch (Exception e) {
            log.error("No se pudo conectar con Twilio", e);
            throw new EnvioOtpException("No se pudo mandar el SMS", e);
        }
    }

    private static String enmascarar(String numero) {
        return numero.length() <= 4 ? "****" : "****" + numero.substring(numero.length() - 4);
    }
}

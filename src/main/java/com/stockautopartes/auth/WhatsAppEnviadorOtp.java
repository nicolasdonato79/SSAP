package com.stockautopartes.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

/**
 * Manda el código con WhatsApp Cloud API (Meta), usando una plantilla de categoría
 * "Autenticación" con botón "Copiar código". El código va en el cuerpo y en el botón.
 *
 * Se activa con app.whatsapp.habilitado=true. Necesita:
 *   app.whatsapp.token            token de acceso (en producción, de un usuario del sistema)
 *   app.whatsapp.phone-number-id  ID del número emisor (no es el número de teléfono)
 *   app.whatsapp.plantilla        nombre de la plantilla aprobada
 *   app.whatsapp.idioma           idioma con que se creó la plantilla (ej. "es")
 */
@Component
@ConditionalOnProperty(name = "app.whatsapp.habilitado", havingValue = "true")
public class WhatsAppEnviadorOtp implements EnviadorOtp {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppEnviadorOtp.class);

    private final RestClient http;
    private final String plantilla;
    private final String idioma;

    public WhatsAppEnviadorOtp(@Value("${app.whatsapp.token}") String token,
                               @Value("${app.whatsapp.phone-number-id}") String phoneNumberId,
                               @Value("${app.whatsapp.plantilla}") String plantilla,
                               @Value("${app.whatsapp.idioma}") String idioma,
                               @Value("${app.whatsapp.version-api}") String versionApi) {
        if (token.isBlank() || phoneNumberId.isBlank()) {
            throw new IllegalStateException("Faltan WHATSAPP_TOKEN o WHATSAPP_PHONE_NUMBER_ID");
        }
        this.plantilla = plantilla;
        this.idioma = idioma;
        this.http = RestClient.builder()
                .baseUrl("https://graph.facebook.com/" + versionApi + "/" + phoneNumberId)
                .defaultHeader("Authorization", "Bearer " + token)
                .build();
    }

    @Override
    public void enviar(String telefono, String codigo) {
        String destino = telefono.replaceAll("\\D", ""); // la API lo quiere sin "+"
        Map<String, Object> cuerpo = Map.of(
                "messaging_product", "whatsapp",
                "recipient_type", "individual",
                "to", destino,
                "type", "template",
                "template", Map.of(
                        "name", plantilla,
                        "language", Map.of("code", idioma),
                        "components", List.of(
                                Map.of("type", "body",
                                        "parameters", List.of(Map.of("type", "text", "text", codigo))),
                                Map.of("type", "button", "sub_type", "url", "index", "0",
                                        "parameters", List.of(Map.of("type", "text", "text", codigo))))));
        try {
            http.post().uri("/messages")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(cuerpo)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Código de acceso enviado por WhatsApp a {}", enmascarar(destino));
        } catch (RestClientResponseException e) {
            log.error("WhatsApp rechazó el envío a {}: {} {}", enmascarar(destino),
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new EnvioOtpException("WhatsApp rechazó el envío", e);
        } catch (Exception e) {
            log.error("No se pudo conectar con WhatsApp", e);
            throw new EnvioOtpException("No se pudo conectar con WhatsApp", e);
        }
    }

    private static String enmascarar(String numero) {
        return numero.length() <= 4 ? "****" : "****" + numero.substring(numero.length() - 4);
    }
}

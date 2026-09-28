package com.adarena.notification.service;

import com.adarena.common.config.AppProperties;
import jakarta.mail.internet.InternetAddress;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Envía los emails con la API de Brevo (brevo.com) por HTTPS, en vez de por SMTP.
 *
 * <p>Por qué: el plan gratuito de Brevo da 300 emails al día sin dominio propio (basta con
 * verificar tu email como remitente), y muchos alojamientos gratuitos bloquean los puertos SMTP
 * (25, 465, 587). Una petición HTTPS normal no se bloquea nunca.
 *
 * <p>Si Brevo responde con un error, se lanza una excepción: la bandeja de salida lo apunta y lo
 * reintenta más tarde, igual que con SMTP.
 */
final class BrevoMailDelivery implements MailDelivery {

    static final URI ENDPOINT = URI.create("https://api.brevo.com/v3/smtp/email");

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final JsonMapper json;
    private final URI endpoint;
    private final String apiKey;
    private final Map<String, String> sender;

    BrevoMailDelivery(AppProperties.Mail mail, JsonMapper json, URI endpoint) {
        this.json = json;
        this.endpoint = endpoint;
        this.apiKey = mail.brevoApiKey();
        InternetAddress from;
        try {
            from = new InternetAddress(mail.from(), true);
        } catch (Exception e) {
            throw new IllegalStateException("MAIL_FROM is not a valid address: " + mail.from(), e);
        }
        Map<String, String> sender = new LinkedHashMap<>();
        if (from.getPersonal() != null && !from.getPersonal().isBlank()) {
            sender.put("name", from.getPersonal());
        }
        sender.put("email", from.getAddress());
        this.sender = sender;
    }

    @Override
    public void send(String to, String subject, String html, String text) throws IOException, InterruptedException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sender", sender);
        body.put("to", List.of(Map.of("email", to)));
        body.put("subject", subject);
        body.put("htmlContent", html);
        body.put("textContent", text);

        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(15))
                .header("api-key", apiKey)
                .header("accept", "application/json")
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            // El cuerpo de Brevo explica el motivo (remitente sin verificar, clave incorrecta…)
            String detail = response.body() == null ? "" : response.body();
            throw new IOException("Brevo answered " + response.statusCode() + ": "
                    + detail.substring(0, Math.min(detail.length(), 300)));
        }
    }

    @Override
    public boolean isReal() {
        return true;
    }
}

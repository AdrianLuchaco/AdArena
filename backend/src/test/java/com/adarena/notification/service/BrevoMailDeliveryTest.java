package com.adarena.notification.service;

import com.adarena.common.config.AppProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Los emails por la API de Brevo: una petición HTTPS con la clave, el remitente y el contenido. */
class BrevoMailDeliveryTest {

    private final JsonMapper json = JsonMapper.builder().build();
    private final AtomicReference<String> receivedKey = new AtomicReference<>();
    private final AtomicReference<String> receivedBody = new AtomicReference<>();
    private final AtomicInteger status = new AtomicInteger(201);
    private HttpServer server;

    @BeforeEach
    void startFakeBrevo() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v3/smtp/email", exchange -> {
            receivedKey.set(exchange.getRequestHeaders().getFirst("api-key"));
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] answer = (status.get() == 201 ? "{\"messageId\":\"<1@brevo>\"}" : "{\"message\":\"sender not valid\"}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status.get(), answer.length);
            exchange.getResponseBody().write(answer);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private BrevoMailDelivery delivery() {
        AppProperties.Mail mail = new AppProperties.Mail("AdArena <avisos@example.com>", null, 465, null, null, true,
                "xkeysib-test");
        URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v3/smtp/email");
        return new BrevoMailDelivery(mail, json, endpoint);
    }

    @Test
    void sendsTheEmailWithTheKeySenderAndContent() throws Exception {
        delivery().send("ana@example.com", "You've been outbid", "<p>Hi</p>", "Hi");

        assertThat(receivedKey.get()).isEqualTo("xkeysib-test");
        JsonNode body = json.readTree(receivedBody.get());
        assertThat(body.get("sender").get("name").asString()).isEqualTo("AdArena");
        assertThat(body.get("sender").get("email").asString()).isEqualTo("avisos@example.com");
        assertThat(body.get("to").get(0).get("email").asString()).isEqualTo("ana@example.com");
        assertThat(body.get("subject").asString()).isEqualTo("You've been outbid");
        assertThat(body.get("htmlContent").asString()).isEqualTo("<p>Hi</p>");
        assertThat(body.get("textContent").asString()).isEqualTo("Hi");
    }

    @Test
    void anErrorFromBrevoFailsSoTheOutboxRetriesLater() {
        status.set(400);

        assertThatThrownBy(() -> delivery().send("ana@example.com", "Subject", "<p>x</p>", "x"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("400")
                .hasMessageContaining("sender not valid");
    }
}

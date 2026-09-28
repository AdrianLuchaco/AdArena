package com.adarena.support;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Utilidades comunes para los tests de la API.
 * <p>
 * Los tests comparten base de datos y datos confirmados, así que cada uno usa emails únicos.
 * Cada petición sale además de una IP aleatoria para no agotar los límites de rate limiting
 * (salvo en el test que los comprueba a propósito).
 */
public abstract class ApiTestSupport {

    protected static final String REFRESH_COOKIE = "adarena_refresh";
    protected static final String PASSWORD = "una-contraseña-segura";
    protected static final String ADMIN_EMAIL = "admin@adarena.test";
    protected static final String ADMIN_PASSWORD = "AdminPassword123!";

    @Autowired
    protected MockMvc mockMvc;

    protected static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@test.dev";
    }

    protected static RequestPostProcessor fromIp(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    protected static RequestPostProcessor randomIp() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return fromIp("10." + random.nextInt(256) + "." + random.nextInt(256) + "." + random.nextInt(1, 255));
    }

    protected MvcResult register(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/register").with(randomIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","displayName":"Usuario Test","acceptTerms":true}
                                """.formatted(email, password)))
                .andReturn();
    }

    protected MvcResult login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").with(randomIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andReturn();
    }

    protected static String accessToken(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    protected static Cookie refreshCookie(MvcResult result) {
        return result.getResponse().getCookie(REFRESH_COOKIE);
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }
}

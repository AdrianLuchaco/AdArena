package com.adarena.user;

import com.adarena.support.ApiTestSupport;
import com.adarena.support.IntegrationTest;
import com.adarena.user.domain.User;
import com.adarena.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class AuthIntegrationTest extends ApiTestSupport {

    @Autowired
    UserRepository userRepository;

    @Autowired
    JwtEncoder jwtEncoder;

    // ------------------------------------------------------------------ registro

    @Test
    void registerCreatesUserAndReturnsTokens() throws Exception {
        String email = uniqueEmail().toUpperCase();

        MvcResult result = register(email, PASSWORD);

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(accessToken(result)).isNotBlank();
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"tokenType\":\"Bearer\"", "\"role\":\"USER\"", email.toLowerCase());

        // El refresh token viaja SOLO en una cookie protegida
        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains(REFRESH_COOKIE + "=", "HttpOnly", "Secure", "SameSite=Lax", "Path=/api/auth");
        assertThat(body).doesNotContain(refreshCookie(result).getValue());

        // La contraseña se guarda con BCrypt, nunca en claro
        User saved = userRepository.findByEmail(email.toLowerCase()).orElseThrow();
        assertThat(saved.getPasswordHash()).startsWith("{bcrypt}$2").doesNotContain(PASSWORD);
        assertThat(saved.getAcceptedTermsVersion()).isEqualTo("2026-09-29-en");
    }

    @Test
    void registerRejectsDuplicateEmailIgnoringCase() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD);

        MvcResult duplicate = register(email.toUpperCase(), PASSWORD);

        assertThat(duplicate.getResponse().getStatus()).isEqualTo(409);
        assertThat(duplicate.getResponse().getContentAsString()).contains("\"code\":\"EMAIL_TAKEN\"");
    }

    @Test
    void registerValidatesEveryField() throws Exception {
        mockMvc.perform(post("/api/auth/register").with(randomIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"no-es-un-email","password":"corta","displayName":"","acceptTerms":false}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.email").value("That email is not valid."))
                .andExpect(jsonPath("$.errors.password").exists())
                .andExpect(jsonPath("$.errors.displayName").exists())
                .andExpect(jsonPath("$.errors.acceptTerms").exists());
    }

    @Test
    void registerRejectsPasswordsLongerThan72Bytes() throws Exception {
        String accented = "ñ".repeat(40); // 40 caracteres, pero 80 bytes en UTF-8

        MvcResult result = register(uniqueEmail(), accented);

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getContentAsString()).contains("PASSWORD_TOO_LONG");
    }

    @Test
    void malformedJsonGetsAClearError() throws Exception {
        mockMvc.perform(post("/api/auth/login").with(randomIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{esto no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    // ------------------------------------------------------------------ login

    @Test
    void loginWorksWithAnyEmailCasing() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD);

        MvcResult result = login(email.toUpperCase(), PASSWORD);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(accessToken(result)).isNotBlank();
        assertThat(refreshCookie(result)).isNotNull();
    }

    @Test
    void loginFailsWithTheSameMessageForWrongPasswordAndUnknownEmail() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD);

        MvcResult wrongPassword = login(email, "otra-contraseña-distinta");
        MvcResult unknownEmail = login(uniqueEmail(), PASSWORD);

        assertThat(wrongPassword.getResponse().getStatus()).isEqualTo(401);
        assertThat(unknownEmail.getResponse().getStatus()).isEqualTo(401);
        assertThat(wrongPassword.getResponse().getContentAsString()).contains("INVALID_CREDENTIALS");
        assertThat(unknownEmail.getResponse().getContentAsString()).contains("INVALID_CREDENTIALS");
    }

    // ------------------------------------------------------------------ rutas protegidas

    @Test
    void meRequiresAValidAccessToken() throws Exception {
        String email = uniqueEmail();
        String token = accessToken(register(email, PASSWORD));

        mockMvc.perform(get("/api/me").with(randomIp()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mockMvc.perform(get("/api/me").with(randomIp()).header(HttpHeaders.AUTHORIZATION, bearer("inventado")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));

        mockMvc.perform(get("/api/me").with(randomIp()).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void expiredAccessTokenIsRejected() throws Exception {
        User user = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
        Instant past = Instant.now().minusSeconds(3600);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("adarena")
                .subject(user.getId().toString())
                .issuedAt(past.minusSeconds(900))
                .expiresAt(past)
                .claim("roles", List.of("ADMIN"))
                .build();
        String expired = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();

        mockMvc.perform(get("/api/me").with(randomIp()).header(HttpHeaders.AUTHORIZATION, bearer(expired)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    @Test
    void adminRoutesRequireAdminRole() throws Exception {
        String userToken = accessToken(register(uniqueEmail(), PASSWORD));
        String adminToken = accessToken(login(ADMIN_EMAIL, ADMIN_PASSWORD));

        mockMvc.perform(get("/api/admin/_test/ping").with(randomIp())
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(get("/api/admin/_test/ping").with(randomIp())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ refresh y logout

    @Test
    void refreshRotatesTheTokenAndDetectsReuse() throws Exception {
        MvcResult registered = register(uniqueEmail(), PASSWORD);
        Cookie first = refreshCookie(registered);

        MvcResult refreshed = mockMvc.perform(post("/api/auth/refresh").with(randomIp()).cookie(first))
                .andExpect(status().isOk())
                .andReturn();
        Cookie second = refreshCookie(refreshed);
        assertThat(second.getValue()).isNotEqualTo(first.getValue());
        assertThat(accessToken(refreshed)).isNotBlank();

        // Alguien reutiliza el token viejo → se considera robado y se revoca toda la familia
        mockMvc.perform(post("/api/auth/refresh").with(randomIp()).cookie(first))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_REUSED"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

        // ...así que el token nuevo (el "legítimo") tampoco sirve ya
        mockMvc.perform(post("/api/auth/refresh").with(randomIp()).cookie(second))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshWithoutCookieOrWithUnknownToken() throws Exception {
        // Sin cookie es un visitante, no un error: 204 sin cuerpo y sin tocar cookies
        mockMvc.perform(post("/api/auth/refresh").with(randomIp()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        mockMvc.perform(post("/api/auth/refresh").with(randomIp()).cookie(new Cookie(REFRESH_COOKIE, "inventado")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
    }

    @Test
    void logoutRevokesTheSession() throws Exception {
        Cookie cookie = refreshCookie(register(uniqueEmail(), PASSWORD));

        mockMvc.perform(post("/api/auth/logout").with(randomIp()).cookie(cookie))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

        mockMvc.perform(post("/api/auth/refresh").with(randomIp()).cookie(cookie))
                .andExpect(status().isUnauthorized());
    }
}

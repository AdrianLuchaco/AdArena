package com.adarena.user;

import com.adarena.support.ApiTestSupport;
import com.adarena.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** "He olvidado mi contraseña" de principio a fin, leyendo el enlace del email de la bandeja de salida. */
@IntegrationTest
class PasswordResetIntegrationTest extends ApiTestSupport {

    private static final Pattern TOKEN = Pattern.compile("/reset-password\\?token=([A-Za-z0-9_-]+)");
    private static final String NEW_PASSWORD = "otra-contraseña-nueva";

    @Autowired JdbcTemplate jdbc;

    private ResultActions forgot(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/password/forgot").with(randomIp())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"));
    }

    private ResultActions reset(String token, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/password/reset").with(randomIp())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"newPassword\":\"" + password + "\"}"));
    }

    private List<String> resetEmails(String email) {
        return jdbc.queryForList("SELECT text_body FROM email_outbox WHERE to_email = ? AND subject LIKE '%password%' "
                + "ORDER BY created_at", String.class, email);
    }

    private static String tokenFrom(String emailText) {
        Matcher matcher = TOKEN.matcher(emailText);
        assertThat(matcher.find()).as("the email contains the reset link").isTrue();
        return matcher.group(1);
    }

    @Test
    void theAnswerIsTheSameWhetherTheAccountExistsOrNot() throws Exception {
        String unknown = uniqueEmail();

        forgot(unknown).andExpect(status().isAccepted());

        assertThat(resetEmails(unknown)).isEmpty();
    }

    @Test
    void resetThePasswordWithTheEmailLinkAndCloseEverySession() throws Exception {
        String email = uniqueEmail();
        MvcResult registered = register(email, PASSWORD);
        Cookie oldSession = refreshCookie(registered);

        forgot(email.toUpperCase()).andExpect(status().isAccepted());
        List<String> emails = resetEmails(email);
        assertThat(emails).hasSize(1);
        String token = tokenFrom(emails.getFirst());

        reset(token, NEW_PASSWORD).andExpect(status().isNoContent());

        assertThat(login(email, PASSWORD).getResponse().getStatus()).isEqualTo(401);
        assertThat(login(email, NEW_PASSWORD).getResponse().getStatus()).isEqualTo(200);
        // La sesión que había abierta antes del cambio ya no sirve
        mockMvc.perform(post("/api/auth/refresh").with(randomIp()).cookie(oldSession))
                .andExpect(status().isUnauthorized());
        // El enlace solo sirve una vez
        reset(token, "y-otra-mas-distinta").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESET_TOKEN_INVALID"));
    }

    @Test
    void expiredOrInventedLinksDoNotWork() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD);
        forgot(email).andExpect(status().isAccepted());
        String token = tokenFrom(resetEmails(email).getFirst());
        jdbc.update("UPDATE password_reset_tokens SET expires_at = now() - interval '1 minute' "
                + "WHERE user_id = (SELECT id FROM users WHERE email = ?)", email);

        reset(token, NEW_PASSWORD).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESET_TOKEN_INVALID"));
        reset("token-inventado-por-un-atacante", NEW_PASSWORD).andExpect(status().isBadRequest());
        assertThat(login(email, PASSWORD).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void atMostThreeLinksPerHourAreSent() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD);

        for (int i = 0; i < 5; i++) {
            forgot(email).andExpect(status().isAccepted());
        }

        assertThat(resetEmails(email)).hasSize(3);
    }

    @Test
    void theNewPasswordMustBeLongEnough() throws Exception {
        reset("cualquier-token", "corta").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.newPassword").exists());
    }
}

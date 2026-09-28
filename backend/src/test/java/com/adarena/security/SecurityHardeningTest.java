package com.adarena.security;

import com.adarena.support.ApiTestSupport;
import com.adarena.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Las protecciones añadidas en la auditoría de seguridad. */
@IntegrationTest
class SecurityHardeningTest extends ApiTestSupport {

    @Test
    void cookieRoutesRejectRequestsFromOtherWebsites() throws Exception {
        mockMvc.perform(post("/api/auth/logout").with(randomIp())
                        .header(HttpHeaders.ORIGIN, "https://web-maliciosa.example"))
                .andExpect(status().isForbidden());
        // Desde la web de AdArena funciona
        mockMvc.perform(post("/api/auth/logout").with(randomIp())
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000"))
                .andExpect(status().isNoContent());
    }

    @Test
    void hugeJsonBodiesAreRejectedBeforeBeingRead() throws Exception {
        String token = accessToken(register(uniqueEmail(), PASSWORD));
        String huge = "{\"companyName\":\"" + "A".repeat(100_000) + "\"}";

        mockMvc.perform(put("/api/me/ad-profile").with(randomIp())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(huge))
                .andExpect(status().is(413))
                .andExpect(jsonPath("$.code").value("REQUEST_TOO_LARGE"));
    }

    @Test
    void apiResponsesCarrySecurityHeaders() throws Exception {
        mockMvc.perform(get("/api/public/home").with(randomIp()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'none'")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
    }

    @Test
    void theAdminAreaNeedsTheAdminRole() throws Exception {
        String userToken = accessToken(register(uniqueEmail(), PASSWORD));

        mockMvc.perform(get("/api/admin/overview").with(randomIp()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/overview").with(randomIp()).header(HttpHeaders.AUTHORIZATION, bearer(userToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/admin/settings").with(randomIp()).header(HttpHeaders.AUTHORIZATION, bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void developmentToolsDoNotExistOutsideDevelopment() throws Exception {
        String adminToken = accessToken(login(ADMIN_EMAIL, ADMIN_PASSWORD));

        mockMvc.perform(post("/api/dev/arena/close-now").with(randomIp()).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/dev/points").with(randomIp()).header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"points\":100000}"))
                .andExpect(status().isNotFound());
    }
}

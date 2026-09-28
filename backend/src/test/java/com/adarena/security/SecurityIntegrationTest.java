package com.adarena.security;

import com.adarena.support.ApiTestSupport;
import com.adarena.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class SecurityIntegrationTest extends ApiTestSupport {

    private static final String FRONTEND = "http://localhost:3000";

    @Test
    void corsPreflightAllowsTheFrontendWithCredentials() throws Exception {
        mockMvc.perform(options("/api/auth/login").with(randomIp())
                        .header(HttpHeaders.ORIGIN, FRONTEND)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONTEND))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    void corsRejectsUnknownOrigins() throws Exception {
        // Una web maliciosa intentando usar la cookie de sesión de la víctima
        mockMvc.perform(post("/api/auth/refresh").with(randomIp())
                        .header(HttpHeaders.ORIGIN, "https://web-maliciosa.example"))
                .andExpect(status().isForbidden());
    }

    @Test
    void loginIsRateLimitedPerIp() throws Exception {
        String ip = "192.0.2.77";
        String body = """
                {"email":"nadie@test.dev","password":"lo-que-sea"}""";

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/auth/login").with(fromIp(ip))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/auth/login").with(fromIp(ip))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));

        // Otra IP no se ve afectada
        mockMvc.perform(post("/api/auth/login").with(fromIp("192.0.2.78"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void healthEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health").with(randomIp()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void openApiDocumentIsPublished() throws Exception {
        mockMvc.perform(get("/v3/api-docs").with(randomIp()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("AdArena API"))
                .andExpect(jsonPath("$.paths['/api/auth/login']").exists());
    }

    @Test
    void unknownProtectedRoutesRequireLogin() throws Exception {
        mockMvc.perform(get("/api/no-existe").with(randomIp()))
                .andExpect(status().isUnauthorized());
    }
}

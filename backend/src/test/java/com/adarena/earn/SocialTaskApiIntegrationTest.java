package com.adarena.earn;

import com.adarena.support.ApiTestSupport;
import com.adarena.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Promocionar y Créditos extra de punta a punta, por HTTP: publicar un enlace, que otro usuario lo
 * visite y reclame sus puntos, límites, denuncias y moderación. Y los puntos de bienvenida.
 */
@IntegrationTest
class SocialTaskApiIntegrationTest extends ApiTestSupport {

    @Autowired JdbcTemplate jdbc;

    String ownerToken;
    String visitorToken;

    @BeforeEach
    void setUp() throws Exception {
        ownerToken = accessToken(register(uniqueEmail(), PASSWORD));
        visitorToken = accessToken(register(uniqueEmail(), PASSWORD));
    }

    private ResultActions promote(String token, String title, String url) throws Exception {
        return mockMvc.perform(post("/api/promotions").with(randomIp())
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title":"%s","description":"Contenido nuevo cada semana","url":"%s"}
                        """.formatted(title, url)));
    }

    private String createTask(String token, String url) throws Exception {
        String body = promote(token, "Mi canal", url).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private ResultActions action(String token, String taskId, String verb) throws Exception {
        return mockMvc.perform(post("/api/earn/tasks/" + taskId + "/" + verb).with(randomIp())
                .header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private long availablePoints(String token) throws Exception {
        String body = mockMvc.perform(get("/api/me/points").with(randomIp()).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.availablePoints")).longValue();
    }

    /**
     * Como si hubieran pasado {@code seconds} segundos desde que abrió el enlace (y desde las tareas
     * que ya cobró: el reloj de atención es uno por persona).
     */
    private void waitSeconds(String taskId, int seconds) {
        jdbc.update("UPDATE social_task_completions SET started_at = started_at - make_interval(secs => ?) WHERE task_id = ?::uuid",
                seconds, taskId);
        jdbc.update("""
                UPDATE social_task_completions SET completed_at = completed_at - make_interval(secs => ?)
                WHERE completed_at IS NOT NULL
                  AND user_id IN (SELECT user_id FROM social_task_completions WHERE task_id = ?::uuid)
                """, seconds, taskId);
    }

    @Test
    void newAccountsStartWithWelcomePoints() throws Exception {
        mockMvc.perform(get("/api/me/points").with(randomIp()).header(HttpHeaders.AUTHORIZATION, bearer(visitorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availablePoints").value(200))
                .andExpect(jsonPath("$.movements[0].type").value("SIGNUP_BONUS"))
                .andExpect(jsonPath("$.movements[0].amountPoints").value(200));
    }

    @Test
    void visitAPromotedLinkAndGetThePointsAfterTenSeconds() throws Exception {
        String taskId = createTask(ownerToken, "youtube.com/@mi-canal-de-prueba");

        // El visitante la ve en sus Créditos extra; su dueño no
        String visitorList = mockMvc.perform(get("/api/earn/tasks").with(randomIp())
                        .header(HttpHeaders.AUTHORIZATION, bearer(visitorToken)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> platform = JsonPath.read(visitorList, "$.tasks[?(@.id == '" + taskId + "')].platform");
        assertThat(platform).containsExactly("YOUTUBE");
        String ownerList = mockMvc.perform(get("/api/earn/tasks").with(randomIp())
                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))).andReturn().getResponse().getContentAsString();
        assertThat(ownerList).doesNotContain(taskId);

        action(visitorToken, taskId, "start")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://youtube.com/@mi-canal-de-prueba"))
                .andExpect(jsonPath("$.minSeconds").value(10));

        // Vuelve enseguida: todavía no
        action(visitorToken, taskId, "claim")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TASK_TOO_SOON"));

        waitSeconds(taskId, 11);
        action(visitorToken, taskId, "claim")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pointsAwarded").value(20))
                .andExpect(jsonPath("$.availablePoints").value(220));

        // Una vez al día
        action(visitorToken, taskId, "claim").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TASK_ALREADY_DONE"));
        action(visitorToken, taskId, "start").andExpect(status().isConflict());
        assertThat(availablePoints(visitorToken)).isEqualTo(220);

        // Su dueño ve la visita en su panel de promoción
        mockMvc.perform(get("/api/promotions").with(randomIp()).header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(jsonPath("$.promotions[0].totalVisits").value(1))
                .andExpect(jsonPath("$.promotions[0].visitsToday").value(1));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ledger_account_mismatches", Long.class)).isZero();
    }

    @Test
    void aDoubleClickOnVisitStartsTheTaskOnce() throws Exception {
        String taskId = createTask(ownerToken, "https://www.twitch.tv/prueba");

        // Dos "Visitar" a la vez: los dos responden bien y solo hay una fila
        CountDownLatch go = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            List<Future<Integer>> results = List.of(
                    pool.submit(() -> { go.await(); return action(visitorToken, taskId, "start").andReturn().getResponse().getStatus(); }),
                    pool.submit(() -> { go.await(); return action(visitorToken, taskId, "start").andReturn().getResponse().getStatus(); }));
            go.countDown();
            for (Future<Integer> result : results) {
                assertThat(result.get()).isEqualTo(200);
            }
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM social_task_completions WHERE task_id = ?::uuid",
                Long.class, taskId)).isOne();

        waitSeconds(taskId, 11);
        action(visitorToken, taskId, "claim").andExpect(status().isOk())
                .andExpect(jsonPath("$.pointsAwarded").value(20));
    }

    @Test
    void claimingWithoutOpeningTheLinkOrYourOwnTaskGivesNothing() throws Exception {
        String taskId = createTask(ownerToken, "https://x.com/prueba");

        action(visitorToken, taskId, "claim").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TASK_NOT_STARTED"));
        action(ownerToken, taskId, "start").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OWN_TASK"));
    }

    @Test
    void onlyPublicHttpsLinksCanBePromoted() throws Exception {
        promote(ownerToken, "Mi web", "http://insegura.com").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.url").exists());
        promote(ownerToken, "Mi web", "https://192.168.1.10").andExpect(status().isBadRequest());
        promote(ownerToken, "x", "https://ejemplo.com").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").exists());
        promote(ownerToken, "Mi web", "ejemplo.com/tienda").andExpect(status().isCreated())
                .andExpect(jsonPath("$.platform").value("WEB"))
                .andExpect(jsonPath("$.url").value("https://ejemplo.com/tienda"));
    }

    @Test
    void atMostFiveActivePromotionsPerUser() throws Exception {
        for (int i = 0; i < 5; i++) {
            promote(ownerToken, "Promo " + i, "https://ejemplo.com/" + i).andExpect(status().isCreated());
        }
        promote(ownerToken, "Una más", "https://ejemplo.com/6").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROMOTION_LIMIT"));
    }

    @Test
    void ownersCanPauseResumeAndDeleteOnlyTheirPromotions() throws Exception {
        String taskId = createTask(ownerToken, "https://ejemplo.com/pausa");

        mockMvc.perform(post("/api/promotions/" + taskId + "/pause").with(randomIp())
                        .header(HttpHeaders.AUTHORIZATION, bearer(visitorToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/promotions/" + taskId + "/pause").with(randomIp())
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAUSED"));
        action(visitorToken, taskId, "start").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TASK_UNAVAILABLE"));
        mockMvc.perform(post("/api/promotions/" + taskId + "/resume").with(randomIp())
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        mockMvc.perform(delete("/api/promotions/" + taskId).with(randomIp())
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isNoContent());
        action(visitorToken, taskId, "start").andExpect(status().isNotFound());
    }

    @Test
    void threeReportsHideATaskAndTheAdminCanRestoreIt() throws Exception {
        String taskId = createTask(ownerToken, "https://ejemplo.com/sospechosa");
        String report = "{\"reason\":\"El enlace no funciona\"}";

        for (int i = 0; i < 3; i++) {
            String reporter = i == 0 ? visitorToken : accessToken(register(uniqueEmail(), PASSWORD));
            mockMvc.perform(post("/api/earn/tasks/" + taskId + "/report").with(randomIp())
                            .header(HttpHeaders.AUTHORIZATION, bearer(reporter))
                            .contentType(MediaType.APPLICATION_JSON).content(report))
                    .andExpect(status().isNoContent());
        }
        // Denunciar dos veces no cuenta
        mockMvc.perform(post("/api/earn/tasks/" + taskId + "/report").with(randomIp())
                        .header(HttpHeaders.AUTHORIZATION, bearer(visitorToken))
                        .contentType(MediaType.APPLICATION_JSON).content(report))
                .andExpect(status().isConflict());

        action(visitorToken, taskId, "start").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TASK_UNAVAILABLE"));
        mockMvc.perform(get("/api/promotions").with(randomIp()).header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(jsonPath("$.promotions[0].status").value("HIDDEN"));
        mockMvc.perform(get("/api/me/notifications").with(randomIp()).header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(jsonPath("$.items[0].type").value("TASK_HIDDEN"));
        // Su dueño no puede reactivarla…
        mockMvc.perform(post("/api/promotions/" + taskId + "/resume").with(randomIp())
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isConflict());

        // …pero el admin sí (y un usuario normal no puede usar las rutas de admin)
        mockMvc.perform(post("/api/admin/tasks/" + taskId + "/restore").with(randomIp())
                        .header(HttpHeaders.AUTHORIZATION, bearer(visitorToken)))
                .andExpect(status().isForbidden());
        String adminToken = accessToken(login(ADMIN_EMAIL, ADMIN_PASSWORD));
        String adminList = mockMvc.perform(get("/api/admin/tasks").with(randomIp())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<List<String>> reasons = JsonPath.read(adminList, "$[?(@.id == '" + taskId + "')].reportReasons");
        assertThat(reasons.getFirst()).contains("El enlace no funciona");
        mockMvc.perform(post("/api/admin/tasks/" + taskId + "/restore").with(randomIp())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk());
        action(visitorToken, taskId, "start").andExpect(status().isOk());
    }

    @Test
    void theAdminCanHideAPromotionWithAReason() throws Exception {
        String taskId = createTask(ownerToken, "https://ejemplo.com/a-ocultar");
        String adminToken = accessToken(login(ADMIN_EMAIL, ADMIN_PASSWORD));

        mockMvc.perform(post("/api/admin/tasks/" + taskId + "/hide").with(randomIp())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Contenido no permitido\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/promotions").with(randomIp()).header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(jsonPath("$.promotions[0].status").value("HIDDEN"))
                .andExpect(jsonPath("$.promotions[0].hiddenReason").value("Contenido no permitido"));
    }

    @Test
    void thereIsADailyLimitOfTasks() throws Exception {
        // 11 tareas de 3 dueños distintos (cada uno puede tener 5 a la vez)
        String[] owners = {ownerToken, accessToken(register(uniqueEmail(), PASSWORD)),
                accessToken(register(uniqueEmail(), PASSWORD))};
        String[] tasks = new String[11];
        for (int i = 0; i < tasks.length; i++) {
            tasks[i] = createTask(owners[i % 3], "https://ejemplo.com/limite/" + i);
        }
        for (int i = 0; i < 10; i++) {
            action(visitorToken, tasks[i], "start").andExpect(status().isOk());
            waitSeconds(tasks[i], 11);
            action(visitorToken, tasks[i], "claim").andExpect(status().isOk());
        }

        action(visitorToken, tasks[10], "start").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TASK_DAILY_LIMIT"));
        assertThat(availablePoints(visitorToken)).isEqualTo(200 + 10 * 20);
    }
}

package com.adarena.notification;

import com.adarena.adprofile.repository.AdProfileRepository;
import com.adarena.auction.repository.AuctionRepository;
import com.adarena.auction.service.BidService;
import com.adarena.image.service.ImageService;
import com.adarena.notification.domain.NotificationType;
import com.adarena.notification.repository.NotificationRepository;
import com.adarena.notification.service.OutboxEmailSender;
import com.adarena.security.TokenService;
import com.adarena.support.ApiTestSupport;
import com.adarena.support.ArenaFixtures;
import com.adarena.support.IsolatedArenaTest;
import com.adarena.user.repository.UserRepository;
import com.adarena.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Regla 4 ("te han superado" por email) sin spam, la campana de avisos y la bandeja de salida. */
@IsolatedArenaTest
class NotificationIntegrationTest extends ApiTestSupport {

    @Autowired BidService bidService;
    @Autowired OutboxEmailSender emailSender;
    @Autowired NotificationRepository notificationRepository;
    @Autowired TokenService tokenService;
    @Autowired UserRepository userRepository;
    @Autowired AdProfileRepository adProfileRepository;
    @Autowired ImageService imageService;
    @Autowired WalletService walletService;
    @Autowired AuctionRepository auctionRepository;
    @Autowired JdbcTemplate jdbc;

    ArenaFixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures = new ArenaFixtures(userRepository, adProfileRepository, imageService, walletService,
                auctionRepository, jdbc);
        fixtures.openRound(Duration.ofHours(3));
    }

    private void bid(UUID user, long cents) {
        bidService.placeBid(user, cents, "test-" + UUID.randomUUID());
    }

    private long outbidEmails(UUID user) {
        String email = userRepository.findById(user).orElseThrow().getEmail();
        Long count = jdbc.queryForObject("SELECT count(*) FROM email_outbox WHERE to_email = ? AND dedup_key LIKE 'outbid:%'",
                Long.class, email);
        return count == null ? 0 : count;
    }

    private String tokenFor(UUID user) {
        return tokenService.issueAccessToken(userRepository.findById(user).orElseThrow()).value();
    }

    @Test
    void theOutbidUserGetsOneEmailPerBidNotOnePerRival() {
        UUID ana = fixtures.bidder("Ana", 100_00);
        UUID luis = fixtures.bidder("Luis", 100_00);
        UUID bea = fixtures.bidder("Bea", 100_00);

        bid(ana, 10_00);
        bid(luis, 11_00);            // supera a Ana → 1 email para Ana
        bid(bea, 12_00);             // también va por delante de Ana, pero Ana ya fue avisada tras su última puja
        assertThat(outbidEmails(ana)).isEqualTo(1);
        assertThat(outbidEmails(luis)).isEqualTo(1);

        bid(ana, 5_00);              // Ana vuelve a ir primera (1.500 puntos)…
        bid(luis, 10_00);            // …y Luis la supera otra vez (2.100 puntos) → segundo email para Ana
        assertThat(outbidEmails(ana)).isEqualTo(2);

        assertThat(notificationRepository.findByUserIdOrderByCreatedAtDesc(ana, Limit.of(10)))
                .extracting("type").containsOnly(NotificationType.OUTBID).hasSize(2);
    }

    @Test
    void theNotificationBellListsAndMarksAsRead() throws Exception {
        UUID ana = fixtures.bidder("Ana", 100_00);
        UUID luis = fixtures.bidder("Luis", 100_00);
        bid(ana, 10_00);
        bid(luis, 11_00);
        String token = tokenFor(ana);

        String body = mockMvc.perform(get("/api/me/notifications").with(randomIp()).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(1))
                .andExpect(jsonPath("$.items[0].type").value("OUTBID"))
                .andExpect(jsonPath("$.items[0].link").value("/arena"))
                .andExpect(jsonPath("$.items[0].read").value(false))
                .andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(body, "$.items[0].id");

        // Nadie puede marcar los avisos de otro
        mockMvc.perform(post("/api/me/notifications/" + id + "/read").with(randomIp())
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor(luis))))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/me/notifications/read-all").with(randomIp()).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/me/notifications").with(randomIp()).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.unreadCount").value(0))
                .andExpect(jsonPath("$.items[0].read").value(true));
    }

    @Test
    void pendingEmailsAreSentAndMarked() {
        UUID ana = fixtures.bidder("Ana", 100_00);
        UUID luis = fixtures.bidder("Luis", 100_00);
        bid(ana, 10_00);
        bid(luis, 11_00);

        while (emailSender.sendDueEmails() > 0) {
            // vacía la bandeja (sin SMTP en los tests, se "envían" al log)
        }

        Long pending = jdbc.queryForObject("SELECT count(*) FROM email_outbox WHERE status = 'PENDING'", Long.class);
        assertThat(pending).isZero();
        String email = userRepository.findById(ana).orElseThrow().getEmail();
        String status = jdbc.queryForObject("SELECT status FROM email_outbox WHERE to_email = ? LIMIT 1", String.class, email);
        assertThat(status).isEqualTo("SENT");
    }

    @Test
    void userContentIsEscapedInEmails() {
        UUID attacker = fixtures.bidder("<script>alert(1)</script>", 100_00);
        UUID ana = fixtures.bidder("Ana", 100_00);
        jdbc.update("UPDATE users SET display_name = '<b>Ana</b>' WHERE id = ?", ana);
        bid(ana, 10_00);
        bid(attacker, 11_00);

        String email = userRepository.findById(ana).orElseThrow().getEmail();
        String html = jdbc.queryForObject("SELECT html_body FROM email_outbox WHERE to_email = ? LIMIT 1", String.class, email);
        assertThat(html).contains("&lt;b&gt;Ana&lt;/b&gt;").doesNotContain("<b>Ana</b>");
    }
}

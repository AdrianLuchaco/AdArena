package com.adarena.admin;

import com.adarena.adprofile.repository.AdProfileRepository;
import com.adarena.auction.domain.Auction;
import com.adarena.auction.repository.AuctionRepository;
import com.adarena.auction.service.ArenaCloseService;
import com.adarena.auction.service.BidService;
import com.adarena.image.service.ImageService;
import com.adarena.support.ApiTestSupport;
import com.adarena.support.ArenaFixtures;
import com.adarena.support.IsolatedArenaTest;
import com.adarena.user.repository.UserRepository;
import com.adarena.wallet.service.WalletService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** El panel de administración por HTTP: resumen, moderación, configuración y auditoría. */
@IsolatedArenaTest
class AdminApiIntegrationTest extends ApiTestSupport {

    @Autowired BidService bidService;
    @Autowired ArenaCloseService closeService;
    @Autowired UserRepository userRepository;
    @Autowired AdProfileRepository adProfileRepository;
    @Autowired ImageService imageService;
    @Autowired WalletService walletService;
    @Autowired AuctionRepository auctionRepository;
    @Autowired JdbcTemplate jdbc;

    ArenaFixtures fixtures;
    String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        fixtures = new ArenaFixtures(userRepository, adProfileRepository, imageService, walletService,
                auctionRepository, jdbc);
        adminToken = accessToken(login(ADMIN_EMAIL, ADMIN_PASSWORD));
    }

    private String adminGet(String path) throws Exception {
        return mockMvc.perform(get(path).with(randomIp()).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    void moderateTheWinnerFromThePanel() throws Exception {
        Auction round = fixtures.openRound(Duration.ofHours(2));
        UUID ana = fixtures.bidder("Café Aurora", 30_00);
        UUID luis = fixtures.bidder("Bicis Norte", 30_00);
        bidService.placeBid(ana, 15_00, "k-" + UUID.randomUUID());
        bidService.placeBid(luis, 10_00, "k-" + UUID.randomUUID());
        fixtures.endRoundNow(round.getId());
        closeService.closeDueRound().orElseThrow();

        // (Otros tests de esta base de datos pueden dejar pendientes: buscamos el nuestro por nombre)
        String pending = adminGet("/api/admin/ad-slots/pending");
        String slotId = pendingIdOf(pending, "Café Aurora");
        List<Integer> amounts = JsonPath.read(pending, "$[?(@.id == '" + slotId + "')].amountPoints");
        List<Integer> ranks = JsonPath.read(pending, "$[?(@.id == '" + slotId + "')].candidateRank");
        assertThat(amounts).containsExactly(15_00);
        assertThat(ranks).containsExactly(1);

        // Rechazar exige motivo
        mockMvc.perform(post("/api/admin/ad-slots/" + slotId + "/reject").with(randomIp())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/admin/ad-slots/" + slotId + "/reject").with(randomIp())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"La web no funciona\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refundedPoints").value(15_00))
                .andExpect(jsonPath("$.promotedSlotId").exists());

        String promoted = pendingIdOf(adminGet("/api/admin/ad-slots/pending"), "Bicis Norte");
        mockMvc.perform(post("/api/admin/ad-slots/" + promoted + "/approve").with(randomIp())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/public/home").with(randomIp()))
                .andExpect(jsonPath("$.state").value("AD"))
                .andExpect(jsonPath("$.currentAd.companyName").value("Bicis Norte"))
                .andExpect(jsonPath("$.currentAd.wonWithPoints").value(10_00));
        assertThat(adminGet("/api/admin/ad-slots/recent")).contains("REJECTED", "APPROVED");
        String audit = adminGet("/api/admin/audit-log");
        assertThat(audit).contains("AD_SLOT_REJECTED", "AD_SLOT_APPROVED");
    }

    private static String pendingIdOf(String json, String companyName) {
        List<String> ids = JsonPath.read(json, "$[?(@.ad.companyName == '" + companyName + "')].id");
        assertThat(ids).hasSize(1);
        return ids.getFirst();
    }

    @Test
    void theOverviewShowsTheBusinessAndThatTheLedgerBalances() throws Exception {
        String overview = adminGet("/api/admin/overview");

        assertThat((Integer) JsonPath.read(overview, "$.ledgerMismatches")).isZero();
        assertThat((Boolean) JsonPath.read(overview, "$.emailsDelivered")).isFalse(); // sin SMTP en los tests
        assertThat((Integer) JsonPath.read(overview, "$.users")).isPositive();
    }

    @Test
    void settingsChangesApplyFromTheNextRound() throws Exception {
        Auction today = fixtures.openRound(Duration.ofHours(2));
        String current = adminGet("/api/admin/settings");
        String changed = current.replace("\"minBidPoints\":100", "\"minBidPoints\":200");

        mockMvc.perform(put("/api/admin/settings").with(randomIp())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(changed))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minBidPoints").value(200));

        // La ronda de hoy mantiene sus reglas; la siguiente nace con las nuevas
        assertThat(auctionRepository.findById(today.getId()).orElseThrow().getRules().minBidPoints()).isEqualTo(100);
        fixtures.endRoundNow(today.getId());
        UUID next = closeService.closeDueRound().orElseThrow().nextRoundId();
        assertThat(auctionRepository.findById(next).orElseThrow().getRules().minBidPoints()).isEqualTo(200);
        assertThat(adminGet("/api/admin/audit-log")).contains("SETTINGS_UPDATED");

        // Dejarlo como estaba para el resto de tests
        mockMvc.perform(put("/api/admin/settings").with(randomIp())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(current))
                .andExpect(status().isOk());
    }

    @Test
    void invalidSettingsAreRejectedFieldByField() throws Exception {
        String invalid = adminGet("/api/admin/settings")
                .replace("\"carryOverPercent\":50", "\"carryOverPercent\":150")
                .replace("\"closeTime\":\"00:00\"", "\"closeTime\":\"25:00\"");

        mockMvc.perform(put("/api/admin/settings").with(randomIp())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.carryOverPercent").exists())
                .andExpect(jsonPath("$.errors.closeTime").exists());
    }
}

package com.adarena.auction;

import com.adarena.adprofile.repository.AdProfileRepository;
import com.adarena.auction.repository.AuctionRepository;
import com.adarena.image.service.ImageService;
import com.adarena.security.TokenService;
import com.adarena.support.ApiTestSupport;
import com.adarena.support.ArenaFixtures;
import com.adarena.support.IsolatedArenaTest;
import com.adarena.user.repository.UserRepository;
import com.adarena.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** La API de la Arena por HTTP: formato de respuestas, códigos de error y seguridad. */
@IsolatedArenaTest
class ArenaApiIntegrationTest extends ApiTestSupport {

    @Autowired UserRepository userRepository;
    @Autowired AdProfileRepository adProfileRepository;
    @Autowired ImageService imageService;
    @Autowired WalletService walletService;
    @Autowired AuctionRepository auctionRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired TokenService tokenService;

    ArenaFixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures = new ArenaFixtures(userRepository, adProfileRepository, imageService, walletService,
                auctionRepository, jdbc);
        fixtures.openRound(Duration.ofHours(2));
    }

    private String tokenFor(UUID userId) {
        return tokenService.issueAccessToken(userRepository.findById(userId).orElseThrow()).value();
    }

    private ResultActions bid(String token, String idempotencyKey, long cents) throws Exception {
        var request = post("/api/arena/bids").with(randomIp())
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amountPoints\":" + cents + "}");
        if (idempotencyKey != null) request.header("Idempotency-Key", idempotencyKey);
        return mockMvc.perform(request);
    }

    @Test
    void bidAndSeeMyStatusWalletAndHistory() throws Exception {
        String token = tokenFor(fixtures.bidder("Café Aurora", 30_00));

        bid(token, "key-" + UUID.randomUUID(), 12_50)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPoints").value(12_50))
                .andExpect(jsonPath("$.position").value(1))
                .andExpect(jsonPath("$.availablePoints").value(17_50))
                .andExpect(jsonPath("$.endsAt").exists());

        mockMvc.perform(get("/api/arena/me").with(randomIp()).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roundOpen").value(true))
                .andExpect(jsonPath("$.hasAdProfile").value(true))
                .andExpect(jsonPath("$.totalPoints").value(12_50))
                .andExpect(jsonPath("$.minNextBidPoints").value(1_00));

        mockMvc.perform(get("/api/me/wallet").with(randomIp()).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.availablePoints").value(17_50))
                .andExpect(jsonPath("$.reservedPoints").value(12_50));

        mockMvc.perform(get("/api/arena/me/bids").with(randomIp()).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$[0].amountPoints").value(12_50))
                .andExpect(jsonPath("$[0].type").value("BID"));
    }

    @Test
    void errorsComeWithClearCodes() throws Exception {
        String token = tokenFor(fixtures.bidder("Sin saldo", 1_00));

        bid(token, null, 1_00)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
        bid(token, "key-" + UUID.randomUUID(), 5_00)
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
        bid(token, "key-" + UUID.randomUUID(), 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amountPoints").exists());
    }

    @Test
    void biddingRequiresLogin() throws Exception {
        mockMvc.perform(post("/api/arena/bids").with(randomIp()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountPoints\":100}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testFundsOnlyExistInDevelopment() throws Exception {
        String token = tokenFor(fixtures.bidder("Curioso", 0));

        mockMvc.perform(post("/api/dev/points").with(randomIp())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"points\":1000}"))
                .andExpect(status().isNotFound());
    }
}

package com.adarena.home;

import com.adarena.adprofile.domain.AdProfile;
import com.adarena.adprofile.repository.AdProfileRepository;
import com.adarena.adslot.domain.AdSlot;
import com.adarena.adslot.repository.AdSlotRepository;
import com.adarena.auction.domain.AdSnapshot;
import com.adarena.auction.domain.Auction;
import com.adarena.auction.domain.AuctionParticipation;
import com.adarena.auction.domain.AuctionResult;
import com.adarena.auction.repository.AuctionParticipationRepository;
import com.adarena.auction.repository.AuctionRepository;
import com.adarena.auction.repository.BidRepository;
import com.adarena.image.service.ImageService;
import com.adarena.support.ApiTestSupport;
import com.adarena.support.ArenaFixtures;
import com.adarena.support.IntegrationTest;
import com.adarena.support.TestImages;
import com.adarena.user.domain.Role;
import com.adarena.user.domain.User;
import com.adarena.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Historial público: ganadores y proyectos de rondas anteriores, con su anuncio "congelado". */
@IntegrationTest
@Transactional
class HistoryIntegrationTest extends ApiTestSupport {

    @Autowired AuctionRepository auctionRepository;
    @Autowired AuctionParticipationRepository participationRepository;
    @Autowired BidRepository bidRepository;
    @Autowired AdSlotRepository adSlotRepository;
    @Autowired AdProfileRepository adProfileRepository;
    @Autowired UserRepository userRepository;
    @Autowired ImageService imageService;

    private final Instant now = Instant.now();

    @Test
    void pastRoundsShowTheWinnerAndEveryProjectWithItsDescription() throws Exception {
        Instant close = now.minus(Duration.ofHours(2));
        Auction round = Auction.open(LocalDate.of(2090, 5, 1), close.minus(Duration.ofDays(1)), close, ArenaFixtures.RULES);
        auctionRepository.save(round);

        AuctionParticipation winner = participant(round, "Ganadora SL", 30_00);
        AuctionParticipation runnerUp = participant(round, "Segunda SL", 20_00);
        winner.markWon(1, snapshot(winner, "Ganadora SL"));
        runnerUp.markLost(2, 10_00, 10_00, snapshot(runnerUp, "Segunda SL"));
        round.close(close, AuctionResult.HAS_WINNER);
        AdSlot slot = new AdSlot(round.getId(), winner.getId(), winner.getUserId(), 1, 30_00, close,
                close.plus(Duration.ofDays(1)));
        slot.approve(null, now, null);
        adSlotRepository.save(slot);

        // Una ronda posterior en la que nadie participó
        Instant laterClose = close.plus(Duration.ofMinutes(1));
        Auction empty = Auction.open(LocalDate.of(2090, 5, 2), close, laterClose, ArenaFixtures.RULES);
        empty.close(laterClose, AuctionResult.NO_BIDS);
        auctionRepository.saveAndFlush(empty);

        mockMvc.perform(get("/api/public/history").with(randomIp()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].outcome").value("NO_BIDS"))
                .andExpect(jsonPath("$.items[1].outcome").value("WINNER"))
                .andExpect(jsonPath("$.items[1].winner.companyName").value("Ganadora SL"))
                .andExpect(jsonPath("$.items[1].winner.description").value("Proyecto Ganadora SL"))
                .andExpect(jsonPath("$.items[1].winner.totalPoints").value(30_00))
                .andExpect(jsonPath("$.items[1].projects.length()").value(2))
                .andExpect(jsonPath("$.items[1].projects[1].companyName").value("Segunda SL"))
                .andExpect(jsonPath("$.items[1].projects[1].rank").value(2));
    }

    private AuctionParticipation participant(Auction round, String company, long cents) {
        User user = userRepository.save(new User(UUID.randomUUID() + "@test.dev", "hash", company, Role.USER,
                "2026-09", now));
        UUID imageId = imageService.store(user.getId(), TestImages.opaquePng(100, 100)).getId();
        adProfileRepository.save(new AdProfile(user.getId(), company, "https://example.com", "Proyecto " + company, imageId));
        return participationRepository.save(AuctionParticipation.start(round, user.getId(), cents,
                bidRepository.nextSeq(), now.minus(Duration.ofHours(5))));
    }

    private AdSnapshot snapshot(AuctionParticipation participation, String company) {
        AdProfile profile = adProfileRepository.findByUserId(participation.getUserId()).orElseThrow();
        return new AdSnapshot(company, profile.getWebsiteUrl(), profile.getDescription(), profile.getImageId());
    }
}

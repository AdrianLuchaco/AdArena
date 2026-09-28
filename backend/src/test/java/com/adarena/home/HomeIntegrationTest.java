package com.adarena.home;

import com.adarena.adprofile.domain.AdProfile;
import com.adarena.adprofile.repository.AdProfileRepository;
import com.adarena.adslot.domain.AdSlot;
import com.adarena.adslot.repository.AdSlotRepository;
import com.adarena.auction.domain.AdSnapshot;
import com.adarena.auction.domain.Auction;
import com.adarena.auction.domain.AuctionParticipation;
import com.adarena.auction.domain.AuctionResult;
import com.adarena.auction.domain.AuctionRules;
import com.adarena.auction.repository.AuctionParticipationRepository;
import com.adarena.auction.repository.AuctionRepository;
import com.adarena.auction.repository.BidRepository;
import com.adarena.image.domain.StoredImage;
import com.adarena.image.service.ImageService;
import com.adarena.support.ApiTestSupport;
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

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La portada en cada situación posible. @Transactional: los datos de cada test se deshacen al
 * terminar, y como MockMvc se ejecuta en el mismo hilo, el endpoint los "ve" igualmente.
 */
@IntegrationTest
@Transactional
class HomeIntegrationTest extends ApiTestSupport {

    private static final AuctionRules RULES = new AuctionRules(100, 100, 50, 120, 120, 10);

    @Autowired
    AuctionRepository auctionRepository;
    @Autowired
    AuctionParticipationRepository participationRepository;
    @Autowired
    BidRepository bidRepository;
    @Autowired
    AdSlotRepository adSlotRepository;
    @Autowired
    AdProfileRepository adProfileRepository;
    @Autowired
    UserRepository userRepository;
    @Autowired
    ImageService imageService;

    private final Instant now = Instant.now();
    private final Instant lastClose = now.minus(Duration.ofHours(3));
    private final Instant nextClose = lastClose.plus(Duration.ofHours(24));

    @Test
    void withoutAuctionsTheHomeShowsBaseContent() throws Exception {
        mockMvc.perform(get("/api/public/home").with(randomIp()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("NO_AD"))
                .andExpect(jsonPath("$.currentAd").value(nullValue()))
                .andExpect(jsonPath("$.round").value(nullValue()))
                .andExpect(jsonPath("$.serverTime").exists());
    }

    @Test
    void approvedWinnerIsShownWithTheLiveRanking() throws Exception {
        Advertiser winner = advertiser("Ganador SL");
        Auction yesterday = closedAuction(AuctionResult.HAS_WINNER);
        AuctionParticipation winning = participate(yesterday, winner, 5000);
        winning.markWon(1, winner.snapshot());
        AdSlot slot = new AdSlot(yesterday.getId(), winning.getId(), winner.user().getId(), 1, 5000, lastClose, nextClose);
        slot.approve(null, now, null);
        adSlotRepository.save(slot);

        Auction today = openAuction();
        participate(today, advertiser("Segunda"), 1200);
        participate(today, advertiser("Primera"), 3000);
        participate(today, advertiser("Tercera"), 1200); // empata con "Segunda" pero pujó después

        mockMvc.perform(get("/api/public/home").with(randomIp()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("AD"))
                .andExpect(jsonPath("$.currentAd.companyName").value("Ganador SL"))
                .andExpect(jsonPath("$.currentAd.websiteUrl").value("https://example.com"))
                .andExpect(jsonPath("$.currentAd.imageUrl").value("/api/public/images/" + winner.imageId()))
                .andExpect(jsonPath("$.round.participants").value(3))
                .andExpect(jsonPath("$.round.ranking[0].companyName").value("Primera"))
                .andExpect(jsonPath("$.round.ranking[0].totalPoints").value(3000))
                // Cada proyecto se muestra con su descripción, su web y su imagen
                .andExpect(jsonPath("$.round.ranking[0].description").value("Descripción de Primera"))
                .andExpect(jsonPath("$.round.ranking[0].websiteUrl").value("https://example.com"))
                .andExpect(jsonPath("$.round.ranking[0].imageUrl").isNotEmpty())
                .andExpect(jsonPath("$.round.ranking[1].companyName").value("Segunda"))
                .andExpect(jsonPath("$.round.ranking[2].companyName").value("Tercera"))
                .andExpect(jsonPath("$.round.ranking[2].position").value(3));
    }

    @Test
    void emptyDayShowsTheNoBidsNotice() throws Exception {
        closedAuction(AuctionResult.NO_BIDS);
        openAuction();

        mockMvc.perform(get("/api/public/home").with(randomIp()))
                .andExpect(jsonPath("$.state").value("NO_BIDS"))
                .andExpect(jsonPath("$.round.participants").value(0));
    }

    @Test
    void winnerAwaitingModerationShowsPendingReview() throws Exception {
        Advertiser winner = advertiser("Pendiente SL");
        Auction yesterday = closedAuction(AuctionResult.HAS_WINNER);
        AuctionParticipation winning = participate(yesterday, winner, 2000);
        winning.markWon(1, winner.snapshot());
        adSlotRepository.save(new AdSlot(yesterday.getId(), winning.getId(), winner.user().getId(), 1, 2000,
                lastClose, nextClose));
        openAuction();

        mockMvc.perform(get("/api/public/home").with(randomIp()))
                .andExpect(jsonPath("$.state").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.currentAd").value(nullValue()));
    }

    // ------------------------------------------------------------------ helpers

    private Auction closedAuction(AuctionResult result) {
        Auction auction = Auction.open(LocalDate.now().minusDays(1), lastClose.minus(Duration.ofDays(1)), lastClose, RULES);
        auction.close(lastClose, result);
        return auctionRepository.saveAndFlush(auction);
    }

    private Auction openAuction() {
        return auctionRepository.saveAndFlush(Auction.open(LocalDate.now(), lastClose, nextClose, RULES));
    }

    private AuctionParticipation participate(Auction auction, Advertiser advertiser, long cents) {
        return participationRepository.save(AuctionParticipation.start(auction, advertiser.user().getId(), cents,
                bidRepository.nextSeq(), now));
    }

    private Advertiser advertiser(String company) {
        User user = userRepository.save(new User(UUID.randomUUID() + "@test.dev", "hash", company, Role.USER,
                "2026-09", now));
        StoredImage image = imageService.store(user.getId(), TestImages.opaquePng(100, 100));
        AdProfile profile = adProfileRepository.save(new AdProfile(user.getId(), company, "https://example.com",
                "Descripción de " + company, image.getId()));
        return new Advertiser(user, profile);
    }

    private record Advertiser(User user, AdProfile profile) {
        UUID imageId() {
            return profile.getImageId();
        }

        AdSnapshot snapshot() {
            return new AdSnapshot(profile.getCompanyName(), profile.getWebsiteUrl(), profile.getDescription(),
                    profile.getImageId());
        }
    }
}

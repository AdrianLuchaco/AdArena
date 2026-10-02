package com.adarena.adslot;

import com.adarena.adprofile.repository.AdProfileRepository;
import com.adarena.adslot.domain.AdSlot;
import com.adarena.adslot.domain.AdSlotStatus;
import com.adarena.adslot.repository.AdSlotRepository;
import com.adarena.adslot.service.ModerationService;
import com.adarena.adslot.service.ModerationService.RejectResult;
import com.adarena.auction.domain.Auction;
import com.adarena.auction.domain.AuctionParticipation;
import com.adarena.auction.domain.ParticipationOutcome;
import com.adarena.auction.repository.AuctionParticipationRepository;
import com.adarena.auction.repository.AuctionRepository;
import com.adarena.auction.service.ArenaCloseService;
import com.adarena.auction.service.BidService;
import com.adarena.common.error.ApiException;
import com.adarena.home.dto.HomeResponse;
import com.adarena.home.service.HomeService;
import com.adarena.image.service.ImageService;
import com.adarena.notification.domain.NotificationType;
import com.adarena.notification.repository.NotificationRepository;
import com.adarena.site.domain.SitePreview;
import com.adarena.site.repository.SitePreviewRepository;
import com.adarena.site.service.SitePreviewJson;
import com.adarena.support.ArenaFixtures;
import com.adarena.support.IsolatedArenaTest;
import com.adarena.user.domain.Role;
import com.adarena.user.domain.User;
import com.adarena.user.repository.UserRepository;
import com.adarena.wallet.service.WalletService;
import com.adarena.wallet.service.WalletService.Balance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Moderación del ganador con los puntos en el ledger: aprobar (con su premio de 500 puntos y su
 * presentación congelada), rechazar (100 % de vuelta y pasa el siguiente), caducar sin moderar y
 * todos los casos que no se deben poder hacer.
 * Importes del ejemplo de cierre de referencia: Ana 1.600 puntos y Luis 1.200 puntos.
 */
@IsolatedArenaTest
class ModerationIntegrationTest {

    @Autowired ModerationService moderationService;
    @Autowired ArenaCloseService closeService;
    @Autowired BidService bidService;
    @Autowired HomeService homeService;
    @Autowired WalletService walletService;
    @Autowired AuctionRepository auctionRepository;
    @Autowired AuctionParticipationRepository participationRepository;
    @Autowired AdSlotRepository adSlotRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired UserRepository userRepository;
    @Autowired AdProfileRepository adProfileRepository;
    @Autowired ImageService imageService;
    @Autowired SitePreviewRepository sitePreviewRepository;
    @Autowired SitePreviewJson sitePreviewJson;
    @Autowired JdbcTemplate jdbc;

    ArenaFixtures fixtures;
    UUID admin;
    UUID ana;
    UUID luis;
    Auction round;
    Auction tomorrow;
    AdSlot anaSlot;
    long spentBeforeClose;

    @BeforeEach
    void setUp() {
        fixtures = new ArenaFixtures(userRepository, adProfileRepository, imageService, walletService,
                auctionRepository, jdbc);
        admin = userRepository.save(new User(UUID.randomUUID() + "@admin.test", "hash", "Admin", Role.ADMIN,
                "2026-09", Instant.now())).getId();
        round = fixtures.openRound(Duration.ofHours(3));
        ana = fixtures.bidder("Ana", 50_00);
        luis = fixtures.bidder("Luis", 20_00);
        bid(ana, 10_00);
        bid(ana, 6_00);
        bid(luis, 12_00);
        spentBeforeClose = fixtures.spentPoints();
        fixtures.endRoundNow(round.getId());
        tomorrow = auctionRepository.findById(closeService.closeDueRound().orElseThrow().nextRoundId()).orElseThrow();
        anaSlot = slotOf(ana);
    }

    private void bid(UUID user, long cents) {
        bidService.placeBid(user, cents, "test-" + UUID.randomUUID());
    }

    private AdSlot slotOf(UUID user) {
        return adSlotRepository.findByAuctionIdIn(List.of(round.getId())).stream()
                .filter(slot -> slot.getUserId().equals(user)).findFirst().orElseThrow();
    }

    private AdSlot reload(AdSlot slot) {
        return adSlotRepository.findById(slot.getId()).orElseThrow();
    }

    private long spentSinceClose() {
        return fixtures.spentPoints() - spentBeforeClose;
    }

    // ------------------------------------------------------------------ aprobar

    @Test
    void approvingChargesTheWinnerAndPutsTheAdOnTheHome() {
        moderationService.approve(anaSlot.getId(), admin);

        // Caso A de la tabla: Ana 3.400 / 0 (+ 500 de premio), Luis 800 / 600, puntos gastados 2.200
        assertThat(walletService.balance(ana)).isEqualTo(new Balance(34_00 + 500, 0));
        assertThat(walletService.balance(luis)).isEqualTo(new Balance(8_00, 6_00));
        assertThat(spentSinceClose()).isEqualTo(22_00);
        assertThat(reload(anaSlot).getStatus()).isEqualTo(AdSlotStatus.APPROVED);
        assertThat(fixtures.ledgerMismatches()).isZero();

        HomeResponse home = homeService.getHome();
        assertThat(home.state()).isEqualTo(HomeResponse.HomeState.AD);
        assertThat(home.currentAd().companyName()).isEqualTo("Ana");
        assertThat(home.currentAd().wonWithPoints()).isEqualTo(16_00);
        assertThat(home.currentAd().showcase()).as("su web no se ha leído: anuncio clásico").isNull();

        // El premio para volver a pujar: un movimiento propio, una sola vez
        Long bonuses = jdbc.queryForObject("""
                SELECT count(*) FROM ledger_transactions WHERE type = 'WINNER_BONUS' AND idempotency_key = ?
                """, Long.class, "winner-bonus:" + anaSlot.getId());
        assertThat(bonuses).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT body FROM notifications WHERE user_id = ? AND type = 'AD_APPROVED'",
                String.class, ana)).contains("500 points");

        assertThat(notificationRepository.findByUserIdOrderByCreatedAtDesc(ana, Limit.of(1)))
                .extracting("type").containsExactly(NotificationType.AD_APPROVED);
        Long audit = jdbc.queryForObject("SELECT count(*) FROM admin_audit_log WHERE action = 'AD_SLOT_APPROVED' AND target_id = ?",
                Long.class, anaSlot.getId().toString());
        assertThat(audit).isEqualTo(1);
    }

    @Test
    void approvingFreezesThePresentationBuiltFromTheWinnersWebsite() {
        String url = participationRepository.findById(anaSlot.getParticipationId()).orElseThrow()
                .getAdSnapshot().websiteUrl();
        SitePreview preview = new SitePreview(url, ana, SitePreview.Source.WEB);
        preview.update(new SitePreview.Content(url, true, null, "Ana Estudio", "Diseño que se nota",
                "Diseño gráfico para marcas pequeñas.", "#1f7a5a", null, null, null, null, "[]",
                sitePreviewJson.writeHighlights(List.of("Logos en una semana", "Webs sencillas"))), Instant.now());
        sitePreviewRepository.save(preview);

        moderationService.approve(anaSlot.getId(), admin);
        HomeResponse.CurrentAd ad = homeService.getHome().currentAd();
        assertThat(ad.showcase()).isNotNull();
        assertThat(ad.showcase().title()).isEqualTo("Diseño que se nota");
        assertThat(ad.showcase().highlights()).containsExactly("Logos en una semana", "Webs sencillas");
        assertThat(ad.showcase().themeColor()).isEqualTo("#1f7a5a");

        // Si su web cambia después, la portada sigue mostrando lo que se aprobó
        SitePreview changed = sitePreviewRepository.findByUrl(url).orElseThrow();
        changed.update(new SitePreview.Content(url, true, null, "Otra cosa", "Texto que nadie ha revisado", null,
                null, null, null, null, null, "[]", "[]"), Instant.now());
        sitePreviewRepository.save(changed);
        assertThat(homeService.getHome().currentAd().showcase().title()).isEqualTo("Diseño que se nota");
    }

    @Test
    void aSlotCanOnlyBeModeratedOnce() {
        moderationService.approve(anaSlot.getId(), admin);

        assertThatThrownBy(() -> moderationService.approve(anaSlot.getId(), admin))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("AD_SLOT_NOT_PENDING");
        assertThatThrownBy(() -> moderationService.reject(anaSlot.getId(), admin, "Demasiado tarde"))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("AD_SLOT_NOT_PENDING");
        assertThat(spentSinceClose()).isEqualTo(22_00); // no se cobra dos veces
    }

    // ------------------------------------------------------------------ rechazar

    @Test
    void rejectingRefundsEverythingAndTheRunnerUpBecomesTheCandidate() {
        RejectResult result = moderationService.reject(anaSlot.getId(), admin, "La imagen no se ve bien");

        // Caso B de la tabla: Ana recupera sus 5.000 puntos, Luis queda retenido como candidato con 600 puntos
        assertThat(result.refundedPoints()).isEqualTo(16_00);
        assertThat(walletService.balance(ana)).isEqualTo(new Balance(50_00, 0));
        assertThat(walletService.balance(luis)).isEqualTo(new Balance(8_00, 6_00));
        assertThat(spentSinceClose()).isEqualTo(6_00);
        assertThat(fixtures.ledgerMismatches()).isZero();

        AdSlot rejected = reload(anaSlot);
        assertThat(rejected.getStatus()).isEqualTo(AdSlotStatus.REJECTED);
        assertThat(rejected.getRejectionReason()).isEqualTo("La imagen no se ve bien");
        assertThat(participationRepository.findById(rejected.getParticipationId()).orElseThrow().getOutcome())
                .isEqualTo(ParticipationOutcome.REFUNDED);

        // Luis pasa a ser el candidato, con la misma ventana, y su arrastre sale de la ronda de hoy
        AdSlot luisSlot = adSlotRepository.findById(result.promotedSlotId()).orElseThrow();
        assertThat(luisSlot.getUserId()).isEqualTo(luis);
        assertThat(luisSlot.getCandidateRank()).isEqualTo(2);
        assertThat(luisSlot.getAmountPoints()).isEqualTo(12_00);
        assertThat(luisSlot.getStatus()).isEqualTo(AdSlotStatus.PENDING_REVIEW);
        assertThat(luisSlot.getStartsAt()).isEqualTo(anaSlot.getStartsAt());
        assertThat(luisSlot.getEndsAt()).isEqualTo(anaSlot.getEndsAt());
        AuctionParticipation luisToday = participationRepository.findByAuctionIdAndUserId(tomorrow.getId(), luis).orElseThrow();
        assertThat(luisToday.getTotalPoints()).isZero();
        assertThat(luisToday.getCarriedInPoints()).isZero();
        assertThat(homeService.getHome().round().ranking()).isEmpty();

        assertThat(notificationRepository.findByUserIdOrderByCreatedAtDesc(ana, Limit.of(1)))
                .extracting("type").containsExactly(NotificationType.AD_REJECTED);
        assertThat(notificationRepository.findByUserIdOrderByCreatedAtDesc(luis, Limit.of(1)))
                .extracting("type").containsExactly(NotificationType.CANDIDATE_PROMOTED);
    }

    @Test
    void approvingThePromotedCandidateChargesItsFullBid() {
        RejectResult result = moderationService.reject(anaSlot.getId(), admin, "No cumple las normas");

        moderationService.approve(result.promotedSlotId(), admin);

        // "Caso B, apruebas a Luis": Luis 800 / 0 (+ 500 de premio) y puntos gastados 1.200 (su puja completa)
        assertThat(walletService.balance(luis)).isEqualTo(new Balance(8_00 + 500, 0));
        assertThat(walletService.balance(ana)).isEqualTo(new Balance(50_00, 0));
        assertThat(spentSinceClose()).isEqualTo(12_00);
        assertThat(fixtures.ledgerMismatches()).isZero();
        assertThat(homeService.getHome().currentAd().companyName()).isEqualTo("Luis");
    }

    @Test
    void rejectingThePromotedCandidateAlsoRefundsItsWholeBid() {
        RejectResult first = moderationService.reject(anaSlot.getId(), admin, "No cumple las normas");

        RejectResult second = moderationService.reject(first.promotedSlotId(), admin, "Tampoco cumple");

        // Luis recupera sus 1.200 puntos: los 600 puntos retenidos + los 600 puntos que ya se habían gastado
        assertThat(second.refundedPoints()).isEqualTo(12_00);
        assertThat(second.promotedSlotId()).isNull(); // no quedan más clasificados
        assertThat(walletService.balance(luis)).isEqualTo(new Balance(20_00, 0));
        assertThat(spentSinceClose()).isZero();
        assertThat(fixtures.ledgerMismatches()).isZero();
        assertThat(adSlotRepository.findByAuctionIdIn(List.of(round.getId())))
                .extracting("status").containsOnly(AdSlotStatus.REJECTED); // ese día la portada queda libre
    }

    @Test
    void aRejectionNeedsAReason() {
        assertThatThrownBy(() -> moderationService.reject(anaSlot.getId(), admin, "  "))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("REASON_REQUIRED");
        assertThat(reload(anaSlot).getStatus()).isEqualTo(AdSlotStatus.PENDING_REVIEW);
    }

    @Test
    void theEmptiedParticipationDoesNotRankWhenTodaysRoundCloses() {
        moderationService.reject(anaSlot.getId(), admin, "No cumple las normas"); // Luis se queda en 0 puntos hoy
        UUID bea = fixtures.bidder("Bea", 10_00);
        bid(bea, 2_00);

        fixtures.endRoundNow(tomorrow.getId());
        var result = closeService.closeDueRound().orElseThrow();

        assertThat(result.winnerUserId()).isEqualTo(bea);
        assertThat(result.participants()).isEqualTo(1);
        AuctionParticipation luisToday = participationRepository.findByAuctionIdAndUserId(tomorrow.getId(), luis).orElseThrow();
        assertThat(luisToday.getFinalRank()).isNull();
        assertThat(luisToday.getOutcome()).isEqualTo(ParticipationOutcome.LOST);
        assertThat(walletService.balance(luis)).isEqualTo(new Balance(8_00, 6_00)); // sigue retenido como candidato
        assertThat(fixtures.ledgerMismatches()).isZero();
    }

    // ------------------------------------------------------------------ caducidad

    @Test
    void anUnmoderatedWinnerIsRefundedWhenItsWindowEnds() {
        endWindow(anaSlot);

        int expired = moderationService.expireOverdueSlots();

        assertThat(expired).isPositive(); // (puede caducar también alguno que dejó otro test en esta base de datos)
        assertThat(reload(anaSlot).getStatus()).isEqualTo(AdSlotStatus.EXPIRED);
        assertThat(walletService.balance(ana)).isEqualTo(new Balance(50_00, 0));
        assertThat(notificationRepository.findByUserIdOrderByCreatedAtDesc(ana, Limit.of(1)))
                .extracting("type").containsExactly(NotificationType.WINNER_REFUNDED);
        assertThat(moderationService.expireOverdueSlots()).isZero(); // idempotente
        assertThat(fixtures.ledgerMismatches()).isZero();
    }

    @Test
    void anExpiredPromotedCandidateGetsBackItsWholeBid() {
        RejectResult result = moderationService.reject(anaSlot.getId(), admin, "No cumple las normas");
        AdSlot luisSlot = adSlotRepository.findById(result.promotedSlotId()).orElseThrow();
        endWindow(luisSlot);

        moderationService.expireOverdueSlots();

        assertThat(walletService.balance(luis)).isEqualTo(new Balance(20_00, 0));
        assertThat(spentSinceClose()).isZero();
        assertThat(fixtures.ledgerMismatches()).isZero();
    }

    @Test
    void nothingCanBeModeratedOnceTheWindowIsOver() {
        endWindow(anaSlot);

        assertThatThrownBy(() -> moderationService.approve(anaSlot.getId(), admin))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("AD_SLOT_WINDOW_OVER");
        assertThat(walletService.balance(ana)).isEqualTo(new Balance(34_00, 16_00));
    }

    /** Mueve la ventana del hueco al pasado, como si hubieran pasado sus 24 horas. */
    private void endWindow(AdSlot slot) {
        Instant end = Instant.now().minusSeconds(5);
        jdbc.update("UPDATE ad_slots SET starts_at = ?, ends_at = ? WHERE id = ?",
                Timestamp.from(end.minus(Duration.ofHours(24))), Timestamp.from(end), slot.getId());
    }
}

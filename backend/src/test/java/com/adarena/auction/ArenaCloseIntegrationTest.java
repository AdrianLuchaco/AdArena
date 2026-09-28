package com.adarena.auction;

import com.adarena.adprofile.repository.AdProfileRepository;
import com.adarena.adslot.domain.AdSlot;
import com.adarena.adslot.domain.AdSlotStatus;
import com.adarena.adslot.repository.AdSlotRepository;
import com.adarena.auction.domain.Auction;
import com.adarena.auction.domain.AuctionParticipation;
import com.adarena.auction.domain.AuctionResult;
import com.adarena.auction.domain.AuctionStatus;
import com.adarena.auction.domain.ParticipationOutcome;
import com.adarena.auction.repository.AuctionParticipationRepository;
import com.adarena.auction.repository.AuctionRepository;
import com.adarena.auction.service.ArenaCloseService;
import com.adarena.auction.service.ArenaCloseService.CloseResult;
import com.adarena.auction.service.BidService;
import com.adarena.image.service.ImageService;
import com.adarena.notification.domain.NotificationType;
import com.adarena.notification.repository.NotificationRepository;
import com.adarena.support.ArenaFixtures;
import com.adarena.support.IsolatedArenaTest;
import com.adarena.user.repository.UserRepository;
import com.adarena.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El cierre diario contra PostgreSQL real: ganador, arrastre del 50 %, redondeo, empates, día
 * vacío, ronda siguiente, idempotencia y avisos. En todos se comprueba que el dinero cuadra.
 */
@IsolatedArenaTest
class ArenaCloseIntegrationTest {

    @Autowired ArenaCloseService closeService;
    @Autowired BidService bidService;
    @Autowired WalletService walletService;
    @Autowired AuctionRepository auctionRepository;
    @Autowired AuctionParticipationRepository participationRepository;
    @Autowired AdSlotRepository adSlotRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired UserRepository userRepository;
    @Autowired AdProfileRepository adProfileRepository;
    @Autowired ImageService imageService;
    @Autowired JdbcTemplate jdbc;

    ArenaFixtures fixtures;
    Auction round;

    @BeforeEach
    void setUp() {
        fixtures = new ArenaFixtures(userRepository, adProfileRepository, imageService, walletService,
                auctionRepository, jdbc);
        round = fixtures.openRound(Duration.ofHours(3));
    }

    private void bid(UUID user, long cents) {
        bidService.placeBid(user, cents, "test-" + UUID.randomUUID());
    }

    private CloseResult closeNow(Auction auction) {
        fixtures.endRoundNow(auction.getId());
        return closeService.closeDueRound().orElseThrow();
    }

    private AuctionParticipation participation(UUID auctionId, UUID userId) {
        return participationRepository.findByAuctionIdAndUserId(auctionId, userId).orElseThrow();
    }

    @Test
    void nothingHappensBeforeTheCountdownEnds() {
        assertThat(closeService.closeDueRound()).isEmpty();
        assertThat(auctionRepository.findById(round.getId()).orElseThrow().getStatus()).isEqualTo(AuctionStatus.OPEN);
    }

    @Test
    void anEmptyDayClosesWithoutWinnerAndTheNextRoundOpens() {
        CloseResult result = closeNow(round);

        assertThat(result.result()).isEqualTo(AuctionResult.NO_BIDS); // regla 8
        assertThat(result.winnerUserId()).isNull();
        Auction closed = auctionRepository.findById(round.getId()).orElseThrow();
        assertThat(closed.getStatus()).isEqualTo(AuctionStatus.CLOSED);
        assertThat(adSlotRepository.findByAuctionIdIn(List.of(round.getId()))).isEmpty();

        Auction next = auctionRepository.findById(result.nextRoundId()).orElseThrow();
        assertThat(next.isOpen()).isTrue();
        assertThat(next.getEndsAt()).isAfter(Instant.now());
        assertThat(next.acceptsBidsAt(Instant.now())).isTrue();
    }

    @Test
    void closingTwiceNeverDuplicatesAnything() {
        UUID ana = fixtures.bidder("Ana", 20_00);
        bid(ana, 5_00);
        closeNow(round);

        assertThat(closeService.closeDueRound()).isEmpty(); // la ronda nueva aún no ha terminado
        assertThat(adSlotRepository.findByAuctionIdIn(List.of(round.getId()))).hasSize(1);
        Long open = jdbc.queryForObject("SELECT count(*) FROM auctions WHERE status = 'OPEN'", Long.class);
        assertThat(open).isEqualTo(1);
    }

    /** El ejemplo del §3.6 de ARCHITECTURE.md, céntimo a céntimo. */
    @Test
    void theWinnerKeepsItsMoneyOnHoldAndTheOthersKeepHalfForTomorrow() {
        UUID ana = fixtures.bidder("Ana", 50_00);
        UUID luis = fixtures.bidder("Luis", 20_00);
        bid(ana, 10_00);
        bid(ana, 6_00);
        bid(luis, 12_00);
        long spentBefore = fixtures.spentPoints();

        CloseResult result = closeNow(round);

        assertThat(result.result()).isEqualTo(AuctionResult.HAS_WINNER);
        assertThat(result.winnerUserId()).isEqualTo(ana);
        assertThat(result.winnerTotalPoints()).isEqualTo(16_00);
        assertThat(result.forfeitedPoints()).isEqualTo(6_00);
        assertThat(result.carriedPoints()).isEqualTo(6_00);

        // Dinero: tabla "Cierre (Ana 1.ª, Luis pierde 50 %)"
        assertThat(walletService.balance(ana)).isEqualTo(new WalletService.Balance(34_00, 16_00));
        assertThat(walletService.balance(luis)).isEqualTo(new WalletService.Balance(8_00, 6_00));
        assertThat(fixtures.spentPoints() - spentBefore).isEqualTo(6_00);
        assertThat(fixtures.ledgerMismatches()).isZero();

        // Resultado de cada uno, con la foto de su anuncio
        AuctionParticipation anaResult = participation(round.getId(), ana);
        assertThat(anaResult.getOutcome()).isEqualTo(ParticipationOutcome.WON);
        assertThat(anaResult.getFinalRank()).isEqualTo(1);
        assertThat(anaResult.getAdSnapshot().companyName()).isEqualTo("Ana");
        AuctionParticipation luisResult = participation(round.getId(), luis);
        assertThat(luisResult.getOutcome()).isEqualTo(ParticipationOutcome.LOST);
        assertThat(luisResult.getFinalRank()).isEqualTo(2);
        assertThat(luisResult.getForfeitedPoints()).isEqualTo(6_00);
        assertThat(luisResult.getCarriedOutPoints()).isEqualTo(6_00);

        // El hueco de Ana en portada, pendiente de moderación, con su ventana fija
        Auction closed = auctionRepository.findById(round.getId()).orElseThrow();
        Auction next = auctionRepository.findById(result.nextRoundId()).orElseThrow();
        AdSlot slot = adSlotRepository.findByAuctionIdIn(List.of(round.getId())).getFirst();
        assertThat(slot.getStatus()).isEqualTo(AdSlotStatus.PENDING_REVIEW);
        assertThat(slot.getUserId()).isEqualTo(ana);
        assertThat(slot.getAmountPoints()).isEqualTo(16_00);
        assertThat(slot.getStartsAt()).isEqualTo(closed.getScheduledEndAt());
        assertThat(slot.getEndsAt()).isEqualTo(next.getScheduledEndAt());

        // Regla 7: Luis empieza mañana con 600 puntos sin hacer nada
        AuctionParticipation luisTomorrow = participation(next.getId(), luis);
        assertThat(luisTomorrow.getTotalPoints()).isEqualTo(6_00);
        assertThat(luisTomorrow.getCarriedInPoints()).isEqualTo(6_00);
        assertThat(luisTomorrow.getCarriedFromParticipationId()).isEqualTo(luisResult.getId());
        Long carryBids = jdbc.queryForObject(
                "SELECT count(*) FROM bids WHERE participation_id = ? AND type = 'CARRY_OVER' AND amount_points = 600",
                Long.class, luisTomorrow.getId());
        assertThat(carryBids).isEqualTo(1);
        assertThat(participationRepository.findByAuctionIdAndUserId(next.getId(), ana)).isEmpty();

        // Avisos: Ana gana (con email), Luis no (solo aviso en la web)
        assertThat(notificationRepository.findByUserIdOrderByCreatedAtDesc(ana, Limit.of(5)))
                .extracting("type").contains(NotificationType.AUCTION_WON);
        assertThat(notificationRepository.findByUserIdOrderByCreatedAtDesc(luis, Limit.of(5)))
                .extracting("type").contains(NotificationType.AUCTION_LOST);
        String anaEmail = userRepository.findById(ana).orElseThrow().getEmail();
        Long emails = jdbc.queryForObject("SELECT count(*) FROM email_outbox WHERE to_email = ? AND subject LIKE '%won%'",
                Long.class, anaEmail);
        assertThat(emails).isEqualTo(1);
    }

    @Test
    void theCarryIsRoundedDownToTheCent() {
        UUID winner = fixtures.bidder("Ganadora", 20_00);
        UUID loser = fixtures.bidder("Perdedor", 20_00);
        bid(winner, 5_00);
        bid(loser, 1_01);

        closeNow(round);

        AuctionParticipation lost = participation(round.getId(), loser);
        assertThat(lost.getCarriedOutPoints()).isEqualTo(50);   // 101 puntos al 50 % → 50 puntos arrastrados…
        assertThat(lost.getForfeitedPoints()).isEqualTo(51);    // …y 51 puntos perdidos: nunca aparecen puntos de la nada
        assertThat(walletService.balance(loser).reservedPoints()).isEqualTo(50);
        assertThat(fixtures.ledgerMismatches()).isZero();
    }

    @Test
    void onATieWhoeverGotThereFirstWins() {
        UUID first = fixtures.bidder("Primera", 20_00);
        UUID second = fixtures.bidder("Segunda", 20_00);
        bid(first, 7_00);
        bid(second, 7_00);

        CloseResult result = closeNow(round);

        assertThat(result.winnerUserId()).isEqualTo(first);
        assertThat(participation(round.getId(), second).getFinalRank()).isEqualTo(2);
    }

    @Test
    void theRunnerUpCanWinTomorrowWithItsCarryAlone() {
        UUID ana = fixtures.bidder("Ana", 50_00);
        UUID luis = fixtures.bidder("Luis", 50_00);
        bid(ana, 20_00);
        bid(luis, 10_00);
        Auction tomorrow = auctionRepository.findById(closeNow(round).nextRoundId()).orElseThrow();

        // Mañana nadie más puja: Luis gana con sus 500 puntos arrastrados (regla 7)
        CloseResult second = closeNow(tomorrow);

        assertThat(second.winnerUserId()).isEqualTo(luis);
        assertThat(second.winnerTotalPoints()).isEqualTo(5_00);
        assertThat(walletService.balance(luis)).isEqualTo(new WalletService.Balance(40_00, 5_00));
        assertThat(fixtures.ledgerMismatches()).isZero();
    }

    @Test
    void losingAgainKeepsHalfOfTheNewTotal() {
        UUID ana = fixtures.bidder("Ana", 100_00);
        UUID luis = fixtures.bidder("Luis", 100_00);
        bid(ana, 30_00);
        bid(luis, 20_00);                                   // pierde: arrastra 1.000 puntos
        Auction tomorrow = auctionRepository.findById(closeNow(round).nextRoundId()).orElseThrow();

        UUID bea = fixtures.bidder("Bea", 100_00);
        bid(luis, 4_00);                                    // 1.000 puntos arrastrados + 400 puntos = 1.400 puntos
        bid(bea, 30_00);
        closeNow(tomorrow);

        AuctionParticipation luisDay2 = participation(tomorrow.getId(), luis);
        assertThat(luisDay2.getTotalPoints()).isEqualTo(14_00);
        assertThat(luisDay2.getCarriedOutPoints()).isEqualTo(7_00); // 50 % del NUEVO total
        assertThat(walletService.balance(luis)).isEqualTo(new WalletService.Balance(76_00, 7_00));
        assertThat(fixtures.ledgerMismatches()).isZero();
    }

    @Test
    void bidsAfterTheCloseGoToTheNewRound() {
        UUID ana = fixtures.bidder("Ana", 20_00);
        UUID next = closeNow(round).nextRoundId();

        bid(ana, 3_00);

        assertThat(participation(next, ana).getTotalPoints()).isEqualTo(3_00);
        assertThat(participationRepository.findByAuctionIdAndUserId(round.getId(), ana)).isEmpty();
    }
}

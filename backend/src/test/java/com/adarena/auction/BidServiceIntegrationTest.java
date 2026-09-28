package com.adarena.auction;

import com.adarena.adprofile.repository.AdProfileRepository;
import com.adarena.auction.domain.Auction;
import com.adarena.auction.dto.BidResponse;
import com.adarena.auction.event.ArenaChangedEvent;
import com.adarena.auction.repository.AuctionParticipationRepository;
import com.adarena.auction.repository.AuctionRepository;
import com.adarena.auction.service.BidService;
import com.adarena.common.error.ApiException;
import com.adarena.image.service.ImageService;
import com.adarena.support.ArenaFixtures;
import com.adarena.support.IsolatedArenaTest;
import com.adarena.user.repository.UserRepository;
import com.adarena.wallet.domain.InsufficientFundsException;
import com.adarena.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Reglas de puja contra PostgreSQL real: acumulación, mínimos, saldo, idempotencia,
 * anti-sniping, desempate, "te han superado" y, sobre todo, pujas SIMULTÁNEAS.
 */
@IsolatedArenaTest
@RecordApplicationEvents
class BidServiceIntegrationTest {

    @Autowired BidService bidService;
    @Autowired WalletService walletService;
    @Autowired AuctionRepository auctionRepository;
    @Autowired AuctionParticipationRepository participationRepository;
    @Autowired UserRepository userRepository;
    @Autowired AdProfileRepository adProfileRepository;
    @Autowired ImageService imageService;
    @Autowired JdbcTemplate jdbc;
    @Autowired ApplicationEvents events;

    ArenaFixtures fixtures;
    Auction round;

    @BeforeEach
    void setUp() {
        fixtures = new ArenaFixtures(userRepository, adProfileRepository, imageService, walletService,
                auctionRepository, jdbc);
        round = fixtures.openRound(Duration.ofHours(3));
    }

    private static String key() {
        return "test-" + UUID.randomUUID();
    }

    // ------------------------------------------------------------------ lo básico

    @Test
    void aBidReservesTheMoneyFromTheWallet() {
        UUID ana = fixtures.bidder("Ana", 50_00);

        BidResponse response = bidService.placeBid(ana, 10_00, key());

        assertThat(response.totalPoints()).isEqualTo(10_00);
        assertThat(response.position()).isEqualTo(1);
        assertThat(response.availablePoints()).isEqualTo(40_00);
        assertThat(response.reservedPoints()).isEqualTo(10_00);
        assertThat(fixtures.ledgerMismatches()).isZero();
    }

    @Test
    void bidsOfTheSameDayAddUp() {
        UUID ana = fixtures.bidder("Ana", 50_00);

        bidService.placeBid(ana, 10_00, key());
        BidResponse second = bidService.placeBid(ana, 6_00, key());

        assertThat(second.totalPoints()).isEqualTo(16_00); // regla 2: 1.000 puntos + 600 puntos = 1.600 puntos
        assertThat(second.reservedPoints()).isEqualTo(16_00);
    }

    @Test
    void minimumBidAndMinimumIncrementAreEnforced() {
        UUID ana = fixtures.bidder("Ana", 50_00);

        assertThatThrownBy(() -> bidService.placeBid(ana, 50, key()))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("BID_TOO_LOW");

        bidService.placeBid(ana, 1_00, key());
        assertThatThrownBy(() -> bidService.placeBid(ana, 99, key()))
                .isInstanceOf(ApiException.class).hasMessageContaining("at least 100 points");
    }

    @Test
    void cannotBidMoreThanTheAvailableBalance() {
        UUID ana = fixtures.bidder("Ana", 5_00);

        assertThatThrownBy(() -> bidService.placeBid(ana, 6_00, key())).isInstanceOf(InsufficientFundsException.class);

        assertThat(walletService.balance(ana).availablePoints()).isEqualTo(5_00);
        assertThat(participationRepository.findByAuctionIdAndUserId(round.getId(), ana)).isEmpty();
    }

    @Test
    void anAdIsRequiredToBid() {
        UUID noProfile = fixtures.userWithoutProfile("Sin anuncio");

        assertThatThrownBy(() -> bidService.placeBid(noProfile, 1_00, key()))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("AD_PROFILE_REQUIRED");
    }

    @Test
    void noBidsWithoutAnOpenRoundOrAfterItsEnd() {
        UUID ana = fixtures.bidder("Ana", 50_00);

        fixtures.closeOpenRounds();
        assertThatThrownBy(() -> bidService.placeBid(ana, 1_00, key()))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("NO_OPEN_ROUND");

        fixtures.openRound(Duration.ofSeconds(-30)); // terminó hace 30 s y aún no se ha cerrado
        assertThatThrownBy(() -> bidService.placeBid(ana, 1_00, key()))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("ROUND_CLOSED");
    }

    @Test
    void retryingWithTheSameKeyDoesNotChargeTwice() {
        UUID ana = fixtures.bidder("Ana", 50_00);
        String key = key();

        bidService.placeBid(ana, 10_00, key);
        BidResponse retry = bidService.placeBid(ana, 10_00, key); // doble clic o reintento de red

        assertThat(retry.replayed()).isTrue();
        assertThat(retry.totalPoints()).isEqualTo(10_00);
        assertThat(walletService.balance(ana).availablePoints()).isEqualTo(40_00);
    }

    // ------------------------------------------------------------------ anti-sniping y desempate

    @Test
    void aBidInTheLastTwoMinutesExtendsTheRound() {
        Auction ending = fixtures.openRound(Duration.ofSeconds(60));
        UUID ana = fixtures.bidder("Ana", 50_00);

        BidResponse response = bidService.placeBid(ana, 1_00, key());

        assertThat(response.extended()).isTrue();
        assertThat(response.endsAt()).isEqualTo(ending.getEndsAt().plusSeconds(120));
    }

    @Test
    void anEarlierBidDoesNotExtendTheRound() {
        UUID ana = fixtures.bidder("Ana", 50_00);

        BidResponse response = bidService.placeBid(ana, 1_00, key());

        assertThat(response.extended()).isFalse();
        assertThat(response.endsAt()).isEqualTo(round.getEndsAt());
    }

    @Test
    void onATieWhoeverGotThereFirstLeads() {
        UUID first = fixtures.bidder("Primera", 50_00);
        UUID second = fixtures.bidder("Segunda", 50_00);

        bidService.placeBid(first, 10_00, key());
        BidResponse late = bidService.placeBid(second, 10_00, key());

        assertThat(late.position()).isEqualTo(2);
    }

    // ------------------------------------------------------------------ "te han superado" (regla 4)

    @Test
    void usersWhoFallBehindAreNotified() {
        UUID ana = fixtures.bidder("Ana", 50_00);
        UUID luis = fixtures.bidder("Luis", 50_00);

        bidService.placeBid(ana, 10_00, key());
        bidService.placeBid(luis, 12_00, key());
        assertThat(lastEvent().outbidUserIds()).containsExactly(ana);

        bidService.placeBid(ana, 1_00, key()); // Ana llega a 1.100 puntos: sigue por detrás de Luis
        assertThat(lastEvent().outbidUserIds()).isEmpty();

        bidService.placeBid(ana, 2_00, key()); // Ana llega a 1.300 puntos: supera a Luis
        assertThat(lastEvent().outbidUserIds()).containsExactly(luis);
    }

    private ArenaChangedEvent lastEvent() {
        List<ArenaChangedEvent> all = events.stream(ArenaChangedEvent.class).toList();
        return all.getLast();
    }

    // ------------------------------------------------------------------ concurrencia

    @Test
    void simultaneousBidsFromOneUserNeverSpendMoreThanTheBalance() throws Exception {
        UUID ana = fixtures.bidder("Ana", 15_00);
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        runConcurrently(20, i -> {
            try {
                bidService.placeBid(ana, 1_00, key());
                accepted.incrementAndGet();
            } catch (InsufficientFundsException e) {
                rejected.incrementAndGet();
            }
        });

        assertThat(accepted.get()).isEqualTo(15);
        assertThat(rejected.get()).isEqualTo(5);
        assertThat(walletService.balance(ana).availablePoints()).isZero();
        assertThat(walletService.balance(ana).reservedPoints()).isEqualTo(15_00);
        assertThat(participationRepository.findByAuctionIdAndUserId(round.getId(), ana).orElseThrow().getTotalPoints())
                .isEqualTo(15_00);
        assertThat(fixtures.ledgerMismatches()).isZero();
    }

    @Test
    void simultaneousBidsFromManyUsersKeepEveryTotalExact() throws Exception {
        List<UUID> users = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            users.add(fixtures.bidder("Proyecto " + i, 20_00));
        }

        runConcurrently(40, i -> bidService.placeBid(users.get(i % users.size()), 1_00, key()));

        for (UUID user : users) {
            assertThat(participationRepository.findByAuctionIdAndUserId(round.getId(), user).orElseThrow().getTotalPoints())
                    .isEqualTo(5_00);
            assertThat(walletService.balance(user).reservedPoints()).isEqualTo(5_00);
        }
        Long distinctSeqs = jdbc.queryForObject(
                "SELECT count(DISTINCT seq) FROM bids WHERE auction_id = ?", Long.class, round.getId());
        assertThat(distinctSeqs).isEqualTo(40);
        assertThat(fixtures.ledgerMismatches()).isZero();
    }

    private interface Task {
        void run(int index) throws Exception;
    }

    /** Lanza {@code count} tareas a la vez (todas esperan a una señal de salida común). */
    private static void runConcurrently(int count, Task task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(count);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Void>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                final int index = i;
                Callable<Void> callable = () -> {
                    start.await();
                    task.run(index);
                    return null;
                };
                futures.add(pool.submit(callable));
            }
            start.countDown();
            for (Future<Void> future : futures) {
                future.get();
            }
        } finally {
            pool.shutdownNow();
        }
    }
}

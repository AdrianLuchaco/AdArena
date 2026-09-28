package com.adarena.support;

import com.adarena.adprofile.domain.AdProfile;
import com.adarena.adprofile.repository.AdProfileRepository;
import com.adarena.auction.domain.Auction;
import com.adarena.auction.domain.AuctionRules;
import com.adarena.auction.repository.AuctionRepository;
import com.adarena.image.domain.StoredImage;
import com.adarena.image.service.ImageService;
import com.adarena.user.domain.Role;
import com.adarena.user.domain.User;
import com.adarena.user.repository.UserRepository;
import com.adarena.wallet.service.WalletService;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** Crea datos de prueba para la Arena: participantes con anuncio y saldo, y rondas abiertas. */
public class ArenaFixtures {

    /** Reglas por defecto: mínimo 100 puntos, incremento 100 puntos, arrastre 50 %, anti-sniping 120 s / 120 s / 10. */
    public static final AuctionRules RULES = new AuctionRules(100, 100, 50, 120, 120, 10);
    private static final AtomicInteger ROUND_COUNTER = new AtomicInteger();

    private final UserRepository userRepository;
    private final AdProfileRepository adProfileRepository;
    private final ImageService imageService;
    private final WalletService walletService;
    private final AuctionRepository auctionRepository;
    private final JdbcTemplate jdbc;

    public ArenaFixtures(UserRepository userRepository, AdProfileRepository adProfileRepository,
                         ImageService imageService, WalletService walletService,
                         AuctionRepository auctionRepository, JdbcTemplate jdbc) {
        this.userRepository = userRepository;
        this.adProfileRepository = adProfileRepository;
        this.imageService = imageService;
        this.walletService = walletService;
        this.auctionRepository = auctionRepository;
        this.jdbc = jdbc;
    }

    /** Un participante con anuncio y {@code fundsPoints} puntos. */
    public UUID bidder(String company, long fundsPoints) {
        UUID userId = userWithoutProfile(company);
        StoredImage image = imageService.store(userId, TestImages.opaquePng(100, 100));
        adProfileRepository.save(new AdProfile(userId, company, "https://example.com/" + userId,
                "Descripción del proyecto " + company, image.getId()));
        if (fundsPoints > 0) {
            walletService.grantTestPoints(userId, fundsPoints);
        }
        return userId;
    }

    public UUID userWithoutProfile(String name) {
        return userRepository.save(new User(UUID.randomUUID() + "@test.dev", "hash", name, Role.USER, "2026-09",
                Instant.now())).getId();
    }

    /** Cierra cualquier ronda abierta y abre una nueva que termina dentro de {@code endsIn}. */
    public Auction openRound(Duration endsIn) {
        closeOpenRounds();
        Instant now = Instant.now();
        Instant end = now.plus(endsIn);
        Instant opens = end.minus(Duration.ofHours(24));
        LocalDate uniqueDate = LocalDate.of(2100, 1, 1).plusDays(ROUND_COUNTER.incrementAndGet());
        return auctionRepository.saveAndFlush(Auction.open(uniqueDate, opens, end, RULES));
    }

    /** Adelanta el fin de una ronda a "ahora mismo", como si hubiera llegado la medianoche. */
    public void endRoundNow(UUID auctionId) {
        // Hora de Java (no la de PostgreSQL): el reloj del contenedor Docker puede ir unos ms desfasado.
        // GREATEST: el fin siempre tiene que ser posterior a la apertura (restricción de la tabla).
        Timestamp justBefore = Timestamp.from(Instant.now().minusMillis(200));
        jdbc.update("""
                UPDATE auctions
                SET scheduled_end_at = GREATEST(opens_at + interval '1 millisecond', ?),
                    ends_at = GREATEST(opens_at + interval '1 millisecond', ?)
                WHERE id = ?
                """, justBefore, justBefore, auctionId);
    }

    /** Saldo de puntos gastados (cuenta POINTS_SPENT). */
    public long spentPoints() {
        Long value = jdbc.queryForObject(
                "SELECT balance_points FROM ledger_accounts WHERE user_id IS NULL AND type = 'POINTS_SPENT'", Long.class);
        return value == null ? 0 : value;
    }

    public void closeOpenRounds() {
        jdbc.update("UPDATE auctions SET status = 'CLOSED', result = 'NO_BIDS', closed_at = now() WHERE status = 'OPEN'");
    }

    public long ledgerMismatches() {
        Long count = jdbc.queryForObject("SELECT count(*) FROM ledger_account_mismatches", Long.class);
        return count == null ? 0 : count;
    }
}

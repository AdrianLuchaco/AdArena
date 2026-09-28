package com.adarena.demo;

import com.adarena.adprofile.domain.AdProfile;
import com.adarena.adprofile.repository.AdProfileRepository;
import com.adarena.adslot.domain.AdSlot;
import com.adarena.adslot.repository.AdSlotRepository;
import com.adarena.auction.domain.AdSnapshot;
import com.adarena.auction.domain.Auction;
import com.adarena.auction.domain.AuctionParticipation;
import com.adarena.auction.domain.AuctionResult;
import com.adarena.auction.domain.AuctionRules;
import com.adarena.auction.domain.Bid;
import com.adarena.auction.repository.AuctionParticipationRepository;
import com.adarena.auction.repository.AuctionRepository;
import com.adarena.auction.repository.BidRepository;
import com.adarena.common.config.AppProperties;
import com.adarena.image.domain.StoredImage;
import com.adarena.image.service.ImageService;
import com.adarena.settings.domain.AppSettings;
import com.adarena.settings.repository.AppSettingsRepository;
import com.adarena.site.domain.SitePreview;
import com.adarena.site.service.SitePreviewJson;
import com.adarena.site.service.SitePreviewService;
import com.adarena.user.domain.Role;
import com.adarena.user.domain.User;
import com.adarena.user.repository.UserRepository;
import com.adarena.wallet.domain.LedgerAccount;
import com.adarena.wallet.domain.LedgerAccountType;
import com.adarena.wallet.domain.LedgerTransaction;
import com.adarena.wallet.domain.LedgerTransactionType;
import com.adarena.wallet.repository.LedgerAccountRepository;
import com.adarena.wallet.repository.LedgerTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * SOLO EN DESARROLLO (perfil dev + app.demo-data.enabled). Si la base de datos no tiene
 * ninguna subasta, crea un escenario de ejemplo para ver la web "viva":
 * <ul>
 *   <li>4 anunciantes con su perfil e imagen (contraseña común: {@value #DEMO_PASSWORD}).</li>
 *   <li>La ronda de AYER, cerrada: ganó Café Aurora (6.000 puntos), su anuncio está aprobado y se
 *       ve hoy en la portada. Bicis Norte perdió con 4.000: perdió 2.000 y arrastró 2.000.</li>
 *   <li>La ronda de HOY, abierta: Bicis Norte 4.500 (2.000 arrastrados + 2.500),
 *       Estudio Lumen 3.200 y Huerta Viva 1.800.</li>
 * </ul>
 * Todos los puntos pasan por el ledger (reparto inicial, reservas, gasto y pérdida), así que las cuentas
 * cuadran exactamente igual que con datos reales.
 * <p>
 * Sus webs (www.example.com/…) no existen de verdad, así que también se crea "lo que habríamos
 * leído de ellas" (logo, fotos, frases): así se ve la presentación animada del ganador en local.
 */
@Component
@Profile("dev")
@ConditionalOnBooleanProperty("app.demo-data.enabled")
@Order(10)
public class DemoDataSeeder implements ApplicationRunner {

    public static final String DEMO_PASSWORD = "DemoAdArena2026!";

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final long DEMO_POINTS = 10_000;

    private final AuctionRepository auctionRepository;
    private final AuctionParticipationRepository participationRepository;
    private final BidRepository bidRepository;
    private final AdSlotRepository adSlotRepository;
    private final AdProfileRepository adProfileRepository;
    private final UserRepository userRepository;
    private final LedgerAccountRepository accountRepository;
    private final LedgerTransactionRepository transactionRepository;
    private final AppSettingsRepository settingsRepository;
    private final ImageService imageService;
    private final DemoSites demoSites;
    private final SitePreviewService sitePreviewService;
    private final SitePreviewJson sitePreviewJson;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties properties;
    private final Clock clock;

    public DemoDataSeeder(AuctionRepository auctionRepository, AuctionParticipationRepository participationRepository,
                          BidRepository bidRepository, AdSlotRepository adSlotRepository,
                          AdProfileRepository adProfileRepository, UserRepository userRepository,
                          LedgerAccountRepository accountRepository, LedgerTransactionRepository transactionRepository,
                          AppSettingsRepository settingsRepository, ImageService imageService,
                          DemoSites demoSites, SitePreviewService sitePreviewService,
                          SitePreviewJson sitePreviewJson, PasswordEncoder passwordEncoder, AppProperties properties,
                          Clock clock) {
        this.auctionRepository = auctionRepository;
        this.participationRepository = participationRepository;
        this.bidRepository = bidRepository;
        this.adSlotRepository = adSlotRepository;
        this.adProfileRepository = adProfileRepository;
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.settingsRepository = settingsRepository;
        this.imageService = imageService;
        this.demoSites = demoSites;
        this.sitePreviewService = sitePreviewService;
        this.sitePreviewJson = sitePreviewJson;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (auctionRepository.count() > 0) {
            log.info("Demo data skipped: the database already has auctions");
            return;
        }
        Instant now = clock.instant();
        AppSettings settings = settingsRepository.getSettings();
        AuctionRules rules = settings.toAuctionRules();
        ZoneId zone = settings.zoneId();

        ZonedDateTime lastClose = now.atZone(zone).toLocalDate().atTime(settings.getCloseTime()).atZone(zone);
        if (lastClose.toInstant().isAfter(now)) {
            lastClose = lastClose.minusDays(1);
        }
        ZonedDateTime nextClose = lastClose.plusDays(1);
        Instant yesterdayEnd = lastClose.toInstant();

        String passwordHash = passwordEncoder.encode(DEMO_PASSWORD);
        Advertiser cafe = createAdvertiser("demo-cafe@adarena.local", "Café Aurora",
                "https://www.example.com/cafe-aurora",
                DemoContent.CAFE,
                new Color(0x7C2D12), new Color(0xF59E0B), DemoImages.Mark.RING, passwordHash, now);
        Advertiser bikes = createAdvertiser("demo-bicis@adarena.local", "Bicis Norte",
                "https://www.example.com/bicis-norte",
                DemoContent.BIKES,
                new Color(0x064E3B), new Color(0x34D399), DemoImages.Mark.TRIANGLE, passwordHash, now);
        Advertiser studio = createAdvertiser("demo-lumen@adarena.local", "Estudio Lumen",
                "https://www.example.com/estudio-lumen",
                DemoContent.STUDIO,
                new Color(0x312E81), new Color(0xA78BFA), DemoImages.Mark.SQUARE, passwordHash, now);
        Advertiser garden = createAdvertiser("demo-huerta@adarena.local", "Huerta Viva",
                "https://www.example.com/huerta-viva",
                DemoContent.GARDEN,
                new Color(0x365314), new Color(0xBEF264), DemoImages.Mark.WAVE, passwordHash, now);

        // Sus webs de ejemplo (logo, fotos y frases) para la presentación animada del ganador
        SitePreview cafeSite = demoSites.ensure(cafe.user().getEmail(), cafe.profile(), now).orElseThrow();
        for (Advertiser advertiser : new Advertiser[]{bikes, studio, garden}) {
            demoSites.ensure(advertiser.user().getEmail(), advertiser.profile(), now);
        }

        LedgerAccount issued = accountRepository.findSystemAccount(LedgerAccountType.POINTS_ISSUED).orElseThrow();
        LedgerAccount spent = accountRepository.findSystemAccount(LedgerAccountType.POINTS_SPENT).orElseThrow();
        for (Advertiser advertiser : new Advertiser[]{cafe, bikes, studio, garden}) {
            move(LedgerTransactionType.TEST_GRANT, "demo:points:" + advertiser.user().getId(), advertiser.user().getId(),
                    "Demo points", issued, advertiser.available(), DEMO_POINTS);
        }

        // ---------- Subasta de ayer: ya cerrada ----------
        Auction yesterday = auctionRepository.save(Auction.open(lastClose.toLocalDate(),
                lastClose.minusDays(1).toInstant(), yesterdayEnd, rules));
        AuctionParticipation cafeBid = bid(yesterday, null, cafe, 60_00, yesterdayEnd.minus(Duration.ofMinutes(95)));
        AuctionParticipation bikesBid = bid(yesterday, null, bikes, 40_00, yesterdayEnd.minus(Duration.ofMinutes(140)));

        cafeBid.markWon(1, cafe.snapshot());
        long carried = rules.carryOverOf(bikesBid.getTotalPoints());
        long forfeited = bikesBid.getTotalPoints() - carried;
        bikesBid.markLost(2, forfeited, carried, bikes.snapshot());
        move(LedgerTransactionType.BID_FORFEIT, "demo:forfeit:" + bikesBid.getId(), bikesBid.getId(),
                "Lost half (demo)", bikes.reserved(), spent, forfeited);
        yesterday.close(yesterdayEnd, AuctionResult.HAS_WINNER);
        // Hibernate ejecuta los INSERT antes que los UPDATE: sin este flush, la subasta de hoy se
        // insertaría "abierta" antes de guardar el cierre de la de ayer y el índice
        // ux_auctions_single_open (solo una abierta) lo rechazaría.
        auctionRepository.flush();

        AdSlot slot = new AdSlot(yesterday.getId(), cafeBid.getId(), cafe.user().getId(), 1, cafeBid.getTotalPoints(),
                yesterdayEnd, nextClose.toInstant());
        UUID adminId = properties.admin() == null || !properties.admin().isConfigured() ? null
                : userRepository.findByEmail(User.normalizeEmail(properties.admin().email()))
                        .map(User::getId).orElse(null);
        Instant reviewedAt = yesterdayEnd.plus(Duration.ofMinutes(10));
        slot.approve(adminId, reviewedAt.isAfter(now) ? now : reviewedAt,
                sitePreviewJson.writeShowcase(sitePreviewService.toShowcase(cafe.profile().getWebsiteUrl(), cafeSite)));
        adSlotRepository.save(slot);
        move(LedgerTransactionType.BID_WIN_CHARGE, "demo:win:" + slot.getId(), slot.getId(),
                "Winning ad approved (demo)", cafe.reserved(), spent, cafeBid.getTotalPoints());

        // ---------- Subasta de hoy: abierta ----------
        Auction today = auctionRepository.save(Auction.open(nextClose.toLocalDate(), yesterdayEnd,
                nextClose.toInstant(), rules));
        long carrySeq = bidRepository.nextSeq();
        AuctionParticipation bikesToday = participationRepository.save(AuctionParticipation.carriedOver(
                today, bikes.user().getId(), carried, bikesBid.getId(), carrySeq, yesterdayEnd));
        bidRepository.save(Bid.carryOver(carrySeq, bikesToday, carried));

        bid(today, null, garden, 18_00, pastButAfter(now, yesterdayEnd, 180));
        bid(today, null, studio, 32_00, pastButAfter(now, yesterdayEnd, 75));
        bid(today, bikesToday, bikes, 25_00, pastButAfter(now, yesterdayEnd, 20));

        log.info("Demo data created: 4 advertisers (password {}), yesterday's winner live, today's auction open",
                DEMO_PASSWORD);
    }

    private Advertiser createAdvertiser(String email, String company, String url, String description,
                                        Color from, Color to, DemoImages.Mark mark, String passwordHash, Instant now) {
        User user = userRepository.save(new User(email, passwordHash, company, Role.USER,
                properties.legal().termsVersion(), now));
        StoredImage image = imageService.store(user.getId(),
                DemoImages.render(from, to, mark, email.hashCode()));
        AdProfile profile = adProfileRepository.save(new AdProfile(user.getId(), company, url, description, image.getId()));
        LedgerAccount available = accountRepository.save(LedgerAccount.forUser(user.getId(), LedgerAccountType.USER_AVAILABLE));
        LedgerAccount reserved = accountRepository.save(LedgerAccount.forUser(user.getId(), LedgerAccountType.USER_RESERVED));
        return new Advertiser(user, profile, available, reserved);
    }

    /** Puja con reserva de puntos, igual que el servicio real de pujas. */
    private AuctionParticipation bid(Auction auction, AuctionParticipation existing, Advertiser advertiser,
                                     long amountPoints, Instant at) {
        long seq = bidRepository.nextSeq();
        AuctionParticipation participation = existing;
        if (participation == null) {
            participation = participationRepository.save(
                    AuctionParticipation.start(auction, advertiser.user().getId(), amountPoints, seq, at));
        } else {
            participation.addBid(amountPoints, seq, at);
        }
        Bid bid = bidRepository.save(Bid.bid(seq, participation, amountPoints, "demo-" + UUID.randomUUID()));
        move(LedgerTransactionType.BID_RESERVE, "bid:" + bid.getId(), bid.getId(), "Bid (demo)",
                advertiser.available(), advertiser.reserved(), amountPoints);
        return participation;
    }

    private void move(LedgerTransactionType type, String key, UUID referenceId, String description,
                      LedgerAccount from, LedgerAccount to, long amountPoints) {
        LedgerTransaction transaction = LedgerTransaction.of(type, key, type.name(), referenceId, description)
                .post(from, -amountPoints)
                .post(to, amountPoints);
        transaction.assertBalanced();
        transactionRepository.save(transaction);
    }

    /** Un instante {@code minutesAgo} minutos antes de ahora, pero nunca anterior a {@code notBefore}. */
    private static Instant pastButAfter(Instant now, Instant notBefore, long minutesAgo) {
        Instant candidate = now.minus(Duration.ofMinutes(minutesAgo));
        return candidate.isBefore(notBefore) ? notBefore : candidate;
    }

    private record Advertiser(User user, AdProfile profile, LedgerAccount available, LedgerAccount reserved) {
        AdSnapshot snapshot() {
            return new AdSnapshot(profile.getCompanyName(), profile.getWebsiteUrl(), profile.getDescription(),
                    profile.getImageId());
        }
    }
}

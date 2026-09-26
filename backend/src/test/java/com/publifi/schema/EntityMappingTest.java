package com.publifi.schema;

import com.publifi.TestcontainersConfiguration;
import com.publifi.admin.domain.AdminAuditLog;
import com.publifi.adprofile.domain.AdProfile;
import com.publifi.adslot.domain.AdSlot;
import com.publifi.adslot.domain.AdSlotStatus;
import com.publifi.auction.domain.AdSnapshot;
import com.publifi.auction.domain.Auction;
import com.publifi.auction.domain.AuctionParticipation;
import com.publifi.auction.domain.AuctionResult;
import com.publifi.auction.domain.Bid;
import com.publifi.auction.domain.ParticipationOutcome;
import com.publifi.image.domain.StoredImage;
import com.publifi.notification.domain.Notification;
import com.publifi.notification.domain.NotificationType;
import com.publifi.notification.domain.OutboxEmail;
import com.publifi.payment.domain.StripeEvent;
import com.publifi.payment.domain.TopUp;
import com.publifi.payment.domain.TopUpStatus;
import com.publifi.settings.domain.AppSettings;
import com.publifi.user.domain.RefreshToken;
import com.publifi.user.domain.Role;
import com.publifi.user.domain.User;
import com.publifi.wallet.domain.LedgerAccount;
import com.publifi.wallet.domain.LedgerAccountType;
import com.publifi.wallet.domain.LedgerTransaction;
import com.publifi.wallet.domain.LedgerTransactionType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guarda y vuelve a leer un ejemplo de cada entidad contra PostgreSQL real. Detecta errores
 * que la validación de Hibernate no ve: valores de enum que no pasan un CHECK, columnas JSON,
 * embebidos, claves foráneas... Al ser @Transactional, todo se deshace al terminar.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class EntityMappingTest {

    private static final Instant NOW = Instant.parse("2026-09-26T10:00:00Z");

    @Autowired
    EntityManager em;

    @Test
    void persistsAndReloadsTheWholeModel() {
        // Usuarios y autenticación
        User user = new User("  Ana@Example.COM ", "hash", "Ana", Role.USER, "2026-09", NOW);
        User admin = new User("admin@publifi.test", "hash", "Admin", Role.ADMIN, "2026-09", NOW);
        em.persist(user);
        em.persist(admin);
        em.persist(new RefreshToken(user, "a".repeat(64), UUID.randomUUID(), NOW.plus(Duration.ofDays(30)), "JUnit"));

        // Perfil de anuncio
        StoredImage image = new StoredImage(user.getId(), "image/png", new byte[]{1, 2, 3}, 10, 10, "b".repeat(64));
        em.persist(image);
        AdProfile profile = new AdProfile(user.getId(), "Ana SL", "https://ana.example", "La mejor tienda", image.getId());
        em.persist(profile);

        // Subasta con una puja de 10 €
        AppSettings settings = em.find(AppSettings.class, AppSettings.SINGLETON_ID);
        Instant scheduledEnd = Instant.parse("2026-09-26T22:00:00Z");
        Auction auction = Auction.open(LocalDate.of(2026, 9, 27), NOW, scheduledEnd, settings.toAuctionRules());
        em.persist(auction);

        long seq = nextBidSeq();
        AuctionParticipation participation = AuctionParticipation.start(auction, user.getId(), 1000, seq, NOW);
        em.persist(participation);
        em.persist(Bid.bid(seq, participation, 1000, "key-1"));

        // Monedero: recarga de 50 € y reserva de 10 € por la puja
        LedgerAccount available = LedgerAccount.forUser(user.getId(), LedgerAccountType.USER_AVAILABLE);
        LedgerAccount reserved = LedgerAccount.forUser(user.getId(), LedgerAccountType.USER_RESERVED);
        em.persist(available);
        em.persist(reserved);
        LedgerAccount stripe = systemAccount(LedgerAccountType.STRIPE_CLEARING);

        LedgerTransaction topUpTx = LedgerTransaction.of(LedgerTransactionType.TOP_UP, "topup:cs_test_1",
                        "TOP_UP", null, "Recarga")
                .post(stripe, -5000)
                .post(available, 5000);
        topUpTx.assertBalanced();
        em.persist(topUpTx);

        LedgerTransaction reserveTx = LedgerTransaction.of(LedgerTransactionType.BID_RESERVE, "bid:key-1",
                        "BID", participation.getId(), "Puja")
                .post(available, -1000)
                .post(reserved, 1000);
        reserveTx.assertBalanced();
        em.persist(reserveTx);

        TopUp topUp = new TopUp(user.getId(), 5000);
        topUp.attachCheckoutSession("cs_test_1");
        topUp.markSucceeded("pi_test_1", topUpTx.getId(), NOW);
        em.persist(topUp);
        em.persist(new StripeEvent("evt_test_1", "checkout.session.completed", "{\"id\":\"evt_test_1\"}", NOW));

        // Cierre: Ana gana y queda pendiente de moderación
        participation.markWon(1, new AdSnapshot(profile.getCompanyName(), profile.getWebsiteUrl(),
                profile.getDescription(), profile.getImageId()));
        auction.close(scheduledEnd, AuctionResult.HAS_WINNER);
        AdSlot slot = new AdSlot(auction.getId(), participation.getId(), user.getId(), 1,
                participation.getTotalCents(), scheduledEnd, scheduledEnd.plus(Duration.ofHours(24)));
        em.persist(slot);

        // Notificaciones, email y auditoría
        em.persist(new Notification(user.getId(), NotificationType.OUTBID, "Te han superado",
                "Otra persona te ha superado, vuelve a pujar", "/subasta"));
        em.persist(new OutboxEmail(user.getEmail(), "Te han superado", "<p>Hola</p>", "Hola",
                "outbid:test", NOW));
        em.persist(new AdminAuditLog(admin.getId(), "APPROVE_AD", "AD_SLOT", slot.getId().toString(),
                "{\"note\":\"ok\"}"));

        em.flush();
        // Fuerza ahora el trigger diferido que comprueba que cada transacción del ledger cuadra
        em.createNativeQuery("SET CONSTRAINTS ALL IMMEDIATE").executeUpdate();
        em.clear();

        // --- Relectura desde la BD ---
        assertThat(em.find(User.class, user.getId()).getEmail()).isEqualTo("ana@example.com");

        AuctionParticipation reloaded = em.find(AuctionParticipation.class, participation.getId());
        assertThat(reloaded.getOutcome()).isEqualTo(ParticipationOutcome.WON);
        assertThat(reloaded.getAdSnapshot().companyName()).isEqualTo("Ana SL");
        assertThat(reloaded.getAuction().getRules().carryOverPercent()).isEqualTo(50);

        assertThat(em.find(LedgerAccount.class, available.getId()).getBalanceCents()).isEqualTo(4000);
        assertThat(em.find(LedgerAccount.class, reserved.getId()).getBalanceCents()).isEqualTo(1000);
        assertThat(em.find(TopUp.class, topUp.getId()).getStatus()).isEqualTo(TopUpStatus.SUCCEEDED);
        assertThat(em.find(AdSlot.class, slot.getId()).getStatus()).isEqualTo(AdSlotStatus.PENDING_REVIEW);
        assertThat(em.find(StripeEvent.class, "evt_test_1").getPayload()).contains("evt_test_1");

        Number mismatches = (Number) em.createNativeQuery("SELECT count(*) FROM ledger_account_mismatches")
                .getSingleResult();
        assertThat(mismatches.longValue()).isZero();
    }

    private long nextBidSeq() {
        return ((Number) em.createNativeQuery("SELECT nextval('bid_seq')").getSingleResult()).longValue();
    }

    private LedgerAccount systemAccount(LedgerAccountType type) {
        return em.createQuery("SELECT a FROM LedgerAccount a WHERE a.type = :type AND a.userId IS NULL",
                        LedgerAccount.class)
                .setParameter("type", type)
                .getSingleResult();
    }
}

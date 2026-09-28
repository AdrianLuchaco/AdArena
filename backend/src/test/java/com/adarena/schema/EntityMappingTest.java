package com.adarena.schema;

import com.adarena.admin.domain.AdminAuditLog;
import com.adarena.adprofile.domain.AdProfile;
import com.adarena.adslot.domain.AdSlot;
import com.adarena.adslot.domain.AdSlotStatus;
import com.adarena.auction.domain.AdSnapshot;
import com.adarena.auction.domain.Auction;
import com.adarena.auction.domain.AuctionParticipation;
import com.adarena.auction.domain.AuctionResult;
import com.adarena.auction.domain.Bid;
import com.adarena.auction.domain.ParticipationOutcome;
import com.adarena.image.domain.StoredImage;
import com.adarena.notification.domain.Notification;
import com.adarena.notification.domain.NotificationType;
import com.adarena.notification.domain.OutboxEmail;
import com.adarena.earn.domain.ProjectView;
import com.adarena.earn.domain.SocialPlatform;
import com.adarena.earn.domain.SocialTask;
import com.adarena.earn.domain.SocialTaskCompletion;
import com.adarena.earn.domain.SocialTaskReport;
import com.adarena.settings.domain.AppSettings;
import com.adarena.support.IntegrationTest;
import com.adarena.user.domain.RefreshToken;
import com.adarena.user.domain.Role;
import com.adarena.user.domain.User;
import com.adarena.wallet.domain.LedgerAccount;
import com.adarena.wallet.domain.LedgerAccountType;
import com.adarena.wallet.domain.LedgerTransaction;
import com.adarena.wallet.domain.LedgerTransactionType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guarda y vuelve a leer un ejemplo de cada entidad contra PostgreSQL real. Detecta errores
 * que la validación de Hibernate no ve: valores de enum que no pasan un CHECK, columnas JSON,
 * embebidos, claves foráneas... Al ser @Transactional, todo se deshace al terminar.
 */
@IntegrationTest
@Transactional
class EntityMappingTest {

    private static final Instant NOW = Instant.parse("2026-09-26T10:00:00Z");

    @Autowired
    EntityManager em;

    @Test
    void persistsAndReloadsTheWholeModel() {
        // Usuarios y autenticación
        User user = new User("  Ana@Example.COM ", "hash", "Ana", Role.USER, "2026-09", NOW);
        User admin = new User("moderator@adarena.test", "hash", "Moderador", Role.ADMIN, "2026-09", NOW);
        em.persist(user);
        em.persist(admin);
        em.persist(new RefreshToken(user, "a".repeat(64), UUID.randomUUID(), NOW.plus(Duration.ofDays(30)), "JUnit"));

        // Perfil de anuncio
        StoredImage image = new StoredImage(user.getId(), "image/png", new byte[]{1, 2, 3}, 10, 10, "b".repeat(64));
        em.persist(image);
        AdProfile profile = new AdProfile(user.getId(), "Ana SL", "https://ana.example", "La mejor tienda", image.getId());
        em.persist(profile);

        // Subasta con una puja de 1.000 puntos
        AppSettings settings = em.find(AppSettings.class, AppSettings.SINGLETON_ID);
        Instant scheduledEnd = Instant.parse("2026-09-26T22:00:00Z");
        Auction auction = Auction.open(LocalDate.of(2026, 9, 27), NOW, scheduledEnd, settings.toAuctionRules());
        em.persist(auction);

        long seq = nextBidSeq();
        AuctionParticipation participation = AuctionParticipation.start(auction, user.getId(), 1000, seq, NOW);
        em.persist(participation);
        em.persist(Bid.bid(seq, participation, 1000, "key-1"));

        // Puntos: 5.000 de bienvenida y reserva de 1.000 por la puja
        LedgerAccount available = LedgerAccount.forUser(user.getId(), LedgerAccountType.USER_AVAILABLE);
        LedgerAccount reserved = LedgerAccount.forUser(user.getId(), LedgerAccountType.USER_RESERVED);
        em.persist(available);
        em.persist(reserved);
        LedgerAccount issued = systemAccount(LedgerAccountType.POINTS_ISSUED);

        LedgerTransaction bonusTx = LedgerTransaction.of(LedgerTransactionType.SIGNUP_BONUS, "signup:" + user.getId(),
                        "USER", user.getId(), "Puntos de bienvenida")
                .post(issued, -5000)
                .post(available, 5000);
        bonusTx.assertBalanced();
        em.persist(bonusTx);

        LedgerTransaction reserveTx = LedgerTransaction.of(LedgerTransactionType.BID_RESERVE, "bid:key-1",
                        "BID", participation.getId(), "Puja")
                .post(available, -1000)
                .post(reserved, 1000);
        reserveTx.assertBalanced();
        em.persist(reserveTx);

        // Ganar puntos: visitas a proyectos, tareas sociales y denuncias
        ProjectView view = new ProjectView(admin.getId(), user.getId(), LocalDate.of(2026, 9, 26), NOW);
        view.recordTicks(NOW.plusSeconds(10), 1, 10, false);
        em.persist(view);
        SocialTask task = new SocialTask(user.getId(), "Mi canal", "Recetas", "https://www.youtube.com/@ana", 20);
        em.persist(task);
        SocialTaskCompletion completion = new SocialTaskCompletion(task.getId(), admin.getId(), LocalDate.of(2026, 9, 26), NOW);
        completion.complete(NOW.plusSeconds(15), 20);
        em.persist(completion);
        em.persist(new SocialTaskReport(task.getId(), admin.getId(), "Enlace roto"));

        // Cierre: Ana gana y queda pendiente de moderación
        participation.markWon(1, new AdSnapshot(profile.getCompanyName(), profile.getWebsiteUrl(),
                profile.getDescription(), profile.getImageId()));
        auction.close(scheduledEnd, AuctionResult.HAS_WINNER);
        AdSlot slot = new AdSlot(auction.getId(), participation.getId(), user.getId(), 1,
                participation.getTotalPoints(), scheduledEnd, scheduledEnd.plus(Duration.ofHours(24)));
        em.persist(slot);

        // Notificaciones, email y auditoría
        em.persist(new Notification(user.getId(), NotificationType.OUTBID, "Te han superado",
                "Otra persona te ha superado, vuelve a pujar", "/arena"));
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

        assertThat(em.find(LedgerAccount.class, available.getId()).getBalancePoints()).isEqualTo(4000);
        assertThat(em.find(LedgerAccount.class, reserved.getId()).getBalancePoints()).isEqualTo(1000);
        assertThat(em.find(ProjectView.class, view.getId()).getPointsEarned()).isEqualTo(10);
        assertThat(em.find(SocialTask.class, task.getId()).getPlatform()).isEqualTo(SocialPlatform.YOUTUBE);
        assertThat(em.find(SocialTaskCompletion.class, completion.getId()).getPointsAwarded()).isEqualTo(20);
        assertThat(em.find(AdSlot.class, slot.getId()).getStatus()).isEqualTo(AdSlotStatus.PENDING_REVIEW);

        Number mismatches = (Number) em.createNativeQuery("SELECT count(*) FROM ledger_account_mismatches")
                .getSingleResult();
        assertThat(mismatches.longValue()).isZero();
    }

    @Test
    void closeTimeIsReadExactlyAsStored() {
        // Regresión: con hibernate.jdbc.time_zone=UTC, las 00:00 se leían como 01:00 o 02:00
        AppSettings settings = em.find(AppSettings.class, AppSettings.SINGLETON_ID);

        assertThat(settings.getCloseTime()).isEqualTo(LocalTime.MIDNIGHT);
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

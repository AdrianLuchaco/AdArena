package com.adarena.adslot.service;

import com.adarena.admin.service.AdminAuditService;
import com.adarena.adslot.domain.AdSlot;
import com.adarena.adslot.domain.AdSlotStatus;
import com.adarena.adslot.repository.AdSlotRepository;
import com.adarena.auction.domain.Auction;
import com.adarena.auction.domain.AuctionParticipation;
import com.adarena.auction.domain.Bid;
import com.adarena.auction.event.ArenaChangedEvent;
import com.adarena.auction.repository.AuctionParticipationRepository;
import com.adarena.auction.repository.AuctionRepository;
import com.adarena.auction.repository.BidRepository;
import com.adarena.common.config.AppProperties;
import com.adarena.common.error.ApiException;
import com.adarena.common.text.TextSanitizer;
import com.adarena.notification.service.NotificationService;
import com.adarena.notification.service.Notices;
import com.adarena.settings.repository.AppSettingsRepository;
import com.adarena.site.service.SitePreviewJson;
import com.adarena.site.service.SitePreviewService;
import com.adarena.user.repository.UserRepository;
import com.adarena.wallet.domain.LedgerAccount;
import com.adarena.wallet.domain.LedgerAccountType;
import com.adarena.wallet.domain.LedgerTransaction;
import com.adarena.wallet.domain.LedgerTransactionType;
import com.adarena.wallet.service.WalletService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Moderación del anuncio ganador (regla 9 y decisiones de la fase 1):
 * <ul>
 *   <li><b>Aprobar:</b> se gastan los puntos que quedaban retenidos, el ganador recibe su premio
 *       (500 puntos para volver a pujar) y el anuncio sale en portada durante lo que quede de su
 *       ventana (la ventana es fija: no se alarga si moderas tarde). Su presentación animada queda
 *       congelada tal y como la ve el administrador en ese momento.</li>
 *   <li><b>Rechazar:</b> se devuelve el 100 % y el siguiente clasificado pasa a ser candidato. Su
 *       arrastre se retira de la ronda de hoy (esos puntos vuelven a quedar retenidos, ahora como
 *       candidato) y se crea su hueco, también pendiente de moderación.</li>
 *   <li><b>Nadie modera a tiempo:</b> al terminar la ventana, el hueco caduca y se devuelve el 100 %.</li>
 * </ul>
 * <p>
 * Los puntos de un candidato: si ganó directamente (puesto 1), su total entero sigue retenido. Si
 * llegó por un rechazo (puesto 2, 3…), en el cierre ya perdió su parte no recuperable (ya se gastó) y el resto era su arrastre (retenido). Aprobar cobra lo retenido; devolver le
 * reintegra las dos partes, así que siempre recupera el 100 %.
 */
@Service
public class ModerationService {

    private static final Logger log = LoggerFactory.getLogger(ModerationService.class);
    private static final int MAX_REASON = 500;

    private final AdSlotRepository adSlotRepository;
    private final AuctionRepository auctionRepository;
    private final AuctionParticipationRepository participationRepository;
    private final BidRepository bidRepository;
    private final UserRepository userRepository;
    private final WalletService walletService;
    private final NotificationService notificationService;
    private final AdminAuditService auditService;
    private final AppSettingsRepository settingsRepository;
    private final SitePreviewService sitePreviewService;
    private final SitePreviewJson sitePreviewJson;
    private final long winnerBonus;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public ModerationService(AdSlotRepository adSlotRepository, AuctionRepository auctionRepository,
                             AuctionParticipationRepository participationRepository, BidRepository bidRepository,
                             UserRepository userRepository, WalletService walletService,
                             NotificationService notificationService, AdminAuditService auditService,
                             AppSettingsRepository settingsRepository, SitePreviewService sitePreviewService,
                             SitePreviewJson sitePreviewJson, AppProperties properties,
                             ApplicationEventPublisher events, PlatformTransactionManager transactionManager,
                             Clock clock) {
        this.adSlotRepository = adSlotRepository;
        this.auctionRepository = auctionRepository;
        this.participationRepository = participationRepository;
        this.bidRepository = bidRepository;
        this.userRepository = userRepository;
        this.walletService = walletService;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.settingsRepository = settingsRepository;
        this.sitePreviewService = sitePreviewService;
        this.sitePreviewJson = sitePreviewJson;
        this.winnerBonus = properties.rewards().winnerBonus();
        this.events = events;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /** Resultado de un rechazo: cuánto se devolvió y, si lo hay, el nuevo candidato. */
    public record RejectResult(UUID rejectedSlotId, long refundedPoints, UUID promotedSlotId) {
    }

    @Transactional
    public AdSlot approve(UUID slotId, UUID adminId) {
        Instant now = clock.instant();
        AdSlot slot = lockPending(slotId, now);
        AuctionParticipation candidate = participationOf(slot);
        long held = heldPoints(slot, candidate);
        // Orden de bloqueo: cuentas del usuario, después POINTS_ISSUED (premio) y por último POINTS_SPENT
        WalletService.UserAccounts accounts = walletService.lockUserAccounts(slot.getUserId());
        if (winnerBonus > 0) {
            walletService.grant(slot.getUserId(), winnerBonus, LedgerTransactionType.WINNER_BONUS,
                    "winner-bonus:" + slot.getId(), "AD_SLOT", slot.getId(), "Prize for winning the Arena");
        }
        if (held > 0) {
            LedgerAccount spent = walletService.lockSystemAccount(LedgerAccountType.POINTS_SPENT);
            walletService.record(LedgerTransaction.of(LedgerTransactionType.BID_WIN_CHARGE, "win:" + slot.getId(),
                            "AD_SLOT", slot.getId(), "Winning ad approved")
                    .post(accounts.reserved(), -held)
                    .post(spent, held));
        }
        // La presentación animada, congelada tal y como la ve ahora el administrador
        String showcase = candidate.getAdSnapshot() == null ? null
                : sitePreviewService.showcase(candidate.getAdSnapshot().websiteUrl())
                        .map(sitePreviewJson::writeShowcase).orElse(null);
        slot.approve(adminId, now, showcase);

        auditService.record(adminId, "AD_SLOT_APPROVED", "AD_SLOT", slot.getId(),
                details("user", slot.getUserId(), "chargedPoints", held, "amountPoints", slot.getAmountPoints(),
                        "winnerBonus", winnerBonus, "showcase", showcase != null));
        userRepository.findById(slot.getUserId())
                .ifPresent(user -> notificationService.notify(user,
                        Notices.approved(untilText(slot.getEndsAt()), winnerBonus)));
        events.publishEvent(ArenaChangedEvent.homeChanged());
        log.info("Ad slot {} approved by {}", slot.getId(), adminId);
        return slot;
    }

    @Transactional
    public RejectResult reject(UUID slotId, UUID adminId, String rawReason) {
        String reason = TextSanitizer.singleLine(rawReason);
        if (reason == null || reason.length() < 3) {
            throw ApiException.badRequest("REASON_REQUIRED", "Briefly explain why you are rejecting it (we will send it to them).");
        }
        if (reason.length() > MAX_REASON) {
            reason = reason.substring(0, MAX_REASON);
        }
        Instant now = clock.instant();
        AdSlot slot = lockPending(slotId, now);
        // Orden de bloqueo: la ronda abierta (donde está el arrastre del siguiente) ANTES que las cuentas
        Optional<Auction> openRound = auctionRepository.findOpenForUpdate();

        AuctionParticipation rejected = participationOf(slot);
        long refunded = refund(slot, rejected, "Ad rejected: 100% refund");
        slot.reject(adminId, reason, now);
        rejected.markRefunded();
        // El hueco rechazado deja de estar "vivo" ANTES de crear el siguiente (índice único por ronda)
        adSlotRepository.flush();

        Optional<AdSlot> promoted = openRound.flatMap(round -> promoteNext(slot, round, now));

        auditService.record(adminId, "AD_SLOT_REJECTED", "AD_SLOT", slot.getId(),
                details("user", slot.getUserId(), "reason", reason, "refundedPoints", refunded,
                        "promotedSlot", promoted.map(AdSlot::getId).orElse(null)));
        String finalReason = reason;
        userRepository.findById(slot.getUserId())
                .ifPresent(user -> notificationService.notify(user, Notices.rejected(finalReason, refunded)));
        events.publishEvent(ArenaChangedEvent.homeChanged());
        log.info("Ad slot {} rejected by {} (refunded {} cents, promoted {})", slot.getId(), adminId, refunded,
                promoted.map(AdSlot::getId).orElse(null));
        return new RejectResult(slot.getId(), refunded, promoted.map(AdSlot::getId).orElse(null));
    }

    /**
     * Caduca los huecos pendientes cuya ventana ya terminó sin moderación y devuelve el 100 %.
     * Cada uno en su propia transacción: un fallo en uno no bloquea a los demás.
     *
     * @return cuántos han caducado
     */
    public int expireOverdueSlots() {
        int expired = 0;
        for (UUID slotId : adSlotRepository.findOverduePendingIds(clock.instant())) {
            try {
                Boolean done = transaction.execute(status -> expireIfOverdue(slotId));
                if (Boolean.TRUE.equals(done)) {
                    expired++;
                }
            } catch (RuntimeException e) {
                log.error("Could not expire ad slot {}", slotId, e);
            }
        }
        return expired;
    }

    private boolean expireIfOverdue(UUID slotId) {
        Instant now = clock.instant();
        AdSlot slot = adSlotRepository.findForUpdate(slotId).orElse(null);
        if (slot == null || slot.getStatus() != AdSlotStatus.PENDING_REVIEW || now.isBefore(slot.getEndsAt())) {
            return false;
        }
        AuctionParticipation participation = participationOf(slot);
        long refunded = refund(slot, participation, "Ad not reviewed in time: 100% refund");
        slot.expire();
        participation.markRefunded();
        userRepository.findById(slot.getUserId())
                .ifPresent(user -> notificationService.notify(user, Notices.expired(refunded)));
        log.warn("Ad slot {} expired without moderation: refunded {} cents", slot.getId(), refunded);
        return true;
    }

    // ------------------------------------------------------------------ piezas internas

    private AdSlot lockPending(UUID slotId, Instant now) {
        AdSlot slot = adSlotRepository.findForUpdate(slotId)
                .orElseThrow(() -> ApiException.notFound("AD_SLOT_NOT_FOUND", "That ad doesn't exist."));
        if (slot.getStatus() != AdSlotStatus.PENDING_REVIEW) {
            throw ApiException.conflict("AD_SLOT_NOT_PENDING", "This ad has already been reviewed.");
        }
        if (!now.isBefore(slot.getEndsAt())) {
            throw ApiException.conflict("AD_SLOT_WINDOW_OVER",
                    "Its day on the homepage is over. Their points will be refunded automatically.");
        }
        return slot;
    }

    private AuctionParticipation participationOf(AdSlot slot) {
        return participationRepository.findById(slot.getParticipationId())
                .orElseThrow(() -> new IllegalStateException("Participation of slot " + slot.getId() + " is missing"));
    }

    /** Parte del importe del candidato que sigue retenida en su saldo reservado. */
    private static long heldPoints(AdSlot slot, AuctionParticipation participation) {
        if (slot.getCandidateRank() == 1) {
            return slot.getAmountPoints();
        }
        return participation.getCarriedOutPoints() == null ? 0 : participation.getCarriedOutPoints();
    }

    /** Parte de la puja del candidato que ya se gastó en el cierre (la que perdió al no ganar). */
    private static long chargedAtClosePoints(AdSlot slot, AuctionParticipation participation) {
        if (slot.getCandidateRank() == 1) {
            return 0;
        }
        return participation.getForfeitedPoints() == null ? 0 : participation.getForfeitedPoints();
    }

    /** Devuelve el 100 % a sus puntos libres: lo retenido + lo que ya se había gastado en el cierre. */
    private long refund(AdSlot slot, AuctionParticipation participation, String description) {
        long held = heldPoints(slot, participation);
        long charged = chargedAtClosePoints(slot, participation);
        long total = held + charged;
        if (total <= 0) {
            return 0;
        }
        WalletService.UserAccounts accounts = walletService.lockUserAccounts(slot.getUserId());
        LedgerTransaction tx = LedgerTransaction.of(LedgerTransactionType.WINNER_REFUND, "refund:" + slot.getId(),
                "AD_SLOT", slot.getId(), description);
        if (held > 0) {
            tx.post(accounts.reserved(), -held);
        }
        if (charged > 0) {
            tx.post(walletService.lockSystemAccount(LedgerAccountType.POINTS_SPENT), -charged);
        }
        tx.post(accounts.available(), total);
        walletService.record(tx);
        return total;
    }

    /**
     * El siguiente clasificado pasa a ser candidato. Si tenía arrastre en la ronda de hoy, se le
     * retira (esos puntos pasan a estar retenidos como candidato; no pueden contar dos veces).
     */
    private Optional<AdSlot> promoteNext(AdSlot rejectedSlot, Auction openRound, Instant now) {
        Optional<AuctionParticipation> next = participationRepository
                .findRunnersUp(rejectedSlot.getAuctionId(), rejectedSlot.getCandidateRank(), Limit.of(1))
                .stream().findFirst();
        if (next.isEmpty()) {
            return Optional.empty();
        }
        AuctionParticipation candidate = next.get();
        Optional<AuctionParticipation> carried = participationRepository.findByCarriedFromParticipationId(candidate.getId());
        if (carried.isPresent()) {
            AuctionParticipation today = carried.get();
            if (!today.getAuction().getId().equals(openRound.getId()) || !today.getAuction().isOpen()) {
                // Su arrastre ya se jugó en una ronda cerrada: esos puntos no se pueden retener otra vez
                log.warn("Cannot promote participation {}: its carry-over was already used", candidate.getId());
                return Optional.empty();
            }
            if (today.getCarriedInPoints() > 0) {
                long reversed = today.reverseCarryOver();
                long seq = bidRepository.nextSeq();
                bidRepository.save(Bid.carryReversal(seq, today, reversed));
            }
        }
        candidate.promoteToWinner();
        AdSlot slot = adSlotRepository.save(new AdSlot(rejectedSlot.getAuctionId(), candidate.getId(),
                candidate.getUserId(), candidate.getFinalRank(), candidate.getTotalPoints(),
                rejectedSlot.getStartsAt(), rejectedSlot.getEndsAt()));
        userRepository.findById(candidate.getUserId())
                .ifPresent(user -> notificationService.notify(user, Notices.promoted(candidate.getTotalPoints())));
        return Optional.of(slot);
    }

    /** "00:00 on Monday 28 September" en la zona horaria de la web. */
    private String untilText(Instant endsAt) {
        DateTimeFormatter format = DateTimeFormatter.ofPattern("HH:mm 'on' EEEE d MMMM",
                Locale.forLanguageTag("en-GB"));
        return endsAt.atZone(settingsRepository.getSettings().zoneId()).format(format);
    }

    private static Map<String, Object> details(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }
}

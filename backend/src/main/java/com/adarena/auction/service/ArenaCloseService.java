package com.adarena.auction.service;

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
import com.adarena.auction.event.ArenaChangedEvent;
import com.adarena.auction.repository.AuctionParticipationRepository;
import com.adarena.auction.repository.AuctionRepository;
import com.adarena.auction.repository.BidRepository;
import com.adarena.notification.service.NotificationService;
import com.adarena.notification.service.Notices;
import com.adarena.user.domain.User;
import com.adarena.user.repository.UserRepository;
import com.adarena.wallet.domain.LedgerAccount;
import com.adarena.wallet.domain.LedgerAccountType;
import com.adarena.wallet.domain.LedgerTransaction;
import com.adarena.wallet.domain.LedgerTransactionType;
import com.adarena.wallet.service.WalletService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * El cierre diario (reglas 6, 7 y 8). Cuando el contador llega a cero:
 * <ol>
 *   <li>Se bloquea la ronda. Si ya estaba cerrada o aún no ha terminado, no se hace nada: se puede
 *       ejecutar mil veces sin duplicar nada (idempotente).</li>
 *   <li>Nadie pujó → resultado NO_BIDS: la portada muestra "Hoy nadie ha pujado" (regla 8).</li>
 *   <li>El 1.º gana: se guarda una copia de su anuncio y se crea su hueco en la portada, pendiente
 *       de moderación. Sus puntos SIGUEN RESERVADOS hasta que lo apruebes (entonces se gastan) o
 *       lo rechaces o caduque (entonces se le devuelve el 100 %).</li>
 *   <li>Los demás (regla 7): conservan el 50 % (redondeado hacia abajo al punto) como puja
 *       inicial de la ronda siguiente, sin hacer nada; el otro 50 % se gasta.</li>
 *   <li>Se abre la ronda siguiente con las reglas vigentes.</li>
 * </ol>
 * Todo en UNA transacción: o se aplica entero o no se aplica nada.
 */
@Service
public class ArenaCloseService {

    private static final Logger log = LoggerFactory.getLogger(ArenaCloseService.class);

    private final AuctionRepository auctionRepository;
    private final AuctionParticipationRepository participationRepository;
    private final BidRepository bidRepository;
    private final AdProfileRepository adProfileRepository;
    private final AdSlotRepository adSlotRepository;
    private final UserRepository userRepository;
    private final WalletService walletService;
    private final NotificationService notificationService;
    private final ArenaLifecycleService lifecycleService;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ArenaCloseService(AuctionRepository auctionRepository, AuctionParticipationRepository participationRepository,
                             BidRepository bidRepository, AdProfileRepository adProfileRepository,
                             AdSlotRepository adSlotRepository, UserRepository userRepository,
                             WalletService walletService, NotificationService notificationService,
                             ArenaLifecycleService lifecycleService, ApplicationEventPublisher events, Clock clock) {
        this.auctionRepository = auctionRepository;
        this.participationRepository = participationRepository;
        this.bidRepository = bidRepository;
        this.adProfileRepository = adProfileRepository;
        this.adSlotRepository = adSlotRepository;
        this.userRepository = userRepository;
        this.walletService = walletService;
        this.notificationService = notificationService;
        this.lifecycleService = lifecycleService;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Resumen de un cierre.
     *
     * @param forfeitedPoints puntos gastados en el cierre (la parte que pierden los que no ganan)
     * @param carriedPoints   lo que se arrastra a la ronda siguiente
     */
    public record CloseResult(UUID closedRoundId, AuctionResult result, UUID winnerUserId, long winnerTotalPoints,
                              int participants, long forfeitedPoints, long carriedPoints, UUID nextRoundId) {
    }

    private record Carry(AuctionParticipation from, long amountPoints) {
    }

    /** Cierra la ronda abierta SI ya ha llegado su hora. */
    @Transactional
    public Optional<CloseResult> closeDueRound() {
        Instant now = clock.instant();
        // 1) Bloqueo de la ronda: a partir de aquí ninguna puja puede colarse
        Optional<Auction> open = auctionRepository.findOpenForUpdate();
        if (open.isEmpty() || !open.get().isDueForClosing(now)) {
            return Optional.empty();
        }
        Auction round = open.get();
        AuctionRules rules = round.getRules();

        List<AuctionParticipation> all = participationRepository.findAllRanked(round.getId());
        List<AuctionParticipation> ranked = all.stream().filter(p -> p.getTotalPoints() > 0).toList();
        // Participaciones que se quedaron en 0 puntos (se les retiró el arrastre): fuera de la clasificación
        all.stream().filter(p -> p.getTotalPoints() == 0).forEach(AuctionParticipation::markWithdrawn);

        List<UUID> userIds = ranked.stream().map(AuctionParticipation::getUserId).toList();
        Map<UUID, AdProfile> profiles = adProfileRepository.findByUserIdIn(userIds).stream()
                .collect(Collectors.toMap(AdProfile::getUserId, Function.identity()));

        AuctionParticipation winner = null;
        List<AuctionParticipation> losers = List.of();
        List<Carry> carries = new ArrayList<>();
        long forfeitedTotal = 0;
        if (ranked.isEmpty()) {
            round.close(now, AuctionResult.NO_BIDS);
        } else {
            winner = ranked.getFirst();
            winner.markWon(1, snapshotOf(profiles, winner));

            losers = ranked.subList(1, ranked.size());
            if (!losers.isEmpty()) {
                // 2) Cuentas de TODOS los perdedores a la vez (orden por id) y después la de ingresos
                Map<UUID, WalletService.UserAccounts> accounts = walletService.lockUserAccounts(
                        losers.stream().map(AuctionParticipation::getUserId).toList());
                LedgerAccount revenue = walletService.lockSystemAccount(LedgerAccountType.POINTS_SPENT);
                for (int i = 0; i < losers.size(); i++) {
                    AuctionParticipation loser = losers.get(i);
                    long carried = rules.carryOverOf(loser.getTotalPoints());
                    long forfeited = loser.getTotalPoints() - carried;
                    if (forfeited > 0) {
                        walletService.record(LedgerTransaction.of(LedgerTransactionType.BID_FORFEIT,
                                        "forfeit:" + loser.getId(), "PARTICIPATION", loser.getId(),
                                        "Lost half of a bid that did not win (round " + round.getAuctionDate() + ")")
                                .post(accounts.get(loser.getUserId()).reserved(), -forfeited)
                                .post(revenue, forfeited));
                        forfeitedTotal += forfeited;
                    }
                    loser.markLost(i + 2, forfeited, carried, snapshotOf(profiles, loser));
                    if (carried > 0) {
                        carries.add(new Carry(loser, carried));
                    }
                }
            }
            round.close(now, AuctionResult.HAS_WINNER);
        }
        // Hibernate ejecuta los INSERT antes que los UPDATE: sin este flush, la ronda nueva se
        // insertaría "abierta" antes de guardar el cierre y el índice "solo una abierta" lo rechazaría.
        auctionRepository.flush();

        // 3) Ronda siguiente
        Auction next = auctionRepository.save(lifecycleService.planRound(now));

        // 4) Hueco del ganador en la portada: ventana FIJA [fin programado de hoy, fin programado de mañana)
        if (winner != null) {
            adSlotRepository.save(new AdSlot(round.getId(), winner.getId(), winner.getUserId(), 1,
                    winner.getTotalPoints(), round.getScheduledEndAt(), next.getScheduledEndAt()));
        }

        // 5) Arrastres (regla 7): los puntos ya están reservados; solo se apunta como puja inicial de mañana.
        //    Se crean en orden de clasificación, así que a igualdad de arrastre va delante quien quedó mejor.
        long carriedTotal = 0;
        for (Carry carry : carries) {
            long seq = bidRepository.nextSeq();
            AuctionParticipation carried = participationRepository.save(AuctionParticipation.carriedOver(
                    next, carry.from().getUserId(), carry.amountPoints(), carry.from().getId(), seq, now));
            bidRepository.save(Bid.carryOver(seq, carried, carry.amountPoints()));
            carriedTotal += carry.amountPoints();
        }

        notifyParticipants(winner, losers);
        events.publishEvent(ArenaChangedEvent.roundOpened(next.getId()));

        CloseResult result = new CloseResult(round.getId(), round.getResult(),
                winner == null ? null : winner.getUserId(), winner == null ? 0 : winner.getTotalPoints(),
                ranked.size(), forfeitedTotal, carriedTotal, next.getId());
        log.info("Round {} closed: {} with {} participants (winner total {} cents, forfeited {}, carried {}). "
                        + "Round {} opened, closes at {}", round.getAuctionDate(), result.result(), result.participants(),
                result.winnerTotalPoints(), forfeitedTotal, carriedTotal, next.getAuctionDate(), next.getEndsAt());
        return Optional.of(result);
    }

    private void notifyParticipants(AuctionParticipation winner, List<AuctionParticipation> losers) {
        if (winner == null) {
            return;
        }
        List<UUID> ids = new ArrayList<>();
        ids.add(winner.getUserId());
        losers.forEach(loser -> ids.add(loser.getUserId()));
        Map<UUID, User> users = userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        User winnerUser = users.get(winner.getUserId());
        if (winnerUser != null) {
            notificationService.notify(winnerUser, Notices.won(winner.getTotalPoints()));
        }
        for (AuctionParticipation loser : losers) {
            User user = users.get(loser.getUserId());
            if (user != null) {
                notificationService.notify(user, Notices.lost(loser.getCarriedOutPoints()));
            }
        }
    }

    /** Foto del anuncio al cierre: es lo que se publicará si gana, aunque luego edite su perfil. */
    private static AdSnapshot snapshotOf(Map<UUID, AdProfile> profiles, AuctionParticipation participation) {
        AdProfile profile = profiles.get(participation.getUserId());
        if (profile == null) {
            // No debería pasar (pujar exige anuncio y no se puede borrar), pero nunca bloqueamos el cierre
            log.error("Participant {} has no ad profile at closing time", participation.getUserId());
            return null;
        }
        return new AdSnapshot(profile.getCompanyName(), profile.getWebsiteUrl(), profile.getDescription(),
                profile.getImageId());
    }
}

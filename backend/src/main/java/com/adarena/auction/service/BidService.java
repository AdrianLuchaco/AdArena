package com.adarena.auction.service;

import com.adarena.adprofile.repository.AdProfileRepository;
import com.adarena.auction.domain.Auction;
import com.adarena.auction.domain.AuctionParticipation;
import com.adarena.auction.domain.AuctionRules;
import com.adarena.auction.domain.Bid;
import com.adarena.auction.dto.BidResponse;
import com.adarena.auction.event.ArenaChangedEvent;
import com.adarena.auction.repository.AuctionParticipationRepository;
import com.adarena.auction.repository.AuctionRepository;
import com.adarena.auction.repository.BidRepository;
import com.adarena.common.error.ApiException;
import com.adarena.common.text.Points;
import com.adarena.notification.service.NotificationService;
import com.adarena.notification.service.Notices;
import com.adarena.user.domain.User;
import com.adarena.user.repository.UserRepository;
import com.adarena.wallet.domain.InsufficientFundsException;
import com.adarena.wallet.service.WalletService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Pujar (reglas 2, 4 y 5). Todo ocurre en UNA transacción, con este orden de bloqueo:
 * <ol>
 *   <li>La ronda abierta (FOR UPDATE): serializa todas las pujas del día.</li>
 *   <li>La participación del usuario.</li>
 *   <li>Sus cuentas del monedero.</li>
 * </ol>
 * Si algo falla (puntos insuficientes, ronda cerrada…), no se guarda NADA.
 */
@Service
public class BidService {

    /** Tope de seguridad por puja: 1.000.000 de puntos. */
    public static final long MAX_BID_POINTS = 1_000_000;
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("^[A-Za-z0-9_-]{8,100}$");

    private final AuctionRepository auctionRepository;
    private final AuctionParticipationRepository participationRepository;
    private final BidRepository bidRepository;
    private final AdProfileRepository adProfileRepository;
    private final WalletService walletService;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public BidService(AuctionRepository auctionRepository, AuctionParticipationRepository participationRepository,
                      BidRepository bidRepository, AdProfileRepository adProfileRepository,
                      WalletService walletService, NotificationService notificationService,
                      UserRepository userRepository, ApplicationEventPublisher events, Clock clock) {
        this.auctionRepository = auctionRepository;
        this.participationRepository = participationRepository;
        this.bidRepository = bidRepository;
        this.adProfileRepository = adProfileRepository;
        this.walletService = walletService;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public BidResponse placeBid(UUID userId, long amountPoints, String idempotencyKey) {
        if (idempotencyKey == null || !IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
            throw ApiException.badRequest("IDEMPOTENCY_KEY_REQUIRED",
                    "Missing Idempotency-Key header (8-100 characters: letters, numbers, - or _).");
        }
        if (amountPoints <= 0) {
            throw ApiException.unprocessable("BID_TOO_LOW", "The amount must be greater than 0.");
        }
        if (amountPoints > MAX_BID_POINTS) {
            throw ApiException.unprocessable("BID_TOO_HIGH", "You can add at most 1,000,000 points in a single bid.");
        }
        if (!adProfileRepository.existsByUserId(userId)) {
            throw ApiException.unprocessable("AD_PROFILE_REQUIRED",
                    "Before bidding, create your ad in your account: it is what everyone will see if you win.");
        }

        // 1) Bloqueo de la ronda: a partir de aquí, las pujas de hoy van de una en una
        Auction auction = auctionRepository.findOpenForUpdate()
                .orElseThrow(() -> ApiException.conflict("NO_OPEN_ROUND",
                        "Today's round hasn't started yet. Come back in a few minutes."));

        // Idempotencia: comprobada DESPUÉS del bloqueo, para que dos reintentos simultáneos no pasen ambos
        Optional<Bid> previous = bidRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (previous.isPresent()) {
            return currentStatus(auction, userId, false, true);
        }

        Instant now = clock.instant();
        if (!auction.acceptsBidsAt(now)) {
            throw ApiException.conflict("ROUND_CLOSED", "Today's round is over. Come back tomorrow!");
        }

        // 2) Participación del usuario
        AuctionParticipation participation = participationRepository.findForUpdate(auction.getId(), userId).orElse(null);
        AuctionRules rules = auction.getRules();
        boolean firstContribution = participation == null || participation.getTotalPoints() == 0;
        long minimum = firstContribution ? rules.minBidPoints() : rules.minIncrementPoints();
        if (amountPoints < minimum) {
            throw ApiException.unprocessable("BID_TOO_LOW", (firstContribution
                    ? "The minimum bid to enter is " : "Each extra bid must be at least ")
                    + Points.format(minimum) + ".");
        }

        // 3) Monedero
        WalletService.UserAccounts accounts = walletService.lockUserAccounts(userId);
        if (accounts.available().getBalancePoints() < amountPoints) {
            throw new InsufficientFundsException(accounts.available().getId(),
                    accounts.available().getBalancePoints(), amountPoints);
        }

        long previousTotal = participation == null ? 0 : participation.getTotalPoints();
        long previousSeq = participation == null ? Long.MAX_VALUE : participation.getLastBidSeq();
        long seq = bidRepository.nextSeq();
        if (participation == null) {
            participation = participationRepository.save(
                    AuctionParticipation.start(auction, userId, amountPoints, seq, now));
        } else {
            participation.addBid(amountPoints, seq, now);
        }
        Bid bid = bidRepository.save(Bid.bid(seq, participation, amountPoints, idempotencyKey));
        walletService.reserveForBid(accounts, bid.getId(), amountPoints);

        boolean extended = auction.applyAntiSniping(now);

        // ¿A quién ha superado? A los que iban por delante antes y ahora van por detrás
        final long oldTotal = previousTotal;
        final long oldSeq = previousSeq;
        List<AuctionParticipation> outbid = participationRepository
                .findInTotalRange(auction.getId(), userId, previousTotal, participation.getTotalPoints())
                .stream()
                .filter(p -> p.getTotalPoints() > oldTotal || p.getLastBidSeq() < oldSeq)
                .toList();
        notifyOutbid(outbid);

        events.publishEvent(new ArenaChangedEvent(auction.getId(), userId,
                outbid.stream().map(AuctionParticipation::getUserId).toList(), extended));
        return currentStatus(auction, userId, extended, false);
    }

    /**
     * Regla 4: email + aviso "te han superado". Como mucho UNO por cada puja del superado (la clave
     * incluye su última puja): en una guerra de pujas no recibe diez emails, pero si vuelve a pujar
     * y le vuelven a superar, sí le avisamos otra vez. El aviso al instante por WebSocket sale siempre.
     */
    private void notifyOutbid(List<AuctionParticipation> outbid) {
        if (outbid.isEmpty()) {
            return;
        }
        Map<UUID, User> users = userRepository.findAllById(outbid.stream().map(AuctionParticipation::getUserId).toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        for (AuctionParticipation participation : outbid) {
            User user = users.get(participation.getUserId());
            if (user != null) {
                notificationService.notifyOnce(user, Notices.outbid(),
                        "outbid:" + participation.getId() + ":" + participation.getLastBidSeq());
            }
        }
    }

    private BidResponse currentStatus(Auction auction, UUID userId, boolean extended, boolean replayed) {
        AuctionParticipation participation = participationRepository.findByAuctionIdAndUserId(auction.getId(), userId)
                .orElseThrow();
        int position = participationRepository.countAhead(auction.getId(), participation.getTotalPoints(),
                participation.getLastBidSeq()) + 1;
        WalletService.Balance balance = walletService.balance(userId);
        return new BidResponse(participation.getTotalPoints(), position, auction.getEndsAt(), extended,
                balance.availablePoints(), balance.reservedPoints(), replayed);
    }
}

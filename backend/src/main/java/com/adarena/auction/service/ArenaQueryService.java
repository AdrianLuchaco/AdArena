package com.adarena.auction.service;

import com.adarena.adprofile.repository.AdProfileRepository;
import com.adarena.auction.domain.Auction;
import com.adarena.auction.domain.AuctionParticipation;
import com.adarena.auction.domain.AuctionStatus;
import com.adarena.auction.dto.MyArenaStatus;
import com.adarena.auction.repository.AuctionParticipationRepository;
import com.adarena.auction.repository.AuctionRepository;
import com.adarena.auction.repository.BidRepository;
import com.adarena.auction.repository.MyBidRow;
import com.adarena.wallet.service.WalletService;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/** Consultas de solo lectura sobre la participación del usuario. */
@Service
public class ArenaQueryService {

    private static final int MAX_HISTORY = 100;

    private final AuctionRepository auctionRepository;
    private final AuctionParticipationRepository participationRepository;
    private final BidRepository bidRepository;
    private final AdProfileRepository adProfileRepository;
    private final WalletService walletService;
    private final Clock clock;

    public ArenaQueryService(AuctionRepository auctionRepository, AuctionParticipationRepository participationRepository,
                             BidRepository bidRepository, AdProfileRepository adProfileRepository,
                             WalletService walletService, Clock clock) {
        this.auctionRepository = auctionRepository;
        this.participationRepository = participationRepository;
        this.bidRepository = bidRepository;
        this.adProfileRepository = adProfileRepository;
        this.walletService = walletService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public MyArenaStatus myStatus(UUID userId) {
        WalletService.Balance balance = walletService.balance(userId);
        boolean hasProfile = adProfileRepository.existsByUserId(userId);
        Auction auction = auctionRepository.findFirstByStatus(AuctionStatus.OPEN).orElse(null);
        if (auction == null) {
            return new MyArenaStatus(false, null, hasProfile, 0, 0, null, 0,
                    balance.availablePoints(), balance.reservedPoints());
        }
        AuctionParticipation participation = participationRepository
                .findByAuctionIdAndUserId(auction.getId(), userId)
                .filter(p -> p.getTotalPoints() > 0)
                .orElse(null);
        Integer position = participation == null ? null
                : participationRepository.countAhead(auction.getId(), participation.getTotalPoints(),
                participation.getLastBidSeq()) + 1;
        long minNext = participation == null ? auction.getRules().minBidPoints() : auction.getRules().minIncrementPoints();
        return new MyArenaStatus(auction.acceptsBidsAt(clock.instant()), auction.getEndsAt(), hasProfile,
                participation == null ? 0 : participation.getTotalPoints(),
                participation == null ? 0 : participation.getCarriedInPoints(),
                position, minNext, balance.availablePoints(), balance.reservedPoints());
    }

    @Transactional(readOnly = true)
    public List<MyBidRow> myBids(UUID userId, int limit) {
        return bidRepository.findHistory(userId, Limit.of(Math.clamp(limit, 1, MAX_HISTORY)));
    }
}

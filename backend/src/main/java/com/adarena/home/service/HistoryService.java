package com.adarena.home.service;

import com.adarena.adslot.domain.AdSlot;
import com.adarena.adslot.domain.AdSlotStatus;
import com.adarena.adslot.repository.AdSlotRepository;
import com.adarena.auction.domain.AdSnapshot;
import com.adarena.auction.domain.Auction;
import com.adarena.auction.domain.AuctionParticipation;
import com.adarena.auction.domain.AuctionResult;
import com.adarena.auction.domain.AuctionStatus;
import com.adarena.auction.repository.AuctionParticipationRepository;
import com.adarena.auction.repository.AuctionRepository;
import com.adarena.home.dto.HistoryPage;
import com.adarena.home.dto.HistoryPage.Outcome;
import com.adarena.home.dto.HistoryPage.PastProject;
import com.adarena.home.dto.HistoryPage.PastRound;
import com.adarena.image.service.ImageService;
import com.adarena.settings.repository.AppSettingsRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Historial público: cada ronda cerrada con su ganador y todos los proyectos que participaron,
 * tal y como eran sus anuncios en el momento del cierre.
 */
@Service
public class HistoryService {

    public static final int MAX_PAGE_SIZE = 20;

    private final AuctionRepository auctionRepository;
    private final AuctionParticipationRepository participationRepository;
    private final AdSlotRepository adSlotRepository;
    private final AppSettingsRepository settingsRepository;

    public HistoryService(AuctionRepository auctionRepository, AuctionParticipationRepository participationRepository,
                          AdSlotRepository adSlotRepository, AppSettingsRepository settingsRepository) {
        this.auctionRepository = auctionRepository;
        this.participationRepository = participationRepository;
        this.adSlotRepository = adSlotRepository;
        this.settingsRepository = settingsRepository;
    }

    @Transactional(readOnly = true)
    public HistoryPage getHistory(int page, int size) {
        Page<Auction> rounds = auctionRepository.findByStatusOrderByScheduledEndAtDesc(AuctionStatus.CLOSED,
                PageRequest.of(Math.max(0, page), Math.clamp(size, 1, MAX_PAGE_SIZE)));
        List<UUID> ids = rounds.map(Auction::getId).getContent();
        if (ids.isEmpty()) {
            return new HistoryPage(List.of(), rounds.getNumber(), rounds.getTotalPages(), rounds.getTotalElements());
        }

        Map<UUID, List<AuctionParticipation>> participantsByRound = participationRepository.findRankedIn(ids).stream()
                .collect(Collectors.groupingBy(p -> p.getAuction().getId()));
        Map<UUID, List<AdSlot>> slotsByRound = adSlotRepository.findByAuctionIdIn(ids).stream()
                .collect(Collectors.groupingBy(AdSlot::getAuctionId));

        ZoneId zone = settingsRepository.getSettings().zoneId();
        List<PastRound> items = rounds.getContent().stream()
                .map(round -> toPastRound(round, zone,
                        participantsByRound.getOrDefault(round.getId(), List.of()),
                        slotsByRound.getOrDefault(round.getId(), List.of())))
                .toList();
        return new HistoryPage(items, rounds.getNumber(), rounds.getTotalPages(), rounds.getTotalElements());
    }

    private static PastRound toPastRound(Auction round, ZoneId zone, List<AuctionParticipation> participants,
                                         List<AdSlot> slots) {
        List<PastProject> projects = participants.stream()
                .filter(p -> p.getAdSnapshot() != null)
                .map(HistoryService::toProject)
                .toList();

        Optional<AdSlot> approved = slots.stream().filter(s -> s.getStatus() == AdSlotStatus.APPROVED).findFirst();
        boolean pending = slots.stream().anyMatch(s -> s.getStatus() == AdSlotStatus.PENDING_REVIEW);

        Outcome outcome;
        PastProject winner = null;
        if (approved.isPresent()) {
            outcome = Outcome.WINNER;
            UUID winnerParticipation = approved.get().getParticipationId();
            winner = participants.stream()
                    .filter(p -> p.getId().equals(winnerParticipation) && p.getAdSnapshot() != null)
                    .map(HistoryService::toProject)
                    .findFirst().orElse(null);
        } else if (pending) {
            outcome = Outcome.PENDING_REVIEW;
        } else if (round.getResult() == AuctionResult.NO_BIDS) {
            outcome = Outcome.NO_BIDS;
        } else {
            outcome = Outcome.NO_WINNER;
        }

        // Se compite el día en que se abrió la ronda; el ganador sale en portada el día en que cierra
        return new PastRound(round.getOpensAt().atZone(zone).toLocalDate(),
                round.getScheduledEndAt().atZone(zone).toLocalDate(),
                round.getClosedAt(), outcome, winner, projects);
    }

    private static PastProject toProject(AuctionParticipation participation) {
        AdSnapshot ad = participation.getAdSnapshot();
        return new PastProject(participation.getId(), participation.getFinalRank(), ad.companyName(), ad.description(),
                ad.websiteUrl(),
                ImageService.publicUrl(ad.imageId()), participation.getTotalPoints());
    }
}

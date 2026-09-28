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
import com.adarena.auction.repository.RankingRow;
import com.adarena.home.dto.HomeResponse;
import com.adarena.home.dto.HomeResponse.CurrentAd;
import com.adarena.home.dto.HomeResponse.HomeState;
import com.adarena.home.dto.HomeResponse.ProjectEntry;
import com.adarena.home.dto.HomeResponse.RoundSummary;
import com.adarena.image.service.ImageService;
import com.adarena.settings.repository.AppSettingsRepository;
import com.adarena.site.service.SitePreviewJson;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Calcula qué se ve en la portada en este momento: anuncio ganador, avisos y proyectos de hoy. */
@Service
public class HomeService {

    public static final int RANKING_SIZE = 50;
    /** Una ventana de emisión dura 24 h (25 h el día del cambio de hora). */
    private static final Duration MAX_WINDOW = Duration.ofHours(25);

    private final AdSlotRepository adSlotRepository;
    private final AuctionRepository auctionRepository;
    private final AuctionParticipationRepository participationRepository;
    private final AppSettingsRepository settingsRepository;
    private final SitePreviewJson sitePreviewJson;
    private final Clock clock;

    public HomeService(AdSlotRepository adSlotRepository, AuctionRepository auctionRepository,
                       AuctionParticipationRepository participationRepository, AppSettingsRepository settingsRepository,
                       SitePreviewJson sitePreviewJson, Clock clock) {
        this.adSlotRepository = adSlotRepository;
        this.auctionRepository = auctionRepository;
        this.participationRepository = participationRepository;
        this.settingsRepository = settingsRepository;
        this.sitePreviewJson = sitePreviewJson;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public HomeResponse getHome() {
        Instant now = clock.instant();
        Optional<CurrentAd> currentAd = findCurrentAd(now);
        HomeState state = currentAd.isPresent() ? HomeState.AD : stateWithoutAd(now);
        RoundSummary round = auctionRepository.findFirstByStatus(AuctionStatus.OPEN)
                .map(this::summarize)
                .orElse(null);
        return new HomeResponse(now, state, currentAd.orElse(null), round);
    }

    private Optional<CurrentAd> findCurrentAd(Instant now) {
        return adSlotRepository.findLive(now, Limit.of(1)).stream().findFirst()
                .flatMap(slot -> participationRepository.findById(slot.getParticipationId())
                        .filter(participation -> participation.getAdSnapshot() != null)
                        .map(participation -> toCurrentAd(slot, participation)));
    }

    private CurrentAd toCurrentAd(AdSlot slot, AuctionParticipation participation) {
        AdSnapshot ad = participation.getAdSnapshot();
        ZoneId zone = settingsRepository.getSettings().zoneId();
        return new CurrentAd(ad.companyName(), ad.description(), ad.websiteUrl(),
                ImageService.publicUrl(ad.imageId()), slot.getStartsAt(), slot.getEndsAt(), slot.getAmountPoints(),
                participation.getAuction().getOpensAt().atZone(zone).toLocalDate(),
                sitePreviewJson.readShowcase(slot.getShowcase()));
    }

    /** Sin anuncio aprobado: ¿nadie pujó, está en revisión o simplemente no hay? */
    private HomeState stateWithoutAd(Instant now) {
        return auctionRepository
                .findFirstByStatusAndScheduledEndAtLessThanEqualOrderByScheduledEndAtDesc(AuctionStatus.CLOSED, now)
                .filter(auction -> auction.getScheduledEndAt().isAfter(now.minus(MAX_WINDOW)))
                .map(auction -> {
                    if (auction.getResult() == AuctionResult.NO_BIDS) {
                        return HomeState.NO_BIDS;
                    }
                    if (adSlotRepository.existsByAuctionIdAndStatus(auction.getId(), AdSlotStatus.PENDING_REVIEW)) {
                        return HomeState.PENDING_REVIEW;
                    }
                    return HomeState.NO_AD;
                })
                .orElse(HomeState.NO_AD);
    }

    private RoundSummary summarize(Auction auction) {
        List<RankingRow> rows = participationRepository.findRanking(auction.getId(), Limit.of(RANKING_SIZE));
        List<ProjectEntry> ranking = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            RankingRow row = rows.get(i);
            ranking.add(new ProjectEntry(row.participationId(), i + 1, row.companyName(), row.description(),
                    row.websiteUrl(), ImageService.publicUrl(row.imageId()), row.totalPoints(), row.carriedInPoints()));
        }
        return new RoundSummary(auction.getId(), auction.getAuctionDate(), auction.getEndsAt(),
                participationRepository.countActiveBidders(auction.getId()), ranking);
    }
}

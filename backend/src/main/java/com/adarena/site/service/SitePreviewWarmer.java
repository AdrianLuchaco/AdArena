package com.adarena.site.service;

import com.adarena.adslot.domain.AdSlot;
import com.adarena.adslot.domain.AdSlotStatus;
import com.adarena.adslot.repository.AdSlotRepository;
import com.adarena.auction.domain.AuctionParticipation;
import com.adarena.auction.domain.AuctionStatus;
import com.adarena.auction.repository.AuctionParticipationRepository;
import com.adarena.auction.repository.AuctionRepository;
import com.adarena.auction.repository.RankingRow;
import com.adarena.earn.domain.SocialTaskStatus;
import com.adarena.earn.repository.SocialTaskRepository;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Tiene al día las webs que se están usando: las de los proyectos que compiten hoy, las de las
 * promociones publicadas y las de los ganadores pendientes de moderar. Así, cuando alguien abre el
 * visor o se aprueba un ganador, su web ya está leída. Solo pide leer las que tienen más de 20 h.
 */
@Service
public class SitePreviewWarmer {

    private static final int MAX_PROJECTS = 50;
    private static final int MAX_PROMOTIONS = 100;

    private final AuctionRepository auctionRepository;
    private final AuctionParticipationRepository participationRepository;
    private final SocialTaskRepository taskRepository;
    private final AdSlotRepository adSlotRepository;
    private final SitePreviewService previewService;

    public SitePreviewWarmer(AuctionRepository auctionRepository, AuctionParticipationRepository participationRepository,
                             SocialTaskRepository taskRepository, AdSlotRepository adSlotRepository,
                             SitePreviewService previewService) {
        this.auctionRepository = auctionRepository;
        this.participationRepository = participationRepository;
        this.taskRepository = taskRepository;
        this.adSlotRepository = adSlotRepository;
        this.previewService = previewService;
    }

    /** @return cuántas webs se han revisado */
    @Transactional(readOnly = true)
    public int warm() {
        Map<String, UUID> sites = new LinkedHashMap<>();

        List<AdSlot> pending = adSlotRepository.findByStatusOrderByCreatedAtAsc(AdSlotStatus.PENDING_REVIEW);
        participationRepository.findAllById(pending.stream().map(AdSlot::getParticipationId).toList()).stream()
                .filter(participation -> participation.getAdSnapshot() != null)
                .forEach(participation -> sites.putIfAbsent(participation.getAdSnapshot().websiteUrl(),
                        participation.getUserId()));

        auctionRepository.findFirstByStatus(AuctionStatus.OPEN).ifPresent(round -> {
            List<RankingRow> ranking = participationRepository.findRanking(round.getId(), Limit.of(MAX_PROJECTS));
            Map<UUID, UUID> owners = participationRepository
                    .findAllById(ranking.stream().map(RankingRow::participationId).toList()).stream()
                    .collect(Collectors.toMap(AuctionParticipation::getId, AuctionParticipation::getUserId,
                            (a, b) -> a));
            ranking.forEach(row -> {
                UUID owner = owners.get(row.participationId());
                if (owner != null) {
                    sites.putIfAbsent(row.websiteUrl(), owner);
                }
            });
        });

        taskRepository.findByStatusOrderByCreatedAtDesc(SocialTaskStatus.ACTIVE, Limit.of(MAX_PROMOTIONS))
                .forEach(task -> sites.putIfAbsent(task.getUrl(), task.getOwnerId()));

        // siteInfos() pide leer (en segundo plano) las que nunca se leyeron o tienen más de 20 h
        previewService.siteInfos(sites);
        return sites.size();
    }
}

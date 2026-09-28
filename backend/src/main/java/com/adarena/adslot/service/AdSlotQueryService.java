package com.adarena.adslot.service;

import com.adarena.adslot.domain.AdSlot;
import com.adarena.adslot.domain.AdSlotStatus;
import com.adarena.adslot.dto.AdminAdSlotView;
import com.adarena.adslot.repository.AdSlotRepository;
import com.adarena.auction.domain.AuctionParticipation;
import com.adarena.auction.repository.AuctionParticipationRepository;
import com.adarena.common.error.ApiException;
import com.adarena.settings.repository.AppSettingsRepository;
import com.adarena.site.dto.SiteDtos.Showcase;
import com.adarena.site.service.SitePreviewJson;
import com.adarena.site.service.SitePreviewRefresher;
import com.adarena.site.service.SitePreviewService;
import com.adarena.user.domain.User;
import com.adarena.user.repository.UserRepository;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Listados de la pantalla de moderación (solo lectura). */
@Service
public class AdSlotQueryService {

    private static final int RECENT = 20;

    private final AdSlotRepository adSlotRepository;
    private final AuctionParticipationRepository participationRepository;
    private final UserRepository userRepository;
    private final AppSettingsRepository settingsRepository;
    private final SitePreviewService sitePreviewService;
    private final SitePreviewRefresher sitePreviewRefresher;
    private final SitePreviewJson sitePreviewJson;

    public AdSlotQueryService(AdSlotRepository adSlotRepository, AuctionParticipationRepository participationRepository,
                              UserRepository userRepository, AppSettingsRepository settingsRepository,
                              SitePreviewService sitePreviewService, SitePreviewRefresher sitePreviewRefresher,
                              SitePreviewJson sitePreviewJson) {
        this.adSlotRepository = adSlotRepository;
        this.participationRepository = participationRepository;
        this.userRepository = userRepository;
        this.settingsRepository = settingsRepository;
        this.sitePreviewService = sitePreviewService;
        this.sitePreviewRefresher = sitePreviewRefresher;
        this.sitePreviewJson = sitePreviewJson;
    }

    /**
     * "Volver a leer su web" desde la moderación (antes de aprobar). La lectura va en segundo plano;
     * nunca más de una vez cada 2 minutos por web.
     */
    @Transactional(readOnly = true)
    public void refreshShowcase(UUID slotId) {
        AdSlot slot = adSlotRepository.findById(slotId)
                .orElseThrow(() -> ApiException.notFound("AD_SLOT_NOT_FOUND", "That ad doesn't exist."));
        participationRepository.findById(slot.getParticipationId())
                .filter(participation -> participation.getAdSnapshot() != null)
                .ifPresent(participation -> sitePreviewRefresher.request(participation.getAdSnapshot().websiteUrl(),
                        slot.getUserId(), Duration.ZERO));
    }

    /** Pendientes de moderar (los más antiguos primero: son los más urgentes). */
    @Transactional(readOnly = true)
    public List<AdminAdSlotView> pending() {
        return toViews(adSlotRepository.findByStatusOrderByCreatedAtAsc(AdSlotStatus.PENDING_REVIEW));
    }

    /** Últimas decisiones (aprobados, rechazados y caducados). */
    @Transactional(readOnly = true)
    public List<AdminAdSlotView> recent() {
        return toViews(adSlotRepository.findByStatusNotOrderByUpdatedAtDesc(AdSlotStatus.PENDING_REVIEW, Limit.of(RECENT)));
    }

    private List<AdminAdSlotView> toViews(List<AdSlot> slots) {
        if (slots.isEmpty()) {
            return List.of();
        }
        Map<UUID, AuctionParticipation> participations = participationRepository
                .findAllById(slots.stream().map(AdSlot::getParticipationId).toList()).stream()
                .collect(Collectors.toMap(AuctionParticipation::getId, Function.identity()));
        Map<UUID, User> users = userRepository.findAllById(slots.stream().map(AdSlot::getUserId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        ZoneId zone = settingsRepository.getSettings().zoneId();
        return slots.stream().map(slot -> {
            AuctionParticipation participation = participations.get(slot.getParticipationId());
            return AdminAdSlotView.from(slot,
                    participation == null ? null : participation.getAuction().getOpensAt().atZone(zone).toLocalDate(),
                    participation == null ? null : participation.getAdSnapshot(),
                    users.get(slot.getUserId()), showcaseOf(slot, participation));
        }).toList();
    }

    private Showcase showcaseOf(AdSlot slot, AuctionParticipation participation) {
        if (slot.getStatus() != AdSlotStatus.PENDING_REVIEW) {
            return sitePreviewJson.readShowcase(slot.getShowcase());
        }
        if (participation == null || participation.getAdSnapshot() == null) {
            return null;
        }
        String url = participation.getAdSnapshot().websiteUrl();
        sitePreviewService.find(url).ifPresentOrElse(
                preview -> sitePreviewService.refreshIfStale(url, slot.getUserId(), preview),
                () -> sitePreviewRefresher.request(url, slot.getUserId()));
        return sitePreviewService.showcase(url).orElse(null);
    }
}

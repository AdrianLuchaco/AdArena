package com.adarena.auction.service;

import com.adarena.auction.domain.Auction;
import com.adarena.auction.domain.AuctionStatus;
import com.adarena.auction.event.ArenaChangedEvent;
import com.adarena.auction.repository.AuctionRepository;
import com.adarena.settings.domain.AppSettings;
import com.adarena.settings.repository.AppSettingsRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * Se asegura de que siempre haya una ronda abierta. Normalmente la abre el cierre diario
 * ({@link ArenaCloseService}) al terminar la anterior; esto cubre el primer arranque (base de
 * datos vacía) y cualquier situación en la que no haya ninguna.
 */
@Service
public class ArenaLifecycleService {

    private final AuctionRepository auctionRepository;
    private final AppSettingsRepository settingsRepository;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ArenaLifecycleService(AuctionRepository auctionRepository, AppSettingsRepository settingsRepository,
                                 ApplicationEventPublisher events, Clock clock) {
        this.auctionRepository = auctionRepository;
        this.settingsRepository = settingsRepository;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Abre la ronda actual si no hay ninguna abierta.
     * La unicidad de auction_date y el índice "solo una abierta" impiden duplicados aunque
     * dos instancias lo intenten a la vez (una de las dos fallará al insertar).
     *
     * @return la ronda creada, o vacío si no hacía falta
     */
    @Transactional
    public Optional<Auction> ensureOpenRound() {
        if (auctionRepository.findFirstByStatus(AuctionStatus.OPEN).isPresent()) {
            return Optional.empty();
        }
        Auction auction = auctionRepository.saveAndFlush(planRound(clock.instant()));
        events.publishEvent(ArenaChangedEvent.roundOpened(auction.getId()));
        return Optional.of(auction);
    }

    /**
     * Una ronda nueva que empieza {@code now} y termina en el próximo cierre (00:00 de Madrid por
     * defecto), con las reglas vigentes ahora mismo. Si ese día ya tiene ronda (por ejemplo, porque
     * el admin cambió la hora de cierre), se pasa al cierre siguiente.
     */
    Auction planRound(Instant now) {
        AppSettings settings = settingsRepository.getSettings();
        ZonedDateTime close = nextCloseAfter(now, settings.getCloseTime(), settings.zoneId());
        while (auctionRepository.existsByAuctionDate(close.toLocalDate())) {
            close = nextCloseAfter(close.toInstant(), settings.getCloseTime(), settings.zoneId());
        }
        LocalDate roundDate = close.toLocalDate();
        return Auction.open(roundDate, now, close.toInstant(), settings.toAuctionRules());
    }

    /**
     * Próximo cierre estrictamente posterior a {@code now}, en hora local (p. ej. las 00:00 de
     * Madrid). Usa fechas de calendario, así que los días de cambio de hora duran 23 o 25 horas.
     */
    public static ZonedDateTime nextCloseAfter(Instant now, LocalTime closeTime, ZoneId zone) {
        LocalDate today = now.atZone(zone).toLocalDate();
        ZonedDateTime candidate = today.atTime(closeTime).atZone(zone);
        if (!candidate.toInstant().isAfter(now)) {
            candidate = today.plusDays(1).atTime(closeTime).atZone(zone);
        }
        return candidate;
    }
}

package com.adarena.auction;

import com.adarena.auction.domain.Auction;
import com.adarena.auction.service.ArenaLifecycleService;
import com.adarena.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
@Transactional
class ArenaLifecycleTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    @Autowired
    ArenaLifecycleService lifecycleService;

    @Test
    void nextCloseIsTheComingMidnightInMadrid() {
        ZonedDateTime close = ArenaLifecycleService.nextCloseAfter(Instant.parse("2026-09-26T10:00:00Z"),
                LocalTime.MIDNIGHT, MADRID);

        assertThat(close.toInstant()).isEqualTo(Instant.parse("2026-09-26T22:00:00Z")); // 00:00 CEST del 27
    }

    @Test
    void exactlyAtCloseTimeTheNextCloseIsTomorrow() {
        ZonedDateTime close = ArenaLifecycleService.nextCloseAfter(Instant.parse("2026-09-26T22:00:00Z"),
                LocalTime.MIDNIGHT, MADRID);

        assertThat(close.toInstant()).isEqualTo(Instant.parse("2026-09-27T22:00:00Z"));
    }

    @Test
    void theRoundOfTheClockChangeLasts25Hours() {
        // La madrugada del 25/10/2026 los relojes vuelven de las 03:00 a las 02:00
        Instant firstClose = ArenaLifecycleService.nextCloseAfter(Instant.parse("2026-10-24T12:00:00Z"),
                LocalTime.MIDNIGHT, MADRID).toInstant();
        Instant secondClose = ArenaLifecycleService.nextCloseAfter(firstClose, LocalTime.MIDNIGHT, MADRID).toInstant();

        assertThat(Duration.between(firstClose, secondClose)).isEqualTo(Duration.ofHours(25));
    }

    @Test
    void opensARoundOnlyWhenNoneIsOpen() {
        Optional<Auction> first = lifecycleService.ensureOpenRound();
        Optional<Auction> second = lifecycleService.ensureOpenRound();

        assertThat(first).isPresent();
        assertThat(first.get().getEndsAt()).isAfter(Instant.now());
        assertThat(second).isEmpty();
    }
}

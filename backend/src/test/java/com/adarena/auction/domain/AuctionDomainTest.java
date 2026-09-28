package com.adarena.auction.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tests unitarios (sin BD) de las reglas que viven en las entidades. */
class AuctionDomainTest {

    private static final Instant OPENS = Instant.parse("2026-09-25T22:00:00Z");
    private static final Instant ENDS = Instant.parse("2026-09-26T22:00:00Z");
    // 50 % de arrastre, anti-sniping: ventana 120 s, extensión 120 s, máximo 2 extensiones
    private static final AuctionRules RULES = new AuctionRules(100, 100, 50, 120, 120, 2);

    private Auction newAuction() {
        return Auction.open(LocalDate.of(2026, 9, 27), OPENS, ENDS, RULES);
    }

    @Test
    void bidsAccumulate() {
        AuctionParticipation p = AuctionParticipation.start(newAuction(), UUID.randomUUID(), 1000, 1, OPENS);
        p.addBid(600, 2, OPENS.plusSeconds(60));

        assertThat(p.getTotalPoints()).isEqualTo(1600); // 1.000 puntos + 600 puntos = 1.600 puntos
        assertThat(p.getLastBidSeq()).isEqualTo(2);
    }

    @Test
    void antiSnipingExtendsWhenBidArrivesInTheLastTwoMinutes() {
        Auction auction = newAuction();

        boolean extended = auction.applyAntiSniping(ENDS.minusSeconds(90));

        assertThat(extended).isTrue();
        assertThat(auction.getEndsAt()).isEqualTo(ENDS.plusSeconds(120));
        assertThat(auction.getExtensionsCount()).isEqualTo(1);
    }

    @Test
    void antiSnipingDoesNothingForEarlierBids() {
        Auction auction = newAuction();

        assertThat(auction.applyAntiSniping(ENDS.minusSeconds(121))).isFalse();
        assertThat(auction.getEndsAt()).isEqualTo(ENDS);
    }

    @Test
    void antiSnipingStopsAtMaxExtensions() {
        Auction auction = newAuction();
        assertThat(auction.applyAntiSniping(ENDS.minusSeconds(10))).isTrue();
        assertThat(auction.applyAntiSniping(ENDS.plusSeconds(110))).isTrue();

        assertThat(auction.applyAntiSniping(ENDS.plusSeconds(230))).isFalse();
        assertThat(auction.getEndsAt()).isEqualTo(ENDS.plusSeconds(240));
    }

    @Test
    void carryOverRoundsDownToTheCent() {
        assertThat(RULES.carryOverOf(1600)).isEqualTo(800);
        assertThat(RULES.carryOverOf(101)).isEqualTo(50); // 50 puntos arrastrados, 51 puntos perdidos
    }

    @Test
    void losingSplitMustAddUpToTheTotal() {
        AuctionParticipation p = AuctionParticipation.start(newAuction(), UUID.randomUUID(), 1000, 1, OPENS);

        assertThatThrownBy(() -> p.markLost(2, 400, 500, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cannotBidOnAClosedParticipation() {
        AuctionParticipation p = AuctionParticipation.start(newAuction(), UUID.randomUUID(), 1000, 1, OPENS);
        p.markLost(2, 500, 500, null);

        assertThatThrownBy(() -> p.addBid(100, 2, OPENS)).isInstanceOf(IllegalStateException.class);
    }
}

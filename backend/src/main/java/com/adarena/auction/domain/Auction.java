package com.adarena.auction.domain;

import com.adarena.common.domain.BaseEntity;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Subasta de un día. Solo puede haber una abierta a la vez (índice único parcial en BD).
 * <p>
 * Toda puja y el cierre bloquean primero esta fila (SELECT ... FOR UPDATE). Eso serializa
 * las operaciones de una misma subasta: dos pujas simultáneas se ejecutan una detrás de otra.
 */
@Entity
@Table(name = "auctions")
public class Auction extends BaseEntity {

    private LocalDate auctionDate;
    private Instant opensAt;
    private Instant scheduledEndAt;
    private Instant endsAt;
    private int extensionsCount;

    @Enumerated(EnumType.STRING)
    private AuctionStatus status;

    @Enumerated(EnumType.STRING)
    private AuctionResult result;

    private Instant closedAt;

    @Embedded
    private AuctionRules rules;

    @CreationTimestamp
    private Instant createdAt;

    @Version
    private long version;

    protected Auction() {
        // JPA
    }

    public static Auction open(LocalDate auctionDate, Instant opensAt, Instant scheduledEndAt, AuctionRules rules) {
        if (!scheduledEndAt.isAfter(opensAt)) {
            throw new IllegalArgumentException("scheduledEndAt must be after opensAt");
        }
        Auction auction = new Auction();
        auction.auctionDate = auctionDate;
        auction.opensAt = opensAt;
        auction.scheduledEndAt = scheduledEndAt;
        auction.endsAt = scheduledEndAt;
        auction.status = AuctionStatus.OPEN;
        auction.rules = rules;
        return auction;
    }

    public boolean acceptsBidsAt(Instant now) {
        return status == AuctionStatus.OPEN && !now.isBefore(opensAt) && now.isBefore(endsAt);
    }

    public boolean isDueForClosing(Instant now) {
        return status == AuctionStatus.OPEN && !now.isBefore(endsAt);
    }

    /**
     * Anti-sniping (regla 5): si la puja entra en la ventana final, el fin se retrasa
     * {@code antiSnipingExtensionSeconds}, hasta un máximo de extensiones.
     *
     * @return true si se ha extendido el contador
     */
    public boolean applyAntiSniping(Instant bidAt) {
        if (extensionsCount >= rules.antiSnipingMaxExtensions() || rules.antiSnipingExtensionSeconds() == 0) {
            return false;
        }
        Duration remaining = Duration.between(bidAt, endsAt);
        if (remaining.compareTo(Duration.ofSeconds(rules.antiSnipingWindowSeconds())) > 0) {
            return false;
        }
        endsAt = endsAt.plusSeconds(rules.antiSnipingExtensionSeconds());
        extensionsCount++;
        return true;
    }

    public void close(Instant now, AuctionResult result) {
        if (status != AuctionStatus.OPEN) {
            throw new IllegalStateException("Auction " + getId() + " is already closed");
        }
        this.status = AuctionStatus.CLOSED;
        this.result = result;
        this.closedAt = now;
    }

    public boolean isOpen() {
        return status == AuctionStatus.OPEN;
    }

    public LocalDate getAuctionDate() {
        return auctionDate;
    }

    public Instant getOpensAt() {
        return opensAt;
    }

    public Instant getScheduledEndAt() {
        return scheduledEndAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public int getExtensionsCount() {
        return extensionsCount;
    }

    public AuctionStatus getStatus() {
        return status;
    }

    public AuctionResult getResult() {
        return result;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public AuctionRules getRules() {
        return rules;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

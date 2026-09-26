package com.publifi.auction.domain;

import com.publifi.common.domain.BaseEntity;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Total acumulado de un usuario en una subasta (regla 2: las pujas se suman).
 * Orden del ranking: total DESC, y a igualdad, quien alcanzó antes su total (last_bid_seq ASC).
 */
@Entity
@Table(name = "auction_participations")
public class AuctionParticipation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "auction_id")
    private Auction auction;

    private UUID userId;
    private long totalCents;
    private long carriedInCents;
    private UUID carriedFromParticipationId;
    private long lastBidSeq;
    private Instant lastBidAt;

    @Enumerated(EnumType.STRING)
    private ParticipationOutcome outcome;

    private Integer finalRank;
    private Long forfeitedCents;
    private Long carriedOutCents;

    @Embedded
    private AdSnapshot adSnapshot;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    @Version
    private long version;

    protected AuctionParticipation() {
        // JPA
    }

    private AuctionParticipation(Auction auction, UUID userId) {
        this.auction = auction;
        this.userId = userId;
        this.outcome = ParticipationOutcome.ACTIVE;
    }

    /** Primera puja del usuario en esta subasta. */
    public static AuctionParticipation start(Auction auction, UUID userId, long amountCents, long seq, Instant at) {
        AuctionParticipation participation = new AuctionParticipation(auction, userId);
        participation.addBid(amountCents, seq, at);
        return participation;
    }

    /** Participación creada automáticamente con el arrastre de un perdedor del día anterior. */
    public static AuctionParticipation carriedOver(Auction auction, UUID userId, long amountCents,
                                                   UUID fromParticipationId, long seq, Instant at) {
        AuctionParticipation participation = new AuctionParticipation(auction, userId);
        participation.addBid(amountCents, seq, at);
        participation.carriedInCents = amountCents;
        participation.carriedFromParticipationId = fromParticipationId;
        return participation;
    }

    public void addBid(long amountCents, long seq, Instant at) {
        requireActive();
        if (amountCents <= 0) {
            throw new IllegalArgumentException("Bid amount must be positive");
        }
        this.totalCents = Math.addExact(totalCents, amountCents);
        this.lastBidSeq = seq;
        this.lastBidAt = at;
    }

    /**
     * Retira el arrastre (su dueño ha pasado a ser el ganador del día anterior tras rechazarse
     * al primero). Devuelve el importe retirado. El desempate no cambia.
     */
    public long reverseCarryOver() {
        requireActive();
        long reversed = carriedInCents;
        this.totalCents -= reversed;
        this.carriedInCents = 0;
        return reversed;
    }

    public void markWon(int rank, AdSnapshot snapshot) {
        requireActive();
        this.outcome = ParticipationOutcome.WON;
        this.finalRank = rank;
        this.adSnapshot = snapshot;
    }

    public void markLost(int rank, long forfeitedCents, long carriedOutCents, AdSnapshot snapshot) {
        requireActive();
        if (forfeitedCents + carriedOutCents != totalCents) {
            throw new IllegalArgumentException("Forfeited + carried must equal total");
        }
        this.outcome = ParticipationOutcome.LOST;
        this.finalRank = rank;
        this.forfeitedCents = forfeitedCents;
        this.carriedOutCents = carriedOutCents;
        this.adSnapshot = snapshot;
    }

    /** El admin rechazó al ganador y este perdedor pasa a ser el nuevo candidato. */
    public void promoteToWinner() {
        if (outcome != ParticipationOutcome.LOST) {
            throw new IllegalStateException("Only a losing participation can be promoted");
        }
        this.outcome = ParticipationOutcome.WON;
    }

    public void markRefunded() {
        if (outcome != ParticipationOutcome.WON) {
            throw new IllegalStateException("Only a winning participation can be refunded");
        }
        this.outcome = ParticipationOutcome.REFUNDED;
    }

    private void requireActive() {
        if (outcome != ParticipationOutcome.ACTIVE) {
            throw new IllegalStateException("Participation " + getId() + " is no longer active");
        }
    }

    public Auction getAuction() {
        return auction;
    }

    public UUID getUserId() {
        return userId;
    }

    public long getTotalCents() {
        return totalCents;
    }

    public long getCarriedInCents() {
        return carriedInCents;
    }

    public UUID getCarriedFromParticipationId() {
        return carriedFromParticipationId;
    }

    public long getLastBidSeq() {
        return lastBidSeq;
    }

    public Instant getLastBidAt() {
        return lastBidAt;
    }

    public ParticipationOutcome getOutcome() {
        return outcome;
    }

    public Integer getFinalRank() {
        return finalRank;
    }

    public Long getForfeitedCents() {
        return forfeitedCents;
    }

    public Long getCarriedOutCents() {
        return carriedOutCents;
    }

    public AdSnapshot getAdSnapshot() {
        return adSnapshot;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

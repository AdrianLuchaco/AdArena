package com.adarena.auction.domain;

import com.adarena.common.domain.BaseEntity;
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
    private long totalPoints;
    private long carriedInPoints;
    private UUID carriedFromParticipationId;
    private long lastBidSeq;
    private Instant lastBidAt;

    @Enumerated(EnumType.STRING)
    private ParticipationOutcome outcome;

    private Integer finalRank;
    private Long forfeitedPoints;
    private Long carriedOutPoints;

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
    public static AuctionParticipation start(Auction auction, UUID userId, long amountPoints, long seq, Instant at) {
        AuctionParticipation participation = new AuctionParticipation(auction, userId);
        participation.addBid(amountPoints, seq, at);
        return participation;
    }

    /** Participación creada automáticamente con el arrastre de un perdedor del día anterior. */
    public static AuctionParticipation carriedOver(Auction auction, UUID userId, long amountPoints,
                                                   UUID fromParticipationId, long seq, Instant at) {
        AuctionParticipation participation = new AuctionParticipation(auction, userId);
        participation.addBid(amountPoints, seq, at);
        participation.carriedInPoints = amountPoints;
        participation.carriedFromParticipationId = fromParticipationId;
        return participation;
    }

    public void addBid(long amountPoints, long seq, Instant at) {
        requireActive();
        if (amountPoints <= 0) {
            throw new IllegalArgumentException("Bid amount must be positive");
        }
        this.totalPoints = Math.addExact(totalPoints, amountPoints);
        this.lastBidSeq = seq;
        this.lastBidAt = at;
    }

    /**
     * Retira el arrastre (su dueño ha pasado a ser el ganador del día anterior tras rechazarse
     * al primero). Devuelve el importe retirado. El desempate no cambia.
     */
    public long reverseCarryOver() {
        requireActive();
        long reversed = carriedInPoints;
        this.totalPoints -= reversed;
        this.carriedInPoints = 0;
        return reversed;
    }

    public void markWon(int rank, AdSnapshot snapshot) {
        requireActive();
        this.outcome = ParticipationOutcome.WON;
        this.finalRank = rank;
        this.adSnapshot = snapshot;
    }

    public void markLost(int rank, long forfeitedPoints, long carriedOutPoints, AdSnapshot snapshot) {
        requireActive();
        if (forfeitedPoints < 0 || carriedOutPoints < 0 || forfeitedPoints + carriedOutPoints != totalPoints) {
            throw new IllegalArgumentException("Forfeited + carried must equal total");
        }
        this.outcome = ParticipationOutcome.LOST;
        this.finalRank = rank;
        this.forfeitedPoints = forfeitedPoints;
        this.carriedOutPoints = carriedOutPoints;
        this.adSnapshot = snapshot;
    }

    /**
     * Cierre de una participación que se quedó en 0 puntos (se le retiró el arrastre porque su dueño
     * pasó a ser candidato del día anterior). No entra en la clasificación y no mueve puntos.
     */
    public void markWithdrawn() {
        requireActive();
        if (totalPoints != 0) {
            throw new IllegalStateException("Only an empty participation can be withdrawn");
        }
        this.outcome = ParticipationOutcome.LOST;
        this.forfeitedPoints = 0L;
        this.carriedOutPoints = 0L;
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

    public long getTotalPoints() {
        return totalPoints;
    }

    public long getCarriedInPoints() {
        return carriedInPoints;
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

    public Long getForfeitedPoints() {
        return forfeitedPoints;
    }

    public Long getCarriedOutPoints() {
        return carriedOutPoints;
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

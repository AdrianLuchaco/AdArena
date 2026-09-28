package com.adarena.auction.domain;

import com.adarena.common.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/**
 * Una aportación individual al total de una participación (historial de pujas). Inmutable.
 */
@Entity
@Immutable
@Table(name = "bids")
public class Bid extends BaseEntity {

    private long seq;
    private UUID auctionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "participation_id")
    private AuctionParticipation participation;

    private UUID userId;

    @Enumerated(EnumType.STRING)
    private BidType type;

    private long amountPoints;
    private long totalAfterPoints;
    private String idempotencyKey;

    @CreationTimestamp
    private Instant createdAt;

    protected Bid() {
        // JPA
    }

    private Bid(long seq, AuctionParticipation participation, BidType type, long amountPoints, String idempotencyKey) {
        this.seq = seq;
        this.auctionId = participation.getAuction().getId();
        this.participation = participation;
        this.userId = participation.getUserId();
        this.type = type;
        this.amountPoints = amountPoints;
        this.totalAfterPoints = participation.getTotalPoints();
        this.idempotencyKey = idempotencyKey;
    }

    /** Registrar DESPUÉS de haber sumado el importe a la participación. */
    public static Bid bid(long seq, AuctionParticipation participation, long amountPoints, String idempotencyKey) {
        return new Bid(seq, participation, BidType.BID, amountPoints, idempotencyKey);
    }

    public static Bid carryOver(long seq, AuctionParticipation participation, long amountPoints) {
        return new Bid(seq, participation, BidType.CARRY_OVER, amountPoints, null);
    }

    public static Bid carryReversal(long seq, AuctionParticipation participation, long reversedPoints) {
        return new Bid(seq, participation, BidType.CARRY_REVERSAL, -reversedPoints, null);
    }

    public long getSeq() {
        return seq;
    }

    public UUID getAuctionId() {
        return auctionId;
    }

    public AuctionParticipation getParticipation() {
        return participation;
    }

    public UUID getUserId() {
        return userId;
    }

    public BidType getType() {
        return type;
    }

    public long getAmountPoints() {
        return amountPoints;
    }

    public long getTotalAfterPoints() {
        return totalAfterPoints;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

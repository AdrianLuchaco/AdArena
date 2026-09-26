package com.publifi.adslot.domain;

import com.publifi.common.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Candidato a ocupar la web durante la ventana [startsAt, endsAt) tras una subasta.
 * La ventana es fija: no se desplaza aunque la moderación tarde.
 */
@Entity
@Table(name = "ad_slots")
public class AdSlot extends BaseEntity {

    private UUID auctionId;
    private UUID participationId;
    private UUID userId;
    private int candidateRank;

    @Enumerated(EnumType.STRING)
    private AdSlotStatus status;

    private long amountCents;
    private Instant startsAt;
    private Instant endsAt;
    private UUID reviewedBy;
    private Instant reviewedAt;
    private String rejectionReason;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    @Version
    private long version;

    protected AdSlot() {
        // JPA
    }

    public AdSlot(UUID auctionId, UUID participationId, UUID userId, int candidateRank,
                  long amountCents, Instant startsAt, Instant endsAt) {
        this.auctionId = auctionId;
        this.participationId = participationId;
        this.userId = userId;
        this.candidateRank = candidateRank;
        this.amountCents = amountCents;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.status = AdSlotStatus.PENDING_REVIEW;
    }

    public void approve(UUID adminId, Instant now) {
        requirePending();
        this.status = AdSlotStatus.APPROVED;
        this.reviewedBy = adminId;
        this.reviewedAt = now;
    }

    public void reject(UUID adminId, String reason, Instant now) {
        requirePending();
        this.status = AdSlotStatus.REJECTED;
        this.reviewedBy = adminId;
        this.reviewedAt = now;
        this.rejectionReason = reason;
    }

    public void expire() {
        requirePending();
        this.status = AdSlotStatus.EXPIRED;
    }

    public boolean isLiveAt(Instant now) {
        return status == AdSlotStatus.APPROVED && !now.isBefore(startsAt) && now.isBefore(endsAt);
    }

    private void requirePending() {
        if (status != AdSlotStatus.PENDING_REVIEW) {
            throw new IllegalStateException("Ad slot " + getId() + " is not pending review");
        }
    }

    public UUID getAuctionId() {
        return auctionId;
    }

    public UUID getParticipationId() {
        return participationId;
    }

    public UUID getUserId() {
        return userId;
    }

    public int getCandidateRank() {
        return candidateRank;
    }

    public AdSlotStatus getStatus() {
        return status;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public UUID getReviewedBy() {
        return reviewedBy;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

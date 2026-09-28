package com.adarena.adslot.domain;

import com.adarena.common.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

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

    private long amountPoints;
    private Instant startsAt;
    private Instant endsAt;
    private UUID reviewedBy;
    private Instant reviewedAt;
    private String rejectionReason;

    /**
     * La presentación animada del ganador (JSON), congelada al aprobarlo: la portada muestra
     * exactamente lo que revisó el administrador, aunque su web cambie después.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    private String showcase;

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
                  long amountPoints, Instant startsAt, Instant endsAt) {
        this.auctionId = auctionId;
        this.participationId = participationId;
        this.userId = userId;
        this.candidateRank = candidateRank;
        this.amountPoints = amountPoints;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.status = AdSlotStatus.PENDING_REVIEW;
    }

    public void approve(UUID adminId, Instant now, String showcaseJson) {
        requirePending();
        this.status = AdSlotStatus.APPROVED;
        this.reviewedBy = adminId;
        this.reviewedAt = now;
        this.showcase = showcaseJson;
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

    public long getAmountPoints() {
        return amountPoints;
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

    public String getShowcase() {
        return showcase;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

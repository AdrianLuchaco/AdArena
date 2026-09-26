package com.publifi.payment.domain;

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
 * Recarga del monedero mediante Stripe Checkout. Se crea PENDING al iniciar el pago y solo
 * pasa a SUCCEEDED cuando llega el webhook firmado de Stripe (nunca por la redirección del
 * navegador, que se puede falsificar).
 */
@Entity
@Table(name = "top_ups")
public class TopUp extends BaseEntity {

    private UUID userId;
    private long amountCents;
    private String currency;

    @Enumerated(EnumType.STRING)
    private TopUpStatus status;

    private String stripeCheckoutSessionId;
    private String stripePaymentIntentId;
    private UUID ledgerTransactionId;
    private Instant completedAt;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    @Version
    private long version;

    protected TopUp() {
        // JPA
    }

    public TopUp(UUID userId, long amountCents) {
        if (amountCents <= 0) {
            throw new IllegalArgumentException("Top-up amount must be positive");
        }
        this.userId = userId;
        this.amountCents = amountCents;
        this.currency = "eur";
        this.status = TopUpStatus.PENDING;
    }

    public void attachCheckoutSession(String sessionId) {
        this.stripeCheckoutSessionId = sessionId;
    }

    public void markSucceeded(String paymentIntentId, UUID ledgerTransactionId, Instant now) {
        if (status != TopUpStatus.PENDING) {
            throw new IllegalStateException("Top-up " + getId() + " is not pending");
        }
        this.status = TopUpStatus.SUCCEEDED;
        this.stripePaymentIntentId = paymentIntentId;
        this.ledgerTransactionId = ledgerTransactionId;
        this.completedAt = now;
    }

    public void markFailed(TopUpStatus finalStatus, Instant now) {
        if (finalStatus != TopUpStatus.FAILED && finalStatus != TopUpStatus.EXPIRED) {
            throw new IllegalArgumentException("Use FAILED or EXPIRED");
        }
        if (status == TopUpStatus.PENDING) {
            this.status = finalStatus;
            this.completedAt = now;
        }
    }

    public UUID getUserId() {
        return userId;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public String getCurrency() {
        return currency;
    }

    public TopUpStatus getStatus() {
        return status;
    }

    public String getStripeCheckoutSessionId() {
        return stripeCheckoutSessionId;
    }

    public String getStripePaymentIntentId() {
        return stripePaymentIntentId;
    }

    public UUID getLedgerTransactionId() {
        return ledgerTransactionId;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

package com.publifi.payment.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import java.time.Instant;

/**
 * Evento de webhook de Stripe ya recibido. Su PK es el id de Stripe (evt_...): si el mismo
 * evento llega dos veces, el segundo INSERT falla y no se procesa de nuevo (idempotencia).
 */
@Entity
@Table(name = "stripe_events")
public class StripeEvent implements Persistable<String> {

    @Id
    private String id;

    private String type;

    @JdbcTypeCode(SqlTypes.JSON)
    private String payload;

    private Instant receivedAt;
    private Instant processedAt;
    private String error;

    @Transient
    private boolean isNew = true;

    protected StripeEvent() {
        // JPA
    }

    public StripeEvent(String id, String type, String payload, Instant receivedAt) {
        this.id = id;
        this.type = type;
        this.payload = payload;
        this.receivedAt = receivedAt;
    }

    public void markProcessed(Instant now) {
        this.processedAt = now;
        this.error = null;
    }

    public void markFailed(String error) {
        this.error = error == null ? null : error.substring(0, Math.min(error.length(), 1000));
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }

    public String getType() {
        return type;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public String getError() {
        return error;
    }
}

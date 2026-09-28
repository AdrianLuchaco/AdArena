package com.adarena.wallet.domain;

import com.adarena.common.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

/** Apunte del ledger. Inmutable (además, un trigger en BD impide UPDATE y DELETE). */
@Entity
@Immutable
@Table(name = "ledger_entries")
public class LedgerEntry extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id")
    private LedgerTransaction transaction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id")
    private LedgerAccount account;

    private long amountPoints;
    private long balanceAfterPoints;

    @CreationTimestamp
    private Instant createdAt;

    protected LedgerEntry() {
        // JPA
    }

    LedgerEntry(LedgerTransaction transaction, LedgerAccount account, long amountPoints, long balanceAfterPoints) {
        this.transaction = transaction;
        this.account = account;
        this.amountPoints = amountPoints;
        this.balanceAfterPoints = balanceAfterPoints;
    }

    public LedgerTransaction getTransaction() {
        return transaction;
    }

    public LedgerAccount getAccount() {
        return account;
    }

    public long getAmountPoints() {
        return amountPoints;
    }

    public long getBalanceAfterPoints() {
        return balanceAfterPoints;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

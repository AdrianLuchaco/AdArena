package com.publifi.wallet.domain;

import com.publifi.common.domain.BaseEntity;
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

    private long amountCents;
    private long balanceAfterCents;

    @CreationTimestamp
    private Instant createdAt;

    protected LedgerEntry() {
        // JPA
    }

    LedgerEntry(LedgerTransaction transaction, LedgerAccount account, long amountCents, long balanceAfterCents) {
        this.transaction = transaction;
        this.account = account;
        this.amountCents = amountCents;
        this.balanceAfterCents = balanceAfterCents;
    }

    public LedgerTransaction getTransaction() {
        return transaction;
    }

    public LedgerAccount getAccount() {
        return account;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public long getBalanceAfterCents() {
        return balanceAfterCents;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

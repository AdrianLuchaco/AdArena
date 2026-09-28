package com.adarena.wallet.domain;

import com.adarena.common.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Cuenta del ledger. {@code balancePoints} es un saldo cacheado que siempre debe coincidir
 * con la suma de sus apuntes (lo vigila la vista {@code ledger_account_mismatches}).
 * Solo se modifica a través de {@link LedgerTransaction#post(LedgerAccount, long)}.
 */
@Entity
@Table(name = "ledger_accounts")
public class LedgerAccount extends BaseEntity {

    private UUID userId;

    @Enumerated(EnumType.STRING)
    private LedgerAccountType type;

    private long balancePoints;

    @CreationTimestamp
    private Instant createdAt;

    @Version
    private long version;

    protected LedgerAccount() {
        // JPA
    }

    public static LedgerAccount forUser(UUID userId, LedgerAccountType type) {
        if (!type.isUserAccount()) {
            throw new IllegalArgumentException(type + " is not a user account type");
        }
        LedgerAccount account = new LedgerAccount();
        account.userId = userId;
        account.type = type;
        return account;
    }

    /** Package-private: solo LedgerTransaction mueve saldos. */
    void apply(long amountPoints) {
        long newBalance = Math.addExact(balancePoints, amountPoints);
        if (type.isUserAccount() && newBalance < 0) {
            throw new InsufficientFundsException(getId(), balancePoints, -amountPoints);
        }
        this.balancePoints = newBalance;
    }

    public UUID getUserId() {
        return userId;
    }

    public LedgerAccountType getType() {
        return type;
    }

    public long getBalancePoints() {
        return balancePoints;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

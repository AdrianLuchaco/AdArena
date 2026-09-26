package com.publifi.wallet.domain;

import com.publifi.common.domain.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Movimiento de dinero de partida doble: sus apuntes siempre suman cero.
 * <pre>
 *   LedgerTransaction tx = LedgerTransaction.of(BID_RESERVE, "bid:" + bidId, "BID", bidId, "Puja")
 *           .post(available, -1000)
 *           .post(reserved, +1000);
 *   tx.assertBalanced();
 * </pre>
 * Las cuentas deben estar bloqueadas (SELECT ... FOR UPDATE) antes de llamar a {@link #post}.
 */
@Entity
@Immutable
@Table(name = "ledger_transactions")
public class LedgerTransaction extends BaseEntity {

    @Enumerated(EnumType.STRING)
    private LedgerTransactionType type;

    private String idempotencyKey;
    private String referenceType;
    private UUID referenceId;
    private String description;

    @CreationTimestamp
    private Instant createdAt;

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.PERSIST)
    private List<LedgerEntry> entries = new ArrayList<>();

    protected LedgerTransaction() {
        // JPA
    }

    public static LedgerTransaction of(LedgerTransactionType type, String idempotencyKey,
                                       String referenceType, UUID referenceId, String description) {
        LedgerTransaction tx = new LedgerTransaction();
        tx.type = type;
        tx.idempotencyKey = idempotencyKey;
        tx.referenceType = referenceType;
        tx.referenceId = referenceId;
        tx.description = description;
        return tx;
    }

    /** Añade un apunte y actualiza el saldo de la cuenta. Positivo = entra, negativo = sale. */
    public LedgerTransaction post(LedgerAccount account, long amountCents) {
        if (amountCents == 0) {
            throw new IllegalArgumentException("Ledger entry amount cannot be zero");
        }
        account.apply(amountCents);
        entries.add(new LedgerEntry(this, account, amountCents, account.getBalanceCents()));
        return this;
    }

    public void assertBalanced() {
        long sum = entries.stream().mapToLong(LedgerEntry::getAmountCents).sum();
        if (entries.size() < 2 || sum != 0) {
            throw new IllegalStateException("Ledger transaction " + idempotencyKey + " is unbalanced: " + sum);
        }
    }

    public LedgerTransactionType getType() {
        return type;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public UUID getReferenceId() {
        return referenceId;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<LedgerEntry> getEntries() {
        return Collections.unmodifiableList(entries);
    }
}

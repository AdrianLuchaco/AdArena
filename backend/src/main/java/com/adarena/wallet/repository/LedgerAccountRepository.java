package com.adarena.wallet.repository;

import com.adarena.wallet.domain.LedgerAccount;
import com.adarena.wallet.domain.LedgerAccountType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LedgerAccountRepository extends JpaRepository<LedgerAccount, UUID> {

    Optional<LedgerAccount> findByUserIdAndType(UUID userId, LedgerAccountType type);

    @Query("SELECT a FROM LedgerAccount a WHERE a.userId IS NULL AND a.type = :type")
    Optional<LedgerAccount> findSystemAccount(@Param("type") LedgerAccountType type);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM LedgerAccount a WHERE a.userId IS NULL AND a.type = :type")
    Optional<LedgerAccount> findSystemAccountForUpdate(@Param("type") LedgerAccountType type);

    /**
     * Crea la cuenta del usuario si no existe. "ON CONFLICT DO NOTHING" hace que dos peticiones
     * simultáneas no choquen: la segunda simplemente no inserta nada.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO ledger_accounts (id, user_id, type, balance_points)
            VALUES (gen_random_uuid(), :userId, :type, 0)
            ON CONFLICT (user_id, type) WHERE user_id IS NOT NULL DO NOTHING
            """, nativeQuery = true)
    void insertIfMissing(@Param("userId") UUID userId, @Param("type") String type);

    /** Las cuentas del usuario, bloqueadas y SIEMPRE en el mismo orden (evita interbloqueos). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM LedgerAccount a WHERE a.userId = :userId ORDER BY a.id")
    List<LedgerAccount> findUserAccountsForUpdate(@Param("userId") UUID userId);

    /** Las cuentas de VARIOS usuarios (p. ej. todos los perdedores del cierre), bloqueadas en orden de id. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM LedgerAccount a WHERE a.userId IN :userIds ORDER BY a.id")
    List<LedgerAccount> findAccountsOfUsersForUpdate(@Param("userIds") Collection<UUID> userIds);

    /** Suma de saldos por tipo de cuenta (panel de administración). */
    @Query("SELECT a.type AS type, COALESCE(SUM(a.balancePoints), 0) AS total FROM LedgerAccount a GROUP BY a.type")
    List<TypeTotal> totalsByType();

    interface TypeTotal {
        LedgerAccountType getType();

        long getTotal();
    }
}

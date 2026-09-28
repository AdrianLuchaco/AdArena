package com.adarena.wallet.repository;

import com.adarena.wallet.domain.LedgerEntry;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    /** Los movimientos de los puntos LIBRES del usuario, los más recientes primero. */
    @Query("""
            SELECT new com.adarena.wallet.repository.MovementRow(
                e.createdAt, t.type, t.description, e.amountPoints, e.balanceAfterPoints)
            FROM LedgerEntry e JOIN e.transaction t JOIN e.account a
            WHERE a.userId = :userId AND a.type = com.adarena.wallet.domain.LedgerAccountType.USER_AVAILABLE
            ORDER BY e.createdAt DESC
            """)
    List<MovementRow> findMovements(@Param("userId") UUID userId, Limit limit);

    /** Puntos repartidos desde {@code since} (panel de administración). */
    @Query("""
            SELECT COALESCE(-SUM(e.amountPoints), 0) FROM LedgerEntry e JOIN e.account a
            WHERE a.userId IS NULL AND a.type = com.adarena.wallet.domain.LedgerAccountType.POINTS_ISSUED
              AND e.createdAt >= :since
            """)
    long pointsIssuedSince(@Param("since") Instant since);
}

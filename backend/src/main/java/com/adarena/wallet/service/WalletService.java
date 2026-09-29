package com.adarena.wallet.service;

import com.adarena.wallet.domain.LedgerAccount;
import com.adarena.wallet.domain.LedgerAccountType;
import com.adarena.wallet.domain.LedgerTransaction;
import com.adarena.wallet.domain.LedgerTransactionType;
import com.adarena.wallet.repository.LedgerAccountRepository;
import com.adarena.wallet.repository.LedgerTransactionRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Los Crown Points de cada usuario, sobre el ledger de partida doble.
 * <p>
 * Orden de bloqueo en TODA la aplicación (mismo orden siempre = nunca hay interbloqueos):
 * <ol>
 *   <li>fila "de negocio" (hueco de anuncio, visita o tarea), si la hay;</li>
 *   <li>la ronda (subasta);</li>
 *   <li>las participaciones;</li>
 *   <li>las cuentas de TODOS los usuarios implicados, a la vez y por id;</li>
 *   <li>las cuentas del sistema: primero POINTS_ISSUED y después POINTS_SPENT.</li>
 * </ol>
 */
@Service
public class WalletService {

    private final LedgerAccountRepository accountRepository;
    private final LedgerTransactionRepository transactionRepository;
    private final JdbcTemplate jdbc;

    public WalletService(LedgerAccountRepository accountRepository, LedgerTransactionRepository transactionRepository,
                         JdbcTemplate jdbc) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.jdbc = jdbc;
    }

    public record Balance(long availablePoints, long reservedPoints) {
        public long totalPoints() {
            return availablePoints + reservedPoints;
        }
    }

    public record UserAccounts(LedgerAccount available, LedgerAccount reserved) {
        public Balance balance() {
            return new Balance(available.getBalancePoints(), reserved.getBalancePoints());
        }
    }

    /**
     * Totales de toda la plataforma (panel de administración).
     *
     * @param spentPoints  puntos gastados en la Arena desde el principio (ganadores + 50 % perdido)
     * @param issuedPoints puntos repartidos desde el principio (bienvenida, visitas, tareas)
     */
    public record PlatformTotals(long spentPoints, long issuedPoints, long usersAvailablePoints,
                                 long usersReservedPoints, long ledgerMismatches) {
    }

    /** Crea las cuentas si faltan y las bloquea (SELECT ... FOR UPDATE) hasta el final de la transacción. */
    @Transactional
    public UserAccounts lockUserAccounts(UUID userId) {
        return lockUserAccounts(List.of(userId)).get(userId);
    }

    /**
     * Bloquea las cuentas de varios usuarios A LA VEZ y en orden de id (p. ej. todos los
     * perdedores de un cierre). Bloquearlas una a una en otro orden podría provocar interbloqueos.
     */
    @Transactional
    public Map<UUID, UserAccounts> lockUserAccounts(Collection<UUID> userIds) {
        TreeSet<UUID> ids = new TreeSet<>(userIds);
        for (UUID userId : ids) {
            accountRepository.insertIfMissing(userId, LedgerAccountType.USER_AVAILABLE.name());
            accountRepository.insertIfMissing(userId, LedgerAccountType.USER_RESERVED.name());
        }
        Map<UUID, LedgerAccount> available = new HashMap<>();
        Map<UUID, LedgerAccount> reserved = new HashMap<>();
        for (LedgerAccount account : accountRepository.findAccountsOfUsersForUpdate(ids)) {
            if (account.getType() == LedgerAccountType.USER_AVAILABLE) available.put(account.getUserId(), account);
            if (account.getType() == LedgerAccountType.USER_RESERVED) reserved.put(account.getUserId(), account);
        }
        Map<UUID, UserAccounts> result = new HashMap<>();
        for (UUID userId : ids) {
            if (!available.containsKey(userId) || !reserved.containsKey(userId)) {
                throw new IllegalStateException("User " + userId + " wallet accounts are missing");
            }
            result.put(userId, new UserAccounts(available.get(userId), reserved.get(userId)));
        }
        return result;
    }

    /** Cuenta del sistema bloqueada. Siempre DESPUÉS de las cuentas de usuario. */
    @Transactional
    public LedgerAccount lockSystemAccount(LedgerAccountType type) {
        return accountRepository.findSystemAccountForUpdate(type)
                .orElseThrow(() -> new IllegalStateException("System account " + type + " is missing"));
    }

    /** Guarda un movimiento ya construido, comprobando antes que cuadra (suma cero). */
    @Transactional
    public LedgerTransaction record(LedgerTransaction transaction) {
        transaction.assertBalanced();
        return transactionRepository.save(transaction);
    }

    @Transactional(readOnly = true)
    public Balance balance(UUID userId) {
        long available = accountRepository.findByUserIdAndType(userId, LedgerAccountType.USER_AVAILABLE)
                .map(LedgerAccount::getBalancePoints).orElse(0L);
        long reserved = accountRepository.findByUserIdAndType(userId, LedgerAccountType.USER_RESERVED)
                .map(LedgerAccount::getBalancePoints).orElse(0L);
        return new Balance(available, reserved);
    }

    /** Puja: los puntos pasan de libres a reservados. Lanza InsufficientFundsException si no llegan. */
    @Transactional
    public void reserveForBid(UserAccounts accounts, UUID bidId, long amountPoints) {
        record(LedgerTransaction.of(LedgerTransactionType.BID_RESERVE, "bid:" + bidId,
                        "BID", bidId, "Bid in the Race")
                .post(accounts.available(), -amountPoints)
                .post(accounts.reserved(), amountPoints));
    }

    @Transactional(readOnly = true)
    public PlatformTotals platformTotals() {
        Map<LedgerAccountType, Long> totals = new EnumMap<>(LedgerAccountType.class);
        for (LedgerAccountRepository.TypeTotal row : accountRepository.totalsByType()) {
            totals.put(row.getType(), row.getTotal());
        }
        Long mismatches = jdbc.queryForObject("SELECT count(*) FROM ledger_account_mismatches", Long.class);
        return new PlatformTotals(
                totals.getOrDefault(LedgerAccountType.POINTS_SPENT, 0L),
                -totals.getOrDefault(LedgerAccountType.POINTS_ISSUED, 0L),
                totals.getOrDefault(LedgerAccountType.USER_AVAILABLE, 0L),
                totals.getOrDefault(LedgerAccountType.USER_RESERVED, 0L),
                mismatches == null ? 0 : mismatches);
    }

    /**
     * Regala puntos a un usuario (bienvenida, visitas, tareas): salen de POINTS_ISSUED y entran en
     * sus puntos libres. {@code idempotencyKey} es única: la misma recompensa nunca se da dos veces.
     * Orden de bloqueo: cuentas del usuario → cuenta del sistema.
     */
    @Transactional
    public Balance grant(UUID userId, long points, LedgerTransactionType type, String idempotencyKey,
                         String referenceType, UUID referenceId, String description) {
        if (points <= 0) {
            throw new IllegalArgumentException("Points to grant must be positive");
        }
        UserAccounts accounts = lockUserAccounts(userId);
        LedgerAccount issued = lockSystemAccount(LedgerAccountType.POINTS_ISSUED);
        record(LedgerTransaction.of(type, idempotencyKey, referenceType, referenceId, description)
                .post(issued, -points)
                .post(accounts.available(), points));
        return accounts.balance();
    }

    /** SOLO PARA DESARROLLO Y TESTS: puntos de prueba. */
    @Transactional
    public Balance grantTestPoints(UUID userId, long points) {
        UUID reference = UUID.randomUUID();
        return grant(userId, points, LedgerTransactionType.TEST_GRANT, "test:" + reference, "TEST_GRANT", reference,
                "Test points (development)");
    }
}

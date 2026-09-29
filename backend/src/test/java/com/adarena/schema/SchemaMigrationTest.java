package com.adarena.schema;

import com.adarena.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprueba que las migraciones se aplican y que las "redes de seguridad" de la base de datos
 * funcionan aunque el código Java fallara. Si el contexto arranca, además, Hibernate ya ha
 * validado que todas las entidades encajan con el esquema (ddl-auto=validate).
 */
@IntegrationTest
class SchemaMigrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Test
    void appliesAllMigrations() {
        Integer applied = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success AND version IS NOT NULL", Integer.class);
        assertThat(applied).isEqualTo(14); // V1…V14
    }

    /**
     * V13 con un administrador que ya existe (como en la nube): sus enlaces de YouTube y X pasan a
     * destacados y se añaden los 6 enlaces iniciales (sin duplicarlos). Se deshace al terminar.
     */
    @Test
    void featuredLinksMigrationSeedsStarterLinksOnce() throws IOException {
        String sql = new ClassPathResource("db/migration/V13__featured_bonus_links.sql")
                .getContentAsString(StandardCharsets.UTF_8)
                .replace("ALTER TABLE social_tasks ADD COLUMN featured boolean NOT NULL DEFAULT false;", "");
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            UUID adminId = jdbc.queryForObject("SELECT id FROM users WHERE role = 'ADMIN' ORDER BY created_at LIMIT 1", UUID.class);
            jdbc.update("""
                    INSERT INTO social_tasks (id, owner_id, platform, title, url, status, reward_points)
                    VALUES (gen_random_uuid(), ?, 'X', 'Mi X', 'https://x.com/prueba-v13', 'ACTIVE', 20)
                    """, adminId);

            jdbc.execute(sql);

            assertThat(jdbc.queryForObject("SELECT featured AND reward_points = 100 FROM social_tasks WHERE url = 'https://x.com/prueba-v13'",
                    Boolean.class)).isTrue();
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM social_tasks
                    WHERE owner_id = ? AND url IN ('https://news.ycombinator.com', 'https://www.producthunt.com',
                        'https://github.com/trending', 'https://www.youtube.com/@Fireship', 'https://roadmap.sh',
                        'https://www.indiehackers.com') AND status = 'ACTIVE' AND reward_points = 20
                    """, Integer.class, adminId)).isEqualTo(6);

            // Si se volviera a ejecutar, no se duplica ningún enlace
            jdbc.execute(sql);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM social_tasks WHERE url = 'https://roadmap.sh'", Integer.class))
                    .isEqualTo(1);
            status.setRollbackOnly();
        });
    }

    /** V14 (AdArena → LaunchCrown): los avisos ya enviados pasan a usar los nombres nuevos. Se deshace al terminar. */
    @Test
    void renameMigrationUpdatesExistingNotifications() throws IOException {
        String sql = new ClassPathResource("db/migration/V14__rename_to_launchcrown.sql").getContentAsString(StandardCharsets.UTF_8);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            UUID userId = jdbc.queryForObject("SELECT id FROM users ORDER BY created_at LIMIT 1", UUID.class);
            UUID id = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO notifications (id, user_id, type, title, body, link)
                    VALUES (?, ?, 'AUCTION_LOST', 'The Arena has closed',
                            'You keep 50 Arena Points for today''s Arena on AdArena.', '/arena?tab=bids')
                    """, id, userId);

            jdbc.execute(sql);

            Map<String, Object> row = jdbc.queryForMap("SELECT title, body, link FROM notifications WHERE id = ?", id);
            assertThat(row.get("title")).isEqualTo("The Race has closed");
            assertThat(row.get("body")).isEqualTo("You keep 50 Crown Points for today's Race on LaunchCrown.");
            assertThat(row.get("link")).isEqualTo("/race?tab=bids");
            status.setRollbackOnly();
        });
    }

    @Test
    void seedsDefaultSettings() {
        Map<String, Object> settings = jdbc.queryForMap("SELECT * FROM app_settings WHERE id = 1");

        assertThat(settings.get("min_bid_points")).isEqualTo(100L);
        assertThat(settings.get("min_increment_points")).isEqualTo(100L);
        assertThat(settings.get("carry_over_percent")).isEqualTo(50);
        assertThat(settings.get("time_zone")).isEqualTo("Europe/Madrid");
        assertThat(settings.get("anti_sniping_window_seconds")).isEqualTo(120);
        assertThat(settings.get("anti_sniping_extension_seconds")).isEqualTo(120);
    }

    @Test
    void seedsSystemLedgerAccounts() {
        Integer systemAccounts = jdbc.queryForObject(
                "SELECT count(*) FROM ledger_accounts WHERE user_id IS NULL AND type IN ('POINTS_SPENT', 'POINTS_ISSUED')",
                Integer.class);
        assertThat(systemAccounts).isEqualTo(2);
    }

    @Test
    void rejectsUnbalancedLedgerTransactionOnCommit() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            UUID txId = insertLedgerTransaction("test:unbalanced");
            insertLedgerEntry(txId, systemAccount("POINTS_SPENT"), 500);
            // Falta la contrapartida de -500: el trigger diferido lo detecta al hacer COMMIT
        })).hasStackTraceContaining("is unbalanced");

        Integer persisted = jdbc.queryForObject(
                "SELECT count(*) FROM ledger_transactions WHERE idempotency_key = 'test:unbalanced'", Integer.class);
        assertThat(persisted).isZero();
    }

    @Test
    void ledgerEntriesAreAppendOnly() {
        inRolledBackTransaction(jdbc -> {
            UUID txId = insertLedgerTransaction("test:append-only");
            insertLedgerEntry(txId, systemAccount("POINTS_ISSUED"), -100);
            insertLedgerEntry(txId, systemAccount("POINTS_SPENT"), 100);

            assertThatThrownBy(() -> jdbc.update(
                    "UPDATE ledger_entries SET amount_points = 999 WHERE transaction_id = ?", txId))
                    .hasStackTraceContaining("append-only");
        });
    }

    @Test
    void allowsOnlyOneOpenAuction() {
        inRolledBackTransaction(jdbc -> {
            insertOpenAuction("2030-01-01");

            assertThatThrownBy(() -> insertOpenAuction("2030-01-02"))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("ux_auctions_single_open");
        });
    }

    @Test
    void userAccountsCannotGoNegative() {
        inRolledBackTransaction(jdbc -> {
            UUID userId = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO users (id, email, password_hash, display_name, role, accepted_terms_version, accepted_terms_at)
                    VALUES (?, ?, 'x', 'Test', 'USER', 'v1', now())""", userId, userId + "@test.dev");

            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO ledger_accounts (id, user_id, type, balance_points)
                    VALUES (?, ?, 'USER_AVAILABLE', -1)""", UUID.randomUUID(), userId))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("ck_ledger_accounts_user_non_negative");
        });
    }

    // ------------------------------------------------------------------ helpers

    private void inRolledBackTransaction(Consumer<JdbcTemplate> work) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            work.accept(jdbc);
            status.setRollbackOnly();
        });
    }

    private UUID systemAccount(String type) {
        return jdbc.queryForObject("SELECT id FROM ledger_accounts WHERE user_id IS NULL AND type = ?", UUID.class, type);
    }

    private UUID insertLedgerTransaction(String idempotencyKey) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO ledger_transactions (id, type, idempotency_key) VALUES (?, 'ADMIN_ADJUSTMENT', ?)",
                id, idempotencyKey);
        return id;
    }

    private void insertLedgerEntry(UUID txId, UUID accountId, long amountPoints) {
        jdbc.update("""
                INSERT INTO ledger_entries (id, transaction_id, account_id, amount_points, balance_after_points)
                VALUES (?, ?, ?, ?, 0)""", UUID.randomUUID(), txId, accountId, amountPoints);
    }

    private void insertOpenAuction(String date) {
        jdbc.update("""
                INSERT INTO auctions (id, auction_date, opens_at, scheduled_end_at, ends_at, status,
                                      min_bid_points, min_increment_points, carry_over_percent,
                                      anti_sniping_window_seconds, anti_sniping_extension_seconds,
                                      anti_sniping_max_extensions)
                VALUES (?, ?::date, ?::date - interval '1 day', ?::date, ?::date, 'OPEN',
                        100, 100, 50, 120, 120, 10)""",
                UUID.randomUUID(), date, date, date, date);
    }
}

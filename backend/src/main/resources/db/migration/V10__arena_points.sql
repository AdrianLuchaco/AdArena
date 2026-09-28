-- =====================================================================
-- V10: Arena Points. AdArena deja de usar dinero real.
-- =====================================================================
-- · Todos los importes pasan de céntimos de euro a PUNTOS (enteros). Los valores se conservan
--   1:1: lo que eran 7.200 céntimos (72 €) son ahora 7.200 puntos.
-- · Los puntos se ganan visitando los proyectos de la Arena y con las tareas sociales.
-- · Desaparecen las recargas con dinero (top_ups, payment_events).
-- · El libro de movimientos (ledger) se mantiene: garantiza que ningún punto aparece o
--   desaparece sin quedar apuntado.

-- ---------------------------------------------------------------------
-- 1. Céntimos → puntos
-- ---------------------------------------------------------------------
ALTER TABLE app_settings RENAME COLUMN min_bid_cents TO min_bid_points;
ALTER TABLE app_settings RENAME COLUMN min_increment_cents TO min_increment_points;

ALTER TABLE auctions RENAME COLUMN min_bid_cents TO min_bid_points;
ALTER TABLE auctions RENAME COLUMN min_increment_cents TO min_increment_points;

ALTER TABLE auction_participations RENAME COLUMN total_cents TO total_points;
ALTER TABLE auction_participations RENAME COLUMN carried_in_cents TO carried_in_points;
ALTER TABLE auction_participations RENAME COLUMN forfeited_cents TO forfeited_points;
ALTER TABLE auction_participations RENAME COLUMN carried_out_cents TO carried_out_points;

ALTER TABLE bids RENAME COLUMN amount_cents TO amount_points;
ALTER TABLE bids RENAME COLUMN total_after_cents TO total_after_points;

ALTER TABLE ad_slots RENAME COLUMN amount_cents TO amount_points;

ALTER TABLE ledger_accounts RENAME COLUMN balance_cents TO balance_points;
ALTER TABLE ledger_entries RENAME COLUMN amount_cents TO amount_points;
ALTER TABLE ledger_entries RENAME COLUMN balance_after_cents TO balance_after_points;

-- La comprobación "cada movimiento suma cero" es código PL/pgSQL: hay que reescribirla con el
-- nombre de columna nuevo (los CHECK e índices ya siguen el cambio de nombre solos).
CREATE OR REPLACE FUNCTION ledger_check_transaction_balanced() RETURNS trigger
    LANGUAGE plpgsql AS
$$
DECLARE
    total bigint;
BEGIN
    SELECT COALESCE(SUM(amount_points), 0) INTO total
    FROM ledger_entries
    WHERE transaction_id = NEW.transaction_id;

    IF total <> 0 THEN
        RAISE EXCEPTION 'Ledger transaction % is unbalanced (sum = %)', NEW.transaction_id, total;
    END IF;
    RETURN NULL;
END;
$$;

DROP VIEW ledger_account_mismatches;
CREATE VIEW ledger_account_mismatches AS
SELECT a.id,
       a.type,
       a.user_id,
       a.balance_points,
       COALESCE(SUM(e.amount_points), 0) AS entries_sum_points
FROM ledger_accounts a
LEFT JOIN ledger_entries e ON e.account_id = a.id
GROUP BY a.id, a.type, a.user_id, a.balance_points
HAVING a.balance_points <> COALESCE(SUM(e.amount_points), 0);

-- ---------------------------------------------------------------------
-- 2. Cuentas del sistema con nombres de puntos
--   PAYMENTS_CLEARING → POINTS_ISSUED : de aquí salen los puntos que se regalan (saldo negativo = total repartido)
--   PLATFORM_REVENUE  → POINTS_SPENT  : aquí acaban los puntos gastados en la Arena (el ganador y el 50 % perdido)
-- ---------------------------------------------------------------------
ALTER TABLE ledger_accounts DROP CONSTRAINT ck_ledger_accounts_type;
ALTER TABLE ledger_accounts DROP CONSTRAINT ck_ledger_accounts_owner;
UPDATE ledger_accounts SET type = 'POINTS_ISSUED' WHERE type = 'PAYMENTS_CLEARING' AND user_id IS NULL;
UPDATE ledger_accounts SET type = 'POINTS_SPENT' WHERE type = 'PLATFORM_REVENUE' AND user_id IS NULL;
ALTER TABLE ledger_accounts ADD CONSTRAINT ck_ledger_accounts_type
    CHECK (type IN ('USER_AVAILABLE', 'USER_RESERVED', 'POINTS_SPENT', 'POINTS_ISSUED'));
ALTER TABLE ledger_accounts ADD CONSTRAINT ck_ledger_accounts_owner
    CHECK ((type IN ('USER_AVAILABLE', 'USER_RESERVED')) = (user_id IS NOT NULL));

-- Tipos de movimiento nuevos. TOP_UP se mantiene solo para los movimientos antiguos (el
-- historial del ledger nunca se edita).
ALTER TABLE ledger_transactions DROP CONSTRAINT ck_ledger_transactions_type;
ALTER TABLE ledger_transactions ADD CONSTRAINT ck_ledger_transactions_type
    CHECK (type IN ('TOP_UP', 'BID_RESERVE', 'BID_WIN_CHARGE', 'BID_FORFEIT', 'WINNER_REFUND',
                    'ADMIN_ADJUSTMENT', 'SIGNUP_BONUS', 'VIEW_REWARD', 'TASK_REWARD', 'TEST_GRANT'));

-- ---------------------------------------------------------------------
-- 3. Adiós a las recargas con dinero
-- ---------------------------------------------------------------------
DROP TABLE payment_events;
DROP TABLE top_ups;

DELETE FROM notifications WHERE type IN ('TOP_UP_SUCCEEDED', 'TOP_UP_REJECTED');
ALTER TABLE notifications DROP CONSTRAINT ck_notifications_type;
ALTER TABLE notifications ADD CONSTRAINT ck_notifications_type CHECK (type IN (
    'OUTBID', 'AUCTION_WON', 'AUCTION_LOST', 'AD_APPROVED', 'AD_REJECTED',
    'WINNER_REFUNDED', 'CANDIDATE_PROMOTED', 'TASK_HIDDEN'));

-- ---------------------------------------------------------------------
-- 4. Intercambio de visitas: puntos por ver los proyectos de la Arena
-- ---------------------------------------------------------------------
-- Una fila por (quien mira, proyecto, día). El proyecto se identifica por su dueño: cada
-- usuario tiene un solo anuncio. Límite: 100 puntos al día por proyecto.
CREATE TABLE project_views (
    id                  uuid         PRIMARY KEY,
    viewer_id           uuid         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    owner_id            uuid         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    view_date           date         NOT NULL,
    -- Inicio de la visita actual: el primer "tick" exige 10 s desde aquí (lo pone el servidor)
    session_started_at  timestamptz  NOT NULL,
    ticks               integer      NOT NULL DEFAULT 0,
    points_earned       integer      NOT NULL DEFAULT 0,
    bonus_awarded       boolean      NOT NULL DEFAULT false,
    last_tick_at        timestamptz,
    created_at          timestamptz  NOT NULL DEFAULT now(),
    updated_at          timestamptz  NOT NULL DEFAULT now(),
    version             bigint       NOT NULL DEFAULT 0,

    CONSTRAINT uq_project_views_day      UNIQUE (viewer_id, owner_id, view_date),
    CONSTRAINT ck_project_views_not_self CHECK (viewer_id <> owner_id),
    CONSTRAINT ck_project_views_amounts  CHECK (ticks >= 0 AND points_earned >= 0)
);

-- "¿Cuándo fue tu último tick?" (sirve para que 10 pestañas abiertas no ganen 10 veces más)
CREATE INDEX ix_project_views_viewer_last_tick ON project_views (viewer_id, last_tick_at DESC);

-- ---------------------------------------------------------------------
-- 5. Tareas sociales (Créditos extra) y promoción social
-- ---------------------------------------------------------------------
-- Un usuario publica un enlace suyo (su canal de YouTube, su perfil de X…). Los demás ganan
-- puntos por VISITARLO (nunca por dar like o seguir: lo prohíben YouTube y X).
--   ACTIVE : se muestra en Créditos extra
--   PAUSED : su dueño la ha pausado
--   HIDDEN : ocultada por el admin o automáticamente por denuncias
CREATE TABLE social_tasks (
    id              uuid           PRIMARY KEY,
    owner_id        uuid           NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    platform        varchar(20)    NOT NULL,
    title           varchar(80)    NOT NULL,
    description     varchar(200),
    url             varchar(2048)  NOT NULL,
    status          varchar(20)    NOT NULL,
    reward_points   integer        NOT NULL,
    reports         integer        NOT NULL DEFAULT 0,
    completions     integer        NOT NULL DEFAULT 0,
    hidden_reason   varchar(300),
    created_at      timestamptz    NOT NULL DEFAULT now(),
    updated_at      timestamptz    NOT NULL DEFAULT now(),
    version         bigint         NOT NULL DEFAULT 0,

    CONSTRAINT ck_social_tasks_platform CHECK (platform IN ('YOUTUBE', 'X', 'INSTAGRAM', 'TIKTOK', 'TWITCH',
                                                          'LINKEDIN', 'FACEBOOK', 'GITHUB', 'WEB')),
    CONSTRAINT ck_social_tasks_status   CHECK (status IN ('ACTIVE', 'PAUSED', 'HIDDEN')),
    CONSTRAINT ck_social_tasks_url      CHECK (url LIKE 'https://%'),
    CONSTRAINT ck_social_tasks_reward   CHECK (reward_points > 0),
    CONSTRAINT ck_social_tasks_counts   CHECK (reports >= 0 AND completions >= 0),
    CONSTRAINT ck_social_tasks_hidden   CHECK (status <> 'HIDDEN' OR hidden_reason IS NOT NULL)
);

CREATE INDEX ix_social_tasks_owner  ON social_tasks (owner_id, created_at DESC);
CREATE INDEX ix_social_tasks_active ON social_tasks (completions) WHERE status = 'ACTIVE';

-- Cada tarea se puede hacer una vez al día por usuario. started_at lo pone el servidor al abrir
-- el enlace; la recompensa solo se da si han pasado los segundos mínimos desde entonces.
CREATE TABLE social_task_completions (
    id             uuid         PRIMARY KEY,
    task_id        uuid         NOT NULL REFERENCES social_tasks (id) ON DELETE CASCADE,
    user_id        uuid         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    task_date      date         NOT NULL,
    started_at     timestamptz  NOT NULL,
    completed_at   timestamptz,
    points_awarded integer,

    CONSTRAINT uq_social_task_completions_day UNIQUE (task_id, user_id, task_date),
    CONSTRAINT ck_social_task_completions_done CHECK ((completed_at IS NULL) = (points_awarded IS NULL))
);

CREATE INDEX ix_social_task_completions_user_day ON social_task_completions (user_id, task_date);

-- Denuncias: una por usuario y tarea. Con varias, la tarea se oculta sola hasta que la revises.
CREATE TABLE social_task_reports (
    id          uuid          PRIMARY KEY,
    task_id     uuid          NOT NULL REFERENCES social_tasks (id) ON DELETE CASCADE,
    user_id     uuid          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    reason      varchar(300)  NOT NULL,
    created_at  timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT uq_social_task_reports UNIQUE (task_id, user_id)
);

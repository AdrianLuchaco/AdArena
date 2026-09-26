-- =====================================================================
-- V5: Monedero y libro de movimientos (ledger) de partida doble
-- =====================================================================
-- Cada movimiento de dinero es una "transacción" con 2 o más "apuntes" (entries)
-- cuyos importes SUMAN CERO. El dinero nunca aparece ni desaparece: solo se mueve
-- entre cuentas. Convención de signos: + entra en la cuenta, - sale.
--
-- Cuentas:
--   USER_AVAILABLE    saldo libre del usuario (puede pujar con él)       >= 0
--   USER_RESERVED     saldo comprometido en pujas o como ganador         >= 0
--   PLATFORM_REVENUE  ingresos de la plataforma (tuyos)
--   STRIPE_CLEARING   contrapartida del dinero que entra desde Stripe (queda en negativo)
--
-- Invariante global: SUM(balance_cents) de todas las cuentas = 0.

CREATE TABLE ledger_accounts (
    id             uuid          PRIMARY KEY,
    user_id        uuid          REFERENCES users (id),
    type           varchar(30)   NOT NULL,
    balance_cents  bigint        NOT NULL DEFAULT 0,
    created_at     timestamptz   NOT NULL DEFAULT now(),
    version        bigint        NOT NULL DEFAULT 0,

    CONSTRAINT ck_ledger_accounts_type
        CHECK (type IN ('USER_AVAILABLE', 'USER_RESERVED', 'PLATFORM_REVENUE', 'STRIPE_CLEARING')),
    -- Las cuentas USER_* pertenecen a un usuario; las de sistema, a nadie.
    CONSTRAINT ck_ledger_accounts_owner
        CHECK ((type IN ('USER_AVAILABLE', 'USER_RESERVED')) = (user_id IS NOT NULL)),
    -- Un usuario nunca puede quedar en negativo (ni por un bug de concurrencia).
    CONSTRAINT ck_ledger_accounts_user_non_negative
        CHECK (user_id IS NULL OR balance_cents >= 0)
);

CREATE UNIQUE INDEX ux_ledger_accounts_user_type   ON ledger_accounts (user_id, type) WHERE user_id IS NOT NULL;
CREATE UNIQUE INDEX ux_ledger_accounts_system_type ON ledger_accounts (type)          WHERE user_id IS NULL;

INSERT INTO ledger_accounts (id, user_id, type, balance_cents)
VALUES (gen_random_uuid(), NULL, 'PLATFORM_REVENUE', 0),
       (gen_random_uuid(), NULL, 'STRIPE_CLEARING', 0);

-- Tipos de transacción:
--   TOP_UP          recarga con Stripe            STRIPE_CLEARING -> USER_AVAILABLE
--   BID_RESERVE     puja                          USER_AVAILABLE  -> USER_RESERVED
--   BID_WIN_CHARGE  ganador aprobado              USER_RESERVED   -> PLATFORM_REVENUE
--   BID_FORFEIT     parte perdida al no ganar     USER_RESERVED   -> PLATFORM_REVENUE
--   WINNER_REFUND   ganador rechazado / expirado  USER_RESERVED (+ PLATFORM_REVENUE) -> USER_AVAILABLE
--   ADMIN_ADJUSTMENT corrección manual auditada
CREATE TABLE ledger_transactions (
    id               uuid          PRIMARY KEY,
    type             varchar(30)   NOT NULL,
    -- Hace idempotente cualquier operación de dinero: repetirla choca con este UNIQUE.
    -- Ej.: 'topup:<stripe_session_id>', 'forfeit:<participation_id>'
    idempotency_key  varchar(150)  NOT NULL,
    reference_type   varchar(40),
    reference_id     uuid,
    description      varchar(255),
    created_at       timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT uq_ledger_transactions_idempotency UNIQUE (idempotency_key),
    CONSTRAINT ck_ledger_transactions_type
        CHECK (type IN ('TOP_UP', 'BID_RESERVE', 'BID_WIN_CHARGE', 'BID_FORFEIT',
                        'WINNER_REFUND', 'ADMIN_ADJUSTMENT'))
);

CREATE INDEX ix_ledger_transactions_reference ON ledger_transactions (reference_type, reference_id);

CREATE TABLE ledger_entries (
    id                   uuid         PRIMARY KEY,
    transaction_id       uuid         NOT NULL REFERENCES ledger_transactions (id),
    account_id           uuid         NOT NULL REFERENCES ledger_accounts (id),
    amount_cents         bigint       NOT NULL,
    balance_after_cents  bigint       NOT NULL,
    created_at           timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT ck_ledger_entries_non_zero CHECK (amount_cents <> 0)
);

CREATE INDEX ix_ledger_entries_transaction ON ledger_entries (transaction_id);
CREATE INDEX ix_ledger_entries_account     ON ledger_entries (account_id, created_at DESC);

-- ---------------------------------------------------------------------
-- Red de seguridad 1: toda transacción debe cuadrar (suma = 0).
-- Es un trigger DIFERIDO: se comprueba al hacer COMMIT, cuando ya están
-- insertados todos los apuntes de la transacción.
-- ---------------------------------------------------------------------
CREATE FUNCTION ledger_check_transaction_balanced() RETURNS trigger
    LANGUAGE plpgsql AS
$$
DECLARE
    total bigint;
BEGIN
    SELECT COALESCE(SUM(amount_cents), 0) INTO total
    FROM ledger_entries
    WHERE transaction_id = NEW.transaction_id;

    IF total <> 0 THEN
        RAISE EXCEPTION 'Ledger transaction % is unbalanced (sum = %)', NEW.transaction_id, total;
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER trg_ledger_entries_balanced
    AFTER INSERT ON ledger_entries
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION ledger_check_transaction_balanced();

-- ---------------------------------------------------------------------
-- Red de seguridad 2: el ledger es de solo inserción. Los errores se
-- corrigen con una transacción nueva (ADMIN_ADJUSTMENT), nunca editando.
-- ---------------------------------------------------------------------
CREATE FUNCTION ledger_forbid_mutation() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'Ledger table % is append-only', TG_TABLE_NAME;
END;
$$;

CREATE TRIGGER trg_ledger_entries_immutable
    BEFORE UPDATE OR DELETE ON ledger_entries
    FOR EACH ROW EXECUTE FUNCTION ledger_forbid_mutation();

CREATE TRIGGER trg_ledger_transactions_immutable
    BEFORE UPDATE OR DELETE ON ledger_transactions
    FOR EACH ROW EXECUTE FUNCTION ledger_forbid_mutation();

-- ---------------------------------------------------------------------
-- Conciliación: cuentas cuyo saldo cacheado no coincide con la suma de
-- sus apuntes. Debe estar SIEMPRE vacía (lo comprueban los tests y el panel admin).
-- ---------------------------------------------------------------------
CREATE VIEW ledger_account_mismatches AS
SELECT a.id,
       a.type,
       a.user_id,
       a.balance_cents,
       COALESCE(SUM(e.amount_cents), 0) AS entries_sum_cents
FROM ledger_accounts a
LEFT JOIN ledger_entries e ON e.account_id = a.id
GROUP BY a.id, a.type, a.user_id, a.balance_cents
HAVING a.balance_cents <> COALESCE(SUM(e.amount_cents), 0);

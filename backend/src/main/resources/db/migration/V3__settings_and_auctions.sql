-- =====================================================================
-- V3: Configuración, subastas, participaciones y pujas
-- =====================================================================

-- Configuración global editable desde el panel de admin (una sola fila, id = 1).
-- Los cambios se aplican a partir de la SIGUIENTE subasta: cada subasta guarda
-- una copia de las reglas con las que se abrió (nadie cambia las reglas a mitad de partido).
CREATE TABLE app_settings (
    id                              integer      PRIMARY KEY,
    min_bid_cents                   bigint       NOT NULL,
    min_increment_cents             bigint       NOT NULL,
    carry_over_percent              integer      NOT NULL,
    close_time                      time         NOT NULL,
    time_zone                       varchar(50)  NOT NULL,
    anti_sniping_window_seconds     integer      NOT NULL,
    anti_sniping_extension_seconds  integer      NOT NULL,
    anti_sniping_max_extensions     integer      NOT NULL,
    updated_at                      timestamptz  NOT NULL DEFAULT now(),
    updated_by                      uuid         REFERENCES users (id),
    version                         bigint       NOT NULL DEFAULT 0,

    CONSTRAINT ck_app_settings_singleton  CHECK (id = 1),
    CONSTRAINT ck_app_settings_min_bid    CHECK (min_bid_cents > 0),
    CONSTRAINT ck_app_settings_increment  CHECK (min_increment_cents > 0),
    CONSTRAINT ck_app_settings_carry      CHECK (carry_over_percent BETWEEN 0 AND 100),
    CONSTRAINT ck_app_settings_sniping    CHECK (anti_sniping_window_seconds >= 0
                                             AND anti_sniping_extension_seconds >= 0
                                             AND anti_sniping_max_extensions >= 0)
);

-- Valores por defecto: puja mínima 1 €, incremento mínimo 1 €, arrastre 50 %,
-- cierre a medianoche de Madrid, anti-sniping de 2 min con un máximo de 10 extensiones.
INSERT INTO app_settings (id, min_bid_cents, min_increment_cents, carry_over_percent,
                          close_time, time_zone, anti_sniping_window_seconds,
                          anti_sniping_extension_seconds, anti_sniping_max_extensions)
VALUES (1, 100, 100, 50, '00:00', 'Europe/Madrid', 120, 120, 10);

-- Una subasta por día. auction_date = fecha local (Europe/Madrid) en la que
-- está previsto que cierre. El ganador se anuncia desde scheduled_end_at hasta
-- el scheduled_end_at de la subasta siguiente (24 h, o 23/25 h en cambio de hora).
CREATE TABLE auctions (
    id                              uuid         PRIMARY KEY,
    auction_date                    date         NOT NULL,
    opens_at                        timestamptz  NOT NULL,
    scheduled_end_at                timestamptz  NOT NULL,
    -- Fin real (se mueve con el anti-sniping). El cierre automático mira esta columna.
    ends_at                         timestamptz  NOT NULL,
    extensions_count                integer      NOT NULL DEFAULT 0,
    status                          varchar(20)  NOT NULL,
    result                          varchar(20),
    closed_at                       timestamptz,
    -- Copia de las reglas vigentes al abrir la subasta
    min_bid_cents                   bigint       NOT NULL,
    min_increment_cents             bigint       NOT NULL,
    carry_over_percent              integer      NOT NULL,
    anti_sniping_window_seconds     integer      NOT NULL,
    anti_sniping_extension_seconds  integer      NOT NULL,
    anti_sniping_max_extensions     integer      NOT NULL,
    created_at                      timestamptz  NOT NULL DEFAULT now(),
    version                         bigint       NOT NULL DEFAULT 0,

    CONSTRAINT uq_auctions_date      UNIQUE (auction_date),
    CONSTRAINT ck_auctions_status    CHECK (status IN ('OPEN', 'CLOSED')),
    CONSTRAINT ck_auctions_result    CHECK (result IS NULL OR result IN ('NO_BIDS', 'HAS_WINNER')),
    -- Una subasta cerrada siempre tiene resultado y hora de cierre; una abierta, nunca.
    CONSTRAINT ck_auctions_closed    CHECK ((status = 'CLOSED') = (closed_at IS NOT NULL AND result IS NOT NULL)),
    CONSTRAINT ck_auctions_times     CHECK (scheduled_end_at > opens_at AND ends_at >= scheduled_end_at),
    CONSTRAINT ck_auctions_extension CHECK (extensions_count >= 0 AND extensions_count <= anti_sniping_max_extensions),
    CONSTRAINT ck_auctions_rules     CHECK (min_bid_cents > 0 AND min_increment_cents > 0
                                         AND carry_over_percent BETWEEN 0 AND 100)
);

-- Garantía a nivel de BD: nunca puede haber dos subastas abiertas a la vez.
CREATE UNIQUE INDEX ux_auctions_single_open ON auctions (status) WHERE status = 'OPEN';

-- Participación = el TOTAL acumulado de un usuario en una subasta (regla 2).
-- Es la fila que se bloquea (SELECT ... FOR UPDATE) y la que ordena el ranking.
CREATE TABLE auction_participations (
    id                              uuid           PRIMARY KEY,
    auction_id                      uuid           NOT NULL REFERENCES auctions (id),
    user_id                         uuid           NOT NULL REFERENCES users (id),
    total_cents                     bigint         NOT NULL,
    -- Parte del total que viene arrastrada del día anterior (regla 7)
    carried_in_cents                bigint         NOT NULL DEFAULT 0,
    carried_from_participation_id   uuid           REFERENCES auction_participations (id),
    -- Desempate: gana quien alcanzó antes su total = menor secuencia de su última puja
    last_bid_seq                    bigint         NOT NULL,
    last_bid_at                     timestamptz    NOT NULL,
    outcome                         varchar(20)    NOT NULL DEFAULT 'ACTIVE',
    -- Rellenado en el cierre
    final_rank                      integer,
    forfeited_cents                 bigint,
    carried_out_cents               bigint,
    -- Foto del anuncio en el momento del cierre (regla 3: editable hasta que termina)
    ad_company_name                 varchar(80),
    ad_website_url                  varchar(2048),
    ad_description                  varchar(300),
    ad_image_id                     uuid           REFERENCES images (id),
    created_at                      timestamptz    NOT NULL DEFAULT now(),
    updated_at                      timestamptz    NOT NULL DEFAULT now(),
    version                         bigint         NOT NULL DEFAULT 0,

    CONSTRAINT uq_participations_auction_user UNIQUE (auction_id, user_id),
    CONSTRAINT ck_participations_amounts      CHECK (total_cents >= 0
                                                 AND carried_in_cents >= 0
                                                 AND (forfeited_cents IS NULL OR forfeited_cents >= 0)
                                                 AND (carried_out_cents IS NULL OR carried_out_cents >= 0)),
    CONSTRAINT ck_participations_outcome      CHECK (outcome IN ('ACTIVE', 'WON', 'LOST', 'REFUNDED'))
);

-- Índice que sirve exactamente la consulta del ranking
CREATE INDEX ix_participations_ranking
    ON auction_participations (auction_id, total_cents DESC, last_bid_seq ASC);
CREATE INDEX ix_participations_user ON auction_participations (user_id, created_at DESC);

-- Secuencia global de pujas (orden estricto para desempates, más fiable que la hora)
CREATE SEQUENCE bid_seq;

-- Cada aportación individual (historial). La suma de las pujas de una participación
-- es igual a su total_cents. Es append-only.
--   BID            -> el usuario añade dinero (> 0)
--   CARRY_OVER     -> arrastre automático desde el día anterior (> 0)
--   CARRY_REVERSAL -> se retira un arrastre porque su dueño pasa a ser ganador
--                     del día anterior tras rechazarse al primero (< 0)
CREATE TABLE bids (
    id                 uuid          PRIMARY KEY,
    seq                bigint        NOT NULL,
    auction_id         uuid          NOT NULL REFERENCES auctions (id),
    participation_id   uuid          NOT NULL REFERENCES auction_participations (id),
    user_id            uuid          NOT NULL REFERENCES users (id),
    type               varchar(20)   NOT NULL,
    amount_cents       bigint        NOT NULL,
    total_after_cents  bigint        NOT NULL,
    -- Clave que envía el cliente para que un doble clic o un reintento no puje dos veces
    idempotency_key    varchar(100),
    created_at         timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT uq_bids_seq             UNIQUE (seq),
    CONSTRAINT uq_bids_idempotency     UNIQUE (user_id, idempotency_key),
    CONSTRAINT ck_bids_type            CHECK (type IN ('BID', 'CARRY_OVER', 'CARRY_REVERSAL')),
    CONSTRAINT ck_bids_amount_sign     CHECK ((type IN ('BID', 'CARRY_OVER') AND amount_cents > 0)
                                           OR (type = 'CARRY_REVERSAL' AND amount_cents < 0)),
    CONSTRAINT ck_bids_total_after     CHECK (total_after_cents >= 0)
);

CREATE INDEX ix_bids_participation ON bids (participation_id, seq);
CREATE INDEX ix_bids_user          ON bids (user_id, created_at DESC);

-- =====================================================================
-- V11 · Webs de los proyectos, presentación del ganador y premio al ganador
--
-- 1. site_previews: lo que AdArena ha leído de la web de cada proyecto o promoción (título,
--    descripción, logo, imágenes, frases destacadas) y si se puede mostrar dentro de AdArena
--    (iframe). Se usa en el visor para ganar puntos y en la presentación animada del ganador.
-- 2. ad_slots.showcase: la presentación del ganador "congelada" en el momento de aprobarla. En la
--    portada se ve exactamente lo que revisó el administrador, aunque la web cambie después.
-- 3. WINNER_BONUS: el ganador recibe puntos de regalo para volver a pujar.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. Lo que sabemos de cada web
-- ---------------------------------------------------------------------
CREATE TABLE site_previews (
    id               uuid          PRIMARY KEY,
    -- La dirección tal y como la guardó el usuario (ya normalizada, siempre https://)
    url              varchar(2048) NOT NULL,
    -- Quién la pidió: las imágenes descargadas quedan a su nombre
    owner_id         uuid          NOT NULL REFERENCES users (id),
    -- WEB: leída de internet. DEMO: datos de ejemplo (solo en local, nunca se vuelven a leer)
    source           varchar(10)   NOT NULL DEFAULT 'WEB',
    status           varchar(10)   NOT NULL,
    final_url        varchar(2048),
    -- ¿Se puede mostrar dentro de AdArena (iframe)? Si no, se abre en una ventana aparte
    frameable        boolean       NOT NULL DEFAULT false,
    -- Dirección para el iframe cuando no es la misma web (p. ej. el reproductor de un vídeo de YouTube)
    embed_url        varchar(2048),
    site_name        varchar(120),
    title            varchar(200),
    description      varchar(500),
    theme_color      varchar(7),
    icon_image_id    uuid          REFERENCES images (id),
    hero_image_id    uuid          REFERENCES images (id),
    -- [{"src": "https://…/foto.jpg", "imageId": "…"}]: fotos de la galería y de dónde salió cada una
    -- (si la web no cambia la foto, no se vuelve a descargar)
    gallery          jsonb         NOT NULL DEFAULT '[]',
    -- ["Tostamos cada semana", "Brunch los domingos", …]: frases destacadas de la web
    highlights       jsonb         NOT NULL DEFAULT '[]',
    -- De dónde salieron el logo y la foto principal (para no descargarlas otra vez)
    icon_src         varchar(2048),
    hero_src         varchar(2048),
    error            varchar(300),
    fetched_at       timestamptz,
    created_at       timestamptz   NOT NULL DEFAULT now(),
    updated_at       timestamptz   NOT NULL DEFAULT now(),
    version          bigint        NOT NULL DEFAULT 0,

    CONSTRAINT uq_site_previews_url    UNIQUE (url),
    CONSTRAINT ck_site_previews_url    CHECK (url LIKE 'https://%'),
    CONSTRAINT ck_site_previews_source CHECK (source IN ('WEB', 'DEMO')),
    CONSTRAINT ck_site_previews_status CHECK (status IN ('READY', 'FAILED')),
    CONSTRAINT ck_site_previews_color  CHECK (theme_color IS NULL OR theme_color ~ '^#[0-9a-f]{6}$'),
    CONSTRAINT ck_site_previews_embed  CHECK (embed_url IS NULL OR embed_url LIKE 'https://%'),
    CONSTRAINT ck_site_previews_final  CHECK (final_url IS NULL OR final_url LIKE 'https://%')
);

CREATE INDEX ix_site_previews_fetched ON site_previews (fetched_at);

-- ---------------------------------------------------------------------
-- 2. Presentación del ganador, tal y como se aprobó
-- ---------------------------------------------------------------------
ALTER TABLE ad_slots ADD COLUMN showcase jsonb;

-- ---------------------------------------------------------------------
-- 3. Premio al ganador
-- ---------------------------------------------------------------------
ALTER TABLE ledger_transactions DROP CONSTRAINT ck_ledger_transactions_type;
ALTER TABLE ledger_transactions ADD CONSTRAINT ck_ledger_transactions_type
    CHECK (type IN ('TOP_UP', 'BID_RESERVE', 'BID_WIN_CHARGE', 'BID_FORFEIT', 'WINNER_REFUND',
                    'ADMIN_ADJUSTMENT', 'SIGNUP_BONUS', 'VIEW_REWARD', 'TASK_REWARD', 'TEST_GRANT',
                    'WINNER_BONUS'));

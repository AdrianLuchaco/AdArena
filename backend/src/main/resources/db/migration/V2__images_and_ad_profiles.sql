-- =====================================================================
-- V2: Imágenes y perfiles de anuncio
-- =====================================================================

-- Las imágenes se guardan ya re-codificadas por el servidor (PNG/JPEG limpios,
-- sin metadatos), así que nunca servimos el archivo original que subió el usuario.
-- Son inmutables: cambiar el logo crea una fila nueva. Así los anuncios ganadores
-- de días anteriores siguen apuntando a la imagen que tenían en su momento.
CREATE TABLE images (
    id            uuid          PRIMARY KEY,
    owner_id      uuid          NOT NULL REFERENCES users (id),
    content_type  varchar(30)   NOT NULL,
    data          bytea         NOT NULL,
    size_bytes    integer       NOT NULL,
    width         integer       NOT NULL,
    height        integer       NOT NULL,
    sha256        varchar(64)   NOT NULL,
    created_at    timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT ck_images_content_type CHECK (content_type IN ('image/png', 'image/jpeg')),
    CONSTRAINT ck_images_size         CHECK (size_bytes > 0 AND size_bytes <= 2097152),
    CONSTRAINT ck_images_dimensions   CHECK (width > 0 AND height > 0)
);

CREATE INDEX ix_images_owner ON images (owner_id);

-- Un perfil de anuncio por usuario. Es obligatorio para poder pujar.
CREATE TABLE ad_profiles (
    id            uuid           PRIMARY KEY,
    user_id       uuid           NOT NULL REFERENCES users (id),
    company_name  varchar(80)    NOT NULL,
    website_url   varchar(2048)  NOT NULL,
    description   varchar(300)   NOT NULL,
    image_id      uuid           NOT NULL REFERENCES images (id),
    created_at    timestamptz    NOT NULL DEFAULT now(),
    updated_at    timestamptz    NOT NULL DEFAULT now(),
    version       bigint         NOT NULL DEFAULT 0,

    CONSTRAINT uq_ad_profiles_user            UNIQUE (user_id),
    CONSTRAINT ck_ad_profiles_url_https       CHECK (website_url LIKE 'https://%'),
    CONSTRAINT ck_ad_profiles_company_present CHECK (btrim(company_name) <> ''),
    CONSTRAINT ck_ad_profiles_desc_present    CHECK (btrim(description) <> '')
);

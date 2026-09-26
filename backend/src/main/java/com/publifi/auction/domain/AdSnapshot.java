package com.publifi.auction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.UUID;

/**
 * Copia del perfil de anuncio en el momento del cierre. Es lo que se publica si esa
 * participación acaba ganando, aunque el usuario edite su perfil después.
 */
@Embeddable
public record AdSnapshot(
        @Column(name = "ad_company_name") String companyName,
        @Column(name = "ad_website_url") String websiteUrl,
        @Column(name = "ad_description") String description,
        @Column(name = "ad_image_id") UUID imageId
) {
}

package com.adarena.auction.repository;

import java.util.UUID;

/** Una fila del ranking público: el proyecto (perfil actual de su anuncio) y su total. */
public record RankingRow(
        UUID participationId,
        String companyName,
        String description,
        String websiteUrl,
        UUID imageId,
        long totalPoints,
        long carriedInPoints
) {
}

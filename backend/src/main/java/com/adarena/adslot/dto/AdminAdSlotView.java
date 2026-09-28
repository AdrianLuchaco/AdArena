package com.adarena.adslot.dto;

import com.adarena.adslot.domain.AdSlot;
import com.adarena.adslot.domain.AdSlotStatus;
import com.adarena.auction.domain.AdSnapshot;
import com.adarena.image.service.ImageService;
import com.adarena.site.dto.SiteDtos.Showcase;
import com.adarena.user.domain.User;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Un anuncio ganador visto desde la moderación: el anuncio tal y como quedó al cierre, quién es,
 * cuánto pujó y en qué ventana se emitiría.
 *
 * @param candidateRank 1 si ganó directamente; 2, 3… si llegó porque se rechazó a los anteriores
 * @param showcase      su presentación animada: la congelada si ya se aprobó; si está pendiente, la
 *                      que se congelaría al aprobarlo ahora (null si su web aún no se ha podido leer)
 */
public record AdminAdSlotView(
        UUID id,
        AdSlotStatus status,
        int candidateRank,
        long amountPoints,
        LocalDate roundDate,
        Instant startsAt,
        Instant endsAt,
        String userEmail,
        String userName,
        Ad ad,
        Instant reviewedAt,
        String rejectionReason,
        Instant createdAt,
        Showcase showcase
) {

    public record Ad(String companyName, String description, String websiteUrl, String imageUrl) {
    }

    public static AdminAdSlotView from(AdSlot slot, LocalDate roundDate, AdSnapshot snapshot, User user,
                                       Showcase showcase) {
        Ad ad = snapshot == null ? null : new Ad(snapshot.companyName(), snapshot.description(), snapshot.websiteUrl(),
                ImageService.publicUrl(snapshot.imageId()));
        return new AdminAdSlotView(slot.getId(), slot.getStatus(), slot.getCandidateRank(), slot.getAmountPoints(),
                roundDate, slot.getStartsAt(), slot.getEndsAt(), user == null ? null : user.getEmail(),
                user == null ? null : user.getDisplayName(), ad, slot.getReviewedAt(), slot.getRejectionReason(),
                slot.getCreatedAt(), showcase);
    }
}

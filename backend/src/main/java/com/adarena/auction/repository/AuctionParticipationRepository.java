package com.adarena.auction.repository;

import com.adarena.auction.domain.AuctionParticipation;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuctionParticipationRepository extends JpaRepository<AuctionParticipation, UUID> {

    /**
     * Ranking de una ronda: mayor total primero y, a igualdad, quien llegó antes a ese total.
     * Usa el índice ix_participations_ranking.
     */
    @Query("""
            SELECT new com.adarena.auction.repository.RankingRow(
                p.id, profile.companyName, profile.description, profile.websiteUrl, profile.imageId,
                p.totalPoints, p.carriedInPoints)
            FROM AuctionParticipation p
            JOIN AdProfile profile ON profile.userId = p.userId
            WHERE p.auction.id = :auctionId AND p.totalPoints > 0
            ORDER BY p.totalPoints DESC, p.lastBidSeq ASC
            """)
    List<RankingRow> findRanking(@Param("auctionId") UUID auctionId, Limit limit);

    @Query("SELECT count(p) FROM AuctionParticipation p WHERE p.auction.id = :auctionId AND p.totalPoints > 0")
    int countActiveBidders(@Param("auctionId") UUID auctionId);

    /** Dinero en juego en una ronda (suma de todos los totales). */
    @Query("SELECT COALESCE(SUM(p.totalPoints), 0) FROM AuctionParticipation p WHERE p.auction.id = :auctionId")
    long sumTotals(@Param("auctionId") UUID auctionId);

    Optional<AuctionParticipation> findByAuctionIdAndUserId(UUID auctionId, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM AuctionParticipation p WHERE p.auction.id = :auctionId AND p.userId = :userId")
    Optional<AuctionParticipation> findForUpdate(@Param("auctionId") UUID auctionId, @Param("userId") UUID userId);

    /** Cuántos van por delante de un total (desempate: quien llegó antes). Posición = esto + 1. */
    @Query("""
            SELECT count(p) FROM AuctionParticipation p
            WHERE p.auction.id = :auctionId
              AND (p.totalPoints > :totalPoints OR (p.totalPoints = :totalPoints AND p.lastBidSeq < :lastBidSeq))
            """)
    int countAhead(@Param("auctionId") UUID auctionId, @Param("totalPoints") long totalPoints,
                   @Param("lastBidSeq") long lastBidSeq);

    /** Participantes con un total en [min, max): candidatos a haber sido superados por una puja. */
    @Query("""
            SELECT p FROM AuctionParticipation p
            WHERE p.auction.id = :auctionId AND p.userId <> :userId
              AND p.totalPoints >= :minPoints AND p.totalPoints < :maxPoints
            """)
    List<AuctionParticipation> findInTotalRange(@Param("auctionId") UUID auctionId, @Param("userId") UUID userId,
                                                @Param("minPoints") long minPoints, @Param("maxPoints") long maxPoints);

    /**
     * TODAS las participaciones de una ronda en orden de clasificación (también las que se han
     * quedado en 0 puntos). Las usa el cierre, con la ronda ya bloqueada.
     */
    @Query("""
            SELECT p FROM AuctionParticipation p
            WHERE p.auction.id = :auctionId
            ORDER BY p.totalPoints DESC, p.lastBidSeq ASC
            """)
    List<AuctionParticipation> findAllRanked(@Param("auctionId") UUID auctionId);

    /** La participación que recibió el arrastre de otra (la del día siguiente). */
    Optional<AuctionParticipation> findByCarriedFromParticipationId(UUID carriedFromParticipationId);

    /**
     * Siguientes clasificados de una ronda cerrada tras el puesto {@code afterRank} que no ganaron:
     * candidatos a ocupar la portada si se rechaza al ganador.
     */
    @Query("""
            SELECT p FROM AuctionParticipation p
            WHERE p.auction.id = :auctionId
              AND p.outcome = com.adarena.auction.domain.ParticipationOutcome.LOST
              AND p.finalRank > :afterRank
            ORDER BY p.finalRank ASC
            """)
    List<AuctionParticipation> findRunnersUp(@Param("auctionId") UUID auctionId, @Param("afterRank") int afterRank,
                                             Limit limit);

    /** Participantes clasificados de varias rondas cerradas (para el historial). */
    @Query("""
            SELECT p FROM AuctionParticipation p
            WHERE p.auction.id IN :auctionIds AND p.finalRank IS NOT NULL
            ORDER BY p.finalRank ASC
            """)
    List<AuctionParticipation> findRankedIn(@Param("auctionIds") Collection<UUID> auctionIds);
}

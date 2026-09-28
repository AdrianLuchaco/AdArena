package com.adarena.adslot.repository;

import com.adarena.adslot.domain.AdSlot;
import com.adarena.adslot.domain.AdSlotStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdSlotRepository extends JpaRepository<AdSlot, UUID> {

    /** Anuncio aprobado cuya ventana incluye este instante (el que se ve en la portada). */
    @Query("""
            SELECT s FROM AdSlot s
            WHERE s.status = com.adarena.adslot.domain.AdSlotStatus.APPROVED
              AND s.startsAt <= :now AND s.endsAt > :now
            ORDER BY s.startsAt DESC
            """)
    List<AdSlot> findLive(@Param("now") Instant now, Limit limit);

    boolean existsByAuctionIdAndStatus(UUID auctionId, AdSlotStatus status);

    List<AdSlot> findByAuctionIdIn(Collection<UUID> auctionIds);

    /** El hueco BLOQUEADO (SELECT ... FOR UPDATE): aprobar, rechazar y caducar pasan por aquí. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM AdSlot s WHERE s.id = :id")
    Optional<AdSlot> findForUpdate(@Param("id") UUID id);

    /** Candidatos pendientes de moderar, del más antiguo al más reciente. */
    List<AdSlot> findByStatusOrderByCreatedAtAsc(AdSlotStatus status);

    /** Últimas decisiones (aprobados, rechazados, caducados) para el panel de moderación. */
    List<AdSlot> findByStatusNotOrderByUpdatedAtDesc(AdSlotStatus status, Limit limit);

    long countByStatus(AdSlotStatus status);

    /** Pendientes cuya ventana de emisión ya terminó sin moderación: hay que devolver los puntos. */
    @Query("""
            SELECT s.id FROM AdSlot s
            WHERE s.status = com.adarena.adslot.domain.AdSlotStatus.PENDING_REVIEW AND s.endsAt <= :now
            """)
    List<UUID> findOverduePendingIds(@Param("now") Instant now);
}

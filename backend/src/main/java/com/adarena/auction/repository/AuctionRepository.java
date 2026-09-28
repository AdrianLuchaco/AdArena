package com.adarena.auction.repository;

import com.adarena.auction.domain.Auction;
import com.adarena.auction.domain.AuctionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface AuctionRepository extends JpaRepository<Auction, UUID> {

    Optional<Auction> findFirstByStatus(AuctionStatus status);

    /**
     * La ronda abierta, BLOQUEADA hasta el final de la transacción (SELECT ... FOR UPDATE).
     * Todas las pujas pasan por aquí: dos pujas simultáneas se ejecutan una detrás de otra.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Auction a WHERE a.status = com.adarena.auction.domain.AuctionStatus.OPEN")
    Optional<Auction> findOpenForUpdate();

    boolean existsByAuctionDate(LocalDate auctionDate);

    /** La última ronda cerrada cuyo fin programado ya pasó: la que "manda" en la portada ahora. */
    Optional<Auction> findFirstByStatusAndScheduledEndAtLessThanEqualOrderByScheduledEndAtDesc(
            AuctionStatus status, Instant now);

    /** Historial: rondas cerradas, de la más reciente a la más antigua. */
    Page<Auction> findByStatusOrderByScheduledEndAtDesc(AuctionStatus status, Pageable pageable);
}

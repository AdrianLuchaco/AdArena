package com.adarena.auction.repository;

import com.adarena.auction.domain.Bid;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BidRepository extends JpaRepository<Bid, UUID> {

    /** Siguiente número de la secuencia global de pujas (orden estricto para desempates). */
    @Query(value = "SELECT nextval('bid_seq')", nativeQuery = true)
    long nextSeq();

    /** Para la idempotencia: ¿ya procesamos una puja con esta clave? */
    Optional<Bid> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

    @Query("""
            SELECT new com.adarena.auction.repository.MyBidRow(b.createdAt, a.auctionDate, b.type, b.amountPoints, b.totalAfterPoints)
            FROM Bid b JOIN Auction a ON a.id = b.auctionId
            WHERE b.userId = :userId
            ORDER BY b.seq DESC
            """)
    List<MyBidRow> findHistory(@Param("userId") UUID userId, Limit limit);
}

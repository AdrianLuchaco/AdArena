package com.adarena.auction.repository;

import com.adarena.auction.domain.BidType;

import java.time.Instant;
import java.time.LocalDate;

/** Una línea del historial de pujas de un usuario. */
public record MyBidRow(Instant createdAt, LocalDate auctionDate, BidType type, long amountPoints, long totalAfterPoints) {
}

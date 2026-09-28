package com.adarena.auction.domain;

public enum AuctionResult {
    /** Día vacío: nadie pujó (regla 8). */
    NO_BIDS,
    /** Hay al menos un pujador; el ganador pasa a moderación. */
    HAS_WINNER
}

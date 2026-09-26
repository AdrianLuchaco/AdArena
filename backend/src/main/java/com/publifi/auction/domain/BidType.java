package com.publifi.auction.domain;

public enum BidType {
    /** El usuario añade dinero a su total. */
    BID,
    /** Arrastre automático desde la subasta anterior. */
    CARRY_OVER,
    /** Retirada de un arrastre (importe negativo). */
    CARRY_REVERSAL
}

package com.publifi.wallet.domain;

public enum LedgerTransactionType {
    /** Recarga con Stripe: STRIPE_CLEARING -> USER_AVAILABLE. */
    TOP_UP,
    /** Puja: USER_AVAILABLE -> USER_RESERVED. */
    BID_RESERVE,
    /** Ganador aprobado: USER_RESERVED -> PLATFORM_REVENUE. */
    BID_WIN_CHARGE,
    /** Parte perdida por un perdedor al cierre: USER_RESERVED -> PLATFORM_REVENUE. */
    BID_FORFEIT,
    /** Ganador rechazado o expirado: se le devuelve el 100 % a USER_AVAILABLE. */
    WINNER_REFUND,
    /** Corrección manual del admin (queda auditada). */
    ADMIN_ADJUSTMENT
}

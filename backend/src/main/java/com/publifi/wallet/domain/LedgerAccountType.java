package com.publifi.wallet.domain;

public enum LedgerAccountType {
    /** Saldo libre del usuario. Nunca negativo. */
    USER_AVAILABLE(true),
    /** Saldo comprometido en pujas o retenido como ganador pendiente. Nunca negativo. */
    USER_RESERVED(true),
    /** Ingresos de la plataforma. */
    PLATFORM_REVENUE(false),
    /** Contrapartida del dinero que entra desde Stripe (saldo negativo = total recargado). */
    STRIPE_CLEARING(false);

    private final boolean userAccount;

    LedgerAccountType(boolean userAccount) {
        this.userAccount = userAccount;
    }

    public boolean isUserAccount() {
        return userAccount;
    }
}

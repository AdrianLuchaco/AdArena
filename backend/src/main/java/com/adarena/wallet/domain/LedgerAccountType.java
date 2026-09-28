package com.adarena.wallet.domain;

public enum LedgerAccountType {
    /** Puntos libres del usuario (para pujar). Nunca negativo. */
    USER_AVAILABLE(true),
    /** Puntos comprometidos en pujas o retenidos como ganador pendiente. Nunca negativo. */
    USER_RESERVED(true),
    /** Puntos gastados en la Arena: lo que paga el ganador y el 50 % que pierden los demás. */
    POINTS_SPENT(false),
    /** Origen de los puntos regalados (bienvenida, visitas, tareas). Saldo negativo = total repartido. */
    POINTS_ISSUED(false);

    private final boolean userAccount;

    LedgerAccountType(boolean userAccount) {
        this.userAccount = userAccount;
    }

    public boolean isUserAccount() {
        return userAccount;
    }
}

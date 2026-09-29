package com.adarena.wallet.domain;

public enum LedgerTransactionType {
    /** HISTÓRICO: recarga con dinero de antes de los Crown Points. Ya no se crean. */
    TOP_UP,
    /** Puja: USER_AVAILABLE -> USER_RESERVED. */
    BID_RESERVE,
    /** Ganador aprobado: USER_RESERVED -> POINTS_SPENT. */
    BID_WIN_CHARGE,
    /** Parte perdida por quien no gana al cierre: USER_RESERVED -> POINTS_SPENT. */
    BID_FORFEIT,
    /** Ganador rechazado o no moderado a tiempo: se le devuelve el 100 % a USER_AVAILABLE. */
    WINNER_REFUND,
    /** Corrección manual del admin (queda auditada). */
    ADMIN_ADJUSTMENT,
    /** Puntos de bienvenida al crear la cuenta: POINTS_ISSUED -> USER_AVAILABLE. */
    SIGNUP_BONUS,
    /** Puntos por ver un proyecto de la Arena: POINTS_ISSUED -> USER_AVAILABLE. */
    VIEW_REWARD,
    /** Puntos por completar una tarea social: POINTS_ISSUED -> USER_AVAILABLE. */
    TASK_REWARD,
    /** SOLO EN DESARROLLO: puntos de prueba. */
    TEST_GRANT,
    /** Premio al ganador cuando su anuncio sale en portada (para que vuelva a pujar): POINTS_ISSUED -> USER_AVAILABLE. */
    WINNER_BONUS
}

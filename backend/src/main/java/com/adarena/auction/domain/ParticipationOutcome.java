package com.adarena.auction.domain;

public enum ParticipationOutcome {
    /** Subasta en curso. */
    ACTIVE,
    /** Ganador (pendiente de moderación o aprobado). */
    WON,
    /** Perdedor: perdió una parte y arrastró el resto al día siguiente. */
    LOST,
    /** Ganador rechazado o no moderado a tiempo: se le devolvió el 100 %. */
    REFUNDED
}

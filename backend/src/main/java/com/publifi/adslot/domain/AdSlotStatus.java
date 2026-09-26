package com.publifi.adslot.domain;

public enum AdSlotStatus {
    /** Dinero retenido; esperando al admin. Mientras tanto se ve el contenido base. */
    PENDING_REVIEW,
    /** Aprobado: se cobra y se muestra en su ventana. */
    APPROVED,
    /** Rechazado: se devuelve el 100 % y pasa el siguiente clasificado. */
    REJECTED,
    /** La ventana terminó sin moderación: se devuelve el 100 %. */
    EXPIRED
}

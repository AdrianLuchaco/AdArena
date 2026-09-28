package com.adarena.earn.domain;

public enum SocialTaskStatus {
    /** Se muestra en Créditos extra. */
    ACTIVE,
    /** Su dueño la ha pausado. */
    PAUSED,
    /** Ocultada por el admin o automáticamente por denuncias. Su dueño no puede reactivarla. */
    HIDDEN
}

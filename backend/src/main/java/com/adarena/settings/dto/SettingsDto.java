package com.adarena.settings.dto;

import com.adarena.settings.domain.AppSettings;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;

/**
 * Configuración de la Arena. Los cambios se aplican desde la SIGUIENTE ronda: la de hoy sigue con
 * las reglas con las que se abrió.
 */
public record SettingsDto(
        @Schema(description = "Puja mínima para entrar, en puntos", example = "100")
        @NotNull @Min(value = 1, message = "The minimum bid must be at least 1 point.")
        @Max(value = 1_000_000, message = "The minimum bid cannot exceed 1,000,000 points.")
        Long minBidPoints,

        @Schema(description = "Mínimo de cada puja adicional, en puntos", example = "100")
        @NotNull @Min(value = 1, message = "The minimum increment must be at least 1 point.")
        @Max(value = 1_000_000, message = "The minimum increment cannot exceed 1,000,000 points.")
        Long minIncrementPoints,

        @Schema(description = "Porcentaje que conservan los que no ganan", example = "50")
        @NotNull @Min(value = 0, message = "The carry-over must be between 0 and 100%.")
        @Max(value = 100, message = "The carry-over must be between 0 and 100%.")
        Integer carryOverPercent,

        @Schema(description = "Hora de cierre (HH:mm)", example = "00:00")
        @NotBlank @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "Use the HH:mm format (for example 00:00).")
        String closeTime,

        @Schema(example = "Europe/Madrid")
        @NotBlank(message = "Enter the time zone.")
        String timeZone,

        @Schema(description = "Anti-sniping: si alguien puja cuando quedan estos segundos o menos…", example = "120")
        @NotNull @Min(value = 0, message = "No puede ser negativo.") @Max(value = 3600, message = "1 hour at most.")
        Integer antiSnipingWindowSeconds,

        @Schema(description = "…el contador se alarga estos segundos", example = "120")
        @NotNull @Min(value = 0, message = "No puede ser negativo.") @Max(value = 3600, message = "1 hour at most.")
        Integer antiSnipingExtensionSeconds,

        @Schema(description = "Máximo de alargues por ronda", example = "10")
        @NotNull @Min(value = 0, message = "No puede ser negativo.") @Max(value = 100, message = "100 at most.")
        Integer antiSnipingMaxExtensions,

        @Schema(accessMode = Schema.AccessMode.READ_ONLY)
        Instant updatedAt
) {

    public static SettingsDto from(AppSettings settings) {
        return new SettingsDto(settings.getMinBidPoints(), settings.getMinIncrementPoints(), settings.getCarryOverPercent(),
                settings.getCloseTime().toString(), settings.getTimeZone(), settings.getAntiSnipingWindowSeconds(),
                settings.getAntiSnipingExtensionSeconds(), settings.getAntiSnipingMaxExtensions(), settings.getUpdatedAt());
    }
}

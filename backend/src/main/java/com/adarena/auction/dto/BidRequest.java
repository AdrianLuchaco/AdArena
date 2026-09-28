package com.adarena.auction.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record BidRequest(
        @Schema(description = "Puntos que AÑADES a tu total", example = "500")
        @NotNull(message = "Enter an amount.")
        @Positive(message = "The amount must be greater than 0.")
        Long amountPoints
) {
}

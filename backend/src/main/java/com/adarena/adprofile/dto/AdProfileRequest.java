package com.adarena.adprofile.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record AdProfileRequest(
        @Schema(example = "Café Aurora")
        @NotBlank(message = "The company name is required.")
        @Size(max = 80, message = "The company name can be at most 80 characters.")
        String companyName,

        @Schema(example = "https://www.cafeaurora.es")
        @NotBlank(message = "The website is required.")
        @Size(max = 2048, message = "The website address is too long.")
        String websiteUrl,

        @Schema(example = "Café de especialidad tostado cada semana en Madrid.")
        @NotBlank(message = "The description is required.")
        @Size(max = 300, message = "The description can be at most 300 characters.")
        String description,

        @Schema(description = "Id devuelto al subir la imagen con POST /api/images")
        @NotNull(message = "An image or logo is required.")
        UUID imageId
) {
}

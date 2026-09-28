package com.adarena.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @Schema(example = "ana@ejemplo.com")
        @NotBlank(message = "Email is required.")
        @Size(max = 254, message = "The email is too long.")
        String email,

        @Schema(example = "una-contraseña-larga")
        @NotBlank(message = "Password is required.")
        @Size(max = 200, message = "The password is too long.")
        String password
) {
}

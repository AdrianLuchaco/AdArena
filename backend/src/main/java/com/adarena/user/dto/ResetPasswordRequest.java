package com.adarena.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @Schema(description = "El token del enlace que llegó por email")
        @NotBlank(message = "The link token is missing.")
        @Size(max = 200, message = "The link is not valid.")
        String token,

        @Schema(example = "una-contraseña-nueva-y-larga")
        @NotBlank(message = "Password is required.")
        @Size(min = 10, max = 72, message = "The password must be between 10 and 72 characters.")
        String newPassword
) {
}

package com.adarena.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ForgotPasswordRequest(
        @Schema(example = "ana@ejemplo.com")
        @NotBlank(message = "Email is required.")
        @Email(message = "That email is not valid.")
        @Size(max = 254, message = "The email is too long.")
        String email
) {
}

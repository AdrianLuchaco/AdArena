package com.adarena.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @Schema(example = "ana@ejemplo.com")
        @NotBlank(message = "Email is required.")
        @Email(message = "That email is not valid.")
        @Size(max = 254, message = "The email is too long.")
        String email,

        @Schema(example = "una-contraseña-larga")
        @NotBlank(message = "Password is required.")
        @Size(min = 10, max = 72, message = "The password must be between 10 and 72 characters.")
        String password,

        @Schema(example = "Ana")
        @NotBlank(message = "Name is required.")
        @Size(min = 2, max = 80, message = "The name must be between 2 and 80 characters.")
        String displayName,

        @Schema(description = "Debe ser true: acepta Términos y Política de Privacidad", example = "true")
        @AssertTrue(message = "You must accept the Terms and the Privacy Policy.")
        boolean acceptTerms
) {
}

package com.adarena.common.error;

import org.springframework.http.HttpStatus;

import java.util.Map;

/**
 * Error de validación detectado en un servicio (no por las anotaciones del DTO), con un mensaje
 * por campo. Se responde igual que los errores de Bean Validation: 400 VALIDATION_FAILED + errors.
 */
public class FieldValidationException extends ApiException {

    private final Map<String, String> errors;

    public FieldValidationException(String field, String message) {
        super(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Some fields are not valid.");
        this.errors = Map.of(field, message);
    }

    public Map<String, String> getErrors() {
        return errors;
    }
}

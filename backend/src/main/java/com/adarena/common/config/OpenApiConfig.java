package com.adarena.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documentación OpenAPI. Con el perfil dev: http://localhost:8080/swagger-ui.html
 * El botón "Authorize" admite el access token (sin la palabra "Bearer").
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    OpenAPI adarenaOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("LaunchCrown API")
                        .version("v1")
                        .description("""
                                API de LaunchCrown: cada día, los proyectos compiten por ocupar la portada.
                                Los errores siguen el formato RFC 9457 (application/problem+json) con un
                                campo `code` estable que el frontend puede usar."""))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}

package com.adarena.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.adarena.common.config.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

/**
 * Firma y verificación de los access tokens (JWT) con HMAC-SHA256 y un secreto compartido.
 * Emite y verifica el mismo backend, así que no hace falta criptografía asimétrica.
 */
@Configuration
public class JwtConfig {

    private static final int MIN_SECRET_BYTES = 32; // 256 bits, exigido por HS256

    @Bean
    SecretKey jwtSecretKey(AppProperties properties) {
        byte[] secret;
        try {
            secret = Base64.getDecoder().decode(properties.jwt().secret());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("app.jwt.secret (JWT_SECRET) must be Base64-encoded", e);
        }
        if (secret.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("app.jwt.secret (JWT_SECRET) must decode to at least 32 bytes");
        }
        return new SecretKeySpec(secret, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSecretKey, AppProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        // Comprueba firma, caducidad (exp/nbf) y que el emisor sea el nuestro
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.jwt().issuer()));
        return decoder;
    }
}

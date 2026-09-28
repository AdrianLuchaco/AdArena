package com.adarena.user.service;

import com.adarena.common.config.AppProperties;
import com.adarena.common.error.ApiException;
import com.adarena.user.domain.RefreshToken;
import com.adarena.user.domain.Role;
import com.adarena.user.domain.User;
import com.adarena.user.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Test unitario con Mockito: el repositorio es un "doble" y controlamos el reloj. */
@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-26T10:00:00Z");
    private static final String TOKEN = "token-en-claro";

    @Mock
    RefreshTokenRepository repository;

    RefreshTokenService service;
    User user;
    UUID familyId;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties(
                List.of("http://localhost:3000"),
                null,
                new AppProperties.Jwt("secret", "adarena", Duration.ofMinutes(15), Duration.ofDays(30)),
                new AppProperties.RefreshCookie("adarena_refresh", true, "Lax", null, Duration.ofSeconds(10)),
                new AppProperties.Legal("2026-09"),
                new AppProperties.RateLimit(false, List.of(), null),
                new AppProperties.Jobs(false),
                new AppProperties.Mail("AdArena <avisos@adarena.test>", null, 465, null, null, true, null),
                new AppProperties.Rewards(200, 500, new AppProperties.Views(10, 10, 60, 40, 100),
                        new AppProperties.Tasks(20, 100, 10, 10, 5, 3)),
                new AppProperties.Previews(false, Duration.ofHours(20), Duration.ofMinutes(2), "AdArenaBot/test"),
                null);
        service = new RefreshTokenService(repository, properties, Clock.fixed(NOW, ZoneOffset.UTC));
        user = new User("ana@test.dev", "hash", "Ana", Role.USER, "2026-09", NOW);
        familyId = UUID.randomUUID();
    }

    private RefreshToken storedToken(Instant expiresAt) {
        RefreshToken token = new RefreshToken(user, RefreshTokenService.hash(TOKEN), familyId, expiresAt, "JUnit");
        when(repository.findByTokenHashForUpdate(RefreshTokenService.hash(TOKEN))).thenReturn(Optional.of(token));
        return token;
    }

    @Test
    void rotationRevokesTheOldTokenAndKeepsTheFamily() {
        RefreshToken old = storedToken(NOW.plus(Duration.ofDays(1)));
        when(repository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RefreshTokenService.Rotation rotation = service.rotate(TOKEN, "JUnit");

        assertThat(old.getRevokedAt()).isEqualTo(NOW);
        assertThat(rotation.user()).isSameAs(user);
        assertThat(rotation.refreshToken().value()).isNotEqualTo(TOKEN);
        assertThat(rotation.refreshToken().expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(30)));
    }

    @Test
    void reuseWithinTheGracePeriodIsTreatedAsARace() {
        RefreshToken token = storedToken(NOW.plus(Duration.ofDays(1)));
        token.revoke(NOW.minusSeconds(3)); // otra pestaña lo acaba de usar

        assertThatThrownBy(() -> service.rotate(TOKEN, "JUnit"))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("REFRESH_TOKEN_RACE");
        verify(repository, never()).revokeFamily(any(), any());
    }

    @Test
    void reuseAfterTheGracePeriodRevokesTheWholeFamily() {
        RefreshToken token = storedToken(NOW.plus(Duration.ofDays(1)));
        token.revoke(NOW.minusSeconds(60));

        assertThatThrownBy(() -> service.rotate(TOKEN, "JUnit"))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("REFRESH_TOKEN_REUSED");
        verify(repository).revokeFamily(familyId, NOW);
    }

    @Test
    void expiredTokensAreRejected() {
        storedToken(NOW.minusSeconds(1));

        assertThatThrownBy(() -> service.rotate(TOKEN, "JUnit"))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("REFRESH_TOKEN_EXPIRED");
    }

    @Test
    void unknownTokensAreRejected() {
        when(repository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotate("desconocido", "JUnit"))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("REFRESH_TOKEN_INVALID");
    }

    @Test
    void onlyTheHashIsStored() {
        assertThat(RefreshTokenService.hash(TOKEN)).hasSize(64).doesNotContain(TOKEN);
    }
}

package com.adarena.user.service;

import com.adarena.common.config.AppProperties;
import com.adarena.common.error.ApiException;
import com.adarena.security.TokenService;
import com.adarena.user.domain.Role;
import com.adarena.user.domain.User;
import com.adarena.user.dto.LoginRequest;
import com.adarena.user.repository.UserRepository;
import com.adarena.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String DUMMY_HASH = "{bcrypt}dummy";

    @Mock
    UserRepository userRepository;
    @Mock
    RefreshTokenService refreshTokenService;
    @Mock
    TokenService tokenService;
    @Mock
    PasswordEncoder passwordEncoder;

    AuthService authService;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode(anyString())).thenReturn(DUMMY_HASH);
        authService = new AuthService(userRepository, refreshTokenService, tokenService, passwordEncoder,
                mock(WalletService.class), mock(AppProperties.class), Clock.systemUTC());
    }

    @Test
    void unknownEmailStillChecksAPasswordToAvoidTimingLeaks() {
        when(userRepository.findByEmail("nadie@test.dev")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("Nadie@Test.dev", "x"), "JUnit"))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("INVALID_CREDENTIALS");
        verify(passwordEncoder).matches(eq("x"), eq(DUMMY_HASH));
        verifyNoInteractions(refreshTokenService, tokenService);
    }

    @Test
    void disabledAccountsCannotLogIn() {
        User user = new User("ana@test.dev", "{bcrypt}real", "Ana", Role.USER, "2026-09", Instant.now());
        user.disable();
        when(userRepository.findByEmail("ana@test.dev")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("buena", "{bcrypt}real")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("ana@test.dev", "buena"), "JUnit"))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("ACCOUNT_DISABLED");
        verify(refreshTokenService, never()).issue(any(), any(), any());
    }
}

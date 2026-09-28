package com.adarena.user.controller;

import com.adarena.common.error.ApiException;
import com.adarena.security.CurrentUser;
import com.adarena.user.dto.UserResponse;
import com.adarena.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Usuario")
@RestController
@RequestMapping("/api/me")
public class MeController {

    private final UserRepository userRepository;

    public MeController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Operation(summary = "Datos del usuario con sesión iniciada")
    @GetMapping
    @Transactional(readOnly = true)
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return userRepository.findById(CurrentUser.id(jwt))
                .map(UserResponse::from)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "That user no longer exists."));
    }
}

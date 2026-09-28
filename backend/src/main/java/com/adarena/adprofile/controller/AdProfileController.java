package com.adarena.adprofile.controller;

import com.adarena.adprofile.dto.AdProfileRequest;
import com.adarena.adprofile.dto.AdProfileResponse;
import com.adarena.adprofile.service.AdProfileService;
import com.adarena.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Perfil de anuncio")
@RestController
@RequestMapping("/api/me/ad-profile")
public class AdProfileController {

    private final AdProfileService adProfileService;

    public AdProfileController(AdProfileService adProfileService) {
        this.adProfileService = adProfileService;
    }

    @Operation(summary = "Mi perfil de anuncio", description = "404 AD_PROFILE_NOT_FOUND si aún no lo has creado.")
    @GetMapping
    public AdProfileResponse get(@AuthenticationPrincipal Jwt jwt) {
        return adProfileService.get(CurrentUser.id(jwt));
    }

    @Operation(summary = "Crear o actualizar mi perfil de anuncio")
    @PutMapping
    public AdProfileResponse save(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AdProfileRequest request) {
        return adProfileService.save(CurrentUser.id(jwt), request);
    }
}

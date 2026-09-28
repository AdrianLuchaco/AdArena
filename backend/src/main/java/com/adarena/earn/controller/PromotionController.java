package com.adarena.earn.controller;

import com.adarena.earn.dto.EarnDtos;
import com.adarena.earn.service.PromotionService;
import com.adarena.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Promocionar", description = "Tus enlaces en los Créditos extra de los demás (gratis)")
@RestController
@RequestMapping("/api/promotions")
public class PromotionController {

    private final PromotionService promotionService;

    public PromotionController(PromotionService promotionService) {
        this.promotionService = promotionService;
    }

    @Operation(summary = "Tus promociones y cuántas visitas reciben")
    @GetMapping
    public EarnDtos.PromotionsOverview mine(@AuthenticationPrincipal Jwt jwt) {
        return promotionService.mine(CurrentUser.id(jwt));
    }

    @Operation(summary = "Publicar un enlace tuyo (gratis). Se publica al momento.")
    @PostMapping
    public ResponseEntity<EarnDtos.Promotion> create(@AuthenticationPrincipal Jwt jwt,
                                                     @Valid @RequestBody EarnDtos.PromotionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(promotionService.create(CurrentUser.id(jwt), request));
    }

    @Operation(summary = "Pausar una promoción")
    @PostMapping("/{id}/pause")
    public EarnDtos.Promotion pause(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return promotionService.pause(CurrentUser.id(jwt), id);
    }

    @Operation(summary = "Reanudar una promoción pausada")
    @PostMapping("/{id}/resume")
    public EarnDtos.Promotion resume(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return promotionService.resume(CurrentUser.id(jwt), id);
    }

    @Operation(summary = "Borrar una promoción")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        promotionService.delete(CurrentUser.id(jwt), id);
        return ResponseEntity.noContent().build();
    }
}

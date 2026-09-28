package com.adarena.site.controller;

import com.adarena.security.CurrentUser;
import com.adarena.site.dto.SiteDtos.MyShowcase;
import com.adarena.site.service.MyShowcaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Mi anuncio", description = "La presentación animada que se verá en portada si ganas")
@RestController
@RequestMapping("/api/me/ad-profile/showcase")
public class MyShowcaseController {

    private final MyShowcaseService service;

    public MyShowcaseController(MyShowcaseService service) {
        this.service = service;
    }

    @Operation(summary = "Tu presentación (montada con lo que hemos leído de tu web)")
    @GetMapping
    public MyShowcase get(@AuthenticationPrincipal Jwt jwt) {
        return service.get(CurrentUser.id(jwt));
    }

    @Operation(summary = "Volver a leer tu web", description = "Como mucho una vez cada 2 minutos. Va en segundo plano: "
            + "vuelve a pedir tu presentación en unos segundos.")
    @PostMapping("/refresh")
    public MyShowcase refresh(@AuthenticationPrincipal Jwt jwt) {
        return service.refresh(CurrentUser.id(jwt));
    }
}

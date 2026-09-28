package com.adarena.earn.controller;

import com.adarena.earn.dto.EarnDtos;
import com.adarena.earn.service.ViewRewardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Portada e historial (público)")
@RestController
public class PublicProjectController {

    private final ViewRewardService viewRewardService;

    public PublicProjectController(ViewRewardService viewRewardService) {
        this.viewRewardService = viewRewardService;
    }

    @Operation(summary = "Un proyecto: su anuncio y cómo va en la Arena de hoy")
    @GetMapping("/api/public/projects/{id}")
    public ResponseEntity<EarnDtos.PublicProject> project(@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(viewRewardService.publicProject(id));
    }
}

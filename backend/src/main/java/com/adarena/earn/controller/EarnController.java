package com.adarena.earn.controller;

import com.adarena.earn.dto.EarnDtos;
import com.adarena.earn.service.SocialTaskService;
import com.adarena.earn.service.ViewRewardService;
import com.adarena.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Gana puntos", description = "Ver proyectos de la Arena y Créditos extra")
@RestController
@RequestMapping("/api/earn")
public class EarnController {

    private final ViewRewardService viewRewardService;
    private final SocialTaskService socialTaskService;

    public EarnController(ViewRewardService viewRewardService, SocialTaskService socialTaskService) {
        this.viewRewardService = viewRewardService;
        this.socialTaskService = socialTaskService;
    }

    @Operation(summary = "Resumen: tus puntos y los proyectos que puedes ver hoy")
    @GetMapping
    public EarnDtos.EarnOverview overview(@AuthenticationPrincipal Jwt jwt) {
        return viewRewardService.overview(CurrentUser.id(jwt));
    }

    @Operation(summary = "Abrir la página de un proyecto (empieza a contar el tiempo)")
    @PostMapping("/projects/{id}/start")
    public EarnDtos.ViewStatus start(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return viewRewardService.start(CurrentUser.id(jwt), id);
    }

    @Operation(summary = "Tiempo mirando la web del proyecto (tramos de 10 s)",
            description = "La web lo envía cada 10 s mientras miras la web del proyecto. Si estuvo abierta en otra "
                    + "ventana, al volver puede pedir varios tramos a la vez ({\"ticks\": 3}). El servidor nunca da "
                    + "más de los que caben en el tiempo real transcurrido (429 TICK_TOO_SOON).")
    @PostMapping("/projects/{id}/tick")
    public EarnDtos.TickResult tick(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                    @Valid @RequestBody(required = false) EarnDtos.TickRequest request) {
        return viewRewardService.tick(CurrentUser.id(jwt), id, request == null ? 1 : request.ticksOrOne());
    }

    @Operation(summary = "Créditos extra: tus tareas de hoy")
    @GetMapping("/tasks")
    public EarnDtos.TasksOverview tasks(@AuthenticationPrincipal Jwt jwt) {
        return socialTaskService.list(CurrentUser.id(jwt));
    }

    @Operation(summary = "Empezar una tarea: devuelve el enlace que hay que visitar")
    @PostMapping("/tasks/{id}/start")
    public EarnDtos.TaskStarted startTask(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return socialTaskService.start(CurrentUser.id(jwt), id);
    }

    @Operation(summary = "Reclamar los puntos de una tarea (tras visitar el enlace el tiempo mínimo)")
    @PostMapping("/tasks/{id}/claim")
    public EarnDtos.TaskClaimed claimTask(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return socialTaskService.claim(CurrentUser.id(jwt), id);
    }

    @Operation(summary = "Denunciar una tarea (enlace roto, engañoso o peligroso)")
    @PostMapping("/tasks/{id}/report")
    public ResponseEntity<Void> report(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                       @Valid @RequestBody EarnDtos.ReportRequest request) {
        socialTaskService.report(CurrentUser.id(jwt), id, request.reason());
        return ResponseEntity.noContent().build();
    }
}

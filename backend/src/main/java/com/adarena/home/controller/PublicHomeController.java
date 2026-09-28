package com.adarena.home.controller;

import com.adarena.home.dto.HistoryPage;
import com.adarena.home.dto.HomeResponse;
import com.adarena.home.service.HistoryService;
import com.adarena.home.service.HomeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@Tag(name = "Portada e historial (público)")
@RestController
public class PublicHomeController {

    private final HomeService homeService;
    private final HistoryService historyService;

    public PublicHomeController(HomeService homeService, HistoryService historyService) {
        this.homeService = homeService;
        this.historyService = historyService;
    }

    @Operation(summary = "Datos de la portada", description = "Anuncio vigente, estado del día y proyectos que compiten hoy.")
    @GetMapping("/api/public/home")
    public ResponseEntity<HomeResponse> home() {
        // Cambia con cada puja: nunca se guarda en caché
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(homeService.getHome());
    }

    @Operation(summary = "Rondas anteriores", description = "Ganadores y proyectos de cada día, del más reciente al más antiguo.")
    @GetMapping("/api/public/history")
    public ResponseEntity<HistoryPage> history(@RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "10") int size) {
        // El pasado no cambia a menudo: 1 minuto de caché
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(1)).cachePublic())
                .body(historyService.getHistory(page, size));
    }
}

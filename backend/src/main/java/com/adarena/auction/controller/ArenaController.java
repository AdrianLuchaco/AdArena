package com.adarena.auction.controller;

import com.adarena.auction.dto.BidRequest;
import com.adarena.auction.dto.BidResponse;
import com.adarena.auction.dto.MyArenaStatus;
import com.adarena.auction.repository.MyBidRow;
import com.adarena.auction.service.ArenaQueryService;
import com.adarena.auction.service.BidService;
import com.adarena.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Arena", description = "Pujar y consultar tu participación en la ronda del día")
@RestController
@RequestMapping("/api/arena")
public class ArenaController {

    private final BidService bidService;
    private final ArenaQueryService queryService;

    public ArenaController(BidService bidService, ArenaQueryService queryService) {
        this.bidService = bidService;
        this.queryService = queryService;
    }

    @Operation(summary = "Pujar (añadir a tu total)",
            description = "La cabecera Idempotency-Key (un identificador único por intento) evita pujar dos veces "
                    + "si se repite la petición por un doble clic o un fallo de red.")
    @PostMapping("/bids")
    public BidResponse bid(@AuthenticationPrincipal Jwt jwt,
                           @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                           @Valid @RequestBody BidRequest request) {
        return bidService.placeBid(CurrentUser.id(jwt), request.amountPoints(), idempotencyKey);
    }

    @Operation(summary = "Tu situación en la ronda de hoy y tus puntos")
    @GetMapping("/me")
    public MyArenaStatus me(@AuthenticationPrincipal Jwt jwt) {
        return queryService.myStatus(CurrentUser.id(jwt));
    }

    @Operation(summary = "Tu historial de pujas (las más recientes primero)")
    @GetMapping("/me/bids")
    public List<MyBidRow> myBids(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "50") int limit) {
        return queryService.myBids(CurrentUser.id(jwt), limit);
    }
}

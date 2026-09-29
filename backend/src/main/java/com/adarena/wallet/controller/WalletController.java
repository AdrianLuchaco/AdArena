package com.adarena.wallet.controller;

import com.adarena.security.CurrentUser;
import com.adarena.wallet.repository.LedgerEntryRepository;
import com.adarena.wallet.repository.MovementRow;
import com.adarena.wallet.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Limit;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Crown Points")
@RestController
public class WalletController {

    private static final int MOVEMENTS = 50;

    private final WalletService walletService;
    private final LedgerEntryRepository entryRepository;

    public WalletController(WalletService walletService, LedgerEntryRepository entryRepository) {
        this.walletService = walletService;
        this.entryRepository = entryRepository;
    }

    /** Tus puntos y tus últimos movimientos. */
    public record PointsOverview(long availablePoints, long reservedPoints,
                                 List<MovementRow> movements) {
    }

    @Operation(summary = "Tus puntos: libres (para pujar) y reservados (en pujas o retenidos)")
    @GetMapping("/api/me/wallet")
    public WalletService.Balance wallet(@AuthenticationPrincipal Jwt jwt) {
        return walletService.balance(CurrentUser.id(jwt));
    }

    @Operation(summary = "Tus puntos y tus últimos 50 movimientos")
    @GetMapping("/api/me/points")
    @Transactional(readOnly = true)
    public PointsOverview points(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = CurrentUser.id(jwt);
        WalletService.Balance balance = walletService.balance(userId);
        return new PointsOverview(balance.availablePoints(), balance.reservedPoints(),
                entryRepository.findMovements(userId, Limit.of(MOVEMENTS)));
    }
}

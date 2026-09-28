package com.adarena.wallet.repository;

import com.adarena.wallet.domain.LedgerTransactionType;

import java.time.Instant;

/** Un movimiento de los puntos libres de un usuario (historial de "Mis puntos"). */
public record MovementRow(Instant createdAt, LedgerTransactionType type, String description, long amountPoints,
                          long balanceAfterPoints) {
}

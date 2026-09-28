package com.adarena.wallet.domain;

import java.util.UUID;

public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(UUID accountId, long balancePoints, long requestedPoints) {
        super("Account " + accountId + " has " + balancePoints + " points, cannot move " + requestedPoints);
    }
}

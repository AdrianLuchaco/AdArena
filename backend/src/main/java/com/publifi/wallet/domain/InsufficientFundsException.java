package com.publifi.wallet.domain;

import java.util.UUID;

public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(UUID accountId, long balanceCents, long requestedCents) {
        super("Account " + accountId + " has " + balanceCents + " cents, cannot move " + requestedCents);
    }
}

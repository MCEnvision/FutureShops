package com.enviouse.futureshops.server.escrow.admin;

import com.enviouse.futureshops.server.escrow.model.EscrowTransactionId;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record EscrowAdministrativeRecord(
        UUID requestId,
        String actor,
        EscrowAdministrativeAction action,
        Optional<EscrowTransactionId> transactionId,
        String reason,
        Instant createdAt,
        boolean successful,
        String outcome
) {
    public EscrowAdministrativeRecord {
        Objects.requireNonNull(requestId, "requestId");
        actor = requireText(actor, "actor", 160);
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(transactionId, "transactionId");
        reason = requireText(reason, "reason", 1024);
        Objects.requireNonNull(createdAt, "createdAt");
        outcome = requireText(outcome, "outcome", 1024);
        if (requiresTransaction(action) && transactionId.isEmpty()) {
            throw new IllegalArgumentException("Administrative action requires a transaction");
        }
    }

    private static String requireText(String value, String field, int maximumLength) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty() || normalized.length() > maximumLength) {
            throw new IllegalArgumentException("Invalid administrative " + field);
        }
        return normalized;
    }

    private static boolean requiresTransaction(EscrowAdministrativeAction action) {
        return action == EscrowAdministrativeAction.RETRY_TRANSACTION
                || action == EscrowAdministrativeAction.FORCE_REFUND
                || action == EscrowAdministrativeAction.FORCE_SETTLEMENT;
    }
}

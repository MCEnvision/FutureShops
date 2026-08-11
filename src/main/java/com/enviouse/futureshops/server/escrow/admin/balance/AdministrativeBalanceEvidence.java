package com.enviouse.futureshops.server.escrow.admin.balance;

import com.enviouse.futureshops.server.shop.ShopResultCode;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

public record AdministrativeBalanceEvidence(
        UUID evidenceId,
        UUID mutationRequestId,
        String mutationFingerprint,
        AdministrativeBalanceEvidencePhase phase,
        AdministrativeBalanceOperation operation,
        UUID targetPlayerId,
        Optional<UUID> counterpartyPlayerId,
        long amountMinor,
        boolean allowNegative,
        AdministrativeBalanceConfirmation confirmation,
        OptionalLong counterpartyBalanceBefore,
        long balanceBefore,
        long resultingBalance,
        OptionalLong counterpartyResultingBalance,
        boolean successful,
        ShopResultCode resultCode,
        Instant recordedAt
) {
    public AdministrativeBalanceEvidence {
        evidenceId = requireUuid(evidenceId, "evidenceId");
        mutationRequestId = requireUuid(
                mutationRequestId, "mutationRequestId");
        if (mutationFingerprint == null
                || !mutationFingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "Invalid balance mutation fingerprint");
        }
        phase = Objects.requireNonNull(phase, "phase");
        operation = Objects.requireNonNull(operation, "operation");
        targetPlayerId = requireUuid(targetPlayerId, "targetPlayerId");
        counterpartyPlayerId = Objects.requireNonNull(
                counterpartyPlayerId, "counterpartyPlayerId");
        confirmation = Objects.requireNonNull(
                confirmation, "confirmation");
        counterpartyBalanceBefore = Objects.requireNonNull(
                counterpartyBalanceBefore, "counterpartyBalanceBefore");
        counterpartyResultingBalance = Objects.requireNonNull(
                counterpartyResultingBalance,
                "counterpartyResultingBalance");
        resultCode = Objects.requireNonNull(resultCode, "resultCode");
        recordedAt = Objects.requireNonNull(recordedAt, "recordedAt");
        if (operation == AdministrativeBalanceOperation.TRANSFER) {
            UUID counterparty = requireUuid(
                    counterpartyPlayerId.orElseThrow(() ->
                            new IllegalArgumentException(
                                    "Balance evidence requires a counterparty")),
                    "counterpartyPlayerId");
        } else if (counterpartyPlayerId.isPresent()) {
            throw new IllegalArgumentException(
                    "Balance evidence has an unexpected counterparty");
        }
        if (counterpartyPlayerId.isPresent()
                != counterpartyBalanceBefore.isPresent()) {
            throw new IllegalArgumentException(
                    "Balance evidence counterparty balances are invalid");
        }
        if (counterpartyBalanceBefore.isPresent()
                != counterpartyResultingBalance.isPresent()) {
            throw new IllegalArgumentException(
                    "Balance evidence counterparty is incomplete");
        }
        if (phase == AdministrativeBalanceEvidencePhase.INTENT) {
            if (successful || resultCode != ShopResultCode.OK
                    || balanceBefore != resultingBalance
                    || counterpartyBalanceBefore.isPresent()
                    && counterpartyBalanceBefore.getAsLong()
                    != counterpartyResultingBalance.getAsLong()) {
                throw new IllegalArgumentException(
                        "Balance intent evidence is invalid");
            }
        } else if (successful != (resultCode == ShopResultCode.OK)) {
            throw new IllegalArgumentException(
                    "Balance outcome evidence is invalid");
        }
    }

    private static UUID requireUuid(UUID value, String name) {
        UUID result = Objects.requireNonNull(value, name);
        if (result.getMostSignificantBits() == 0L
                && result.getLeastSignificantBits() == 0L) {
            throw new IllegalArgumentException("Invalid " + name);
        }
        return result;
    }
}

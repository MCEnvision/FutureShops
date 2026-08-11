package com.enviouse.futureshops.server.escrow.item.runtime;

import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationReceipt;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationToken;

import java.util.Objects;
import java.util.Optional;

public record ItemInventoryExecutionResult(
        ItemInventoryExecutionStatus status,
        Optional<ItemInventoryMutationToken> token,
        Optional<ItemInventoryMutationReceipt> receipt,
        boolean replayed
) {
    public ItemInventoryExecutionResult {
        Objects.requireNonNull(status, "status");
        token = Objects.requireNonNull(token, "token");
        receipt = Objects.requireNonNull(receipt, "receipt");
        if (receipt.isPresent()
                && (token.isEmpty()
                || !receipt.orElseThrow().token().equals(
                token.orElseThrow()))
                || replayed && status != ItemInventoryExecutionStatus.REPLAYED
                && status != ItemInventoryExecutionStatus.ABORTED
                && status != ItemInventoryExecutionStatus.RECOVERY_REQUIRED
                && status != ItemInventoryExecutionStatus.MANUAL_REVIEW) {
            throw new IllegalArgumentException(
                    "Item inventory execution result is invalid");
        }
    }

    public static ItemInventoryExecutionResult rejected(
            ItemInventoryExecutionStatus status
    ) {
        return new ItemInventoryExecutionResult(status, Optional.empty(),
                Optional.empty(), false);
    }

    public static ItemInventoryExecutionResult applied(
            ItemInventoryMutationReceipt receipt
    ) {
        return new ItemInventoryExecutionResult(
                ItemInventoryExecutionStatus.APPLIED,
                Optional.of(receipt.token()), Optional.of(receipt), false);
    }

    public static ItemInventoryExecutionResult replayed(
            ItemInventoryMutationReceipt receipt
    ) {
        return new ItemInventoryExecutionResult(
                ItemInventoryExecutionStatus.REPLAYED,
                Optional.of(receipt.token()), Optional.of(receipt), true);
    }

    public static ItemInventoryExecutionResult state(
            ItemInventoryExecutionStatus status,
            ItemInventoryMutationIntent intent,
            boolean replayed
    ) {
        return new ItemInventoryExecutionResult(status,
                Optional.of(intent.token()),
                Optional.of(intent.plannedReceipt()), replayed);
    }

    public static ItemInventoryExecutionResult compactedReplay(
            ItemInventoryMutationToken token
    ) {
        return new ItemInventoryExecutionResult(
                ItemInventoryExecutionStatus.REPLAYED,
                Optional.of(Objects.requireNonNull(token, "token")),
                Optional.empty(), true);
    }

    public static ItemInventoryExecutionResult compactedAbort(
            ItemInventoryMutationToken token
    ) {
        return new ItemInventoryExecutionResult(
                ItemInventoryExecutionStatus.ABORTED,
                Optional.of(Objects.requireNonNull(token, "token")),
                Optional.empty(), true);
    }
}

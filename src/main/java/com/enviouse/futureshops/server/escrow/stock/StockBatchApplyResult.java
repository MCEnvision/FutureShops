package com.enviouse.futureshops.server.escrow.stock;

import java.util.List;
import java.util.Objects;

public record StockBatchApplyResult(
        StockMutationReceipt receipt,
        List<StockReservation> reservations,
        boolean replayed
) {
    public StockBatchApplyResult {
        receipt = Objects.requireNonNull(receipt, "receipt");
        reservations = List.copyOf(Objects.requireNonNull(
                reservations, "reservations"));
        if (!receipt.operation().batchOperation()) {
            throw new IllegalArgumentException(
                    "Stock batch result requires a batch receipt");
        }
        if (receipt.outcome() == StockMutationOutcome.APPLIED
                && reservations.size() != receipt.reservationIds().size()) {
            throw new IllegalArgumentException(
                    "Stock batch result is missing reservation state");
        }
    }
}

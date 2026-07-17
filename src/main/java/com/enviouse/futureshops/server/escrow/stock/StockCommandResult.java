package com.enviouse.futureshops.server.escrow.stock;

import java.util.List;
import java.util.Objects;

public record StockCommandResult(
        StockMutationReceipt receipt,
        List<StockReservation> reservations,
        boolean replayed
) {
    public StockCommandResult {
        receipt = Objects.requireNonNull(receipt, "receipt");
        reservations = List.copyOf(Objects.requireNonNull(
                reservations, "reservations"));
    }
}

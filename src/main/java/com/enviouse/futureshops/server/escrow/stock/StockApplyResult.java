package com.enviouse.futureshops.server.escrow.stock;

import java.util.Objects;

public record StockApplyResult(StockMutationReceipt receipt, boolean replayed) {
    public StockApplyResult {
        receipt = Objects.requireNonNull(receipt, "receipt");
    }
}

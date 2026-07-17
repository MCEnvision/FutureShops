package com.enviouse.futureshops.server.escrow.custody;

import java.util.Objects;

public record CustodyOperationResult(
        CustodyLot lot,
        CustodyOperationReceipt receipt,
        boolean replayed
) {
    public CustodyOperationResult {
        Objects.requireNonNull(lot, "lot");
        Objects.requireNonNull(receipt, "receipt");
        if (!lot.lotId().equals(receipt.lotId())) {
            throw new IllegalArgumentException("Custody operation lot and receipt do not match");
        }
    }
}

package com.enviouse.futureshops.server.escrow.mint;

import java.util.List;
import java.util.Objects;

public record ProtectedMintApplyResult(ProtectedMintReceipt receipt,
                                       List<ProtectedMintBatch> affectedBatches,
                                       List<ProtectedMintBatch> replacementBatches,
                                       boolean replayed) {
    public ProtectedMintApplyResult {
        Objects.requireNonNull(receipt, "receipt");
        affectedBatches = List.copyOf(Objects.requireNonNull(affectedBatches,
                "affectedBatches"));
        replacementBatches = List.copyOf(Objects.requireNonNull(replacementBatches,
                "replacementBatches"));
    }
}

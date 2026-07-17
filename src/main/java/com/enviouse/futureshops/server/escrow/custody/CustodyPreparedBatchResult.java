package com.enviouse.futureshops.server.escrow.custody;

public record CustodyPreparedBatchResult(CustodyPreparedBatch batch, boolean replayed) {
    public CustodyPreparedBatchResult {
        if (batch == null) {
            throw new IllegalArgumentException("Custody prepared batch result requires a batch");
        }
    }
}

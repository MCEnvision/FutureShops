package com.enviouse.futureshops.server.escrow.custody;

public record CustodyBatchApplyResult(CustodyBatchCommit commit, boolean replayed) {
    public CustodyBatchApplyResult {
        if (commit == null) {
            throw new IllegalArgumentException("Custody batch apply result requires a commit");
        }
    }
}

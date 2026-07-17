package com.enviouse.futureshops.server.escrow.runtime;

public record EscrowJournalMetrics(long sizeBytes, long recordCount) {
    public EscrowJournalMetrics {
        if (sizeBytes < 0L || recordCount < 0L) {
            throw new IllegalArgumentException("Escrow journal metrics cannot be negative");
        }
    }
}

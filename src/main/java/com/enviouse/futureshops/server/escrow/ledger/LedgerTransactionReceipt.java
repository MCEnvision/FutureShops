package com.enviouse.futureshops.server.escrow.ledger;

import java.util.Objects;

public record LedgerTransactionReceipt(long applicationSequence,
                                       LedgerTransaction transaction,
                                       String fingerprint) {
    public LedgerTransactionReceipt {
        Objects.requireNonNull(transaction, "transaction");
        fingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
        if (applicationSequence < 0L
                || !fingerprint.matches("[0-9a-f]{64}")
                || !fingerprint.equals(transaction.fingerprint())) {
            throw new IllegalArgumentException("Ledger transaction receipt is invalid");
        }
    }

    public static LedgerTransactionReceipt create(long applicationSequence,
                                                  LedgerTransaction transaction) {
        Objects.requireNonNull(transaction, "transaction");
        return new LedgerTransactionReceipt(
                applicationSequence, transaction, transaction.fingerprint());
    }
}

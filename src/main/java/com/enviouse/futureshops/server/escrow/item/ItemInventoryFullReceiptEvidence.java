package com.enviouse.futureshops.server.escrow.item;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ItemInventoryFullReceiptEvidence(
        ItemInventoryMutationReceipt receipt
) implements ItemInventoryReceiptEvidence {
    public ItemInventoryFullReceiptEvidence {
        Objects.requireNonNull(receipt, "receipt");
    }

    @Override
    public UUID requestId() {
        return receipt.token().requestId();
    }

    @Override
    public UUID receiptId() {
        return receipt.token().receiptId();
    }

    @Override
    public UUID mutationId() {
        return receipt.token().mutationId();
    }

    @Override
    public UUID playerId() {
        return receipt.token().playerId();
    }

    @Override
    public UUID transactionId() {
        return receipt.token().transactionId();
    }

    @Override
    public byte[] tokenDigest() {
        return receipt.token().digest();
    }

    @Override
    public byte[] receiptDigest() {
        return receipt.digest();
    }

    @Override
    public Instant appliedAt() {
        return receipt.appliedAt();
    }
}

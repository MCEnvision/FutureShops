package com.enviouse.futureshops.server.escrow.item;

import java.time.Instant;
import java.util.UUID;

public sealed interface ItemInventoryReceiptEvidence
        permits ItemInventoryFullReceiptEvidence,
        ItemInventoryReceiptTombstone {
    UUID requestId();

    UUID receiptId();

    UUID mutationId();

    UUID playerId();

    UUID transactionId();

    byte[] tokenDigest();

    byte[] receiptDigest();

    Instant appliedAt();
}

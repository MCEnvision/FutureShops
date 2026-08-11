package com.enviouse.futureshops.server.escrow.item;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public final class ItemInventoryReceiptTombstone
        implements ItemInventoryReceiptEvidence {
    private final UUID requestId;
    private final UUID receiptId;
    private final UUID mutationId;
    private final UUID playerId;
    private final UUID transactionId;
    private final byte[] tokenDigest;
    private final byte[] receiptDigest;
    private final Instant appliedAt;

    public ItemInventoryReceiptTombstone(
            UUID requestId,
            UUID receiptId,
            UUID mutationId,
            UUID playerId,
            UUID transactionId,
            byte[] tokenDigest,
            byte[] receiptDigest,
            Instant appliedAt
    ) {
        this.requestId = ItemInventoryBatchEntry.requireUuid(
                requestId, "requestId");
        this.receiptId = ItemInventoryBatchEntry.requireUuid(
                receiptId, "receiptId");
        this.mutationId = ItemInventoryBatchEntry.requireUuid(
                mutationId, "mutationId");
        this.playerId = ItemInventoryBatchEntry.requireUuid(
                playerId, "playerId");
        this.transactionId = ItemInventoryBatchEntry.requireUuid(
                transactionId, "transactionId");
        this.tokenDigest = requireHash(tokenDigest,
                "Item receipt tombstone token digest");
        this.receiptDigest = requireHash(receiptDigest,
                "Item receipt tombstone receipt digest");
        this.appliedAt = Objects.requireNonNull(appliedAt, "appliedAt");
    }

    public static ItemInventoryReceiptTombstone fromReceipt(
            ItemInventoryMutationReceipt receipt
    ) {
        ItemInventoryMutationReceipt value = Objects.requireNonNull(
                receipt, "receipt");
        ItemInventoryMutationToken token = value.token();
        return new ItemInventoryReceiptTombstone(token.requestId(),
                token.receiptId(), token.mutationId(), token.playerId(),
                token.transactionId(), token.digest(), value.digest(),
                value.appliedAt());
    }

    @Override
    public UUID requestId() {
        return requestId;
    }

    @Override
    public UUID receiptId() {
        return receiptId;
    }

    @Override
    public UUID mutationId() {
        return mutationId;
    }

    @Override
    public UUID playerId() {
        return playerId;
    }

    @Override
    public UUID transactionId() {
        return transactionId;
    }

    @Override
    public byte[] tokenDigest() {
        return tokenDigest.clone();
    }

    @Override
    public byte[] receiptDigest() {
        return receiptDigest.clone();
    }

    @Override
    public Instant appliedAt() {
        return appliedAt;
    }

    public boolean matchesToken(ItemInventoryMutationToken token) {
        return token != null
                && requestId.equals(token.requestId())
                && receiptId.equals(token.receiptId())
                && mutationId.equals(token.mutationId())
                && playerId.equals(token.playerId())
                && transactionId.equals(token.transactionId())
                && ItemInventoryHashes.equal(tokenDigest, token.digest());
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ItemInventoryReceiptTombstone other
                && requestId.equals(other.requestId)
                && receiptId.equals(other.receiptId)
                && mutationId.equals(other.mutationId)
                && playerId.equals(other.playerId)
                && transactionId.equals(other.transactionId)
                && Arrays.equals(tokenDigest, other.tokenDigest)
                && Arrays.equals(receiptDigest, other.receiptDigest)
                && appliedAt.equals(other.appliedAt);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(requestId, receiptId, mutationId,
                playerId, transactionId, appliedAt);
        result = 31 * result + Arrays.hashCode(tokenDigest);
        return 31 * result + Arrays.hashCode(receiptDigest);
    }

    private static byte[] requireHash(byte[] value, String name) {
        byte[] result = Objects.requireNonNull(value, name).clone();
        ItemInventoryHashes.requireHash(result, name);
        return result;
    }
}

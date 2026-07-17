package com.enviouse.futureshops.server.escrow.item;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class ItemInventoryMutationToken {
    private final UUID playerId;
    private final UUID transactionId;
    private final UUID requestId;
    private final UUID mutationId;
    private final UUID receiptId;
    private final ItemInventoryMutationDirection direction;
    private final byte[] batchFingerprint;
    private final byte[] portionFingerprint;
    private final byte[] beforeInventoryHash;
    private final byte[] afterInventoryHash;
    private final List<ItemInventorySlotChange> changes;
    private final byte[] digest;

    ItemInventoryMutationToken(
            UUID playerId,
            UUID transactionId,
            UUID requestId,
            UUID mutationId,
            UUID receiptId,
            ItemInventoryMutationDirection direction,
            byte[] batchFingerprint,
            byte[] portionFingerprint,
            byte[] beforeInventoryHash,
            byte[] afterInventoryHash,
            List<ItemInventorySlotChange> changes,
            byte[] digest
    ) {
        this.playerId = ItemInventoryBatchEntry.requireUuid(
                playerId, "playerId");
        this.transactionId = ItemInventoryBatchEntry.requireUuid(
                transactionId, "transactionId");
        this.requestId = ItemInventoryBatchEntry.requireUuid(
                requestId, "requestId");
        this.mutationId = ItemInventoryBatchEntry.requireUuid(
                mutationId, "mutationId");
        this.receiptId = ItemInventoryBatchEntry.requireUuid(
                receiptId, "receiptId");
        this.direction = Objects.requireNonNull(direction, "direction");
        this.batchFingerprint = cloneHash(batchFingerprint,
                "Item mutation batch fingerprint");
        this.portionFingerprint = cloneHash(portionFingerprint,
                "Item mutation portion fingerprint");
        this.beforeInventoryHash = cloneHash(beforeInventoryHash,
                "Item mutation before inventory hash");
        this.afterInventoryHash = cloneHash(afterInventoryHash,
                "Item mutation after inventory hash");
        this.changes = List.copyOf(Objects.requireNonNull(changes, "changes"));
        this.digest = cloneHash(digest, "Item mutation token digest");
        if (ItemInventoryHashes.equal(this.beforeInventoryHash,
                this.afterInventoryHash)
                || this.changes.isEmpty()
                || this.changes.size()
                > ItemInventorySlot.ACCESSIBLE_SLOT_COUNT) {
            throw new IllegalArgumentException(
                    "Item mutation token contains no valid inventory change");
        }
        Set<ItemInventorySlot> uniqueSlots = new HashSet<>();
        ItemInventorySlot previous = null;
        for (ItemInventorySlotChange change : this.changes) {
            Objects.requireNonNull(change, "change");
            if (!uniqueSlots.add(change.slot())
                    || previous != null
                    && previous.compareTo(change.slot()) >= 0) {
                throw new IllegalArgumentException(
                        "Item mutation token slot changes are not canonical");
            }
            previous = change.slot();
        }
        UUID expectedMutationId = deterministicId("mutation", this.playerId,
                this.transactionId, this.requestId, this.direction,
                this.batchFingerprint, this.portionFingerprint,
                this.beforeInventoryHash, this.afterInventoryHash);
        UUID expectedReceiptId = deterministicId("receipt", this.playerId,
                this.transactionId, this.requestId, this.direction,
                this.batchFingerprint, this.portionFingerprint,
                this.beforeInventoryHash, this.afterInventoryHash);
        if (!this.mutationId.equals(expectedMutationId)
                || !this.receiptId.equals(expectedReceiptId)
                || !ItemInventoryHashes.equal(this.digest,
                computeDigest(this.playerId, this.transactionId,
                        this.requestId, this.mutationId, this.receiptId,
                        this.direction, this.batchFingerprint,
                        this.portionFingerprint,
                        this.beforeInventoryHash, this.afterInventoryHash,
                        this.changes))) {
            throw new IllegalArgumentException(
                    "Item mutation token identity is invalid");
        }
    }

    public static ItemInventoryMutationToken create(
            UUID playerId,
            UUID transactionId,
            UUID requestId,
            ItemInventoryMutationPlan plan
    ) {
        Objects.requireNonNull(plan, "plan");
        if (!plan.applicable()) {
            throw new IllegalArgumentException(
                    "Rejected item inventory plan cannot create a token");
        }
        byte[] batchFingerprint = plan.batchFingerprint();
        byte[] portionFingerprint = fingerprintPortions(
                plan.allocations());
        byte[] beforeHash = plan.beforeInventoryHash();
        byte[] afterHash = plan.afterInventoryHash();
        UUID mutationId = deterministicId("mutation", playerId,
                transactionId, requestId, plan.direction(),
                batchFingerprint, portionFingerprint, beforeHash, afterHash);
        UUID receiptId = deterministicId("receipt", playerId,
                transactionId, requestId, plan.direction(),
                batchFingerprint, portionFingerprint, beforeHash, afterHash);
        byte[] digest = computeDigest(playerId, transactionId, requestId,
                mutationId, receiptId, plan.direction(), batchFingerprint,
                portionFingerprint, beforeHash, afterHash, plan.changes());
        return new ItemInventoryMutationToken(playerId, transactionId,
                requestId, mutationId, receiptId, plan.direction(),
                batchFingerprint, portionFingerprint, beforeHash, afterHash,
                plan.changes(), digest);
    }

    public UUID playerId() {
        return playerId;
    }

    public UUID transactionId() {
        return transactionId;
    }

    public UUID requestId() {
        return requestId;
    }

    public UUID mutationId() {
        return mutationId;
    }

    public UUID receiptId() {
        return receiptId;
    }

    public ItemInventoryMutationDirection direction() {
        return direction;
    }

    public byte[] batchFingerprint() {
        return batchFingerprint.clone();
    }

    public byte[] portionFingerprint() {
        return portionFingerprint.clone();
    }

    public byte[] beforeInventoryHash() {
        return beforeInventoryHash.clone();
    }

    public byte[] afterInventoryHash() {
        return afterInventoryHash.clone();
    }

    public List<ItemInventorySlotChange> changes() {
        return changes;
    }

    public byte[] digest() {
        return digest.clone();
    }

    public boolean matches(ItemInventoryMutationPlan plan) {
        return plan != null && plan.applicable()
                && direction == plan.direction()
                && ItemInventoryHashes.equal(batchFingerprint,
                plan.batchFingerprint())
                && ItemInventoryHashes.equal(portionFingerprint,
                fingerprintPortions(plan.allocations()))
                && ItemInventoryHashes.equal(beforeInventoryHash,
                plan.beforeInventoryHash())
                && ItemInventoryHashes.equal(afterInventoryHash,
                plan.afterInventoryHash())
                && changes.equals(plan.changes());
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ItemInventoryMutationToken other
                && playerId.equals(other.playerId)
                && transactionId.equals(other.transactionId)
                && requestId.equals(other.requestId)
                && mutationId.equals(other.mutationId)
                && receiptId.equals(other.receiptId)
                && direction == other.direction
                && Arrays.equals(batchFingerprint, other.batchFingerprint)
                && Arrays.equals(portionFingerprint,
                other.portionFingerprint)
                && Arrays.equals(beforeInventoryHash,
                other.beforeInventoryHash)
                && Arrays.equals(afterInventoryHash,
                other.afterInventoryHash)
                && changes.equals(other.changes)
                && Arrays.equals(digest, other.digest);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(playerId, transactionId, requestId,
                mutationId, receiptId, direction, changes);
        result = 31 * result + Arrays.hashCode(batchFingerprint);
        result = 31 * result + Arrays.hashCode(portionFingerprint);
        result = 31 * result + Arrays.hashCode(beforeInventoryHash);
        result = 31 * result + Arrays.hashCode(afterInventoryHash);
        return 31 * result + Arrays.hashCode(digest);
    }

    private static UUID deterministicId(
            String purpose,
            UUID playerId,
            UUID transactionId,
            UUID requestId,
            ItemInventoryMutationDirection direction,
            byte[] batchFingerprint,
            byte[] portionFingerprint,
            byte[] beforeHash,
            byte[] afterHash
    ) {
        Objects.requireNonNull(purpose, "purpose");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(direction, "direction");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.write("futureshops item inventory ".getBytes(
                    StandardCharsets.UTF_8));
            output.write(purpose.getBytes(StandardCharsets.UTF_8));
            ItemEscrowBinaryIo.writeUuid(output, playerId);
            ItemEscrowBinaryIo.writeUuid(output, transactionId);
            ItemEscrowBinaryIo.writeUuid(output, requestId);
            output.writeByte(direction.wireCode());
            output.write(batchFingerprint);
            output.write(portionFingerprint);
            output.write(beforeHash);
            output.write(afterHash);
            output.flush();
            return UUID.nameUUIDFromBytes(bytes.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to create item mutation identity", exception);
        }
    }

    private static byte[] computeDigest(
            UUID playerId,
            UUID transactionId,
            UUID requestId,
            UUID mutationId,
            UUID receiptId,
            ItemInventoryMutationDirection direction,
            byte[] batchFingerprint,
            byte[] portionFingerprint,
            byte[] beforeHash,
            byte[] afterHash,
            List<ItemInventorySlotChange> changes
    ) {
        return ItemInventoryHashes.sha256(
                ItemInventoryMutationTokenCodec.payloadBytes(playerId,
                        transactionId, requestId, mutationId, receiptId,
                        direction, batchFingerprint, portionFingerprint,
                        beforeHash, afterHash, changes));
    }

    static byte[] fingerprintPortions(
            List<ItemInventoryAllocation> portions
    ) {
        Objects.requireNonNull(portions, "portions");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(0x49504F52);
            output.writeInt(1);
            output.writeInt(portions.size());
            for (ItemInventoryAllocation portion : portions) {
                Objects.requireNonNull(portion, "portion");
                ItemEscrowBinaryIo.writeUuid(output, portion.entryId());
                output.writeInt(portion.slot().serializedSlot());
                output.writeInt(portion.count());
                ItemEscrowBinaryIo.writeBytes(output,
                        portion.actualStackSnapshot());
            }
            output.flush();
            return ItemInventoryHashes.sha256(bytes.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to fingerprint item mutation portions",
                    exception);
        }
    }

    private static byte[] cloneHash(byte[] value, String name) {
        byte[] result = Objects.requireNonNull(value, "value").clone();
        ItemInventoryHashes.requireHash(result, name);
        return result;
    }
}

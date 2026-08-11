package com.enviouse.futureshops.server.escrow.item;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ItemInventoryMutationTokenCodec {
    public static final int MAX_ENCODED_BYTES = 8192;

    private static final int MAGIC = 0x49544F4B;
    private static final int VERSION = 1;

    private ItemInventoryMutationTokenCodec() {
    }

    public static byte[] encode(ItemInventoryMutationToken token) {
        Objects.requireNonNull(token, "token");
        byte[] payload = payloadBytes(token.playerId(),
                token.transactionId(), token.requestId(), token.mutationId(),
                token.receiptId(), token.direction(),
                token.batchFingerprint(), token.portionFingerprint(),
                token.beforeInventoryHash(), token.afterInventoryHash(),
                token.changes());
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.write(payload);
            output.write(token.digest());
            output.flush();
            byte[] encoded = bytes.toByteArray();
            requireSize(encoded);
            return encoded;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to encode item mutation token", exception);
        }
    }

    public static ItemInventoryMutationToken decode(byte[] encoded) {
        Objects.requireNonNull(encoded, "encoded");
        requireSize(encoded);
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(encoded))) {
            if (input.readInt() != MAGIC) {
                throw new IllegalArgumentException(
                        "Item mutation token magic is invalid");
            }
            if (input.readUnsignedShort() != VERSION) {
                throw new IllegalArgumentException(
                        "Item mutation token version is unsupported");
            }
            UUID playerId = ItemEscrowBinaryIo.readUuid(input);
            UUID transactionId = ItemEscrowBinaryIo.readUuid(input);
            UUID requestId = ItemEscrowBinaryIo.readUuid(input);
            UUID mutationId = ItemEscrowBinaryIo.readUuid(input);
            UUID receiptId = ItemEscrowBinaryIo.readUuid(input);
            int directionValue = input.readUnsignedByte();
            ItemInventoryMutationDirection direction =
                    ItemInventoryMutationDirection.fromWireCode(
                            directionValue);
            byte[] batchFingerprint = ItemEscrowBinaryIo.readFixed(input,
                    ItemInventoryHashes.HASH_BYTES);
            byte[] portionFingerprint = ItemEscrowBinaryIo.readFixed(input,
                    ItemInventoryHashes.HASH_BYTES);
            byte[] beforeHash = ItemEscrowBinaryIo.readFixed(input,
                    ItemInventoryHashes.HASH_BYTES);
            byte[] afterHash = ItemEscrowBinaryIo.readFixed(input,
                    ItemInventoryHashes.HASH_BYTES);
            int changeCount = input.readInt();
            if (changeCount <= 0 || changeCount
                    > ItemInventorySlot.ACCESSIBLE_SLOT_COUNT) {
                throw new IllegalArgumentException(
                        "Item mutation token change count is invalid");
            }
            List<ItemInventorySlotChange> changes = new ArrayList<>(
                    changeCount);
            for (int index = 0; index < changeCount; index++) {
                changes.add(new ItemInventorySlotChange(
                        new ItemInventorySlot(input.readInt()),
                        ItemEscrowBinaryIo.readFixed(input,
                                ItemInventoryHashes.HASH_BYTES),
                        ItemEscrowBinaryIo.readFixed(input,
                                ItemInventoryHashes.HASH_BYTES)));
            }
            byte[] digest = ItemEscrowBinaryIo.readFixed(input,
                    ItemInventoryHashes.HASH_BYTES);
            ItemEscrowBinaryIo.requireFinished(input,
                    "Item mutation token");
            return new ItemInventoryMutationToken(playerId, transactionId,
                    requestId, mutationId, receiptId, direction,
                    batchFingerprint, portionFingerprint, beforeHash,
                    afterHash, changes, digest);
        } catch (EOFException exception) {
            throw new IllegalArgumentException(
                    "Item mutation token is truncated", exception);
        } catch (IOException | ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "Item mutation token is invalid", exception);
        }
    }

    static byte[] payloadBytes(
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
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(MAGIC);
            output.writeShort(VERSION);
            ItemEscrowBinaryIo.writeUuid(output, playerId);
            ItemEscrowBinaryIo.writeUuid(output, transactionId);
            ItemEscrowBinaryIo.writeUuid(output, requestId);
            ItemEscrowBinaryIo.writeUuid(output, mutationId);
            ItemEscrowBinaryIo.writeUuid(output, receiptId);
            output.writeByte(direction.wireCode());
            output.write(batchFingerprint);
            output.write(portionFingerprint);
            output.write(beforeHash);
            output.write(afterHash);
            output.writeInt(changes.size());
            for (ItemInventorySlotChange change : changes) {
                output.writeInt(change.slot().serializedSlot());
                output.write(change.beforeHash());
                output.write(change.afterHash());
            }
            output.flush();
            byte[] payload = bytes.toByteArray();
            if (payload.length + ItemInventoryHashes.HASH_BYTES
                    > MAX_ENCODED_BYTES) {
                throw new IllegalArgumentException(
                        "Item mutation token exceeds its limit");
            }
            return payload;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to encode item mutation token payload", exception);
        }
    }

    private static void requireSize(byte[] encoded) {
        if (encoded.length <= ItemInventoryHashes.HASH_BYTES
                || encoded.length > MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException(
                    "Item mutation token size is invalid");
        }
    }
}

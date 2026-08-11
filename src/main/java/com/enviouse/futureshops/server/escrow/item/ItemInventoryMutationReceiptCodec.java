package com.enviouse.futureshops.server.escrow.item;

import com.enviouse.futureshops.money.ItemStackSnapshotCodec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ItemInventoryMutationReceiptCodec {
    public static final int MAX_ENCODED_BYTES = 4 * 1024 * 1024;

    private static final int MAGIC = 0x49524543;
    private static final int VERSION = 1;

    private ItemInventoryMutationReceiptCodec() {
    }

    public static byte[] encode(ItemInventoryMutationReceipt receipt) {
        Objects.requireNonNull(receipt, "receipt");
        byte[] payload = payloadBytes(receipt.token(),
                receipt.actualPortions(), receipt.appliedAt());
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.write(payload);
            output.write(receipt.digest());
            output.flush();
            byte[] encoded = bytes.toByteArray();
            requireSize(encoded);
            return encoded;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to encode item mutation receipt", exception);
        }
    }

    public static ItemInventoryMutationReceipt decode(byte[] encoded) {
        Objects.requireNonNull(encoded, "encoded");
        requireSize(encoded);
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(encoded))) {
            if (input.readInt() != MAGIC) {
                throw new IllegalArgumentException(
                        "Item mutation receipt magic is invalid");
            }
            if (input.readUnsignedShort() != VERSION) {
                throw new IllegalArgumentException(
                        "Item mutation receipt version is unsupported");
            }
            byte[] tokenBytes = ItemEscrowBinaryIo.readBytes(input,
                    ItemInventoryMutationTokenCodec.MAX_ENCODED_BYTES, false);
            ItemInventoryMutationToken token =
                    ItemInventoryMutationTokenCodec.decode(tokenBytes);
            int portionCount = input.readInt();
            if (portionCount <= 0
                    || portionCount
                    > ItemInventoryMutationReceipt.MAX_ALLOCATIONS) {
                throw new IllegalArgumentException(
                        "Item mutation receipt portion count is invalid");
            }
            List<ItemInventoryAllocation> portions = new ArrayList<>(
                    portionCount);
            for (int index = 0; index < portionCount; index++) {
                portions.add(new ItemInventoryAllocation(
                        ItemEscrowBinaryIo.readUuid(input),
                        new ItemInventorySlot(input.readInt()),
                        input.readInt(),
                        ItemEscrowBinaryIo.readBytes(input,
                                ItemStackSnapshotCodec.MAXIMUM_BYTES,
                                false)));
            }
            Instant appliedAt = ItemEscrowBinaryIo.readInstant(input);
            byte[] digest = ItemEscrowBinaryIo.readFixed(input,
                    ItemInventoryHashes.HASH_BYTES);
            ItemEscrowBinaryIo.requireFinished(input,
                    "Item mutation receipt");
            return new ItemInventoryMutationReceipt(token, portions,
                    appliedAt, digest);
        } catch (EOFException exception) {
            throw new IllegalArgumentException(
                    "Item mutation receipt is truncated", exception);
        } catch (IOException | ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "Item mutation receipt is invalid", exception);
        }
    }

    static byte[] payloadBytes(
            ItemInventoryMutationToken token,
            List<ItemInventoryAllocation> portions,
            Instant appliedAt
    ) {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(portions, "portions");
        Objects.requireNonNull(appliedAt, "appliedAt");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(MAGIC);
            output.writeShort(VERSION);
            ItemEscrowBinaryIo.writeBytes(output,
                    ItemInventoryMutationTokenCodec.encode(token));
            output.writeInt(portions.size());
            for (ItemInventoryAllocation portion : portions) {
                ItemEscrowBinaryIo.writeUuid(output, portion.entryId());
                output.writeInt(portion.slot().serializedSlot());
                output.writeInt(portion.count());
                ItemEscrowBinaryIo.writeBytes(output,
                        portion.actualStackSnapshot());
            }
            ItemEscrowBinaryIo.writeInstant(output, appliedAt);
            output.flush();
            byte[] payload = bytes.toByteArray();
            if (payload.length + ItemInventoryHashes.HASH_BYTES
                    > MAX_ENCODED_BYTES) {
                throw new IllegalArgumentException(
                        "Item mutation receipt exceeds its limit");
            }
            return payload;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to encode item mutation receipt payload",
                    exception);
        }
    }

    static void requirePlanFits(
            List<ItemInventoryAllocation> portions,
            int changeCount
    ) {
        long receiptBytes = projectedEncodedSize(portions, changeCount);
        if (receiptBytes > MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException(
                    "Item mutation receipt exceeds its limit");
        }
    }

    static long projectedEncodedSize(
            List<ItemInventoryAllocation> portions,
            int changeCount
    ) {
        Objects.requireNonNull(portions, "portions");
        if (changeCount <= 0
                || changeCount > ItemInventorySlot.ACCESSIBLE_SLOT_COUNT) {
            throw new IllegalArgumentException(
                    "Item mutation receipt change count is invalid");
        }
        long tokenBytes = 251L + Math.multiplyExact(68L, changeCount);
        long receiptBytes = Math.addExact(58L, tokenBytes);
        for (ItemInventoryAllocation portion : portions) {
            Objects.requireNonNull(portion, "portion");
            receiptBytes = Math.addExact(receiptBytes,
                    Math.addExact(28L,
                            portion.actualStackSnapshot().length));
        }
        if (tokenBytes > ItemInventoryMutationTokenCodec.MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException(
                    "Item mutation token exceeds its limit");
        }
        return receiptBytes;
    }

    private static void requireSize(byte[] encoded) {
        if (encoded.length <= ItemInventoryHashes.HASH_BYTES
                || encoded.length > MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException(
                    "Item mutation receipt size is invalid");
        }
    }
}

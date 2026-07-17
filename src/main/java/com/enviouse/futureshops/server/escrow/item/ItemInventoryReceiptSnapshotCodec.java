package com.enviouse.futureshops.server.escrow.item;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class ItemInventoryReceiptSnapshotCodec {
    public static final int MAX_ENCODED_BYTES = 32 * 1024 * 1024;

    private static final int MAGIC = 0x49525350;
    private static final int VERSION_ONE = 1;
    private static final int VERSION_TWO = 2;
    private static final int FULL = 1;
    private static final int TOMBSTONE = 2;
    private static final int HEADER_BYTES = 18;
    private static final int DIGEST_BYTES = ItemInventoryHashes.HASH_BYTES;
    private static final int FULL_ENTRY_OVERHEAD = 21;
    private static final int TOMBSTONE_ENTRY_BYTES = 157;

    private ItemInventoryReceiptSnapshotCodec() {
    }

    public static byte[] encode(ItemInventoryReceiptSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        long fullReceiptBytes = ItemInventoryReceiptRepository.encodedSize(
                snapshot.receipts());
        requireProjectedSize(snapshot.receipts().size(), fullReceiptBytes,
                snapshot.tombstones().size());
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(MAGIC);
            output.writeShort(VERSION_TWO);
            output.writeLong(snapshot.revision());
            List<SnapshotEntry> entries = entries(snapshot);
            output.writeInt(entries.size());
            for (SnapshotEntry entry : entries) {
                output.writeByte(entry.kind());
                ItemEscrowBinaryIo.writeUuid(output, entry.requestId());
                if (entry.kind() == FULL) {
                    ItemEscrowBinaryIo.writeBytes(output,
                            ItemInventoryMutationReceiptCodec.encode(
                                    entry.receipt()));
                } else {
                    writeTombstone(output, entry.tombstone());
                }
            }
            output.flush();
            byte[] payload = bytes.toByteArray();
            byte[] digest = ItemInventoryHashes.sha256(payload);
            byte[] encoded = Arrays.copyOf(payload,
                    Math.addExact(payload.length, digest.length));
            System.arraycopy(digest, 0, encoded, payload.length,
                    digest.length);
            requireSize(encoded);
            return encoded;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to encode item receipt snapshot", exception);
        }
    }

    public static ItemInventoryReceiptSnapshot decode(byte[] encoded) {
        Objects.requireNonNull(encoded, "encoded");
        requireSize(encoded);
        int payloadLength = encoded.length - DIGEST_BYTES;
        byte[] payload = Arrays.copyOf(encoded, payloadLength);
        byte[] storedDigest = Arrays.copyOfRange(encoded, payloadLength,
                encoded.length);
        if (!MessageDigest.isEqual(ItemInventoryHashes.sha256(payload),
                storedDigest)) {
            throw new IllegalArgumentException(
                    "Item receipt snapshot digest is invalid");
        }
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(payload))) {
            if (input.readInt() != MAGIC) {
                throw new IllegalArgumentException(
                        "Item receipt snapshot magic is invalid");
            }
            int version = input.readUnsignedShort();
            if (version != VERSION_ONE && version != VERSION_TWO) {
                throw new IllegalArgumentException(
                        "Item receipt snapshot version is unsupported");
            }
            long revision = input.readLong();
            int count = input.readInt();
            if (version == VERSION_ONE) {
                return decodeVersionOne(input, revision, count);
            }
            return decodeVersionTwo(input, revision, count);
        } catch (EOFException exception) {
            throw new IllegalArgumentException(
                    "Item receipt snapshot is truncated", exception);
        } catch (IOException | ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "Item receipt snapshot is invalid", exception);
        }
    }

    static void requireProjectedSize(
            int receiptCount,
            long totalReceiptBytes,
            int tombstoneCount
    ) {
        requireProjectedSize(receiptCount, totalReceiptBytes,
                tombstoneCount, MAX_ENCODED_BYTES);
    }

    static void requireProjectedSize(
            int receiptCount,
            long totalReceiptBytes,
            int tombstoneCount,
            int maximumEncodedBytes
    ) {
        if (receiptCount < 0
                || receiptCount > ItemInventoryReceiptRepository.MAX_RECEIPTS
                || totalReceiptBytes < 0L
                || totalReceiptBytes
                > ItemInventoryReceiptRepository.MAX_TOTAL_RECEIPT_BYTES
                || tombstoneCount < 0
                || tombstoneCount
                > ItemInventoryReceiptRepository.MAX_TOMBSTONES
                || maximumEncodedBytes < HEADER_BYTES + DIGEST_BYTES
                || maximumEncodedBytes > MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException(
                    "Item receipt snapshot projection is invalid");
        }
        long size = Math.addExact(HEADER_BYTES, DIGEST_BYTES);
        size = Math.addExact(size, totalReceiptBytes);
        size = Math.addExact(size, Math.multiplyExact(
                (long) receiptCount, FULL_ENTRY_OVERHEAD));
        size = Math.addExact(size, Math.multiplyExact(
                (long) tombstoneCount, TOMBSTONE_ENTRY_BYTES));
        if (size > maximumEncodedBytes) {
            throw new IllegalArgumentException(
                    "Item receipt snapshot exceeds its limit");
        }
    }

    private static ItemInventoryReceiptSnapshot decodeVersionOne(
            DataInputStream input,
            long revision,
            int count
    ) throws IOException {
        if (count < 0
                || count > ItemInventoryReceiptRepository.MAX_RECEIPTS) {
            throw new IllegalArgumentException(
                    "Item receipt snapshot count is invalid");
        }
        Map<UUID, ItemInventoryMutationReceipt> receipts =
                new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            UUID requestId = ItemEscrowBinaryIo.readUuid(input);
            ItemInventoryMutationReceipt receipt =
                    ItemInventoryMutationReceiptCodec.decode(
                            ItemEscrowBinaryIo.readBytes(input,
                                    ItemInventoryMutationReceiptCodec
                                            .MAX_ENCODED_BYTES,
                                    false));
            if (!requestId.equals(receipt.token().requestId())
                    || receipts.put(requestId, receipt) != null) {
                throw new IllegalArgumentException(
                        "Item receipt snapshot contains a conflict");
            }
        }
        ItemEscrowBinaryIo.requireFinished(input,
                "Item receipt snapshot");
        return new ItemInventoryReceiptSnapshot(revision, receipts,
                Map.of());
    }

    private static ItemInventoryReceiptSnapshot decodeVersionTwo(
            DataInputStream input,
            long revision,
            int count
    ) throws IOException {
        int maximumEntries = Math.addExact(
                ItemInventoryReceiptRepository.MAX_RECEIPTS,
                ItemInventoryReceiptRepository.MAX_TOMBSTONES);
        if (count < 0 || count > maximumEntries) {
            throw new IllegalArgumentException(
                    "Item receipt snapshot count is invalid");
        }
        Map<UUID, ItemInventoryMutationReceipt> receipts =
                new LinkedHashMap<>();
        Map<UUID, ItemInventoryReceiptTombstone> tombstones =
                new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            int kind = input.readUnsignedByte();
            UUID requestId = ItemEscrowBinaryIo.readUuid(input);
            if (kind == FULL) {
                ItemInventoryMutationReceipt receipt =
                        ItemInventoryMutationReceiptCodec.decode(
                                ItemEscrowBinaryIo.readBytes(input,
                                        ItemInventoryMutationReceiptCodec
                                                .MAX_ENCODED_BYTES,
                                        false));
                if (!requestId.equals(receipt.token().requestId())
                        || receipts.put(requestId, receipt) != null
                        || tombstones.containsKey(requestId)) {
                    throw new IllegalArgumentException(
                            "Item receipt snapshot contains a conflict");
                }
            } else if (kind == TOMBSTONE) {
                ItemInventoryReceiptTombstone tombstone = readTombstone(
                        input, requestId);
                if (tombstones.put(requestId, tombstone) != null
                        || receipts.containsKey(requestId)) {
                    throw new IllegalArgumentException(
                            "Item receipt snapshot contains a conflict");
                }
            } else {
                throw new IllegalArgumentException(
                        "Item receipt snapshot entry kind is invalid");
            }
            if (receipts.size()
                    > ItemInventoryReceiptRepository.MAX_RECEIPTS
                    || tombstones.size()
                    > ItemInventoryReceiptRepository.MAX_TOMBSTONES) {
                throw new IllegalArgumentException(
                        "Item receipt snapshot entry limit is exceeded");
            }
        }
        ItemEscrowBinaryIo.requireFinished(input,
                "Item receipt snapshot");
        return new ItemInventoryReceiptSnapshot(revision, receipts,
                tombstones);
    }

    private static List<SnapshotEntry> entries(
            ItemInventoryReceiptSnapshot snapshot
    ) {
        List<SnapshotEntry> entries = new ArrayList<>(Math.addExact(
                snapshot.receipts().size(), snapshot.tombstones().size()));
        snapshot.receipts().forEach((requestId, receipt) -> entries.add(
                SnapshotEntry.full(requestId, receipt)));
        snapshot.tombstones().forEach((requestId, tombstone) -> entries.add(
                SnapshotEntry.tombstone(requestId, tombstone)));
        entries.sort(Comparator.comparing(entry ->
                entry.requestId().toString()));
        return entries;
    }

    private static void writeTombstone(
            DataOutputStream output,
            ItemInventoryReceiptTombstone tombstone
    ) throws IOException {
        ItemEscrowBinaryIo.writeUuid(output, tombstone.receiptId());
        ItemEscrowBinaryIo.writeUuid(output, tombstone.mutationId());
        ItemEscrowBinaryIo.writeUuid(output, tombstone.playerId());
        ItemEscrowBinaryIo.writeUuid(output, tombstone.transactionId());
        output.write(tombstone.tokenDigest());
        output.write(tombstone.receiptDigest());
        ItemEscrowBinaryIo.writeInstant(output, tombstone.appliedAt());
    }

    private static ItemInventoryReceiptTombstone readTombstone(
            DataInputStream input,
            UUID requestId
    ) throws IOException {
        return new ItemInventoryReceiptTombstone(requestId,
                ItemEscrowBinaryIo.readUuid(input),
                ItemEscrowBinaryIo.readUuid(input),
                ItemEscrowBinaryIo.readUuid(input),
                ItemEscrowBinaryIo.readUuid(input),
                ItemEscrowBinaryIo.readFixed(input,
                        ItemInventoryHashes.HASH_BYTES),
                ItemEscrowBinaryIo.readFixed(input,
                        ItemInventoryHashes.HASH_BYTES),
                ItemEscrowBinaryIo.readInstant(input));
    }

    private static void requireSize(byte[] encoded) {
        if (encoded.length <= DIGEST_BYTES
                || encoded.length > MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException(
                    "Item receipt snapshot size is invalid");
        }
    }

    private record SnapshotEntry(
            int kind,
            UUID requestId,
            ItemInventoryMutationReceipt receipt,
            ItemInventoryReceiptTombstone tombstone
    ) {
        private static SnapshotEntry full(
                UUID requestId,
                ItemInventoryMutationReceipt receipt
        ) {
            return new SnapshotEntry(FULL, requestId, receipt, null);
        }

        private static SnapshotEntry tombstone(
                UUID requestId,
                ItemInventoryReceiptTombstone tombstone
        ) {
            return new SnapshotEntry(TOMBSTONE, requestId, null, tombstone);
        }
    }
}

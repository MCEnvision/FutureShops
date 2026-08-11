package com.enviouse.futureshops.server.escrow.item.runtime;

import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationReceipt;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationReceiptCodec;
import com.enviouse.futureshops.server.escrow.runtime.EscrowJournalEventCodec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class ItemInventoryJournalSnapshotCodec {
    public static final int MAX_ENCODED_BYTES = 60 * 1024 * 1024;

    private static final int MAGIC = 0x494A5350;
    private static final int VERSION = 3;
    private static final int DIGEST_BYTES = 32;

    private ItemInventoryJournalSnapshotCodec() {
    }

    public static byte[] encode(ItemInventoryJournalSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(MAGIC);
            output.writeShort(VERSION);
            output.writeLong(snapshot.revision());
            output.writeInt(snapshot.entries().size());
            for (ItemInventoryJournalEntry entry : snapshot.entries()) {
                writeEntry(output, entry);
                if (bytes.size() > MAX_ENCODED_BYTES - DIGEST_BYTES) {
                    throw new IllegalArgumentException(
                            "Item inventory journal snapshot exceeds its limit");
                }
            }
            output.writeInt(snapshot.administrations().size());
            for (ItemInventoryQuarantineAdministration administration
                    : snapshot.administrations()) {
                writeBytes(output,
                        ItemInventoryQuarantineAdministrationCodec.encode(
                                administration));
                if (bytes.size() > MAX_ENCODED_BYTES - DIGEST_BYTES) {
                    throw new IllegalArgumentException(
                            "Item inventory journal snapshot exceeds its limit");
                }
            }
            output.writeInt(snapshot.tombstones().size());
            for (ItemInventoryTerminalTombstone tombstone
                    : snapshot.tombstones()) {
                writeBytes(output,
                        ItemInventoryTerminalTombstoneCodec.encode(
                                tombstone));
                if (bytes.size() > MAX_ENCODED_BYTES - DIGEST_BYTES) {
                    throw new IllegalArgumentException(
                            "Item inventory journal snapshot exceeds its limit");
                }
            }
            output.flush();
            byte[] payload = bytes.toByteArray();
            byte[] digest = sha256(payload);
            byte[] encoded = Arrays.copyOf(payload,
                    Math.addExact(payload.length, digest.length));
            System.arraycopy(digest, 0, encoded, payload.length,
                    digest.length);
            requireSize(encoded);
            return encoded;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to encode item inventory journal snapshot",
                    exception);
        }
    }

    public static ItemInventoryJournalSnapshot decode(byte[] encoded) {
        byte[] copied = Objects.requireNonNull(encoded, "encoded").clone();
        requireSize(copied);
        int payloadLength = copied.length - DIGEST_BYTES;
        byte[] payload = Arrays.copyOf(copied, payloadLength);
        byte[] storedDigest = Arrays.copyOfRange(copied, payloadLength,
                copied.length);
        if (!MessageDigest.isEqual(storedDigest, sha256(payload))) {
            throw new IllegalArgumentException(
                    "Item inventory journal snapshot digest is invalid");
        }
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(payload))) {
            if (input.readInt() != MAGIC) {
                throw new IllegalArgumentException(
                        "Item inventory journal snapshot magic is invalid");
            }
            int version = input.readUnsignedShort();
            if (version < 1 || version > VERSION) {
                throw new IllegalArgumentException(
                        "Item inventory journal snapshot version is unsupported");
            }
            long revision = input.readLong();
            int count = input.readInt();
            if (revision < 0L || count < 0
                    || count > PersistentItemInventoryJournal.MAX_ENTRIES) {
                throw new IllegalArgumentException(
                        "Item inventory journal snapshot header is invalid");
            }
            List<ItemInventoryJournalEntry> entries = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                entries.add(readEntry(input));
            }
            List<ItemInventoryQuarantineAdministration> administrations =
                    new ArrayList<>();
            if (version >= 2) {
                int administrationCount = input.readInt();
                if (administrationCount < 0
                        || administrationCount
                        > PersistentItemInventoryJournal.MAX_ENTRIES) {
                    throw new IllegalArgumentException(
                            "Item inventory journal administration count is invalid");
                }
                administrations = new ArrayList<>(administrationCount);
                for (int index = 0; index < administrationCount; index++) {
                    administrations.add(
                            ItemInventoryQuarantineAdministrationCodec.decode(
                                    readBytes(input,
                                            EscrowJournalEventCodec
                                                    .MAX_BODY_BYTES)));
                }
            }
            List<ItemInventoryTerminalTombstone> tombstones =
                    new ArrayList<>();
            if (version >= 3) {
                int tombstoneCount = input.readInt();
                if (tombstoneCount < 0
                        || tombstoneCount
                        > PersistentItemInventoryJournal.MAX_TOMBSTONES) {
                    throw new IllegalArgumentException(
                            "Item inventory journal tombstone count is invalid");
                }
                tombstones = new ArrayList<>(tombstoneCount);
                for (int index = 0; index < tombstoneCount; index++) {
                    tombstones.add(ItemInventoryTerminalTombstoneCodec.decode(
                            readBytes(input,
                                    ItemInventoryTerminalTombstoneCodec
                                            .MAX_ENCODED_BYTES)));
                }
            }
            if (input.available() != 0) {
                throw new IllegalArgumentException(
                        "Item inventory journal snapshot has trailing data");
            }
            return new ItemInventoryJournalSnapshot(revision, entries,
                    administrations, tombstones);
        } catch (EOFException exception) {
            throw new IllegalArgumentException(
                    "Item inventory journal snapshot is truncated",
                    exception);
        } catch (IOException | ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "Item inventory journal snapshot is invalid",
                    exception);
        }
    }

    static int encodedEntryBytes(ItemInventoryJournalEntry entry) {
        Objects.requireNonNull(entry, "entry");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            writeEntry(new DataOutputStream(bytes), entry);
            return bytes.size();
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to size item inventory journal entry",
                    exception);
        }
    }

    static int encodedAdministrationBytes(
            ItemInventoryQuarantineAdministration administration
    ) {
        return Integer.BYTES
                + ItemInventoryQuarantineAdministrationCodec.encode(
                administration).length;
    }

    static int encodedTombstoneBytes(
            ItemInventoryTerminalTombstone tombstone
    ) {
        return Integer.BYTES
                + ItemInventoryTerminalTombstoneCodec.encode(
                tombstone).length;
    }

    private static void writeEntry(
            DataOutputStream output,
            ItemInventoryJournalEntry entry
    ) throws IOException {
        Objects.requireNonNull(entry, "entry");
        writeBytes(output, ItemInventoryMutationIntentCodec.encode(
                entry.intent()));
        output.writeByte(entry.status().wireCode());
        switch (entry.status()) {
            case PREPARED -> {
            }
            case COMMITTED -> writeBytes(output,
                    ItemInventoryMutationReceiptCodec.encode(
                            entry.committedReceipt().orElseThrow()));
            case ABORTED -> writeBytes(output,
                    ItemInventoryMutationAbortCodec.encode(
                            entry.abort().orElseThrow()));
            case QUARANTINED -> {
                output.writeBoolean(entry.committedReceipt().isPresent());
                if (entry.committedReceipt().isPresent()) {
                    writeBytes(output,
                            ItemInventoryMutationReceiptCodec.encode(
                                    entry.committedReceipt().orElseThrow()));
                }
                writeBytes(output, ItemInventoryMutationQuarantineCodec
                        .encode(entry.quarantine().orElseThrow()));
            }
        }
    }

    private static ItemInventoryJournalEntry readEntry(
            DataInputStream input
    ) throws IOException {
        ItemInventoryMutationIntent intent =
                ItemInventoryMutationIntentCodec.decode(readBytes(input,
                        ItemInventoryMutationIntentCodec.MAX_ENCODED_BYTES));
        ItemInventoryJournalStatus status =
                ItemInventoryJournalStatus.fromWireCode(
                        input.readUnsignedByte());
        return switch (status) {
            case PREPARED -> ItemInventoryJournalEntry.prepared(intent);
            case COMMITTED -> ItemInventoryJournalEntry.committed(intent,
                    ItemInventoryMutationReceiptCodec.decode(readBytes(input,
                            ItemInventoryMutationReceiptCodec
                                    .MAX_ENCODED_BYTES)));
            case ABORTED -> ItemInventoryJournalEntry.aborted(intent,
                    ItemInventoryMutationAbortCodec.decode(readBytes(input,
                            ItemInventoryMutationAbortCodec
                                    .MAX_ENCODED_BYTES)));
            case QUARANTINED -> {
                int receiptFlag = input.readUnsignedByte();
                if (receiptFlag > 1) {
                    throw new IllegalArgumentException(
                            "Item inventory journal receipt flag is invalid");
                }
                Optional<ItemInventoryMutationReceipt> receipt =
                        receiptFlag == 1
                                ? Optional.of(ItemInventoryMutationReceiptCodec
                                .decode(readBytes(input,
                                        ItemInventoryMutationReceiptCodec
                                                .MAX_ENCODED_BYTES)))
                                : Optional.empty();
                ItemInventoryMutationQuarantine quarantine =
                        ItemInventoryMutationQuarantineCodec.decode(
                                readBytes(input,
                                        ItemInventoryMutationQuarantineCodec
                                                .MAX_ENCODED_BYTES));
                yield new ItemInventoryJournalEntry(intent,
                        ItemInventoryJournalStatus.QUARANTINED, receipt,
                        Optional.empty(), Optional.of(quarantine));
            }
        };
    }

    private static void writeBytes(DataOutputStream output, byte[] value)
            throws IOException {
        output.writeInt(value.length);
        output.write(value);
    }

    private static byte[] readBytes(DataInputStream input, int maximum)
            throws IOException {
        int length = input.readInt();
        if (length <= 0 || length > maximum
                || length > input.available()) {
            throw new IllegalArgumentException(
                    "Item inventory journal snapshot field size is invalid");
        }
        byte[] value = new byte[length];
        input.readFully(value);
        return value;
    }

    private static void requireSize(byte[] encoded) {
        if (encoded.length <= DIGEST_BYTES
                || encoded.length > MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException(
                    "Item inventory journal snapshot size is invalid");
        }
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable",
                    exception);
        }
    }

}

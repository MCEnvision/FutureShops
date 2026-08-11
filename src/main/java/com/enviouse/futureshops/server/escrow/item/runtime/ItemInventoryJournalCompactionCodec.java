package com.enviouse.futureshops.server.escrow.item.runtime;

import com.enviouse.futureshops.server.escrow.runtime.EscrowJournalEventCodec;

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

public final class ItemInventoryJournalCompactionCodec {
    private static final int MAGIC = 0x494A434F;
    private static final int VERSION = 1;

    private ItemInventoryJournalCompactionCodec() {
    }

    public static byte[] encode(ItemInventoryJournalCompaction compaction) {
        Objects.requireNonNull(compaction, "compaction");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(MAGIC);
            output.writeShort(VERSION);
            writeUuid(output, compaction.commandId());
            writeUuid(output, compaction.checkpointId());
            writeUuid(output, compaction.sourceJournalLineageId());
            writeUuid(output, compaction.replacementJournalLineageId());
            output.writeLong(compaction.baseJournalSequence());
            output.write(compaction.checkpointDigest());
            output.writeInt(compaction.tombstones().size());
            for (ItemInventoryTerminalTombstone tombstone
                    : compaction.tombstones()) {
                byte[] encoded = ItemInventoryTerminalTombstoneCodec.encode(
                        tombstone);
                output.writeInt(encoded.length);
                output.write(encoded);
            }
            output.flush();
            byte[] encoded = bytes.toByteArray();
            if (encoded.length > EscrowJournalEventCodec.MAX_BODY_BYTES) {
                throw new IllegalArgumentException(
                        "Item inventory compaction exceeds journal bounds");
            }
            return encoded;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to encode item inventory compaction", exception);
        }
    }

    public static ItemInventoryJournalCompaction decode(byte[] encoded) {
        byte[] value = Objects.requireNonNull(encoded, "encoded").clone();
        if (value.length == 0
                || value.length > EscrowJournalEventCodec.MAX_BODY_BYTES) {
            throw new IllegalArgumentException(
                    "Item inventory compaction size is invalid");
        }
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(value))) {
            if (input.readInt() != MAGIC
                    || input.readUnsignedShort() != VERSION) {
                throw new IllegalArgumentException(
                        "Item inventory compaction header is invalid");
            }
            UUID commandId = readUuid(input);
            UUID checkpointId = readUuid(input);
            UUID sourceLineage = readUuid(input);
            UUID replacementLineage = readUuid(input);
            long baseSequence = input.readLong();
            byte[] checkpointDigest = input.readNBytes(32);
            if (checkpointDigest.length != 32) {
                throw new EOFException();
            }
            int count = input.readInt();
            if (count <= 0
                    || count > ItemInventoryJournalCompaction
                    .MAX_TOMBSTONES_PER_COMPACTION) {
                throw new IllegalArgumentException(
                        "Item inventory compaction count is invalid");
            }
            List<ItemInventoryTerminalTombstone> tombstones =
                    new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                int length = input.readInt();
                if (length <= 0
                        || length
                        > ItemInventoryTerminalTombstoneCodec
                        .MAX_ENCODED_BYTES
                        || length > input.available()) {
                    throw new IllegalArgumentException(
                            "Item inventory compaction tombstone is invalid");
                }
                tombstones.add(ItemInventoryTerminalTombstoneCodec.decode(
                        input.readNBytes(length)));
            }
            if (input.available() != 0) {
                throw new IllegalArgumentException(
                        "Item inventory compaction has trailing data");
            }
            return new ItemInventoryJournalCompaction(commandId,
                    checkpointId, sourceLineage, replacementLineage,
                    baseSequence, checkpointDigest, tombstones);
        } catch (EOFException exception) {
            throw new IllegalArgumentException(
                    "Item inventory compaction is truncated", exception);
        } catch (IOException | ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "Item inventory compaction is invalid", exception);
        }
    }

    private static void writeUuid(DataOutputStream output, UUID value)
            throws IOException {
        output.writeLong(value.getMostSignificantBits());
        output.writeLong(value.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream input) throws IOException {
        return new UUID(input.readLong(), input.readLong());
    }
}

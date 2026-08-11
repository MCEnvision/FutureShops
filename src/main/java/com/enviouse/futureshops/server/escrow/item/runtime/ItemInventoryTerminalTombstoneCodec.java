package com.enviouse.futureshops.server.escrow.item.runtime;

import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationToken;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationTokenCodec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class ItemInventoryTerminalTombstoneCodec {
    public static final int MAX_ENCODED_BYTES =
            ItemInventoryMutationTokenCodec.MAX_ENCODED_BYTES + 128;

    private static final int MAGIC = 0x49545453;
    private static final int VERSION = 1;

    private ItemInventoryTerminalTombstoneCodec() {
    }

    public static byte[] encode(ItemInventoryTerminalTombstone tombstone) {
        Objects.requireNonNull(tombstone, "tombstone");
        try {
            byte[] token = ItemInventoryMutationTokenCodec.encode(
                    tombstone.token());
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(MAGIC);
            output.writeShort(VERSION);
            writeUuid(output, tombstone.compactionCommandId());
            writeUuid(output, tombstone.checkpointId());
            output.writeByte(tombstone.status().wireCode());
            output.writeLong(tombstone.terminalAt().getEpochSecond());
            output.writeInt(tombstone.terminalAt().getNano());
            output.write(tombstone.terminalDigest());
            output.writeInt(token.length);
            output.write(token);
            output.flush();
            byte[] encoded = bytes.toByteArray();
            if (encoded.length > MAX_ENCODED_BYTES) {
                throw new IllegalArgumentException(
                        "Item inventory tombstone exceeds its limit");
            }
            return encoded;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to encode item inventory tombstone", exception);
        }
    }

    public static ItemInventoryTerminalTombstone decode(byte[] encoded) {
        byte[] value = Objects.requireNonNull(encoded, "encoded").clone();
        if (value.length == 0 || value.length > MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException(
                    "Item inventory tombstone size is invalid");
        }
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(value))) {
            if (input.readInt() != MAGIC
                    || input.readUnsignedShort() != VERSION) {
                throw new IllegalArgumentException(
                        "Item inventory tombstone header is invalid");
            }
            UUID commandId = readUuid(input);
            UUID checkpointId = readUuid(input);
            ItemInventoryJournalStatus status =
                    ItemInventoryJournalStatus.fromWireCode(
                            input.readUnsignedByte());
            Instant terminalAt = Instant.ofEpochSecond(input.readLong(),
                    input.readInt());
            byte[] terminalDigest = input.readNBytes(32);
            if (terminalDigest.length != 32) {
                throw new EOFException();
            }
            int tokenLength = input.readInt();
            if (tokenLength <= 0
                    || tokenLength
                    > ItemInventoryMutationTokenCodec.MAX_ENCODED_BYTES
                    || tokenLength != input.available()) {
                throw new IllegalArgumentException(
                        "Item inventory tombstone token is invalid");
            }
            ItemInventoryMutationToken token =
                    ItemInventoryMutationTokenCodec.decode(
                            input.readNBytes(tokenLength));
            return new ItemInventoryTerminalTombstone(commandId,
                    checkpointId, token, status, terminalDigest,
                    terminalAt);
        } catch (EOFException exception) {
            throw new IllegalArgumentException(
                    "Item inventory tombstone is truncated", exception);
        } catch (IOException | DateTimeException | ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "Item inventory tombstone is invalid", exception);
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

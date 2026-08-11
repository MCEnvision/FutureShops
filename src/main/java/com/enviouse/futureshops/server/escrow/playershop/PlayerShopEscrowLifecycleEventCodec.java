package com.enviouse.futureshops.server.escrow.playershop;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public final class PlayerShopEscrowLifecycleEventCodec {
    public static final int CURRENT_SCHEMA = 1;
    public static final int MAX_ENCODED_BYTES = 16_777_184;

    private static final int MAGIC = 0x4653504c;

    private PlayerShopEscrowLifecycleEventCodec() {
    }

    public static byte[] encode(PlayerShopEscrowLifecycleEvent event) {
        Objects.requireNonNull(event, "event");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(MAGIC);
            output.writeInt(CURRENT_SCHEMA);
            PlayerShopBinarySupport.writeUuid(output, event.eventId());
            PlayerShopBinarySupport.writeUuid(output, event.requestId());
            output.writeLong(event.expectedRevision());
            output.writeLong(event.nextRevision());
            PlayerShopBinarySupport.writeBytes(output,
                    PlayerShopExecutionSnapshotCodec.encode(event.snapshot()),
                    MAX_ENCODED_BYTES - 64);
            output.writeBoolean(event.settlementImported());
            output.flush();
            byte[] encoded = bytes.toByteArray();
            requireSize(encoded);
            return encoded;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to encode player shop lifecycle event", exception);
        }
    }

    public static PlayerShopEscrowLifecycleEvent decode(byte[] encoded) {
        byte[] copy = Objects.requireNonNull(encoded, "encoded").clone();
        requireSize(copy);
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(copy))) {
            if (input.readInt() != MAGIC) {
                throw new IllegalArgumentException("Player shop lifecycle magic is invalid");
            }
            if (input.readInt() != CURRENT_SCHEMA) {
                throw new IllegalArgumentException("Player shop lifecycle schema is unsupported");
            }
            UUID eventId = PlayerShopBinarySupport.readUuid(input,
                    "lifecycle event id");
            UUID requestId = PlayerShopBinarySupport.readUuid(input,
                    "lifecycle request id");
            long expectedRevision = input.readLong();
            long nextRevision = input.readLong();
            PlayerShopExecutionSnapshot snapshot =
                    PlayerShopExecutionSnapshotCodec.decode(
                            PlayerShopBinarySupport.readBytes(input,
                                    MAX_ENCODED_BYTES - 64,
                                    "lifecycle snapshot"));
            boolean settlementImported = input.readBoolean();
            PlayerShopBinarySupport.requireFinished(input, "lifecycle event");
            PlayerShopEscrowLifecycleEvent event =
                    new PlayerShopEscrowLifecycleEvent(eventId, requestId,
                            expectedRevision, nextRevision, snapshot,
                            settlementImported);
            if (!Arrays.equals(copy, encode(event))) {
                throw new IllegalArgumentException("Player shop lifecycle event is not canonical");
            }
            return event;
        } catch (EOFException exception) {
            throw new IllegalArgumentException("Player shop lifecycle event is truncated", exception);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException invalid) {
                throw invalid;
            }
            throw new IllegalArgumentException("Player shop lifecycle event is invalid", exception);
        }
    }

    private static void requireSize(byte[] encoded) {
        if (encoded.length == 0 || encoded.length > MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException("Player shop lifecycle event size is invalid");
        }
    }
}

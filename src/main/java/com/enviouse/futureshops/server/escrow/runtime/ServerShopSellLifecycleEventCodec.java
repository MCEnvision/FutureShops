package com.enviouse.futureshops.server.escrow.runtime;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

public final class ServerShopSellLifecycleEventCodec {
    public static final int CURRENT_SCHEMA = 1;
    public static final int MAX_ENCODED_BYTES =
            EscrowJournalEventCodec.MAX_BODY_BYTES;

    private static final int MAGIC = 0x4653534C;
    private static final int PREPARE = 1;
    private static final int ABORT = 2;
    private static final int COMMIT = 3;

    private ServerShopSellLifecycleEventCodec() {
    }

    public static byte[] encode(ServerShopSellLifecycleEvent event) {
        Objects.requireNonNull(event, "event");
        try {
            byte[][] components;
            int tag;
            if (event instanceof ServerShopSellLifecycleEvent.Prepare value) {
                tag = PREPARE;
                components = new byte[][]{
                        ServerShopSellIntentCodec.encode(value.intent())};
            } else if (event instanceof ServerShopSellLifecycleEvent.Abort value) {
                tag = ABORT;
                components = new byte[][]{
                        ServerShopSellIntentCodec.encode(
                                value.expectedIntent()),
                        ServerShopSellIntentCodec.encode(
                                value.terminalIntent())};
            } else if (event instanceof ServerShopSellLifecycleEvent.Commit value) {
                tag = COMMIT;
                components = new byte[][]{
                        ServerShopSellIntentCodec.encode(
                                value.completedIntent()),
                        ServerShopSellCommitCodec.encode(value.commit())};
            } else {
                throw new IllegalArgumentException(
                        "Unknown server shop sell lifecycle event");
            }
            requireEnvelopeSize(components);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(MAGIC);
            output.writeInt(CURRENT_SCHEMA);
            output.writeByte(tag);
            for (byte[] component : components) {
                output.writeInt(component.length);
                output.write(component);
            }
            output.flush();
            byte[] encoded = bytes.toByteArray();
            requireSize(encoded);
            return encoded;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to encode server shop sell lifecycle event",
                    exception);
        }
    }

    public static ServerShopSellLifecycleEvent decode(byte[] encoded) {
        byte[] copy = Objects.requireNonNull(encoded, "encoded").clone();
        requireSize(copy);
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(copy))) {
            if (input.readInt() != MAGIC) {
                throw new IllegalArgumentException(
                        "Server shop sell lifecycle magic is invalid");
            }
            if (input.readInt() != CURRENT_SCHEMA) {
                throw new IllegalArgumentException(
                        "Server shop sell lifecycle schema is unsupported");
            }
            int tag = input.readUnsignedByte();
            ServerShopSellLifecycleEvent result = switch (tag) {
                case PREPARE -> new ServerShopSellLifecycleEvent.Prepare(
                        ServerShopSellIntentCodec.decode(
                                readComponent(input,
                                        ServerShopSellIntentCodec
                                                .MAX_ENCODED_BYTES)));
                case ABORT -> new ServerShopSellLifecycleEvent.Abort(
                        ServerShopSellIntentCodec.decode(
                                readComponent(input,
                                        ServerShopSellIntentCodec
                                                .MAX_ENCODED_BYTES)),
                        ServerShopSellIntentCodec.decode(
                                readComponent(input,
                                        ServerShopSellIntentCodec
                                                .MAX_ENCODED_BYTES)));
                case COMMIT -> new ServerShopSellLifecycleEvent.Commit(
                        ServerShopSellIntentCodec.decode(
                                readComponent(input,
                                        ServerShopSellIntentCodec
                                                .MAX_ENCODED_BYTES)),
                        ServerShopSellCommitCodec.decode(
                                readComponent(input,
                                        ServerShopSellCommitCodec
                                                .MAX_ENCODED_BYTES)));
                default -> throw new IllegalArgumentException(
                        "Server shop sell lifecycle tag is invalid");
            };
            if (input.read() != -1 || !Arrays.equals(copy, encode(result))) {
                throw new IllegalArgumentException(
                        "Server shop sell lifecycle encoding is not canonical");
            }
            return result;
        } catch (EOFException exception) {
            throw new IllegalArgumentException(
                    "Server shop sell lifecycle event is truncated",
                    exception);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException invalid) {
                throw invalid;
            }
            throw new IllegalArgumentException(
                    "Server shop sell lifecycle event is invalid",
                    exception);
        }
    }

    private static byte[] readComponent(
            DataInputStream input,
            int maximum
    ) throws IOException {
        int length = input.readInt();
        if (length <= 0 || length > maximum
                || length > input.available()) {
            throw new IllegalArgumentException(
                    "Server shop sell lifecycle component size is invalid");
        }
        byte[] result = input.readNBytes(length);
        if (result.length != length) {
            throw new EOFException(
                    "Server shop sell lifecycle component is truncated");
        }
        return result;
    }

    private static void requireEnvelopeSize(byte[][] components) {
        long size = Integer.BYTES * 2L + 1L;
        for (byte[] component : components) {
            size = Math.addExact(size,
                    Math.addExact(Integer.BYTES, component.length));
        }
        if (size > MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException(
                    "Server shop sell lifecycle event is too large");
        }
    }

    private static void requireSize(byte[] encoded) {
        if (encoded.length == 0
                || encoded.length > MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException(
                    "Server shop sell lifecycle size is invalid");
        }
    }
}

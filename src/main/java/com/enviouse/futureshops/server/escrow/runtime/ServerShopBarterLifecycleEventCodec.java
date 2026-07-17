package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.stock.StockMutationCommandCodec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

public final class ServerShopBarterLifecycleEventCodec {
    public static final int CURRENT_SCHEMA = 1;
    public static final int MAX_ENCODED_BYTES =
            EscrowJournalEventCodec.MAX_BODY_BYTES;

    private static final int MAGIC = 0x4653424C;
    private static final int PREPARE = 1;
    private static final int ABORT = 2;
    private static final int COMMIT = 3;

    private ServerShopBarterLifecycleEventCodec() {
    }

    public static byte[] encode(ServerShopBarterLifecycleEvent event) {
        Objects.requireNonNull(event, "event");
        try {
            byte[][] components;
            int tag;
            if (event instanceof ServerShopBarterLifecycleEvent.Prepare value) {
                tag = PREPARE;
                components = new byte[][]{
                        ServerShopBarterIntentCodec.encode(value.intent()),
                        StockMutationCommandCodec.encode(
                                value.stockReservation())};
            } else if (event instanceof ServerShopBarterLifecycleEvent.Abort value) {
                tag = ABORT;
                components = new byte[][]{
                        ServerShopBarterIntentCodec.encode(
                                value.terminalIntent()),
                        StockMutationCommandCodec.encode(
                                value.stockRelease())};
            } else if (event instanceof ServerShopBarterLifecycleEvent.Commit value) {
                tag = COMMIT;
                components = new byte[][]{
                        ServerShopBarterIntentCodec.encode(
                                value.completedIntent()),
                        ServerShopBarterCommitCodec.encode(value.commit())};
            } else {
                throw new IllegalArgumentException(
                        "Unknown server shop barter lifecycle event");
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
                    "Unable to encode server shop barter lifecycle event",
                    exception);
        }
    }

    public static ServerShopBarterLifecycleEvent decode(byte[] encoded) {
        byte[] copy = Objects.requireNonNull(encoded, "encoded").clone();
        requireSize(copy);
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(copy))) {
            if (input.readInt() != MAGIC) {
                throw new IllegalArgumentException(
                        "Server shop barter lifecycle magic is invalid");
            }
            if (input.readInt() != CURRENT_SCHEMA) {
                throw new IllegalArgumentException(
                        "Server shop barter lifecycle schema is unsupported");
            }
            int tag = input.readUnsignedByte();
            ServerShopBarterLifecycleEvent result = switch (tag) {
                case PREPARE -> new ServerShopBarterLifecycleEvent.Prepare(
                        ServerShopBarterIntentCodec.decode(
                                readComponent(input,
                                        ServerShopBarterIntentCodec
                                                .MAX_ENCODED_BYTES)),
                        requireReserve(StockMutationCommandCodec.decode(
                                readComponent(input,
                                        StockMutationCommandCodec
                                                .MAX_ENCODED_BYTES))));
                case ABORT -> new ServerShopBarterLifecycleEvent.Abort(
                        ServerShopBarterIntentCodec.decode(
                                readComponent(input,
                                        ServerShopBarterIntentCodec
                                                .MAX_ENCODED_BYTES)),
                        requireResolve(StockMutationCommandCodec.decode(
                                readComponent(input,
                                        StockMutationCommandCodec
                                                .MAX_ENCODED_BYTES))));
                case COMMIT -> new ServerShopBarterLifecycleEvent.Commit(
                        ServerShopBarterIntentCodec.decode(
                                readComponent(input,
                                        ServerShopBarterIntentCodec
                                                .MAX_ENCODED_BYTES)),
                        ServerShopBarterCommitCodec.decode(
                                readComponent(input,
                                        ServerShopBarterCommitCodec
                                                .MAX_ENCODED_BYTES)));
                default -> throw new IllegalArgumentException(
                        "Server shop barter lifecycle tag is invalid");
            };
            if (input.read() != -1 || !Arrays.equals(copy, encode(result))) {
                throw new IllegalArgumentException(
                        "Server shop barter lifecycle encoding is not canonical");
            }
            return result;
        } catch (EOFException exception) {
            throw new IllegalArgumentException(
                    "Server shop barter lifecycle event is truncated",
                    exception);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException invalid) {
                throw invalid;
            }
            throw new IllegalArgumentException(
                    "Server shop barter lifecycle event is invalid",
                    exception);
        }
    }

    private static com.enviouse.futureshops.server.escrow.stock
            .StockMutationCommand.ReserveBatch requireReserve(
            com.enviouse.futureshops.server.escrow.stock
                    .StockMutationCommand command
    ) {
        if (command instanceof com.enviouse.futureshops.server.escrow.stock
                .StockMutationCommand.ReserveBatch value) {
            return value;
        }
        throw new IllegalArgumentException(
                "Server shop barter reserve command is invalid");
    }

    private static com.enviouse.futureshops.server.escrow.stock
            .StockMutationCommand.ResolveBatch requireResolve(
            com.enviouse.futureshops.server.escrow.stock
                    .StockMutationCommand command
    ) {
        if (command instanceof com.enviouse.futureshops.server.escrow.stock
                .StockMutationCommand.ResolveBatch value) {
            return value;
        }
        throw new IllegalArgumentException(
                "Server shop barter resolve command is invalid");
    }

    private static byte[] readComponent(
            DataInputStream input,
            int maximum
    ) throws IOException {
        int length = input.readInt();
        if (length <= 0 || length > maximum
                || length > input.available()) {
            throw new IllegalArgumentException(
                    "Server shop barter lifecycle component size is invalid");
        }
        byte[] result = input.readNBytes(length);
        if (result.length != length) {
            throw new EOFException(
                    "Server shop barter lifecycle component is truncated");
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
                    "Server shop barter lifecycle event is too large");
        }
    }

    private static void requireSize(byte[] encoded) {
        if (encoded.length == 0
                || encoded.length > MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException(
                    "Server shop barter lifecycle size is invalid");
        }
    }
}

package com.enviouse.futureshops.server.market.auction.escrow;

import com.enviouse.futureshops.server.escrow.runtime.EscrowJournalEventCodec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

public final class AuctionEscrowLifecycleEventCodec {
    public static final int CURRENT_SCHEMA = 1;
    public static final int MAX_ENCODED_BYTES =
            EscrowJournalEventCodec.MAX_BODY_BYTES;

    private static final int MAGIC = 0x41454C45;
    private static final int PREPARE = 1;
    private static final int ABORT = 2;
    private static final int COMMIT = 3;

    private AuctionEscrowLifecycleEventCodec() {
    }

    public static byte[] encode(AuctionEscrowLifecycleEvent event) {
        Objects.requireNonNull(event, "event");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(MAGIC);
            output.writeInt(CURRENT_SCHEMA);
            if (event instanceof AuctionEscrowLifecycleEvent.Prepare value) {
                output.writeByte(PREPARE);
                writeComponent(output,
                        AuctionCreateEscrowIntentCodec.encode(
                                value.intent()));
            } else if (event
                    instanceof AuctionEscrowLifecycleEvent.Abort value) {
                output.writeByte(ABORT);
                writeComponent(output,
                        AuctionCreateEscrowIntentCodec.encode(
                                value.expectedIntent()));
                writeComponent(output,
                        AuctionCreateEscrowIntentCodec.encode(
                                value.terminalIntent()));
            } else if (event
                    instanceof AuctionEscrowLifecycleEvent.Commit value) {
                output.writeByte(COMMIT);
                output.writeBoolean(value.completedIntent().isPresent());
                if (value.completedIntent().isPresent()) {
                    writeComponent(output,
                            AuctionCreateEscrowIntentCodec.encode(
                                    value.completedIntent()
                                            .orElseThrow()));
                }
                writeComponent(output, AuctionEscrowCommitCodec.encode(
                        value.commit()));
            } else {
                throw new IllegalArgumentException(
                        "Unknown auction escrow lifecycle event");
            }
            output.flush();
            byte[] encoded = bytes.toByteArray();
            requireSize(encoded);
            return encoded;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to encode auction escrow lifecycle event",
                    exception);
        }
    }

    public static AuctionEscrowLifecycleEvent decode(byte[] encoded) {
        byte[] copy = Objects.requireNonNull(encoded, "encoded").clone();
        requireSize(copy);
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(copy))) {
            if (input.readInt() != MAGIC) {
                throw invalid(
                        "Auction escrow lifecycle magic is invalid");
            }
            if (input.readInt() != CURRENT_SCHEMA) {
                throw invalid(
                        "Auction escrow lifecycle schema is unsupported");
            }
            AuctionEscrowLifecycleEvent result = switch (
                    input.readUnsignedByte()) {
                case PREPARE -> new AuctionEscrowLifecycleEvent.Prepare(
                        AuctionCreateEscrowIntentCodec.decode(
                                readComponent(input)));
                case ABORT -> new AuctionEscrowLifecycleEvent.Abort(
                        AuctionCreateEscrowIntentCodec.decode(
                                readComponent(input)),
                        AuctionCreateEscrowIntentCodec.decode(
                                readComponent(input)));
                case COMMIT -> new AuctionEscrowLifecycleEvent.Commit(
                        input.readBoolean()
                                ? java.util.Optional.of(
                                AuctionCreateEscrowIntentCodec.decode(
                                        readComponent(input)))
                                : java.util.Optional.empty(),
                        AuctionEscrowCommitCodec.decode(
                                readComponent(input)));
                default -> throw invalid(
                        "Auction escrow lifecycle tag is invalid");
            };
            if (input.read() != -1 || !Arrays.equals(copy,
                    encode(result))) {
                throw invalid(
                        "Auction escrow lifecycle encoding is not canonical");
            }
            return result;
        } catch (EOFException exception) {
            throw invalid(
                    "Auction escrow lifecycle event is truncated",
                    exception);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException invalid) {
                throw invalid;
            }
            throw invalid(
                    "Auction escrow lifecycle event is invalid",
                    exception);
        }
    }

    private static void writeComponent(
            DataOutputStream output,
            byte[] component
    ) throws IOException {
        if (component.length == 0
                || component.length > MAX_ENCODED_BYTES) {
            throw invalid(
                    "Auction escrow lifecycle component is too large");
        }
        long projected = Math.addExact((long) component.length,
                Integer.BYTES);
        if (projected > MAX_ENCODED_BYTES) {
            throw invalid(
                    "Auction escrow lifecycle component is too large");
        }
        output.writeInt(component.length);
        output.write(component);
    }

    private static byte[] readComponent(DataInputStream input)
            throws IOException {
        int length = input.readInt();
        if (length <= 0 || length > MAX_ENCODED_BYTES
                || length > input.available()) {
            throw invalid(
                    "Auction escrow lifecycle component size is invalid");
        }
        byte[] result = input.readNBytes(length);
        if (result.length != length) {
            throw new EOFException(
                    "Auction escrow lifecycle component is truncated");
        }
        return result;
    }

    private static void requireSize(byte[] encoded) {
        if (encoded.length == 0
                || encoded.length > MAX_ENCODED_BYTES) {
            throw invalid("Auction escrow lifecycle size is invalid");
        }
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }

    private static IllegalArgumentException invalid(
            String message,
            Throwable cause
    ) {
        return new IllegalArgumentException(message, cause);
    }
}

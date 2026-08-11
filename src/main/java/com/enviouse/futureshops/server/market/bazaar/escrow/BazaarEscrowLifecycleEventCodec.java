package com.enviouse.futureshops.server.market.bazaar.escrow;

import com.enviouse.futureshops.server.escrow.runtime.EscrowJournalEventCodec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;

public final class BazaarEscrowLifecycleEventCodec {
    public static final int CURRENT_SCHEMA = 1;
    public static final int MAX_ENCODED_BYTES =
            EscrowJournalEventCodec.MAX_BODY_BYTES;

    private static final int MAGIC = 0x425A4C45;
    private static final int PREPARE = 1;
    private static final int RESOLVE = 2;
    private static final int COMMIT = 3;

    private BazaarEscrowLifecycleEventCodec() {
    }

    public static byte[] encode(BazaarEscrowLifecycleEvent event) {
        Objects.requireNonNull(event, "event");
        byte[] payload = payloadBytes(event);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(MAGIC);
            output.writeInt(CURRENT_SCHEMA);
            BazaarEscrowBinarySupport.writeBytes(output, payload,
                    MAX_ENCODED_BYTES);
            BazaarEscrowBinarySupport.writeText(output,
                    fingerprint(payload));
            output.flush();
            byte[] result = bytes.toByteArray();
            requireSize(result);
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to encode Bazaar lifecycle event", exception);
        }
    }

    public static BazaarEscrowLifecycleEvent decode(byte[] encoded) {
        byte[] copy = Objects.requireNonNull(encoded, "encoded").clone();
        requireSize(copy);
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(copy))) {
            if (input.readInt() != MAGIC) {
                throw invalid("Bazaar lifecycle event magic is invalid");
            }
            if (input.readInt() != CURRENT_SCHEMA) {
                throw invalid(
                        "Bazaar lifecycle event schema is unsupported");
            }
            byte[] payload = BazaarEscrowBinarySupport.readBytes(input,
                    MAX_ENCODED_BYTES);
            String storedFingerprint = BazaarEscrowBinarySupport.readText(
                    input, 64, false);
            if (input.read() != -1
                    || !storedFingerprint.equals(fingerprint(payload))) {
                throw invalid("Bazaar lifecycle event digest is invalid");
            }
            BazaarEscrowLifecycleEvent result = decodePayload(payload);
            if (!Arrays.equals(copy, encode(result))) {
                throw invalid(
                        "Bazaar lifecycle event encoding is not canonical");
            }
            return result;
        } catch (EOFException exception) {
            throw invalid("Bazaar lifecycle event is truncated", exception);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException invalid) {
                throw invalid;
            }
            throw invalid("Bazaar lifecycle event is invalid", exception);
        }
    }

    private static byte[] payloadBytes(BazaarEscrowLifecycleEvent event) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            if (event instanceof BazaarEscrowLifecycleEvent.Prepare value) {
                output.writeInt(PREPARE);
                writeIntent(output, value.intent());
            } else if (event instanceof BazaarEscrowLifecycleEvent.Resolve
                    value) {
                output.writeInt(RESOLVE);
                writeIntent(output, value.expectedIntent());
                writeIntent(output, value.resolvedIntent());
            } else if (event instanceof BazaarEscrowLifecycleEvent.Commit
                    value) {
                output.writeInt(COMMIT);
                output.writeBoolean(value.completedIntent().isPresent());
                if (value.completedIntent().isPresent()) {
                    writeIntent(output,
                            value.completedIntent().orElseThrow());
                }
                BazaarEscrowBinarySupport.writeBytes(output,
                        BazaarEscrowCommitCodec.encode(value.commit()),
                        BazaarEscrowCommitCodec.MAX_ENCODED_BYTES);
            } else {
                throw invalid("Bazaar lifecycle event type is invalid");
            }
            output.flush();
            byte[] result = bytes.toByteArray();
            requireSize(result);
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to encode Bazaar lifecycle event payload",
                    exception);
        }
    }

    private static BazaarEscrowLifecycleEvent decodePayload(byte[] payload)
            throws IOException {
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(payload))) {
            int type = input.readInt();
            BazaarEscrowLifecycleEvent event;
            if (type == PREPARE) {
                event = new BazaarEscrowLifecycleEvent.Prepare(
                        readIntent(input));
            } else if (type == RESOLVE) {
                event = new BazaarEscrowLifecycleEvent.Resolve(
                        readIntent(input), readIntent(input));
            } else if (type == COMMIT) {
                Optional<BazaarCreateEscrowIntent> intent =
                        input.readBoolean()
                                ? Optional.of(readIntent(input))
                                : Optional.empty();
                BazaarEscrowCommit commit = BazaarEscrowCommitCodec.decode(
                        BazaarEscrowBinarySupport.readBytes(input,
                                BazaarEscrowCommitCodec.MAX_ENCODED_BYTES));
                event = new BazaarEscrowLifecycleEvent.Commit(intent,
                        commit);
            } else {
                throw invalid("Bazaar lifecycle event type is invalid");
            }
            if (input.read() != -1) {
                throw invalid(
                        "Bazaar lifecycle event payload has trailing data");
            }
            return event;
        }
    }

    private static void writeIntent(
            DataOutputStream output,
            BazaarCreateEscrowIntent intent
    ) throws IOException {
        BazaarEscrowBinarySupport.writeBytes(output,
                BazaarCreateEscrowIntentCodec.encode(intent),
                BazaarCreateEscrowIntentCodec.MAX_ENCODED_BYTES);
    }

    private static BazaarCreateEscrowIntent readIntent(
            DataInputStream input
    ) throws IOException {
        return BazaarCreateEscrowIntentCodec.decode(
                BazaarEscrowBinarySupport.readBytes(input,
                        BazaarCreateEscrowIntentCodec.MAX_ENCODED_BYTES));
    }

    private static String fingerprint(byte[] payload) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(payload));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable",
                    exception);
        }
    }

    private static void requireSize(byte[] value) {
        if (value.length == 0 || value.length > MAX_ENCODED_BYTES) {
            throw invalid("Bazaar lifecycle event size is invalid");
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

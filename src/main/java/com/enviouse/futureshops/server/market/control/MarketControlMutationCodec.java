package com.enviouse.futureshops.server.market.control;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Objects;

public final class MarketControlMutationCodec {
    public static final int CURRENT_SCHEMA = 1;
    public static final int MAX_ENCODED_BYTES = 16_384;

    private static final int MAGIC = 0x46534D55;
    private static final int DIGEST_BYTES = 32;

    private MarketControlMutationCodec() {
    }

    public static byte[] encode(MarketControlMutation mutation) {
        Objects.requireNonNull(mutation, "mutation");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(MAGIC);
            output.writeInt(CURRENT_SCHEMA);
            MarketControlBinarySupport.writeText(output,
                    mutation.previousStateFingerprint(), 64);
            MarketControlBinarySupport.writeText(output,
                    mutation.nextStateFingerprint(), 64);
            MarketControlBinarySupport.writeModule(output,
                    mutation.previousModule());
            MarketControlBinarySupport.writeModule(output,
                    mutation.nextModule());
            MarketControlBinarySupport.writeAudit(output,
                    mutation.auditEntry());
            output.flush();
            return appendDigest(bytes.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to encode market control mutation",
                    exception);
        }
    }

    public static MarketControlMutation decode(byte[] encoded) {
        byte[] copy = requireAndVerify(encoded);
        byte[] payload = Arrays.copyOf(copy,
                copy.length - DIGEST_BYTES);
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(payload))) {
            if (input.readInt() != MAGIC) {
                throw invalid(
                        "Market control mutation magic is invalid");
            }
            if (input.readInt() != CURRENT_SCHEMA) {
                throw invalid(
                        "Market control mutation schema is unsupported");
            }
            MarketControlMutation mutation = new MarketControlMutation(
                    MarketControlBinarySupport.readText(input, 64),
                    MarketControlBinarySupport.readText(input, 64),
                    MarketControlBinarySupport.readModule(input),
                    MarketControlBinarySupport.readModule(input),
                    MarketControlBinarySupport.readAudit(input));
            if (input.read() != -1) {
                throw invalid(
                        "Market control mutation has trailing data");
            }
            if (!Arrays.equals(copy, encode(mutation))) {
                throw invalid(
                        "Market control mutation encoding is not canonical");
            }
            return mutation;
        } catch (EOFException exception) {
            throw new IllegalArgumentException(
                    "Market control mutation is truncated", exception);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException invalid) {
                throw invalid;
            }
            throw new IllegalArgumentException(
                    "Market control mutation is invalid", exception);
        }
    }

    private static byte[] appendDigest(byte[] payload) {
        int total = Math.addExact(payload.length, DIGEST_BYTES);
        if (payload.length == 0 || total > MAX_ENCODED_BYTES) {
            throw invalid("Market control mutation size is invalid");
        }
        byte[] encoded = Arrays.copyOf(payload, total);
        byte[] digest = digest(payload);
        System.arraycopy(digest, 0, encoded, payload.length,
                DIGEST_BYTES);
        return encoded;
    }

    private static byte[] requireAndVerify(byte[] encoded) {
        byte[] copy = Objects.requireNonNull(encoded, "encoded").clone();
        if (copy.length <= DIGEST_BYTES
                || copy.length > MAX_ENCODED_BYTES) {
            throw invalid("Market control mutation size is invalid");
        }
        int payloadLength = copy.length - DIGEST_BYTES;
        byte[] expected = digest(Arrays.copyOf(copy, payloadLength));
        byte[] actual = Arrays.copyOfRange(copy, payloadLength,
                copy.length);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw invalid(
                    "Market control mutation digest is invalid");
        }
        return copy;
    }

    private static byte[] digest(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "Market control hashing is unavailable", exception);
        }
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}

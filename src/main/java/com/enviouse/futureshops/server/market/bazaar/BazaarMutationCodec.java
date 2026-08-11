package com.enviouse.futureshops.server.market.bazaar;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class BazaarMutationCodec {
    public static final int CURRENT_SCHEMA = 1;
    public static final int MAX_ENCODED_BYTES = 16_777_184;

    private static final int MAGIC = 0x4653424D;
    private static final int DIGEST_BYTES = 32;
    private static final int REQUEST = 1;
    private static final int LIFECYCLE = 2;
    private static final int MAX_COMPONENT_BYTES =
            MAX_ENCODED_BYTES - 256;

    private BazaarMutationCodec() {
    }

    public static byte[] encode(BazaarMutation mutation) {
        Objects.requireNonNull(mutation, "mutation");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(MAGIC);
            output.writeInt(CURRENT_SCHEMA);
            writeUuid(output, mutation.mutationId());
            writeText(output, mutation.previousSnapshotFingerprint(), 64);
            writeText(output, mutation.nextSnapshotFingerprint(), 64);
            byte[] component;
            if (mutation.requestReceipt().isPresent()) {
                output.writeInt(REQUEST);
                component = BazaarOrderBookSnapshotCodec.encodeReceipt(
                        mutation.requestReceipt().orElseThrow());
            } else {
                output.writeInt(LIFECYCLE);
                component = BazaarOrderBookSnapshotCodec
                        .encodeLifecycleCommand(
                                mutation.lifecycleCommand().orElseThrow());
            }
            writeComponent(output, component);
            output.flush();
            return appendDigest(bytes.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to encode Bazaar mutation", exception);
        }
    }

    public static BazaarMutation decode(byte[] encoded) {
        byte[] copy = requireAndVerify(encoded);
        byte[] payload = Arrays.copyOf(copy,
                copy.length - DIGEST_BYTES);
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(payload))) {
            if (input.readInt() != MAGIC) {
                throw invalid("Bazaar mutation magic is invalid");
            }
            if (input.readInt() != CURRENT_SCHEMA) {
                throw invalid("Bazaar mutation schema is unsupported");
            }
            UUID mutationId = readUuid(input);
            String previousFingerprint = readText(input, 64);
            String nextFingerprint = readText(input, 64);
            int type = input.readInt();
            byte[] component = readComponent(input);
            BazaarMutation mutation;
            if (type == REQUEST) {
                mutation = new BazaarMutation(mutationId,
                        previousFingerprint, nextFingerprint,
                        Optional.of(BazaarOrderBookSnapshotCodec
                                .decodeReceipt(component)),
                        Optional.empty());
            } else if (type == LIFECYCLE) {
                mutation = new BazaarMutation(mutationId,
                        previousFingerprint, nextFingerprint,
                        Optional.empty(),
                        Optional.of(BazaarOrderBookSnapshotCodec
                                .decodeLifecycleCommand(component)));
            } else {
                throw invalid("Bazaar mutation type is invalid");
            }
            if (input.read() != -1) {
                throw invalid("Bazaar mutation has trailing data");
            }
            if (!Arrays.equals(copy, encode(mutation))) {
                throw invalid("Bazaar mutation encoding is not canonical");
            }
            return mutation;
        } catch (EOFException exception) {
            throw new IllegalArgumentException(
                    "Bazaar mutation is truncated", exception);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException invalid) {
                throw invalid;
            }
            throw new IllegalArgumentException(
                    "Bazaar mutation is invalid", exception);
        }
    }

    private static void writeComponent(DataOutputStream output,
                                       byte[] component) throws IOException {
        if (component.length == 0
                || component.length > MAX_COMPONENT_BYTES) {
            throw invalid("Bazaar mutation component size is invalid");
        }
        output.writeInt(component.length);
        output.write(component);
    }

    private static byte[] readComponent(DataInputStream input)
            throws IOException {
        int size = input.readInt();
        if (size <= 0 || size > MAX_COMPONENT_BYTES
                || size > input.available()) {
            throw invalid("Bazaar mutation component size is invalid");
        }
        byte[] component = input.readNBytes(size);
        if (component.length != size) {
            throw new EOFException("Bazaar mutation component is truncated");
        }
        return component;
    }

    private static void writeUuid(DataOutputStream output, UUID value)
            throws IOException {
        output.writeLong(value.getMostSignificantBits());
        output.writeLong(value.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream input) throws IOException {
        return new UUID(input.readLong(), input.readLong());
    }

    private static void writeText(DataOutputStream output, String value,
                                  int maximum) throws IOException {
        byte[] bytes = Objects.requireNonNull(value, "value")
                .getBytes(StandardCharsets.UTF_8);
        if (bytes.length == 0 || bytes.length > maximum) {
            throw invalid("Bazaar mutation text size is invalid");
        }
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String readText(DataInputStream input, int maximum)
            throws IOException {
        int size = input.readInt();
        if (size <= 0 || size > maximum || size > input.available()) {
            throw invalid("Bazaar mutation text size is invalid");
        }
        byte[] bytes = input.readNBytes(size);
        if (bytes.length != size) {
            throw new EOFException("Bazaar mutation text is truncated");
        }
        try {
            String value = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
            if (!Arrays.equals(bytes,
                    value.getBytes(StandardCharsets.UTF_8))) {
                throw invalid(
                        "Bazaar mutation text encoding is not canonical");
            }
            return value;
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException(
                    "Bazaar mutation text is not valid UTF8", exception);
        }
    }

    private static byte[] appendDigest(byte[] payload) {
        int total = Math.addExact(payload.length, DIGEST_BYTES);
        if (payload.length == 0 || total > MAX_ENCODED_BYTES) {
            throw invalid("Bazaar mutation size is invalid");
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
            throw invalid("Bazaar mutation size is invalid");
        }
        int payloadLength = copy.length - DIGEST_BYTES;
        byte[] expected = digest(Arrays.copyOf(copy, payloadLength));
        byte[] actual = Arrays.copyOfRange(copy, payloadLength,
                copy.length);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw invalid("Bazaar mutation digest is invalid");
        }
        return copy;
    }

    private static byte[] digest(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "Bazaar hashing is unavailable", exception);
        }
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}

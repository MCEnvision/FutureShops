package com.enviouse.futureshops.server.escrow.playershop;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

final class PlayerShopBinarySupport {
    private static final UUID ZERO_UUID = new UUID(0L, 0L);

    private PlayerShopBinarySupport() {
    }

    static UUID requireUuid(UUID value, String label) {
        Objects.requireNonNull(value, label);
        if (ZERO_UUID.equals(value)) {
            throw new IllegalArgumentException("Player shop " + label + " is invalid");
        }
        return value;
    }

    static String requireString(String value, int maximum, String label) {
        String normalized = Objects.requireNonNull(value, label).strip();
        if (normalized.isEmpty() || normalized.length() > maximum) {
            throw new IllegalArgumentException("Player shop " + label + " is invalid");
        }
        return normalized;
    }

    static String optionalString(String value, int maximum, String label) {
        String normalized = Objects.requireNonNull(value, label).strip();
        if (normalized.length() > maximum) {
            throw new IllegalArgumentException("Player shop " + label + " is invalid");
        }
        return normalized;
    }

    static byte[] requireBytes(byte[] value, int maximum, String label) {
        byte[] copy = Objects.requireNonNull(value, label).clone();
        if (copy.length == 0 || copy.length > maximum) {
            throw new IllegalArgumentException("Player shop " + label + " is invalid");
        }
        return copy;
    }

    static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA 256 is unavailable", exception);
        }
    }

    static UUID deterministicUuid(String namespace, UUID requestId, String key) {
        requireString(namespace, 128, "UUID namespace");
        requireUuid(requestId, "request id");
        String material = "futureshops player shop " + namespace + "\u0000"
                + requestId + "\u0000" + Objects.requireNonNull(key, "key");
        return UUID.nameUUIDFromBytes(material.getBytes(StandardCharsets.UTF_8));
    }

    static void writeUuid(DataOutputStream output, UUID value) throws IOException {
        output.writeLong(value.getMostSignificantBits());
        output.writeLong(value.getLeastSignificantBits());
    }

    static UUID readUuid(DataInputStream input, String label) throws IOException {
        return requireUuid(new UUID(input.readLong(), input.readLong()), label);
    }

    static UUID readRawUuid(DataInputStream input) throws IOException {
        return new UUID(input.readLong(), input.readLong());
    }

    static void writeString(DataOutputStream output, String value, int maximum)
            throws IOException {
        String normalized = requireString(value, maximum, "encoded text");
        byte[] encoded = normalized.getBytes(StandardCharsets.UTF_8);
        if (encoded.length > maximum * 4) {
            throw new IllegalArgumentException("Player shop encoded text is too large");
        }
        output.writeInt(encoded.length);
        output.write(encoded);
    }

    static void writeOptionalString(DataOutputStream output, String value, int maximum)
            throws IOException {
        String normalized = optionalString(value, maximum, "encoded text");
        byte[] encoded = normalized.getBytes(StandardCharsets.UTF_8);
        if (encoded.length > maximum * 4) {
            throw new IllegalArgumentException("Player shop encoded text is too large");
        }
        output.writeInt(encoded.length);
        output.write(encoded);
    }

    static String readString(DataInputStream input, int maximum, String label)
            throws IOException {
        int length = input.readInt();
        if (length <= 0 || length > maximum * 4 || length > input.available()) {
            throw new IllegalArgumentException("Player shop " + label + " size is invalid");
        }
        byte[] encoded = input.readNBytes(length);
        if (encoded.length != length) {
            throw new EOFException("Player shop " + label + " is truncated");
        }
        return requireString(new String(encoded, StandardCharsets.UTF_8), maximum, label);
    }

    static String readOptionalString(DataInputStream input, int maximum, String label)
            throws IOException {
        int length = input.readInt();
        if (length < 0 || length > maximum * 4 || length > input.available()) {
            throw new IllegalArgumentException("Player shop " + label + " size is invalid");
        }
        byte[] encoded = input.readNBytes(length);
        if (encoded.length != length) {
            throw new EOFException("Player shop " + label + " is truncated");
        }
        return optionalString(new String(encoded, StandardCharsets.UTF_8), maximum, label);
    }

    static void writeBytes(DataOutputStream output, byte[] value, int maximum)
            throws IOException {
        byte[] copy = requireBytes(value, maximum, "encoded bytes");
        output.writeInt(copy.length);
        output.write(copy);
    }

    static byte[] readBytes(DataInputStream input, int maximum, String label)
            throws IOException {
        int length = input.readInt();
        if (length <= 0 || length > maximum || length > input.available()) {
            throw new IllegalArgumentException("Player shop " + label + " size is invalid");
        }
        byte[] value = input.readNBytes(length);
        if (value.length != length) {
            throw new EOFException("Player shop " + label + " is truncated");
        }
        return value;
    }

    static void writeOptionalBytes(DataOutputStream output, byte[] value,
                                   int maximum) throws IOException {
        byte[] copy = Objects.requireNonNull(value, "value").clone();
        if (copy.length > maximum) {
            throw new IllegalArgumentException("Player shop encoded bytes are too large");
        }
        output.writeInt(copy.length);
        output.write(copy);
    }

    static byte[] readOptionalBytes(DataInputStream input, int maximum,
                                    String label) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > maximum || length > input.available()) {
            throw new IllegalArgumentException("Player shop " + label + " size is invalid");
        }
        byte[] value = input.readNBytes(length);
        if (value.length != length) {
            throw new EOFException("Player shop " + label + " is truncated");
        }
        return value;
    }

    static <E extends Enum<E>> E readEnum(
            DataInputStream input,
            E[] values,
            String label
    ) throws IOException {
        int ordinal = input.readUnsignedByte();
        if (ordinal >= values.length) {
            throw new IllegalArgumentException("Player shop " + label + " is invalid");
        }
        return values[ordinal];
    }

    static void requireFinished(DataInputStream input, String label) throws IOException {
        if (input.read() != -1) {
            throw new IllegalArgumentException("Player shop " + label + " has trailing data");
        }
    }
}

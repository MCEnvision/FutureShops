package com.enviouse.futureshops.server.escrow.item;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.UUID;

final class ItemEscrowBinaryIo {
    private ItemEscrowBinaryIo() {
    }

    static void writeUuid(DataOutputStream output, UUID value)
            throws IOException {
        output.writeLong(value.getMostSignificantBits());
        output.writeLong(value.getLeastSignificantBits());
    }

    static UUID readUuid(DataInputStream input) throws IOException {
        return new UUID(input.readLong(), input.readLong());
    }

    static void writeString(DataOutputStream output, String value)
            throws IOException {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(encoded.length);
        output.write(encoded);
    }

    static String readString(DataInputStream input, int maximumCharacters)
            throws IOException {
        int length = input.readInt();
        int maximumBytes = Math.multiplyExact(maximumCharacters, 4);
        if (length < 0 || length > maximumBytes
                || length > input.available()) {
            throw new IllegalArgumentException(
                    "Item escrow string length is invalid");
        }
        byte[] encoded = input.readNBytes(length);
        try {
            String value = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(encoded)).toString();
            if (value.length() > maximumCharacters) {
                throw new IllegalArgumentException(
                        "Item escrow string is too long");
            }
            return value;
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException(
                    "Item escrow string is malformed", exception);
        }
    }

    static void writeBytes(DataOutputStream output, byte[] value)
            throws IOException {
        output.writeInt(value.length);
        output.write(value);
    }

    static byte[] readBytes(
            DataInputStream input,
            int maximumBytes,
            boolean allowEmpty
    ) throws IOException {
        int length = input.readInt();
        if (length < 0 || (!allowEmpty && length == 0)
                || length > maximumBytes || length > input.available()) {
            throw new IllegalArgumentException(
                    "Item escrow byte array length is invalid");
        }
        byte[] value = input.readNBytes(length);
        if (value.length != length) {
            throw new EOFException("Item escrow byte array is truncated");
        }
        return value;
    }

    static byte[] readFixed(DataInputStream input, int length)
            throws IOException {
        byte[] value = input.readNBytes(length);
        if (value.length != length) {
            throw new EOFException("Item escrow fixed data is truncated");
        }
        return value;
    }

    static void writeInstant(DataOutputStream output, Instant value)
            throws IOException {
        output.writeLong(value.getEpochSecond());
        output.writeInt(value.getNano());
    }

    static Instant readInstant(DataInputStream input) throws IOException {
        long seconds = input.readLong();
        int nanos = input.readInt();
        if (nanos < 0 || nanos > 999999999) {
            throw new IllegalArgumentException(
                    "Item escrow instant nanoseconds are invalid");
        }
        try {
            return Instant.ofEpochSecond(seconds, nanos);
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException(
                    "Item escrow instant is invalid", exception);
        }
    }

    static void requireFinished(DataInputStream input, String name)
            throws IOException {
        if (input.read() != -1) {
            throw new IllegalArgumentException(name + " has trailing data");
        }
    }
}

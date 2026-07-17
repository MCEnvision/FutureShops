package com.enviouse.futureshops.server.escrow.stock;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CodingErrorAction;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.UUID;

final class StockBinaryIo {
    private StockBinaryIo() {
    }

    static void writeUuid(DataOutputStream output, UUID value) throws IOException {
        output.writeLong(value.getMostSignificantBits());
        output.writeLong(value.getLeastSignificantBits());
    }

    static UUID readUuid(DataInputStream input) throws IOException {
        return new UUID(input.readLong(), input.readLong());
    }

    static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(encoded.length);
        output.write(encoded);
    }

    static String readString(DataInputStream input, int maximumCharacters) throws IOException {
        int length = input.readInt();
        int maximumBytes = Math.multiplyExact(maximumCharacters, 4);
        if (length < 0 || length > maximumBytes || length > input.available()) {
            throw new IllegalArgumentException("Invalid stock string length");
        }
        byte[] encoded = input.readNBytes(length);
        try {
            String value = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(encoded)).toString();
            if (value.length() > maximumCharacters) {
                throw new IllegalArgumentException("Invalid stock string value");
            }
            return value;
        } catch (java.nio.charset.CharacterCodingException exception) {
            throw new IllegalArgumentException("Invalid stock string value", exception);
        }
    }

    static void writeInstant(DataOutputStream output, Instant value) throws IOException {
        output.writeLong(value.getEpochSecond());
        output.writeInt(value.getNano());
    }

    static Instant readInstant(DataInputStream input) throws IOException {
        long seconds = input.readLong();
        int nanos = input.readInt();
        try {
            return Instant.ofEpochSecond(seconds, nanos);
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("Invalid stock instant", exception);
        }
    }

    static void writeBoolean(DataOutputStream output, boolean value) throws IOException {
        output.writeByte(value ? 1 : 0);
    }

    static boolean readBoolean(DataInputStream input) throws IOException {
        int value = input.readUnsignedByte();
        if (value != 0 && value != 1) {
            throw new IllegalArgumentException("Invalid stock boolean");
        }
        return value == 1;
    }

    static <E extends Enum<E>> void writeEnum(DataOutputStream output, E value)
            throws IOException {
        writeString(output, value.name());
    }

    static <E extends Enum<E>> E readEnum(DataInputStream input, Class<E> type, String name)
            throws IOException {
        String value = readString(input, 64);
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid " + name, exception);
        }
    }

    static void requireFinished(DataInputStream input, String name) throws IOException {
        if (input.read() != -1) {
            throw new IllegalArgumentException(name + " has trailing bytes");
        }
    }
}

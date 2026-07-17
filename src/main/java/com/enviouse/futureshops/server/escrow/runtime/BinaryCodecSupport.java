package com.enviouse.futureshops.server.escrow.runtime;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

final class BinaryCodecSupport {
    private BinaryCodecSupport() {
    }

    static void writeUuid(DataOutput output, UUID value) throws IOException {
        output.writeLong(value.getMostSignificantBits());
        output.writeLong(value.getLeastSignificantBits());
    }

    static UUID readUuid(DataInput input) throws IOException {
        return new UUID(input.readLong(), input.readLong());
    }

    static void writeString(DataOutput output, String value, int maxBytes) throws IOException {
        Objects.requireNonNull(value, "value");
        byte[] encoded;
        try {
            ByteBuffer buffer = StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(value));
            encoded = new byte[buffer.remaining()];
            buffer.get(encoded);
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("String is not valid Unicode", exception);
        }
        if (encoded.length == 0 || encoded.length > maxBytes) {
            throw new IllegalArgumentException("Invalid encoded string length");
        }
        output.writeInt(encoded.length);
        output.write(encoded);
    }

    static String readString(DataInput input, int maxBytes) throws IOException {
        int size = input.readInt();
        if (size <= 0 || size > maxBytes) {
            throw new IllegalArgumentException("Invalid decoded string length");
        }
        byte[] encoded = new byte[size];
        input.readFully(encoded);
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(encoded))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("Decoded string is not valid UTF8", exception);
        }
    }

    static boolean readBoolean(DataInput input) throws IOException {
        int value = input.readUnsignedByte();
        if (value != 0 && value != 1) {
            throw new IllegalArgumentException("Decoded boolean is invalid");
        }
        return value == 1;
    }
}

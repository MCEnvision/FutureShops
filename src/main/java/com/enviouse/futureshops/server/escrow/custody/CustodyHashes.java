package com.enviouse.futureshops.server.escrow.custody;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class CustodyHashes {
    static final int HASH_BYTES = 32;

    private CustodyHashes() {
    }

    static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    static byte[] itemHash(String registryId, int count, byte[] nbt) {
        return encodeAndHash(output -> {
            writeString(output, registryId);
            output.writeInt(count);
            writeBytes(output, nbt);
        });
    }

    static byte[] evidenceHash(String adapterId, String ownerKey, String locationKey,
                               byte[] before, byte[] after, String token) {
        return encodeAndHash(output -> {
            writeString(output, adapterId);
            writeString(output, ownerKey);
            writeString(output, locationKey);
            writeBytes(output, before);
            writeBytes(output, after);
            writeString(output, token);
        });
    }

    static boolean equal(byte[] left, byte[] right) {
        return MessageDigest.isEqual(left, right);
    }

    static String hex(byte[] value) {
        return HexFormat.of().formatHex(value);
    }

    static void requireHash(byte[] value, String label) {
        if (value.length != HASH_BYTES) {
            throw new IllegalArgumentException(label + " must be a SHA-256 hash");
        }
    }

    static byte[] encodeAndHash(IoWriter writer) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                writer.write(output);
            }
            return sha256(bytes.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to hash custody data", exception);
        }
    }

    static void writeString(DataOutputStream output, String value) throws IOException {
        writeBytes(output, strictUtf8(value));
    }

    static byte[] strictUtf8(String value) {
        try {
            ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(java.nio.CharBuffer.wrap(value));
            byte[] result = new byte[encoded.remaining()];
            encoded.get(result);
            return result;
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("Custody string is not valid UTF-8", exception);
        }
    }

    static void writeBytes(DataOutputStream output, byte[] value) throws IOException {
        output.writeInt(value.length);
        output.write(value);
    }

    @FunctionalInterface
    interface IoWriter {
        void write(DataOutputStream output) throws IOException;
    }
}

package com.enviouse.futureshops.server.escrow.item.runtime;

import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationToken;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationTokenCodec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.time.DateTimeException;
import java.time.Instant;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Objects;

public final class ItemInventoryMutationAbortCodec {
    public static final int MAX_ENCODED_BYTES =
            ItemInventoryMutationTokenCodec.MAX_ENCODED_BYTES + 64;

    private static final int MAGIC = 0x494D4142;
    private static final int VERSION = 1;
    private static final int DIGEST_BYTES = 32;

    private ItemInventoryMutationAbortCodec() {
    }

    public static byte[] encode(ItemInventoryMutationAbort abort) {
        Objects.requireNonNull(abort, "abort");
        try {
            byte[] token = ItemInventoryMutationTokenCodec.encode(
                    abort.token());
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(MAGIC);
            output.writeShort(VERSION);
            output.writeInt(token.length);
            output.write(token);
            output.writeByte(abort.reason().wireCode());
            output.writeLong(abort.abortedAt().getEpochSecond());
            output.writeInt(abort.abortedAt().getNano());
            output.flush();
            byte[] payload = bytes.toByteArray();
            byte[] digest = sha256(payload);
            byte[] encoded = Arrays.copyOf(payload,
                    Math.addExact(payload.length, digest.length));
            System.arraycopy(digest, 0, encoded, payload.length,
                    digest.length);
            requireSize(encoded);
            return encoded;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to encode item inventory abort", exception);
        }
    }

    public static ItemInventoryMutationAbort decode(byte[] encoded) {
        byte[] copied = Objects.requireNonNull(encoded, "encoded").clone();
        requireSize(copied);
        int payloadLength = copied.length - DIGEST_BYTES;
        byte[] payload = Arrays.copyOf(copied, payloadLength);
        byte[] storedDigest = Arrays.copyOfRange(copied, payloadLength,
                copied.length);
        if (!MessageDigest.isEqual(storedDigest, sha256(payload))) {
            throw new IllegalArgumentException(
                    "Item inventory abort digest is invalid");
        }
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(payload))) {
            if (input.readInt() != MAGIC) {
                throw new IllegalArgumentException(
                        "Item inventory abort magic is invalid");
            }
            if (input.readUnsignedShort() != VERSION) {
                throw new IllegalArgumentException(
                        "Item inventory abort version is unsupported");
            }
            int tokenLength = input.readInt();
            if (tokenLength <= 0 || tokenLength
                    > ItemInventoryMutationTokenCodec.MAX_ENCODED_BYTES) {
                throw new IllegalArgumentException(
                        "Item inventory abort token size is invalid");
            }
            byte[] tokenBytes = new byte[tokenLength];
            input.readFully(tokenBytes);
            ItemInventoryMutationToken token =
                    ItemInventoryMutationTokenCodec.decode(tokenBytes);
            ItemInventoryAbortReason reason =
                    ItemInventoryAbortReason.fromWireCode(
                            input.readUnsignedByte());
            long seconds = input.readLong();
            int nanos = input.readInt();
            if (nanos < 0 || nanos > 999_999_999
                    || nanos % 1_000_000 != 0) {
                throw new IllegalArgumentException(
                        "Item inventory abort time is not canonical");
            }
            Instant abortedAt = Instant.ofEpochSecond(seconds, nanos);
            if (input.available() != 0) {
                throw new IllegalArgumentException(
                        "Item inventory abort has trailing data");
            }
            return new ItemInventoryMutationAbort(token, reason, abortedAt);
        } catch (EOFException exception) {
            throw new IllegalArgumentException(
                    "Item inventory abort is truncated", exception);
        } catch (IOException | DateTimeException exception) {
            throw new IllegalArgumentException(
                    "Item inventory abort is invalid", exception);
        }
    }

    private static void requireSize(byte[] encoded) {
        if (encoded.length <= DIGEST_BYTES
                || encoded.length > MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException(
                    "Item inventory abort size is invalid");
        }
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable",
                    exception);
        }
    }
}

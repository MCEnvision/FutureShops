package com.enviouse.futureshops.server.escrow.item.runtime;

import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationToken;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationTokenCodec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

public final class ItemInventoryMutationQuarantineCodec {
    public static final int MAX_ENCODED_BYTES =
            ItemInventoryMutationTokenCodec.MAX_ENCODED_BYTES + 64;

    private static final int MAGIC = 0x494D5155;
    private static final int VERSION = 1;
    private static final int DIGEST_BYTES = 32;

    private ItemInventoryMutationQuarantineCodec() {
    }

    public static byte[] encode(ItemInventoryMutationQuarantine value) {
        Objects.requireNonNull(value, "value");
        try {
            byte[] token = ItemInventoryMutationTokenCodec.encode(
                    value.token());
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(MAGIC);
            output.writeShort(VERSION);
            output.writeInt(token.length);
            output.write(token);
            output.writeByte(value.reason().wireCode());
            output.writeLong(value.quarantinedAt().getEpochSecond());
            output.writeInt(value.quarantinedAt().getNano());
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
                    "Unable to encode item inventory quarantine",
                    exception);
        }
    }

    public static ItemInventoryMutationQuarantine decode(byte[] encoded) {
        byte[] copied = Objects.requireNonNull(encoded, "encoded").clone();
        requireSize(copied);
        int payloadLength = copied.length - DIGEST_BYTES;
        byte[] payload = Arrays.copyOf(copied, payloadLength);
        byte[] storedDigest = Arrays.copyOfRange(copied, payloadLength,
                copied.length);
        if (!MessageDigest.isEqual(storedDigest, sha256(payload))) {
            throw new IllegalArgumentException(
                    "Item inventory quarantine digest is invalid");
        }
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(payload))) {
            if (input.readInt() != MAGIC) {
                throw new IllegalArgumentException(
                        "Item inventory quarantine magic is invalid");
            }
            if (input.readUnsignedShort() != VERSION) {
                throw new IllegalArgumentException(
                        "Item inventory quarantine version is unsupported");
            }
            int tokenLength = input.readInt();
            if (tokenLength <= 0 || tokenLength
                    > ItemInventoryMutationTokenCodec.MAX_ENCODED_BYTES) {
                throw new IllegalArgumentException(
                        "Item inventory quarantine token size is invalid");
            }
            byte[] tokenBytes = new byte[tokenLength];
            input.readFully(tokenBytes);
            ItemInventoryMutationToken token =
                    ItemInventoryMutationTokenCodec.decode(tokenBytes);
            ItemInventoryQuarantineReason reason =
                    ItemInventoryQuarantineReason.fromWireCode(
                            input.readUnsignedByte());
            long seconds = input.readLong();
            int nanos = input.readInt();
            if (nanos < 0 || nanos > 999_999_999
                    || nanos % 1_000_000 != 0) {
                throw new IllegalArgumentException(
                        "Item inventory quarantine time is not canonical");
            }
            Instant quarantinedAt = Instant.ofEpochSecond(seconds, nanos);
            if (input.available() != 0) {
                throw new IllegalArgumentException(
                        "Item inventory quarantine has trailing data");
            }
            return new ItemInventoryMutationQuarantine(token, reason,
                    quarantinedAt);
        } catch (EOFException exception) {
            throw new IllegalArgumentException(
                    "Item inventory quarantine is truncated", exception);
        } catch (IOException | DateTimeException exception) {
            throw new IllegalArgumentException(
                    "Item inventory quarantine is invalid", exception);
        }
    }

    private static void requireSize(byte[] encoded) {
        if (encoded.length <= DIGEST_BYTES
                || encoded.length > MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException(
                    "Item inventory quarantine size is invalid");
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

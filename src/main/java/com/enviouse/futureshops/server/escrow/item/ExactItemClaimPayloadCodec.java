package com.enviouse.futureshops.server.escrow.item;

import com.enviouse.futureshops.money.ItemStackSnapshotCodec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

public final class ExactItemClaimPayloadCodec {
    public static final int MAX_ENCODED_BYTES =
            ItemStackSnapshotCodec.MAXIMUM_BYTES * 2 + 4096;

    private static final int MAGIC = 0x49434C4D;
    private static final int FINGERPRINT_MAGIC = 0x49434650;
    private static final int VERSION = 1;

    private ExactItemClaimPayloadCodec() {
    }

    public static byte[] encode(ExactItemClaimPayload payload) {
        Objects.requireNonNull(payload, "payload");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(MAGIC);
            output.writeShort(VERSION);
            ItemEscrowBinaryIo.writeUuid(output, payload.lotId());
            writeFields(output, payload.sourceTransactionId(),
                    payload.sourceKey(), payload.portionIndex(),
                    payload.portionCount(), payload.registryItemId(),
                    payload.stackCount(),
                    payload.canonicalOneCountTemplate(),
                    payload.serializedStackSnapshot());
            ItemEscrowBinaryIo.writeString(output, payload.fingerprint());
            output.flush();
            byte[] encoded = bytes.toByteArray();
            requireSize(encoded);
            return encoded;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to encode exact item claim payload", exception);
        }
    }

    public static ExactItemClaimPayload decode(byte[] encoded) {
        Objects.requireNonNull(encoded, "encoded");
        requireSize(encoded);
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(encoded))) {
            if (input.readInt() != MAGIC) {
                throw new IllegalArgumentException(
                        "Exact item claim payload magic is invalid");
            }
            if (input.readUnsignedShort() != VERSION) {
                throw new IllegalArgumentException(
                        "Exact item claim payload version is unsupported");
            }
            UUID lotId = ItemEscrowBinaryIo.readUuid(input);
            UUID sourceTransactionId = ItemEscrowBinaryIo.readUuid(input);
            String sourceKey = ItemEscrowBinaryIo.readString(input,
                    ExactItemClaimPayload.MAX_SOURCE_KEY_LENGTH);
            int portionIndex = input.readInt();
            int portionCount = input.readInt();
            String registryItemId = ItemEscrowBinaryIo.readString(input,
                    256);
            int stackCount = input.readInt();
            byte[] template = ItemEscrowBinaryIo.readBytes(input,
                    ItemStackSnapshotCodec.MAXIMUM_BYTES, false);
            byte[] snapshot = ItemEscrowBinaryIo.readBytes(input,
                    ItemStackSnapshotCodec.MAXIMUM_BYTES, false);
            String fingerprint = ItemEscrowBinaryIo.readString(input,
                    ItemInventoryHashes.HASH_BYTES * 2);
            ItemEscrowBinaryIo.requireFinished(input,
                    "Exact item claim payload");
            return new ExactItemClaimPayload(lotId, sourceTransactionId,
                    sourceKey, portionIndex, portionCount, registryItemId,
                    stackCount, template, snapshot, fingerprint);
        } catch (EOFException exception) {
            throw new IllegalArgumentException(
                    "Exact item claim payload is truncated", exception);
        } catch (IOException | ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "Exact item claim payload is invalid", exception);
        }
    }

    static String fingerprintOf(
            UUID sourceTransactionId,
            String sourceKey,
            int portionIndex,
            int portionCount,
            String registryItemId,
            int stackCount,
            byte[] canonicalOneCountTemplate,
            byte[] serializedStackSnapshot
    ) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(FINGERPRINT_MAGIC);
            output.writeShort(VERSION);
            writeFields(output, sourceTransactionId, sourceKey,
                    portionIndex, portionCount, registryItemId, stackCount,
                    canonicalOneCountTemplate, serializedStackSnapshot);
            output.flush();
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(bytes.toByteArray()));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable",
                    exception);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to fingerprint exact item claim payload",
                    exception);
        }
    }

    private static void writeFields(
            DataOutputStream output,
            UUID sourceTransactionId,
            String sourceKey,
            int portionIndex,
            int portionCount,
            String registryItemId,
            int stackCount,
            byte[] canonicalOneCountTemplate,
            byte[] serializedStackSnapshot
    ) throws IOException {
        ItemEscrowBinaryIo.writeUuid(output, sourceTransactionId);
        ItemEscrowBinaryIo.writeString(output, sourceKey);
        output.writeInt(portionIndex);
        output.writeInt(portionCount);
        ItemEscrowBinaryIo.writeString(output, registryItemId);
        output.writeInt(stackCount);
        ItemEscrowBinaryIo.writeBytes(output, canonicalOneCountTemplate);
        ItemEscrowBinaryIo.writeBytes(output, serializedStackSnapshot);
    }

    private static void requireSize(byte[] encoded) {
        if (encoded.length == 0 || encoded.length > MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException(
                    "Exact item claim payload size is invalid");
        }
    }
}

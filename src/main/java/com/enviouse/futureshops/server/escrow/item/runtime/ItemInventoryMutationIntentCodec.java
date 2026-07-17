package com.enviouse.futureshops.server.escrow.item.runtime;

import com.enviouse.futureshops.money.ItemStackSnapshotCodec;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationReceipt;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationReceiptCodec;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationToken;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationTokenCodec;
import com.enviouse.futureshops.server.escrow.item.ItemInventorySlot;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class ItemInventoryMutationIntentCodec {
    public static final int MAX_ENCODED_BYTES = 24 * 1024 * 1024;

    private static final int MAGIC = 0x494D494E;
    private static final int VERSION = 1;
    private static final int DIGEST_BYTES = 32;

    private ItemInventoryMutationIntentCodec() {
    }

    public static byte[] encode(ItemInventoryMutationIntent intent) {
        Objects.requireNonNull(intent, "intent");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(MAGIC);
            output.writeShort(VERSION);
            writeBytes(output, ItemInventoryMutationTokenCodec.encode(
                    intent.token()));
            writeBytes(output, ItemInventoryMutationReceiptCodec.encode(
                    intent.plannedReceipt()));
            output.writeInt(intent.slotEvidence().size());
            for (ItemInventorySlotMutationEvidence evidence
                    : intent.slotEvidence()) {
                output.writeInt(evidence.slot().serializedSlot());
                writeBytes(output, evidence.beforeSnapshot());
                writeBytes(output, evidence.afterSnapshot());
            }
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
                    "Unable to encode item inventory intent", exception);
        }
    }

    public static ItemInventoryMutationIntent decode(byte[] encoded) {
        byte[] copied = Objects.requireNonNull(encoded, "encoded").clone();
        requireSize(copied);
        int payloadLength = copied.length - DIGEST_BYTES;
        byte[] payload = Arrays.copyOf(copied, payloadLength);
        byte[] storedDigest = Arrays.copyOfRange(copied, payloadLength,
                copied.length);
        if (!MessageDigest.isEqual(storedDigest, sha256(payload))) {
            throw new IllegalArgumentException(
                    "Item inventory intent digest is invalid");
        }
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(payload))) {
            if (input.readInt() != MAGIC) {
                throw new IllegalArgumentException(
                        "Item inventory intent magic is invalid");
            }
            if (input.readUnsignedShort() != VERSION) {
                throw new IllegalArgumentException(
                        "Item inventory intent version is unsupported");
            }
            ItemInventoryMutationToken token =
                    ItemInventoryMutationTokenCodec.decode(readBytes(input,
                            ItemInventoryMutationTokenCodec.MAX_ENCODED_BYTES,
                            false));
            ItemInventoryMutationReceipt receipt =
                    ItemInventoryMutationReceiptCodec.decode(readBytes(input,
                            ItemInventoryMutationReceiptCodec
                                    .MAX_ENCODED_BYTES, false));
            int count = input.readInt();
            if (count <= 0
                    || count > ItemInventorySlot.ACCESSIBLE_SLOT_COUNT) {
                throw new IllegalArgumentException(
                        "Item inventory intent slot count is invalid");
            }
            List<ItemInventorySlotMutationEvidence> evidence =
                    new ArrayList<>(count);
            long snapshotBytes = 0L;
            for (int index = 0; index < count; index++) {
                ItemInventorySlot slot = new ItemInventorySlot(
                        input.readInt());
                byte[] before = readBytes(input,
                        ItemStackSnapshotCodec.MAXIMUM_BYTES, true);
                byte[] after = readBytes(input,
                        ItemStackSnapshotCodec.MAXIMUM_BYTES, true);
                snapshotBytes = Math.addExact(snapshotBytes,
                        Math.addExact(before.length, after.length));
                if (snapshotBytes
                        > ItemInventoryMutationIntent.MAX_EVIDENCE_BYTES) {
                    throw new IllegalArgumentException(
                            "Item inventory intent evidence exceeds its limit");
                }
                evidence.add(new ItemInventorySlotMutationEvidence(
                        slot, before, after));
            }
            if (input.available() != 0) {
                throw new IllegalArgumentException(
                        "Item inventory intent has trailing data");
            }
            return new ItemInventoryMutationIntent(token, receipt, evidence);
        } catch (EOFException exception) {
            throw new IllegalArgumentException(
                    "Item inventory intent is truncated", exception);
        } catch (IOException | ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "Item inventory intent is invalid", exception);
        }
    }

    private static void writeBytes(DataOutputStream output, byte[] value)
            throws IOException {
        output.writeInt(value.length);
        output.write(value);
    }

    private static byte[] readBytes(
            DataInputStream input,
            int maximum,
            boolean emptyAllowed
    ) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > maximum
                || !emptyAllowed && length == 0) {
            throw new IllegalArgumentException(
                    "Item inventory intent field size is invalid");
        }
        byte[] value = new byte[length];
        input.readFully(value);
        return value;
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable",
                    exception);
        }
    }

    private static void requireSize(byte[] encoded) {
        if (encoded.length <= DIGEST_BYTES
                || encoded.length > MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException(
                    "Item inventory intent size is invalid");
        }
    }
}

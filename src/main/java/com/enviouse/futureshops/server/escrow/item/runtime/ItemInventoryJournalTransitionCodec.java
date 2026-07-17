package com.enviouse.futureshops.server.escrow.item.runtime;

import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationReceiptCodec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Objects;

public final class ItemInventoryJournalTransitionCodec {
    public static final int MAX_ENCODED_BYTES = 16_777_184;

    private static final int MAGIC = 0x494A5452;
    private static final int VERSION = 1;
    private static final int DIGEST_BYTES = 32;
    private static final int FIXED_BYTES = Integer.BYTES + Short.BYTES
            + Byte.BYTES + Integer.BYTES + DIGEST_BYTES;

    private ItemInventoryJournalTransitionCodec() {
    }

    public static byte[] encode(ItemInventoryJournalTransition transition) {
        Objects.requireNonNull(transition, "transition");
        byte[] body = encodeBody(transition);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(
                    Math.addExact(FIXED_BYTES, body.length));
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(MAGIC);
            output.writeShort(VERSION);
            output.writeByte(transition.type().wireCode());
            output.writeInt(body.length);
            output.write(body);
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
                    "Unable to encode item inventory journal transition",
                    exception);
        }
    }

    public static ItemInventoryJournalTransition decode(byte[] encoded) {
        byte[] copied = Objects.requireNonNull(encoded, "encoded").clone();
        requireSize(copied);
        int payloadLength = copied.length - DIGEST_BYTES;
        byte[] payload = Arrays.copyOf(copied, payloadLength);
        byte[] storedDigest = Arrays.copyOfRange(copied, payloadLength,
                copied.length);
        if (!MessageDigest.isEqual(storedDigest, sha256(payload))) {
            throw new IllegalArgumentException(
                    "Item inventory journal transition digest is invalid");
        }
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(payload))) {
            if (input.readInt() != MAGIC) {
                throw new IllegalArgumentException(
                        "Item inventory journal transition magic is invalid");
            }
            if (input.readUnsignedShort() != VERSION) {
                throw new IllegalArgumentException(
                        "Item inventory journal transition version is unsupported");
            }
            ItemInventoryJournalTransitionType type =
                    ItemInventoryJournalTransitionType.fromWireCode(
                            input.readUnsignedByte());
            int bodyLength = input.readInt();
            int maximum = maximumBodyBytes(type);
            if (bodyLength <= 0 || bodyLength > maximum
                    || bodyLength != input.available()) {
                throw new IllegalArgumentException(
                        "Item inventory journal transition body size is invalid");
            }
            byte[] body = new byte[bodyLength];
            input.readFully(body);
            return decodeBody(type, body);
        } catch (EOFException exception) {
            throw new IllegalArgumentException(
                    "Item inventory journal transition is truncated",
                    exception);
        } catch (IOException | ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "Item inventory journal transition is invalid",
                    exception);
        }
    }

    private static byte[] encodeBody(
            ItemInventoryJournalTransition transition
    ) {
        byte[] body = switch (transition.type()) {
            case PREPARE -> ItemInventoryMutationIntentCodec.encode(
                    transition.intent().orElseThrow());
            case COMMIT -> ItemInventoryMutationReceiptCodec.encode(
                    transition.receipt().orElseThrow());
            case ABORT -> ItemInventoryMutationAbortCodec.encode(
                    transition.abort().orElseThrow());
            case QUARANTINE -> ItemInventoryMutationQuarantineCodec.encode(
                    transition.quarantine().orElseThrow());
        };
        if (body.length > maximumBodyBytes(transition.type())) {
            throw new IllegalArgumentException(
                    "Item inventory journal transition body exceeds its limit");
        }
        return body;
    }

    private static ItemInventoryJournalTransition decodeBody(
            ItemInventoryJournalTransitionType type,
            byte[] body
    ) {
        return switch (type) {
            case PREPARE -> ItemInventoryJournalTransition.prepare(
                    ItemInventoryMutationIntentCodec.decode(body));
            case COMMIT -> ItemInventoryJournalTransition.commit(
                    ItemInventoryMutationReceiptCodec.decode(body));
            case ABORT -> ItemInventoryJournalTransition.abort(
                    ItemInventoryMutationAbortCodec.decode(body));
            case QUARANTINE -> ItemInventoryJournalTransition.quarantine(
                    ItemInventoryMutationQuarantineCodec.decode(body));
        };
    }

    private static int maximumBodyBytes(
            ItemInventoryJournalTransitionType type
    ) {
        int codecMaximum = switch (type) {
            case PREPARE -> ItemInventoryMutationIntentCodec
                    .MAX_ENCODED_BYTES;
            case COMMIT -> ItemInventoryMutationReceiptCodec
                    .MAX_ENCODED_BYTES;
            case ABORT -> ItemInventoryMutationAbortCodec
                    .MAX_ENCODED_BYTES;
            case QUARANTINE -> ItemInventoryMutationQuarantineCodec
                    .MAX_ENCODED_BYTES;
        };
        return Math.min(codecMaximum,
                MAX_ENCODED_BYTES - FIXED_BYTES);
    }

    private static void requireSize(byte[] encoded) {
        if (encoded.length <= FIXED_BYTES
                || encoded.length > MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException(
                    "Item inventory journal transition size is invalid");
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

package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryJournalTransition;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryJournalTransitionCodec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class ExactItemClaimDeliveryCommitCodec {
    private static final int MAGIC = 0x49434443;
    private static final int VERSION = 1;

    private ExactItemClaimDeliveryCommitCodec() {
    }

    public static byte[] encode(ExactItemClaimDeliveryCommit commit) {
        Objects.requireNonNull(commit, "commit");
        try {
            byte[] delivery = ClaimJournalCodec.encodeDelivery(
                    commit.delivery());
            byte[] item = ItemInventoryJournalTransitionCodec.encode(
                    commit.itemCommit());
            byte[] fingerprint = commit.payloadFingerprint().getBytes(
                    StandardCharsets.US_ASCII);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(MAGIC);
            output.writeShort(VERSION);
            output.writeLong(commit.remainingBefore());
            output.writeInt(commit.retryIndex());
            output.writeInt(fingerprint.length);
            output.write(fingerprint);
            output.writeInt(delivery.length);
            output.write(delivery);
            output.writeInt(item.length);
            output.write(item);
            output.flush();
            byte[] encoded = bytes.toByteArray();
            if (encoded.length > EscrowJournalEventCodec.MAX_BODY_BYTES) {
                throw new IllegalArgumentException(
                        "Exact item claim delivery exceeds journal bounds");
            }
            return encoded;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to encode exact item claim delivery",
                    exception);
        }
    }

    public static ExactItemClaimDeliveryCommit decode(byte[] encoded) {
        byte[] value = Objects.requireNonNull(encoded, "encoded").clone();
        if (value.length == 0
                || value.length > EscrowJournalEventCodec.MAX_BODY_BYTES) {
            throw new IllegalArgumentException(
                    "Exact item claim delivery size is invalid");
        }
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(value))) {
            if (input.readInt() != MAGIC
                    || input.readUnsignedShort() != VERSION) {
                throw new IllegalArgumentException(
                        "Exact item claim delivery header is invalid");
            }
            long remainingBefore = input.readLong();
            int retryIndex = input.readInt();
            int fingerprintLength = input.readInt();
            if (fingerprintLength != 64
                    || fingerprintLength > input.available()) {
                throw new IllegalArgumentException(
                        "Exact item claim fingerprint is invalid");
            }
            String fingerprint = new String(
                    input.readNBytes(fingerprintLength),
                    StandardCharsets.US_ASCII);
            int deliveryLength = input.readInt();
            if (deliveryLength <= 0
                    || deliveryLength > input.available()) {
                throw new IllegalArgumentException(
                        "Exact item claim delivery component is invalid");
            }
            ClaimDeliveryCommit delivery = ClaimJournalCodec.decodeDelivery(
                    input.readNBytes(deliveryLength));
            int itemLength = input.readInt();
            if (itemLength <= 0
                    || itemLength
                    > ItemInventoryJournalTransitionCodec.MAX_ENCODED_BYTES
                    || itemLength != input.available()) {
                throw new IllegalArgumentException(
                        "Exact item inventory component is invalid");
            }
            ItemInventoryJournalTransition item =
                    ItemInventoryJournalTransitionCodec.decode(
                            input.readNBytes(itemLength));
            return new ExactItemClaimDeliveryCommit(delivery, item,
                    remainingBefore, retryIndex, fingerprint);
        } catch (EOFException exception) {
            throw new IllegalArgumentException(
                    "Exact item claim delivery is truncated", exception);
        } catch (IOException | ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "Exact item claim delivery is invalid", exception);
        }
    }
}

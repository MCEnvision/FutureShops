package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.custody.CustodyBatchCommit;
import com.enviouse.futureshops.server.escrow.custody.CustodyBatchCommitCodec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Objects;

public final class CashClaimDeliveryCommitCodec {
    private static final int MAGIC = 0x43434443;
    private static final int VERSION = 1;

    private CashClaimDeliveryCommitCodec() {
    }

    public static byte[] encode(CashClaimDeliveryCommit commit) {
        Objects.requireNonNull(commit, "commit");
        try {
            byte[] delivery = ClaimJournalCodec.encodeDelivery(
                    commit.delivery());
            byte[] custody = CustodyBatchCommitCodec.encode(
                    commit.custody());
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(
                    Math.addExact(delivery.length, custody.length) + 16);
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(MAGIC);
            output.writeShort(VERSION);
            output.writeInt(delivery.length);
            output.write(delivery);
            output.writeInt(custody.length);
            output.write(custody);
            output.flush();
            byte[] encoded = bytes.toByteArray();
            if (encoded.length > EscrowJournalEventCodec.MAX_BODY_BYTES) {
                throw new IllegalArgumentException(
                        "Cash claim delivery commit exceeds journal bounds");
            }
            return encoded;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to encode cash claim delivery commit", exception);
        }
    }

    public static CashClaimDeliveryCommit decode(byte[] encoded) {
        Objects.requireNonNull(encoded, "encoded");
        if (encoded.length == 0
                || encoded.length > EscrowJournalEventCodec.MAX_BODY_BYTES) {
            throw new IllegalArgumentException(
                    "Cash claim delivery commit size is invalid");
        }
        try {
            DataInputStream input = new DataInputStream(
                    new ByteArrayInputStream(encoded));
            if (input.readInt() != MAGIC
                    || input.readUnsignedShort() != VERSION) {
                throw new IllegalArgumentException(
                        "Cash claim delivery commit header is invalid");
            }
            int deliveryBytes = input.readInt();
            if (deliveryBytes <= 0 || deliveryBytes > input.available()) {
                throw new IllegalArgumentException(
                        "Cash claim delivery component is invalid");
            }
            ClaimDeliveryCommit delivery = ClaimJournalCodec.decodeDelivery(
                    input.readNBytes(deliveryBytes));
            int custodyBytes = input.readInt();
            if (custodyBytes <= 0
                    || custodyBytes > CustodyBatchCommitCodec.MAX_ENCODED_BYTES
                    || custodyBytes != input.available()) {
                throw new IllegalArgumentException(
                        "Cash claim custody component is invalid");
            }
            CustodyBatchCommit custody = CustodyBatchCommitCodec.decode(
                    input.readNBytes(custodyBytes));
            if (input.read() != -1) {
                throw new IllegalArgumentException(
                        "Cash claim delivery commit has trailing data");
            }
            return new CashClaimDeliveryCommit(delivery, custody);
        } catch (IOException exception) {
            throw new IllegalArgumentException(
                    "Unable to decode cash claim delivery commit", exception);
        }
    }
}

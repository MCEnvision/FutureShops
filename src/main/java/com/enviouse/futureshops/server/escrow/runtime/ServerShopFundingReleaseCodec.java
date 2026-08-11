package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;
import com.enviouse.futureshops.server.escrow.store.EscrowTransactionByteCodec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class ServerShopFundingReleaseCodec {
    public static final int CURRENT_SCHEMA = 1;
    public static final int MAX_ENCODED_BYTES =
            EscrowJournalEventCodec.MAX_BODY_BYTES;

    private static final int MAGIC = 0x46534652;
    private static final int MAX_TRANSACTION_BYTES = 8_388_608;
    private static final int MAX_DELIVERY_BYTES = 4_096;
    private static final int MAX_CLAIM_BYTES = 4_500_000;
    private static final int MAX_LEDGER_BYTES = 65_536;

    private ServerShopFundingReleaseCodec() {
    }

    public static byte[] encode(ServerShopFundingRelease release) {
        Objects.requireNonNull(release, "release");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(MAGIC);
            output.writeInt(CURRENT_SCHEMA);
            BinaryCodecSupport.writeUuid(output, release.releaseId());
            BinaryCodecSupport.writeUuid(output,
                    release.purchaseRequestId());
            BinaryCodecSupport.writeUuid(output, release.playerId());
            BinaryCodecSupport.writeUuid(output,
                    release.fundingTransactionId());
            BinaryCodecSupport.writeUuid(output, release.fundingClaimId());
            output.writeLong(release.amountMinorUnits());
            writeInstant(output, release.releasedAt());
            writeComponent(output, EscrowTransactionByteCodec.encode(
                    release.completedTransaction()),
                    MAX_TRANSACTION_BYTES, "release transaction");
            writeComponent(output, ClaimJournalCodec.encodeDelivery(
                    release.fundingClaimDelivery()),
                    MAX_DELIVERY_BYTES, "release delivery");
            writeComponent(output, ClaimJournalCodec.encodeClaim(
                    release.refundClaim()), MAX_CLAIM_BYTES,
                    "release refund claim");
            writeComponent(output, LedgerJournalCodec.encode(
                    release.ledgerTransaction()), MAX_LEDGER_BYTES,
                    "release ledger");
            output.flush();
            byte[] encoded = bytes.toByteArray();
            requireSize(encoded);
            return encoded;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to encode server shop funding release",
                    exception);
        }
    }

    public static ServerShopFundingRelease decode(byte[] encoded) {
        Objects.requireNonNull(encoded, "encoded");
        requireSize(encoded);
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(encoded))) {
            if (input.readInt() != MAGIC) {
                throw new IllegalArgumentException(
                        "Server shop funding release magic is invalid");
            }
            if (input.readInt() != CURRENT_SCHEMA) {
                throw new IllegalArgumentException(
                        "Server shop funding release schema is unsupported");
            }
            UUID releaseId = BinaryCodecSupport.readUuid(input);
            UUID purchaseRequestId = BinaryCodecSupport.readUuid(input);
            UUID playerId = BinaryCodecSupport.readUuid(input);
            UUID fundingTransactionId = BinaryCodecSupport.readUuid(input);
            UUID fundingClaimId = BinaryCodecSupport.readUuid(input);
            long amount = input.readLong();
            Instant releasedAt = readInstant(input);
            EscrowTransaction transaction =
                    EscrowTransactionByteCodec.decode(readComponent(input,
                            MAX_TRANSACTION_BYTES,
                            "release transaction"));
            ClaimDeliveryCommit delivery = ClaimJournalCodec.decodeDelivery(
                    readComponent(input, MAX_DELIVERY_BYTES,
                            "release delivery"));
            EscrowClaim refund = ClaimJournalCodec.decodeClaim(
                    readComponent(input, MAX_CLAIM_BYTES,
                            "release refund claim"));
            LedgerTransaction ledger = LedgerJournalCodec.decode(
                    readComponent(input, MAX_LEDGER_BYTES,
                            "release ledger"));
            if (input.read() != -1) {
                throw new IllegalArgumentException(
                        "Server shop funding release has trailing data");
            }
            return new ServerShopFundingRelease(releaseId,
                    purchaseRequestId, playerId, fundingTransactionId,
                    fundingClaimId, amount, releasedAt, transaction,
                    delivery, refund, ledger);
        } catch (EOFException exception) {
            throw new IllegalArgumentException(
                    "Server shop funding release is truncated", exception);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalArgumentException(
                    "Server shop funding release is invalid", exception);
        }
    }

    private static void writeComponent(
            DataOutputStream output,
            byte[] component,
            int maximum,
            String label
    ) throws IOException {
        if (component.length <= 0 || component.length > maximum) {
            throw new IllegalArgumentException(label + " size is invalid");
        }
        output.writeInt(component.length);
        output.write(component);
    }

    private static byte[] readComponent(
            DataInputStream input,
            int maximum,
            String label
    ) throws IOException {
        int size = input.readInt();
        if (size <= 0 || size > maximum || size > input.available()) {
            throw new IllegalArgumentException(label + " size is invalid");
        }
        return input.readNBytes(size);
    }

    private static void writeInstant(
            DataOutputStream output,
            Instant value
    ) throws IOException {
        output.writeLong(value.getEpochSecond());
        output.writeInt(value.getNano());
    }

    private static Instant readInstant(DataInputStream input)
            throws IOException {
        long second = input.readLong();
        int nano = input.readInt();
        if (nano < 0 || nano > 999_999_999) {
            throw new IllegalArgumentException(
                    "Server shop funding release time is invalid");
        }
        try {
            return Instant.ofEpochSecond(second, nano);
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException(
                    "Server shop funding release time is invalid",
                    exception);
        }
    }

    private static void requireSize(byte[] encoded) {
        if (encoded.length <= 0 || encoded.length > MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException(
                    "Server shop funding release size is invalid");
        }
    }
}

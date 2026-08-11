package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

public final class MoneyClaimSettlementCodec {
    private static final int LEGACY_VERSION =
            MoneyClaimSettlement.LEGACY_FORMAT_VERSION;
    private static final int VERSION =
            MoneyClaimSettlement.CURRENT_FORMAT_VERSION;

    private MoneyClaimSettlementCodec() {
    }

    public static byte[] encode(MoneyClaimSettlement settlement) {
        Objects.requireNonNull(settlement, "settlement");
        if (settlement.formatVersion() != VERSION) {
            throw new IllegalArgumentException(
                    "Only current money claim settlements can be written");
        }
        byte[] delivery = ClaimJournalCodec.encodeDelivery(
                settlement.delivery());
        byte[] ledger = LedgerJournalCodec.encode(
                settlement.ledgerTransaction());
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(
                    delivery.length + ledger.length + 96);
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(VERSION);
            BinaryCodecSupport.writeUuid(output, settlement.requestId());
            output.writeLong(settlement.walletBeforeMinorUnits());
            output.writeLong(settlement.debtBeforeMinorUnits());
            output.writeLong(settlement.reservedBeforeMinorUnits());
            output.writeLong(settlement.claimRemainingBeforeUnits());
            output.writeLong(settlement.walletBalanceLimitMinorUnits());
            output.writeLong(settlement.configurationGeneration());
            writePart(output, delivery);
            writePart(output, ledger);
            output.flush();
            byte[] result = bytes.toByteArray();
            if (result.length > EscrowJournalEventCodec.MAX_BODY_BYTES) {
                throw new IllegalArgumentException(
                        "Money claim settlement is too large");
            }
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to encode money claim settlement", exception);
        }
    }

    public static MoneyClaimSettlement decode(byte[] encoded) {
        Objects.requireNonNull(encoded, "encoded");
        if (encoded.length <= Integer.BYTES
                || encoded.length > EscrowJournalEventCodec.MAX_BODY_BYTES) {
            throw new IllegalArgumentException(
                    "Money claim settlement size is invalid");
        }
        ByteArrayInputStream bytes = new ByteArrayInputStream(encoded);
        try (DataInputStream input = new DataInputStream(bytes)) {
            int version = input.readInt();
            if (version == LEGACY_VERSION) {
                ClaimDeliveryCommit delivery = ClaimJournalCodec
                        .decodeDelivery(readPart(input, bytes));
                LedgerTransaction ledger = LedgerJournalCodec.decode(
                        readPart(input, bytes));
                requireExhausted(bytes);
                return MoneyClaimSettlement.legacy(delivery, ledger);
            }
            if (version != VERSION) {
                throw new IllegalArgumentException(
                        "Unsupported money claim settlement version");
            }
            UUID requestId = BinaryCodecSupport.readUuid(input);
            long wallet = input.readLong();
            long debt = input.readLong();
            long reserved = input.readLong();
            long claimRemaining = input.readLong();
            long limit = input.readLong();
            long generation = input.readLong();
            ClaimDeliveryCommit delivery = ClaimJournalCodec
                    .decodeDelivery(readPart(input, bytes));
            LedgerTransaction ledger = LedgerJournalCodec.decode(
                    readPart(input, bytes));
            requireExhausted(bytes);
            return new MoneyClaimSettlement(
                    MoneyClaimSettlement.CURRENT_FORMAT_VERSION,
                    requestId, wallet, debt, reserved, claimRemaining,
                    limit, generation, delivery, ledger);
        } catch (EOFException exception) {
            throw new IllegalArgumentException(
                    "Money claim settlement is truncated", exception);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalArgumentException(
                    "Unable to decode money claim settlement", exception);
        }
    }

    private static void writePart(
            DataOutputStream output,
            byte[] value
    ) throws IOException {
        if (value.length <= 0
                || value.length > EscrowJournalEventCodec.MAX_BODY_BYTES) {
            throw new IllegalArgumentException(
                    "Money claim settlement part size is invalid");
        }
        output.writeInt(value.length);
        output.write(value);
    }

    private static byte[] readPart(
            DataInputStream input,
            ByteArrayInputStream bytes
    ) throws IOException {
        int size = input.readInt();
        if (size <= 0 || size > EscrowJournalEventCodec.MAX_BODY_BYTES
                || size > bytes.available()) {
            throw new IllegalArgumentException(
                    "Invalid money claim settlement part size");
        }
        byte[] result = input.readNBytes(size);
        if (result.length != size) {
            throw new EOFException(
                    "Money claim settlement part is truncated");
        }
        return result;
    }

    private static void requireExhausted(ByteArrayInputStream bytes) {
        if (bytes.available() != 0) {
            throw new IllegalArgumentException(
                    "Money claim settlement has trailing data");
        }
    }
}

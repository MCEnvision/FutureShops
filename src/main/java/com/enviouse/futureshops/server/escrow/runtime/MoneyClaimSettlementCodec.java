package com.enviouse.futureshops.server.escrow.runtime;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Objects;

public final class MoneyClaimSettlementCodec {
    private static final int VERSION = 1;

    private MoneyClaimSettlementCodec() {
    }

    public static byte[] encode(MoneyClaimSettlement settlement) {
        Objects.requireNonNull(settlement, "settlement");
        byte[] delivery = ClaimJournalCodec.encodeDelivery(settlement.delivery());
        byte[] ledger = LedgerJournalCodec.encode(settlement.ledgerTransaction());
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(delivery.length + ledger.length + 16);
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(VERSION);
            output.writeInt(delivery.length);
            output.write(delivery);
            output.writeInt(ledger.length);
            output.write(ledger);
            output.flush();
            byte[] result = bytes.toByteArray();
            if (result.length > EscrowJournalEventCodec.MAX_BODY_BYTES) {
                throw new IllegalArgumentException("Money claim settlement is too large");
            }
            return result;
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to encode money claim settlement", ex);
        }
    }

    public static MoneyClaimSettlement decode(byte[] encoded) {
        Objects.requireNonNull(encoded, "encoded");
        try {
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded));
            if (input.readInt() != VERSION) {
                throw new IllegalArgumentException("Unsupported money claim settlement version");
            }
            byte[] delivery = readPart(input);
            byte[] ledger = readPart(input);
            if (input.read() != -1) {
                throw new IllegalArgumentException("Money claim settlement has trailing data");
            }
            return new MoneyClaimSettlement(
                    ClaimJournalCodec.decodeDelivery(delivery),
                    LedgerJournalCodec.decode(ledger));
        } catch (IOException ex) {
            throw new IllegalArgumentException("Unable to decode money claim settlement", ex);
        }
    }

    private static byte[] readPart(DataInputStream input) throws IOException {
        int size = input.readInt();
        if (size <= 0 || size > EscrowJournalEventCodec.MAX_BODY_BYTES || size > input.available()) {
            throw new IllegalArgumentException("Invalid money claim settlement part size");
        }
        return input.readNBytes(size);
    }
}

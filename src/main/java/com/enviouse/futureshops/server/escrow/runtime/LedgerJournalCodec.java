package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountId;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountType;
import com.enviouse.futureshops.server.escrow.ledger.LedgerLeg;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class LedgerJournalCodec {
    private static final int VERSION = 1;
    private static final int MAX_LEGS = 128;

    private LedgerJournalCodec() {
    }

    public static byte[] encode(LedgerTransaction transaction) {
        Objects.requireNonNull(transaction, "transaction");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(VERSION);
            BinaryCodecSupport.writeUuid(output, transaction.transactionId());
            BinaryCodecSupport.writeString(output, transaction.idempotencyKey(), 768);
            BinaryCodecSupport.writeString(output, transaction.reason(), 384);
            output.writeInt(transaction.legs().size());
            for (LedgerLeg leg : transaction.legs()) {
                BinaryCodecSupport.writeString(output, leg.account().type().name(), 128);
                BinaryCodecSupport.writeString(output, leg.account().ownerKey(), 512);
                output.writeLong(leg.deltaMinor());
            }
            output.flush();
            byte[] result = bytes.toByteArray();
            if (result.length > EscrowJournalEventCodec.MAX_BODY_BYTES) {
                throw new IllegalArgumentException("Ledger journal body is too large");
            }
            return result;
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to encode ledger journal body", ex);
        }
    }

    public static LedgerTransaction decode(byte[] encoded) {
        Objects.requireNonNull(encoded, "encoded");
        try {
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded));
            if (input.readInt() != VERSION) {
                throw new IllegalArgumentException("Unsupported ledger journal version");
            }
            java.util.UUID id = BinaryCodecSupport.readUuid(input);
            String key = BinaryCodecSupport.readString(input, 768);
            String reason = BinaryCodecSupport.readString(input, 384);
            int count = input.readInt();
            if (count < 2 || count > MAX_LEGS) {
                throw new IllegalArgumentException("Invalid ledger journal leg count");
            }
            List<LedgerLeg> legs = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                LedgerAccountType type;
                try {
                    type = LedgerAccountType.valueOf(BinaryCodecSupport.readString(input, 128));
                } catch (IllegalArgumentException ex) {
                    throw new IllegalArgumentException("Unknown ledger account type", ex);
                }
                String owner = BinaryCodecSupport.readString(input, 512);
                long delta = input.readLong();
                legs.add(new LedgerLeg(new LedgerAccountId(type, owner), delta));
            }
            if (input.read() != -1) {
                throw new IllegalArgumentException("Ledger journal body has trailing data");
            }
            return new LedgerTransaction(id, key, reason, legs);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Unable to decode ledger journal body", ex);
        }
    }
}

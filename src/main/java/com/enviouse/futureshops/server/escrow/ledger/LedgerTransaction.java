package com.enviouse.futureshops.server.escrow.ledger;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record LedgerTransaction(UUID transactionId, String idempotencyKey, String reason,
                                List<LedgerLeg> legs) {
    public LedgerTransaction {
        Objects.requireNonNull(transactionId, "transactionId");
        idempotencyKey = requireText(idempotencyKey, "idempotencyKey", 192);
        reason = requireText(reason, "reason", 96);
        legs = List.copyOf(Objects.requireNonNull(legs, "legs"));
        if (legs.size() < 2 || legs.size() > 128) {
            throw new IllegalArgumentException("Invalid ledger leg count");
        }
        long total = 0L;
        for (LedgerLeg leg : canonicalLegs(legs)) {
            total = Math.addExact(total, leg.deltaMinor());
        }
        if (total != 0L) {
            throw new IllegalArgumentException("Ledger transaction is not balanced");
        }
    }

    public String fingerprint() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeLong(transactionId.getMostSignificantBits());
            output.writeLong(transactionId.getLeastSignificantBits());
            output.writeUTF(idempotencyKey);
            output.writeUTF(reason);
            List<LedgerLeg> canonical = canonicalLegs(legs);
            output.writeInt(canonical.size());
            for (LedgerLeg leg : canonical) {
                output.writeUTF(leg.account().type().name());
                output.writeUTF(leg.account().ownerKey());
                output.writeLong(leg.deltaMinor());
            }
            output.flush();
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray());
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException | IOException ex) {
            throw new IllegalStateException("Unable to fingerprint ledger transaction", ex);
        }
    }

    static List<LedgerLeg> canonicalLegs(List<LedgerLeg> legs) {
        return legs.stream()
                .sorted(Comparator
                        .comparing((LedgerLeg leg) -> leg.account().type().ordinal())
                        .thenComparing(leg -> leg.account().ownerKey())
                        .thenComparingLong(LedgerLeg::deltaMinor))
                .toList();
    }

    private static String requireText(String value, String name, int maxLength) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty() || result.length() > maxLength) {
            throw new IllegalArgumentException("Invalid " + name);
        }
        return result;
    }
}

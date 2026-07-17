package com.enviouse.futureshops.server.escrow.runtime;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ClaimQuarantineCommit(UUID ownerId, UUID claimId, UUID transactionId,
                                    String requestKey, Instant quarantinedAt,
                                    String reasonCode) {
    private static final byte[] NAMESPACE =
            "futureshops.claim.quarantine.v1".getBytes(StandardCharsets.UTF_8);

    public ClaimQuarantineCommit {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(claimId, "claimId");
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(quarantinedAt, "quarantinedAt");
        reasonCode = Objects.requireNonNull(reasonCode, "reasonCode").trim();
        if (reasonCode.isEmpty() || reasonCode.length() > 160) {
            throw new IllegalArgumentException("Invalid claim quarantine reason code");
        }
        requestKey = Objects.requireNonNull(requestKey, "requestKey").trim();
        String expected = requestKeyFor(
                ownerId, claimId, transactionId, quarantinedAt, reasonCode);
        if (!requestKey.equals(expected)) {
            throw new IllegalArgumentException("Claim quarantine request key is not deterministic");
        }
    }

    public static ClaimQuarantineCommit create(UUID ownerId, UUID claimId, UUID transactionId,
                                               Instant quarantinedAt, String reasonCode) {
        String normalizedReason = Objects.requireNonNull(reasonCode, "reasonCode").trim();
        return new ClaimQuarantineCommit(
                ownerId,
                claimId,
                transactionId,
                requestKeyFor(ownerId, claimId, transactionId, quarantinedAt, normalizedReason),
                quarantinedAt,
                normalizedReason);
    }

    public static String requestKeyFor(UUID ownerId, UUID claimId, UUID transactionId,
                                       Instant quarantinedAt, String reasonCode) {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(claimId, "claimId");
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(quarantinedAt, "quarantinedAt");
        String normalizedReason = Objects.requireNonNull(reasonCode, "reasonCode").trim();
        if (normalizedReason.isEmpty() || normalizedReason.length() > 160) {
            throw new IllegalArgumentException("Invalid claim quarantine reason code");
        }
        byte[] reason = normalizedReason.getBytes(StandardCharsets.UTF_8);
        ByteBuffer canonical = ByteBuffer.allocate(
                NAMESPACE.length + Long.BYTES * 7 + Integer.BYTES + reason.length);
        canonical.put(NAMESPACE);
        putUuid(canonical, ownerId);
        putUuid(canonical, claimId);
        putUuid(canonical, transactionId);
        canonical.putLong(quarantinedAt.getEpochSecond());
        canonical.putInt(quarantinedAt.getNano());
        canonical.put(reason);
        return "claim.quarantine." + UUID.nameUUIDFromBytes(canonical.array());
    }

    private static void putUuid(ByteBuffer target, UUID value) {
        target.putLong(value.getMostSignificantBits());
        target.putLong(value.getLeastSignificantBits());
    }
}

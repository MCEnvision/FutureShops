package com.enviouse.futureshops.server.escrow.claim;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public record EscrowClaim(UUID claimId, UUID transactionId, UUID ownerId, String sourceKey,
                          ClaimKind kind,
                          long originalUnits, long remainingUnits, byte[] payload,
                          ClaimStatus status, String label, Instant createdAt, Instant updatedAt) {
    public static final int MAX_PAYLOAD_BYTES = 4 * 1024 * 1024;

    public EscrowClaim {
        Objects.requireNonNull(claimId, "claimId");
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(ownerId, "ownerId");
        sourceKey = requireSourceKey(sourceKey);
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        label = Objects.requireNonNull(label, "label").trim();
        payload = Arrays.copyOf(Objects.requireNonNull(payload, "payload"), payload.length);
        if (payload.length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Claim payload is too large");
        }
        if ((kind == ClaimKind.MONEY
                || kind == ClaimKind.INTERNAL_ESCROW_MONEY)
                && payload.length != 0) {
            throw new IllegalArgumentException("Money claim cannot contain an item payload");
        }
        if ((kind == ClaimKind.ITEM
                || kind == ClaimKind.PROTECTED_CASH
                || kind == ClaimKind.FOREIGN_CASH
                || kind == ClaimKind.BARTER_ITEM)
                && payload.length == 0) {
            throw new IllegalArgumentException("Item claim requires a payload");
        }
        if (originalUnits <= 0L || remainingUnits < 0L || remainingUnits > originalUnits) {
            throw new IllegalArgumentException("Invalid claim units");
        }
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("Claim update time precedes creation time");
        }
        if (label.isEmpty() || label.length() > 160) {
            throw new IllegalArgumentException("Claim label is too long");
        }
        if (status == ClaimStatus.COMPLETED && remainingUnits != 0L) {
            throw new IllegalArgumentException("Completed claim has remaining units");
        }
        if (status == ClaimStatus.PENDING && remainingUnits != originalUnits) {
            throw new IllegalArgumentException("Pending claim was already delivered");
        }
        if (status == ClaimStatus.PARTIALLY_DELIVERED
                && (remainingUnits == 0L || remainingUnits == originalUnits)) {
            throw new IllegalArgumentException("Invalid partial claim state");
        }
        if (status == ClaimStatus.QUARANTINED && remainingUnits == 0L) {
            throw new IllegalArgumentException("Quarantined claim has no remaining units");
        }
    }

    @Override
    public byte[] payload() {
        return Arrays.copyOf(payload, payload.length);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof EscrowClaim other)) {
            return false;
        }
        return originalUnits == other.originalUnits
                && remainingUnits == other.remainingUnits
                && claimId.equals(other.claimId)
                && transactionId.equals(other.transactionId)
                && ownerId.equals(other.ownerId)
                && sourceKey.equals(other.sourceKey)
                && kind == other.kind
                && Arrays.equals(payload, other.payload)
                && status == other.status
                && label.equals(other.label)
                && createdAt.equals(other.createdAt)
                && updatedAt.equals(other.updatedAt);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hash(claimId, transactionId, ownerId, sourceKey, kind,
                originalUnits, remainingUnits, status, label, createdAt, updatedAt)
                + Arrays.hashCode(payload);
    }

    public EscrowClaim deliver(long deliveredUnits, Instant now) {
        if (status == ClaimStatus.COMPLETED || status == ClaimStatus.QUARANTINED) {
            throw new IllegalStateException("Claim cannot be delivered");
        }
        if (deliveredUnits <= 0L || deliveredUnits > remainingUnits) {
            throw new IllegalArgumentException("Invalid delivered units");
        }
        Objects.requireNonNull(now, "now");
        if (now.isBefore(updatedAt)) {
            throw new IllegalArgumentException("Claim delivery time moved backward");
        }
        long remaining = Math.subtractExact(remainingUnits, deliveredUnits);
        ClaimStatus next = remaining == 0L ? ClaimStatus.COMPLETED : ClaimStatus.PARTIALLY_DELIVERED;
        return new EscrowClaim(claimId, transactionId, ownerId, sourceKey, kind, originalUnits,
                remaining, payload, next, label, createdAt, now);
    }

    public EscrowClaim quarantine(Instant now) {
        if (status == ClaimStatus.COMPLETED || status == ClaimStatus.QUARANTINED) {
            throw new IllegalStateException("Claim cannot be quarantined");
        }
        Objects.requireNonNull(now, "now");
        if (now.isBefore(updatedAt)) {
            throw new IllegalArgumentException("Claim quarantine time moved backward");
        }
        return new EscrowClaim(claimId, transactionId, ownerId, sourceKey, kind, originalUnits,
                remainingUnits, payload, ClaimStatus.QUARANTINED, label, createdAt, now);
    }

    public static String requireSourceKey(String sourceKey) {
        String normalized = Objects.requireNonNull(sourceKey, "sourceKey").trim();
        if (normalized.isEmpty() || normalized.length() > 192) {
            throw new IllegalArgumentException("Invalid claim source key");
        }
        return normalized;
    }
}

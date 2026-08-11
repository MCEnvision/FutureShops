package com.enviouse.futureshops.server.escrow.item.runtime;

import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationToken;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ItemInventoryQuarantineAdministration {
    public static final int MAX_REASON_LENGTH = 512;

    private final UUID commandId;
    private final UUID requestId;
    private final UUID playerId;
    private final UUID actorId;
    private final ItemInventoryQuarantineAdministrativeAction action;
    private final long expectedJournalRevision;
    private final byte[] expectedQuarantineDigest;
    private final Optional<EscrowClaim> refundClaim;
    private final String reason;
    private final Instant reviewedAt;

    public ItemInventoryQuarantineAdministration(
            UUID commandId,
            UUID requestId,
            UUID playerId,
            UUID actorId,
            ItemInventoryQuarantineAdministrativeAction action,
            long expectedJournalRevision,
            byte[] expectedQuarantineDigest,
            Optional<EscrowClaim> refundClaim,
            String reason,
            Instant reviewedAt
    ) {
        this.commandId = requireUuid(commandId, "commandId");
        this.requestId = requireUuid(requestId, "requestId");
        this.playerId = requireUuid(playerId, "playerId");
        this.actorId = requireUuid(actorId, "actorId");
        this.action = Objects.requireNonNull(action, "action");
        if (expectedJournalRevision < 0L) {
            throw new IllegalArgumentException(
                    "Item inventory journal revision is invalid");
        }
        this.expectedJournalRevision = expectedJournalRevision;
        this.expectedQuarantineDigest = requireDigest(
                expectedQuarantineDigest);
        this.refundClaim = Objects.requireNonNull(
                refundClaim, "refundClaim");
        this.reason = Objects.requireNonNull(reason, "reason").strip();
        this.reviewedAt = Objects.requireNonNull(reviewedAt, "reviewedAt");
        if (this.reason.isEmpty()
                || this.reason.length() > MAX_REASON_LENGTH
                || action == ItemInventoryQuarantineAdministrativeAction
                .REFUND != this.refundClaim.isPresent()) {
            throw new IllegalArgumentException(
                    "Item inventory quarantine administration is invalid");
        }
        this.refundClaim.ifPresent(claim -> {
            if (!claim.ownerId().equals(this.playerId)
                    || claim.status() != ClaimStatus.PENDING
                    || claim.remainingUnits() != claim.originalUnits()) {
                throw new IllegalArgumentException(
                        "Item inventory quarantine refund claim is invalid");
            }
        });
    }

    public static byte[] quarantineDigest(
            ItemInventoryMutationQuarantine quarantine
    ) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(
                    ItemInventoryMutationQuarantineCodec.encode(
                            Objects.requireNonNull(
                                    quarantine, "quarantine")));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable",
                    exception);
        }
    }

    public boolean matches(ItemInventoryJournalEntry entry) {
        if (entry == null
                || entry.status() != ItemInventoryJournalStatus.QUARANTINED) {
            return false;
        }
        ItemInventoryMutationToken token = entry.intent().token();
        return requestId.equals(token.requestId())
                && playerId.equals(token.playerId())
                && MessageDigest.isEqual(expectedQuarantineDigest,
                quarantineDigest(entry.quarantine().orElseThrow()));
    }

    public UUID commandId() {
        return commandId;
    }

    public UUID requestId() {
        return requestId;
    }

    public UUID playerId() {
        return playerId;
    }

    public UUID actorId() {
        return actorId;
    }

    public ItemInventoryQuarantineAdministrativeAction action() {
        return action;
    }

    public long expectedJournalRevision() {
        return expectedJournalRevision;
    }

    public byte[] expectedQuarantineDigest() {
        return expectedQuarantineDigest.clone();
    }

    public Optional<EscrowClaim> refundClaim() {
        return refundClaim;
    }

    public String reason() {
        return reason;
    }

    public Instant reviewedAt() {
        return reviewedAt;
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ItemInventoryQuarantineAdministration other
                && commandId.equals(other.commandId)
                && requestId.equals(other.requestId)
                && playerId.equals(other.playerId)
                && actorId.equals(other.actorId)
                && action == other.action
                && expectedJournalRevision == other.expectedJournalRevision
                && Arrays.equals(expectedQuarantineDigest,
                other.expectedQuarantineDigest)
                && refundClaim.equals(other.refundClaim)
                && reason.equals(other.reason)
                && reviewedAt.equals(other.reviewedAt);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(commandId, requestId, playerId, actorId,
                action, expectedJournalRevision, refundClaim, reason,
                reviewedAt);
        return 31 * result + Arrays.hashCode(expectedQuarantineDigest);
    }

    private static UUID requireUuid(UUID value, String name) {
        UUID result = Objects.requireNonNull(value, name);
        if (result.getMostSignificantBits() == 0L
                && result.getLeastSignificantBits() == 0L) {
            throw new IllegalArgumentException(name + " cannot be zero");
        }
        return result;
    }

    private static byte[] requireDigest(byte[] value) {
        byte[] result = Objects.requireNonNull(
                value, "expectedQuarantineDigest").clone();
        if (result.length != 32) {
            throw new IllegalArgumentException(
                    "Item inventory quarantine digest is invalid");
        }
        return result;
    }
}

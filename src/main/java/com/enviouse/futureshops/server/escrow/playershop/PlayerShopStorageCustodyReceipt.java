package com.enviouse.futureshops.server.escrow.playershop;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public final class PlayerShopStorageCustodyReceipt {
    private final UUID requestId;
    private final PlayerShopStorageMutationPlan plan;
    private final RecoveryState state;
    private final int appliedQuantity;
    private final String observedBeforeFingerprint;
    private final String observedAfterFingerprint;
    private final byte[] adapterReceipt;
    private final Instant updatedAt;
    private final long revision;
    private final String reason;
    private final String receiptFingerprint;

    public PlayerShopStorageCustodyReceipt(
            UUID requestId,
            PlayerShopStorageMutationPlan plan,
            RecoveryState state,
            int appliedQuantity,
            String observedBeforeFingerprint,
            String observedAfterFingerprint,
            byte[] adapterReceipt,
            Instant updatedAt,
            long revision,
            String reason,
            String receiptFingerprint
    ) {
        this.requestId = PlayerShopBinarySupport.requireUuid(requestId,
                "custody request id");
        this.plan = Objects.requireNonNull(plan, "plan");
        this.state = Objects.requireNonNull(state, "state");
        if (appliedQuantity < 0 || appliedQuantity > plan.lot().quantity()) {
            throw new IllegalArgumentException("Player shop custody quantity is invalid");
        }
        this.appliedQuantity = appliedQuantity;
        this.observedBeforeFingerprint = PlayerShopBinarySupport.optionalString(
                observedBeforeFingerprint, 128, "custody before fingerprint");
        this.observedAfterFingerprint = PlayerShopBinarySupport.optionalString(
                observedAfterFingerprint, 128, "custody after fingerprint");
        this.adapterReceipt = Objects.requireNonNull(adapterReceipt,
                "adapterReceipt").clone();
        if (this.adapterReceipt.length > PlayerShopEscrowConstants.MAX_COMPONENT_BYTES) {
            throw new IllegalArgumentException("Player shop adapter receipt is too large");
        }
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.revision = revision;
        this.reason = PlayerShopBinarySupport.optionalString(reason,
                PlayerShopEscrowConstants.MAX_TEXT_LENGTH, "custody reason");
        this.receiptFingerprint = PlayerShopBinarySupport.requireString(
                receiptFingerprint, 64, "custody receipt fingerprint");
        validateState();
        if (!computedFingerprint().equals(this.receiptFingerprint)) {
            throw new IllegalArgumentException("Player shop custody receipt is invalid");
        }
    }

    public static PlayerShopStorageCustodyReceipt prepared(
            UUID requestId,
            PlayerShopStorageMutationPlan plan,
            Instant now
    ) {
        return create(requestId, plan, RecoveryState.PREPARED, 0, "", "",
                new byte[0], now, 0L, "");
    }

    public PlayerShopStorageCustodyReceipt applied(
            String observedBefore,
            String observedAfter,
            byte[] adapterEvidence,
            Instant now
    ) {
        requirePrepared();
        return create(requestId, plan, RecoveryState.APPLIED,
                plan.lot().quantity(), observedBefore, observedAfter,
                adapterEvidence, now, 1L, "");
    }

    public PlayerShopStorageCustodyReceipt recoveryRequired(
            int quantityApplied,
            String observedBefore,
            String observedAfter,
            byte[] adapterEvidence,
            Instant now,
            String reason
    ) {
        requirePrepared();
        return create(requestId, plan, RecoveryState.RECOVERY_REQUIRED,
                quantityApplied, observedBefore, observedAfter,
                adapterEvidence, now, 1L, reason);
    }

    public PlayerShopStorageCustodyReceipt resolve(
            RecoveryState resolution,
            String observedAfter,
            byte[] adapterEvidence,
            Instant now,
            String reason
    ) {
        if (state != RecoveryState.RECOVERY_REQUIRED
                || !resolution.isResolution()) {
            throw new IllegalStateException("Player shop custody recovery state is invalid");
        }
        int finalApplied = resolution == RecoveryState.ROLLED_BACK
                ? 0 : appliedQuantity;
        return create(requestId, plan, resolution, finalApplied,
                observedBeforeFingerprint, observedAfter, adapterEvidence,
                now, 2L, reason);
    }

    private void requirePrepared() {
        if (state != RecoveryState.PREPARED) {
            throw new IllegalStateException("Player shop custody receipt is terminal");
        }
    }

    private void validateState() {
        if (revision < 0L || revision > 2L) {
            throw new IllegalArgumentException("Player shop custody revision is invalid");
        }
        switch (state) {
            case PREPARED -> {
                if (revision != 0L || appliedQuantity != 0
                        || !observedBeforeFingerprint.isEmpty()
                        || !observedAfterFingerprint.isEmpty()
                        || adapterReceipt.length != 0 || !reason.isEmpty()) {
                    throw new IllegalArgumentException("Player shop prepared custody is invalid");
                }
            }
            case APPLIED -> {
                if (revision != 1L || appliedQuantity != plan.lot().quantity()
                        || observedBeforeFingerprint.isEmpty()
                        || observedAfterFingerprint.isEmpty()
                        || adapterReceipt.length == 0 || !reason.isEmpty()) {
                    throw new IllegalArgumentException("Player shop applied custody is invalid");
                }
            }
            case RECOVERY_REQUIRED -> {
                if (revision != 1L || observedBeforeFingerprint.isEmpty()
                        || observedAfterFingerprint.isEmpty()
                        || adapterReceipt.length == 0 || reason.isEmpty()) {
                    throw new IllegalArgumentException("Player shop recovery custody is invalid");
                }
            }
            case ROLLED_BACK -> {
                if (revision != 2L || appliedQuantity != 0
                        || observedBeforeFingerprint.isEmpty()
                        || observedAfterFingerprint.isEmpty()
                        || adapterReceipt.length == 0 || reason.isEmpty()) {
                    throw new IllegalArgumentException("Player shop rollback custody is invalid");
                }
            }
            case CLAIM_PRESERVED, QUARANTINED -> {
                if (revision != 2L || observedBeforeFingerprint.isEmpty()
                        || observedAfterFingerprint.isEmpty()
                        || adapterReceipt.length == 0 || reason.isEmpty()) {
                    throw new IllegalArgumentException("Player shop terminal custody is invalid");
                }
            }
        }
    }

    private String computedFingerprint() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeUTF("futureshops player shop storage custody v1");
            PlayerShopBinarySupport.writeUuid(output, requestId);
            PlayerShopIntentCodec.writeStorageMutation(output, plan);
            output.writeByte(state.ordinal());
            output.writeInt(appliedQuantity);
            PlayerShopBinarySupport.writeOptionalString(output,
                    observedBeforeFingerprint, 128);
            PlayerShopBinarySupport.writeOptionalString(output,
                    observedAfterFingerprint, 128);
            PlayerShopBinarySupport.writeOptionalBytes(output, adapterReceipt,
                    PlayerShopEscrowConstants.MAX_COMPONENT_BYTES);
            output.writeLong(updatedAt.getEpochSecond());
            output.writeInt(updatedAt.getNano());
            output.writeLong(revision);
            PlayerShopBinarySupport.writeOptionalString(output, reason,
                    PlayerShopEscrowConstants.MAX_TEXT_LENGTH);
            output.flush();
            return PlayerShopBinarySupport.sha256(bytes.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to fingerprint player shop custody", exception);
        }
    }

    private static PlayerShopStorageCustodyReceipt create(
            UUID requestId,
            PlayerShopStorageMutationPlan plan,
            RecoveryState state,
            int appliedQuantity,
            String observedBefore,
            String observedAfter,
            byte[] adapterEvidence,
            Instant now,
            long revision,
            String reason
    ) {
        PlayerShopStorageCustodyReceipt provisional =
                new PlayerShopStorageCustodyReceipt(requestId, plan, state,
                        appliedQuantity, observedBefore, observedAfter,
                        adapterEvidence, now, revision, reason,
                        fingerprintOf(requestId, plan, state, appliedQuantity,
                                observedBefore, observedAfter, adapterEvidence,
                                now, revision, reason));
        return provisional;
    }

    private static String fingerprintOf(
            UUID requestId,
            PlayerShopStorageMutationPlan plan,
            RecoveryState state,
            int appliedQuantity,
            String observedBefore,
            String observedAfter,
            byte[] adapterEvidence,
            Instant now,
            long revision,
            String reason
    ) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeUTF("futureshops player shop storage custody v1");
            PlayerShopBinarySupport.writeUuid(output, requestId);
            PlayerShopIntentCodec.writeStorageMutation(output, plan);
            output.writeByte(state.ordinal());
            output.writeInt(appliedQuantity);
            PlayerShopBinarySupport.writeOptionalString(output,
                    observedBefore, 128);
            PlayerShopBinarySupport.writeOptionalString(output,
                    observedAfter, 128);
            PlayerShopBinarySupport.writeOptionalBytes(output,
                    adapterEvidence,
                    PlayerShopEscrowConstants.MAX_COMPONENT_BYTES);
            output.writeLong(now.getEpochSecond());
            output.writeInt(now.getNano());
            output.writeLong(revision);
            PlayerShopBinarySupport.writeOptionalString(output, reason,
                    PlayerShopEscrowConstants.MAX_TEXT_LENGTH);
            output.flush();
            return PlayerShopBinarySupport.sha256(bytes.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to fingerprint player shop custody", exception);
        }
    }

    public UUID requestId() {
        return requestId;
    }

    public PlayerShopStorageMutationPlan plan() {
        return plan;
    }

    public RecoveryState state() {
        return state;
    }

    public int appliedQuantity() {
        return appliedQuantity;
    }

    public String observedBeforeFingerprint() {
        return observedBeforeFingerprint;
    }

    public String observedAfterFingerprint() {
        return observedAfterFingerprint;
    }

    public byte[] adapterReceipt() {
        return adapterReceipt.clone();
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public long revision() {
        return revision;
    }

    public String reason() {
        return reason;
    }

    public String receiptFingerprint() {
        return receiptFingerprint;
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof PlayerShopStorageCustodyReceipt other
                && requestId.equals(other.requestId) && plan.equals(other.plan)
                && state == other.state
                && appliedQuantity == other.appliedQuantity
                && observedBeforeFingerprint.equals(
                        other.observedBeforeFingerprint)
                && observedAfterFingerprint.equals(
                        other.observedAfterFingerprint)
                && Arrays.equals(adapterReceipt, other.adapterReceipt)
                && updatedAt.equals(other.updatedAt)
                && revision == other.revision && reason.equals(other.reason)
                && receiptFingerprint.equals(other.receiptFingerprint);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hash(requestId, plan, state, appliedQuantity,
                observedBeforeFingerprint, observedAfterFingerprint,
                updatedAt, revision, reason, receiptFingerprint)
                + Arrays.hashCode(adapterReceipt);
    }

    public enum RecoveryState {
        PREPARED,
        APPLIED,
        RECOVERY_REQUIRED,
        ROLLED_BACK,
        CLAIM_PRESERVED,
        QUARANTINED;

        public boolean isResolution() {
            return this == ROLLED_BACK || this == CLAIM_PRESERVED
                    || this == QUARANTINED;
        }

        public boolean isSafeTerminal() {
            return this == APPLIED || this == ROLLED_BACK
                    || this == CLAIM_PRESERVED;
        }
    }
}

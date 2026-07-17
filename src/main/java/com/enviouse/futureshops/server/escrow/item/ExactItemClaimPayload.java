package com.enviouse.futureshops.server.escrow.item;

import com.enviouse.futureshops.money.ItemStackSnapshotCodec;
import net.minecraft.world.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public final class ExactItemClaimPayload {
    public static final int MAX_SOURCE_KEY_LENGTH = 192;
    public static final int MAX_PORTIONS = 4096;

    private final UUID lotId;
    private final UUID sourceTransactionId;
    private final String sourceKey;
    private final int portionIndex;
    private final int portionCount;
    private final String registryItemId;
    private final int stackCount;
    private final byte[] canonicalOneCountTemplate;
    private final byte[] serializedStackSnapshot;
    private final String fingerprint;

    ExactItemClaimPayload(
            UUID lotId,
            UUID sourceTransactionId,
            String sourceKey,
            int portionIndex,
            int portionCount,
            String registryItemId,
            int stackCount,
            byte[] canonicalOneCountTemplate,
            byte[] serializedStackSnapshot,
            String fingerprint
    ) {
        this.lotId = ItemInventoryBatchEntry.requireUuid(lotId, "lotId");
        this.sourceTransactionId = ItemInventoryBatchEntry.requireUuid(
                sourceTransactionId, "sourceTransactionId");
        this.sourceKey = requireSourceKey(sourceKey);
        this.portionIndex = portionIndex;
        this.portionCount = portionCount;
        this.registryItemId = ItemStackSnapshotEvidence.requireRegistryId(
                registryItemId);
        this.stackCount = stackCount;
        this.canonicalOneCountTemplate = Objects.requireNonNull(
                canonicalOneCountTemplate,
                "canonicalOneCountTemplate").clone();
        this.serializedStackSnapshot = Objects.requireNonNull(
                serializedStackSnapshot,
                "serializedStackSnapshot").clone();
        this.fingerprint = Objects.requireNonNull(
                fingerprint, "fingerprint");
        if (portionCount <= 0 || portionCount > MAX_PORTIONS
                || portionIndex < 0 || portionIndex >= portionCount) {
            throw new IllegalArgumentException(
                    "Exact item claim portion identity is invalid");
        }
        ItemStackSnapshotEvidence.SnapshotMetadata templateMetadata =
                ItemStackSnapshotEvidence.inspect(
                        this.canonicalOneCountTemplate);
        ItemStackSnapshotEvidence.SnapshotMetadata stackMetadata =
                ItemStackSnapshotEvidence.inspect(
                        this.serializedStackSnapshot);
        if (stackCount <= 0 || templateMetadata.count() != 1
                || stackMetadata.count() != stackCount
                || !templateMetadata.registryId().equals(this.registryItemId)
                || !stackMetadata.registryId().equals(this.registryItemId)
                || !Arrays.equals(this.canonicalOneCountTemplate,
                ItemStackSnapshotEvidence.canonicalOneCountSnapshot(
                        this.serializedStackSnapshot))) {
            throw new IllegalArgumentException(
                    "Exact item claim snapshots are invalid");
        }
        String expectedFingerprint = ExactItemClaimPayloadCodec.fingerprintOf(
                this.sourceTransactionId, this.sourceKey, this.portionIndex,
                this.portionCount, this.registryItemId, this.stackCount,
                this.canonicalOneCountTemplate,
                this.serializedStackSnapshot);
        UUID expectedLotId = deterministicLotId(this.sourceTransactionId,
                this.sourceKey, this.portionIndex);
        if (!expectedFingerprint.equals(this.fingerprint)
                || !expectedLotId.equals(this.lotId)) {
            throw new IllegalArgumentException(
                    "Exact item claim identity is invalid");
        }
    }

    public static ExactItemClaimPayload capture(
            UUID sourceTransactionId,
            String sourceKey,
            int portionIndex,
            int portionCount,
            ItemStack stack
    ) {
        Objects.requireNonNull(stack, "stack");
        if (stack.isEmpty()) {
            throw new IllegalArgumentException(
                    "Exact item claim stack is empty");
        }
        String registryId = ItemStackSnapshotEvidence.registryId(stack);
        byte[] snapshot = ItemStackSnapshotCodec.encode(stack);
        byte[] template = ItemStackSnapshotEvidence
                .canonicalOneCountSnapshot(snapshot);
        return preserveRaw(sourceTransactionId, sourceKey, portionIndex,
                portionCount, registryId, stack.getCount(), template,
                snapshot);
    }

    public static ExactItemClaimPayload preserveRaw(
            UUID sourceTransactionId,
            String sourceKey,
            int portionIndex,
            int portionCount,
            String registryItemId,
            int stackCount,
            byte[] canonicalOneCountTemplate,
            byte[] serializedStackSnapshot
    ) {
        ItemInventoryBatchEntry.requireUuid(sourceTransactionId,
                "sourceTransactionId");
        String normalizedSource = requireSourceKey(sourceKey);
        String normalizedRegistry =
                ItemStackSnapshotEvidence.requireRegistryId(registryItemId);
        ItemStackSnapshotEvidence.inspect(canonicalOneCountTemplate);
        ItemStackSnapshotEvidence.inspect(serializedStackSnapshot);
        if (portionCount <= 0 || portionCount > MAX_PORTIONS
                || portionIndex < 0 || portionIndex >= portionCount
                || stackCount <= 0) {
            throw new IllegalArgumentException(
                    "Exact item claim raw identity is invalid");
        }
        String fingerprint = ExactItemClaimPayloadCodec.fingerprintOf(
                sourceTransactionId, normalizedSource, portionIndex,
                portionCount, normalizedRegistry, stackCount,
                canonicalOneCountTemplate, serializedStackSnapshot);
        UUID lotId = deterministicLotId(sourceTransactionId,
                normalizedSource, portionIndex);
        return new ExactItemClaimPayload(lotId, sourceTransactionId,
                normalizedSource, portionIndex, portionCount,
                normalizedRegistry, stackCount, canonicalOneCountTemplate,
                serializedStackSnapshot, fingerprint);
    }

    public ExactItemClaimResolution resolve() {
        try {
            ItemStack stack = ItemStackSnapshotCodec.decode(
                    serializedStackSnapshot);
            ItemStack template = ItemStackSnapshotCodec.decode(
                    canonicalOneCountTemplate);
            if (stack.getCount() != stackCount || template.getCount() != 1
                    || !registryItemId.equals(
                    ItemStackSnapshotEvidence.registryId(stack))
                    || !registryItemId.equals(
                    ItemStackSnapshotEvidence.registryId(template))
                    || !Arrays.equals(serializedStackSnapshot,
                    ItemStackSnapshotCodec.encode(stack))
                    || !Arrays.equals(canonicalOneCountTemplate,
                    ItemStackSnapshotEvidence.canonicalOneCountSnapshot(
                            serializedStackSnapshot))) {
                return ExactItemClaimResolution.missing(this);
            }
            return ExactItemClaimResolution.resolved(stack);
        } catch (RuntimeException exception) {
            return ExactItemClaimResolution.missing(this);
        }
    }

    public UUID lotId() {
        return lotId;
    }

    public UUID sourceTransactionId() {
        return sourceTransactionId;
    }

    public String sourceKey() {
        return sourceKey;
    }

    public int portionIndex() {
        return portionIndex;
    }

    public int portionCount() {
        return portionCount;
    }

    public String registryItemId() {
        return registryItemId;
    }

    public int stackCount() {
        return stackCount;
    }

    public byte[] canonicalOneCountTemplate() {
        return canonicalOneCountTemplate.clone();
    }

    public byte[] serializedStackSnapshot() {
        return serializedStackSnapshot.clone();
    }

    public String fingerprint() {
        return fingerprint;
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ExactItemClaimPayload other
                && lotId.equals(other.lotId)
                && sourceTransactionId.equals(other.sourceTransactionId)
                && sourceKey.equals(other.sourceKey)
                && portionIndex == other.portionIndex
                && portionCount == other.portionCount
                && registryItemId.equals(other.registryItemId)
                && stackCount == other.stackCount
                && Arrays.equals(canonicalOneCountTemplate,
                other.canonicalOneCountTemplate)
                && Arrays.equals(serializedStackSnapshot,
                other.serializedStackSnapshot)
                && fingerprint.equals(other.fingerprint);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(lotId, sourceTransactionId, sourceKey,
                portionIndex, portionCount, registryItemId, stackCount,
                fingerprint);
        result = 31 * result + Arrays.hashCode(canonicalOneCountTemplate);
        return 31 * result + Arrays.hashCode(serializedStackSnapshot);
    }

    private static UUID deterministicLotId(
            UUID sourceTransactionId,
            String sourceKey,
            int portionIndex
    ) {
        String identity = "futureshops exact item claim lot\u0000"
                + sourceTransactionId + "\u0000" + sourceKey + "\u0000"
                + portionIndex;
        return UUID.nameUUIDFromBytes(identity.getBytes(
                StandardCharsets.UTF_8));
    }

    private static String requireSourceKey(String value) {
        String normalized = Objects.requireNonNull(value, "sourceKey");
        if (normalized.isEmpty()
                || normalized.length() > MAX_SOURCE_KEY_LENGTH
                || !normalized.equals(normalized.strip())
                || !wellFormedUtf16(normalized)) {
            throw new IllegalArgumentException(
                    "Exact item claim source key is invalid");
        }
        return normalized;
    }

    private static boolean wellFormedUtf16(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isHighSurrogate(character)) {
                if (index + 1 >= value.length()
                        || !Character.isLowSurrogate(
                        value.charAt(index + 1))) {
                    return false;
                }
                index++;
            } else if (Character.isLowSurrogate(character)) {
                return false;
            }
        }
        return true;
    }
}

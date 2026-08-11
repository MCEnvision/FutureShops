package com.enviouse.futureshops.server.escrow.item;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public final class MissingItemSnapshot {
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

    MissingItemSnapshot(ExactItemClaimPayload payload) {
        Objects.requireNonNull(payload, "payload");
        this.lotId = payload.lotId();
        this.sourceTransactionId = payload.sourceTransactionId();
        this.sourceKey = payload.sourceKey();
        this.portionIndex = payload.portionIndex();
        this.portionCount = payload.portionCount();
        this.registryItemId = payload.registryItemId();
        this.stackCount = payload.stackCount();
        this.canonicalOneCountTemplate =
                payload.canonicalOneCountTemplate();
        this.serializedStackSnapshot = payload.serializedStackSnapshot();
        this.fingerprint = payload.fingerprint();
    }

    public UUID lotId() {
        return lotId;
    }

    public String registryItemId() {
        return registryItemId;
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
        return object instanceof MissingItemSnapshot other
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
}

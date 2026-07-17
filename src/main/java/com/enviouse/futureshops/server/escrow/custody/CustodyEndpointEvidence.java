package com.enviouse.futureshops.server.escrow.custody;

import java.util.Arrays;
import java.util.Objects;

public record CustodyEndpointEvidence(
        String adapterId,
        CustodyAdapterCapability capability,
        String ownerKey,
        String locationKey,
        byte[] beforeStateHash,
        byte[] afterStateHash,
        String mutationToken
) {
    public static final int MAX_TEXT_LENGTH = 512;

    public CustodyEndpointEvidence {
        Objects.requireNonNull(adapterId, "adapterId");
        Objects.requireNonNull(capability, "capability");
        Objects.requireNonNull(ownerKey, "ownerKey");
        Objects.requireNonNull(locationKey, "locationKey");
        Objects.requireNonNull(beforeStateHash, "beforeStateHash");
        Objects.requireNonNull(afterStateHash, "afterStateHash");
        Objects.requireNonNull(mutationToken, "mutationToken");
        adapterId = requireText(adapterId, "adapter ID");
        ownerKey = requireText(ownerKey, "owner key");
        locationKey = requireText(locationKey, "location key");
        mutationToken = requireText(mutationToken, "mutation token");
        beforeStateHash = beforeStateHash.clone();
        afterStateHash = afterStateHash.clone();
        CustodyHashes.requireHash(beforeStateHash, "Custody before state hash");
        CustodyHashes.requireHash(afterStateHash, "Custody after state hash");
    }

    public static CustodyEndpointEvidence captured(String adapterId,
                                                    CustodyAdapterCapability capability,
                                                    String ownerKey,
                                                    String locationKey,
                                                    byte[] beforeState,
                                                    byte[] afterState,
                                                    String mutationToken) {
        Objects.requireNonNull(beforeState, "beforeState");
        Objects.requireNonNull(afterState, "afterState");
        return new CustodyEndpointEvidence(adapterId, capability, ownerKey, locationKey,
                CustodyHashes.sha256(beforeState), CustodyHashes.sha256(afterState), mutationToken);
    }

    @Override
    public byte[] beforeStateHash() {
        return beforeStateHash.clone();
    }

    @Override
    public byte[] afterStateHash() {
        return afterStateHash.clone();
    }

    public boolean matchesObservedAfterState(byte[] observedState) {
        Objects.requireNonNull(observedState, "observedState");
        return CustodyHashes.equal(afterStateHash, CustodyHashes.sha256(observedState));
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof CustodyEndpointEvidence other)) {
            return false;
        }
        return adapterId.equals(other.adapterId)
                && capability == other.capability
                && ownerKey.equals(other.ownerKey)
                && locationKey.equals(other.locationKey)
                && Arrays.equals(beforeStateHash, other.beforeStateHash)
                && Arrays.equals(afterStateHash, other.afterStateHash)
                && mutationToken.equals(other.mutationToken);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(adapterId, capability, ownerKey, locationKey, mutationToken);
        result = 31 * result + Arrays.hashCode(beforeStateHash);
        return 31 * result + Arrays.hashCode(afterStateHash);
    }

    private static String requireText(String value, String label) {
        String normalized = value.strip();
        if (normalized.isEmpty() || normalized.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("Invalid custody " + label);
        }
        return normalized;
    }
}

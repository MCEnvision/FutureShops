package com.enviouse.futureshops.server.escrow.custody;

import java.util.Arrays;
import java.util.Objects;

public record CustodyItemSnapshot(
        String registryId,
        int count,
        byte[] serializedNbt,
        byte[] contentHash
) {
    public static final int MAX_REGISTRY_ID_LENGTH = 256;
    public static final int MAX_NBT_BYTES = 1_048_576;

    public CustodyItemSnapshot {
        Objects.requireNonNull(registryId, "registryId");
        Objects.requireNonNull(serializedNbt, "serializedNbt");
        Objects.requireNonNull(contentHash, "contentHash");
        registryId = registryId.strip();
        if (registryId.isEmpty() || registryId.length() > MAX_REGISTRY_ID_LENGTH) {
            throw new IllegalArgumentException("Invalid custody item registry ID");
        }
        if (count <= 0) {
            throw new IllegalArgumentException("Custody item count must be positive");
        }
        if (serializedNbt.length == 0 || serializedNbt.length > MAX_NBT_BYTES) {
            throw new IllegalArgumentException("Custody item NBT payload exceeds bounds");
        }
        serializedNbt = serializedNbt.clone();
        contentHash = contentHash.clone();
        CustodyHashes.requireHash(contentHash, "Custody item content hash");
        byte[] expected = CustodyHashes.itemHash(registryId, count, serializedNbt);
        if (!CustodyHashes.equal(expected, contentHash)) {
            throw new IllegalArgumentException("Custody item snapshot hash does not match its payload");
        }
    }

    public static CustodyItemSnapshot capture(String registryId, int count, byte[] serializedNbt) {
        Objects.requireNonNull(serializedNbt, "serializedNbt");
        return new CustodyItemSnapshot(registryId, count, serializedNbt,
                CustodyHashes.itemHash(registryId.strip(), count, serializedNbt));
    }

    @Override
    public byte[] serializedNbt() {
        return serializedNbt.clone();
    }

    @Override
    public byte[] contentHash() {
        return contentHash.clone();
    }

    public String fingerprint() {
        return CustodyHashes.hex(contentHash);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof CustodyItemSnapshot other)) {
            return false;
        }
        return count == other.count
                && registryId.equals(other.registryId)
                && Arrays.equals(serializedNbt, other.serializedNbt)
                && Arrays.equals(contentHash, other.contentHash);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(registryId, count);
        result = 31 * result + Arrays.hashCode(serializedNbt);
        return 31 * result + Arrays.hashCode(contentHash);
    }
}

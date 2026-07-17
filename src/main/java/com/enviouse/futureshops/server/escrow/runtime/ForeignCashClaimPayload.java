package com.enviouse.futureshops.server.escrow.runtime;

import java.util.Arrays;
import java.util.Objects;
import java.util.regex.Pattern;

public record ForeignCashClaimPayload(
        String providerId,
        String configSignature,
        String registryItemId,
        long denominationMinorUnits,
        int stackCount,
        int denominationIndex,
        int portionIndex,
        int portionCount,
        byte[] serializedItemStackNbt,
        String fingerprint
) {
    public static final String PROTECTED_ITEM_ID = "futureshops:money";
    public static final int MAX_PROVIDER_ID_LENGTH = 128;
    public static final int CONFIG_SIGNATURE_LENGTH = 64;
    public static final int MAX_REGISTRY_ITEM_ID_LENGTH = 256;
    public static final int MAX_DENOMINATIONS = 32;
    public static final int MAX_STACK_COUNT = 4096;
    public static final int MAX_PORTIONS = 4095;
    public static final int MAX_ITEM_STACK_NBT_BYTES = 1_048_576;

    private static final Pattern SIGNATURE = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern REGISTRY_ID = Pattern.compile(
            "[a-z0-9_.-]+:[a-z0-9/._-]+");

    public ForeignCashClaimPayload {
        providerId = requireText(providerId, MAX_PROVIDER_ID_LENGTH,
                "Foreign cash provider ID is invalid");
        if (providerId.equalsIgnoreCase("futureshops")) {
            throw new IllegalArgumentException(
                    "Foreign cash provider cannot be the protected provider");
        }
        configSignature = Objects.requireNonNull(
                configSignature, "configSignature");
        if (!SIGNATURE.matcher(configSignature).matches()) {
            throw new IllegalArgumentException(
                    "Foreign cash config signature is invalid");
        }
        registryItemId = requireText(registryItemId,
                MAX_REGISTRY_ITEM_ID_LENGTH,
                "Foreign cash registry item ID is invalid");
        if (!REGISTRY_ID.matcher(registryItemId).matches()) {
            throw new IllegalArgumentException(
                    "Foreign cash registry item ID is invalid");
        }
        if (PROTECTED_ITEM_ID.equals(registryItemId)) {
            throw new IllegalArgumentException(
                    "Foreign cash cannot use the protected currency item");
        }
        serializedItemStackNbt = Objects.requireNonNull(
                serializedItemStackNbt, "serializedItemStackNbt").clone();
        if (denominationMinorUnits <= 0L
                || stackCount <= 0
                || stackCount > MAX_STACK_COUNT
                || denominationIndex < 0
                || denominationIndex >= MAX_DENOMINATIONS
                || portionCount <= 0
                || portionCount > MAX_PORTIONS
                || portionIndex < 0
                || portionIndex >= portionCount
                || serializedItemStackNbt.length == 0
                || serializedItemStackNbt.length > MAX_ITEM_STACK_NBT_BYTES) {
            throw new IllegalArgumentException(
                    "Foreign cash claim payload is invalid");
        }
        Math.multiplyExact(denominationMinorUnits, (long) stackCount);
        fingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
        if (!SIGNATURE.matcher(fingerprint).matches()
                || !fingerprint.equals(ForeignCashClaimPayloadCodec.fingerprintOf(
                providerId, configSignature, registryItemId,
                denominationMinorUnits, stackCount, denominationIndex,
                portionIndex, portionCount, serializedItemStackNbt))) {
            throw new IllegalArgumentException(
                    "Foreign cash claim fingerprint is invalid");
        }
    }

    public static ForeignCashClaimPayload capture(
            String providerId,
            String configSignature,
            String registryItemId,
            long denominationMinorUnits,
            int stackCount,
            int denominationIndex,
            int portionIndex,
            int portionCount,
            byte[] serializedItemStackNbt
    ) {
        String fingerprint = ForeignCashClaimPayloadCodec.fingerprintOf(
                providerId, configSignature, registryItemId,
                denominationMinorUnits, stackCount, denominationIndex,
                portionIndex, portionCount, serializedItemStackNbt);
        return new ForeignCashClaimPayload(
                providerId, configSignature, registryItemId,
                denominationMinorUnits, stackCount, denominationIndex,
                portionIndex, portionCount, serializedItemStackNbt, fingerprint);
    }

    @Override
    public byte[] serializedItemStackNbt() {
        return serializedItemStackNbt.clone();
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof ForeignCashClaimPayload other)) {
            return false;
        }
        return denominationMinorUnits == other.denominationMinorUnits
                && stackCount == other.stackCount
                && denominationIndex == other.denominationIndex
                && portionIndex == other.portionIndex
                && portionCount == other.portionCount
                && providerId.equals(other.providerId)
                && configSignature.equals(other.configSignature)
                && registryItemId.equals(other.registryItemId)
                && Arrays.equals(serializedItemStackNbt,
                other.serializedItemStackNbt)
                && fingerprint.equals(other.fingerprint);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(providerId, configSignature, registryItemId,
                denominationMinorUnits, stackCount, denominationIndex,
                portionIndex, portionCount, fingerprint);
        return 31 * result + Arrays.hashCode(serializedItemStackNbt);
    }

    private static String requireText(String value, int maximumLength,
                                      String message) {
        String normalized = Objects.requireNonNull(value, "value");
        if (normalized.isEmpty() || normalized.length() > maximumLength
                || !normalized.equals(normalized.strip())
                || !wellFormedUtf16(normalized)) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private static boolean wellFormedUtf16(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isHighSurrogate(character)) {
                if (index + 1 >= value.length()
                        || !Character.isLowSurrogate(value.charAt(index + 1))) {
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

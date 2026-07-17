package com.enviouse.futureshops.server.escrow.runtime;

import java.util.Arrays;
import java.util.Objects;

public record ForeignAtmStackSelection(
        int denominationIndex,
        String registryItemId,
        long denominationMinorUnits,
        int stackCount,
        int portionIndex,
        int portionCount,
        byte[] serializedItemStackNbt
) {
    public ForeignAtmStackSelection {
        registryItemId = Objects.requireNonNull(
                registryItemId, "registryItemId");
        serializedItemStackNbt = Objects.requireNonNull(
                serializedItemStackNbt, "serializedItemStackNbt").clone();
        if (denominationIndex < 0
                || denominationIndex
                >= ForeignCashClaimPayload.MAX_DENOMINATIONS
                || denominationMinorUnits <= 0L
                || stackCount <= 0
                || stackCount > ForeignCashClaimPayload.MAX_STACK_COUNT
                || portionCount <= 0
                || portionCount > ForeignCashClaimPayload.MAX_PORTIONS
                || portionIndex < 0
                || portionIndex >= portionCount
                || serializedItemStackNbt.length == 0
                || serializedItemStackNbt.length
                > ForeignCashClaimPayload.MAX_ITEM_STACK_NBT_BYTES) {
            throw new IllegalArgumentException(
                    "Foreign ATM stack selection is invalid");
        }
        Math.multiplyExact(denominationMinorUnits, (long) stackCount);
        if (ForeignCashClaimPayload.PROTECTED_ITEM_ID.equals(
                registryItemId)) {
            throw new IllegalArgumentException(
                    "Foreign ATM cannot mint protected currency");
        }
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
        if (!(object instanceof ForeignAtmStackSelection other)) {
            return false;
        }
        return denominationIndex == other.denominationIndex
                && denominationMinorUnits
                == other.denominationMinorUnits
                && stackCount == other.stackCount
                && portionIndex == other.portionIndex
                && portionCount == other.portionCount
                && registryItemId.equals(other.registryItemId)
                && Arrays.equals(serializedItemStackNbt,
                other.serializedItemStackNbt);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(
                denominationIndex, registryItemId,
                denominationMinorUnits, stackCount,
                portionIndex, portionCount);
        return 31 * result + Arrays.hashCode(serializedItemStackNbt);
    }

}

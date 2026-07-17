package com.enviouse.futureshops.server.escrow.redemption;

import net.minecraft.nbt.CompoundTag;

import java.util.Objects;

public final class CashDepositEvidenceKeys {
    public static final String PROTECTED =
            "futureshops_protected_cash_redemption";
    public static final String FOREIGN =
            "futureshops_foreign_cash_deposit";

    private CashDepositEvidenceKeys() {
    }

    public static boolean hasConflict(CompoundTag persistent,
                                      String activeKey) {
        Objects.requireNonNull(persistent, "persistent");
        Objects.requireNonNull(activeKey, "activeKey");
        if (activeKey.equals(PROTECTED)) {
            return persistent.get(FOREIGN) != null;
        }
        if (activeKey.equals(FOREIGN)) {
            return persistent.get(PROTECTED) != null;
        }
        throw new IllegalArgumentException(
                "Cash deposit evidence key is invalid");
    }
}

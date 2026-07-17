package com.enviouse.futureshops.server.escrow.playershop;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record PlayerShopClaimPlan(
        UUID claimId,
        UUID beneficiaryId,
        Kind kind,
        String purpose,
        long moneyAmountMinorUnits,
        PlayerShopItemLot itemLot
) {
    public PlayerShopClaimPlan {
        claimId = PlayerShopBinarySupport.requireUuid(claimId, "claim id");
        beneficiaryId = PlayerShopBinarySupport.requireUuid(beneficiaryId,
                "claim beneficiary id");
        kind = Objects.requireNonNull(kind, "kind");
        purpose = PlayerShopBinarySupport.requireString(purpose,
                PlayerShopEscrowConstants.MAX_TEXT_LENGTH, "claim purpose");
        if (kind == Kind.MONEY) {
            if (moneyAmountMinorUnits <= 0L || itemLot != null) {
                throw new IllegalArgumentException("Player shop money claim is invalid");
            }
        } else if (moneyAmountMinorUnits != 0L || itemLot == null) {
            throw new IllegalArgumentException("Player shop item claim is invalid");
        }
    }

    public static PlayerShopClaimPlan money(
            UUID requestId,
            String key,
            UUID beneficiaryId,
            long amountMinorUnits,
            String purpose
    ) {
        return new PlayerShopClaimPlan(
                PlayerShopBinarySupport.deterministicUuid("money claim",
                        requestId, key),
                beneficiaryId, Kind.MONEY, purpose, amountMinorUnits, null);
    }

    public static PlayerShopClaimPlan item(
            UUID requestId,
            String key,
            UUID beneficiaryId,
            PlayerShopItemLot lot,
            String purpose
    ) {
        return new PlayerShopClaimPlan(
                PlayerShopBinarySupport.deterministicUuid("item claim",
                        requestId, key),
                beneficiaryId, Kind.EXACT_ITEM, purpose, 0L,
                Objects.requireNonNull(lot, "lot"));
    }

    public Optional<PlayerShopItemLot> exactItem() {
        return Optional.ofNullable(itemLot);
    }

    public enum Kind {
        MONEY,
        EXACT_ITEM
    }
}

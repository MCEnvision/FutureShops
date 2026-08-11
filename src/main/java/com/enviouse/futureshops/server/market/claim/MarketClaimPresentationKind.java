package com.enviouse.futureshops.server.market.claim;

import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;

import java.util.Objects;

public enum MarketClaimPresentationKind {
    UNKNOWN,
    MONEY,
    ITEM,
    PROTECTED_CASH,
    FOREIGN_CASH,
    BARTER_ITEM,
    MONEY_REFUND,
    ITEM_REFUND;

    public static MarketClaimPresentationKind from(EscrowClaim claim) {
        EscrowClaim value = Objects.requireNonNull(claim, "claim");
        return switch (value.kind()) {
            case MONEY -> MONEY;
            case ITEM -> ITEM;
            case PROTECTED_CASH -> PROTECTED_CASH;
            case FOREIGN_CASH -> FOREIGN_CASH;
            case BARTER_ITEM -> BARTER_ITEM;
            case REFUND -> value.payload().length == 0
                    ? MONEY_REFUND : ITEM_REFUND;
            case INTERNAL_ESCROW_MONEY -> UNKNOWN;
        };
    }

    public boolean collectible() {
        return this != UNKNOWN;
    }
}

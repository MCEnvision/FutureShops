package com.enviouse.futureshops.server.market.claim;

import java.util.Objects;
import java.util.OptionalLong;

public record MarketClaimDeliveryOutcome(
        MarketClaimCollectionCode code,
        long deliveredUnits,
        long remainingUnits,
        OptionalLong resultingBalanceMinor,
        boolean replayed
) {
    public MarketClaimDeliveryOutcome {
        code = Objects.requireNonNull(code, "code");
        resultingBalanceMinor = Objects.requireNonNull(
                resultingBalanceMinor, "resultingBalanceMinor");
        if (deliveredUnits < 0L || remainingUnits < 0L
                || code == MarketClaimCollectionCode.COLLECTED
                && (deliveredUnits <= 0L || remainingUnits != 0L)
                || code == MarketClaimCollectionCode
                .PARTIALLY_COLLECTED
                && (deliveredUnits <= 0L || remainingUnits <= 0L)
                || code == MarketClaimCollectionCode.ALREADY_COLLECTED
                && (deliveredUnits != 0L || remainingUnits != 0L)
                || replayed && code != MarketClaimCollectionCode.COLLECTED
                && code != MarketClaimCollectionCode
                .PARTIALLY_COLLECTED
                && code != MarketClaimCollectionCode.ALREADY_COLLECTED) {
            throw new IllegalArgumentException(
                    "Market claim delivery outcome is invalid");
        }
    }

    public static MarketClaimDeliveryOutcome failure(
            MarketClaimCollectionCode code,
            long remainingUnits
    ) {
        MarketClaimCollectionCode resultCode = Objects.requireNonNull(
                code, "code");
        return new MarketClaimDeliveryOutcome(resultCode, 0L,
                remainingUnits, OptionalLong.empty(), false);
    }
}

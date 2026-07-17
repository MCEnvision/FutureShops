package com.enviouse.futureshops.server.escrow.custody;

import java.util.Objects;

public record CustodyLiabilityKey(
        CustodyAssetType assetType,
        CustodyProtectionTier protectionTier,
        String currencyProvider
) {
    public CustodyLiabilityKey {
        Objects.requireNonNull(assetType, "assetType");
        Objects.requireNonNull(protectionTier, "protectionTier");
        Objects.requireNonNull(currencyProvider, "currencyProvider");
        currencyProvider = currencyProvider.strip();
    }

    public static CustodyLiabilityKey from(CustodyLot lot) {
        return new CustodyLiabilityKey(lot.assetType(), lot.protectionTier(), lot.currencyProvider());
    }
}

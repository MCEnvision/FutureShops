package com.enviouse.futureshops.server.escrow.custody;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record CustodyHeldLiability(UUID lotId,
                                   UUID transactionId,
                                   CustodyAssetType assetType,
                                   long units,
                                   String currencyProvider,
                                   List<ProtectedCurrencyProvenance> protectedProvenance) {
    public CustodyHeldLiability {
        Objects.requireNonNull(lotId, "lotId");
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(assetType, "assetType");
        currencyProvider = Objects.requireNonNull(currencyProvider,
                "currencyProvider").strip();
        protectedProvenance = List.copyOf(Objects.requireNonNull(
                protectedProvenance, "protectedProvenance"));
        if (units <= 0L) {
            throw new IllegalArgumentException("Held custody liability must be positive");
        }
        if ((assetType == CustodyAssetType.PROTECTED_PHYSICAL_CURRENCY)
                != !protectedProvenance.isEmpty()) {
            throw new IllegalArgumentException(
                    "Held custody provenance does not match its asset type");
        }
    }
}

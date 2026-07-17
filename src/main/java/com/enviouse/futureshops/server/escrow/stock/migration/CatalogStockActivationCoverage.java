package com.enviouse.futureshops.server.escrow.stock.migration;

import java.util.ArrayList;
import java.util.List;

public record CatalogStockActivationCoverage(
        boolean serverBuyAndCart,
        boolean serverSell,
        boolean serverBarter,
        boolean administrativeMutations,
        boolean scheduledRefresh,
        boolean catalogReload
) {
    public static CatalogStockActivationCoverage legacyCallersPresent() {
        return new CatalogStockActivationCoverage(
                false, false, false, false, false, false);
    }

    public static CatalogStockActivationCoverage productionCutover() {
        return new CatalogStockActivationCoverage(
                true, true, true, true, true, true);
    }

    public boolean complete() {
        return blockers().isEmpty();
    }

    public List<String> blockers() {
        List<String> blockers = new ArrayList<>();
        if (!serverBuyAndCart) {
            blockers.add("server buy and cart");
        }
        if (!serverSell) {
            blockers.add("server sell");
        }
        if (!serverBarter) {
            blockers.add("server barter");
        }
        if (!administrativeMutations) {
            blockers.add("administrative stock mutations");
        }
        if (!scheduledRefresh) {
            blockers.add("scheduled stock refresh");
        }
        if (!catalogReload) {
            blockers.add("catalog reload reconciliation");
        }
        return List.copyOf(blockers);
    }
}

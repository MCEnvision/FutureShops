package com.enviouse.futureshops.client.market;

public enum MarketModuleAvailability {
    ENABLED,
    CLAIMS_ONLY,
    DISABLED,
    HIDDEN,
    FROZEN,
    DRAINING,
    CANCEL_AND_REFUND;

    public boolean visible() {
        return this == ENABLED || this == FROZEN
                || this == DRAINING || this == CANCEL_AND_REFUND;
    }

    public boolean allowsBrowse() {
        return this == ENABLED || this == FROZEN
                || this == DRAINING;
    }

    public boolean allowsClaims() {
        return this == ENABLED || this == CLAIMS_ONLY
                || this == FROZEN || this == DRAINING
                || this == CANCEL_AND_REFUND;
    }

    public boolean allowsNewValueOperations() {
        return this == ENABLED;
    }

    public boolean allowsOwnershipCancellationRoutes() {
        return this == ENABLED || this == DRAINING
                || this == CANCEL_AND_REFUND;
    }

    public boolean readOnly() {
        return this == FROZEN || this == DRAINING
                || this == CANCEL_AND_REFUND;
    }

    public boolean canOpenView(MarketModule module, String viewId) {
        if (this == ENABLED) {
            return true;
        }
        String view = java.util.Objects.requireNonNull(
                viewId, "viewId");
        if ("claims".equals(view)) {
            return allowsClaims();
        }
        if (this == FROZEN) {
            return readOnlyBrowseView(module, view);
        }
        if (this == DRAINING) {
            return readOnlyBrowseView(module, view)
                    || ownershipCancellationView(module, view);
        }
        if (this == CANCEL_AND_REFUND) {
            return ownershipCancellationView(module, view);
        }
        return false;
    }

    public static boolean ownershipCancellationView(
            MarketModule module,
            String viewId
    ) {
        java.util.Objects.requireNonNull(module, "module");
        String view = java.util.Objects.requireNonNull(
                viewId, "viewId");
        return switch (module) {
            case SHOP -> false;
            case BAZAAR -> "orders".equals(view)
                    || "buy_orders".equals(view)
                    || "sell_orders".equals(view);
            case AUCTION_HOUSE -> "mine".equals(view);
        };
    }

    private static boolean readOnlyBrowseView(
            MarketModule module,
            String view
    ) {
        java.util.Objects.requireNonNull(module, "module");
        return switch (module) {
            case SHOP -> "browse".equals(view);
            case BAZAAR -> "products".equals(view)
                    || MarketRoute.isDetailView(module, view)
                    || "portfolio".equals(view)
                    || "watched".equals(view)
                    || "history".equals(view);
            case AUCTION_HOUSE -> "browse".equals(view)
                    || MarketRoute.isDetailView(module, view)
                    || "bids".equals(view)
                    || "watched".equals(view)
                    || "history".equals(view);
        };
    }
}

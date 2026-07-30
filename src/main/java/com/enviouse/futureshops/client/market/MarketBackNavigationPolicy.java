package com.enviouse.futureshops.client.market;

import java.util.Objects;

public final class MarketBackNavigationPolicy {
    private MarketBackNavigationPolicy() {
    }

    public static boolean show(
            MarketModule module,
            String view,
            int historyDepth
    ) {
        Objects.requireNonNull(module, "module");
        Objects.requireNonNull(view, "view");
        return historyDepth > 0
                && MarketRoute.isDetailView(module, view);
    }
}

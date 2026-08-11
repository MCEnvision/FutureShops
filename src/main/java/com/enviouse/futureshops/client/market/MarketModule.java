package com.enviouse.futureshops.client.market;

import java.util.Locale;

public enum MarketModule {
    SHOP("shop", "Shop", "#9184D9", "browse"),
    BAZAAR("bazaar", "Bazaar", "#48B978", "products"),
    AUCTION_HOUSE("auction_house", "Auction House", "#D85B68", "browse");

    private final String id;
    private final String defaultDisplayName;
    private final String defaultAccent;
    private final String rootView;

    MarketModule(String id, String defaultDisplayName, String defaultAccent, String rootView) {
        this.id = id;
        this.defaultDisplayName = defaultDisplayName;
        this.defaultAccent = defaultAccent;
        this.rootView = rootView;
    }

    public String id() {
        return id;
    }

    public String defaultDisplayName() {
        return defaultDisplayName;
    }

    public String defaultAccent() {
        return defaultAccent;
    }

    public String rootView() {
        return rootView;
    }

    public static MarketModule fromId(String id) {
        if (id == null) {
            throw new IllegalArgumentException("Market module identifier is required.");
        }
        String normalized = id.strip().toLowerCase(Locale.ROOT);
        for (MarketModule module : values()) {
            if (module.id.equals(normalized)) {
                return module;
            }
        }
        throw new IllegalArgumentException("Unknown market module identifier.");
    }
}

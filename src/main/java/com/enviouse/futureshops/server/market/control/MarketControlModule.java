package com.enviouse.futureshops.server.market.control;

public enum MarketControlModule {
    SHOP(1, "shop"),
    BAZAAR(2, "bazaar"),
    AUCTION_HOUSE(3, "auction_house");

    private final int wireTag;
    private final String id;

    MarketControlModule(int wireTag, String id) {
        this.wireTag = wireTag;
        this.id = id;
    }

    public int wireTag() {
        return wireTag;
    }

    public String id() {
        return id;
    }

    public static MarketControlModule fromWireTag(int tag) {
        for (MarketControlModule module : values()) {
            if (module.wireTag == tag) {
                return module;
            }
        }
        throw new IllegalArgumentException(
                "Market control module tag is invalid");
    }
}

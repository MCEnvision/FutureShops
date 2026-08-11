package com.enviouse.futureshops.server.market.bazaar;

import java.util.Locale;

public enum BazaarProductIdentityPolicy {
    COMMODITY,
    EXACT;

    public static BazaarProductIdentityPolicy parse(String value) {
        if (value == null) {
            return COMMODITY;
        }
        try {
            return valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Bazaar product identity policy is invalid");
        }
    }
}

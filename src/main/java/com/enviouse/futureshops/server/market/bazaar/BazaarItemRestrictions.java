package com.enviouse.futureshops.server.market.bazaar;

public record BazaarItemRestrictions(
        boolean allowDamaged,
        boolean allowNamed,
        boolean allowEnchanted,
        boolean allowContainers,
        boolean allowCapabilities
) {
    public static BazaarItemRestrictions safeDefaults() {
        return new BazaarItemRestrictions(false, false, false,
                false, false);
    }
}

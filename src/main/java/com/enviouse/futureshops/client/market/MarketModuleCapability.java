package com.enviouse.futureshops.client.market;

import java.util.Objects;

public record MarketModuleCapability(
    MarketModule module,
    MarketModuleAvailability availability,
    String displayName,
    String accentHex,
    long openClaims,
    long revision
) {
    public MarketModuleCapability {
        Objects.requireNonNull(module, "module");
        Objects.requireNonNull(availability, "availability");
        if (displayName == null || displayName.isBlank() || displayName.length() > 64) {
            throw new IllegalArgumentException("Market display name must contain between one and sixty four characters.");
        }
        displayName = displayName.strip();
        MarketThemeColors.parseHex(accentHex);
        if (openClaims < 0L || revision < 0L) {
            throw new IllegalArgumentException("Market capability counters must not be negative.");
        }
        if (availability == MarketModuleAvailability.CLAIMS_ONLY && openClaims == 0L) {
            throw new IllegalArgumentException("Claims only modules must expose at least one claim.");
        }
        if ((availability == MarketModuleAvailability.HIDDEN
            || availability == MarketModuleAvailability.DISABLED) && openClaims > 0L) {
            throw new IllegalArgumentException("A disabled module with claims must use claims only availability.");
        }
    }

    public boolean canOpenView(String viewId) {
        Objects.requireNonNull(viewId, "viewId");
        return availability.allowsBrowse()
            || availability.allowsClaims() && "claims".equals(viewId);
    }
}

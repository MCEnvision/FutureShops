package com.enviouse.futureshops.client.market;

import java.util.Objects;

public record MarketTheme(
    MarketModule module,
    int accent,
    int accentDim,
    int selectedSurface,
    int activeBorder,
    int callToAction,
    int headerStart,
    int headerEnd,
    int focusHighlight,
    int textStrong,
    int textMuted,
    int semanticSuccess,
    int semanticWarning,
    int semanticDanger,
    int semanticInformation,
    boolean highContrast,
    boolean reducedMotion
) {
    public MarketTheme {
        Objects.requireNonNull(module, "module");
    }
}

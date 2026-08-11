package com.enviouse.futureshops.client.market;

import com.enviouse.futureshops.ClientConfig;

import java.util.Objects;

public final class MarketThemeResolver {
    private static final int SURFACE_BASE = 0xFF161826;
    private static final int SURFACE_RAISED = 0xFF232532;
    private static final int WHITE = 0xFFF3F5FE;
    private static final int TEXT = 0xFFE9E9ED;
    private static final int TEXT_MUTED = 0xFFB2B6CA;
    private static final int SUCCESS = 0xFF7FCF9E;
    private static final int WARNING = 0xFFE0B87A;
    private static final int DANGER = 0xFFE08B93;
    private static final int INFORMATION = 0xFFD2CEFD;

    private MarketThemeResolver() {
    }

    public static MarketTheme resolve(
        MarketModule module,
        String serverAccent,
        ClientConfig.Settings clientSettings
    ) {
        Objects.requireNonNull(module, "module");
        Objects.requireNonNull(clientSettings, "clientSettings");
        ClientConfig.Theme preference = clientSettings.theme();
        int accent = presetAccent(module, serverAccent, preference.preset());
        if (preference.customAccentEnabled()) {
            accent = MarketThemeColors.parseHex(preference.customAccent());
        }
        accent = MarketThemeColors.opaque(accent);
        accent = accessibleAccent(module, accent, clientSettings.accessibility().colorblindMode());
        boolean highContrast = clientSettings.accessibility().highContrast();
        int focus = highContrast ? WHITE : accent;
        int selected = MarketThemeColors.blend(SURFACE_RAISED, accent, highContrast ? 112 : 72);
        int headerStart = MarketThemeColors.blend(SURFACE_BASE, accent, highContrast ? 122 : 78);
        return new MarketTheme(
            module,
            accent,
            MarketThemeColors.withAlpha(accent, highContrast ? 104 : 68),
            selected,
            focus,
            accent,
            headerStart,
            SURFACE_BASE,
            focus,
            highContrast ? WHITE : TEXT,
            highContrast ? 0xFFD8DCEB : TEXT_MUTED,
            SUCCESS,
            WARNING,
            DANGER,
            INFORMATION,
            highContrast,
            clientSettings.accessibility().reducedMotion()
                || clientSettings.motion().animationSpeedPercent() == 0
        );
    }

    private static int presetAccent(MarketModule module, String serverAccent, String preset) {
        return switch (preset) {
            case "server" -> MarketThemeColors.parseHex(
                serverAccent == null || serverAccent.isBlank() ? module.defaultAccent() : serverAccent);
            case "nocturne" -> MarketThemeColors.parseHex(MarketModule.SHOP.defaultAccent());
            case "emerald" -> MarketThemeColors.parseHex(MarketModule.BAZAAR.defaultAccent());
            case "crimson" -> MarketThemeColors.parseHex(MarketModule.AUCTION_HOUSE.defaultAccent());
            default -> throw new IllegalArgumentException("Unknown client theme preset.");
        };
    }

    private static int accessibleAccent(MarketModule module, int accent, String mode) {
        return switch (mode) {
            case "none" -> accent;
            case "deuteranopia" -> switch (module) {
                case SHOP -> 0xFF8D82E8;
                case BAZAAR -> 0xFF3BA7C8;
                case AUCTION_HOUSE -> 0xFFE2823C;
            };
            case "protanopia" -> switch (module) {
                case SHOP -> 0xFF8B86E6;
                case BAZAAR -> 0xFF3BA9C7;
                case AUCTION_HOUSE -> 0xFFE0A13D;
            };
            case "tritanopia" -> switch (module) {
                case SHOP -> 0xFFB36DD2;
                case BAZAAR -> 0xFF51AD66;
                case AUCTION_HOUSE -> 0xFFD95F6B;
            };
            case "monochrome" -> switch (module) {
                case SHOP -> 0xFFC5C8D4;
                case BAZAAR -> 0xFFF0F2F7;
                case AUCTION_HOUSE -> 0xFF8C909D;
            };
            default -> throw new IllegalArgumentException("Unknown client colorblind mode.");
        };
    }
}

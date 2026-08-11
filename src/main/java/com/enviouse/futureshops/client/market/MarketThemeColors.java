package com.enviouse.futureshops.client.market;

import java.util.Locale;

public final class MarketThemeColors {
    private MarketThemeColors() {
    }

    public static int parseHex(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Theme color is required.");
        }
        String text = value.strip();
        if (text.startsWith("#")) {
            text = text.substring(1);
        }
        if (text.length() != 6 && text.length() != 8) {
            throw new IllegalArgumentException("Theme color must use six or eight hexadecimal digits.");
        }
        try {
            long parsed = Long.parseUnsignedLong(text.toUpperCase(Locale.ROOT), 16);
            return text.length() == 6 ? (int) (0xFF000000L | parsed) : (int) parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Theme color contains invalid hexadecimal digits.", exception);
        }
    }

    public static int withAlpha(int color, int alpha) {
        if (alpha < 0 || alpha > 255) {
            throw new IllegalArgumentException("Theme alpha must be between zero and two hundred fifty five.");
        }
        return alpha << 24 | color & 0x00FFFFFF;
    }

    public static int opaque(int color) {
        return 0xFF000000 | color & 0x00FFFFFF;
    }

    public static int blend(int left, int right, int rightWeight) {
        if (rightWeight < 0 || rightWeight > 255) {
            throw new IllegalArgumentException("Theme blend weight must be between zero and two hundred fifty five.");
        }
        int leftWeight = 255 - rightWeight;
        int alpha = channel(left, 24) * leftWeight + channel(right, 24) * rightWeight;
        int red = channel(left, 16) * leftWeight + channel(right, 16) * rightWeight;
        int green = channel(left, 8) * leftWeight + channel(right, 8) * rightWeight;
        int blue = channel(left, 0) * leftWeight + channel(right, 0) * rightWeight;
        return rounded(alpha) << 24 | rounded(red) << 16 | rounded(green) << 8 | rounded(blue);
    }

    private static int channel(int color, int shift) {
        return color >>> shift & 0xFF;
    }

    private static int rounded(int weighted) {
        return (weighted + 127) / 255;
    }
}

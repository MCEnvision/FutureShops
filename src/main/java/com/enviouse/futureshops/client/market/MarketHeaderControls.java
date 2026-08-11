package com.enviouse.futureshops.client.market;

import java.util.List;
import java.util.Objects;

public record MarketHeaderControls(
    MarketRectangle search,
    MarketRectangle balance,
    MarketRectangle profile,
    MarketRectangle notifications,
    MarketRectangle claims,
    MarketRectangle back,
    MarketRectangle close
) {
    public MarketHeaderControls {
        Objects.requireNonNull(search, "search");
        Objects.requireNonNull(balance, "balance");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(notifications, "notifications");
        Objects.requireNonNull(claims, "claims");
        Objects.requireNonNull(back, "back");
        Objects.requireNonNull(close, "close");
    }

    public static MarketHeaderControls compute(
        MarketRectangle header,
        MarketLayoutMode mode,
        boolean showBack
    ) {
        MarketRectangle bounds = Objects.requireNonNull(header, "header");
        MarketLayoutMode layoutMode = Objects.requireNonNull(mode, "mode");
        int gap = bounds.width() >= 96 ? 4 : 0;
        int controlHeight = Math.min(18, bounds.height());
        int controlY = bounds.y() + Math.min(5,
            Math.max(0, bounds.height() - controlHeight));
        int cursor = bounds.right() - Math.min(8, bounds.width());
        MarketRectangle close = take(bounds, cursor, 16,
            controlY, controlHeight);
        cursor = before(bounds, close, gap);
        MarketRectangle back = showBack
            ? take(bounds, cursor, 16, controlY, controlHeight)
            : new MarketRectangle(cursor, controlY, 0, controlHeight);
        if (showBack) {
            cursor = before(bounds, back, gap);
        }
        int[] widths = switch (layoutMode) {
            case WIDE -> new int[]{44, 44, 82, 72};
            case MEDIUM -> new int[]{34, 30, 24, 54};
            case NARROW -> new int[]{34, 30, 20, 48};
        };
        MarketRectangle claims = take(bounds, cursor, widths[0],
            controlY, controlHeight);
        cursor = before(bounds, claims, gap);
        MarketRectangle notifications = take(bounds, cursor, widths[1],
            controlY, controlHeight);
        cursor = before(bounds, notifications, gap);
        MarketRectangle profile = take(bounds, cursor, widths[2],
            controlY, controlHeight);
        cursor = before(bounds, profile, gap);
        MarketRectangle balance = take(bounds, cursor, widths[3],
            controlY, controlHeight);
        cursor = before(bounds, balance, gap);
        MarketRectangle search;
        if (layoutMode == MarketLayoutMode.NARROW) {
            int x = bounds.x() + Math.min(8, bounds.width());
            int y = bounds.y() + Math.min(30, bounds.height());
            int width = Math.max(0, bounds.right()
                - Math.min(8, bounds.width()) - x);
            int height = Math.min(16,
                Math.max(0, bounds.bottom() - y));
            search = new MarketRectangle(x, y, width, height);
        } else {
            int desired = layoutMode == MarketLayoutMode.WIDE ? 180 : 120;
            int minimumX = bounds.x() + Math.min(bounds.width(),
                layoutMode == MarketLayoutMode.WIDE ? 320 : 270);
            int width = Math.min(desired,
                Math.max(0, cursor - minimumX));
            search = new MarketRectangle(cursor - width, controlY,
                width, controlHeight);
        }
        return new MarketHeaderControls(search, balance, profile,
            notifications, claims, back, close);
    }

    public List<MarketRectangle> accountPills() {
        return List.of(balance, profile, notifications, claims);
    }

    private static MarketRectangle take(
        MarketRectangle bounds,
        int cursor,
        int desiredWidth,
        int y,
        int height
    ) {
        int width = Math.min(desiredWidth,
            Math.max(0, cursor - bounds.x()));
        return new MarketRectangle(cursor - width, y, width, height);
    }

    private static int before(
        MarketRectangle bounds,
        MarketRectangle rectangle,
        int gap
    ) {
        if (rectangle.width() == 0) {
            return rectangle.x();
        }
        return Math.max(bounds.x(), rectangle.x() - gap);
    }
}

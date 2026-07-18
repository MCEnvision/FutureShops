package com.enviouse.futureshops.client.market;

import java.util.Locale;

public final class MarketLayoutEngine {
    private MarketLayoutEngine() {
    }

    public static MarketLayout compute(MarketViewport viewport, String density, String cardSize) {
        if (viewport == null) {
            throw new IllegalArgumentException("Market viewport is required.");
        }
        String normalizedDensity = normalize(density);
        String normalizedCardSize = normalize(cardSize);
        int width = Math.min(viewport.guiWidth(),
            Math.max(300, Math.max(1, viewport.guiWidth() - 4)));
        int height = Math.min(viewport.guiHeight(),
            Math.max(176, Math.max(1, viewport.guiHeight() - 4)));
        int left = Math.max(0, (viewport.guiWidth() - width) / 2);
        int top = Math.max(0, (viewport.guiHeight() - height) / 2);
        MarketLayoutMode mode = modeFor(width);
        int desiredPadding = switch (normalizedDensity) {
            case "compact" -> 6;
            case "normal" -> 9;
            case "comfortable" -> 12;
            default -> throw new IllegalArgumentException("Unknown market density.");
        };
        int padding = Math.min(desiredPadding, Math.max(0, (width - 1) / 2));
        int desiredHeaderHeight = height < 300 ? 30 : 36;
        int desiredBreadcrumbHeight = height < 220 ? 0 : 16;
        int desiredFooterHeight = height < 205 ? 22 : 28;
        int desiredToolbarHeight = mode == MarketLayoutMode.NARROW ? 38 : 24;
        int desiredSecondaryTabsHeight = mode == MarketLayoutMode.NARROW
            ? 22 : 0;
        int headerHeight = Math.min(desiredHeaderHeight, height);
        int remainingHeight = height - headerHeight;
        int footerHeight = Math.min(desiredFooterHeight, remainingHeight);
        remainingHeight -= footerHeight;
        int breadcrumbHeight = Math.min(desiredBreadcrumbHeight, remainingHeight);
        remainingHeight -= breadcrumbHeight;
        int secondaryTabsHeight = Math.min(
            desiredSecondaryTabsHeight, remainingHeight);
        remainingHeight -= secondaryTabsHeight;
        int toolbarHeight = Math.min(desiredToolbarHeight, remainingHeight);
        int railWidth = switch (mode) {
            case WIDE -> Math.min(196, Math.max(148, width / 5));
            case MEDIUM -> Math.min(154, Math.max(116, width / 5));
            case NARROW -> 0;
        };
        int innerX = left + padding;
        int innerWidth = Math.max(1, width - padding * 2);
        int bodyTop = top + headerHeight + breadcrumbHeight;
        int bodyBottom = top + height - footerHeight;
        int mainTop = bodyTop + secondaryTabsHeight;
        int contentX = innerX + (railWidth == 0 ? 0 : railWidth + padding);
        int contentWidth = Math.max(1, innerX + innerWidth - contentX);
        int toolbarY = mainTop;
        int contentY = toolbarY + toolbarHeight;
        int contentHeight = Math.max(0, bodyBottom - contentY);
        int targetCardWidth = switch (normalizedCardSize) {
            case "small" -> 128;
            case "medium" -> 164;
            case "large" -> 208;
            default -> throw new IllegalArgumentException("Unknown market card size.");
        };
        if (normalizedDensity.equals("compact")) {
            targetCardWidth = Math.max(96, targetCardWidth - 20);
        } else if (normalizedDensity.equals("comfortable")) {
            targetCardWidth += 20;
        }
        int columns = mode == MarketLayoutMode.NARROW
            ? 1
            : Math.max(1, (contentWidth + padding) / (targetCardWidth + padding));
        MarketRectangle window = new MarketRectangle(left, top, width, height);
        MarketRectangle header = new MarketRectangle(left, top, width, headerHeight);
        MarketRectangle breadcrumb = new MarketRectangle(left, top + headerHeight, width, breadcrumbHeight);
        MarketRectangle secondaryTabs = new MarketRectangle(left,
            bodyTop, width, secondaryTabsHeight);
        MarketRectangle rail = new MarketRectangle(innerX, mainTop,
            railWidth, Math.max(0, bodyBottom - mainTop));
        MarketRectangle toolbar = new MarketRectangle(contentX, toolbarY, contentWidth, toolbarHeight);
        MarketRectangle content = new MarketRectangle(contentX, contentY, contentWidth, contentHeight);
        MarketRectangle footer = new MarketRectangle(left, bodyBottom, width, footerHeight);
        return new MarketLayout(
            mode,
            window,
            header,
            breadcrumb,
            secondaryTabs,
            rail,
            toolbar,
            content,
            footer,
            columns,
            padding,
            mode == MarketLayoutMode.WIDE,
            mode == MarketLayoutMode.NARROW,
            secondaryTabsHeight > 0
        );
    }

    private static MarketLayoutMode modeFor(int width) {
        if (width >= 760) {
            return MarketLayoutMode.WIDE;
        }
        if (width >= 520) {
            return MarketLayoutMode.MEDIUM;
        }
        return MarketLayoutMode.NARROW;
    }

    private static String normalize(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Market layout option is required.");
        }
        return value.strip().toLowerCase(Locale.ROOT);
    }
}

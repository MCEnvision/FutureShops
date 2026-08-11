package com.enviouse.futureshops.client.market;

public final class MarketCompactPager {
    private MarketCompactPager() {
    }

    public static Window window(
        int itemCount,
        int requestedOffset,
        int capacity
    ) {
        if (itemCount < 0 || requestedOffset < 0 || capacity <= 0
            || capacity > 256) {
            throw new IllegalArgumentException(
                "Market compact pager input is invalid.");
        }
        int maximumOffset = Math.max(0, itemCount - capacity);
        int offset = Math.min(requestedOffset, maximumOffset);
        int end = Math.min(itemCount, offset + capacity);
        return new Window(offset, end, offset > 0, end < itemCount);
    }

    public static int ensureVisible(
        int itemCount,
        int requestedOffset,
        int capacity,
        int selectedIndex
    ) {
        if (selectedIndex < 0 || selectedIndex >= itemCount) {
            throw new IllegalArgumentException(
                "Market compact selection is invalid.");
        }
        Window current = window(itemCount, requestedOffset, capacity);
        if (selectedIndex < current.offset()) {
            return selectedIndex;
        }
        if (selectedIndex >= current.end()) {
            return selectedIndex - capacity + 1;
        }
        return current.offset();
    }

    public static int nextOffset(
        int itemCount,
        int requestedOffset,
        int capacity
    ) {
        Window current = window(itemCount, requestedOffset, capacity);
        return Math.min(Math.max(0, itemCount - capacity),
            current.offset() + capacity);
    }

    public static int previousOffset(
        int itemCount,
        int requestedOffset,
        int capacity
    ) {
        Window current = window(itemCount, requestedOffset, capacity);
        return Math.max(0, current.offset() - capacity);
    }

    public record Window(
        int offset,
        int end,
        boolean hasPrevious,
        boolean hasNext
    ) {
        public Window {
            if (offset < 0 || end < offset) {
                throw new IllegalArgumentException(
                    "Market compact window is invalid.");
            }
        }

        public int size() {
            return end - offset;
        }
    }
}

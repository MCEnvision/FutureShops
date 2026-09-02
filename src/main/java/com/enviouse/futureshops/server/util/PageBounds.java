package com.enviouse.futureshops.server.util;

/** Shared bounds for server side paged queries. */
public final class PageBounds {
    public static final int MAX_PAGE_INDEX = 1_000_000;
    public static final int MAX_PAGE_SIZE = 100;

    private PageBounds() {
    }

    public static boolean isValid(int page, int pageSize) {
        return page >= 1 && page <= MAX_PAGE_INDEX
                && pageSize >= 1 && pageSize <= MAX_PAGE_SIZE;
    }

    public static int normalizePage(int page) {
        return Math.max(1, Math.min(MAX_PAGE_INDEX, page));
    }

    public static int normalizePageSize(int pageSize) {
        return Math.max(1, Math.min(MAX_PAGE_SIZE, pageSize));
    }

    public static long offset(int page, int pageSize) {
        return ((long) normalizePage(page) - 1L) * normalizePageSize(pageSize);
    }
}

package com.enviouse.futureshopsp.client;

import java.util.List;

/**
 * Client-side state for the department search/picker UI.
 */
public final class DepartmentClientState {
    private static List<String> searchResults = List.of();

    private DepartmentClientState() {}

    public static void setSearchResults(List<String> results) {
        searchResults = results == null ? List.of() : List.copyOf(results);
    }

    public static List<String> getSearchResults() {
        return searchResults;
    }

    public static void clear() {
        searchResults = List.of();
    }
}


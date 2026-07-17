package com.enviouse.futureshops.client.screen;

import java.util.Locale;

public final class AdminItemSearchPolicy {
    private AdminItemSearchPolicy() {
    }

    public static boolean matches(String itemId, String searchText,
                                  String query) {
        String normalizedId = normalize(itemId);
        String normalizedQuery = normalize(query);
        if (normalizedQuery.isBlank()) {
            return true;
        }
        if (normalizedQuery.startsWith("@")) {
            String wantedNamespace = normalizedQuery.substring(1);
            int separator = normalizedId.indexOf(':');
            String namespace = separator >= 0
                    ? normalizedId.substring(0, separator) : normalizedId;
            return namespace.startsWith(wantedNamespace);
        }
        return normalize(searchText).contains(normalizedQuery);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}

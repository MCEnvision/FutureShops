package com.enviouse.futureshops.client.screen;

import java.util.Locale;

public final class AdminItemSearchPolicy {
    private AdminItemSearchPolicy() {
    }

    public static boolean matches(String itemId, String searchText,
                                  String query) {
        return matches(itemId, searchText, "", query);
    }

    public static boolean matches(
            String itemId,
            String searchText,
            String modDisplayName,
            String query
    ) {
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
            String modName = normalize(modDisplayName);
            String compactWanted = compact(wantedNamespace);
            return namespace.startsWith(wantedNamespace)
                    || modName.startsWith(wantedNamespace)
                    || (!compactWanted.isEmpty()
                    && compact(modName).startsWith(compactWanted));
        }
        return normalize(searchText).contains(normalizedQuery);
    }

    private static String compact(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isLetterOrDigit(character)) {
                result.append(character);
            }
        }
        return result.toString();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}

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
        return matches(itemId, searchText, modDisplayName, "", query);
    }

    public static boolean matches(
            String itemId,
            String searchText,
            String modDisplayName,
            String tagText,
            String query
    ) {
        String normalizedId = normalize(itemId);
        String normalizedQuery = normalize(query);
        if (normalizedQuery.isBlank()) {
            return true;
        }
        String normalizedSearch = normalize(searchText);
        String normalizedTags = normalize(tagText);
        for (String token : normalizedQuery.split("\\s+")) {
            if (!matchesToken(normalizedId, normalizedSearch,
                    normalize(modDisplayName), normalizedTags, token)) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesToken(
            String itemId,
            String searchText,
            String modDisplayName,
            String tagText,
            String token
    ) {
        if (token.startsWith("@")) {
            String wantedNamespace = token.substring(1);
            int separator = itemId.indexOf(':');
            String namespace = separator >= 0
                    ? itemId.substring(0, separator) : itemId;
            String compactWanted = compact(wantedNamespace);
            return namespace.startsWith(wantedNamespace)
                    || modDisplayName.startsWith(wantedNamespace)
                    || (!compactWanted.isEmpty()
                    && compact(modDisplayName).startsWith(compactWanted));
        }
        if (token.startsWith("#")) {
            return tagText.contains(token.substring(1));
        }
        return searchText.contains(token) || tagText.contains(token);
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

package com.enviouse.futureshops.config;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ConfigValidation {
    private ConfigValidation() {
    }

    public static boolean isOption(Object value, Set<String> allowed) {
        return value instanceof String text && allowed.contains(normalize(text));
    }

    public static String requireOption(String value, Set<String> allowed, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required.");
        }
        String normalized = normalize(value);
        if (!allowed.contains(normalized)) {
            throw new IllegalArgumentException(field + " has an unsupported value. " + value);
        }
        return normalized;
    }

    public static boolean isHexColor(Object value) {
        if (!(value instanceof String text)) {
            return false;
        }
        String normalized = text.startsWith("#") ? text.substring(1) : text;
        if (normalized.length() != 6 && normalized.length() != 8) {
            return false;
        }
        for (int index = 0; index < normalized.length(); index++) {
            if (Character.digit(normalized.charAt(index), 16) < 0) {
                return false;
            }
        }
        return true;
    }

    public static String requireHexColor(String value, String field) {
        if (!isHexColor(value)) {
            throw new IllegalArgumentException(field + " must be a six or eight digit hexadecimal color.");
        }
        String normalized = value.startsWith("#") ? value : "#" + value;
        return normalized.toUpperCase(Locale.ROOT);
    }

    public static List<Integer> requirePositiveList(List<? extends Integer> values, String field) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be empty.");
        }
        List<Integer> copy = List.copyOf(values);
        if (copy.stream().anyMatch(value -> value == null || value <= 0)) {
            throw new IllegalArgumentException(field + " must contain positive values.");
        }
        if (copy.stream().distinct().count() != copy.size()) {
            throw new IllegalArgumentException(field + " must not contain duplicate values.");
        }
        return copy;
    }

    public static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    private static String normalize(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }
}

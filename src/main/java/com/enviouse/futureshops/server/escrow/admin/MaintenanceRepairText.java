package com.enviouse.futureshops.server.escrow.admin;

import java.util.Objects;

final class MaintenanceRepairText {
    private MaintenanceRepairText() {
    }

    static String require(String value, String label, int maximumLength) {
        String normalized = Objects.requireNonNull(value, label).trim();
        if (normalized.isEmpty() || normalized.length() > maximumLength
                || !wellFormedUtf16(normalized)) {
            throw new IllegalArgumentException("Invalid maintenance " + label);
        }
        return normalized;
    }

    private static boolean wellFormedUtf16(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isISOControl(character)) {
                return false;
            }
            if (Character.isHighSurrogate(character)) {
                if (index + 1 >= value.length()
                        || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    return false;
                }
                index++;
            } else if (Character.isLowSurrogate(character)) {
                return false;
            }
        }
        return true;
    }
}

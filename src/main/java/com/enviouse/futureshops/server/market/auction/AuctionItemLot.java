package com.enviouse.futureshops.server.market.auction;

import java.util.Locale;
import java.util.UUID;

public record AuctionItemLot(
    UUID custodyLotId,
    String registryId,
    String fingerprint,
    int count,
    int serializedBytes,
    String categoryId,
    String searchDocument
) {
    public AuctionItemLot {
        requireId(custodyLotId, "custody lot");
        registryId = bounded(registryId, 256, false, "Auction item registry identifier");
        if (!validResourceIdentifier(registryId)) {
            throw new IllegalArgumentException("Auction item registry identifier is invalid.");
        }
        fingerprint = bounded(fingerprint, 64, false, "Auction item fingerprint").toLowerCase(Locale.ROOT);
        if (fingerprint.length() != 64 || !fingerprint.chars().allMatch(AuctionItemLot::isHex)) {
            throw new IllegalArgumentException("Auction item fingerprint must use sixty four hexadecimal digits.");
        }
        if (count <= 0 || serializedBytes <= 0 || serializedBytes > 1048576) {
            throw new IllegalArgumentException("Auction item count or serialized size is invalid.");
        }
        categoryId = bounded(categoryId, 128, true, "Auction category identifier");
        if (!categoryId.chars().allMatch(AuctionItemLot::isCategoryCharacter)) {
            throw new IllegalArgumentException("Auction category identifier is invalid.");
        }
        searchDocument = bounded(searchDocument, 4096, false, "Auction search document");
    }

    private static String bounded(String value, int maximum, boolean allowEmpty, String label) {
        if (value == null) {
            throw new IllegalArgumentException(label + " is required.");
        }
        String normalized = value.strip();
        if (!value.equals(normalized)
            || (!allowEmpty && normalized.isEmpty())
            || normalized.length() > maximum
            || !validText(normalized)) {
            throw new IllegalArgumentException(label + " has an invalid length.");
        }
        return normalized;
    }

    private static boolean validResourceIdentifier(String value) {
        int separator = value.indexOf(':');
        if (separator <= 0 || separator == value.length() - 1
            || separator != value.lastIndexOf(':')) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (index == separator) {
                continue;
            }
            boolean namespace = index < separator;
            boolean valid = character >= 'a' && character <= 'z'
                || character >= '0' && character <= '9'
                || character == '_'
                || character == '-'
                || character == '.'
                || !namespace && character == '/';
            if (!valid) {
                return false;
            }
        }
        return true;
    }

    private static boolean validText(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isHighSurrogate(character)) {
                if (index + 1 >= value.length()
                    || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    return false;
                }
                index++;
            } else if (Character.isLowSurrogate(character)
                || Character.isISOControl(character)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isCategoryCharacter(int character) {
        return character >= 'a' && character <= 'z'
            || character >= '0' && character <= '9'
            || character == '_'
            || character == '-'
            || character == '.'
            || character == '/';
    }

    private static boolean isHex(int character) {
        return Character.digit(character, 16) >= 0;
    }

    private static void requireId(UUID id, String label) {
        if (id == null || id.equals(new UUID(0L, 0L))) {
            throw new IllegalArgumentException("Auction " + label + " identifier is required.");
        }
    }
}

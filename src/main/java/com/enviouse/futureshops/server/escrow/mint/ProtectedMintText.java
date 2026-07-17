package com.enviouse.futureshops.server.escrow.mint;

final class ProtectedMintText {
    static final int MAX_REQUEST_KEY_LENGTH = 160;
    static final int MAX_SERVER_EVIDENCE_LENGTH = 256;
    static final int MAX_CHECKSUM_EVIDENCE_LENGTH = 512;

    private ProtectedMintText() {
    }

    static String requestKey(String value) {
        return require(value, MAX_REQUEST_KEY_LENGTH, "Protected mint request key");
    }

    static String serverEvidence(String value) {
        return require(value, MAX_SERVER_EVIDENCE_LENGTH, "Protected mint server evidence");
    }

    static String checksumEvidence(String value) {
        return require(value, MAX_CHECKSUM_EVIDENCE_LENGTH, "Protected mint checksum evidence");
    }

    private static String require(String value, int maximumLength, String label) {
        if (value == null) {
            throw new NullPointerException(label);
        }
        if (value.isEmpty() || value.length() > maximumLength
                || !value.equals(value.trim()) || !wellFormedUtf16(value)) {
            throw new IllegalArgumentException(label + " is invalid");
        }
        return value;
    }

    private static boolean wellFormedUtf16(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
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

package com.enviouse.futureshops.server.escrow.store;

import com.enviouse.futureshops.server.escrow.journal.WriteAheadJournal;

final class EscrowCodecLimits {
    static final int MAX_PARTICIPANTS = 64;
    static final int MAX_ASSET_LOTS = 4096;
    static final int MAX_PAYLOAD_BYTES = 1_048_576;
    static final int MAX_TOTAL_PAYLOAD_BYTES = 8_388_608;
    static final int MAX_ATTRIBUTES = 128;
    static final int MAX_ERROR_DETAILS = 128;
    static final int MAX_MAP_KEY_LENGTH = 256;
    static final int MAX_MAP_VALUE_LENGTH = 4096;
    static final int MAX_BINARY_BYTES = WriteAheadJournal.MAX_PAYLOAD_BYTES - 32;

    private EscrowCodecLimits() {
    }

    static void requireCount(String field, int count, int maximum) {
        if (count < 0 || count > maximum) {
            throw new IllegalStateException(field + " exceeds its limit");
        }
    }

    static String requireString(String field, String value, int maximumLength) {
        if (value == null || value.isEmpty() || value.length() > maximumLength) {
            throw new IllegalStateException(field + " is invalid");
        }
        return value;
    }

    static byte[] requirePayload(byte[] payload) {
        if (payload == null || payload.length > MAX_PAYLOAD_BYTES) {
            throw new IllegalStateException("Escrow asset payload exceeds its limit");
        }
        return payload;
    }
}

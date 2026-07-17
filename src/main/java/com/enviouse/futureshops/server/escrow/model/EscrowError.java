package com.enviouse.futureshops.server.escrow.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record EscrowError(
        String code,
        String message,
        boolean retryable,
        Instant occurredAt,
        Map<String, String> details
) {
    public static final int MAX_CODE_LENGTH = 64;
    public static final int MAX_MESSAGE_LENGTH = 1024;
    public static final int MAX_DETAIL_COUNT = 32;
    public static final int MAX_DETAIL_KEY_LENGTH = 64;
    public static final int MAX_DETAIL_VALUE_LENGTH = 512;

    public EscrowError {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(details, "details");
        code = code.strip();
        message = message.strip();
        if (code.isEmpty() || code.length() > MAX_CODE_LENGTH || !code.matches("[A-Z0-9_]+")) {
            throw new IllegalArgumentException("Invalid escrow error code");
        }
        if (message.isEmpty() || message.length() > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("Invalid escrow error message");
        }
        details = Map.copyOf(details);
        if (details.size() > MAX_DETAIL_COUNT) {
            throw new IllegalArgumentException("Too many escrow error details");
        }
        for (Map.Entry<String, String> entry : details.entrySet()) {
            if (entry.getKey().isBlank()
                    || entry.getValue().isBlank()
                    || entry.getKey().length() > MAX_DETAIL_KEY_LENGTH
                    || entry.getValue().length() > MAX_DETAIL_VALUE_LENGTH) {
                throw new IllegalArgumentException("Escrow error details cannot be blank");
            }
        }
    }
}

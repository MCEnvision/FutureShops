package com.enviouse.futureshops.server.escrow.runtime;

import java.util.Arrays;
import java.util.Objects;

public record EscrowJournalEvent(EscrowJournalEventType type, byte[] body) {
    public EscrowJournalEvent {
        Objects.requireNonNull(type, "type");
        body = Arrays.copyOf(Objects.requireNonNull(body, "body"), body.length);
        if (body.length == 0 || body.length > EscrowJournalEventCodec.MAX_BODY_BYTES) {
            throw new IllegalArgumentException("Invalid escrow journal event body");
        }
    }

    @Override
    public byte[] body() {
        return Arrays.copyOf(body, body.length);
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) {
            return true;
        }
        return value instanceof EscrowJournalEvent other
                && type == other.type
                && Arrays.equals(body, other.body);
    }

    @Override
    public int hashCode() {
        return 31 * type.hashCode() + Arrays.hashCode(body);
    }
}

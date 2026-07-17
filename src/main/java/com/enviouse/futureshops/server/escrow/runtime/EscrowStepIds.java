package com.enviouse.futureshops.server.escrow.runtime;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

public final class EscrowStepIds {
    private static final byte[] NAMESPACE = "futureshops.escrow.step.v1".getBytes(StandardCharsets.UTF_8);

    private EscrowStepIds() {
    }

    public static UUID forEvent(UUID transactionId, EscrowJournalEvent event) {
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(event, "event");
        byte[] body = event.body();
        ByteBuffer canonical = ByteBuffer.allocate(
                NAMESPACE.length + Integer.BYTES + Long.BYTES * 2 + body.length);
        canonical.put(NAMESPACE);
        canonical.putInt(event.type().wireId());
        canonical.putLong(transactionId.getMostSignificantBits());
        canonical.putLong(transactionId.getLeastSignificantBits());
        canonical.put(body);
        return UUID.nameUUIDFromBytes(canonical.array());
    }
}

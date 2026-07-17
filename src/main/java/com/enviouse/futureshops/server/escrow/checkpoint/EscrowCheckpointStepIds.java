package com.enviouse.futureshops.server.escrow.checkpoint;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

public final class EscrowCheckpointStepIds {
    private static final byte[] NAMESPACE =
            "futureshops.escrow.checkpoint.reference.v1".getBytes(StandardCharsets.UTF_8);

    private EscrowCheckpointStepIds() {
    }

    public static UUID forReference(EscrowCheckpointReference reference) {
        Objects.requireNonNull(reference, "reference");
        byte[] encoded = EscrowCheckpointReferenceCodec.encode(reference);
        ByteBuffer canonical = ByteBuffer.allocate(NAMESPACE.length + encoded.length);
        canonical.put(NAMESPACE);
        canonical.put(encoded);
        return UUID.nameUUIDFromBytes(canonical.array());
    }
}

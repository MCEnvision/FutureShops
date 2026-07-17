package com.enviouse.futureshops.server.escrow.playershop;

import java.util.Objects;
import java.util.UUID;

public record PlayerShopEscrowLifecycleEvent(
        UUID eventId,
        UUID requestId,
        long expectedRevision,
        long nextRevision,
        PlayerShopExecutionSnapshot snapshot,
        boolean settlementImported
) {
    public PlayerShopEscrowLifecycleEvent {
        eventId = PlayerShopBinarySupport.requireUuid(eventId, "lifecycle event id");
        requestId = PlayerShopBinarySupport.requireUuid(requestId,
                "lifecycle request id");
        snapshot = Objects.requireNonNull(snapshot, "snapshot");
        if (!requestId.equals(snapshot.intent().requestId())
                || expectedRevision < -1L
                || nextRevision != Math.addExact(expectedRevision, 1L)
                || settlementImported
                && snapshot.settlementImport() == null
                || !deterministicEventId(requestId, nextRevision)
                .equals(eventId)) {
            throw new IllegalArgumentException("Player shop lifecycle event is invalid");
        }
    }

    public static PlayerShopEscrowLifecycleEvent advance(
            PlayerShopExecutionSnapshot snapshot,
            long expectedRevision,
            boolean settlementImported
    ) {
        Objects.requireNonNull(snapshot, "snapshot");
        long next = Math.addExact(expectedRevision, 1L);
        UUID requestId = snapshot.intent().requestId();
        return new PlayerShopEscrowLifecycleEvent(
                deterministicEventId(requestId, next), requestId,
                expectedRevision, next, snapshot, settlementImported);
    }

    private static UUID deterministicEventId(UUID requestId, long revision) {
        return PlayerShopBinarySupport.deterministicUuid(
                "lifecycle event", requestId, Long.toString(revision));
    }
}

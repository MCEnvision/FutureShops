package com.enviouse.futureshops.server.market.bazaar;

import java.util.Objects;
import java.util.UUID;

public record CancelBazaarOrderCommand(
        UUID requestId,
        UUID orderId,
        UUID actorId,
        UUID terminalTransactionId,
        long expectedRevision,
        long nowMillis
) {
    public CancelBazaarOrderCommand {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(terminalTransactionId, "terminalTransactionId");
    }
}

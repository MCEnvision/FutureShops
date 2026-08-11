package com.enviouse.futureshops.server.market.bazaar;

import java.util.Objects;
import java.util.UUID;

public record ExpireBazaarOrderCommand(
        UUID requestId,
        UUID orderId,
        UUID terminalTransactionId,
        long expectedRevision,
        long nowMillis
) {
    public ExpireBazaarOrderCommand {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(terminalTransactionId, "terminalTransactionId");
    }
}

package com.enviouse.futureshops.server.market.bazaar;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record CreateBazaarOrderCommand(
        UUID requestId,
        UUID orderId,
        UUID ownerId,
        UUID activationTransactionId,
        Optional<UUID> moneyHoldAccountId,
        Optional<UUID> custodyLotId,
        String productId,
        long productVersion,
        BazaarOrderSide side,
        BazaarOrderType type,
        BazaarTimeInForce timeInForce,
        long limitPriceMinor,
        int quantity,
        long createdAtMillis,
        long expiresAtMillis,
        BazaarRuleSnapshot rules
) {
    public CreateBazaarOrderCommand {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(activationTransactionId, "activationTransactionId");
        moneyHoldAccountId = Objects.requireNonNull(moneyHoldAccountId, "moneyHoldAccountId");
        custodyLotId = Objects.requireNonNull(custodyLotId, "custodyLotId");
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(timeInForce, "timeInForce");
        Objects.requireNonNull(rules, "rules");
    }
}

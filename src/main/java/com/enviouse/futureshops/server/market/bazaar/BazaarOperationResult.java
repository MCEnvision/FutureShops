package com.enviouse.futureshops.server.market.bazaar;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record BazaarOperationResult(
        UUID requestId,
        UUID orderId,
        BazaarOperationType operation,
        BazaarOperationStatus status,
        boolean replayed,
        long observedRevision,
        Optional<BazaarOrder> order,
        List<BazaarFill> fills,
        List<BazaarEscrowSettlement> settlements,
        List<UUID> cancelledMakerOrderIds,
        boolean productHalted
) {
    public BazaarOperationResult {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(status, "status");
        order = Objects.requireNonNull(order, "order");
        fills = List.copyOf(fills);
        settlements = List.copyOf(settlements);
        cancelledMakerOrderIds = List.copyOf(cancelledMakerOrderIds);
        if (observedRevision < -1L) {
            throw new IllegalArgumentException("Bazaar observed revision is invalid");
        }
        if (order.isPresent() && !order.orElseThrow().orderId().equals(orderId)
                || order.isPresent() && order.orElseThrow().revision() != observedRevision
                || order.isEmpty() && observedRevision != -1L) {
            throw new IllegalArgumentException("Bazaar result order evidence is invalid");
        }
        if (status != BazaarOperationStatus.APPLIED
                && (!fills.isEmpty() || !settlements.isEmpty()
                || !cancelledMakerOrderIds.isEmpty() || productHalted)) {
            throw new IllegalArgumentException("Rejected Bazaar result cannot move value");
        }
    }

    public boolean newlyCommitted() {
        return status == BazaarOperationStatus.APPLIED && !replayed;
    }

    public boolean durablyApplied() {
        return status == BazaarOperationStatus.APPLIED;
    }

    public BazaarOperationResult asReplay() {
        return replayed ? this : new BazaarOperationResult(requestId, orderId, operation,
                status, true, observedRevision, order, fills, settlements,
                cancelledMakerOrderIds, productHalted);
    }
}

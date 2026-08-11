package com.enviouse.futureshops.server.market.auction.escrow;

import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationReceipt;

import java.util.Objects;
import java.util.Optional;

public record AuctionCreateRecoveryDecision(
        Action action,
        Optional<ItemInventoryMutationReceipt> receipt
) {
    public AuctionCreateRecoveryDecision {
        action = Objects.requireNonNull(action, "action");
        receipt = Objects.requireNonNull(receipt, "receipt");
        if ((action == Action.COMMIT) != receipt.isPresent()) {
            throw new IllegalArgumentException(
                    "Auction creation recovery receipt shape is invalid");
        }
    }

    public static AuctionCreateRecoveryDecision decide(
            AuctionCreateEscrowIntent intent,
            CustodyState custodyState,
            Optional<ItemInventoryMutationReceipt> receipt
    ) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(custodyState, "custodyState");
        receipt = Objects.requireNonNull(receipt, "receipt");
        if (intent.status() != AuctionCreateEscrowIntent.Status.PREPARED) {
            return new AuctionCreateRecoveryDecision(Action.NONE,
                    Optional.empty());
        }
        return switch (custodyState) {
            case NONE, ABORTED -> new AuctionCreateRecoveryDecision(
                    Action.ABORT, Optional.empty());
            case PREPARED -> new AuctionCreateRecoveryDecision(
                    Action.RETRY_CUSTODY, Optional.empty());
            case QUARANTINED -> new AuctionCreateRecoveryDecision(
                    Action.MANUAL_REVIEW, Optional.empty());
            case COMMITTED -> {
                ItemInventoryMutationReceipt value = receipt.orElse(null);
                if (value == null || !value.equals(
                        intent.plannedCustody().receipt())) {
                    yield new AuctionCreateRecoveryDecision(
                            Action.MANUAL_REVIEW, Optional.empty());
                }
                yield new AuctionCreateRecoveryDecision(Action.COMMIT,
                        Optional.of(value));
            }
        };
    }

    public enum Action {
        NONE,
        RETRY_CUSTODY,
        COMMIT,
        ABORT,
        MANUAL_REVIEW
    }

    public enum CustodyState {
        NONE,
        PREPARED,
        COMMITTED,
        ABORTED,
        QUARANTINED
    }
}

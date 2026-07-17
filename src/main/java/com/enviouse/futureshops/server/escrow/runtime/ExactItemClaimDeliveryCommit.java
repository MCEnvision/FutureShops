package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationDirection;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationReceipt;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryJournalTransition;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryJournalTransitionType;

import java.util.Objects;
import java.util.UUID;

public record ExactItemClaimDeliveryCommit(
        ClaimDeliveryCommit delivery,
        ItemInventoryJournalTransition itemCommit,
        long remainingBefore,
        int retryIndex,
        String payloadFingerprint
) {
    public ExactItemClaimDeliveryCommit {
        Objects.requireNonNull(delivery, "delivery");
        Objects.requireNonNull(itemCommit, "itemCommit");
        payloadFingerprint = Objects.requireNonNull(
                payloadFingerprint, "payloadFingerprint");
        if (itemCommit.type()
                != ItemInventoryJournalTransitionType.COMMIT
                || remainingBefore <= 0L
                || delivery.units() > remainingBefore
                || retryIndex < 0
                || retryIndex
                > ExactItemClaimDeliveryPlanner.MAX_RETRY_INDEX
                || payloadFingerprint.length() != 64) {
            throw new IllegalArgumentException(
                    "Exact item claim delivery commit is invalid");
        }
        ItemInventoryMutationReceipt receipt = itemCommit.receipt()
                .orElseThrow();
        if (!receipt.token().playerId().equals(delivery.ownerId())
                || receipt.token().direction()
                != ItemInventoryMutationDirection.INSERT
                || !receipt.appliedAt().equals(delivery.deliveredAt())
                || !ExactItemClaimDeliveryPlanner.requestKey(
                delivery.claimId(), receipt.token().requestId())
                .equals(delivery.requestKey())) {
            throw new IllegalArgumentException(
                    "Exact item claim delivery proof conflicts");
        }
    }

    public UUID requestId() {
        return itemCommit.requestId();
    }
}

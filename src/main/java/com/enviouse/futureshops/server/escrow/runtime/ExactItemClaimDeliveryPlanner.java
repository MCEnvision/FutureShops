package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.claim.ClaimAttemptResult;
import com.enviouse.futureshops.server.escrow.claim.ClaimKind;
import com.enviouse.futureshops.server.escrow.claim.ClaimSavedData;
import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.item.ExactItemClaimPayload;
import com.enviouse.futureshops.server.escrow.item.ExactItemClaimPayloadCodec;
import com.enviouse.futureshops.server.escrow.item.ExactItemClaimResolutionStatus;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryBatchEntry;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryBatchPlanner;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationReceipt;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryJournalTransition;
import net.minecraft.world.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

final class ExactItemClaimDeliveryPlanner {
    static final int MAX_RETRY_INDEX = 255;

    private ExactItemClaimDeliveryPlanner() {
    }

    static Optional<ExactItemClaimDeliveryCommit> match(
            ItemInventoryMutationReceipt receipt,
            ClaimSavedData claims
    ) {
        Objects.requireNonNull(receipt, "receipt");
        Objects.requireNonNull(claims, "claims");
        for (EscrowClaim claim : claims.claimsForTransaction(
                receipt.token().transactionId())) {
            if (!claim.ownerId().equals(receipt.token().playerId())
                    || !supportedKind(claim.kind())) {
                continue;
            }
            String requestKey = requestKey(claim.claimId(),
                    receipt.token().requestId());
            Optional<ClaimAttemptResult> prior = claims.attempt(requestKey);
            long remainingBefore = prior.map(value -> Math.addExact(
                    value.deliveredUnits(), value.remainingUnits()))
                    .orElse(claim.remainingUnits());
            for (int retryIndex = 0;
                 retryIndex <= MAX_RETRY_INDEX; retryIndex++) {
                if (!requestId(claim, remainingBefore, retryIndex).equals(
                        receipt.token().requestId())) {
                    continue;
                }
                long units = deliveredUnits(receipt);
                ExactItemClaimDeliveryCommit commit =
                        new ExactItemClaimDeliveryCommit(
                                new ClaimDeliveryCommit(claim.ownerId(),
                                        claim.claimId(), requestKey, units,
                                        receipt.appliedAt()),
                                ItemInventoryJournalTransition.commit(
                                        receipt), remainingBefore,
                                retryIndex, payload(claim).fingerprint());
                validate(claim, commit);
                return Optional.of(commit);
            }
        }
        return Optional.empty();
    }

    static void validate(
            EscrowClaim claim,
            ExactItemClaimDeliveryCommit commit
    ) {
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(commit, "commit");
        ExactItemClaimPayload payload = payload(claim);
        ItemInventoryMutationReceipt receipt = commit.itemCommit()
                .receipt().orElseThrow();
        if (!claim.claimId().equals(commit.delivery().claimId())
                || !claim.ownerId().equals(commit.delivery().ownerId())
                || !claim.transactionId().equals(
                receipt.token().transactionId())
                || !claim.ownerId().equals(receipt.token().playerId())
                || claim.originalUnits() != payload.stackCount()
                || commit.remainingBefore() > claim.originalUnits()
                || !payload.fingerprint().equals(
                commit.payloadFingerprint())
                || !requestId(claim, commit.remainingBefore(),
                commit.retryIndex()).equals(commit.requestId())
                || !requestKey(claim.claimId(), commit.requestId()).equals(
                commit.delivery().requestKey())
                || deliveredUnits(receipt) != commit.delivery().units()) {
            throw new EscrowRuntimeException(
                    "Exact item claim delivery does not match its claim");
        }
        ItemStack portion = portion(payload,
                Math.toIntExact(commit.delivery().units()));
        ItemInventoryBatchEntry expected = ItemInventoryBatchEntry.insert(
                entryId(commit.requestId()), portion);
        if (!MessageDigest.isEqual(receipt.token().batchFingerprint(),
                ItemInventoryBatchPlanner.fingerprint(
                        java.util.List.of(expected)))) {
            throw new EscrowRuntimeException(
                    "Exact item claim inventory proof conflicts");
        }
        if (claim.status() != ClaimStatus.PENDING
                && claim.status() != ClaimStatus.PARTIALLY_DELIVERED
                && claim.status() != ClaimStatus.COMPLETED
                || claim.status() != ClaimStatus.COMPLETED
                && claim.remainingUnits() > commit.remainingBefore()) {
            throw new EscrowRuntimeException(
                    "Exact item claim state conflicts with delivery");
        }
    }

    static ExactItemClaimPayload payload(EscrowClaim claim) {
        Objects.requireNonNull(claim, "claim");
        if (!supportedKind(claim.kind())) {
            throw new IllegalArgumentException(
                    "Claim is not an exact item claim");
        }
        ExactItemClaimPayload payload = ExactItemClaimPayloadCodec.decode(
                claim.payload());
        if (!payload.sourceTransactionId().equals(claim.transactionId())
                || payload.stackCount() != claim.originalUnits()) {
            throw new IllegalArgumentException(
                    "Exact item claim payload conflicts with claim");
        }
        return payload;
    }

    static ItemStack portion(ExactItemClaimPayload payload, int units) {
        Objects.requireNonNull(payload, "payload");
        if (units <= 0 || units > payload.stackCount()) {
            throw new IllegalArgumentException(
                    "Exact item claim portion is invalid");
        }
        var resolution = payload.resolve();
        if (resolution.status()
                != ExactItemClaimResolutionStatus.RESOLVED) {
            throw new IllegalArgumentException(
                    "Exact item claim payload cannot be resolved");
        }
        ItemStack stack = resolution.resolvedStack().orElseThrow();
        stack.setCount(units);
        return stack;
    }

    static UUID requestId(
            EscrowClaim claim,
            long remainingBefore,
            int retryIndex
    ) {
        ExactItemClaimPayload payload = payload(claim);
        if (remainingBefore <= 0L
                || remainingBefore > claim.originalUnits()
                || retryIndex < 0 || retryIndex > MAX_RETRY_INDEX) {
            throw new IllegalArgumentException(
                    "Exact item claim request state is invalid");
        }
        String identity = "futureshops exact item claim delivery\u0000"
                + claim.claimId() + "\u0000" + claim.ownerId() + "\u0000"
                + claim.transactionId() + "\u0000" + remainingBefore
                + "\u0000" + retryIndex + "\u0000"
                + payload.fingerprint();
        return UUID.nameUUIDFromBytes(identity.getBytes(
                StandardCharsets.UTF_8));
    }

    static UUID entryId(UUID requestId) {
        return UUID.nameUUIDFromBytes(
                ("futureshops exact item claim entry\u0000"
                        + Objects.requireNonNull(requestId, "requestId"))
                        .getBytes(StandardCharsets.UTF_8));
    }

    static String requestKey(UUID claimId, UUID requestId) {
        return "exact.item.claim.delivery."
                + Objects.requireNonNull(claimId, "claimId") + "."
                + Objects.requireNonNull(requestId, "requestId");
    }

    static long deliveredUnits(ItemInventoryMutationReceipt receipt) {
        long units = 0L;
        for (var portion : Objects.requireNonNull(
                receipt, "receipt").actualPortions()) {
            units = Math.addExact(units, portion.count());
        }
        if (units <= 0L) {
            throw new IllegalArgumentException(
                    "Exact item claim receipt has no units");
        }
        return units;
    }

    static boolean supportedKind(ClaimKind kind) {
        return kind == ClaimKind.ITEM || kind == ClaimKind.BARTER_ITEM
                || kind == ClaimKind.REFUND;
    }
}

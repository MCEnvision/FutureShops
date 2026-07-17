package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.claim.ClaimKind;
import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.item.ExactItemClaimPayload;
import com.enviouse.futureshops.server.escrow.item.ExactItemClaimPayloadCodec;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryAllocation;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryBatchPlanner;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationDirection;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationReceipt;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationToken;
import com.enviouse.futureshops.server.escrow.model.EscrowOperation;
import com.enviouse.futureshops.server.escrow.model.EscrowState;
import com.enviouse.futureshops.server.escrow.stock.StockKey;
import com.enviouse.futureshops.server.escrow.stock.StockMutationType;
import com.enviouse.futureshops.server.escrow.stock.StockReservationDirection;
import com.enviouse.futureshops.server.escrow.stock.StockReservationId;
import com.enviouse.futureshops.server.escrow.stock.StockReservationRequest;
import com.enviouse.futureshops.server.escrow.stock.StockReservationResolution;

import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class ServerShopBarterConservationValidator {
    private ServerShopBarterConservationValidator() {
    }

    public static void validate(ServerShopBarterCommit commit) {
        Objects.requireNonNull(commit, "commit");
        validate(new ServerShopBarterCommit.CommitView(
                commit.requestId(), commit.playerId(), commit.shopId(),
                commit.recipeId(), commit.multiplier(),
                commit.quoteRevision(), commit.recipeRevision(),
                commit.quoteCreatedAt(), commit.ingredients(),
                commit.outputs(), commit.ingredientCustodyReceipt(),
                commit.completedTransaction(), commit.stockReservation(),
                commit.stockCommit(), commit.outputClaims()));
    }

    static void validate(ServerShopBarterCommit.CommitView commit) {
        Objects.requireNonNull(commit, "commit");
        requireCustody(commit);
        ServerShopBarterCommit.CanonicalComponents expected =
                ServerShopBarterCommit.canonical(commit.canonicalInput());
        if (!expected.transaction().equals(
                commit.completedTransaction())
                || !expected.reserve().equals(
                commit.stockReservation())
                || !expected.commit().equals(commit.stockCommit())
                || !expected.claims().equals(commit.outputClaims())) {
            throw new IllegalArgumentException(
                    "Server shop barter commit evidence conflicts");
        }
        requireTransaction(commit);
        requireStock(commit);
        requireClaims(commit);
    }

    private static void requireCustody(
            ServerShopBarterCommit.CommitView commit
    ) {
        ItemInventoryMutationReceipt receipt =
                commit.ingredientCustodyReceipt();
        ItemInventoryMutationToken token = receipt.token();
        byte[] expectedBatch = ItemInventoryBatchPlanner.fingerprint(
                ServerShopBarterCommit.custodyEntries(
                        commit.requestId(), commit.multiplier(),
                        commit.ingredients()));
        if (!token.playerId().equals(commit.playerId())
                || !token.transactionId().equals(commit.requestId())
                || !token.requestId().equals(
                ServerShopBarterCommit.ingredientCustodyRequestId(
                        commit.requestId()))
                || token.direction()
                != ItemInventoryMutationDirection.EXTRACT
                || !MessageDigest.isEqual(
                token.batchFingerprint(), expectedBatch)) {
            throw new IllegalArgumentException(
                    "Server shop barter custody identity is invalid");
        }
        Map<UUID, ServerShopBarterCommit.Ingredient> ingredients =
                new HashMap<>();
        Map<UUID, Integer> totals = new HashMap<>();
        for (ServerShopBarterCommit.Ingredient ingredient
                : commit.ingredients()) {
            UUID entryId = ServerShopBarterCommit.ingredientEntryId(
                    commit.requestId(), ingredient);
            ingredients.put(entryId, ingredient);
            totals.put(entryId, 0);
        }
        for (ItemInventoryAllocation allocation
                : receipt.actualPortions()) {
            ServerShopBarterCommit.Ingredient ingredient =
                    ingredients.get(allocation.entryId());
            if (ingredient == null
                    || !ServerShopBarterCommit.portionMatches(
                    ingredient, allocation)) {
                throw new IllegalArgumentException(
                        "Server shop barter custody does not match");
            }
            totals.compute(allocation.entryId(), (key, value) ->
                    Math.addExact(Objects.requireNonNull(value),
                            allocation.count()));
        }
        for (Map.Entry<UUID, ServerShopBarterCommit.Ingredient> entry
                : ingredients.entrySet()) {
            int expected = entry.getValue().totalQuantity(
                    commit.multiplier());
            if (totals.get(entry.getKey()) != expected) {
                throw new IllegalArgumentException(
                        "Server shop barter custody quantity is invalid");
            }
        }
    }

    private static void requireTransaction(
            ServerShopBarterCommit.CommitView commit
    ) {
        if (!commit.completedTransaction().transactionId().value().equals(
                commit.requestId())
                || commit.completedTransaction()
                .parentTransactionId().isPresent()
                || commit.completedTransaction().operation()
                != EscrowOperation.SERVER_SHOP_BARTER
                || commit.completedTransaction().state()
                != EscrowState.COMPLETED
                || commit.completedTransaction().configRevision()
                != ServerShopBarterCommit.configurationRevision(
                commit.quoteRevision(), commit.recipeRevision())
                || commit.completedTransaction().shopReference().isEmpty()
                || !commit.completedTransaction().shopReference()
                .orElseThrow().shopId().equals(commit.shopId())) {
            throw new IllegalArgumentException(
                    "Server shop barter transaction is invalid");
        }
    }

    private static void requireStock(
            ServerShopBarterCommit.CommitView commit
    ) {
        if (!commit.stockReservation().requestId().equals(
                ServerShopBarterCommit.stockReserveRequestId(
                        commit.requestId()))
                || !commit.stockCommit().requestId().equals(
                ServerShopBarterCommit.stockCommitRequestId(
                        commit.requestId()))
                || !commit.stockReservation().transactionId().equals(
                commit.requestId())
                || !commit.stockCommit().transactionId().equals(
                commit.requestId())
                || commit.stockCommit().operation()
                != StockMutationType.COMMIT_BATCH
                || !commit.stockReservation().appliedAt().equals(
                commit.quoteCreatedAt())
                || !commit.stockCommit().appliedAt().equals(
                commit.ingredientCustodyReceipt().appliedAt())
                || commit.stockReservation().reservations().size()
                != commit.outputs().size()
                || commit.stockCommit().reservations().size()
                != commit.outputs().size()) {
            throw new IllegalArgumentException(
                    "Server shop barter stock batch is invalid");
        }
        Map<StockKey, StockReservationRequest> reserved = new HashMap<>();
        for (StockReservationRequest request
                : commit.stockReservation().reservations()) {
            if (reserved.put(request.stockKey(), request) != null) {
                throw new IllegalArgumentException(
                        "Server shop barter stock key is duplicated");
            }
        }
        Map<StockReservationId, StockReservationResolution> resolved =
                new HashMap<>();
        for (StockReservationResolution resolution
                : commit.stockCommit().reservations()) {
            if (resolved.put(resolution.reservationId(), resolution)
                    != null) {
                throw new IllegalArgumentException(
                        "Server shop barter stock reservation is duplicated");
            }
        }
        for (ServerShopBarterCommit.OutputLine output
                : commit.outputs()) {
            StockKey key = new StockKey(commit.shopId(),
                    output.listingId());
            StockReservationRequest request = reserved.get(key);
            StockReservationId reservationId =
                    StockReservationId.forTransaction(commit.requestId(),
                            key, StockReservationDirection.OUTBOUND);
            StockReservationResolution resolution =
                    resolved.get(reservationId);
            if (request == null
                    || request.direction()
                    != StockReservationDirection.OUTBOUND
                    || request.quantity()
                    != output.totalQuantity(commit.multiplier())
                    || request.expectedListingRevision()
                    != output.expectedStockRevision()
                    || resolution == null
                    || resolution.expectedReservationRevision() != 0L) {
                throw new IllegalArgumentException(
                        "Server shop barter stock line is invalid");
            }
        }
    }

    private static void requireClaims(
            ServerShopBarterCommit.CommitView commit
    ) {
        int expectedCount = 0;
        Map<UUID, EscrowClaim> claims = new HashMap<>();
        for (EscrowClaim claim : commit.outputClaims()) {
            if (claims.put(claim.claimId(), claim) != null) {
                throw new IllegalArgumentException(
                        "Server shop barter claim is duplicated");
            }
        }
        Set<String> sources = new HashSet<>();
        for (ServerShopBarterCommit.OutputLine output
                : commit.outputs()) {
            int outputTotal = 0;
            for (ExactItemClaimPayload payload : output.portions()) {
                expectedCount = Math.addExact(expectedCount, 1);
                outputTotal = Math.addExact(outputTotal,
                        payload.stackCount());
                EscrowClaim claim = claims.get(payload.lotId());
                String sourceKey = ServerShopBarterCommit.claimSourceKey(
                        payload);
                if (!sources.add(sourceKey)
                        || claim == null
                        || !claim.transactionId().equals(
                        commit.requestId())
                        || !claim.ownerId().equals(commit.playerId())
                        || !claim.sourceKey().equals(sourceKey)
                        || claim.kind() != ClaimKind.ITEM
                        || claim.status() != ClaimStatus.PENDING
                        || claim.originalUnits() != payload.stackCount()
                        || claim.remainingUnits() != payload.stackCount()
                        || !MessageDigest.isEqual(claim.payload(),
                        ExactItemClaimPayloadCodec.encode(payload))
                        || !claim.label().equals(
                        ServerShopBarterCommit.CLAIM_LABEL)
                        || !claim.createdAt().equals(
                        commit.ingredientCustodyReceipt().appliedAt())
                        || !claim.updatedAt().equals(claim.createdAt())) {
                    throw new IllegalArgumentException(
                            "Server shop barter claim is invalid");
                }
            }
            if (outputTotal != output.totalQuantity(
                    commit.multiplier())) {
                throw new IllegalArgumentException(
                        "Server shop barter output is not conserved");
            }
        }
        if (claims.size() != expectedCount) {
            throw new IllegalArgumentException(
                    "Server shop barter claim count is invalid");
        }
    }
}

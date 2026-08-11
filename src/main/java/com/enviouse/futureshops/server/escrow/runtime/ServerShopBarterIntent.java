package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.item.ExactItemClaimPayload;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationReceipt;
import com.enviouse.futureshops.server.escrow.model.DimensionAwareShopReference;
import com.enviouse.futureshops.server.escrow.stock.StockMutationCommand;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ServerShopBarterIntent(
        UUID requestId,
        UUID playerId,
        String shopId,
        String recipeId,
        int multiplier,
        long quoteRevision,
        long recipeRevision,
        Instant quoteCreatedAt,
        List<ServerShopBarterCommit.Ingredient> ingredients,
        List<ServerShopBarterCommit.OutputLine> outputs,
        DimensionAwareShopReference shopReference,
        Status status,
        long revision
) {
    public ServerShopBarterIntent {
        requestId = ServerShopBarterCommit.requireUuid(
                requestId, "requestId");
        playerId = ServerShopBarterCommit.requireUuid(
                playerId, "playerId");
        shopId = ServerShopBarterCommit.requireIdentifier(
                shopId, "shopId");
        recipeId = ServerShopBarterCommit.requireIdentifier(
                recipeId, "recipeId");
        multiplier = ServerShopBarterCommit.requireMultiplier(multiplier);
        ServerShopBarterCommit.requireRevision(
                quoteRevision, "quote revision");
        ServerShopBarterCommit.requireRevision(
                recipeRevision, "recipe revision");
        quoteCreatedAt = Objects.requireNonNull(
                quoteCreatedAt, "quoteCreatedAt");
        ingredients = ServerShopBarterCommit.copyIngredients(ingredients);
        outputs = ServerShopBarterCommit.copyOutputs(outputs);
        shopReference = Objects.requireNonNull(
                shopReference, "shopReference");
        status = Objects.requireNonNull(status, "status");
        if (!shopReference.shopId().equals(shopId)
                || revision < 0L || revision > 1L
                || status == Status.PREPARED && revision != 0L
                || status != Status.PREPARED && revision != 1L) {
            throw new IllegalArgumentException(
                    "Server shop barter intent state is invalid");
        }
        for (ServerShopBarterCommit.Ingredient ingredient : ingredients) {
            ingredient.totalQuantity(multiplier);
        }
        for (ServerShopBarterCommit.OutputLine output : outputs) {
            int total = output.totalQuantity(multiplier);
            int delivered = 0;
            for (ExactItemClaimPayload payload : output.portions()) {
                if (!payload.sourceTransactionId().equals(requestId)
                        || !payload.sourceKey().equals(
                        ServerShopBarterCommit.outputSourceKey(requestId,
                                output.outputIndex()))) {
                    throw new IllegalArgumentException(
                            "Server shop barter intent output identity conflicts");
                }
                delivered = Math.addExact(delivered,
                        payload.stackCount());
            }
            if (delivered != total) {
                throw new IllegalArgumentException(
                        "Server shop barter intent output quantity conflicts");
            }
        }
        ServerShopBarterCommit.custodyEntries(requestId, multiplier,
                ingredients);
        ServerShopBarterCommit.stockReservation(requestId, shopId,
                multiplier, outputs, quoteCreatedAt);
    }

    public static ServerShopBarterIntent prepared(
            ServerShopBarterService.PreparedRequest request
    ) {
        Objects.requireNonNull(request, "request");
        ServerShopBarterService.Identity identity = request.identity();
        return new ServerShopBarterIntent(identity.requestId(),
                identity.playerId(), identity.shopId(),
                identity.recipeId(), identity.multiplier(),
                request.quoteRevision(), request.recipeRevision(),
                request.quoteCreatedAt(), request.ingredients(),
                request.outputs(), request.shopReference(),
                Status.PREPARED, 0L);
    }

    public String wireFingerprint() {
        return ServerShopBarterCommit.wireFingerprint(requestId,
                playerId, shopId, recipeId, multiplier);
    }

    public String intentFingerprint() {
        ServerShopBarterIntent prepared = status == Status.PREPARED
                ? this : copy(Status.PREPARED, 0L);
        return ServerShopBarterCommit.sha256(
                ServerShopBarterIntentCodec.encode(prepared));
    }

    public StockMutationCommand.ReserveBatch stockReservation() {
        return ServerShopBarterCommit.stockReservation(requestId, shopId,
                multiplier, outputs, quoteCreatedAt);
    }

    public StockMutationCommand.ResolveBatch stockRelease() {
        return ServerShopBarterCommit.stockRelease(requestId, shopId,
                outputs, quoteCreatedAt);
    }

    public ServerShopBarterCommit commit(
            ItemInventoryMutationReceipt receipt
    ) {
        if (status != Status.PREPARED && status != Status.COMMITTED) {
            throw new IllegalStateException(
                    "Server shop barter intent is terminal");
        }
        return ServerShopBarterCommit.create(requestId, playerId, shopId,
                recipeId, multiplier, quoteRevision, recipeRevision,
                quoteCreatedAt, ingredients, outputs,
                Objects.requireNonNull(receipt, "receipt"),
                shopReference);
    }

    public ServerShopBarterIntent complete() {
        return transition(Status.COMMITTED);
    }

    public ServerShopBarterIntent abort(Status terminalStatus) {
        if (terminalStatus == Status.PREPARED
                || terminalStatus == Status.COMMITTED) {
            throw new IllegalArgumentException(
                    "Server shop barter abort status is invalid");
        }
        return transition(terminalStatus);
    }

    private ServerShopBarterIntent transition(Status terminalStatus) {
        Objects.requireNonNull(terminalStatus, "terminalStatus");
        if (status == terminalStatus) {
            return this;
        }
        if (status != Status.PREPARED) {
            throw new IllegalStateException(
                    "Server shop barter intent is terminal");
        }
        return copy(terminalStatus, 1L);
    }

    private ServerShopBarterIntent copy(
            Status newStatus,
            long newRevision
    ) {
        return new ServerShopBarterIntent(requestId, playerId, shopId,
                recipeId, multiplier, quoteRevision, recipeRevision,
                quoteCreatedAt, ingredients, outputs, shopReference,
                newStatus, newRevision);
    }

    public enum Status {
        PREPARED,
        COMMITTED,
        ABORTED_MISSING_INGREDIENTS,
        ABORTED_UNSUPPORTED_ITEM,
        ABORTED_CUSTODY
    }
}

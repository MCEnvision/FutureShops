package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.claim.ClaimKind;
import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationReceipt;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryExecutionResult;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryExecutionStatus;
import com.enviouse.futureshops.server.escrow.model.DimensionAwareShopReference;
import com.enviouse.futureshops.server.escrow.model.EscrowOperation;
import com.enviouse.futureshops.server.escrow.model.EscrowState;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;
import com.enviouse.futureshops.server.escrow.stock.StockKey;
import com.enviouse.futureshops.server.escrow.stock.StockMutationCommand;
import com.enviouse.futureshops.server.escrow.stock.StockReservation;
import com.enviouse.futureshops.server.escrow.stock.StockReservationDirection;
import com.enviouse.futureshops.server.escrow.stock.StockReservationId;
import com.enviouse.futureshops.server.escrow.stock.StockReservationState;
import net.minecraft.server.level.ServerPlayer;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class ServerShopBarterService {
    private ServerShopBarterService() {
    }

    public static Result barter(
            ServerPlayer player,
            PreparedRequest request
    ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(request, "request");
        if (!player.getUUID().equals(request.identity().playerId())) {
            return Result.failure(Status.REQUEST_CONFLICT,
                    request.identity().requestId());
        }
        EscrowRuntimeService runtime = EscrowRuntimeManager.getOrNull();
        if (runtime == null) {
            return Result.failure(Status.ESCROW_UNAVAILABLE,
                    request.identity().requestId());
        }
        try {
            return execute(request, new LiveBackend(runtime),
                    runtime.serverShopBarterCustody(player));
        } catch (RuntimeException exception) {
            return Result.failure(Status.ESCROW_UNAVAILABLE,
                    request.identity().requestId());
        }
    }

    public static Optional<Result> resolveReplay(
            ServerPlayer player,
            Identity identity
    ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(identity, "identity");
        if (!player.getUUID().equals(identity.playerId())) {
            return Optional.of(Result.failure(Status.REQUEST_CONFLICT,
                    identity.requestId()));
        }
        EscrowRuntimeService runtime = EscrowRuntimeManager.getOrNull();
        if (runtime == null) {
            return Optional.empty();
        }
        LiveBackend backend = new LiveBackend(runtime);
        try {
            Optional<ServerShopBarterIntent> stored = backend.intent(
                    identity.requestId());
            if (stored.isEmpty()) {
                ServerShopBarterItemCustody custody =
                        runtime.serverShopBarterCustody(player);
                if (backend.transaction(identity.requestId()).isPresent()
                        || !backend.claimsForTransaction(
                        identity.requestId()).isEmpty()
                        || !backend.stockReservations(
                        identity.requestId()).isEmpty()
                        || custody.inspect(ServerShopBarterCommit
                        .ingredientCustodyRequestId(identity.requestId()))
                        .state()
                        != ServerShopBarterItemCustody.State.NONE) {
                    return Optional.of(Result.failure(
                            Status.RECOVERY_REQUIRED,
                            identity.requestId()));
                }
                return Optional.empty();
            }
            ServerShopBarterIntent intent = stored.orElseThrow();
            if (!identity.wireFingerprint().equals(
                    intent.wireFingerprint())) {
                return Optional.of(Result.failure(
                        Status.REQUEST_CONFLICT,
                        identity.requestId()));
            }
            return resolveReplay(PreparedRequest.from(intent), backend,
                    runtime.serverShopBarterCustody(player));
        } catch (RuntimeException exception) {
            return Optional.of(Result.failure(Status.ESCROW_UNAVAILABLE,
                    identity.requestId()));
        }
    }

    public static Result execute(
            PreparedRequest request,
            Backend backend,
            ServerShopBarterItemCustody custody
    ) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(backend, "backend");
        Objects.requireNonNull(custody, "custody");
        try {
            Optional<Result> replay = resolveReplay(
                    request, backend, custody);
            if (replay.isPresent()) {
                return replay.orElseThrow();
            }
            ServerShopBarterIntent expected =
                    ServerShopBarterIntent.prepared(request);
            Optional<ServerShopBarterIntent> existing = backend.intent(
                    request.identity().requestId());
            if (existing.isPresent()) {
                ServerShopBarterIntent stored = existing.orElseThrow();
                if (!sameIntent(expected, stored)) {
                    return Result.failure(Status.REQUEST_CONFLICT,
                            request.identity().requestId());
                }
                if (stored.status()
                        != ServerShopBarterIntent.Status.PREPARED) {
                    return Result.failure(Status.RECOVERY_REQUIRED,
                            request.identity().requestId());
                }
                return resume(stored, backend, custody);
            }
            if (!backend.ready()) {
                return Result.failure(Status.ESCROW_UNAVAILABLE,
                        request.identity().requestId());
            }
            PrepareDisposition prepared = Objects.requireNonNull(
                    backend.prepare(expected,
                            expected.stockReservation()),
                    "prepareDisposition");
            Result rejected = prepareFailure(prepared,
                    request.identity().requestId());
            if (rejected != null) {
                return rejected;
            }
            Optional<Result> afterPrepare = resolveReplay(
                    request, backend, custody);
            if (afterPrepare.isPresent()) {
                return afterPrepare.orElseThrow();
            }
            ServerShopBarterIntent stored = backend.intent(
                    request.identity().requestId()).orElseThrow(() ->
                    new IllegalStateException(
                            "Server shop barter prepared intent is missing"));
            if (!sameIntent(expected, stored)
                    || stored.status()
                    != ServerShopBarterIntent.Status.PREPARED) {
                return Result.failure(Status.RECOVERY_REQUIRED,
                        request.identity().requestId());
            }
            return resume(stored, backend, custody);
        } catch (RuntimeException exception) {
            try {
                return resolveReplay(request, backend, custody)
                        .orElseGet(() -> Result.failure(
                                Status.RECOVERY_REQUIRED,
                                request.identity().requestId()));
            } catch (RuntimeException replayFailure) {
                return Result.failure(Status.RECOVERY_REQUIRED,
                        request.identity().requestId());
            }
        }
    }

    public static Optional<Result> resolveReplay(
            PreparedRequest request,
            Backend backend,
            ServerShopBarterItemCustody custody
    ) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(backend, "backend");
        Objects.requireNonNull(custody, "custody");
        UUID requestId = request.identity().requestId();
        ServerShopBarterIntent expected =
                ServerShopBarterIntent.prepared(request);
        Optional<ServerShopBarterIntent> intent = backend.intent(
                requestId);
        Optional<EscrowTransaction> transaction = backend.transaction(
                requestId);
        List<EscrowClaim> claims = List.copyOf(
                backend.claimsForTransaction(requestId));
        List<StockReservation> reservations = List.copyOf(
                backend.stockReservations(requestId));
        ServerShopBarterItemCustody.Inspection itemEvidence =
                custody.inspect(
                        ServerShopBarterCommit.ingredientCustodyRequestId(
                                requestId));
        if (intent.isEmpty()) {
            if (transaction.isPresent() || !claims.isEmpty()
                    || !reservations.isEmpty()
                    || itemEvidence.state()
                    != ServerShopBarterItemCustody.State.NONE) {
                return Optional.of(Result.failure(
                        Status.RECOVERY_REQUIRED, requestId));
            }
            return Optional.empty();
        }
        ServerShopBarterIntent stored = intent.orElseThrow();
        if (!sameIntent(expected, stored)) {
            return Optional.of(Result.failure(
                    Status.REQUEST_CONFLICT, requestId));
        }
        if (stored.status()
                == ServerShopBarterIntent.Status.PREPARED) {
            if (transaction.isPresent() || !claims.isEmpty()
                    || !reservationsMatch(stored, reservations,
                    StockReservationState.HELD,
                    stored.quoteCreatedAt())
                    || itemEvidence.state()
                    == ServerShopBarterItemCustody.State.QUARANTINED) {
                return Optional.of(Result.failure(
                        Status.RECOVERY_REQUIRED, requestId));
            }
            if (itemEvidence.receipt().isPresent()
                    && !custodyReceiptMatches(stored,
                    itemEvidence.receipt().orElseThrow())) {
                return Optional.of(Result.failure(
                        Status.RECOVERY_REQUIRED, requestId));
            }
            return Optional.empty();
        }
        if (stored.status()
                == ServerShopBarterIntent.Status.COMMITTED) {
            return Optional.of(resolveCompleted(stored, transaction,
                    claims, reservations, itemEvidence));
        }
        return Optional.of(resolveAborted(stored, transaction, claims,
                reservations, itemEvidence));
    }

    private static Result resume(
            ServerShopBarterIntent intent,
            Backend backend,
            ServerShopBarterItemCustody custody
    ) {
        UUID requestId = intent.requestId();
        if (intent.status() != ServerShopBarterIntent.Status.PREPARED
                || backend.transaction(requestId).isPresent()
                || !backend.claimsForTransaction(requestId).isEmpty()
                || !reservationsMatch(intent,
                backend.stockReservations(requestId),
                StockReservationState.HELD, intent.quoteCreatedAt())) {
            return Result.failure(Status.RECOVERY_REQUIRED, requestId);
        }
        ServerShopBarterItemCustody.Inspection inspection =
                custody.inspect(
                        ServerShopBarterCommit.ingredientCustodyRequestId(
                                requestId));
        if (inspection.state()
                == ServerShopBarterItemCustody.State.QUARANTINED) {
            return Result.failure(Status.RECOVERY_REQUIRED, requestId);
        }
        if (inspection.state()
                == ServerShopBarterItemCustody.State.ABORTED) {
            return abort(intent,
                    ServerShopBarterIntent.Status.ABORTED_CUSTODY,
                    Status.ITEM_CUSTODY_ABORTED, backend, custody);
        }
        ItemInventoryMutationReceipt receipt;
        boolean custodyReplayed;
        if (inspection.state()
                == ServerShopBarterItemCustody.State.COMMITTED) {
            receipt = inspection.receipt().orElseThrow();
            custodyReplayed = true;
        } else {
            ItemInventoryExecutionResult itemResult = custody.extract(
                    requestId,
                    ServerShopBarterCommit.ingredientCustodyRequestId(
                            requestId),
                    ServerShopBarterCommit.custodyEntries(requestId,
                            intent.multiplier(), intent.ingredients()));
            if (itemResult.status()
                    == ItemInventoryExecutionStatus.INSUFFICIENT_ITEMS) {
                return abort(intent,
                        ServerShopBarterIntent.Status
                                .ABORTED_MISSING_INGREDIENTS,
                        Status.MISSING_INGREDIENTS, backend, custody);
            }
            if (itemResult.status()
                    == ItemInventoryExecutionStatus.UNSUPPORTED_STACK) {
                return abort(intent,
                        ServerShopBarterIntent.Status
                                .ABORTED_UNSUPPORTED_ITEM,
                        Status.UNSUPPORTED_ITEM, backend, custody);
            }
            if (itemResult.status()
                    == ItemInventoryExecutionStatus.ABORTED) {
                return abort(intent,
                        ServerShopBarterIntent.Status.ABORTED_CUSTODY,
                        Status.ITEM_CUSTODY_ABORTED, backend, custody);
            }
            if (itemResult.status()
                    != ItemInventoryExecutionStatus.APPLIED
                    && itemResult.status()
                    != ItemInventoryExecutionStatus.REPLAYED) {
                return Result.failure(Status.RECOVERY_REQUIRED,
                        requestId);
            }
            receipt = itemResult.receipt().orElseThrow(() ->
                    new IllegalStateException(
                            "Server shop barter custody receipt is missing"));
            custodyReplayed = itemResult.replayed();
        }
        ServerShopBarterCommit commit = intent.commit(receipt);
        CommitDisposition disposition = Objects.requireNonNull(
                backend.commit(intent.complete(), commit),
                "commitDisposition");
        if (disposition != CommitDisposition.APPLIED
                && disposition != CommitDisposition.REPLAYED) {
            return Result.failure(Status.RECOVERY_REQUIRED, requestId);
        }
        PreparedRequest request = PreparedRequest.from(intent);
        boolean replayed = custodyReplayed
                || disposition == CommitDisposition.REPLAYED;
        return resolveReplay(request, backend, custody)
                .filter(Result::success)
                .map(result -> result.withReplay(replayed))
                .orElseGet(() -> Result.failure(
                        Status.RECOVERY_REQUIRED, requestId));
    }

    private static Result abort(
            ServerShopBarterIntent intent,
            ServerShopBarterIntent.Status intentStatus,
            Status resultStatus,
            Backend backend,
            ServerShopBarterItemCustody custody
    ) {
        TransitionDisposition disposition = Objects.requireNonNull(
                backend.abort(intent.abort(intentStatus),
                        intent.stockRelease()),
                "abortDisposition");
        if (disposition != TransitionDisposition.APPLIED
                && disposition != TransitionDisposition.REPLAYED) {
            return Result.failure(Status.RECOVERY_REQUIRED,
                    intent.requestId());
        }
        return resolveReplay(PreparedRequest.from(intent), backend,
                custody).filter(value -> value.status() == resultStatus)
                .orElseGet(() -> Result.failure(
                        Status.RECOVERY_REQUIRED, intent.requestId()));
    }

    private static Result resolveCompleted(
            ServerShopBarterIntent intent,
            Optional<EscrowTransaction> transaction,
            List<EscrowClaim> claims,
            List<StockReservation> reservations,
            ServerShopBarterItemCustody.Inspection itemEvidence
    ) {
        if (transaction.isEmpty()
                || itemEvidence.state()
                != ServerShopBarterItemCustody.State.COMMITTED
                || itemEvidence.receipt().isEmpty()) {
            return Result.failure(Status.RECOVERY_REQUIRED,
                    intent.requestId());
        }
        try {
            ItemInventoryMutationReceipt receipt =
                    itemEvidence.receipt().orElseThrow();
            ServerShopBarterCommit expected = intent.commit(receipt);
            EscrowTransaction stored = transaction.orElseThrow();
            if (!stored.transactionId().value().equals(intent.requestId())
                    || stored.operation()
                    != EscrowOperation.SERVER_SHOP_BARTER
                    || stored.state() != EscrowState.COMPLETED
                    || !expected.completedTransaction().equals(stored)
                    || !reservationsMatch(intent, reservations,
                    StockReservationState.COMMITTED,
                    receipt.appliedAt())
                    || !claimsMatch(expected.outputClaims(), claims)) {
                return Result.failure(Status.RECOVERY_REQUIRED,
                        intent.requestId());
            }
            return Result.success(expected,
                    orderClaims(expected.outputClaims(), claims), true);
        } catch (RuntimeException exception) {
            return Result.failure(Status.RECOVERY_REQUIRED,
                    intent.requestId());
        }
    }

    private static Result resolveAborted(
            ServerShopBarterIntent intent,
            Optional<EscrowTransaction> transaction,
            List<EscrowClaim> claims,
            List<StockReservation> reservations,
            ServerShopBarterItemCustody.Inspection itemEvidence
    ) {
        if (transaction.isPresent() || !claims.isEmpty()
                || !reservationsMatch(intent, reservations,
                StockReservationState.RELEASED,
                intent.quoteCreatedAt())
                || itemEvidence.state()
                == ServerShopBarterItemCustody.State.PREPARED
                || itemEvidence.state()
                == ServerShopBarterItemCustody.State.COMMITTED
                || itemEvidence.state()
                == ServerShopBarterItemCustody.State.QUARANTINED
                || itemEvidence.receipt().isPresent()
                && !custodyReceiptMatches(intent,
                itemEvidence.receipt().orElseThrow())) {
            return Result.failure(Status.RECOVERY_REQUIRED,
                    intent.requestId());
        }
        Status status = switch (intent.status()) {
            case ABORTED_MISSING_INGREDIENTS ->
                    Status.MISSING_INGREDIENTS;
            case ABORTED_UNSUPPORTED_ITEM -> Status.UNSUPPORTED_ITEM;
            case ABORTED_CUSTODY -> Status.ITEM_CUSTODY_ABORTED;
            default -> Status.RECOVERY_REQUIRED;
        };
        if (intent.status()
                == ServerShopBarterIntent.Status.ABORTED_CUSTODY
                && itemEvidence.state()
                != ServerShopBarterItemCustody.State.ABORTED) {
            return Result.failure(Status.RECOVERY_REQUIRED,
                    intent.requestId());
        }
        return Result.failure(status, intent.requestId());
    }

    private static boolean custodyReceiptMatches(
            ServerShopBarterIntent intent,
            ItemInventoryMutationReceipt receipt
    ) {
        try {
            return preparedVersion(intent).commit(receipt)
                    .ingredientCustodyReceipt().equals(receipt);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static ServerShopBarterIntent preparedVersion(
            ServerShopBarterIntent intent
    ) {
        return new ServerShopBarterIntent(intent.requestId(),
                intent.playerId(), intent.shopId(), intent.recipeId(),
                intent.multiplier(), intent.quoteRevision(),
                intent.recipeRevision(), intent.quoteCreatedAt(),
                intent.ingredients(), intent.outputs(),
                intent.shopReference(),
                ServerShopBarterIntent.Status.PREPARED, 0L);
    }

    private static boolean sameIntent(
            ServerShopBarterIntent expected,
            ServerShopBarterIntent actual
    ) {
        return expected.requestId().equals(actual.requestId())
                && expected.wireFingerprint().equals(
                actual.wireFingerprint())
                && expected.intentFingerprint().equals(
                actual.intentFingerprint());
    }

    private static boolean reservationsMatch(
            ServerShopBarterIntent intent,
            List<StockReservation> values,
            StockReservationState expectedState,
            Instant expectedUpdatedAt
    ) {
        List<StockReservation> reservations = List.copyOf(values);
        if (reservations.size() != intent.outputs().size()) {
            return false;
        }
        Map<StockKey, StockReservation> byKey = new HashMap<>();
        for (StockReservation reservation : reservations) {
            if (byKey.put(reservation.stockKey(), reservation) != null) {
                return false;
            }
        }
        long expectedRevision = expectedState
                == StockReservationState.HELD ? 0L : 1L;
        for (ServerShopBarterCommit.OutputLine output
                : intent.outputs()) {
            StockKey key = new StockKey(intent.shopId(),
                    output.listingId());
            StockReservation reservation = byKey.get(key);
            if (reservation == null
                    || !reservation.reservationId().equals(
                    StockReservationId.forTransaction(intent.requestId(),
                            key, StockReservationDirection.OUTBOUND))
                    || !reservation.transactionId().equals(
                    intent.requestId())
                    || reservation.direction()
                    != StockReservationDirection.OUTBOUND
                    || reservation.quantity()
                    != output.totalQuantity(intent.multiplier())
                    || reservation.state() != expectedState
                    || reservation.revision() != expectedRevision
                    || !reservation.createdAt().equals(
                    intent.quoteCreatedAt())
                    || !reservation.updatedAt().equals(
                    expectedUpdatedAt)) {
                return false;
            }
        }
        return true;
    }

    private static boolean claimsMatch(
            List<EscrowClaim> expected,
            List<EscrowClaim> actual
    ) {
        if (expected.size() != actual.size()) {
            return false;
        }
        Map<UUID, EscrowClaim> byId = new HashMap<>();
        for (EscrowClaim claim : actual) {
            if (byId.put(claim.claimId(), claim) != null) {
                return false;
            }
        }
        for (EscrowClaim pending : expected) {
            EscrowClaim stored = byId.get(pending.claimId());
            if (stored == null
                    || !stored.transactionId().equals(
                    pending.transactionId())
                    || !stored.ownerId().equals(pending.ownerId())
                    || !stored.sourceKey().equals(pending.sourceKey())
                    || stored.kind() != ClaimKind.ITEM
                    || stored.originalUnits() != pending.originalUnits()
                    || stored.remainingUnits() < 0L
                    || stored.remainingUnits() > stored.originalUnits()
                    || !MessageDigest.isEqual(
                    stored.payload(), pending.payload())
                    || stored.status() == ClaimStatus.QUARANTINED
                    || !stored.label().equals(pending.label())
                    || !stored.createdAt().equals(pending.createdAt())
                    || stored.updatedAt().isBefore(stored.createdAt())) {
                return false;
            }
        }
        return true;
    }

    private static List<EscrowClaim> orderClaims(
            List<EscrowClaim> expected,
            List<EscrowClaim> actual
    ) {
        Map<UUID, EscrowClaim> byId = new HashMap<>();
        for (EscrowClaim claim : actual) {
            byId.put(claim.claimId(), claim);
        }
        return expected.stream().map(value -> byId.get(value.claimId()))
                .toList();
    }

    private static Result prepareFailure(
            PrepareDisposition disposition,
            UUID requestId
    ) {
        return switch (disposition) {
            case APPLIED, REPLAYED -> null;
            case STOCK_UNAVAILABLE -> Result.failure(
                    Status.STOCK_UNAVAILABLE, requestId);
            case STOCK_CHANGED -> Result.failure(
                    Status.STOCK_CHANGED, requestId);
            case CONFLICT -> Result.failure(
                    Status.REQUEST_CONFLICT, requestId);
            case ESCROW_UNAVAILABLE -> Result.failure(
                    Status.ESCROW_UNAVAILABLE, requestId);
            case RECOVERY_REQUIRED -> Result.failure(
                    Status.RECOVERY_REQUIRED, requestId);
        };
    }

    public interface Backend {
        boolean ready();

        Optional<ServerShopBarterIntent> intent(UUID requestId);

        Optional<EscrowTransaction> transaction(UUID transactionId);

        List<EscrowClaim> claimsForTransaction(UUID transactionId);

        List<StockReservation> stockReservations(UUID transactionId);

        PrepareDisposition prepare(
                ServerShopBarterIntent intent,
                StockMutationCommand.ReserveBatch reservation
        );

        TransitionDisposition abort(
                ServerShopBarterIntent terminalIntent,
                StockMutationCommand.ResolveBatch release
        );

        CommitDisposition commit(
                ServerShopBarterIntent completedIntent,
                ServerShopBarterCommit commit
        );
    }

    private record LiveBackend(EscrowRuntimeService runtime)
            implements Backend {
        private LiveBackend {
            Objects.requireNonNull(runtime, "runtime");
        }

        @Override
        public boolean ready() {
            return runtime.serverShopLifecycleReady();
        }

        @Override
        public Optional<ServerShopBarterIntent> intent(UUID requestId) {
            return runtime.serverShopBarterIntent(requestId);
        }

        @Override
        public Optional<EscrowTransaction> transaction(
                UUID transactionId
        ) {
            return runtime.transaction(transactionId);
        }

        @Override
        public List<EscrowClaim> claimsForTransaction(
                UUID transactionId
        ) {
            return runtime.claimsForTransaction(transactionId);
        }

        @Override
        public List<StockReservation> stockReservations(
                UUID transactionId
        ) {
            return runtime.stockReservations(transactionId);
        }

        @Override
        public PrepareDisposition prepare(
                ServerShopBarterIntent intent,
                StockMutationCommand.ReserveBatch reservation
        ) {
            if (!ready()) {
                return PrepareDisposition.ESCROW_UNAVAILABLE;
            }
            for (com.enviouse.futureshops.server.escrow.stock
                    .StockReservationRequest line
                    : reservation.reservations()) {
                com.enviouse.futureshops.server.escrow.stock
                        .CatalogStockState listing = runtime.stockListing(
                        line.stockKey()).orElse(null);
                if (listing == null || listing.status()
                        != com.enviouse.futureshops.server.escrow.stock
                        .CatalogStockStatus.ACTIVE) {
                    return PrepareDisposition.STOCK_UNAVAILABLE;
                }
                if (listing.revision()
                        != line.expectedListingRevision()) {
                    return PrepareDisposition.STOCK_CHANGED;
                }
                if (!listing.unlimited()
                        && listing.availableQuantity()
                        < line.quantity()) {
                    return PrepareDisposition.STOCK_UNAVAILABLE;
                }
            }
            try {
                EscrowCommitResult committed =
                        runtime.commitServerShopBarterLifecycle(
                                new ServerShopBarterLifecycleEvent.Prepare(
                                        intent, reservation));
                return committed.replayed()
                        ? PrepareDisposition.REPLAYED
                        : PrepareDisposition.APPLIED;
            } catch (ServerShopIntentConflictException exception) {
                return PrepareDisposition.CONFLICT;
            } catch (RuntimeException exception) {
                return PrepareDisposition.RECOVERY_REQUIRED;
            }
        }

        @Override
        public TransitionDisposition abort(
                ServerShopBarterIntent terminalIntent,
                StockMutationCommand.ResolveBatch release
        ) {
            try {
                EscrowCommitResult committed =
                        runtime.commitServerShopBarterLifecycle(
                                new ServerShopBarterLifecycleEvent.Abort(
                                        terminalIntent, release));
                return committed.replayed()
                        ? TransitionDisposition.REPLAYED
                        : TransitionDisposition.APPLIED;
            } catch (ServerShopIntentConflictException exception) {
                return TransitionDisposition.CONFLICT;
            } catch (RuntimeException exception) {
                return TransitionDisposition.RECOVERY_REQUIRED;
            }
        }

        @Override
        public CommitDisposition commit(
                ServerShopBarterIntent completedIntent,
                ServerShopBarterCommit commit
        ) {
            try {
                EscrowCommitResult committed =
                        runtime.commitServerShopBarterLifecycle(
                                new ServerShopBarterLifecycleEvent.Commit(
                                        completedIntent, commit));
                return committed.replayed()
                        ? CommitDisposition.REPLAYED
                        : CommitDisposition.APPLIED;
            } catch (ServerShopIntentConflictException exception) {
                return CommitDisposition.CONFLICT;
            } catch (RuntimeException exception) {
                return CommitDisposition.RECOVERY_REQUIRED;
            }
        }
    }

    public enum PrepareDisposition {
        APPLIED,
        REPLAYED,
        STOCK_UNAVAILABLE,
        STOCK_CHANGED,
        CONFLICT,
        ESCROW_UNAVAILABLE,
        RECOVERY_REQUIRED
    }

    public enum TransitionDisposition {
        APPLIED,
        REPLAYED,
        CONFLICT,
        RECOVERY_REQUIRED
    }

    public enum CommitDisposition {
        APPLIED,
        REPLAYED,
        CONFLICT,
        RECOVERY_REQUIRED
    }

    public record Identity(
            UUID requestId,
            UUID playerId,
            String shopId,
            String recipeId,
            int multiplier
    ) {
        public Identity {
            requestId = ServerShopBarterCommit.requireUuid(
                    requestId, "requestId");
            playerId = ServerShopBarterCommit.requireUuid(
                    playerId, "playerId");
            shopId = ServerShopBarterCommit.requireIdentifier(
                    shopId, "shopId");
            recipeId = ServerShopBarterCommit.requireIdentifier(
                    recipeId, "recipeId");
            multiplier = ServerShopBarterCommit.requireMultiplier(
                    multiplier);
        }

        public String wireFingerprint() {
            return ServerShopBarterCommit.wireFingerprint(requestId,
                    playerId, shopId, recipeId, multiplier);
        }
    }

    public record PreparedRequest(
            Identity identity,
            long quoteRevision,
            long recipeRevision,
            Instant quoteCreatedAt,
            List<ServerShopBarterCommit.Ingredient> ingredients,
            List<ServerShopBarterCommit.OutputLine> outputs,
            DimensionAwareShopReference shopReference
    ) {
        public PreparedRequest {
            identity = Objects.requireNonNull(identity, "identity");
            ServerShopBarterCommit.requireRevision(
                    quoteRevision, "quote revision");
            ServerShopBarterCommit.requireRevision(
                    recipeRevision, "recipe revision");
            quoteCreatedAt = Objects.requireNonNull(
                    quoteCreatedAt, "quoteCreatedAt");
            ingredients = ServerShopBarterCommit.copyIngredients(
                    ingredients);
            outputs = ServerShopBarterCommit.copyOutputs(outputs);
            shopReference = Objects.requireNonNull(
                    shopReference, "shopReference");
            new ServerShopBarterIntent(identity.requestId(),
                    identity.playerId(), identity.shopId(),
                    identity.recipeId(), identity.multiplier(),
                    quoteRevision, recipeRevision, quoteCreatedAt,
                    ingredients, outputs, shopReference,
                    ServerShopBarterIntent.Status.PREPARED, 0L);
        }

        static PreparedRequest from(ServerShopBarterIntent intent) {
            return new PreparedRequest(new Identity(intent.requestId(),
                    intent.playerId(), intent.shopId(), intent.recipeId(),
                    intent.multiplier()), intent.quoteRevision(),
                    intent.recipeRevision(), intent.quoteCreatedAt(),
                    intent.ingredients(), intent.outputs(),
                    intent.shopReference());
        }
    }

    public enum Status {
        SUCCESS,
        ESCROW_UNAVAILABLE,
        REQUEST_CONFLICT,
        MISSING_INGREDIENTS,
        UNSUPPORTED_ITEM,
        ITEM_CUSTODY_ABORTED,
        STOCK_UNAVAILABLE,
        STOCK_CHANGED,
        RECOVERY_REQUIRED;

        public boolean success() {
            return this == SUCCESS;
        }
    }

    public record Result(
            Status status,
            UUID requestId,
            Optional<ServerShopBarterCommit> commit,
            List<EscrowClaim> outputClaims,
            boolean replayed
    ) {
        public Result {
            status = Objects.requireNonNull(status, "status");
            requestId = ServerShopBarterCommit.requireUuid(
                    requestId, "requestId");
            commit = Objects.requireNonNull(commit, "commit");
            outputClaims = List.copyOf(Objects.requireNonNull(
                    outputClaims, "outputClaims"));
            if (status.success()) {
                ServerShopBarterCommit value = commit.orElseThrow();
                Set<UUID> expected = new HashSet<>(value.outputClaims()
                        .stream().map(EscrowClaim::claimId).toList());
                Set<UUID> actual = new HashSet<>(outputClaims.stream()
                        .map(EscrowClaim::claimId).toList());
                if (!value.requestId().equals(requestId)
                        || expected.size() != outputClaims.size()
                        || !expected.equals(actual)) {
                    throw new IllegalArgumentException(
                            "Server shop barter success result is invalid");
                }
            } else if (commit.isPresent() || !outputClaims.isEmpty()
                    || replayed) {
                throw new IllegalArgumentException(
                        "Server shop barter failure result is invalid");
            }
        }

        static Result success(
                ServerShopBarterCommit commit,
                List<EscrowClaim> claims,
                boolean replayed
        ) {
            return new Result(Status.SUCCESS, commit.requestId(),
                    Optional.of(commit), claims, replayed);
        }

        static Result failure(Status status, UUID requestId) {
            if (status.success()) {
                throw new IllegalArgumentException(
                        "Server shop barter failure status is invalid");
            }
            return new Result(status, requestId, Optional.empty(),
                    List.of(), false);
        }

        Result withReplay(boolean replayed) {
            if (!success()) {
                return this;
            }
            return new Result(status, requestId, commit, outputClaims,
                    replayed);
        }

        public boolean success() {
            return status.success();
        }
    }
}

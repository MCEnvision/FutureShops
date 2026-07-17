package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.Config;
import com.enviouse.futureshops.money.CurrencyManager;
import com.enviouse.futureshops.money.ItemStackSnapshotCodec;
import com.enviouse.futureshops.server.escrow.claim.ClaimKind;
import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationReceipt;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryExecutionResult;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryExecutionStatus;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;
import com.enviouse.futureshops.server.escrow.model.DimensionAwareShopReference;
import com.enviouse.futureshops.server.escrow.model.EscrowAssetLot;
import com.enviouse.futureshops.server.escrow.model.EscrowAssetLotType;
import com.enviouse.futureshops.server.escrow.model.EscrowOperation;
import com.enviouse.futureshops.server.escrow.model.EscrowState;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;
import com.enviouse.futureshops.server.escrow.stock.StockKey;
import com.enviouse.futureshops.server.escrow.stock.StockReservation;
import com.enviouse.futureshops.server.escrow.stock.StockReservationDirection;
import com.enviouse.futureshops.server.escrow.stock.StockReservationId;
import com.enviouse.futureshops.server.escrow.stock.StockReservationState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ServerShopSellService {
    private ServerShopSellService() {
    }

    public static Result sell(
            ServerPlayer player,
            PreparedRequest request
    ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(request, "request");
        if (!player.getUUID().equals(request.identity().playerId())) {
            return Result.failure(Status.INVALID_REQUEST,
                    request.identity().requestId());
        }
        EscrowRuntimeService runtime = EscrowRuntimeManager.getOrNull();
        if (runtime == null) {
            return Result.failure(Status.ESCROW_UNAVAILABLE,
                    request.identity().requestId());
        }
        try {
            return execute(request, new LiveBackend(runtime),
                    runtime.serverShopSellCustody(player));
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
            return Optional.of(Result.failure(Status.INVALID_REQUEST,
                    identity.requestId()));
        }
        EscrowRuntimeService runtime = EscrowRuntimeManager.getOrNull();
        if (runtime == null) {
            return Optional.empty();
        }
        try {
            return resolveReplay(identity, new LiveBackend(runtime),
                    runtime.serverShopSellCustody(player));
        } catch (RuntimeException exception) {
            return Optional.of(Result.failure(Status.ESCROW_UNAVAILABLE,
                    identity.requestId()));
        }
    }

    public static Result execute(
            PreparedRequest request,
            Backend backend,
            ServerShopSellItemCustody custody
    ) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(backend, "backend");
        Objects.requireNonNull(custody, "custody");
        try {
            Optional<Result> replay = resolveReplay(
                    request.identity(), backend, custody);
            if (replay.isPresent()) {
                return replay.orElseThrow();
            }
            Optional<ServerShopSellIntent> storedIntent = backend.intent(
                    request.identity().requestId());
            if (storedIntent.isPresent()) {
                if (!backend.ready()) {
                    return Result.failure(Status.RECOVERY_REQUIRED,
                            request.identity().requestId());
                }
                return resume(storedIntent.orElseThrow(), backend,
                        custody);
            }
            if (!backend.ready()) {
                return Result.failure(Status.ESCROW_UNAVAILABLE,
                        request.identity().requestId());
            }
            return executeFresh(request, backend, custody);
        } catch (RuntimeException exception) {
            try {
                return resolveReplay(request.identity(), backend, custody)
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
            Identity identity,
            Backend backend,
            ServerShopSellItemCustody custody
    ) {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(backend, "backend");
        Objects.requireNonNull(custody, "custody");
        Optional<EscrowTransaction> transaction = backend.transaction(
                identity.requestId());
        Optional<LedgerTransaction> ledger = backend.ledgerTransaction(
                identity.requestId());
        List<EscrowClaim> claims = List.copyOf(
                backend.claimsForTransaction(identity.requestId()));
        List<StockReservation> reservations = List.copyOf(
                backend.stockReservations(identity.requestId()));
        Optional<ServerShopSellIntent> intent = backend.intent(
                identity.requestId());
        ServerShopSellItemCustody.Inspection itemEvidence =
                custody.inspect(ServerShopSellCommit.itemCustodyRequestId(
                        identity.requestId()));
        if (transaction.isEmpty()) {
            if (ledger.isPresent() || !claims.isEmpty()
                    || !reservations.isEmpty()) {
                return Optional.of(Result.failure(
                        Status.RECOVERY_REQUIRED, identity.requestId()));
            }
            if (intent.isPresent()) {
                ServerShopSellIntent storedIntent = intent.orElseThrow();
                if (!identity.wireFingerprint().equals(
                        storedIntent.wireFingerprint())) {
                    return Optional.of(Result.failure(
                            Status.REQUEST_CONFLICT,
                            identity.requestId()));
                }
                if (storedIntent.status()
                        != ServerShopSellIntent.Status.PREPARED) {
                    if (!terminalEvidenceMatches(storedIntent.status(),
                            itemEvidence.state())) {
                        return Optional.of(Result.failure(
                                Status.RECOVERY_REQUIRED,
                                identity.requestId()));
                    }
                    return Optional.of(Result.failure(
                            terminalStatus(storedIntent.status()),
                            identity.requestId()));
                }
                if (itemEvidence.state()
                        == ServerShopSellItemCustody.State.ABORTED
                        || itemEvidence.state()
                        == ServerShopSellItemCustody.State.QUARANTINED) {
                    return Optional.of(Result.failure(
                            Status.RECOVERY_REQUIRED,
                            identity.requestId()));
                }
                return Optional.empty();
            }
            return switch (itemEvidence.state()) {
                case ABORTED -> Optional.of(Result.failure(
                        Status.RECOVERY_REQUIRED,
                        identity.requestId()));
                case PREPARED, COMMITTED, QUARANTINED -> Optional.of(
                        Result.failure(
                        Status.RECOVERY_REQUIRED, identity.requestId()));
                case NONE -> Optional.empty();
            };
        }
        EscrowTransaction stored = transaction.orElseThrow();
        if (!stored.transactionId().value().equals(identity.requestId())
                || stored.operation() != EscrowOperation.SERVER_SHOP_SELL) {
            return Optional.of(Result.failure(Status.REQUEST_CONFLICT,
                    identity.requestId()));
        }
        EscrowAssetLot money;
        try {
            money = moneyAsset(stored);
        } catch (RuntimeException exception) {
            return Optional.of(Result.failure(Status.RECOVERY_REQUIRED,
                    identity.requestId()));
        }
        if (!identity.wireFingerprint().equals(
                money.attributes().get("wire_fingerprint"))) {
            return Optional.of(Result.failure(Status.REQUEST_CONFLICT,
                    identity.requestId()));
        }
        if (intent.isEmpty()
                || intent.orElseThrow().status()
                != ServerShopSellIntent.Status.COMMITTED
                || !identity.wireFingerprint().equals(
                intent.orElseThrow().wireFingerprint())) {
            return Optional.of(Result.failure(Status.RECOVERY_REQUIRED,
                    identity.requestId()));
        }
        if (stored.state() != EscrowState.COMPLETED
                || ledger.isEmpty()
                || itemEvidence.state()
                != ServerShopSellItemCustody.State.COMMITTED
                || itemEvidence.receipt().isEmpty()) {
            return Optional.of(Result.failure(Status.RECOVERY_REQUIRED,
                    identity.requestId()));
        }
        try {
            ServerShopSellCommit expected = fromEvidence(identity, stored,
                    ledger.orElseThrow(), itemEvidence.receipt()
                            .orElseThrow());
            if (!intent.orElseThrow().commit(itemEvidence.receipt()
                            .orElseThrow()).equals(expected)
                    || !expected.completedTransaction().equals(stored)
                    || !expected.ledgerTransaction().equals(
                    ledger.orElseThrow())
                    || !reservationsMatch(expected, reservations)
                    || !claimsMatch(expected.overflowClaim(), claims)) {
                return Optional.of(Result.failure(
                        Status.RECOVERY_REQUIRED, identity.requestId()));
            }
            Optional<EscrowClaim> actualClaim = claims.isEmpty()
                    ? Optional.empty() : Optional.of(claims.get(0));
            return Optional.of(Result.success(expected, actualClaim,
                    true));
        } catch (RuntimeException exception) {
            return Optional.of(Result.failure(Status.RECOVERY_REQUIRED,
                    identity.requestId()));
        }
    }

    private static Result executeFresh(
            PreparedRequest request,
            Backend backend,
            ServerShopSellItemCustody custody
    ) {
        Identity identity = request.identity();
        PreflightResult preflight = Objects.requireNonNull(
                backend.preflight(identity.playerId(),
                        new StockKey(identity.shopId(),
                                identity.listingId()),
                        identity.quantity(),
                        request.expectedStockRevision()),
                "preflight");
        if (preflight.disposition()
                == PreflightDisposition.STOCK_UNAVAILABLE) {
            return Result.failure(Status.STOCK_UNAVAILABLE,
                    identity.requestId());
        }
        if (preflight.disposition()
                == PreflightDisposition.STOCK_CHANGED) {
            return Result.failure(Status.STOCK_CHANGED,
                    identity.requestId());
        }
        if (preflight.disposition()
                != PreflightDisposition.READY
                || preflight.wallet().isEmpty()) {
            return Result.failure(Status.ESCROW_UNAVAILABLE,
                    identity.requestId());
        }
        ServerShopSellIntent intent = ServerShopSellIntent.prepared(
                request, preflight.wallet().orElseThrow());
        IntentDisposition intentDisposition = Objects.requireNonNull(
                backend.prepareIntent(intent), "intentDisposition");
        if (intentDisposition == IntentDisposition.CONFLICT) {
            return Result.failure(Status.REQUEST_CONFLICT,
                    identity.requestId());
        }
        if (intentDisposition != IntentDisposition.APPLIED
                && intentDisposition != IntentDisposition.REPLAYED) {
            return Result.failure(Status.RECOVERY_REQUIRED,
                    identity.requestId());
        }
        ServerShopSellIntent stored = backend.intent(identity.requestId())
                .filter(intent::equals).orElse(null);
        if (stored == null) {
            return Result.failure(Status.RECOVERY_REQUIRED,
                    identity.requestId());
        }
        return resume(stored, backend, custody);
    }

    private static Result resume(
            ServerShopSellIntent intent,
            Backend backend,
            ServerShopSellItemCustody custody
    ) {
        if (intent.status() != ServerShopSellIntent.Status.PREPARED) {
            return Result.failure(terminalStatus(intent.status()),
                    intent.requestId());
        }
        ServerShopSellItemCustody.Inspection inspection = custody.inspect(
                ServerShopSellCommit.itemCustodyRequestId(
                        intent.requestId()));
        if (inspection.state()
                == ServerShopSellItemCustody.State.QUARANTINED) {
            return Result.failure(Status.RECOVERY_REQUIRED,
                    intent.requestId());
        }
        if (inspection.state()
                == ServerShopSellItemCustody.State.ABORTED) {
            return abort(intent, ServerShopSellIntent.Status.ABORTED_CUSTODY,
                    backend, Status.ITEM_CUSTODY_ABORTED);
        }
        ItemInventoryExecutionResult itemResult;
        if (inspection.state()
                == ServerShopSellItemCustody.State.COMMITTED) {
            ItemInventoryMutationReceipt receipt = inspection.receipt()
                    .orElse(null);
            if (receipt == null) {
                return Result.failure(Status.RECOVERY_REQUIRED,
                        intent.requestId());
            }
            itemResult = ItemInventoryExecutionResult.replayed(receipt);
        } else {
            itemResult = custody.extract(intent.requestId(),
                    ServerShopSellCommit.itemCustodyRequestId(
                            intent.requestId()),
                    ServerShopSellCommit.custodyEntries(
                            intent.requestId(), intent.quantity(),
                            intent.exactItemTemplate()));
        }
        if (itemResult.status()
                == ItemInventoryExecutionStatus.INSUFFICIENT_ITEMS) {
            return abort(intent,
                    ServerShopSellIntent.Status.ABORTED_MISSING_ITEMS,
                    backend, Status.MISSING_ITEMS);
        }
        if (itemResult.status()
                == ItemInventoryExecutionStatus.UNSUPPORTED_STACK) {
            return abort(intent,
                    ServerShopSellIntent.Status.ABORTED_UNSUPPORTED_ITEM,
                    backend, Status.UNSUPPORTED_ITEM);
        }
        if (itemResult.status()
                == ItemInventoryExecutionStatus.ABORTED) {
            return abort(intent,
                    ServerShopSellIntent.Status.ABORTED_CUSTODY,
                    backend, Status.ITEM_CUSTODY_ABORTED);
        }
        if (itemResult.status()
                != ItemInventoryExecutionStatus.APPLIED
                && itemResult.status()
                != ItemInventoryExecutionStatus.REPLAYED) {
            return Result.failure(Status.RECOVERY_REQUIRED,
                    intent.requestId());
        }
        ItemInventoryMutationReceipt receipt = itemResult.receipt()
                .orElseThrow(() -> new IllegalStateException(
                        "Server shop sell custody receipt is missing"));
        ServerShopSellCommit commit = intent.commit(receipt);
        CommitDisposition disposition = Objects.requireNonNull(
                backend.commit(commit), "commitDisposition");
        if (disposition != CommitDisposition.APPLIED
                && disposition != CommitDisposition.REPLAYED) {
            return Result.failure(Status.RECOVERY_REQUIRED,
                    intent.requestId());
        }
        Identity identity = new Identity(intent.requestId(),
                intent.playerId(), intent.shopId(), intent.listingId(),
                intent.quantity());
        return resolveReplay(identity, backend, custody)
                .filter(Result::success)
                .map(result -> result.withReplay(
                        disposition == CommitDisposition.REPLAYED))
                .orElseGet(() -> Result.failure(Status.RECOVERY_REQUIRED,
                        intent.requestId()));
    }

    private static Result abort(
            ServerShopSellIntent intent,
            ServerShopSellIntent.Status terminal,
            Backend backend,
            Status resultStatus
    ) {
        ServerShopSellIntent aborted = intent.abort(terminal);
        IntentDisposition disposition = Objects.requireNonNull(
                backend.abortIntent(intent, aborted), "intentDisposition");
        if (disposition != IntentDisposition.APPLIED
                && disposition != IntentDisposition.REPLAYED) {
            return Result.failure(Status.RECOVERY_REQUIRED,
                    intent.requestId());
        }
        return Result.failure(resultStatus, intent.requestId());
    }

    private static boolean terminalEvidenceMatches(
            ServerShopSellIntent.Status status,
            ServerShopSellItemCustody.State state
    ) {
        return switch (status) {
            case PREPARED, COMMITTED -> false;
            case ABORTED_MISSING_ITEMS, ABORTED_UNSUPPORTED_ITEM ->
                    state == ServerShopSellItemCustody.State.NONE
                            || state == ServerShopSellItemCustody.State.ABORTED;
            case ABORTED_CUSTODY ->
                    state == ServerShopSellItemCustody.State.ABORTED;
        };
    }

    private static Status terminalStatus(
            ServerShopSellIntent.Status status
    ) {
        return switch (status) {
            case PREPARED, COMMITTED -> Status.RECOVERY_REQUIRED;
            case ABORTED_MISSING_ITEMS -> Status.MISSING_ITEMS;
            case ABORTED_UNSUPPORTED_ITEM -> Status.UNSUPPORTED_ITEM;
            case ABORTED_CUSTODY -> Status.ITEM_CUSTODY_ABORTED;
        };
    }

    private static ServerShopSellCommit fromEvidence(
            Identity identity,
            EscrowTransaction transaction,
            LedgerTransaction ledger,
            ItemInventoryMutationReceipt receipt
    ) {
        EscrowAssetLot money = moneyAsset(transaction);
        Map<String, String> attributes = money.attributes();
        List<EscrowAssetLot> itemLots = transaction.assetLots().stream()
                .filter(value -> value.type()
                        == EscrowAssetLotType.ITEM_STACK)
                .toList();
        if (itemLots.isEmpty()) {
            throw new IllegalArgumentException(
                    "Server shop sell item evidence is missing");
        }
        ItemStack template = ItemStackSnapshotCodec.decode(
                itemLots.get(0).serializedPayload());
        template.setCount(1);
        byte[] exactTemplate = ItemStackSnapshotCodec.encode(template);
        ServerShopSellCommit expected = ServerShopSellCommit.create(
                identity.requestId(), identity.playerId(),
                identity.shopId(), identity.listingId(),
                requireAttribute(attributes, "item_id"),
                identity.quantity(), parseLong(attributes, "unit_price"),
                parseLong(attributes, "quote_revision"),
                parseLong(attributes, "stock_revision"),
                Instant.parse(requireAttribute(
                        attributes, "quote_created_at")),
                parseLong(attributes, "wallet_before"),
                parseLong(attributes, "debt_before"),
                parseLong(attributes, "reserved_before"),
                parseLong(attributes, "wallet_limit"),
                parseLong(attributes, "configuration_generation"),
                requireAttribute(attributes, "currency_name"),
                parseInt(attributes, "currency_decimals"), exactTemplate,
                receipt, transaction.shopReference().orElseThrow());
        if (!expected.ledgerTransaction().equals(ledger)
                || !expected.quoteFingerprint().equals(
                attributes.get("quote_fingerprint"))) {
            throw new IllegalArgumentException(
                    "Server shop sell quote evidence conflicts");
        }
        return expected;
    }

    private static EscrowAssetLot moneyAsset(
            EscrowTransaction transaction
    ) {
        List<EscrowAssetLot> money = transaction.assetLots().stream()
                .filter(value -> value.type()
                        == EscrowAssetLotType.WALLET_MONEY)
                .toList();
        if (money.size() != 1
                || money.get(0).money().isEmpty()) {
            throw new IllegalArgumentException(
                    "Server shop sell money evidence is invalid");
        }
        return money.get(0);
    }

    private static boolean reservationsMatch(
            ServerShopSellCommit commit,
            List<StockReservation> reservations
    ) {
        if (reservations.size() != 1) {
            return false;
        }
        StockReservation reservation = reservations.get(0);
        StockKey key = new StockKey(commit.shopId(), commit.listingId());
        return reservation.reservationId().equals(
                StockReservationId.forTransaction(commit.requestId(), key,
                        StockReservationDirection.INBOUND))
                && reservation.transactionId().equals(commit.requestId())
                && reservation.stockKey().equals(key)
                && reservation.direction()
                == StockReservationDirection.INBOUND
                && reservation.quantity() == commit.quantity()
                && reservation.state() == StockReservationState.COMMITTED
                && reservation.revision() == 1L;
    }

    private static boolean claimsMatch(
            Optional<EscrowClaim> expected,
            List<EscrowClaim> actual
    ) {
        if (expected.isEmpty()) {
            return actual.isEmpty();
        }
        if (actual.size() != 1) {
            return false;
        }
        EscrowClaim pending = expected.orElseThrow();
        EscrowClaim stored = actual.get(0);
        return stored.claimId().equals(pending.claimId())
                && stored.transactionId().equals(pending.transactionId())
                && stored.ownerId().equals(pending.ownerId())
                && stored.sourceKey().equals(pending.sourceKey())
                && stored.kind() == ClaimKind.MONEY
                && stored.originalUnits() == pending.originalUnits()
                && stored.remainingUnits() >= 0L
                && stored.remainingUnits() <= stored.originalUnits()
                && stored.payload().length == 0
                && stored.status() != ClaimStatus.QUARANTINED
                && stored.label().equals(pending.label())
                && stored.createdAt().equals(pending.createdAt())
                && !stored.updatedAt().isBefore(stored.createdAt());
    }

    private static String requireAttribute(
            Map<String, String> attributes,
            String key
    ) {
        String value = attributes.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Server shop sell evidence attribute is missing");
        }
        return value;
    }

    private static long parseLong(
            Map<String, String> attributes,
            String key
    ) {
        return Long.parseLong(requireAttribute(attributes, key));
    }

    private static int parseInt(
            Map<String, String> attributes,
            String key
    ) {
        return Integer.parseInt(requireAttribute(attributes, key));
    }

    public interface Backend {
        boolean ready();

        Optional<ServerShopSellIntent> intent(UUID requestId);

        IntentDisposition prepareIntent(ServerShopSellIntent intent);

        IntentDisposition abortIntent(
                ServerShopSellIntent expected,
                ServerShopSellIntent replacement
        );

        Optional<EscrowTransaction> transaction(UUID transactionId);

        Optional<LedgerTransaction> ledgerTransaction(UUID transactionId);

        List<EscrowClaim> claimsForTransaction(UUID transactionId);

        List<StockReservation> stockReservations(UUID transactionId);

        PreflightResult preflight(
                UUID playerId,
                StockKey stockKey,
                int quantity,
                long expectedStockRevision
        );

        CommitDisposition commit(ServerShopSellCommit commit);
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
        public Optional<ServerShopSellIntent> intent(UUID requestId) {
            return runtime.serverShopSellIntent(requestId);
        }

        @Override
        public IntentDisposition prepareIntent(
                ServerShopSellIntent intent
        ) {
            try {
                EscrowCommitResult committed =
                        runtime.commitServerShopSellLifecycle(
                                new ServerShopSellLifecycleEvent.Prepare(
                                        intent));
                return committed.replayed()
                        ? IntentDisposition.REPLAYED
                        : IntentDisposition.APPLIED;
            } catch (ServerShopIntentConflictException exception) {
                return IntentDisposition.CONFLICT;
            } catch (RuntimeException exception) {
                return IntentDisposition.RECOVERY_REQUIRED;
            }
        }

        @Override
        public IntentDisposition abortIntent(
                ServerShopSellIntent expected,
                ServerShopSellIntent replacement
        ) {
            try {
                EscrowCommitResult committed =
                        runtime.commitServerShopSellLifecycle(
                                new ServerShopSellLifecycleEvent.Abort(
                                        expected, replacement));
                return committed.replayed()
                        ? IntentDisposition.REPLAYED
                        : IntentDisposition.APPLIED;
            } catch (ServerShopIntentConflictException exception) {
                return IntentDisposition.CONFLICT;
            } catch (RuntimeException exception) {
                return IntentDisposition.RECOVERY_REQUIRED;
            }
        }

        @Override
        public Optional<EscrowTransaction> transaction(
                UUID transactionId
        ) {
            return runtime.transaction(transactionId);
        }

        @Override
        public Optional<LedgerTransaction> ledgerTransaction(
                UUID transactionId
        ) {
            return runtime.ledgerTransaction(transactionId);
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
        public PreflightResult preflight(
                UUID playerId,
                StockKey stockKey,
                int quantity,
                long expectedStockRevision
        ) {
            if (!ready()) {
                return PreflightResult.failure(
                        PreflightDisposition.ESCROW_UNAVAILABLE);
            }
            com.enviouse.futureshops.server.escrow.stock
                    .CatalogStockState listing = runtime.stockListing(
                    stockKey).orElse(null);
            if (listing == null || listing.status()
                    != com.enviouse.futureshops.server.escrow.stock
                    .CatalogStockStatus.ACTIVE) {
                return PreflightResult.failure(
                        PreflightDisposition.STOCK_UNAVAILABLE);
            }
            if (listing.revision() != expectedStockRevision) {
                return PreflightResult.failure(
                        PreflightDisposition.STOCK_CHANGED);
            }
            if (!listing.unlimited()) {
                long held = 0L;
                try {
                    for (StockReservation reservation
                            : runtime.stockSnapshot().reservations()
                            .values()) {
                        if (reservation.stockKey().equals(stockKey)
                                && reservation.inventoryBacked()
                                && reservation.state()
                                == StockReservationState.HELD) {
                            held = Math.addExact(held,
                                    reservation.quantity());
                        }
                    }
                    long exposure = Math.addExact(
                            listing.availableQuantity(), held);
                    long capacity = Math.max(0L,
                            listing.policy().configuredQuantity()
                                    - exposure);
                    if (capacity < quantity) {
                        return PreflightResult.failure(
                                PreflightDisposition.STOCK_UNAVAILABLE);
                    }
                } catch (ArithmeticException exception) {
                    return PreflightResult.failure(
                            PreflightDisposition.ESCROW_UNAVAILABLE);
                }
            }
            try (CurrencyManager.ConfigurationReadLease lease =
                         CurrencyManager.acquireConfigurationReadLease()) {
                return PreflightResult.ready(new WalletSnapshot(
                        runtime.ledgerBalance(
                                ServerShopSellCommit.walletAccount(
                                        playerId)),
                        runtime.ledgerBalance(
                                ServerShopSellCommit.debtAccount(
                                        playerId)),
                        runtime.ledgerBalance(
                                ServerShopSellCommit.reservedAccount(
                                        playerId)),
                        Config.economyMaxBalanceMinorUnits,
                        lease.generation(), Config.economyCurrencyName,
                        Config.economyCurrencyDecimals));
            } catch (RuntimeException exception) {
                return PreflightResult.failure(
                        PreflightDisposition.ESCROW_UNAVAILABLE);
            }
        }

        @Override
        public CommitDisposition commit(ServerShopSellCommit commit) {
            try {
                ServerShopSellIntent current = runtime
                        .serverShopSellIntent(commit.requestId())
                        .orElseThrow(() ->
                                new ServerShopIntentConflictException(
                                        "Server shop sell intent is missing"));
                EscrowCommitResult committed =
                        runtime.commitServerShopSellLifecycle(
                                new ServerShopSellLifecycleEvent.Commit(
                                        current.complete(), commit));
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

    public enum CommitDisposition {
        APPLIED,
        REPLAYED,
        CONFLICT,
        RECOVERY_REQUIRED
    }

    public enum IntentDisposition {
        APPLIED,
        REPLAYED,
        CONFLICT,
        RECOVERY_REQUIRED
    }

    public enum PreflightDisposition {
        READY,
        STOCK_UNAVAILABLE,
        STOCK_CHANGED,
        ESCROW_UNAVAILABLE
    }

    public record PreflightResult(
            PreflightDisposition disposition,
            Optional<WalletSnapshot> wallet
    ) {
        public PreflightResult {
            disposition = Objects.requireNonNull(
                    disposition, "disposition");
            wallet = Objects.requireNonNull(wallet, "wallet");
            if (disposition == PreflightDisposition.READY
                    != wallet.isPresent()) {
                throw new IllegalArgumentException(
                        "Server shop sell preflight result is invalid");
            }
        }

        public static PreflightResult ready(WalletSnapshot wallet) {
            return new PreflightResult(PreflightDisposition.READY,
                    Optional.of(Objects.requireNonNull(wallet, "wallet")));
        }

        public static PreflightResult failure(
                PreflightDisposition disposition
        ) {
            if (disposition == PreflightDisposition.READY) {
                throw new IllegalArgumentException(
                        "Server shop sell preflight failure is invalid");
            }
            return new PreflightResult(disposition, Optional.empty());
        }
    }

    public record WalletSnapshot(
            long walletMinorUnits,
            long debtMinorUnits,
            long reservedMinorUnits,
            long walletBalanceLimitMinorUnits,
            long configurationGeneration,
            String currencyName,
            int currencyDecimals
    ) {
        public WalletSnapshot {
            ServerShopSellCommit.requireWalletSnapshot(walletMinorUnits,
                    debtMinorUnits, reservedMinorUnits,
                    walletBalanceLimitMinorUnits);
            if (configurationGeneration < 0L
                    || currencyDecimals < 0 || currencyDecimals > 6) {
                throw new IllegalArgumentException(
                        "Server shop sell wallet policy is invalid");
            }
            currencyName = ServerShopSellCommit.normalizeCurrencyName(
                    currencyName);
        }
    }

    public record Identity(
            UUID requestId,
            UUID playerId,
            String shopId,
            String listingId,
            int quantity
    ) {
        public Identity {
            requestId = ServerShopSellCommit.requireUuid(
                    requestId, "requestId");
            playerId = ServerShopSellCommit.requireUuid(
                    playerId, "playerId");
            shopId = ServerShopSellCommit.requireIdentifier(
                    shopId, "shopId");
            listingId = ServerShopSellCommit.requireIdentifier(
                    listingId, "listingId");
            if (quantity <= 0) {
                throw new IllegalArgumentException(
                        "Server shop sell identity quantity is invalid");
            }
        }

        public String wireFingerprint() {
            return ServerShopSellCommit.wireFingerprint(requestId,
                    playerId, shopId, listingId, quantity);
        }
    }

    public record PreparedRequest(
            Identity identity,
            String itemId,
            long unitPriceMinorUnits,
            long quoteRevision,
            long expectedStockRevision,
            Instant quoteCreatedAt,
            byte[] exactItemTemplate,
            DimensionAwareShopReference shopReference
    ) {
        public PreparedRequest {
            identity = Objects.requireNonNull(identity, "identity");
            itemId = ServerShopSellCommit.requireIdentifier(
                    itemId, "itemId");
            if (unitPriceMinorUnits <= 0L) {
                throw new IllegalArgumentException(
                        "Server shop sell request price is invalid");
            }
            Math.multiplyExact(unitPriceMinorUnits, identity.quantity());
            ServerShopSellCommit.requireRevision(
                    quoteRevision, "quote revision");
            ServerShopSellCommit.requireRevision(expectedStockRevision,
                    "expected stock revision");
            quoteCreatedAt = Objects.requireNonNull(
                    quoteCreatedAt, "quoteCreatedAt");
            exactItemTemplate = Objects.requireNonNull(
                    exactItemTemplate, "exactItemTemplate").clone();
            ServerShopSellCommit.requireExactTemplate(
                    exactItemTemplate, itemId);
            ServerShopSellCommit.custodyEntries(identity.requestId(),
                    identity.quantity(), exactItemTemplate);
            shopReference = Objects.requireNonNull(
                    shopReference, "shopReference");
            if (!shopReference.shopId().equals(identity.shopId())) {
                throw new IllegalArgumentException(
                        "Server shop sell request shop reference conflicts");
            }
        }

        @Override
        public byte[] exactItemTemplate() {
            return exactItemTemplate.clone();
        }

        @Override
        public boolean equals(Object object) {
            return object instanceof PreparedRequest other
                    && identity.equals(other.identity)
                    && itemId.equals(other.itemId)
                    && unitPriceMinorUnits == other.unitPriceMinorUnits
                    && quoteRevision == other.quoteRevision
                    && expectedStockRevision
                    == other.expectedStockRevision
                    && quoteCreatedAt.equals(other.quoteCreatedAt)
                    && Arrays.equals(exactItemTemplate,
                    other.exactItemTemplate)
                    && shopReference.equals(other.shopReference);
        }

        @Override
        public int hashCode() {
            return 31 * Objects.hash(identity, itemId,
                    unitPriceMinorUnits, quoteRevision,
                    expectedStockRevision, quoteCreatedAt, shopReference)
                    + Arrays.hashCode(exactItemTemplate);
        }
    }

    public enum Status {
        SUCCESS,
        ESCROW_UNAVAILABLE,
        INVALID_REQUEST,
        REQUEST_CONFLICT,
        MISSING_ITEMS,
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
            Optional<ServerShopSellCommit> commit,
            Optional<EscrowClaim> overflowClaim,
            long payoutMinorUnits,
            long resultingBalanceMinorUnits,
            boolean replayed
    ) {
        public Result {
            status = Objects.requireNonNull(status, "status");
            requestId = ServerShopSellCommit.requireUuid(
                    requestId, "requestId");
            commit = Objects.requireNonNull(commit, "commit");
            overflowClaim = Objects.requireNonNull(
                    overflowClaim, "overflowClaim");
            if (status.success()) {
                ServerShopSellCommit value = commit.orElseThrow();
                if (!value.requestId().equals(requestId)
                        || payoutMinorUnits != value.payoutMinorUnits()
                        || resultingBalanceMinorUnits
                        != value.resultingBalanceMinorUnits()) {
                    throw new IllegalArgumentException(
                            "Server shop sell success result is invalid");
                }
            } else if (commit.isPresent() || overflowClaim.isPresent()
                    || payoutMinorUnits != 0L
                    || resultingBalanceMinorUnits != 0L || replayed) {
                throw new IllegalArgumentException(
                        "Server shop sell failure result is invalid");
            }
        }

        static Result success(
                ServerShopSellCommit commit,
                Optional<EscrowClaim> overflowClaim,
                boolean replayed
        ) {
            return new Result(Status.SUCCESS, commit.requestId(),
                    Optional.of(commit), overflowClaim,
                    commit.payoutMinorUnits(),
                    commit.resultingBalanceMinorUnits(), replayed);
        }

        static Result failure(Status status, UUID requestId) {
            if (status.success()) {
                throw new IllegalArgumentException(
                        "Server shop sell failure status is invalid");
            }
            return new Result(status, requestId, Optional.empty(),
                    Optional.empty(), 0L, 0L, false);
        }

        Result withReplay(boolean replayed) {
            if (!success()) {
                return this;
            }
            return new Result(status, requestId, commit, overflowClaim,
                    payoutMinorUnits, resultingBalanceMinorUnits,
                    replayed);
        }

        public boolean success() {
            return status.success();
        }
    }
}

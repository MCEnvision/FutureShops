package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.Config;
import com.enviouse.futureshops.catalog.ShopCatalog;
import com.enviouse.futureshops.catalog.offer.AcquireOfferOption;
import com.enviouse.futureshops.catalog.offer.OfferAction;
import com.enviouse.futureshops.catalog.offer.OfferItemComponent;
import com.enviouse.futureshops.catalog.offer.OfferLimitPolicy;
import com.enviouse.futureshops.catalog.offer.OfferSchedule;
import com.enviouse.futureshops.catalog.offer.SellOfferOption;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferListing;
import com.enviouse.futureshops.catalog.offer.ServerShopBundleSavings;
import com.enviouse.futureshops.money.PaymentSource;
import com.enviouse.futureshops.server.economy.BalanceManager;
import com.enviouse.futureshops.server.escrow.model.DimensionAwareShopReference;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopEscrowIntent;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopEscrowOrchestrator;
import com.enviouse.futureshops.server.escrow.stock.CatalogStockState;
import com.enviouse.futureshops.server.escrow.stock.CatalogStockStatus;
import com.enviouse.futureshops.server.escrow.stock.StockCommandResult;
import com.enviouse.futureshops.server.escrow.stock.StockKey;
import com.enviouse.futureshops.server.escrow.stock.StockMutationCommand;
import com.enviouse.futureshops.server.escrow.stock.StockMutationOutcome;
import com.enviouse.futureshops.server.escrow.stock.StockMutationType;
import com.enviouse.futureshops.server.escrow.stock.StockReservation;
import com.enviouse.futureshops.server.escrow.stock.StockReservationDirection;
import com.enviouse.futureshops.server.escrow.stock.StockReservationId;
import com.enviouse.futureshops.server.escrow.stock.StockReservationResolution;
import com.enviouse.futureshops.server.escrow.stock.StockReservationRequest;
import com.enviouse.futureshops.server.escrow.stock.StockReservationState;
import com.enviouse.futureshops.server.transaction.ServerShopOfferIntentFactory;
import com.enviouse.futureshops.server.transaction.NormalizedOfferTransactionEvents;
import com.enviouse.futureshops.server.transaction.ServerShopOfferPermissionPolicy;
import com.enviouse.futureshops.server.transaction.ServerShopOfferPricing;
import com.enviouse.futureshops.server.transaction.ServerShopOfferUsageSavedData;
import com.enviouse.futureshops.server.transaction.TransactionHistoryService;
import com.enviouse.futureshops.server.pricing.DynamicPricingEngine;
import com.enviouse.futureshops.server.security.ServerRequestAction;
import com.enviouse.futureshops.server.security.ServerRequestSecurityManager;
import com.enviouse.futureshops.server.session.ShopSessionManager;
import com.enviouse.futureshops.server.shop.AdminShopToggleSavedData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.concurrent.TimeUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ServerShopOfferService {
    private static final org.slf4j.Logger LOGGER =
            com.mojang.logging.LogUtils.getLogger();
    private static final long STOCK_DIAGNOSTIC_INTERVAL_NANOS =
            TimeUnit.SECONDS.toNanos(30L);
    private static final int MAXIMUM_STOCK_DIAGNOSTIC_KEYS = 512;
    private static final Map<String, Long> STOCK_DIAGNOSTIC_LOGS =
            new LinkedHashMap<>(MAXIMUM_STOCK_DIAGNOSTIC_KEYS, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(
                        Map.Entry<String, Long> eldest
                ) {
                    return size() > MAXIMUM_STOCK_DIAGNOSTIC_KEYS;
                }
            };

    private ServerShopOfferService() {
    }

    public static Result execute(
            ServerPlayer player,
            Request request
    ) {
        return executeInternal(player, request, true, 0L);
    }

    public static Result executeBulkLine(
            ServerPlayer player,
            Request request,
            long minimumPayoutMinorUnits
    ) {
        if (minimumPayoutMinorUnits < 1L) {
            return Result.failure(
                    Status.INVALID_REQUEST, request.requestId());
        }
        return executeInternal(
                player, request, false, minimumPayoutMinorUnits);
    }

    public static boolean canExecuteBulkLine(
            ServerPlayer player,
            Request request
    ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(request, "request");
        if (request.action() != OfferAction.SELL_TO_SHOP
                || !player.getUUID().equals(request.playerId())
                || player.getServer() == null
                || !AdminShopToggleSavedData.get(player.getServer())
                .isAdminShopEnabled()) {
            return false;
        }
        EscrowRuntimeService runtime = EscrowRuntimeManager.getOrNull();
        if (runtime == null || !runtime.isReady()) {
            return false;
        }
        try {
            return quote(player, request, runtime).failure() == null;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static Result executeInternal(
            ServerPlayer player,
            Request request,
            boolean acquireRequestGate,
            long minimumPayoutMinorUnits
    ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(request, "request");
        if (!player.getUUID().equals(request.playerId())) {
            return Result.failure(Status.CONFLICT, request.requestId());
        }
        if (player.getServer() == null
                || !AdminShopToggleSavedData.get(player.getServer())
                .isAdminShopEnabled()
                || ShopSessionManager.get(player.getUUID())
                .filter(session -> session.shopId().equals(
                        request.shopId())).isEmpty()) {
            return Result.failure(Status.NOT_AVAILABLE,
                    request.requestId());
        }
        EscrowRuntimeService runtime = EscrowRuntimeManager.getOrNull();
        if (runtime == null || !runtime.isReady()) {
            return Result.failure(Status.UNAVAILABLE, request.requestId());
        }
        try {
            if (acquireRequestGate
                    && !ServerRequestSecurityManager.tryAcquire(
                    player, ServerRequestAction.SERVER_SHOP_OFFER)
                    .allowed()) {
                return Result.failure(Status.UNAVAILABLE,
                        request.requestId());
            }
            ServerShopOfferCommitSavedData commits =
                    ServerShopOfferCommitSavedData.get(
                            player.getServer());
            Optional<ServerShopOfferReplayReceipt> durable =
                    ServerShopOfferReplayLedger.get(
                            player.getServer()).find(
                            request.requestId());
            if (durable.isPresent()) {
                ServerShopOfferReplayReceipt receipt =
                        durable.orElseThrow();
                if (!receipt.matches(request)) {
                    return Result.failure(Status.CONFLICT,
                            request.requestId());
                }
                return receipt.successful()
                        ? Result.archivedSuccess(
                        receipt.status(), request.requestId())
                        : Result.failure(
                        receipt.status(), request.requestId());
            }
            Optional<ServerShopOfferCommit> replay =
                    commits.find(request.requestId());
            if (replay.isPresent()) {
                ServerShopOfferCommit commit = replay.orElseThrow();
                if (!commitMatches(request, commit)) {
                    return Result.failure(Status.CONFLICT,
                            request.requestId());
                }
                Optional<ServerShopOfferReplayReceipt> archived =
                        commits.findArchived(request.requestId());
                if (archived.isPresent()) {
                    ServerShopOfferReplayReceipt receipt =
                            archived.orElseThrow();
                    return receipt.matches(request)
                            && receipt.matches(commit)
                            && mirrorReplayReceipt(
                            player, receipt)
                            ? Result.success(receipt.status(),
                            commit, true)
                            : Result.failure(Status.CONFLICT,
                            request.requestId());
                }
                Optional<ServerShopOfferPreparedSavedData.Entry>
                        preparedEntry =
                        ServerShopOfferPreparedSavedData.get(
                                player.getServer()).find(
                                request.requestId());
                if (preparedEntry.isEmpty()) {
                    Optional<ServerShopOfferReplayReceipt>
                            preparedArchive =
                            ServerShopOfferPreparedSavedData.get(
                                    player.getServer()).findArchived(
                                    request.requestId());
                    if (preparedArchive.isPresent()
                            && preparedArchive.orElseThrow()
                            .matches(request)
                            && preparedArchive.orElseThrow()
                            .matches(commit)) {
                        ServerShopOfferReplayLedger.get(
                                player.getServer()).record(
                                preparedArchive.orElseThrow());
                        return Result.success(
                                preparedArchive.orElseThrow().status(),
                                commit, true);
                    }
                    return Result.failureWithValue(
                            Status.RECOVERY_REQUIRED,
                            request.requestId(),
                            commit.valueCommit());
                }
                if (!preparedEntry.orElseThrow().matches(request)) {
                    return Result.failureWithValue(
                            Status.RECOVERY_REQUIRED,
                            request.requestId(),
                            commit.valueCommit());
                }
                ServerShopOfferPreparedSavedData.Entry entry =
                        preparedEntry.orElseThrow();
                OptionFacts facts = option(entry.listing(), request);
                if (facts == null) {
                    return Result.failureWithValue(
                            Status.RECOVERY_REQUIRED,
                            request.requestId(),
                            commit.valueCommit());
                }
                Quote storedQuote = new Quote(
                        entry.listing(), facts, null,
                        entry.quotedAt(), committedMoneyTotal(entry.intent()),
                        null);
                publishProjections(
                        player, request, storedQuote, commit);
                recordReplayReceipt(
                        player, entry, commit);
                return Result.success(commit.claimsPending()
                        ? Status.CLAIMS_PENDING : Status.SUCCESS,
                        commit, true);
            }
            ServerShopOfferTerminalReceiptSavedData terminalReceipts =
                    ServerShopOfferTerminalReceiptSavedData.get(
                            player.getServer());
            Optional<ServerShopOfferTerminalReceiptSavedData.Receipt>
                    terminal = terminalReceipts.find(
                    request.requestId());
            if (terminal.isPresent()) {
                ServerShopOfferTerminalReceiptSavedData.Receipt receipt =
                        terminal.orElseThrow();
                boolean matches = receipt.kind()
                        == ServerShopOfferTerminalReceiptSavedData.Kind.SINGLE
                        && receipt.requestFingerprint().equals(
                        request.fingerprint());
                if (!matches) {
                    return Result.failure(Status.CONFLICT,
                            request.requestId());
                }
                ServerShopOfferReplayLedger.get(
                        player.getServer()).record(
                        ServerShopOfferReplayReceipt.terminal(
                                request, receipt.status()));
                return Result.failure(receipt.status(),
                        request.requestId());
            }
            Optional<ServerShopOfferReplayReceipt> archived =
                    commits.findArchived(request.requestId());
            if (archived.isEmpty()) {
                archived = ServerShopOfferPreparedSavedData.get(
                        player.getServer()).findArchived(
                        request.requestId());
            }
            if (archived.isPresent()) {
                ServerShopOfferReplayReceipt receipt =
                        archived.orElseThrow();
                return receipt.matches(request)
                        && mirrorReplayReceipt(player, receipt)
                        ? Result.archivedSuccess(
                        receipt.status(), request.requestId())
                        : Result.failure(Status.CONFLICT,
                        request.requestId());
            }
            if (ServerShopOfferCartCommitSavedData.get(
                    player.getServer()).find(
                    request.requestId()).isPresent()
                    || ServerShopOfferCartCommitSavedData.get(
                    player.getServer()).findArchived(
                    request.requestId()).isPresent()
                    || ServerShopOfferCartPreparedSavedData.get(
                    player.getServer()).find(
                    request.requestId()).isPresent()
                    || ServerShopOfferCartPreparedSavedData.get(
                    player.getServer()).findArchived(
                    request.requestId()).isPresent()) {
                return Result.failure(
                        Status.CONFLICT, request.requestId());
            }
            ServerShopOfferPreparedSavedData preparedEntries =
                    ServerShopOfferPreparedSavedData.get(
                            player.getServer());
            Optional<ServerShopOfferPreparedSavedData.Entry> preparedReplay =
                    preparedEntries.find(request.requestId());
            if (preparedReplay.isPresent()) {
                ServerShopOfferPreparedSavedData.Entry entry =
                        preparedReplay.orElseThrow();
                if (!entry.matches(request)) {
                    return Result.failure(Status.CONFLICT,
                            request.requestId());
                }
                if (!ServerShopOfferReplayRetention
                        .ensureSingleCapacity(
                                player.getServer(),
                                request.requestId(),
                                request.playerId())) {
                    return Result.failure(Status.UNAVAILABLE,
                            request.requestId());
                }
                OptionFacts facts = option(entry.listing(), request);
                if (facts == null) {
                    return Result.failure(Status.QUARANTINED,
                            request.requestId());
                }
                Quote storedQuote = new Quote(
                        entry.listing(), facts, null,
                        entry.quotedAt(), committedMoneyTotal(entry.intent()),
                        null);
                ServerShopOfferIntentFactory.Prepared prepared =
                        new ServerShopOfferIntentFactory.Prepared(
                                entry.action(), entry.listingId(),
                                entry.optionId(), entry.offerRevision(),
                                entry.intent());
                return executeQuoted(player, request, storedQuote,
                        prepared, entry.stockReservation(), runtime,
                        false);
            }
            if (runtime.playerShopEscrowEntry(
                    request.requestId()).isPresent()) {
                return Result.failure(
                        Status.RECOVERY_REQUIRED,
                        request.requestId());
            }
            if (!ServerShopOfferReplayRetention.ensureSingleCapacity(
                    player.getServer(), request.requestId(),
                    request.playerId())) {
                return Result.failure(Status.UNAVAILABLE,
                        request.requestId());
            }
            Quote quote = quote(player, request, runtime);
            if (quote.failure() != null) {
                return failAcceptedRequest(
                        player, request, quote.failure());
            }
            NormalizedOfferTransactionEvents.Decision event =
                    firePreEvent(player, request, quote);
            if (event.status()
                    == NormalizedOfferTransactionEvents.Status.CANCELLED) {
                return failAcceptedRequest(
                        player, request, Status.CANCELLED_BY_EVENT);
            }
            if (event.status()
                    != NormalizedOfferTransactionEvents.Status.ACCEPTED) {
                return failAcceptedRequest(
                        player, request, Status.INVALID_REQUEST);
            }
            quote = new Quote(
                    quote.listing(), quote.option(), quote.stock(),
                    quote.quotedAt(),
                    event.authorizedMoneyMinorUnits(), null);
            if (request.action() == OfferAction.SELL_TO_SHOP
                    && minimumPayoutMinorUnits > 0L
                    && quote.moneyTotalMinorUnits()
                    < minimumPayoutMinorUnits) {
                return failAcceptedRequest(
                        player, request, Status.STALE_REVISION);
            }
            ServerShopOfferIntentFactory.Prepared prepared =
                    prepareIntent(player, request, quote);
            StockMutationCommand.ReserveBatch reserve =
                    stockReservation(request, quote);
            preparedEntries.prepare(
                    new ServerShopOfferPreparedSavedData.Entry(
                            request.requestId(), request.playerId(),
                            request.shopId(), request.listingId(),
                            request.optionId(), request.action(),
                            request.quantity(),
                            quote.listing().revision(),
                            request.paymentSource(), quote.quotedAt(),
                            quote.listing(), prepared.intent(), reserve));
            return executeQuoted(player, request, quote,
                    prepared, reserve, runtime, false);
        } catch (ArithmeticException | IllegalArgumentException exception) {
            return failAcceptedRequest(
                    player, request, Status.INVALID_REQUEST);
        } catch (RuntimeException exception) {
            return recoverResult(request, runtime, exception);
        }
    }

    public static Result recoverPersisted(
            ServerPlayer player,
            UUID requestId
    ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(requestId, "requestId");
        if (player.getServer() == null) {
            return Result.failure(Status.UNAVAILABLE, requestId);
        }
        EscrowRuntimeService runtime = EscrowRuntimeManager.getOrNull();
        if (runtime == null || !runtime.isReady()) {
            return Result.failure(Status.UNAVAILABLE, requestId);
        }
        ServerShopOfferPreparedSavedData preparedEntries =
                ServerShopOfferPreparedSavedData.get(
                        player.getServer());
        Optional<ServerShopOfferPreparedSavedData.Entry> stored =
                preparedEntries.find(requestId);
        if (stored.isEmpty()
                || !stored.orElseThrow().playerId().equals(
                player.getUUID())) {
            return Result.failure(Status.RECOVERY_REQUIRED, requestId);
        }
        ServerShopOfferPreparedSavedData.Entry entry =
                stored.orElseThrow();
        Request request = requestFrom(entry);
        try {
            Optional<ServerShopOfferReplayReceipt> durable =
                    ServerShopOfferReplayLedger.get(
                            player.getServer()).find(requestId);
            if (durable.isPresent()) {
                ServerShopOfferReplayReceipt receipt =
                        durable.orElseThrow();
                if (!receipt.matches(request)
                        || !receipt.matches(entry)) {
                    return Result.failure(Status.CONFLICT, requestId);
                }
                preparedEntries.remove(requestId);
                return receipt.successful()
                        ? Result.archivedSuccess(
                        receipt.status(), requestId)
                        : Result.failure(receipt.status(), requestId);
            }
            Optional<ServerShopOfferTerminalReceiptSavedData.Receipt>
                    terminal =
                    ServerShopOfferTerminalReceiptSavedData.get(
                            player.getServer()).find(requestId);
            if (terminal.isPresent()) {
                ServerShopOfferTerminalReceiptSavedData.Receipt receipt =
                        terminal.orElseThrow();
                if (receipt.kind()
                        != ServerShopOfferTerminalReceiptSavedData.Kind.SINGLE
                        || !receipt.requestFingerprint().equals(
                        request.fingerprint())) {
                    return Result.failure(Status.CONFLICT, requestId);
                }
                ServerShopOfferReplayLedger.get(
                        player.getServer()).record(
                        ServerShopOfferReplayReceipt.terminal(
                                request, receipt.status()));
                preparedEntries.remove(requestId);
                return Result.failure(receipt.status(), requestId);
            }
            if (!ServerShopOfferReplayRetention.ensureSingleCapacity(
                    player.getServer(), requestId, player.getUUID())) {
                return Result.failure(Status.UNAVAILABLE, requestId);
            }
            ServerShopOfferCommitSavedData commits =
                    ServerShopOfferCommitSavedData.get(
                            player.getServer());
            Optional<ServerShopOfferReplayReceipt> archived =
                    commits.findArchived(requestId);
            if (archived.isPresent()) {
                ServerShopOfferReplayReceipt receipt =
                        archived.orElseThrow();
                if (!receipt.matches(request)
                        || !receipt.matches(entry)
                        || !mirrorReplayReceipt(player, receipt)) {
                    return Result.failure(Status.CONFLICT, requestId);
                }
                return Result.archivedSuccess(
                        receipt.status(), requestId);
            }
            Optional<ServerShopOfferCommit> completed =
                    commits.find(requestId);
            OptionFacts facts = option(entry.listing(), request);
            if (facts == null) {
                return Result.failure(Status.QUARANTINED, requestId);
            }
            Quote storedQuote = new Quote(
                    entry.listing(), facts, null,
                    entry.quotedAt(), committedMoneyTotal(entry.intent()),
                    null);
            if (completed.isPresent()) {
                ServerShopOfferCommit commit =
                        completed.orElseThrow();
                if (!commitMatches(request, commit)) {
                    return Result.failure(Status.CONFLICT, requestId);
                }
                publishProjections(
                        player, request, storedQuote, commit);
                recordReplayReceipt(player, entry, commit);
                return Result.success(
                        commit.claimsPending()
                                ? Status.CLAIMS_PENDING
                                : Status.SUCCESS,
                        commit, true);
            }
            if (!preparedEntries.find(requestId)
                    .filter(entry::equals).isPresent()) {
                return Result.failure(
                        Status.RECOVERY_REQUIRED, requestId);
            }
            ServerShopOfferIntentFactory.Prepared prepared =
                    new ServerShopOfferIntentFactory.Prepared(
                            entry.action(), entry.listingId(),
                            entry.optionId(), entry.offerRevision(),
                            entry.intent());
            return executeQuoted(
                    player, request, storedQuote, prepared,
                    entry.stockReservation(), runtime, true);
        } catch (ArithmeticException | IllegalArgumentException exception) {
            return Result.failure(Status.INVALID_REQUEST, requestId);
        } catch (RuntimeException exception) {
            return recoverResult(request, runtime, exception);
        }
    }

    private static Request requestFrom(
            ServerShopOfferPreparedSavedData.Entry entry
    ) {
        return new Request(
                entry.requestId(), entry.playerId(), entry.shopId(),
                entry.listingId(), entry.optionId(), entry.action(),
                entry.quantity(), entry.offerRevision(),
                entry.paymentSource(), 0);
    }

    private static Result executeQuoted(
            ServerPlayer player,
            Request request,
            Quote quote,
            ServerShopOfferIntentFactory.Prepared prepared,
            StockMutationCommand.ReserveBatch reserve,
            EscrowRuntimeService runtime,
            boolean trustedRecovery
    ) {
        if (request.action() == OfferAction.SELL_TO_SHOP
                && !ServerShopOfferUsageSavedData.get(
                player.getServer()).reserveCapacity(
                request.requestId(), request.shopId(),
                request.listingId(), request.optionId(),
                request.quantity(), quote.option().capacity(),
                quote.quotedAt().getEpochSecond())) {
            recordTerminal(player, request, Status.OUT_OF_STOCK);
            return Result.failure(
                    Status.OUT_OF_STOCK, request.requestId());
        }
        StockCommandResult reserved;
        try {
            reserved = runtime.commitStockMutation(reserve);
        } catch (RuntimeException exception) {
            return recoverResult(request, runtime, exception);
        }
        if (reserved.receipt().outcome()
                == StockMutationOutcome.INSUFFICIENT_STOCK) {
            releaseCapacity(player, request, quote);
            recordTerminal(player, request, Status.OUT_OF_STOCK);
            return Result.failure(Status.OUT_OF_STOCK,
                    request.requestId());
        }
        if (reserved.receipt().outcome() != StockMutationOutcome.APPLIED
                && reserved.receipt().outcome()
                != StockMutationOutcome.UNCHANGED
                || !reservationMatches(request, quote,
                runtime.stockReservations(request.requestId()))
                && !reservationCommitted(request, quote,
                runtime.stockReservations(request.requestId()))) {
            return Result.failure(Status.RECOVERY_REQUIRED,
                    request.requestId());
        }
        PlayerShopEscrowOrchestrator.Result valueResult =
                PlayerShopLiveEscrowService.execute(
                        player, prepared.intent(), request.responseToken(),
                        new OfferStorageAccess(
                                player, request, quote, runtime,
                                trustedRecovery));
        if (preCommit(valueResult)) {
            if (valueResult.status()
                    == PlayerShopEscrowOrchestrator.Status.REJECTED) {
                if (!releaseStock(request, quote, runtime)) {
                    return Result.failure(Status.RECOVERY_REQUIRED,
                            request.requestId());
                }
                releaseCapacity(player, request, quote);
                recordTerminal(player, request, Status.REJECTED);
                return Result.failure(Status.REJECTED,
                        request.requestId());
            }
            if (valueResult.status()
                    == PlayerShopEscrowOrchestrator.Status.CONFLICT) {
                return Result.failure(Status.CONFLICT,
                        request.requestId());
            }
            return Result.failure(valueResult.status()
                    == PlayerShopEscrowOrchestrator.Status.QUARANTINED
                    ? Status.QUARANTINED : Status.RECOVERY_REQUIRED,
                    request.requestId());
        }
        if (claimsPending(valueResult)
                && valueResult.status()
                != PlayerShopEscrowOrchestrator.Status
                .COMMITTED_WITH_PENDING_DELIVERY) {
            LOGGER.error(
                    "Server shop offer value committed with incomplete claim delivery for request {} with status {}, code {}, detail {}",
                    request.requestId(), valueResult.status(),
                    valueResult.code(), valueResult.detail());
        }
        StockMutationCommand.ResolveBatch stockCommit =
                stockResolution(request, quote,
                        StockMutationType.COMMIT_BATCH,
                        resolutionTime(quote,
                                StockMutationType.COMMIT_BATCH));
        try {
            StockCommandResult committed =
                    runtime.commitStockMutation(stockCommit);
            if (committed.receipt().outcome()
                    != StockMutationOutcome.APPLIED
                    && committed.receipt().outcome()
                    != StockMutationOutcome.UNCHANGED
                    || !reservationCommitted(request, quote,
                    runtime.stockReservations(request.requestId()))) {
                return Result.failureWithValue(
                        Status.RECOVERY_REQUIRED, request.requestId(),
                        valueResult.commit());
            }
        } catch (RuntimeException exception) {
            return Result.failureWithValue(
                    Status.RECOVERY_REQUIRED, request.requestId(),
                    valueResult.commit());
        }
        ServerShopOfferCommit commit = ServerShopOfferCommit.create(
                request.requestId(), request.playerId(), request.shopId(),
                request.listingId(), request.optionId(), request.action(),
                request.quantity(), quote.listing().revision(),
                request.paymentSource(), quote.quotedAt(),
                claimsPending(valueResult),
                valueResult.commit(), reserve, stockCommit,
                savingsSnapshot(player, request, quote));
        try {
            ServerShopOfferCommitSavedData.get(
                    player.getServer()).commit(commit);
            publishProjections(player, request, quote, commit);
            ServerShopOfferPreparedSavedData.Entry entry =
                    ServerShopOfferPreparedSavedData.get(
                            player.getServer()).find(
                            request.requestId()).orElseThrow();
            recordReplayReceipt(player, entry, commit);
        } catch (RuntimeException exception) {
            return Result.failureWithValue(
                    Status.RECOVERY_REQUIRED, request.requestId(),
                    valueResult.commit());
        }
        firePostEvent(player, request, quote, commit);
        Status status = claimsPending(valueResult)
                ? Status.CLAIMS_PENDING : Status.SUCCESS;
        return Result.success(status, commit,
                valueResult.status()
                        == PlayerShopEscrowOrchestrator.Status.REPLAYED);
    }

    static boolean preCommit(
            PlayerShopEscrowOrchestrator.Result value
    ) {
        return value.commit() == null;
    }

    static boolean claimsPending(
            PlayerShopEscrowOrchestrator.Result value
    ) {
        return switch (value.status()) {
            case COMMITTED_WITH_PENDING_DELIVERY,
                    RECOVERY_REQUIRED, QUARANTINED -> value.commit() != null;
            default -> false;
        };
    }

    private static void releaseCapacity(
            ServerPlayer player,
            Request request,
            Quote quote
    ) {
        if (request.action() != OfferAction.SELL_TO_SHOP) {
            return;
        }
        ServerShopOfferUsageSavedData.get(
                player.getServer()).releaseCapacity(
                request.requestId(), request.shopId(),
                request.listingId(), request.optionId(),
                request.quantity(), quote.option().capacity());
    }

    private static void recordTerminal(
            ServerPlayer player,
            Request request,
            Status status
    ) {
        ServerShopOfferReplayLedger.get(
                player.getServer()).record(
                ServerShopOfferReplayReceipt.terminal(
                        request, status));
        ServerShopOfferPreparedSavedData.get(
                player.getServer()).remove(request.requestId());
    }

    private static Result failAcceptedRequest(
            ServerPlayer player,
            Request request,
            Status status
    ) {
        if (ServerShopOfferReplayReceipt
                .isDurableTerminalFailure(status)) {
            recordTerminal(player, request, status);
        }
        return Result.failure(status, request.requestId());
    }

    private static void recordReplayReceipt(
            ServerPlayer player,
            ServerShopOfferPreparedSavedData.Entry prepared,
            ServerShopOfferCommit commit
    ) {
        ServerShopOfferReplayReceipt receipt =
                ServerShopOfferReplayReceipt.single(
                        prepared, commit);
        ServerShopOfferReplayLedger.get(
                player.getServer()).record(receipt);
    }

    private static boolean mirrorReplayReceipt(
            ServerPlayer player,
            ServerShopOfferReplayReceipt receipt
    ) {
        Optional<ServerShopOfferReplayReceipt> existing =
                ServerShopOfferReplayLedger.get(
                        player.getServer()).find(
                        receipt.requestId());
        if (existing.isPresent()) {
            return existing.orElseThrow().equals(receipt);
        }
        ServerShopOfferReplayLedger.get(
                player.getServer()).record(receipt);
        return true;
    }

    private static void publishProjections(
            ServerPlayer player,
            Request request,
            Quote quote,
            ServerShopOfferCommit commit
    ) {
        ServerShopOfferUsageSavedData usage =
                ServerShopOfferUsageSavedData.get(player.getServer());
        usage.record(
                request.requestId(), request.playerId(),
                request.shopId(), request.listingId(),
                request.optionId(), request.action(),
                request.quantity(), quote.listing().limits(),
                quote.option().limits(),
                commit.quotedAt().getEpochSecond());
        if (request.action() == OfferAction.SELL_TO_SHOP) {
            usage.recordCapacity(
                    request.requestId(), request.shopId(),
                    request.listingId(), request.optionId(),
                    request.quantity(), quote.option().capacity(),
                    commit.quotedAt().getEpochSecond());
        }
        if (ShopCatalog.get(request.shopId())
                .map(definition -> definition.schemaVersion() == 1)
                .orElse(false)) {
            String receipt = "server.shop.offer."
                    + request.requestId() + "." + request.listingId()
                    + "." + request.optionId();
            if (request.action() == OfferAction.ACQUIRE_FROM_SHOP
                    && quote.option().acquire().moneyCostPresent()) {
                DynamicPricingEngine.recordBuyOnce(
                        player.getServer(), receipt, request.shopId(),
                        request.listingId(), request.quantity());
            } else if (request.action() == OfferAction.SELL_TO_SHOP) {
                DynamicPricingEngine.recordSellOnce(
                        player.getServer(), receipt, request.shopId(),
                        request.listingId(), request.quantity());
            }
        }
        recordHistory(player, request, quote, commit);
    }

    private static NormalizedOfferTransactionEvents.Decision firePreEvent(
            ServerPlayer player,
            Request request,
            Quote quote
    ) {
        if (!Config.eventsTransactionEnabled) {
            return new NormalizedOfferTransactionEvents.Decision(
                    NormalizedOfferTransactionEvents.Status.ACCEPTED,
                    quote.moneyTotalMinorUnits());
        }
        return request.action() == OfferAction.ACQUIRE_FROM_SHOP
                ? NormalizedOfferTransactionEvents.fireAcquirePre(
                player, request.shopId(), quote.listing(),
                quote.option().acquire(), request.quantity(),
                quote.moneyTotalMinorUnits())
                : NormalizedOfferTransactionEvents.fireSellPre(
                player, request.shopId(), quote.listing(),
                quote.option().sell(), request.quantity(),
                quote.moneyTotalMinorUnits());
    }

    private static void firePostEvent(
            ServerPlayer player,
            Request request,
            Quote quote,
            ServerShopOfferCommit commit
    ) {
        if (!Config.eventsTransactionEnabled) {
            return;
        }
        try {
            long balance = BalanceManager.getProvider().getBalance(
                    request.playerId());
            if (request.action()
                    == OfferAction.ACQUIRE_FROM_SHOP) {
                NormalizedOfferTransactionEvents.fireAcquirePost(
                        player, request.shopId(), quote.listing(),
                        quote.option().acquire(), request.quantity(),
                        commit.moneyDebitMinorUnits().orElse(0L),
                        balance);
            } else {
                NormalizedOfferTransactionEvents.fireSellPost(
                        player, request.shopId(), quote.listing(),
                        quote.option().sell(), request.quantity(),
                        commit.moneyPayoutMinorUnits().orElseThrow(),
                        balance);
            }
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Server shop offer post event failed for request {}",
                    request.requestId(), exception);
        }
    }

    private static boolean commitMatches(
            Request request,
            ServerShopOfferCommit commit
    ) {
        return commit.requestId().equals(request.requestId())
                && commit.playerId().equals(request.playerId())
                && commit.shopId().equals(request.shopId())
                && commit.listingId().equals(request.listingId())
                && commit.optionId().equals(request.optionId())
                && commit.action() == request.action()
                && commit.quantity() == request.quantity()
                && commit.offerRevision()
                == request.expectedOfferRevision()
                && commit.paymentSource()
                .equals(request.paymentSource());
    }

    private static Quote quote(
            ServerPlayer player,
            Request request,
            EscrowRuntimeService runtime
    ) {
        ServerShopOfferListing listing = ShopCatalog.getOffer(
                request.shopId(), request.listingId()).orElse(null);
        if (listing == null || !listing.active()) {
            return Quote.failure(Status.NOT_FOUND);
        }
        if (listing.revision() != request.expectedOfferRevision()) {
            return Quote.failure(Status.STALE_REVISION);
        }
        Instant quotedAt = Instant.now();
        long now = quotedAt.getEpochSecond();
        if (listing.expiresAtEpoch() > 0L
                && now >= listing.expiresAtEpoch()
                || !listing.schedule().activeAt(now)
                || !ServerShopOfferPermissionPolicy.allowed(
                player, listing.permissionNode())) {
            return Quote.failure(Status.NOT_AVAILABLE);
        }
        OptionFacts option = option(listing, request);
        if (option == null
                || !option.schedule().activeAt(now)
                || !ServerShopOfferPermissionPolicy.allowed(
                player, option.permissionNode())
                || request.quantity()
                > Math.min(listing.limits().maximumPerRequest(),
                option.limits().maximumPerRequest())) {
            return Quote.failure(Status.NOT_AVAILABLE);
        }
        if (option.moneyRequired()
                != request.paymentSource().isPresent()) {
            return Quote.failure(Status.INVALID_REQUEST);
        }
        long moneyTotal = quotedMoneyTotal(
                player, request, listing, option);
        if (option.moneyRequired() && moneyTotal <= 0L) {
            return Quote.failure(Status.NOT_AVAILABLE);
        }
        ServerShopOfferUsageSavedData usageData =
                ServerShopOfferUsageSavedData.get(player.getServer());
        ServerShopOfferUsageSavedData.Decision usage = usageData.check(
                        request.playerId(), request.shopId(),
                        request.listingId(), request.optionId(),
                        request.action(), request.quantity(),
                        listing.limits(), option.limits(), now);
        if (usage != ServerShopOfferUsageSavedData.Decision.ALLOWED) {
            if (usage == ServerShopOfferUsageSavedData.Decision
                    .RECOVERY_REQUIRED) {
                return Quote.failure(Status.UNAVAILABLE);
            }
            return Quote.failure(usage
                    == ServerShopOfferUsageSavedData.Decision.COOLDOWN
                    ? Status.COOLDOWN : Status.LIMIT_REACHED);
        }
        if (request.action() == OfferAction.SELL_TO_SHOP) {
            ServerShopOfferUsageSavedData.Decision capacity =
                    usageData.checkCapacity(
                request.shopId(), request.listingId(),
                request.optionId(), request.quantity(),
                option.capacity(), now);
            if (capacity
                    == ServerShopOfferUsageSavedData.Decision
                    .RECOVERY_REQUIRED) {
                return Quote.failure(Status.UNAVAILABLE);
            }
            if (capacity
                    != ServerShopOfferUsageSavedData.Decision.ALLOWED) {
                return Quote.failure(Status.OUT_OF_STOCK);
            }
        }
        CatalogStockState stock = runtime.stockListing(
                new StockKey(request.shopId(),
                        request.listingId())).orElse(null);
        if (stock == null) {
            if (shouldLogStockDiagnostic("missing", request)) {
                LOGGER.warn(
                        "Server shop offer request {} has no stock listing for shop {} listing {}.",
                        request.requestId(), request.shopId(),
                        request.listingId());
            }
            return Quote.failure(Status.UNAVAILABLE);
        }
        if (stock.status() != CatalogStockStatus.ACTIVE) {
            if (shouldLogStockDiagnostic("status", request)) {
                LOGGER.warn(
                        "Server shop offer request {} found stock listing {} in status {}.",
                        request.requestId(), request.listingId(), stock.status());
            }
            return Quote.failure(Status.UNAVAILABLE);
        }
        if (request.action() == OfferAction.ACQUIRE_FROM_SHOP
                && !stock.unlimited()
                && stock.availableQuantity()
                < stockQuantity(request, option)) {
            if (shouldLogStockDiagnostic("exhausted", request)) {
                LOGGER.debug(
                        "Server shop offer request {} is out of stock for shop {} listing {}. Available {}, requested {}.",
                        request.requestId(), request.shopId(),
                        request.listingId(), stock.availableQuantity(),
                        stockQuantity(request, option));
            }
            return Quote.failure(Status.OUT_OF_STOCK);
        }
        if (request.action() == OfferAction.SELL_TO_SHOP
                && option.capacity() > 0L
                && !stock.unlimited()
                && Math.addExact(stock.availableQuantity(),
                request.quantity()) > option.capacity()) {
            return Quote.failure(Status.OUT_OF_STOCK);
        }
        return new Quote(listing, option, stock, quotedAt,
                moneyTotal, null);
    }

    private static boolean shouldLogStockDiagnostic(
            String diagnostic,
            Request request
    ) {
        String key = diagnostic + "\u0000" + request.shopId()
                + "\u0000" + request.listingId();
        long now = System.nanoTime();
        synchronized (STOCK_DIAGNOSTIC_LOGS) {
            Long previous = STOCK_DIAGNOSTIC_LOGS.get(key);
            if (previous != null
                    && now - previous < STOCK_DIAGNOSTIC_INTERVAL_NANOS) {
                return false;
            }
            STOCK_DIAGNOSTIC_LOGS.put(key, now);
            return true;
        }
    }

    private static void recordHistory(
            ServerPlayer player,
            Request request,
            Quote quote,
            ServerShopOfferCommit commit
    ) {
        if (request.action() == OfferAction.ACQUIRE_FROM_SHOP) {
            AcquireOfferOption option = quote.option().acquire();
            long total = commit.moneyDebitMinorUnits().orElse(0L);
            String type = option.free() ? "FREE"
                    : option.compound() ? "MONEY_AND_BARTER"
                    : option.hasItemCosts() ? "BARTER" : "BUY";
            List<TransactionHistoryService.ServerOfferComponent>
                    components = new java.util.ArrayList<>();
            components.addAll(historyComponents(
                    quote.listing().outputs(),
                    option.outputMultiplier(),
                    TransactionHistoryService.ComponentRole.OUTPUT));
            components.addAll(historyComponents(
                    option.itemCosts(), 1,
                    TransactionHistoryService.ComponentRole.INPUT));
            TransactionHistoryService.recordServerOfferComponents(
                    player.getServer(), request.playerId(),
                    request.shopId(), request.requestId(),
                    request.listingId(), type, request.quantity(),
                    total, request.optionId(), components,
                    commit.bundleSavings(),
                    commit.quotedAt());
            return;
        }
        SellOfferOption option = quote.option().sell();
        TransactionHistoryService.recordServerOfferComponents(
                player.getServer(), request.playerId(),
                request.shopId(), request.requestId(),
                request.listingId(), "SELL", request.quantity(),
                commit.moneyPayoutMinorUnits().orElseThrow(),
                request.optionId(), historyComponents(
                        option.itemInputs(), 1,
                        TransactionHistoryService.ComponentRole.INPUT),
                Optional.empty(),
                commit.quotedAt());
    }

    private static List<TransactionHistoryService.ServerOfferComponent>
    historyComponents(
            List<OfferItemComponent> components,
            int multiplier,
            TransactionHistoryService.ComponentRole role
    ) {
        return components.stream().map(component ->
                new TransactionHistoryService.ServerOfferComponent(
                        role, component.componentId(),
                        component.itemId(),
                        Math.multiplyExact(
                                component.count(), multiplier),
                        component.exactNbt())).toList();
    }

    private static Optional<ServerShopBundleSavings.Snapshot>
    savingsSnapshot(
            ServerPlayer player,
            Request request,
            Quote quote
    ) {
        if (request.action() != OfferAction.ACQUIRE_FROM_SHOP) {
            return Optional.empty();
        }
        Map<String, ServerShopOfferListing> listings = ShopCatalog.get(
                        request.shopId())
                .stream()
                .flatMap(definition -> definition.offers().stream())
                .collect(Collectors.toUnmodifiableMap(
                        ServerShopOfferListing::listingId,
                        Function.identity()));
        return ServerShopBundleSavings.calculate(
                quote.listing(), quote.option().acquire(),
                request.quantity(), listings, quote.quotedAt(),
                permission -> ServerShopOfferPermissionPolicy.allowed(
                        player, permission),
                (listing, option, quantity) -> {
                    if (listing.listingId().equals(
                            quote.listing().listingId())
                            && option.optionId().equals(
                            quote.option().acquire().optionId())
                            && quantity == request.quantity()) {
                        return quote.moneyTotalMinorUnits();
                    }
                    return ServerShopOfferPricing.moneyTotal(
                            player.getServer(), request.shopId(),
                            listing, option, quantity);
                });
    }

    private static OptionFacts option(
            ServerShopOfferListing listing,
            Request request
    ) {
        if (request.action() == OfferAction.ACQUIRE_FROM_SHOP) {
            AcquireOfferOption option = listing.acquireOptions().stream()
                    .filter(value -> value.optionId().equals(
                            request.optionId())).findFirst().orElse(null);
            return option == null ? null : new OptionFacts(
                    option, null, option.moneyCostPresent(), 0L,
                    option.limits(), option.schedule(),
                    option.permissionNode());
        }
        SellOfferOption option = listing.sellOptions().stream()
                .filter(value -> value.optionId().equals(
                        request.optionId())).findFirst().orElse(null);
        return option == null ? null : new OptionFacts(
                null, option, false, option.capacity(),
                option.limits(), option.schedule(),
                option.permissionNode());
    }

    private static ServerShopOfferIntentFactory.Prepared prepareIntent(
            ServerPlayer player,
            Request request,
            Quote quote
    ) {
        DimensionAwareShopReference reference =
                new DimensionAwareShopReference(
                        request.shopId(),
                        player.serverLevel().dimension().location()
                                .toString(),
                        player.blockPosition().getX(),
                        player.blockPosition().getY(),
                        player.blockPosition().getZ());
        if (request.action() == OfferAction.ACQUIRE_FROM_SHOP) {
            return ServerShopOfferIntentFactory.acquire(
                    request.requestId(), request.playerId(),
                    request.shopId(), quote.listing(),
                    quote.option().acquire(), request.quantity(),
                    quote.moneyTotalMinorUnits(),
                    request.paymentSource().orElse(null),
                    BalanceManager.getProvider().getBalance(
                            request.playerId()),
                    reference, quote.quotedAt());
        }
        return ServerShopOfferIntentFactory.sell(
                request.requestId(), request.playerId(), request.shopId(),
                quote.listing(), quote.option().sell(), request.quantity(),
                quote.moneyTotalMinorUnits(),
                reference, quote.quotedAt());
    }

    private static StockMutationCommand.ReserveBatch stockReservation(
            Request request,
            Quote quote
    ) {
        StockReservationDirection direction =
                request.action() == OfferAction.ACQUIRE_FROM_SHOP
                        ? StockReservationDirection.OUTBOUND
                        : StockReservationDirection.INBOUND;
        return new StockMutationCommand.ReserveBatch(
                ServerShopOfferCommit.stockReserveRequestId(
                        request.requestId()),
                request.requestId(), List.of(new StockReservationRequest(
                new StockKey(request.shopId(), request.listingId()),
                direction, stockQuantity(request, quote.option()),
                quote.stock().revision())),
                quote.quotedAt());
    }

    private static StockMutationCommand.ResolveBatch stockResolution(
            Request request,
            Quote quote,
            StockMutationType operation,
            Instant appliedAt
    ) {
        StockReservationDirection direction =
                request.action() == OfferAction.ACQUIRE_FROM_SHOP
                        ? StockReservationDirection.OUTBOUND
                        : StockReservationDirection.INBOUND;
        UUID commandId = operation == StockMutationType.COMMIT_BATCH
                ? ServerShopOfferCommit.stockCommitRequestId(
                request.requestId())
                : ServerShopOfferCommit.stockReleaseRequestId(
                request.requestId());
        return new StockMutationCommand.ResolveBatch(commandId, operation,
                request.requestId(),
                List.of(new StockReservationResolution(
                        StockReservationId.forTransaction(
                                request.requestId(),
                                new StockKey(request.shopId(),
                                        request.listingId()),
                                direction),
                        0L)), appliedAt);
    }

    private static boolean releaseStock(
            Request request,
            Quote quote,
            EscrowRuntimeService runtime
    ) {
        try {
            runtime.commitStockMutation(stockResolution(
                    request, quote, StockMutationType.RELEASE_BATCH,
                    resolutionTime(quote,
                            StockMutationType.RELEASE_BATCH)));
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static Instant resolutionTime(
            Quote quote,
            StockMutationType operation
    ) {
        return quote.quotedAt().plusNanos(
                operation == StockMutationType.COMMIT_BATCH ? 1L : 2L);
    }

    private static boolean reservationMatches(
            Request request,
            Quote quote,
            List<StockReservation> reservations
    ) {
        return reservationStateMatches(request, quote, reservations,
                StockReservationState.HELD);
    }

    private static boolean reservationCommitted(
            Request request,
            Quote quote,
            List<StockReservation> reservations
    ) {
        return reservationStateMatches(request, quote, reservations,
                StockReservationState.COMMITTED);
    }

    private static boolean reservationStateMatches(
            Request request,
            Quote quote,
            List<StockReservation> reservations,
            StockReservationState state
    ) {
        StockReservationDirection direction =
                request.action() == OfferAction.ACQUIRE_FROM_SHOP
                        ? StockReservationDirection.OUTBOUND
                        : StockReservationDirection.INBOUND;
        return reservations.size() == 1
                && reservations.get(0).transactionId()
                .equals(request.requestId())
                && reservations.get(0).stockKey().equals(
                new StockKey(request.shopId(), request.listingId()))
                && reservations.get(0).direction() == direction
                && reservations.get(0).quantity()
                == stockQuantity(request, quote.option())
                && reservations.get(0).state() == state;
    }

    private static int stockQuantity(
            Request request,
            OptionFacts option
    ) {
        return request.action() == OfferAction.ACQUIRE_FROM_SHOP
                ? Math.multiplyExact(request.quantity(),
                option.acquire().outputMultiplier())
                : request.quantity();
    }

    private static long quotedMoneyTotal(
            ServerPlayer player,
            Request request,
            ServerShopOfferListing listing,
            OptionFacts option
    ) {
        if (request.action() == OfferAction.SELL_TO_SHOP) {
            return sellMoneyTotal(
                    option.sell().moneyPayoutMinorUnits(),
                    request.quantity());
        }
        if (!option.moneyRequired()) {
            return 0L;
        }
        return ServerShopOfferPricing.moneyTotal(
                player.getServer(), request.shopId(), listing,
                option.acquire(), request.quantity());
    }

    static long sellMoneyTotal(long unitPayoutMinorUnits, int quantity) {
        return Math.multiplyExact(unitPayoutMinorUnits, (long) quantity);
    }

    private static long committedMoneyTotal(
            PlayerShopEscrowIntent intent
    ) {
        return intent.moneyTransfers().isEmpty()
                ? 0L
                : intent.moneyTransfers().get(0).amountMinorUnits();
    }

    private static Result recoverResult(
            Request request,
            EscrowRuntimeService runtime,
            RuntimeException cause
    ) {
        LOGGER.error(
                "Server shop offer entered recovery for request {}",
                request.requestId(), cause);
        try {
            Optional<com.enviouse.futureshops.server.escrow.playershop
                    .PlayerShopAtomicCommit> commit =
                    runtime.playerShopEscrowEntry(request.requestId())
                            .map(value -> value.snapshot().commit());
            if (commit.isPresent()) {
                return Result.failureWithValue(
                        Status.RECOVERY_REQUIRED, request.requestId(),
                        commit.orElseThrow());
            }
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Server shop offer recovery inspection failed for request {}",
                    request.requestId(), exception);
        }
        return Result.failure(Status.RECOVERY_REQUIRED,
                request.requestId());
    }

    public record Request(
            UUID requestId,
            UUID playerId,
            String shopId,
            String listingId,
            String optionId,
            OfferAction action,
            int quantity,
            long expectedOfferRevision,
            Optional<PaymentSource> paymentSource,
            int responseToken
    ) {
        public Request {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(playerId, "playerId");
            shopId = Objects.requireNonNull(shopId, "shopId").strip();
            listingId = Objects.requireNonNull(
                    listingId, "listingId").strip();
            optionId = Objects.requireNonNull(
                    optionId, "optionId").strip();
            Objects.requireNonNull(action, "action");
            paymentSource = Objects.requireNonNull(
                    paymentSource, "paymentSource");
            if (requestId.equals(new UUID(0L, 0L))
                    || playerId.equals(new UUID(0L, 0L))
                    || shopId.isEmpty() || shopId.length() > 160
                    || listingId.isEmpty() || listingId.length() > 160
                    || optionId.isEmpty() || optionId.length() > 160
                    || quantity <= 0 || quantity > 2304
                    || expectedOfferRevision < 0L
                    || expectedOfferRevision
                    > ServerShopOfferCommit.MAX_REVISION
                    || responseToken < 0 || responseToken > 2303) {
                throw new IllegalArgumentException(
                        "Server shop offer request is invalid");
            }
        }

        public String fingerprint() {
            String material =
                    "futureshops server shop offer request v1"
                            + '\u0000' + playerId
                            + '\u0000' + shopId
                            + '\u0000' + listingId
                            + '\u0000' + optionId
                            + '\u0000' + action
                            + '\u0000' + quantity
                            + '\u0000' + expectedOfferRevision
                            + '\u0000' + paymentSource
                            .map(Enum::name).orElse("NONE");
            try {
                return java.util.HexFormat.of().formatHex(
                        java.security.MessageDigest.getInstance(
                                "SHA-256").digest(
                                material.getBytes(
                                        java.nio.charset.StandardCharsets
                                                .UTF_8)));
            } catch (java.security.NoSuchAlgorithmException exception) {
                throw new IllegalStateException(
                        "SHA 256 is unavailable", exception);
            }
        }
    }

    public record Result(
            Status status,
            UUID requestId,
            ServerShopOfferCommit commit,
            com.enviouse.futureshops.server.escrow.playershop
                    .PlayerShopAtomicCommit valueCommit,
            boolean replayed
    ) {
        public Result {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(requestId, "requestId");
            if (status.success()
                    != (commit != null
                    || replayed && valueCommit == null)
                    || commit != null
                    && !commit.requestId().equals(requestId)
                    || valueCommit != null
                    && !valueCommit.commitId().equals(requestId)
                    || commit != null
                    && !commit.valueCommit().equals(valueCommit)) {
                throw new IllegalArgumentException(
                        "Server shop offer result is invalid");
            }
        }

        static Result success(
                Status status,
                ServerShopOfferCommit commit,
                boolean replayed
        ) {
            return new Result(status, commit.requestId(), commit,
                    commit.valueCommit(), replayed);
        }

        static Result archivedSuccess(
                Status status,
                UUID requestId
        ) {
            return new Result(status, requestId,
                    null, null, true);
        }

        static Result failure(Status status, UUID requestId) {
            return new Result(status, requestId, null, null, false);
        }

        static Result failureWithValue(
                Status status,
                UUID requestId,
                com.enviouse.futureshops.server.escrow.playershop
                        .PlayerShopAtomicCommit value
        ) {
            return new Result(status, requestId, null, value, false);
        }

        public long settledMoneyMinorUnits() {
            if (commit == null) {
                return 0L;
            }
            return commit.valueCommit().committedIntent()
                    .moneyTransfers().stream()
                    .mapToLong(transfer ->
                            transfer.amountMinorUnits())
                    .reduce(0L, Math::addExact);
        }
    }

    public enum Status {
        SUCCESS(true),
        CLAIMS_PENDING(true),
        RECOVERY_REQUIRED(false),
        QUARANTINED(false),
        CONFLICT(false),
        INVALID_REQUEST(false),
        STALE_REVISION(false),
        NOT_FOUND(false),
        NOT_AVAILABLE(false),
        OUT_OF_STOCK(false),
        LIMIT_REACHED(false),
        COOLDOWN(false),
        REJECTED(false),
        CANCELLED_BY_EVENT(false),
        UNAVAILABLE(false);

        private final boolean success;

        Status(boolean success) {
            this.success = success;
        }

        public boolean success() {
            return success;
        }
    }

    private record Quote(
            ServerShopOfferListing listing,
            OptionFacts option,
            CatalogStockState stock,
            Instant quotedAt,
            long moneyTotalMinorUnits,
            Status failure
    ) {
        private static Quote failure(Status status) {
            return new Quote(null, null, null, null, 0L, status);
        }
    }

    private record OptionFacts(
            AcquireOfferOption acquire,
            SellOfferOption sell,
            boolean moneyRequired,
            long capacity,
            OfferLimitPolicy limits,
            OfferSchedule schedule,
            String permissionNode
    ) {
    }

    private record OfferStorageAccess(
            ServerPlayer player,
            Request request,
            Quote quote,
            EscrowRuntimeService runtime,
            boolean trustedRecovery
    ) implements PlayerShopLiveEscrowService.StorageAccess {
        @Override
        public boolean revalidate(PlayerShopEscrowIntent intent) {
            if (trustedRecovery) {
                Optional<ServerShopOfferPreparedSavedData.Entry> stored =
                        ServerShopOfferPreparedSavedData.get(
                                player.getServer()).find(
                                request.requestId());
                if (stored.isEmpty()
                        || !stored.orElseThrow().matches(request)
                        || !stored.orElseThrow().listing().equals(
                        quote.listing())
                        || !stored.orElseThrow().intent().equals(intent)) {
                    return false;
                }
                List<StockReservation> reservations =
                        runtime.stockReservations(request.requestId());
                return reservationMatches(request, quote, reservations)
                        || reservationCommitted(
                        request, quote, reservations);
            }
            ServerShopOfferListing current = ShopCatalog.getOffer(
                    request.shopId(), request.listingId()).orElse(null);
            long now = Instant.now().getEpochSecond();
            OptionFacts currentOption = current == null
                    ? null : option(current, request);
            if (current == null
                    || current.revision() != quote.listing().revision()
                    || !current.active()
                    || current.expiresAtEpoch() > 0L
                    && now >= current.expiresAtEpoch()
                    || !current.schedule().activeAt(now)
                    || !ServerShopOfferPermissionPolicy.allowed(
                    player, current.permissionNode())
                    || currentOption == null
                    || !currentOption.schedule().activeAt(now)
                    || !ServerShopOfferPermissionPolicy.allowed(
                    player, currentOption.permissionNode())
                    || ShopSessionManager.get(player.getUUID())
                    .filter(session -> session.shopId().equals(
                            request.shopId())).isEmpty()) {
                return false;
            }
            List<StockReservation> reservations =
                    runtime.stockReservations(request.requestId());
            return reservationMatches(request, quote, reservations)
                    || reservationCommitted(request, quote, reservations);
        }

        @Override
        public boolean canExtract(
                com.enviouse.futureshops.server.escrow.playershop
                        .PlayerShopStorageMutationPlan plan
        ) {
            return false;
        }

        @Override
        public PlayerShopLiveEscrowService.StorageObservation observe(
                com.enviouse.futureshops.server.escrow.playershop
                        .PlayerShopStorageMutationPlan plan
        ) {
            return new PlayerShopLiveEscrowService.StorageObservation(
                    PlayerShopLiveEscrowService.StorageState.UNKNOWN,
                    "not applicable", "not applicable");
        }

        @Override
        public PlayerShopLiveEscrowService.StorageMutationResult extract(
                com.enviouse.futureshops.server.escrow.playershop
                        .PlayerShopStorageMutationPlan plan
        ) {
            return unavailableStorage();
        }

        @Override
        public PlayerShopLiveEscrowService.StorageMutationResult insert(
                com.enviouse.futureshops.server.escrow.playershop
                        .PlayerShopStorageMutationPlan plan,
                ItemStack stack
        ) {
            return unavailableStorage();
        }

        @Override
        public boolean applyBuybackCounter(PlayerShopEscrowIntent intent) {
            return true;
        }

        private PlayerShopLiveEscrowService.StorageMutationResult
        unavailableStorage() {
            return new PlayerShopLiveEscrowService.StorageMutationResult(
                    PlayerShopLiveEscrowService.StorageMutationStatus
                            .RECOVERY_REQUIRED,
                    "not applicable", "not applicable",
                    new byte[]{1}, "Server offer has no player shop storage");
        }
    }
}

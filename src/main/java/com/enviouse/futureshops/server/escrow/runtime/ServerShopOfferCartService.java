package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.Config;
import com.enviouse.futureshops.catalog.ShopCatalog;
import com.enviouse.futureshops.catalog.offer.AcquireOfferOption;
import com.enviouse.futureshops.catalog.offer.OfferAction;
import com.enviouse.futureshops.catalog.offer.OfferEscrowFanout;
import com.enviouse.futureshops.catalog.offer.OfferItemComponent;
import com.enviouse.futureshops.catalog.offer.ServerShopBundleSavings;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferListing;
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
import com.enviouse.futureshops.server.escrow.stock.StockReservationRequest;
import com.enviouse.futureshops.server.escrow.stock.StockReservationResolution;
import com.enviouse.futureshops.server.escrow.stock.StockReservationState;
import com.enviouse.futureshops.server.session.ShopSessionManager;
import com.enviouse.futureshops.server.shop.AdminShopToggleSavedData;
import com.enviouse.futureshops.server.transaction.ServerShopOfferIntentFactory;
import com.enviouse.futureshops.server.transaction.NormalizedOfferTransactionEvents;
import com.enviouse.futureshops.server.transaction.ServerShopOfferPermissionPolicy;
import com.enviouse.futureshops.server.transaction.ServerShopOfferPricing;
import com.enviouse.futureshops.server.transaction.ServerShopOfferUsageSavedData;
import com.enviouse.futureshops.server.transaction.TransactionHistoryService;
import com.enviouse.futureshops.server.pricing.DynamicPricingEngine;
import com.enviouse.futureshops.server.security.ServerRequestAction;
import com.enviouse.futureshops.server.security.ServerRequestSecurityManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ServerShopOfferCartService {
    private static final org.slf4j.Logger LOGGER =
            com.mojang.logging.LogUtils.getLogger();

    private ServerShopOfferCartService() {
    }

    public static Result execute(ServerPlayer player, Request request) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(request, "request");
        if (!player.getUUID().equals(request.playerId())) {
            return Result.failure(
                    ServerShopOfferService.Status.CONFLICT,
                    request.requestId());
        }
        if (player.getServer() == null
                || !AdminShopToggleSavedData.get(player.getServer())
                .isAdminShopEnabled()
                || ShopSessionManager.get(player.getUUID())
                .filter(session -> session.shopId().equals(
                        request.shopId())).isEmpty()) {
            return Result.failure(
                    ServerShopOfferService.Status.NOT_AVAILABLE,
                    request.requestId());
        }
        EscrowRuntimeService runtime =
                EscrowRuntimeManager.getOrNull();
        if (runtime == null || !runtime.isReady()) {
            return Result.failure(
                    ServerShopOfferService.Status.UNAVAILABLE,
                    request.requestId());
        }
        try {
            if (!ServerRequestSecurityManager.tryAcquire(
                    player, ServerRequestAction.SERVER_SHOP_OFFER)
                    .allowed()) {
                return Result.failure(
                        ServerShopOfferService.Status.UNAVAILABLE,
                        request.requestId());
            }
            ServerShopOfferCartCommitSavedData commits =
                    ServerShopOfferCartCommitSavedData.get(
                            player.getServer());
            Optional<ServerShopOfferReplayReceipt> durable =
                    ServerShopOfferReplayLedger.get(
                            player.getServer()).find(
                            request.requestId());
            if (durable.isPresent()) {
                ServerShopOfferReplayReceipt receipt =
                        durable.orElseThrow();
                if (!receipt.matches(request)) {
                    return Result.failure(
                            ServerShopOfferService.Status.CONFLICT,
                            request.requestId());
                }
                return receipt.successful()
                        ? Result.archivedSuccess(
                        receipt.status(), request.requestId())
                        : Result.failure(
                        receipt.status(), request.requestId());
            }
            Optional<ServerShopOfferCartCommit> completed =
                    commits.find(request.requestId());
            if (completed.isPresent()) {
                ServerShopOfferCartCommit commit =
                        completed.orElseThrow();
                if (!commitMatches(request, commit)) {
                    return Result.failure(
                            ServerShopOfferService.Status.CONFLICT,
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
                            : Result.failure(
                            ServerShopOfferService.Status.CONFLICT,
                            request.requestId());
                }
                Optional<ServerShopOfferCartPreparedSavedData.Entry>
                        preparedEntry =
                        ServerShopOfferCartPreparedSavedData.get(
                                player.getServer()).find(
                                request.requestId());
                if (preparedEntry.isEmpty()) {
                    Optional<ServerShopOfferReplayReceipt>
                            preparedArchive =
                            ServerShopOfferCartPreparedSavedData.get(
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
                            ServerShopOfferService.Status
                                    .RECOVERY_REQUIRED,
                            request.requestId(),
                            commit.valueCommit());
                }
                if (!preparedMatches(
                        request, preparedEntry.orElseThrow())) {
                    return Result.failureWithValue(
                            ServerShopOfferService.Status
                                    .RECOVERY_REQUIRED,
                            request.requestId(),
                            commit.valueCommit());
                }
                publishProjections(
                        player, request,
                        preparedEntry.orElseThrow().quotedAt(),
                        preparedEntry.orElseThrow().lines());
                recordReplayReceipt(
                        player, preparedEntry.orElseThrow(),
                        commit);
                return Result.success(
                        commit.claimsPending()
                                ? ServerShopOfferService.Status
                                .CLAIMS_PENDING
                                : ServerShopOfferService.Status.SUCCESS,
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
                        == ServerShopOfferTerminalReceiptSavedData.Kind.CART
                        && receipt.requestFingerprint().equals(
                        request.fingerprint());
                if (!matches) {
                    return Result.failure(
                            ServerShopOfferService.Status.CONFLICT,
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
                archived = ServerShopOfferCartPreparedSavedData.get(
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
                        : Result.failure(
                        ServerShopOfferService.Status.CONFLICT,
                        request.requestId());
            }
            if (ServerShopOfferCommitSavedData.get(
                    player.getServer()).find(
                    request.requestId()).isPresent()
                    || ServerShopOfferCommitSavedData.get(
                    player.getServer()).findArchived(
                    request.requestId()).isPresent()
                    || ServerShopOfferPreparedSavedData.get(
                    player.getServer()).find(
                    request.requestId()).isPresent()
                    || ServerShopOfferPreparedSavedData.get(
                    player.getServer()).findArchived(
                    request.requestId()).isPresent()) {
                return Result.failure(
                        ServerShopOfferService.Status.CONFLICT,
                        request.requestId());
            }
            ServerShopOfferCartPreparedSavedData preparedEntries =
                    ServerShopOfferCartPreparedSavedData.get(
                            player.getServer());
            Optional<ServerShopOfferCartPreparedSavedData.Entry>
                    stored = preparedEntries.find(request.requestId());
            if (stored.isPresent()) {
                ServerShopOfferCartPreparedSavedData.Entry entry =
                        stored.orElseThrow();
                if (!preparedMatches(request, entry)) {
                    return Result.failure(
                            ServerShopOfferService.Status.CONFLICT,
                            request.requestId());
                }
                if (!ServerShopOfferReplayRetention
                        .ensureCartCapacity(
                                player.getServer(),
                                request.requestId(),
                                request.playerId())) {
                    return Result.failure(
                            ServerShopOfferService.Status.UNAVAILABLE,
                            request.requestId());
                }
                return executeQuoted(player, request,
                        entry.quotedAt(), entry.lines(), entry.intent(),
                        entry.stockReservation(), runtime, false);
            }
            if (runtime.playerShopEscrowEntry(
                    request.requestId()).isPresent()) {
                return Result.failure(
                        ServerShopOfferService.Status
                                .RECOVERY_REQUIRED,
                        request.requestId());
            }
            if (!ServerShopOfferReplayRetention.ensureCartCapacity(
                    player.getServer(), request.requestId(),
                    request.playerId())) {
                return Result.failure(
                        ServerShopOfferService.Status.UNAVAILABLE,
                        request.requestId());
            }
            Quote quote = quote(player, request, runtime);
            if (quote.failure() != null) {
                return failAcceptedRequest(
                        player, request, quote.failure());
            }
            List<QuotedLine> authorizedLines = new ArrayList<>(
                    quote.lines().size());
            for (QuotedLine line : quote.lines()) {
                if (!Config.eventsTransactionEnabled) {
                    authorizedLines.add(line);
                    continue;
                }
                NormalizedOfferTransactionEvents.Decision event =
                        NormalizedOfferTransactionEvents.fireAcquirePre(
                                player, request.shopId(),
                                line.listing(), line.option(),
                                line.quantity(),
                                line.moneyTotalMinorUnits());
                if (event.status()
                        == NormalizedOfferTransactionEvents.Status
                        .CANCELLED) {
                    return failAcceptedRequest(
                            player, request,
                            ServerShopOfferService.Status
                                    .CANCELLED_BY_EVENT);
                }
                if (event.status()
                        != NormalizedOfferTransactionEvents.Status
                        .ACCEPTED) {
                    return failAcceptedRequest(
                            player, request,
                            ServerShopOfferService.Status.INVALID_REQUEST);
                }
                authorizedLines.add(new QuotedLine(
                        line.listing(), line.option(), line.quantity(),
                        line.stock(),
                        event.authorizedMoneyMinorUnits(),
                        adjustedSavings(
                                line.savings(),
                                event.authorizedMoneyMinorUnits())));
            }
            quote = new Quote(
                    List.copyOf(authorizedLines),
                    quote.quotedAt(), null);
            DimensionAwareShopReference reference =
                    new DimensionAwareShopReference(
                            request.shopId(),
                            player.serverLevel().dimension().location()
                                    .toString(),
                            player.blockPosition().getX(),
                            player.blockPosition().getY(),
                            player.blockPosition().getZ());
            List<ServerShopOfferIntentFactory.AcquireLine>
                    intentLines = quote.lines().stream()
                    .map(line ->
                            new ServerShopOfferIntentFactory.AcquireLine(
                                    line.listing(), line.option(),
                                    line.quantity(),
                                    line.moneyTotalMinorUnits()))
                    .toList();
            PlayerShopEscrowIntent intent =
                    ServerShopOfferIntentFactory.acquireCart(
                            request.requestId(), request.playerId(),
                            request.shopId(), intentLines,
                            request.paymentSource().orElse(null),
                            BalanceManager.getProvider().getBalance(
                                    request.playerId()),
                            reference, quote.quotedAt()).intent();
            StockMutationCommand.ReserveBatch reserve =
                    stockReservation(request, quote.lines(),
                            quote.quotedAt());
            List<ServerShopOfferCartPreparedSavedData.QuotedLine>
                    storedLines = quote.lines().stream().map(line ->
                    new ServerShopOfferCartPreparedSavedData.QuotedLine(
                            line.listing(), line.option().optionId(),
                            line.quantity(), line.stock().revision(),
                            line.moneyTotalMinorUnits(),
                            line.savings())).toList();
            preparedEntries.prepare(
                    new ServerShopOfferCartPreparedSavedData.Entry(
                            request.requestId(), request.playerId(),
                            request.shopId(), request.fingerprint(),
                            request.paymentSource(), quote.quotedAt(),
                            storedLines, intent, reserve));
            return executeQuoted(player, request, quote.quotedAt(),
                    storedLines, intent, reserve, runtime, false);
        } catch (ArithmeticException | IllegalArgumentException exception) {
            return failAcceptedRequest(
                    player, request,
                    ServerShopOfferService.Status.INVALID_REQUEST);
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
            return Result.failure(
                    ServerShopOfferService.Status.UNAVAILABLE,
                    requestId);
        }
        EscrowRuntimeService runtime =
                EscrowRuntimeManager.getOrNull();
        if (runtime == null || !runtime.isReady()) {
            return Result.failure(
                    ServerShopOfferService.Status.UNAVAILABLE,
                    requestId);
        }
        ServerShopOfferCartPreparedSavedData preparedEntries =
                ServerShopOfferCartPreparedSavedData.get(
                        player.getServer());
        Optional<ServerShopOfferCartPreparedSavedData.Entry> stored =
                preparedEntries.find(requestId);
        if (stored.isEmpty()
                || !stored.orElseThrow().playerId().equals(
                player.getUUID())) {
            return Result.failure(
                    ServerShopOfferService.Status.RECOVERY_REQUIRED,
                    requestId);
        }
        ServerShopOfferCartPreparedSavedData.Entry entry =
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
                    return Result.failure(
                            ServerShopOfferService.Status.CONFLICT,
                            requestId);
                }
                preparedEntries.remove(requestId);
                return receipt.successful()
                        ? Result.archivedSuccess(
                        receipt.status(), requestId)
                        : Result.failure(
                        receipt.status(), requestId);
            }
            Optional<ServerShopOfferTerminalReceiptSavedData.Receipt>
                    terminal =
                    ServerShopOfferTerminalReceiptSavedData.get(
                            player.getServer()).find(requestId);
            if (terminal.isPresent()) {
                ServerShopOfferTerminalReceiptSavedData.Receipt receipt =
                        terminal.orElseThrow();
                if (receipt.kind()
                        != ServerShopOfferTerminalReceiptSavedData.Kind.CART
                        || !receipt.requestFingerprint().equals(
                        request.fingerprint())) {
                    return Result.failure(
                            ServerShopOfferService.Status.CONFLICT,
                            requestId);
                }
                ServerShopOfferReplayLedger.get(
                        player.getServer()).record(
                        ServerShopOfferReplayReceipt.terminal(
                                request, receipt.status()));
                preparedEntries.remove(requestId);
                return Result.failure(receipt.status(), requestId);
            }
            if (!ServerShopOfferReplayRetention.ensureCartCapacity(
                    player.getServer(), requestId, player.getUUID())) {
                return Result.failure(
                        ServerShopOfferService.Status.UNAVAILABLE,
                        requestId);
            }
            ServerShopOfferCartCommitSavedData commits =
                    ServerShopOfferCartCommitSavedData.get(
                            player.getServer());
            Optional<ServerShopOfferReplayReceipt> archived =
                    commits.findArchived(requestId);
            if (archived.isPresent()) {
                ServerShopOfferReplayReceipt receipt =
                        archived.orElseThrow();
                if (!receipt.matches(request)
                        || !receipt.matches(entry)
                        || !mirrorReplayReceipt(player, receipt)) {
                    return Result.failure(
                            ServerShopOfferService.Status.CONFLICT,
                            requestId);
                }
                return Result.archivedSuccess(
                        receipt.status(), requestId);
            }
            Optional<ServerShopOfferCartCommit> completed =
                    commits.find(requestId);
            if (completed.isPresent()) {
                ServerShopOfferCartCommit commit =
                        completed.orElseThrow();
                if (!commitMatches(request, commit)) {
                    return Result.failure(
                            ServerShopOfferService.Status.CONFLICT,
                            requestId);
                }
                publishProjections(
                        player, request, entry.quotedAt(),
                        entry.lines());
                recordReplayReceipt(player, entry, commit);
                return Result.success(
                        commit.claimsPending()
                                ? ServerShopOfferService.Status
                                .CLAIMS_PENDING
                                : ServerShopOfferService.Status.SUCCESS,
                        commit, true);
            }
            if (!preparedEntries.find(requestId)
                    .filter(entry::equals).isPresent()) {
                return Result.failure(
                        ServerShopOfferService.Status.RECOVERY_REQUIRED,
                        requestId);
            }
            return executeQuoted(
                    player, request, entry.quotedAt(), entry.lines(),
                    entry.intent(), entry.stockReservation(), runtime,
                    true);
        } catch (ArithmeticException | IllegalArgumentException exception) {
            return Result.failure(
                    ServerShopOfferService.Status.INVALID_REQUEST,
                    requestId);
        } catch (RuntimeException exception) {
            return recoverResult(request, runtime, exception);
        }
    }

    private static Request requestFrom(
            ServerShopOfferCartPreparedSavedData.Entry entry
    ) {
        List<LineRequest> lines = entry.lines().stream()
                .map(line -> new LineRequest(
                        line.listing().listingId(),
                        line.optionId(), line.quantity(),
                        line.listing().revision()))
                .toList();
        return new Request(
                entry.requestId(), entry.playerId(), entry.shopId(),
                lines, entry.paymentSource(), 0);
    }

    private static Result executeQuoted(
            ServerPlayer player,
            Request request,
            Instant quotedAt,
            List<ServerShopOfferCartPreparedSavedData.QuotedLine> lines,
            PlayerShopEscrowIntent intent,
            StockMutationCommand.ReserveBatch reserve,
            EscrowRuntimeService runtime,
            boolean trustedRecovery
    ) {
        try {
            StockCommandResult result =
                    runtime.commitStockMutation(reserve);
            if (result.receipt().outcome()
                    == StockMutationOutcome.INSUFFICIENT_STOCK) {
                recordTerminal(player, request,
                        ServerShopOfferService.Status.OUT_OF_STOCK);
                return Result.failure(
                        ServerShopOfferService.Status.OUT_OF_STOCK,
                        request.requestId());
            }
            if (result.receipt().outcome()
                    != StockMutationOutcome.APPLIED
                    && result.receipt().outcome()
                    != StockMutationOutcome.UNCHANGED
                    || !reservationStateMatches(
                    request, lines,
                    runtime.stockReservations(request.requestId()),
                    StockReservationState.HELD)
                    && !reservationStateMatches(
                    request, lines,
                    runtime.stockReservations(request.requestId()),
                    StockReservationState.COMMITTED)) {
                LOGGER.error(
                        "Server shop offer cart stock reservation verification failed for request {} with outcome {}",
                        request.requestId(), result.receipt().outcome());
                return Result.failure(
                        ServerShopOfferService.Status.RECOVERY_REQUIRED,
                        request.requestId());
            }
        } catch (RuntimeException exception) {
            return recoverResult(request, runtime, exception);
        }
        PlayerShopEscrowOrchestrator.Result value =
                PlayerShopLiveEscrowService.execute(
                        player, intent, request.responseToken(),
                        new CartStorageAccess(
                                player, request, lines, runtime,
                                trustedRecovery));
        if (preCommit(value)) {
            if (value.status()
                    == PlayerShopEscrowOrchestrator.Status.REJECTED) {
                if (!releaseStock(
                        request, lines, quotedAt, runtime)) {
                    return Result.failure(
                            ServerShopOfferService.Status
                                    .RECOVERY_REQUIRED,
                            request.requestId());
                }
                recordTerminal(player, request,
                        ServerShopOfferService.Status.REJECTED);
                return Result.failure(
                        ServerShopOfferService.Status.REJECTED,
                        request.requestId());
            }
            LOGGER.error(
                    "Server shop offer cart escrow did not commit for request {} with status {}, code {}, detail {}",
                    request.requestId(), value.status(), value.code(),
                    value.detail());
            return Result.failure(
                    value.status()
                            == PlayerShopEscrowOrchestrator.Status.CONFLICT
                            ? ServerShopOfferService.Status.CONFLICT
                            : value.status()
                            == PlayerShopEscrowOrchestrator.Status
                            .QUARANTINED
                            ? ServerShopOfferService.Status.QUARANTINED
                            : ServerShopOfferService.Status
                            .RECOVERY_REQUIRED,
                    request.requestId());
        }
        if (claimsPending(value)
                && value.status()
                != PlayerShopEscrowOrchestrator.Status
                .COMMITTED_WITH_PENDING_DELIVERY) {
            LOGGER.error(
                    "Server shop offer cart value committed with incomplete claim delivery for request {} with status {}, code {}, detail {}",
                    request.requestId(), value.status(), value.code(),
                    value.detail());
        }
        StockMutationCommand.ResolveBatch commitStock =
                stockResolution(request, lines,
                        StockMutationType.COMMIT_BATCH,
                        resolutionTime(
                                quotedAt,
                                StockMutationType.COMMIT_BATCH));
        try {
            StockCommandResult result =
                    runtime.commitStockMutation(commitStock);
            if (result.receipt().outcome()
                    != StockMutationOutcome.APPLIED
                    && result.receipt().outcome()
                    != StockMutationOutcome.UNCHANGED
                    || !reservationStateMatches(
                    request, lines,
                    runtime.stockReservations(request.requestId()),
                    StockReservationState.COMMITTED)) {
                LOGGER.error(
                        "Server shop offer cart stock commit verification failed for request {} with outcome {}",
                        request.requestId(), result.receipt().outcome());
                return Result.failureWithValue(
                        ServerShopOfferService.Status
                                .RECOVERY_REQUIRED,
                        request.requestId(), value.commit());
            }
        } catch (RuntimeException exception) {
            return Result.failureWithValue(
                    ServerShopOfferService.Status.RECOVERY_REQUIRED,
                    request.requestId(), value.commit());
        }
        List<ServerShopOfferCartCommit.Line> commitLines =
                lines.stream().map(line ->
                        ServerShopOfferCartCommit.captureLine(
                                line.listing(), line.optionId(),
                                line.quantity(), line.savings()))
                        .toList();
        ServerShopOfferCartCommit commit;
        try {
            commit = ServerShopOfferCartCommit.create(
                    request.requestId(), request.playerId(),
                    request.shopId(), commitLines,
                    request.paymentSource(), quotedAt,
                    claimsPending(value),
                    value.commit(), reserve, commitStock);
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Server shop offer cart commit evidence validation failed for request {}",
                    request.requestId(), exception);
            return Result.failureWithValue(
                    ServerShopOfferService.Status.RECOVERY_REQUIRED,
                    request.requestId(), value.commit());
        }
        try {
            ServerShopOfferCartCommitSavedData.get(
                    player.getServer()).commit(commit);
            publishProjections(player, request, quotedAt, lines);
            ServerShopOfferCartPreparedSavedData.Entry entry =
                    ServerShopOfferCartPreparedSavedData.get(
                            player.getServer()).find(
                            request.requestId()).orElseThrow();
            recordReplayReceipt(player, entry, commit);
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Server shop offer cart durable commit recording failed for request {}",
                    request.requestId(), exception);
            return Result.failureWithValue(
                    ServerShopOfferService.Status.RECOVERY_REQUIRED,
                    request.requestId(), value.commit());
        }
        firePostEvents(player, request, lines);
        ServerShopOfferService.Status status =
                claimsPending(value)
                        ? ServerShopOfferService.Status.CLAIMS_PENDING
                        : ServerShopOfferService.Status.SUCCESS;
        return Result.success(status, commit,
                value.status()
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

    private static void firePostEvents(
            ServerPlayer player,
            Request request,
            List<ServerShopOfferCartPreparedSavedData.QuotedLine> lines
    ) {
        if (!Config.eventsTransactionEnabled) {
            return;
        }
        long balance = BalanceManager.getProvider().getBalance(
                request.playerId());
        for (ServerShopOfferCartPreparedSavedData.QuotedLine line
                : lines) {
            try {
                AcquireOfferOption option = option(line);
                NormalizedOfferTransactionEvents.fireAcquirePost(
                        player, request.shopId(), line.listing(),
                        option, line.quantity(),
                        line.moneyTotalMinorUnits(), balance);
            } catch (RuntimeException exception) {
                LOGGER.error(
                        "Server shop offer cart post event failed for request {}",
                        request.requestId(), exception);
            }
        }
    }

    private static void recordTerminal(
            ServerPlayer player,
            Request request,
            ServerShopOfferService.Status status
    ) {
        ServerShopOfferReplayLedger.get(
                player.getServer()).record(
                ServerShopOfferReplayReceipt.terminal(
                        request, status));
        ServerShopOfferCartPreparedSavedData.get(
                player.getServer()).remove(request.requestId());
    }

    private static Result failAcceptedRequest(
            ServerPlayer player,
            Request request,
            ServerShopOfferService.Status status
    ) {
        if (ServerShopOfferReplayReceipt
                .isDurableTerminalFailure(status)) {
            recordTerminal(player, request, status);
        }
        return Result.failure(status, request.requestId());
    }

    private static void recordReplayReceipt(
            ServerPlayer player,
            ServerShopOfferCartPreparedSavedData.Entry prepared,
            ServerShopOfferCartCommit commit
    ) {
        ServerShopOfferReplayReceipt receipt =
                ServerShopOfferReplayReceipt.cart(
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

    private static boolean preparedMatches(
            Request request,
            ServerShopOfferCartPreparedSavedData.Entry entry
    ) {
        return entry.playerId().equals(request.playerId())
                && entry.shopId().equals(request.shopId())
                && entry.requestFingerprint().equals(
                request.fingerprint())
                && entry.paymentSource().equals(
                request.paymentSource());
    }

    private static void publishProjections(
            ServerPlayer player,
            Request request,
            Instant quotedAt,
            List<ServerShopOfferCartPreparedSavedData.QuotedLine> lines
    ) {
        long now = quotedAt.getEpochSecond();
        Map<String, Integer> listingTotals =
                requestedListingTotals(lines);
        for (Map.Entry<String, Integer> total
                : listingTotals.entrySet()) {
            ServerShopOfferCartPreparedSavedData.QuotedLine sample =
                    lines.stream().filter(line ->
                            line.listing().listingId().equals(
                                    total.getKey()))
                            .findFirst().orElseThrow();
            ServerShopOfferUsageSavedData.get(
                    player.getServer()).recordListing(
                    request.requestId(), request.playerId(),
                    request.shopId(), total.getKey(),
                    OfferAction.ACQUIRE_FROM_SHOP,
                    total.getValue(),
                    sample.listing().limits(), now);
        }
        for (ServerShopOfferCartPreparedSavedData.QuotedLine line
                : lines) {
            AcquireOfferOption option = option(line);
            ServerShopOfferUsageSavedData.get(
                    player.getServer()).recordOption(
                    request.requestId(), request.playerId(),
                    request.shopId(),
                    line.listing().listingId(),
                    line.optionId(),
                    OfferAction.ACQUIRE_FROM_SHOP,
                    line.quantity(), option.limits(), now);
            if (option.moneyCostPresent()
                    && ShopCatalog.get(request.shopId())
                    .map(definition -> definition.schemaVersion() == 1)
                    .orElse(false)) {
                DynamicPricingEngine.recordBuyOnce(
                        player.getServer(),
                        "server.shop.offer." + request.requestId()
                                + "." + line.listing().listingId()
                                + "." + line.optionId(),
                        request.shopId(),
                        line.listing().listingId(),
                        line.quantity());
            }
            recordHistory(player, request, quotedAt, line, option);
        }
    }

    private static Quote quote(
            ServerPlayer player,
            Request request,
            EscrowRuntimeService runtime
    ) {
        Instant quotedAt = Instant.now();
        long now = quotedAt.getEpochSecond();
        Map<String, ServerShopOfferListing> allListings =
                new LinkedHashMap<>();
        ShopCatalog.get(request.shopId()).ifPresent(definition ->
                definition.offers().forEach(listing ->
                        allListings.put(
                                listing.listingId(), listing)));
        List<QuotedLine> lines = new ArrayList<>();
        boolean moneyRequired = false;
        Map<String, Integer> listingTotals =
                request.lines().stream().collect(
                        java.util.stream.Collectors.toMap(
                                LineRequest::listingId,
                                LineRequest::quantity,
                                Math::addExact,
                                LinkedHashMap::new));
        java.util.Set<String> checkedListings =
                new java.util.HashSet<>();
        Map<String, Integer> stockTotals = new LinkedHashMap<>();
        Map<String, CatalogStockState> stocks =
                new LinkedHashMap<>();
        for (LineRequest requested : request.lines()) {
            ServerShopOfferListing listing =
                    allListings.get(requested.listingId());
            if (listing == null || !listing.active()) {
                return Quote.failure(
                        ServerShopOfferService.Status.NOT_FOUND);
            }
            if (listing.revision()
                    != requested.expectedOfferRevision()) {
                return Quote.failure(
                        ServerShopOfferService.Status.STALE_REVISION);
            }
            if (listing.expiresAtEpoch() > 0L
                    && now >= listing.expiresAtEpoch()
                    || !listing.schedule().activeAt(now)
                    || !ServerShopOfferPermissionPolicy.allowed(
                    player, listing.permissionNode())) {
                return Quote.failure(
                        ServerShopOfferService.Status.NOT_AVAILABLE);
            }
            AcquireOfferOption option =
                    listing.acquireOptions().stream()
                            .filter(value -> value.optionId().equals(
                                    requested.optionId()))
                            .findFirst().orElse(null);
            if (option == null
                    || !option.schedule().activeAt(now)
                    || !ServerShopOfferPermissionPolicy.allowed(
                    player, option.permissionNode())
                    || requested.quantity()
                    > Math.min(
                    listing.limits().maximumPerRequest(),
                    option.limits().maximumPerRequest())) {
                return Quote.failure(
                        ServerShopOfferService.Status.NOT_AVAILABLE);
            }
            ServerShopOfferUsageSavedData usageData =
                    ServerShopOfferUsageSavedData.get(
                            player.getServer());
            ServerShopOfferUsageSavedData.Decision usage =
                    checkedListings.add(listing.listingId())
                            ? usageData.checkListing(
                            request.playerId(), request.shopId(),
                            listing.listingId(),
                            OfferAction.ACQUIRE_FROM_SHOP,
                            listingTotals.get(listing.listingId()),
                            listing.limits(), now)
                            : ServerShopOfferUsageSavedData.Decision
                            .ALLOWED;
            if (usage == ServerShopOfferUsageSavedData.Decision.ALLOWED) {
                usage = usageData.checkOption(
                        request.playerId(), request.shopId(),
                        listing.listingId(), option.optionId(),
                        OfferAction.ACQUIRE_FROM_SHOP,
                        requested.quantity(), option.limits(), now);
            }
            if (usage != ServerShopOfferUsageSavedData.Decision.ALLOWED) {
                if (usage == ServerShopOfferUsageSavedData.Decision
                        .RECOVERY_REQUIRED) {
                    return Quote.failure(
                            ServerShopOfferService.Status.UNAVAILABLE);
                }
                return Quote.failure(
                        usage == ServerShopOfferUsageSavedData.Decision
                                .COOLDOWN
                                ? ServerShopOfferService.Status.COOLDOWN
                                : ServerShopOfferService.Status
                                .LIMIT_REACHED);
            }
            CatalogStockState stock = stocks.computeIfAbsent(
                    listing.listingId(), ignored ->
                            runtime.stockListing(new StockKey(
                                    request.shopId(),
                                    listing.listingId()))
                                    .orElse(null));
            if (stock == null
                    || stock.status() != CatalogStockStatus.ACTIVE) {
                return Quote.failure(
                        ServerShopOfferService.Status.UNAVAILABLE);
            }
            stockTotals.merge(listing.listingId(),
                    Math.multiplyExact(requested.quantity(),
                            option.outputMultiplier()),
                    Math::addExact);
            moneyRequired |= option.moneyCostPresent();
            long moneyTotal = quotedLineMoneyTotal(
                    player, request, requested, option);
            if (option.moneyCostPresent() && moneyTotal <= 0L) {
                return Quote.failure(
                        ServerShopOfferService.Status.NOT_AVAILABLE);
            }
            Optional<ServerShopBundleSavings.Snapshot> savings =
                    ServerShopBundleSavings.calculate(
                            listing, option, requested.quantity(),
                            allListings, quotedAt,
                            permission ->
                                    ServerShopOfferPermissionPolicy.allowed(
                                            player, permission),
                            (comparisonListing, comparisonOption,
                             comparisonQuantity) ->
                                    ServerShopOfferPricing.moneyTotal(
                                            player.getServer(),
                                            request.shopId(),
                                            comparisonListing,
                                            comparisonOption,
                                            comparisonQuantity));
            lines.add(new QuotedLine(
                    listing, option, requested.quantity(),
                    stock, moneyTotal, savings));
        }
        if (moneyRequired
                != request.paymentSource().isPresent()) {
            return Quote.failure(
                    ServerShopOfferService.Status.INVALID_REQUEST);
        }
        for (Map.Entry<String, Integer> total
                : stockTotals.entrySet()) {
            CatalogStockState stock = stocks.get(total.getKey());
            if (!stock.unlimited()
                    && stock.availableQuantity() < total.getValue()) {
                return Quote.failure(
                        ServerShopOfferService.Status.OUT_OF_STOCK);
            }
        }
        if (!escrowFanoutFits(lines.stream().map(line ->
                new ServerShopOfferIntentFactory.AcquireLine(
                        line.listing(), line.option(), line.quantity(),
                        line.moneyTotalMinorUnits())).toList())) {
            return Quote.failure(
                    ServerShopOfferService.Status.INVALID_REQUEST);
        }
        return new Quote(
                List.copyOf(lines), quotedAt, null);
    }

    static boolean escrowFanoutFits(
            List<ServerShopOfferIntentFactory.AcquireLine> lines
    ) {
        return escrowFanoutFits(lines,
                OfferEscrowFanout::registeredMaximumStackSize);
    }

    static boolean escrowFanoutFits(
            List<ServerShopOfferIntentFactory.AcquireLine> lines,
            java.util.function.ToIntFunction<String> maximumStackSize
    ) {
        LinkedHashMap<ComponentIdentity, Long> inputs =
                new LinkedHashMap<>();
        LinkedHashMap<ComponentIdentity, Long> outputs =
                new LinkedHashMap<>();
        try {
            for (ServerShopOfferIntentFactory.AcquireLine line : lines) {
                long quantity = line.quantity();
                for (OfferItemComponent component
                        : line.listing().outputs()) {
                    long total = Math.multiplyExact(
                            Math.multiplyExact(
                                    (long) component.count(),
                                    line.option().outputMultiplier()),
                            quantity);
                    mergeComponent(outputs, component, total);
                }
                for (OfferItemComponent component
                        : line.option().itemCosts()) {
                    mergeComponent(inputs, component,
                            Math.multiplyExact(
                                    (long) component.count(), quantity));
                }
            }
            return OfferEscrowFanout.fits(
                    componentUnits(inputs), componentUnits(outputs),
                    maximumStackSize);
        } catch (ArithmeticException exception) {
            return false;
        }
    }

    private static void mergeComponent(
            Map<ComponentIdentity, Long> totals,
            OfferItemComponent component,
            long amount
    ) {
        totals.merge(new ComponentIdentity(
                        component.itemId(), component.exactNbt()),
                amount, Math::addExact);
    }

    private static List<OfferEscrowFanout.ComponentUnits> componentUnits(
            Map<ComponentIdentity, Long> totals
    ) {
        return totals.entrySet().stream().map(entry ->
                new OfferEscrowFanout.ComponentUnits(
                        entry.getKey().itemId(),
                        entry.getKey().exactNbt(),
                        entry.getValue())).toList();
    }

    private static Optional<ServerShopBundleSavings.Snapshot>
    adjustedSavings(
            Optional<ServerShopBundleSavings.Snapshot> savings,
            long authorizedMoneyMinorUnits
    ) {
        if (savings.isEmpty()) {
            return Optional.empty();
        }
        ServerShopBundleSavings.Snapshot snapshot =
                savings.orElseThrow();
        if (authorizedMoneyMinorUnits
                >= snapshot.individualTotalMinorUnits()) {
            return Optional.empty();
        }
        long exactSavings = Math.subtractExact(
                snapshot.individualTotalMinorUnits(),
                authorizedMoneyMinorUnits);
        long basisPoints = Math.floorDiv(
                Math.multiplyExact(exactSavings, 10_000L),
                snapshot.individualTotalMinorUnits());
        return Optional.of(new ServerShopBundleSavings.Snapshot(
                snapshot.individualTotalMinorUnits(),
                authorizedMoneyMinorUnits, exactSavings, basisPoints,
                snapshot.comparisonRevisions()));
    }

    private static StockMutationCommand.ReserveBatch stockReservation(
            Request request,
            List<QuotedLine> lines,
            Instant quotedAt
    ) {
        Map<String, Integer> totals = stockTotals(lines);
        List<StockReservationRequest> reservations =
                new ArrayList<>(totals.size());
        for (Map.Entry<String, Integer> entry : totals.entrySet()) {
            CatalogStockState stock = lines.stream()
                    .filter(line -> line.listing().listingId()
                            .equals(entry.getKey()))
                    .findFirst().orElseThrow().stock();
            reservations.add(new StockReservationRequest(
                    new StockKey(request.shopId(), entry.getKey()),
                    StockReservationDirection.OUTBOUND,
                    entry.getValue(), stock.revision()));
        }
        return new StockMutationCommand.ReserveBatch(
                ServerShopOfferCartCommit.stockReserveRequestId(
                        request.requestId()),
                request.requestId(), reservations, quotedAt);
    }

    private static StockMutationCommand.ResolveBatch stockResolution(
            Request request,
            List<ServerShopOfferCartPreparedSavedData.QuotedLine> lines,
            StockMutationType operation,
            Instant appliedAt
    ) {
        Map<String, Integer> totals = storedStockTotals(lines);
        List<StockReservationResolution> resolutions =
                totals.keySet().stream().map(listingId ->
                        new StockReservationResolution(
                                StockReservationId.forTransaction(
                                        request.requestId(),
                                        new StockKey(request.shopId(),
                                                listingId),
                                        StockReservationDirection
                                                .OUTBOUND),
                                0L)).toList();
        UUID commandId = operation == StockMutationType.COMMIT_BATCH
                ? ServerShopOfferCartCommit.stockCommitRequestId(
                request.requestId())
                : ServerShopOfferCartCommit.stockReleaseRequestId(
                request.requestId());
        return new StockMutationCommand.ResolveBatch(
                commandId, operation, request.requestId(),
                resolutions, appliedAt);
    }

    private static boolean releaseStock(
            Request request,
            List<ServerShopOfferCartPreparedSavedData.QuotedLine> lines,
            Instant quotedAt,
            EscrowRuntimeService runtime
    ) {
        try {
            runtime.commitStockMutation(stockResolution(
                    request, lines, StockMutationType.RELEASE_BATCH,
                    resolutionTime(quotedAt,
                            StockMutationType.RELEASE_BATCH)));
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static Instant resolutionTime(
            Instant quotedAt,
            StockMutationType operation
    ) {
        return quotedAt.plusNanos(
                operation == StockMutationType.COMMIT_BATCH ? 1L : 2L);
    }

    private static boolean reservationStateMatches(
            Request request,
            List<ServerShopOfferCartPreparedSavedData.QuotedLine> lines,
            List<StockReservation> reservations,
            StockReservationState state
    ) {
        Map<String, Integer> totals = storedStockTotals(lines);
        if (reservations.size() != totals.size()) {
            return false;
        }
        for (Map.Entry<String, Integer> entry : totals.entrySet()) {
            StockKey key = new StockKey(
                    request.shopId(), entry.getKey());
            StockReservation found = reservations.stream()
                    .filter(value -> value.stockKey().equals(key))
                    .findFirst().orElse(null);
            if (found == null
                    || !found.transactionId().equals(request.requestId())
                    || found.direction()
                    != StockReservationDirection.OUTBOUND
                    || found.quantity() != entry.getValue()
                    || found.state() != state) {
                return false;
            }
        }
        return true;
    }

    private static Map<String, Integer> stockTotals(
            List<QuotedLine> lines
    ) {
        LinkedHashMap<String, Integer> totals = new LinkedHashMap<>();
        lines.stream().sorted(Comparator.comparing(line ->
                        line.listing().listingId()))
                .forEach(line -> totals.merge(
                        line.listing().listingId(),
                        Math.multiplyExact(line.quantity(),
                                line.option().outputMultiplier()),
                        Math::addExact));
        return totals;
    }

    private static Map<String, Integer> storedStockTotals(
            List<ServerShopOfferCartPreparedSavedData.QuotedLine> lines
    ) {
        LinkedHashMap<String, Integer> totals = new LinkedHashMap<>();
        lines.stream().sorted(Comparator.comparing(line ->
                        line.listing().listingId()))
                .forEach(line -> totals.merge(
                        line.listing().listingId(),
                        Math.multiplyExact(line.quantity(),
                                option(line).outputMultiplier()),
                        Math::addExact));
        return totals;
    }

    static Map<String, Integer> requestedListingTotals(
            List<ServerShopOfferCartPreparedSavedData.QuotedLine> lines
    ) {
        LinkedHashMap<String, Integer> totals = new LinkedHashMap<>();
        lines.stream().sorted(Comparator.comparing(line ->
                        line.listing().listingId()))
                .forEach(line -> totals.merge(
                        line.listing().listingId(),
                        line.quantity(),
                        Math::addExact));
        return Map.copyOf(totals);
    }

    private static AcquireOfferOption option(
            ServerShopOfferCartPreparedSavedData.QuotedLine line
    ) {
        return line.listing().acquireOptions().stream()
                .filter(value -> value.optionId().equals(
                        line.optionId()))
                .findFirst().orElseThrow();
    }

    private static void recordHistory(
            ServerPlayer player,
            Request request,
            Instant quotedAt,
            ServerShopOfferCartPreparedSavedData.QuotedLine line,
            AcquireOfferOption option
    ) {
        long total = line.moneyTotalMinorUnits();
        String type = option.free() ? "FREE"
                : option.compound() ? "MONEY_AND_BARTER"
                : option.hasItemCosts() ? "BARTER" : "BUY";
        List<TransactionHistoryService.ServerOfferComponent>
                components = new ArrayList<>();
        components.addAll(historyComponents(
                line.listing().outputs(),
                option.outputMultiplier(),
                TransactionHistoryService.ComponentRole.OUTPUT));
        components.addAll(historyComponents(
                option.itemCosts(), 1,
                TransactionHistoryService.ComponentRole.INPUT));
        TransactionHistoryService.recordServerOfferComponents(
                player.getServer(), request.playerId(),
                request.shopId(), request.requestId(),
                line.listing().listingId(), type,
                line.quantity(), total, line.optionId(),
                components, line.savings(),
                quotedAt);
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

    private static boolean commitMatches(
            Request request,
            ServerShopOfferCartCommit commit
    ) {
        if (!commit.requestId().equals(request.requestId())
                || !commit.playerId().equals(request.playerId())
                || !commit.shopId().equals(request.shopId())
                || !commit.paymentSource().equals(
                request.paymentSource())
                || commit.lines().size() != request.lines().size()) {
            return false;
        }
        for (int index = 0; index < request.lines().size(); index++) {
            LineRequest requested = request.lines().get(index);
            ServerShopOfferCartCommit.Line actual =
                    commit.lines().get(index);
            if (!actual.listingId().equals(requested.listingId())
                    || !actual.optionId().equals(requested.optionId())
                    || actual.quantity() != requested.quantity()
                    || actual.offerRevision()
                    != requested.expectedOfferRevision()) {
                return false;
            }
        }
        return true;
    }

    private static long quotedLineMoneyTotal(
            ServerPlayer player,
            Request request,
            LineRequest line,
            AcquireOfferOption option
    ) {
        if (!option.moneyCostPresent()) {
            return 0L;
        }
        ServerShopOfferListing listing = ShopCatalog.getOffer(
                request.shopId(), line.listingId()).orElseThrow();
        return ServerShopOfferPricing.moneyTotal(
                player.getServer(), request.shopId(), listing,
                option, line.quantity());
    }

    private static Result recoverResult(
            Request request,
            EscrowRuntimeService runtime,
            RuntimeException cause
    ) {
        LOGGER.error(
                "Server shop offer cart entered recovery for request {}",
                request.requestId(), cause);
        try {
            Optional<com.enviouse.futureshops.server.escrow.playershop
                    .PlayerShopAtomicCommit> commit =
                    runtime.playerShopEscrowEntry(request.requestId())
                            .map(value -> value.snapshot().commit());
            if (commit.isPresent()) {
                return Result.failureWithValue(
                        ServerShopOfferService.Status.RECOVERY_REQUIRED,
                        request.requestId(), commit.orElseThrow());
            }
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Server shop offer cart recovery inspection failed for request {}",
                    request.requestId(), exception);
        }
        return Result.failure(
                ServerShopOfferService.Status.RECOVERY_REQUIRED,
                request.requestId());
    }

    public record LineRequest(
            String listingId,
            String optionId,
            int quantity,
            long expectedOfferRevision
    ) {
        public LineRequest {
            listingId = identifier(listingId);
            optionId = identifier(optionId);
            if (quantity <= 0 || quantity > 2304
                    || expectedOfferRevision < 0L
                    || expectedOfferRevision
                    > ServerShopOfferCommit.MAX_REVISION) {
                throw new IllegalArgumentException(
                        "Server shop offer cart line is invalid");
            }
        }
    }

    public record Request(
            UUID requestId,
            UUID playerId,
            String shopId,
            List<LineRequest> lines,
            Optional<PaymentSource> paymentSource,
            int responseToken
    ) {
        public Request {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(playerId, "playerId");
            shopId = identifier(shopId);
            lines = canonicalLines(lines);
            paymentSource = Objects.requireNonNull(
                    paymentSource, "paymentSource");
            if (requestId.equals(new UUID(0L, 0L))
                    || playerId.equals(new UUID(0L, 0L))
                    || responseToken < 0 || responseToken > 2303) {
                throw new IllegalArgumentException(
                        "Server shop offer cart request is invalid");
            }
        }

        public String fingerprint() {
            StringBuilder material = new StringBuilder(
                    "futureshops server shop offer cart request v1")
                    .append('\u0000').append(playerId)
                    .append('\u0000').append(shopId)
                    .append('\u0000').append(
                            paymentSource.map(Enum::name)
                                    .orElse("NONE"));
            for (LineRequest line : lines) {
                material.append('\u0000').append(line.listingId())
                        .append('\u0000').append(line.optionId())
                        .append('\u0000').append(line.quantity())
                        .append('\u0000').append(
                                line.expectedOfferRevision());
            }
            return sha256(material.toString()
                    .getBytes(StandardCharsets.UTF_8));
        }
    }

    public record Result(
            ServerShopOfferService.Status status,
            UUID requestId,
            ServerShopOfferCartCommit commit,
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
                        "Server shop offer cart result is invalid");
            }
        }

        static Result success(
                ServerShopOfferService.Status status,
                ServerShopOfferCartCommit commit,
                boolean replayed
        ) {
            return new Result(status, commit.requestId(), commit,
                    commit.valueCommit(), replayed);
        }

        static Result archivedSuccess(
                ServerShopOfferService.Status status,
                UUID requestId
        ) {
            return new Result(
                    status, requestId, null, null, true);
        }

        static Result failure(
                ServerShopOfferService.Status status,
                UUID requestId
        ) {
            return new Result(
                    status, requestId, null, null, false);
        }

        static Result failureWithValue(
                ServerShopOfferService.Status status,
                UUID requestId,
                com.enviouse.futureshops.server.escrow.playershop
                        .PlayerShopAtomicCommit value
        ) {
            return new Result(
                    status, requestId, null, value, false);
        }
    }

    private record QuotedLine(
            ServerShopOfferListing listing,
            AcquireOfferOption option,
            int quantity,
            CatalogStockState stock,
            long moneyTotalMinorUnits,
            Optional<ServerShopBundleSavings.Snapshot> savings
    ) {
    }

    private record Quote(
            List<QuotedLine> lines,
            Instant quotedAt,
            ServerShopOfferService.Status failure
    ) {
        private static Quote failure(
                ServerShopOfferService.Status status
        ) {
            return new Quote(List.of(), null, status);
        }
    }

    private record ComponentIdentity(
            String itemId,
            String exactNbt
    ) {
    }

    private record CartStorageAccess(
            ServerPlayer player,
            Request request,
            List<ServerShopOfferCartPreparedSavedData.QuotedLine> lines,
            EscrowRuntimeService runtime,
            boolean trustedRecovery
    ) implements PlayerShopLiveEscrowService.StorageAccess {
        @Override
        public boolean revalidate(PlayerShopEscrowIntent intent) {
            if (trustedRecovery) {
                Optional<ServerShopOfferCartPreparedSavedData.Entry>
                        stored =
                        ServerShopOfferCartPreparedSavedData.get(
                                player.getServer()).find(
                                request.requestId());
                if (stored.isEmpty()
                        || !preparedMatches(
                        request, stored.orElseThrow())
                        || !stored.orElseThrow().lines().equals(lines)
                        || !stored.orElseThrow().intent().equals(intent)) {
                    return false;
                }
                List<StockReservation> reservations =
                        runtime.stockReservations(request.requestId());
                return reservationStateMatches(
                        request, lines, reservations,
                        StockReservationState.HELD)
                        || reservationStateMatches(
                        request, lines, reservations,
                        StockReservationState.COMMITTED);
            }
            for (ServerShopOfferCartPreparedSavedData.QuotedLine line
                    : lines) {
                ServerShopOfferListing current = ShopCatalog.getOffer(
                        request.shopId(),
                        line.listing().listingId()).orElse(null);
                long now = Instant.now().getEpochSecond();
                AcquireOfferOption currentOption = current == null
                        ? null : current.acquireOptions().stream()
                        .filter(option -> option.optionId().equals(
                                line.optionId()))
                        .findFirst().orElse(null);
                if (current == null
                        || current.revision()
                        != line.listing().revision()
                        || !current.active()
                        || current.expiresAtEpoch() > 0L
                        && now >= current.expiresAtEpoch()
                        || !current.schedule().activeAt(now)
                        || !ServerShopOfferPermissionPolicy.allowed(
                        player, current.permissionNode())
                        || currentOption == null
                        || !currentOption.schedule().activeAt(now)
                        || !ServerShopOfferPermissionPolicy.allowed(
                        player, currentOption.permissionNode())) {
                    return false;
                }
            }
            if (ShopSessionManager.get(player.getUUID())
                    .filter(session -> session.shopId().equals(
                            request.shopId())).isEmpty()) {
                return false;
            }
            List<StockReservation> reservations =
                    runtime.stockReservations(request.requestId());
            return reservationStateMatches(
                    request, lines, reservations,
                    StockReservationState.HELD)
                    || reservationStateMatches(
                    request, lines, reservations,
                    StockReservationState.COMMITTED);
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
            return unavailable();
        }

        @Override
        public PlayerShopLiveEscrowService.StorageMutationResult insert(
                com.enviouse.futureshops.server.escrow.playershop
                        .PlayerShopStorageMutationPlan plan,
                ItemStack stack
        ) {
            return unavailable();
        }

        @Override
        public boolean applyBuybackCounter(
                PlayerShopEscrowIntent intent
        ) {
            return true;
        }

        private PlayerShopLiveEscrowService.StorageMutationResult
        unavailable() {
            return new PlayerShopLiveEscrowService.StorageMutationResult(
                    PlayerShopLiveEscrowService.StorageMutationStatus
                            .RECOVERY_REQUIRED,
                    "not applicable", "not applicable",
                    new byte[]{1},
                    "Server offer cart has no player shop storage");
        }
    }

    private static List<LineRequest> canonicalLines(
            List<LineRequest> requested
    ) {
        List<LineRequest> sorted = List.copyOf(
                Objects.requireNonNull(requested, "lines")).stream()
                .sorted(Comparator.comparing(LineRequest::listingId)
                        .thenComparing(LineRequest::optionId))
                .toList();
        if (sorted.isEmpty()
                || sorted.size()
                > ServerShopOfferCartCommit.MAXIMUM_LINES) {
            throw new IllegalArgumentException(
                    "Server shop offer cart line count is invalid");
        }
        List<LineRequest> merged = new ArrayList<>();
        for (LineRequest line : sorted) {
            if (!merged.isEmpty()) {
                LineRequest previous =
                        merged.get(merged.size() - 1);
                if (previous.listingId().equals(line.listingId())
                        && previous.optionId().equals(
                        line.optionId())) {
                    if (previous.expectedOfferRevision()
                            != line.expectedOfferRevision()) {
                        throw new IllegalArgumentException(
                                "Server shop offer cart revisions conflict");
                    }
                    merged.set(merged.size() - 1,
                            new LineRequest(
                                    previous.listingId(),
                                    previous.optionId(),
                                    Math.addExact(previous.quantity(),
                                            line.quantity()),
                                    previous.expectedOfferRevision()));
                    continue;
                }
            }
            merged.add(line);
        }
        return List.copyOf(merged);
    }

    private static String identifier(String value) {
        String normalized =
                Objects.requireNonNull(value, "identifier").strip();
        if (normalized.isEmpty() || normalized.length() > 160
                || !normalized.matches("[a-z0-9_.:/-]+")) {
            throw new IllegalArgumentException(
                    "Server shop offer cart identifier is invalid");
        }
        return normalized;
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA 256 is unavailable", exception);
        }
    }
}

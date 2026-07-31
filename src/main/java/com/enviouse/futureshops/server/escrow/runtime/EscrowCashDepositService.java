package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.Config;
import com.enviouse.futureshops.event.MoneyDepositEvent;
import com.enviouse.futureshops.init.ModItems;
import com.enviouse.futureshops.money.AtmCurrencyCatalog;
import com.enviouse.futureshops.money.CurrencyManager;
import com.enviouse.futureshops.money.InternalBillAuthorityRouter;
import com.enviouse.futureshops.money.InternalBillInventoryPlanner;
import com.enviouse.futureshops.money.PhysicalCurrencyAdapter;
import com.enviouse.futureshops.money.SpentMintsSavedData;
import com.enviouse.futureshops.server.economy.BalanceManager;
import com.enviouse.futureshops.server.economy.EconomyProvider;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintSavedData;
import com.enviouse.futureshops.server.escrow.claim.ClaimKind;
import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountId;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountType;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;
import com.enviouse.futureshops.server.escrow.model.EscrowOperation;
import com.enviouse.futureshops.server.escrow.model.EscrowAssetLotType;
import com.enviouse.futureshops.server.escrow.model.EscrowState;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;
import com.enviouse.futureshops.server.escrow.model.CashDepositMode;
import com.enviouse.futureshops.server.escrow.redemption.CashDepositEvidenceKeys;
import com.enviouse.futureshops.server.escrow.redemption.ProtectedCashInventoryState;
import com.enviouse.futureshops.server.escrow.redemption.ProtectedCashRedemptionEvidence;
import com.enviouse.futureshops.server.security.ServerRequestAction;
import com.enviouse.futureshops.server.security.ServerRequestSecurityManager;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.regex.Pattern;

public final class EscrowCashDepositService {
    public static final int MAX_ITEMS_CONSUMED = 131_072;
    public static final long MAX_RETRY_AFTER_MILLIS = 3_600_000_000L;

    private static final UUID ZERO_UUID = new UUID(0L, 0L);
    private static final Pattern CURRENCY_SIGNATURE =
            Pattern.compile("[0-9a-f]{64}");

    private EscrowCashDepositService() {
    }

    public static synchronized DepositResult deposit(
            ServerPlayer player,
            DepositRequest request
    ) {
        return depositInternal(player, request,
                CashDepositMode.PUBLIC_WALLET);
    }

    public static synchronized DepositResult depositForEscrow(
            ServerPlayer player,
            DepositRequest request
    ) {
        return depositInternal(player, request,
                CashDepositMode.INTERNAL_ESCROW);
    }

    public static synchronized Optional<DepositRecovery> recoverySnapshot(
            ServerPlayer player
    ) {
        Objects.requireNonNull(player, "player");
        EscrowRuntimeService runtime = runtimeFor(player);
        ActiveEvidence evidence = inspectActiveEvidence(player);
        if (evidence.corrupt() || evidence.identity().isEmpty()
                || evidence.identity().orElseThrow().depositMode()
                != CashDepositMode.PUBLIC_WALLET) {
            return Optional.empty();
        }
        RecoveryIdentity identity = evidence.identity().orElseThrow();
        if (!transactionIdForRequest(player.getUUID(), identity.requestId())
                .equals(identity.transactionId())) {
            return Optional.of(new DepositRecovery(
                    identity.requestId(), identity.transactionId(),
                    RecoveryStatus.MANUAL_REVIEW,
                    identity.amountMinorUnits()));
        }
        RecoveryStatus status = RecoveryStatus.RECOVERY_PENDING;
        if (runtime != null) {
            synchronized (runtime) {
                if (runtime.isReady()) {
                    evidence = attemptAutomaticRecovery(runtime, player,
                            evidence);
                    if (evidence.corrupt()) {
                        return Optional.of(new DepositRecovery(
                                identity.requestId(),
                                identity.transactionId(),
                                RecoveryStatus.MANUAL_REVIEW,
                                identity.amountMinorUnits()));
                    }
                    if (evidence.identity().isEmpty()) {
                        return Optional.empty();
                    }
                    identity = evidence.identity().orElseThrow();
                }
                Optional<EscrowTransaction> transaction =
                        runtime.transaction(identity.transactionId());
                if (transaction.isPresent()) {
                    EscrowTransaction stored = transaction.orElseThrow();
                    if (!matchesPublicRecovery(
                            stored, player.getUUID(), identity.requestId(),
                            identity.transactionId())) {
                        status = RecoveryStatus.MANUAL_REVIEW;
                    } else if (stored.state().isTerminal()) {
                        if (runtime.isReady()
                                && enqueueTransactionRecovery(
                                runtime, stored, player.getUUID())) {
                            recoverBounded(runtime);
                        }
                        if (!hasRawEvidence(player)) {
                            return Optional.empty();
                        }
                        status = runtime.failure().isPresent()
                                ? RecoveryStatus.MANUAL_REVIEW
                                : RecoveryStatus.RECOVERY_PENDING;
                    } else {
                        status = recoveryStatus(stored.state());
                    }
                }
            }
        }
        return Optional.of(new DepositRecovery(
                identity.requestId(), identity.transactionId(), status,
                identity.amountMinorUnits()));
    }

    public static synchronized DepositResult checkRecovery(
            ServerPlayer player,
            UUID requestId,
            UUID transactionId
    ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(transactionId, "transactionId");
        EscrowRuntimeService runtime = runtimeFor(player);
        DepositRequest request = recoveryRequest(requestId);
        if (!transactionIdForRequest(
                player.getUUID(), requestId).equals(transactionId)) {
            return failure(Status.REQUEST_CONFLICT, request,
                    Optional.of(transactionId), Optional.empty());
        }
        ServerRequestSecurityManager.GateDecision gate =
                ServerRequestSecurityManager.tryAcquire(
                        player, ServerRequestAction.ATM_DEPOSIT);
        if (!gate.allowed()) {
            return gateFailure(request, gate);
        }
        if (runtime == null) {
            return failure(Status.ESCROW_UNAVAILABLE, request,
                    Optional.empty(), Optional.empty());
        }
        synchronized (runtime) {
            return checkRecoveryLocked(player, requestId, transactionId,
                    request, runtime);
        }
    }

    private static DepositResult checkRecoveryLocked(
            ServerPlayer player,
            UUID requestId,
            UUID transactionId,
            DepositRequest request,
            EscrowRuntimeService runtime
    ) {
        ActiveEvidence evidence = inspectActiveEvidence(player);
        if (evidence.corrupt()) {
            return failure(Status.MANUAL_REVIEW, request,
                    Optional.of(transactionId), Optional.empty());
        }
        if (evidence.identity().isPresent()) {
            RecoveryIdentity identity = evidence.identity().orElseThrow();
            if (!identity.requestId().equals(requestId)
                    || !identity.transactionId().equals(transactionId)
                    || identity.depositMode()
                    != CashDepositMode.PUBLIC_WALLET) {
                return failure(Status.REQUEST_CONFLICT, request,
                        Optional.of(transactionId), Optional.empty());
            }
        }
        Optional<EscrowTransaction> stored = runtime.transaction(
                transactionId);
        if (stored.isPresent()) {
            EscrowTransaction transaction = stored.orElseThrow();
            if (!matchesPublicRecovery(
                    transaction, player.getUUID(),
                    requestId, transactionId)) {
                return failure(Status.REQUEST_CONFLICT, request,
                        Optional.of(transactionId), Optional.empty());
            }
            if (transaction.state().isTerminal()
                    || transaction.state() == EscrowState.MANUAL_REVIEW) {
                Optional<DepositResult> cleanupBlock =
                        terminalCleanupBlock(runtime, player, request,
                                transactionId, transaction);
                if (cleanupBlock.isPresent()) {
                    return cleanupBlock.orElseThrow();
                }
                return recoveryResult(
                        runtime, player, request, transactionId);
            }
        }
        if (!runtime.isReady()) {
            Status status = evidence.identity().isPresent()
                    || stored.isPresent()
                    ? Status.RECOVERY_PENDING
                    : Status.ESCROW_UNAVAILABLE;
            return failure(status, request,
                    status == Status.RECOVERY_PENDING
                            ? Optional.of(transactionId) : Optional.empty(),
                    Optional.empty());
        }
        if (stored.isEmpty()) {
            boolean queued = enqueueIntentRecovery(
                    runtime, player, evidence, transactionId);
            if (queued) {
                recoverBounded(runtime);
            }
            stored = runtime.transaction(transactionId);
            if (stored.isEmpty() && !queued) {
                return failure(Status.MANUAL_REVIEW, request,
                        Optional.of(transactionId), Optional.empty());
            }
        }
        if (stored.isEmpty()) {
            Status status = evidence.identity().isPresent()
                    ? Status.RECOVERY_PENDING : Status.MANUAL_REVIEW;
            return failure(status, request, Optional.of(transactionId),
                    Optional.empty());
        }
        EscrowTransaction transaction = stored.orElseThrow();
        if (!transaction.state().isTerminal()
                && transaction.state() != EscrowState.MANUAL_REVIEW) {
            if (!enqueueTransactionRecovery(
                    runtime, transaction, player.getUUID())) {
                return failure(Status.MANUAL_REVIEW, request,
                        Optional.of(transactionId), Optional.empty());
            }
            recoverBounded(runtime);
        }
        return recoveryResult(runtime, player, request, transactionId);
    }

    public static EscrowClaim requireInternalEscrowFundingClaim(
            ServerPlayer player,
            DepositResult result
    ) {
        EscrowClaim claim = requireInternalEscrowFundingClaimEvidence(
                player, result);
        if (claim.status() != ClaimStatus.PENDING
                || claim.remainingUnits() != claim.originalUnits()) {
            throw new EscrowRuntimeException(
                    "Internal cash funding claim is not pending");
        }
        return claim;
    }

    public static EscrowClaim requireInternalEscrowFundingClaimEvidence(
            ServerPlayer player,
            DepositResult result
    ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(result, "result");
        UUID transactionId = result.transactionId().orElseThrow();
        if (!result.successful() || result.walletCreditMinorUnits() != 0L
                || result.overflowClaimMinorUnits()
                != result.depositedMinorUnits()) {
            throw new EscrowRuntimeException(
                    "Internal cash funding result is invalid");
        }
        EscrowRuntimeService runtime = EscrowRuntimeManager.requireReady(
                player.getServer());
        List<EscrowClaim> matches;
        synchronized (runtime) {
            matches = runtime.claimsForTransaction(transactionId).stream()
                    .filter(claim -> claim.ownerId().equals(
                            player.getUUID()))
                    .filter(claim -> claim.transactionId().equals(
                            transactionId))
                    .filter(claim -> claim.kind()
                            == ClaimKind.INTERNAL_ESCROW_MONEY)
                    .filter(claim -> claim.originalUnits()
                            == result.depositedMinorUnits())
                    .filter(claim -> (claim.status()
                            == ClaimStatus.PENDING
                            && claim.remainingUnits()
                            == result.depositedMinorUnits())
                            || (claim.status() == ClaimStatus.COMPLETED
                            && claim.remainingUnits() == 0L))
                    .toList();
        }
        if (matches.size() != 1) {
            throw new EscrowRuntimeException(
                    "Internal cash funding claim is missing or ambiguous");
        }
        return matches.get(0);
    }

    public static Optional<UUID> serverShopPurchaseBinding(
            EscrowTransaction transaction
    ) {
        Objects.requireNonNull(transaction, "transaction");
        if (transaction.operation() != EscrowOperation.CURRENCY_DEPOSIT
                || transaction.state() != EscrowState.COMPLETED) {
            return Optional.empty();
        }
        String key = transaction.requestKey().value();
        int marker = key.lastIndexOf(".shop.");
        if (marker < 0 || marker + 6 + 36 != key.length()) {
            return Optional.empty();
        }
        try {
            UUID purchaseRequestId = UUID.fromString(
                    key.substring(marker + 6));
            String prefix = key.substring(0, marker);
            if (!prefix.startsWith("cash.deposit.")) {
                return Optional.empty();
            }
            int requestEnd = prefix.indexOf('.', "cash.deposit.".length());
            if (requestEnd < 0) {
                return Optional.empty();
            }
            UUID depositRequestId = UUID.fromString(prefix.substring(
                    "cash.deposit.".length(), requestEnd));
            String fingerprint = prefix.substring(requestEnd + 1);
            if (!CURRENCY_SIGNATURE.matcher(fingerprint).matches()
                    || !depositRequestId.equals(
                    ServerShopFundingRelease.fundingRequestId(
                            purchaseRequestId))) {
                return Optional.empty();
            }
            return Optional.of(purchaseRequestId);
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static DepositResult depositInternal(
            ServerPlayer player,
            DepositRequest request,
            CashDepositMode requiredMode
    ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(requiredMode, "requiredMode");
        if (request.mode() != requiredMode) {
            throw new IllegalArgumentException(
                    "Cash deposit mode does not match its entrypoint");
        }
        UUID transactionId = transactionIdForRequest(player.getUUID(),
                request.requestId());
        ServerRequestSecurityManager.GateDecision gate =
                ServerRequestSecurityManager.tryAcquire(
                        player, ServerRequestAction.ATM_DEPOSIT);
        if (!gate.allowed()) {
            return gateFailure(request, gate);
        }
        EscrowRuntimeService runtime = runtimeFor(player);
        if (runtime == null) {
            return failure(Status.ESCROW_UNAVAILABLE, request,
                    Optional.empty(), Optional.empty());
        }
        synchronized (runtime) {
            return depositLocked(player, request, requiredMode,
                    transactionId, runtime);
        }
    }

    private static DepositResult depositLocked(
            ServerPlayer player,
            DepositRequest request,
            CashDepositMode requiredMode,
            UUID transactionId,
            EscrowRuntimeService runtime
    ) {
        if (!runtime.isReady()) {
            return failure(Status.ESCROW_UNAVAILABLE, request,
                    Optional.empty(), Optional.empty());
        }
        DepositResult result;
        try {
            Optional<DepositResult> replay = replay(runtime, player,
                    request, transactionId);
            if (replay.isPresent()) {
                return replay.orElseThrow();
            }
            result = withConfigurationReadLease(
                    () -> depositWithConfigurationLease(
                            runtime, player, request, transactionId,
                            requiredMode));
        } catch (ForeignCashDepositWorkflow
                .ConfigurationChangedException exception) {
            result = resolveAfterFailure(runtime, player, request,
                    transactionId, Status.CONFIG_CHANGED,
                    Optional.of(transactionId));
        } catch (CashDepositCancellationCompletedException exception) {
            result = resolveAfterFailure(runtime, player, request,
                    transactionId, Status.CANCELLED,
                    Optional.of(exception.transactionId()));
        } catch (EscrowRuntimeException exception) {
            result = resolveAfterFailure(runtime, player, request,
                    transactionId, Status.RECOVERY_REQUIRED,
                    Optional.of(transactionId));
        } catch (ArithmeticException | IllegalStateException exception) {
            result = resolveAfterFailure(runtime, player, request,
                    transactionId, Status.INVALID_CURRENCY,
                    Optional.empty());
        } catch (RuntimeException exception) {
            result = resolveAfterFailure(runtime, player, request,
                    transactionId, Status.ESCROW_UNAVAILABLE,
                    Optional.empty());
        }
        if (result.successful() && !result.replayed()) {
            publishDepositEvent(player, result);
        }
        return result;
    }

    static <T> T withConfigurationReadLease(Supplier<T> operation) {
        Objects.requireNonNull(operation, "operation");
        try (CurrencyManager.ConfigurationReadLease ignored =
                     CurrencyManager.acquireConfigurationReadLease()) {
            return operation.get();
        }
    }

    private static EscrowRuntimeService runtimeFor(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        return server == null ? null : EscrowRuntimeManager.getOrNull(server);
    }

    private static DepositResult depositWithConfigurationLease(
            EscrowRuntimeService runtime,
            ServerPlayer player,
            DepositRequest request,
            UUID transactionId,
            CashDepositMode mode
    ) {
        PhysicalCurrencyAdapter adapter = CurrencyManager.getOrNull();
        if (adapter == null) {
            return failure(Status.ESCROW_UNAVAILABLE, request,
                    Optional.empty(), Optional.empty());
        }
        EconomyProvider economy = BalanceManager.getProvider();
        AtmCurrencyCatalog catalog = AtmCurrencyCatalog.capture(
                adapter, economy);
        if (!request.currencySignature().equals(catalog.signature())) {
            return failure(Status.CONFIG_CHANGED, request,
                    Optional.empty(), Optional.empty());
        }
        long walletBalanceLimitMinorUnits = walletBalanceLimit(
                mode == CashDepositMode.INTERNAL_ESCROW,
                economy.getBalance(player.getUUID()),
                Config.economyMaxBalanceMinorUnits);
        long protectedConfigurationRevision =
                configurationRevision(adapter);
        try {
            economy.getBalance(player.getUUID());
        } catch (RuntimeException exception) {
            return resolveAfterFailure(runtime, player, request,
                    transactionId, Status.ESCROW_UNAVAILABLE,
                    Optional.empty());
        }
        if (request.requestedMinorUnits().isPresent()
                && request.requestedMinorUnits().getAsLong() <= 0L) {
            return failure(Status.INVALID_AMOUNT, request,
                    Optional.empty(), Optional.empty());
        }
        if (player.getAbilities().instabuild) {
            return failure(Status.CREATIVE_BLOCKED, request,
                    Optional.empty(), Optional.empty());
        }
        ActiveEvidence activeEvidence = inspectActiveEvidence(player);
        if (activeEvidence.corrupt()) {
            return failure(Status.ESCROW_UNAVAILABLE, request,
                    Optional.empty(), Optional.empty());
        }
        if (activeEvidence.identity().isPresent()) {
            RecoveryIdentity identity =
                    activeEvidence.identity().orElseThrow();
            if (!identity.requestId().equals(request.requestId())) {
                activeEvidence = attemptAutomaticRecovery(
                        runtime, player, activeEvidence);
                if (activeEvidence.corrupt()) {
                    return failure(Status.ESCROW_UNAVAILABLE, request,
                            Optional.empty(), Optional.empty());
                }
            }
        }
        if (activeEvidence.identity().isPresent()) {
            return failure(Status.RECOVERY_REQUIRED, request,
                    Optional.of(activeEvidence.identity().orElseThrow()
                            .transactionId()), Optional.empty());
        }
        return adapter.isInternal()
                ? depositProtected(runtime, player, request,
                transactionId, protectedConfigurationRevision,
                walletBalanceLimitMinorUnits, mode)
                : depositForeign(runtime, player, request,
                transactionId, adapter,
                walletBalanceLimitMinorUnits, mode);
    }

    static long walletBalanceLimit(
            boolean claimOnly,
            long currentBalanceMinorUnits,
            long configuredLimitMinorUnits
    ) {
        if (configuredLimitMinorUnits < 0L
                || !claimOnly && (currentBalanceMinorUnits < 0L
                || configuredLimitMinorUnits < currentBalanceMinorUnits)) {
            throw new IllegalArgumentException(
                    "Cash deposit wallet limit is invalid");
        }
        return claimOnly ? 0L
                : configuredLimitMinorUnits;
    }

    public static DepositRequest requestForCurrentState(
            ServerPlayer player,
            Source source,
            OptionalLong requestedMinorUnits
    ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(requestedMinorUnits,
                "requestedMinorUnits");
        try (CurrencyManager.ConfigurationReadLease ignored =
                     CurrencyManager.acquireConfigurationReadLease()) {
            PhysicalCurrencyAdapter adapter = CurrencyManager.get();
            AtmCurrencyCatalog catalog = AtmCurrencyCatalog.capture(
                    adapter, BalanceManager.getProvider());
            return new DepositRequest(requestIdForCurrentState(
                    player, source, requestedMinorUnits,
                    catalog.signature()), catalog.signature(), source,
                    requestedMinorUnits);
        }
    }

    static UUID requestIdForCurrentState(
            ServerPlayer player,
            Source source,
            OptionalLong requestedMinorUnits,
            String currencySignature
    ) {
        Objects.requireNonNull(currencySignature, "currencySignature");
        ProtectedCashInventoryState inventory =
                ProtectedCashInventoryState.capture(player.getInventory());
        String amount = requestedMinorUnits.isPresent()
                ? Long.toString(requestedMinorUnits.getAsLong()) : "all";
        String material = "futureshops cash deposit request v2 "
                + player.getUUID() + " " + source + " " + amount + " "
                + currencySignature + " "
                + player.serverLevel().getGameTime() + " "
                + HexFormat.of().formatHex(inventory.hash());
        return UUID.nameUUIDFromBytes(material.getBytes(
                StandardCharsets.UTF_8));
    }

    private static DepositResult depositProtected(
            EscrowRuntimeService runtime,
            ServerPlayer player,
            DepositRequest request,
            UUID transactionId,
            long protectedConfigurationRevision,
            long walletBalanceLimitMinorUnits,
            CashDepositMode mode
    ) {
        InternalBillInventoryPlanner planner =
                new InternalBillInventoryPlanner(
                        ProtectedMintSavedData.get(player.getServer()),
                        SpentMintsSavedData.get(player.getServer()));
        InternalBillInventoryPlanner.SlotIdentity onlySlot =
                sourceSlot(player, request.source());
        InternalBillInventoryPlanner.InventoryFacts facts =
                planner.inspect(player, onlySlot);
        Optional<LegacyMigrationFacts> legacy =
                facts.legacyAvailableMinorUnits() > 0L
                        ? Optional.of(legacyFacts(facts))
                        : Optional.empty();
        if (facts.crossStoreCollision()) {
            return failure(Status.INVALID_CURRENCY, request,
                    Optional.empty(), Optional.empty());
        }
        long amount;
        if (request.requestedMinorUnits().isEmpty()) {
            if (facts.legacyAvailableMinorUnits() > 0L) {
                return failure(Status.LEGACY_MIGRATION_REQUIRED, request,
                        Optional.empty(), legacy);
            }
            amount = facts.protectedAvailableMinorUnits();
            if (amount <= 0L) {
                return failure(Status.NO_CURRENCY, request,
                        Optional.empty(), Optional.empty());
            }
        } else {
            amount = request.requestedMinorUnits().getAsLong();
        }
        InternalBillInventoryPlanner.ExactPlan plan = onlySlot == null
                ? planner.planExact(player, amount)
                : planner.planExactSlot(player, onlySlot, amount);
        if (plan.successful()
                && plan.authority()
                == InternalBillAuthorityRouter.Authority.LEGACY) {
            return failure(Status.LEGACY_MIGRATION_REQUIRED, request,
                    Optional.empty(), legacy);
        }
        if (!plan.successful()
                || plan.authority()
                != InternalBillAuthorityRouter.Authority.PROTECTED) {
            long available = Math.addExact(
                    facts.protectedAvailableMinorUnits(),
                    facts.legacyAvailableMinorUnits());
            Status status = amount > available
                    ? Status.NOT_ENOUGH_CURRENCY
                    : facts.legacyAvailableMinorUnits() > 0L
                    ? Status.LEGACY_MIGRATION_REQUIRED
                    : Status.INVALID_DENOMINATION;
            return failure(status, request, Optional.empty(),
                    status == Status.LEGACY_MIGRATION_REQUIRED
                            ? legacy : Optional.empty());
        }
        OptionalInt itemCount = boundedItemCount(plan.portions(),
                InternalBillInventoryPlanner.Portion::selectedCount);
        if (itemCount.isEmpty()) {
            return failure(Status.TOO_MANY_ITEMS, request,
                    Optional.empty(), Optional.empty());
        }
        ProtectedCashRedemptionResult terminal = runtime.redeemProtectedCash(
                player, plan, transactionId, requestKey(player, request),
                protectedConfigurationRevision,
                walletBalanceLimitMinorUnits, mode, Instant.now());
        return success(runtime, player, request, terminal.transactionId(),
                terminal.depositedMinorUnits(), itemCount.getAsInt(),
                terminal.walletCreditMinorUnits(),
                terminal.overflowClaimMinorUnits(),
                terminal.cleanupPending());
    }

    private static DepositResult depositForeign(
            EscrowRuntimeService runtime,
            ServerPlayer player,
            DepositRequest request,
            UUID transactionId,
            PhysicalCurrencyAdapter adapter,
            long walletBalanceLimitMinorUnits,
            CashDepositMode mode
    ) {
        InternalBillInventoryPlanner.SlotIdentity onlySlot =
                sourceSlot(player, request.source());
        if (onlySlot != null) {
            ItemStack held = stack(player, onlySlot);
            if (held.getItem() == ModItems.MONEY_ITEM.get()) {
                return failure(Status.WRONG_PROVIDER, request,
                        Optional.empty(), Optional.empty());
            }
        }
        long available;
        try {
            available = ForeignCashDepositPlan.availableValue(adapter,
                    player.getInventory().items,
                    player.getInventory().offhand, onlySlot);
        } catch (IllegalArgumentException exception) {
            return failure(Status.INVALID_CURRENCY, request,
                    Optional.empty(), Optional.empty());
        }
        if (available <= 0L) {
            return failure(Status.NO_CURRENCY, request,
                    Optional.empty(), Optional.empty());
        }
        long amount = request.requestedMinorUnits().orElse(available);
        if (amount > available) {
            return failure(Status.NOT_ENOUGH_CURRENCY, request,
                    Optional.empty(), Optional.empty());
        }
        ForeignCashDepositPlan plan;
        try {
            plan = ForeignCashDepositPlan.select(adapter,
                    player.getInventory().items,
                    player.getInventory().offhand, amount, onlySlot);
        } catch (ForeignCashDepositPlan.NoExactSelectionException exception) {
            return failure(Status.INVALID_DENOMINATION, request,
                    Optional.empty(), Optional.empty());
        } catch (IllegalArgumentException exception) {
            return failure(Status.INVALID_CURRENCY, request,
                    Optional.empty(), Optional.empty());
        }
        OptionalInt itemCount = boundedItemCount(plan.portions(),
                ForeignCashDepositPlan.Portion::selectedCount);
        if (itemCount.isEmpty()) {
            return failure(Status.TOO_MANY_ITEMS, request,
                    Optional.empty(), Optional.empty());
        }
        ForeignCashDepositResult terminal = runtime.redeemForeignCash(
                player, plan, request.requestId(), transactionId,
                requestKey(player, request),
                walletBalanceLimitMinorUnits, mode, Instant.now());
        return success(runtime, player, request, terminal.transactionId(),
                terminal.depositedMinorUnits(), itemCount.getAsInt(),
                terminal.walletCreditMinorUnits(),
                terminal.overflowClaimMinorUnits(),
                terminal.cleanupPending());
    }

    private static DepositResult success(
            EscrowRuntimeService runtime,
            ServerPlayer player,
            DepositRequest request,
            UUID transactionId,
            long amount,
            int items,
            long walletCredit,
            long overflowClaim,
            boolean cleanupPending
    ) {
        long balance = runtime.ledgerBalance(playerWallet(player.getUUID()));
        return new DepositResult(Status.SUCCESS, request.requestId(),
                Optional.of(transactionId), amount, items, walletCredit,
                overflowClaim, 0L, RefundDestination.NONE,
                balance, cleanupPending, false,
                Optional.empty(), 0L);
    }

    private static void publishDepositEvent(
            ServerPlayer player,
            DepositResult result
    ) {
        try {
            MinecraftForge.EVENT_BUS.post(new MoneyDepositEvent(
                    player.getUUID(), result.depositedMinorUnits(),
                    result.itemsConsumed()));
        } catch (RuntimeException ignored) {
        }
    }

    private static DepositResult failure(
            Status status,
            DepositRequest request,
            Optional<UUID> transactionId,
            Optional<LegacyMigrationFacts> legacy
    ) {
        return failure(status, request, transactionId, legacy, 0L);
    }

    private static DepositResult failure(
            Status status,
            DepositRequest request,
            Optional<UUID> transactionId,
            Optional<LegacyMigrationFacts> legacy,
            long retryAfterMillis
    ) {
        return new DepositResult(status, request.requestId(), transactionId,
                0L, 0, 0L, 0L, 0L, RefundDestination.NONE,
                0L, false, false, legacy,
                retryAfterMillis);
    }

    static DepositResult gateFailure(
            DepositRequest request,
            ServerRequestSecurityManager.GateDecision gate
    ) {
        if (gate.status()
                == ServerRequestSecurityManager.GateStatus.RATE_LIMITED) {
            return failure(Status.RATE_LIMITED, request, Optional.empty(),
                    Optional.empty(), retryAfterMillis(gate));
        }
        return failure(Status.ESCROW_UNAVAILABLE, request,
                Optional.empty(), Optional.empty());
    }

    private static Optional<DepositResult> replay(
            EscrowRuntimeService runtime,
            ServerPlayer player,
            DepositRequest request,
            UUID transactionId
    ) {
        Optional<EscrowTransaction> stored = runtime.transaction(
                transactionId);
        String expectedKey = requestKey(player, request);
        ReplayDisposition disposition = classifyReplay(stored, expectedKey,
                legacyRequestKey(player.getUUID(), request));
        switch (disposition) {
            case ABSENT -> {
                return Optional.empty();
            }
            case REQUEST_CONFLICT -> {
                return Optional.of(failure(Status.REQUEST_CONFLICT, request,
                        Optional.of(transactionId), Optional.empty()));
            }
            case CANCELLED -> {
                Optional<DepositResult> cleanupBlock =
                        terminalCleanupBlock(
                                runtime, player, request, transactionId,
                                stored.orElseThrow());
                if (cleanupBlock.isPresent()) {
                    return cleanupBlock;
                }
                return Optional.of(failure(Status.CANCELLED, request,
                        Optional.of(transactionId), Optional.empty()));
            }
            case RECOVERY_REQUIRED -> {
                EscrowTransaction transaction = stored.orElseThrow();
                if (matchesPublicRecovery(transaction, player.getUUID(),
                        request.requestId(), transactionId)
                        && transaction.state()
                        != EscrowState.MANUAL_REVIEW) {
                    if (!enqueueTransactionRecovery(
                            runtime, transaction, player.getUUID())) {
                        DepositResult current = recoveryResult(
                                runtime, player, request, transactionId);
                        return Optional.of(current.status()
                                == Status.RECOVERY_PENDING
                                ? failure(Status.MANUAL_REVIEW, request,
                                Optional.of(transactionId), Optional.empty())
                                : current);
                    }
                    recoverBounded(runtime);
                }
                return Optional.of(recoveryResult(
                        runtime, player, request, transactionId));
            }
            case COMPLETED -> {
            }
        }
        EscrowTransaction transaction = stored.orElseThrow();
        Optional<DepositResult> cleanupBlock = terminalCleanupBlock(
                runtime, player, request, transactionId, transaction);
        if (cleanupBlock.isPresent()) {
            return cleanupBlock;
        }
        return Optional.of(completedReplayFromRuntime(runtime, player,
                request, transactionId, transaction));
    }

    private static DepositResult completedReplayFromRuntime(
            EscrowRuntimeService runtime,
            ServerPlayer player,
            DepositRequest request,
            UUID transactionId,
            EscrowTransaction transaction
    ) {
        LedgerTransaction ledger = runtime.ledgerTransaction(transactionId)
                .orElseThrow(() -> new EscrowRuntimeException(
                        "Cash deposit replay ledger is missing"));
        List<EscrowClaim> claims = runtime.claimsForTransaction(
                transactionId);
        long balance = runtime.ledgerBalance(playerWallet(player.getUUID()));
        return completedReplay(request, transactionId,
                transaction, ledger, claims, player.getUUID(), balance,
                hasRawEvidence(player));
    }

    private static DepositResult resolveAfterFailure(
            EscrowRuntimeService runtime,
            ServerPlayer player,
            DepositRequest request,
            UUID transactionId,
            Status fallbackStatus,
            Optional<UUID> fallbackTransactionId
    ) {
        try {
            Optional<EscrowTransaction> stored = runtime.transaction(
                    transactionId);
            ReplayDisposition disposition = classifyReplay(stored,
                    requestKey(player, request),
                    legacyRequestKey(player.getUUID(), request));
            return resolveFailureDisposition(request, transactionId,
                    disposition, fallbackStatus, fallbackTransactionId,
                    () -> completedReplayFromRuntime(runtime, player,
                            request, transactionId,
                            stored.orElseThrow()));
        } catch (RuntimeException exception) {
            if (fallbackStatus == Status.CANCELLED
                    && fallbackTransactionId.isPresent()) {
                return failure(Status.CANCELLED, request,
                        fallbackTransactionId, Optional.empty());
            }
            return failure(Status.RECOVERY_REQUIRED, request,
                    Optional.of(transactionId), Optional.empty());
        }
    }

    static DepositResult resolveFailureDisposition(
            DepositRequest request,
            UUID transactionId,
            ReplayDisposition disposition,
            Status fallbackStatus,
            Optional<UUID> fallbackTransactionId,
            Supplier<DepositResult> completedReplay
    ) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(disposition, "disposition");
        Objects.requireNonNull(fallbackStatus, "fallbackStatus");
        Objects.requireNonNull(fallbackTransactionId,
                "fallbackTransactionId");
        Objects.requireNonNull(completedReplay, "completedReplay");
        return switch (disposition) {
            case ABSENT -> failure(fallbackStatus, request,
                    fallbackTransactionId, Optional.empty());
            case REQUEST_CONFLICT -> failure(Status.REQUEST_CONFLICT,
                    request, Optional.of(transactionId), Optional.empty());
            case CANCELLED -> failure(Status.CANCELLED, request,
                    Optional.of(transactionId), Optional.empty());
            case RECOVERY_REQUIRED -> failure(Status.RECOVERY_REQUIRED,
                    request, Optional.of(transactionId), Optional.empty());
            case COMPLETED -> {
                try {
                    yield completedReplay.get();
                } catch (RuntimeException exception) {
                    yield failure(Status.RECOVERY_REQUIRED, request,
                            Optional.of(transactionId), Optional.empty());
                }
            }
        };
    }

    static DepositResult completedReplay(
            DepositRequest request,
            UUID transactionId,
            EscrowTransaction transaction,
            LedgerTransaction ledger,
            List<EscrowClaim> claims,
            UUID playerId,
            long resultingBalanceMinorUnits,
            boolean cleanupPending
    ) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(transaction, "transaction");
        Objects.requireNonNull(ledger, "ledger");
        claims = List.copyOf(Objects.requireNonNull(claims, "claims"));
        Objects.requireNonNull(playerId, "playerId");
        if (transaction.state() != EscrowState.COMPLETED
                || transaction.operation()
                != EscrowOperation.CURRENCY_DEPOSIT
                || !transaction.transactionId().value().equals(transactionId)
                || !ledger.transactionId().equals(transactionId)) {
            throw new EscrowRuntimeException(
                    "Cash deposit replay terminal identity is invalid");
        }
        long amount = transaction.assetLots().stream()
                .map(asset -> asset.money().orElseThrow().minorUnits())
                .reduce(0L, Math::addExact);
        int items = Math.toIntExact(transaction.assetLots().stream()
                .mapToLong(asset -> asset.quantity())
                .reduce(0L, Math::addExact));
        long walletCredit = ledger.legs().stream()
                .filter(leg -> leg.account().type()
                        == LedgerAccountType.PLAYER_WALLET
                        && leg.account().ownerKey().equals(
                        playerId.toString()))
                .map(leg -> leg.deltaMinor())
                .reduce(0L, Math::addExact);
        ClaimKind expectedClaimKind = request.mode()
                == CashDepositMode.INTERNAL_ESCROW
                ? ClaimKind.INTERNAL_ESCROW_MONEY : ClaimKind.MONEY;
        boolean conflictingMoneyClaim = claims.stream().anyMatch(claim ->
                claim.ownerId().equals(playerId)
                        && claim.transactionId().equals(transactionId)
                        && (claim.kind() == ClaimKind.MONEY
                        || claim.kind()
                        == ClaimKind.INTERNAL_ESCROW_MONEY)
                        && claim.kind() != expectedClaimKind);
        long overflow = claims.stream()
                .filter(claim -> claim.kind() == expectedClaimKind
                        && claim.ownerId().equals(playerId)
                        && claim.transactionId().equals(transactionId))
                .map(EscrowClaim::originalUnits)
                .reduce(0L, Math::addExact);
        boolean legacyPublicEvidence = request.mode()
                == CashDepositMode.PUBLIC_WALLET
                && legacyRequestKey(playerId, request).filter(value ->
                transaction.requestKey().value().equals(value)).isPresent();
        boolean modeEvidence = transaction.assetLots().stream()
                .allMatch(asset -> request.mode().name().equals(
                        asset.attributes().get("deposit_mode"))
                        || legacyPublicEvidence
                        && !asset.attributes().containsKey("deposit_mode"));
        if (amount <= 0L || items <= 0
                || items > MAX_ITEMS_CONSUMED || walletCredit < 0L
                || overflow < 0L
                || conflictingMoneyClaim || !modeEvidence
                || Math.addExact(walletCredit, overflow) != amount) {
            throw new EscrowRuntimeException(
                    "Cash deposit replay evidence does not conserve");
        }
        return new DepositResult(Status.SUCCESS,
                request.requestId(), Optional.of(transactionId), amount,
                items, walletCredit, overflow, 0L, RefundDestination.NONE,
                resultingBalanceMinorUnits,
                cleanupPending, true, Optional.empty(), 0L);
    }

    static ReplayDisposition classifyReplay(
            Optional<EscrowTransaction> stored,
            String expectedRequestKey
    ) {
        return classifyReplay(stored, expectedRequestKey, Optional.empty());
    }

    static ReplayDisposition classifyReplay(
            Optional<EscrowTransaction> stored,
            String expectedRequestKey,
            Optional<String> legacyRequestKey
    ) {
        Objects.requireNonNull(stored, "stored");
        Objects.requireNonNull(expectedRequestKey,
                "expectedRequestKey");
        Objects.requireNonNull(legacyRequestKey, "legacyRequestKey");
        if (stored.isEmpty()) {
            return ReplayDisposition.ABSENT;
        }
        EscrowTransaction transaction = stored.orElseThrow();
        if (transaction.operation() != EscrowOperation.CURRENCY_DEPOSIT
                || !transaction.requestKey().value().equals(
                expectedRequestKey)
                && legacyRequestKey.stream().noneMatch(value ->
                transaction.requestKey().value().equals(value))) {
            return ReplayDisposition.REQUEST_CONFLICT;
        }
        return switch (transaction.state()) {
            case COMPLETED -> ReplayDisposition.COMPLETED;
            case REFUNDED -> ReplayDisposition.CANCELLED;
            default -> ReplayDisposition.RECOVERY_REQUIRED;
        };
    }

    static <T> OptionalInt boundedItemCount(
            List<T> values,
            ToIntFunction<T> counter
    ) {
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(counter, "counter");
        int total = 0;
        try {
            for (T value : values) {
                int count = counter.applyAsInt(value);
                if (count <= 0) {
                    return OptionalInt.empty();
                }
                total = Math.addExact(total, count);
                if (total > MAX_ITEMS_CONSUMED) {
                    return OptionalInt.empty();
                }
            }
        } catch (ArithmeticException exception) {
            return OptionalInt.empty();
        }
        return total > 0 ? OptionalInt.of(total) : OptionalInt.empty();
    }

    private static LedgerAccountId playerWallet(UUID playerId) {
        return new LedgerAccountId(LedgerAccountType.PLAYER_WALLET,
                playerId.toString());
    }

    private static long retryAfterMillis(
            ServerRequestSecurityManager.GateDecision gate
    ) {
        long nanos;
        try {
            nanos = gate.retryAfter().toNanos();
        } catch (ArithmeticException exception) {
            return MAX_RETRY_AFTER_MILLIS;
        }
        if (nanos <= 0L) {
            return 1L;
        }
        long maximumNanos = Math.multiplyExact(
                MAX_RETRY_AFTER_MILLIS, 1_000_000L);
        if (nanos >= maximumNanos) {
            return MAX_RETRY_AFTER_MILLIS;
        }
        return Math.addExact(
                Math.subtractExact(nanos, 1L) / 1_000_000L, 1L);
    }

    private static InternalBillInventoryPlanner.SlotIdentity sourceSlot(
            ServerPlayer player,
            Source source
    ) {
        return switch (source) {
            case INVENTORY -> null;
            case MAIN_HAND -> new InternalBillInventoryPlanner.SlotIdentity(
                    InternalBillInventoryPlanner.Container.MAIN,
                    player.getInventory().selected);
            case OFF_HAND -> new InternalBillInventoryPlanner.SlotIdentity(
                    InternalBillInventoryPlanner.Container.OFFHAND, 0);
        };
    }

    private static ItemStack stack(
            ServerPlayer player,
            InternalBillInventoryPlanner.SlotIdentity slot
    ) {
        return slot.container() == InternalBillInventoryPlanner.Container.MAIN
                ? player.getInventory().items.get(slot.index())
                : player.getInventory().offhand.get(slot.index());
    }

    private static ActiveEvidence inspectActiveEvidence(
            ServerPlayer player
    ) {
        Tag protectedRaw = player.getPersistentData().get(
                CashDepositEvidenceKeys.PROTECTED);
        Tag foreignRaw = player.getPersistentData().get(
                CashDepositEvidenceKeys.FOREIGN);
        if (protectedRaw != null && foreignRaw != null) {
            return ActiveEvidence.corruptEvidence();
        }
        try {
            if (protectedRaw != null) {
                if (!(protectedRaw instanceof ByteArrayTag bytes)) {
                    return ActiveEvidence.corruptEvidence();
                }
                ProtectedCashRedemptionEvidence evidence =
                        ProtectedCashRedemptionEvidence.decode(
                                bytes.getAsByteArray());
                if (!evidence.playerId().equals(player.getUUID())) {
                    return ActiveEvidence.corruptEvidence();
                }
                UUID requestId = requestId(
                        evidence.reservation().heldTransaction())
                        .orElse(null);
                return requestId == null
                        ? ActiveEvidence.corruptEvidence()
                        : ActiveEvidence.recovery(new RecoveryIdentity(
                        requestId, evidence.transactionId(),
                        evidence.reservation().amountMinorUnits(),
                        evidence.reservation().depositMode(),
                        evidence.phase()
                                == ProtectedCashRedemptionEvidence.Phase
                                .INTENT));
            }
            if (foreignRaw != null) {
                if (!(foreignRaw instanceof ByteArrayTag bytes)) {
                    return ActiveEvidence.corruptEvidence();
                }
                ForeignCashDepositEvidence evidence =
                        ForeignCashDepositEvidence.decode(
                                bytes.getAsByteArray());
                return evidence.playerId().equals(player.getUUID())
                        ? ActiveEvidence.recovery(new RecoveryIdentity(
                        evidence.reservation().requestId(),
                        evidence.transactionId(),
                        evidence.reservation().amountMinorUnits(),
                        evidence.reservation().depositMode(),
                        evidence.phase()
                                == ForeignCashDepositEvidence.Phase.INTENT))
                        : ActiveEvidence.corruptEvidence();
            }
            return ActiveEvidence.none();
        } catch (RuntimeException exception) {
            return ActiveEvidence.corruptEvidence();
        }
    }

    private static DepositRequest recoveryRequest(UUID requestId) {
        return new DepositRequest(requestId, "0".repeat(64),
                Source.INVENTORY, OptionalLong.empty(),
                CashDepositMode.PUBLIC_WALLET);
    }

    private static boolean enqueueIntentRecovery(
            EscrowRuntimeService runtime,
            ServerPlayer player,
            ActiveEvidence evidence,
            UUID transactionId
    ) {
        if (evidence.identity().isEmpty()
                || !evidence.identity().orElseThrow().intent()
                || !evidence.identity().orElseThrow().transactionId()
                .equals(transactionId)) {
            return false;
        }
        Tag protectedRaw = player.getPersistentData().get(
                CashDepositEvidenceKeys.PROTECTED);
        Tag foreignRaw = player.getPersistentData().get(
                CashDepositEvidenceKeys.FOREIGN);
        try {
            if (protectedRaw instanceof ByteArrayTag bytes) {
                return runtime.enqueueProtectedCashIntentRecovery(
                        ProtectedCashRedemptionEvidence.decode(
                                bytes.getAsByteArray()))
                        == CashDepositRecoveryEnqueueResult.QUEUED;
            }
            if (foreignRaw instanceof ByteArrayTag bytes) {
                return runtime.enqueueForeignCashIntentRecovery(
                        ForeignCashDepositEvidence.decode(
                                bytes.getAsByteArray()))
                        == CashDepositRecoveryEnqueueResult.QUEUED;
            }
        } catch (RuntimeException ignored) {
            return false;
        }
        return false;
    }

    private static boolean enqueueTransactionRecovery(
            EscrowRuntimeService runtime,
            EscrowTransaction transaction,
            UUID playerId
    ) {
        try {
            if (transaction.assetLots().stream().anyMatch(lot ->
                    lot.type() == EscrowAssetLotType
                            .PROTECTED_PHYSICAL_CURRENCY)) {
                if (transaction.state().isTerminal()) {
                    runtime.scheduleProtectedCashCleanup(
                            playerId, transaction.transactionId().value());
                } else {
                    runtime.enqueueProtectedCashRecovery(
                            transaction.transactionId().value());
                }
                return true;
            } else if (transaction.assetLots().stream().anyMatch(lot ->
                    lot.type() == EscrowAssetLotType
                            .FOREIGN_PHYSICAL_CURRENCY)) {
                if (transaction.state().isTerminal()) {
                    runtime.scheduleForeignCashCleanup(
                            playerId, transaction.transactionId().value());
                } else {
                    runtime.enqueueForeignCashRecovery(
                            transaction.transactionId().value());
                }
                return true;
            }
        } catch (RuntimeException ignored) {
            return false;
        }
        return false;
    }

    private static ActiveEvidence attemptAutomaticRecovery(
            EscrowRuntimeService runtime,
            ServerPlayer player,
            ActiveEvidence evidence
    ) {
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(evidence, "evidence");
        if (!runtime.isReady() || evidence.corrupt()
                || evidence.identity().isEmpty()) {
            return evidence;
        }
        RecoveryIdentity identity = evidence.identity().orElseThrow();
        if (identity.depositMode() != CashDepositMode.PUBLIC_WALLET
                || !transactionIdForRequest(
                player.getUUID(), identity.requestId())
                .equals(identity.transactionId())) {
            return ActiveEvidence.corruptEvidence();
        }
        Optional<EscrowTransaction> transaction =
                runtime.transaction(identity.transactionId());
        boolean queued;
        if (transaction.isEmpty()) {
            queued = enqueueIntentRecovery(
                    runtime, player, evidence, identity.transactionId());
        } else {
            EscrowTransaction stored = transaction.orElseThrow();
            if (!matchesPublicRecovery(stored, player.getUUID(),
                    identity.requestId(), identity.transactionId())) {
                return ActiveEvidence.corruptEvidence();
            }
            queued = stored.state() != EscrowState.MANUAL_REVIEW
                    && enqueueTransactionRecovery(
                    runtime, stored, player.getUUID());
        }
        if (queued) {
            recoverBounded(runtime);
        }
        return inspectActiveEvidence(player);
    }

    private static Optional<DepositResult> terminalCleanupBlock(
            EscrowRuntimeService runtime,
            ServerPlayer player,
            DepositRequest request,
            UUID transactionId,
            EscrowTransaction transaction
    ) {
        if (!transaction.state().isTerminal() || !hasRawEvidence(player)) {
            return Optional.empty();
        }
        if (!runtime.isReady()
                || !enqueueTransactionRecovery(
                runtime, transaction, player.getUUID())) {
            return Optional.of(failure(Status.MANUAL_REVIEW, request,
                    Optional.of(transactionId), Optional.empty()));
        }
        recoverBounded(runtime);
        if (!hasRawEvidence(player)) {
            return Optional.empty();
        }
        Status status = runtime.failure().isPresent()
                ? Status.MANUAL_REVIEW : Status.RECOVERY_PENDING;
        return Optional.of(failure(status, request,
                Optional.of(transactionId), Optional.empty()));
    }

    private static boolean recoverBounded(EscrowRuntimeService runtime) {
        try {
            runtime.recoverBatch(32);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static DepositResult recoveryResult(
            EscrowRuntimeService runtime,
            ServerPlayer player,
            DepositRequest request,
            UUID transactionId
    ) {
        Optional<EscrowTransaction> stored = runtime.transaction(
                transactionId);
        if (stored.isEmpty()) {
            return failure(Status.RECOVERY_PENDING, request,
                    Optional.of(transactionId), Optional.empty());
        }
        EscrowTransaction transaction = stored.orElseThrow();
        return switch (transaction.state()) {
            case COMPLETED -> {
                try {
                    yield completedReplayFromRuntime(runtime, player,
                            request, transactionId, transaction);
                } catch (RuntimeException exception) {
                    yield failure(Status.MANUAL_REVIEW, request,
                            Optional.of(transactionId), Optional.empty());
                }
            }
            case REFUNDED -> refunded(
                    request, transactionId, transaction);
            case MANUAL_REVIEW -> failure(Status.MANUAL_REVIEW, request,
                    Optional.of(transactionId), Optional.empty());
            default -> failure(Status.RECOVERY_PENDING, request,
                    Optional.of(transactionId), Optional.empty());
        };
    }

    private static boolean matchesPublicRecovery(
            EscrowTransaction transaction,
            UUID playerId,
            UUID requestId,
            UUID transactionId
    ) {
        return transaction.transactionId().value().equals(transactionId)
                && transaction.operation() == EscrowOperation.CURRENCY_DEPOSIT
                && transactionIdForRequest(playerId, requestId).equals(
                transactionId)
                && requestId(transaction).filter(requestId::equals)
                .isPresent()
                && !transaction.assetLots().isEmpty()
                && transaction.assetLots().stream().allMatch(lot ->
                (lot.type() == EscrowAssetLotType
                        .PROTECTED_PHYSICAL_CURRENCY
                        || lot.type() == EscrowAssetLotType
                        .FOREIGN_PHYSICAL_CURRENCY)
                        && (CashDepositMode.PUBLIC_WALLET.name().equals(
                        lot.attributes().get("deposit_mode"))
                        || !lot.attributes().containsKey("deposit_mode")));
    }

    private static Optional<UUID> requestId(EscrowTransaction transaction) {
        String key = transaction.requestKey().value();
        String prefix = "cash.deposit.";
        if (!key.startsWith(prefix)) {
            return Optional.empty();
        }
        int end = key.indexOf('.', prefix.length());
        if (end < 0) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(
                    key.substring(prefix.length(), end)));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static RecoveryStatus recoveryStatus(EscrowState state) {
        return switch (state) {
            case COMPLETED -> RecoveryStatus.COMPLETED;
            case REFUNDED -> RecoveryStatus.REFUNDED;
            case MANUAL_REVIEW -> RecoveryStatus.MANUAL_REVIEW;
            default -> RecoveryStatus.RECOVERY_PENDING;
        };
    }

    private static DepositResult refunded(
            DepositRequest request,
            UUID transactionId,
            EscrowTransaction transaction
    ) {
        long returnedMinorUnits = transaction.assetLots().stream()
                .map(lot -> lot.money().orElseThrow().minorUnits())
                .reduce(0L, Math::addExact);
        if (returnedMinorUnits <= 0L) {
            return failure(Status.MANUAL_REVIEW, request,
                    Optional.of(transactionId), Optional.empty());
        }
        return new DepositResult(
                Status.REFUNDED, request.requestId(),
                Optional.of(transactionId),
                0L, 0, 0L, 0L,
                returnedMinorUnits,
                RefundDestination.ORIGINAL_INVENTORY,
                0L, false, false, Optional.empty(), 0L);
    }

    private static boolean hasRawEvidence(ServerPlayer player) {
        return player.getPersistentData().get(
                CashDepositEvidenceKeys.PROTECTED) != null
                || player.getPersistentData().get(
                CashDepositEvidenceKeys.FOREIGN) != null;
    }

    private static LegacyMigrationFacts legacyFacts(
            InternalBillInventoryPlanner.InventoryFacts facts
    ) {
        List<LegacyBillFact> bills = facts.legacyBills().stream()
                .map(value -> new LegacyBillFact(value.slot(),
                        value.mintId(), value.denominationMinorUnits(),
                        value.authorizedCount(), value.availableCount(),
                        value.originalStackCount(),
                        value.exactStackSnapshot()))
                .toList();
        return new LegacyMigrationFacts(
                facts.legacyAvailableMinorUnits(),
                facts.legacyBillCount(), bills);
    }

    public static UUID transactionIdForRequest(
            UUID playerId,
            UUID requestId
    ) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(requestId, "requestId");
        return UUID.nameUUIDFromBytes((
                "futureshops cash deposit transaction v1 " + playerId + " "
                        + requestId).getBytes(StandardCharsets.UTF_8));
    }

    private static String requestKey(ServerPlayer player,
                                     DepositRequest request) {
        return requestKey(player.getUUID(), request);
    }

    static String requestKey(UUID playerId, DepositRequest request) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(request, "request");
        String amount = request.requestedMinorUnits().isPresent()
                ? Long.toString(request.requestedMinorUnits().getAsLong())
                : "all";
        if (request.serverShopPurchaseRequestId().isEmpty()) {
            byte[] digest = ForeignCashDepositReservation.sha256(
                    ("futureshops cash deposit payload v3 "
                            + playerId + " " + request.source() + " "
                            + amount + " " + request.currencySignature()
                            + " " + request.mode())
                            .getBytes(StandardCharsets.UTF_8));
            return "cash.deposit." + request.requestId() + "."
                    + HexFormat.of().formatHex(digest);
        }
        UUID purchaseRequestId = request.serverShopPurchaseRequestId()
                .orElseThrow();
        byte[] digest = ForeignCashDepositReservation.sha256(
                ("futureshops cash deposit payload v4 "
                        + playerId + " " + request.source() + " "
                        + amount + " " + request.currencySignature() + " "
                        + request.mode() + " " + purchaseRequestId)
                        .getBytes(StandardCharsets.UTF_8));
        return "cash.deposit." + request.requestId() + "."
                + HexFormat.of().formatHex(digest) + ".shop."
                + purchaseRequestId;
    }

    static Optional<String> legacyRequestKey(
            UUID playerId,
            DepositRequest request
    ) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(request, "request");
        if (request.mode() != CashDepositMode.PUBLIC_WALLET) {
            return Optional.empty();
        }
        String amount = request.requestedMinorUnits().isPresent()
                ? Long.toString(request.requestedMinorUnits().getAsLong())
                : "all";
        byte[] digest = ForeignCashDepositReservation.sha256(
                ("futureshops cash deposit payload v2 "
                        + playerId + " " + request.source() + " "
                        + amount + " " + request.currencySignature())
                        .getBytes(StandardCharsets.UTF_8));
        return Optional.of("cash.deposit." + request.requestId() + "."
                + HexFormat.of().formatHex(digest));
    }

    private static long configurationRevision(
            PhysicalCurrencyAdapter adapter
    ) {
        byte[] digest = ForeignCashDepositReservation.sha256(
                (adapter.id() + "\u0000"
                        + adapter.depositConfigurationSignature())
                        .getBytes(StandardCharsets.UTF_8));
        long result = 0L;
        for (int index = 0; index < Long.BYTES; index++) {
            result = result << 8 | digest[index] & 255L;
        }
        return result & Long.MAX_VALUE;
    }

    private record ActiveEvidence(
            Optional<RecoveryIdentity> identity,
            boolean corrupt
    ) {
        private ActiveEvidence {
            identity = Objects.requireNonNull(identity, "identity");
            if (corrupt && identity.isPresent()) {
                throw new IllegalArgumentException(
                        "Active cash deposit evidence is invalid");
            }
        }

        private static ActiveEvidence none() {
            return new ActiveEvidence(Optional.empty(), false);
        }

        private static ActiveEvidence recovery(RecoveryIdentity identity) {
            return new ActiveEvidence(Optional.of(identity), false);
        }

        private static ActiveEvidence corruptEvidence() {
            return new ActiveEvidence(Optional.empty(), true);
        }
    }

    private record RecoveryIdentity(
            UUID requestId,
            UUID transactionId,
            long amountMinorUnits,
            CashDepositMode depositMode,
            boolean intent
    ) {
        private RecoveryIdentity {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(transactionId, "transactionId");
            Objects.requireNonNull(depositMode, "depositMode");
            if (requestId.equals(ZERO_UUID)
                    || transactionId.equals(ZERO_UUID)
                    || amountMinorUnits <= 0L) {
                throw new IllegalArgumentException(
                        "Cash deposit recovery identity is invalid");
            }
        }
    }

    enum ReplayDisposition {
        ABSENT,
        COMPLETED,
        REQUEST_CONFLICT,
        CANCELLED,
        RECOVERY_REQUIRED
    }

    public enum Source {
        INVENTORY,
        MAIN_HAND,
        OFF_HAND
    }

    public enum Status {
        SUCCESS,
        NO_CURRENCY,
        INVALID_AMOUNT,
        NOT_ENOUGH_CURRENCY,
        INVALID_DENOMINATION,
        TOO_MANY_ITEMS,
        WRONG_PROVIDER,
        CREATIVE_BLOCKED,
        LEGACY_MIGRATION_REQUIRED,
        INVALID_CURRENCY,
        REQUEST_CONFLICT,
        CANCELLED,
        CONFIG_CHANGED,
        RATE_LIMITED,
        ESCROW_UNAVAILABLE,
        RECOVERY_REQUIRED,
        RECOVERY_PENDING,
        MANUAL_REVIEW,
        REFUNDED
    }

    public enum RecoveryStatus {
        RECOVERY_PENDING,
        MANUAL_REVIEW,
        COMPLETED,
        REFUNDED
    }

    public enum RefundDestination {
        NONE,
        ORIGINAL_INVENTORY
    }

    public record DepositRecovery(
            UUID requestId,
            UUID transactionId,
            RecoveryStatus status,
            long amountMinorUnits
    ) {
        public DepositRecovery {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(transactionId, "transactionId");
            Objects.requireNonNull(status, "status");
            if (requestId.equals(ZERO_UUID)
                    || transactionId.equals(ZERO_UUID)
                    || amountMinorUnits <= 0L) {
                throw new IllegalArgumentException(
                        "Cash deposit recovery is invalid");
            }
        }
    }

    public record DepositRequest(
            UUID requestId,
            String currencySignature,
            Source source,
            OptionalLong requestedMinorUnits,
            CashDepositMode mode,
            Optional<UUID> serverShopPurchaseRequestId
    ) {
        public DepositRequest(
                UUID requestId,
                String currencySignature,
                Source source,
                OptionalLong requestedMinorUnits
        ) {
            this(requestId, currencySignature, source,
                    requestedMinorUnits, CashDepositMode.PUBLIC_WALLET,
                    Optional.empty());
        }

        public DepositRequest(
                UUID requestId,
                String currencySignature,
                Source source,
                OptionalLong requestedMinorUnits,
                CashDepositMode mode
        ) {
            this(requestId, currencySignature, source,
                    requestedMinorUnits, mode, Optional.empty());
        }

        public DepositRequest {
            Objects.requireNonNull(requestId, "requestId");
            currencySignature = Objects.requireNonNull(
                    currencySignature, "currencySignature");
            Objects.requireNonNull(source, "source");
            requestedMinorUnits = Objects.requireNonNull(
                    requestedMinorUnits, "requestedMinorUnits");
            Objects.requireNonNull(mode, "mode");
            serverShopPurchaseRequestId = Objects.requireNonNull(
                    serverShopPurchaseRequestId,
                    "serverShopPurchaseRequestId");
            if (requestId.equals(ZERO_UUID)
                    || !CURRENCY_SIGNATURE.matcher(
                    currencySignature).matches()
                    || serverShopPurchaseRequestId.filter(
                    ZERO_UUID::equals).isPresent()
                    || serverShopPurchaseRequestId.isPresent()
                    && mode != CashDepositMode.INTERNAL_ESCROW
                    || serverShopPurchaseRequestId.isPresent()
                    && !requestId.equals(
                    ServerShopFundingRelease.fundingRequestId(
                            serverShopPurchaseRequestId.orElseThrow()))) {
                throw new IllegalArgumentException(
                        "Cash deposit request identity is invalid");
            }
        }
    }

    public record DepositResult(
            Status status,
            UUID requestId,
            Optional<UUID> transactionId,
            long depositedMinorUnits,
            int itemsConsumed,
            long walletCreditMinorUnits,
            long overflowClaimMinorUnits,
            long returnedMinorUnits,
            RefundDestination refundDestination,
            long resultingBalanceMinorUnits,
            boolean cleanupPending,
            boolean replayed,
            Optional<LegacyMigrationFacts> legacyMigration,
            long retryAfterMillis
    ) {
        public DepositResult(
                Status status,
                UUID requestId,
                Optional<UUID> transactionId,
                long depositedMinorUnits,
                int itemsConsumed,
                long walletCreditMinorUnits,
                long overflowClaimMinorUnits,
                long resultingBalanceMinorUnits,
                boolean cleanupPending,
                boolean replayed,
                Optional<LegacyMigrationFacts> legacyMigration,
                long retryAfterMillis
        ) {
            this(status, requestId, transactionId,
                    depositedMinorUnits, itemsConsumed,
                    walletCreditMinorUnits, overflowClaimMinorUnits,
                    0L, RefundDestination.NONE,
                    resultingBalanceMinorUnits, cleanupPending, replayed,
                    legacyMigration, retryAfterMillis);
        }

        public DepositResult {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(requestId, "requestId");
            transactionId = Objects.requireNonNull(
                    transactionId, "transactionId");
            Objects.requireNonNull(refundDestination, "refundDestination");
            legacyMigration = Objects.requireNonNull(
                    legacyMigration, "legacyMigration");
            if (requestId.equals(ZERO_UUID)
                    || transactionId.filter(ZERO_UUID::equals).isPresent()
                    || returnedMinorUnits < 0L
                    || retryAfterMillis < 0L
                    || retryAfterMillis > MAX_RETRY_AFTER_MILLIS
                    || (status == Status.RATE_LIMITED)
                    != (retryAfterMillis > 0L)
                    || replayed && status != Status.SUCCESS
                    || status == Status.REQUEST_CONFLICT
                    && transactionId.isEmpty()
                    || status == Status.CANCELLED
                    && transactionId.isEmpty()
                    || (status == Status.RECOVERY_PENDING
                    || status == Status.MANUAL_REVIEW
                    || status == Status.REFUNDED)
                    && transactionId.isEmpty()
                    || status == Status.SUCCESS
                    && (transactionId.isEmpty()
                    || depositedMinorUnits <= 0L || itemsConsumed <= 0
                    || itemsConsumed > MAX_ITEMS_CONSUMED
                    || walletCreditMinorUnits < 0L
                    || overflowClaimMinorUnits < 0L
                    || Math.addExact(walletCreditMinorUnits,
                    overflowClaimMinorUnits) != depositedMinorUnits)
                    || status != Status.SUCCESS
                    && (depositedMinorUnits != 0L || itemsConsumed != 0
                    || walletCreditMinorUnits != 0L
                    || overflowClaimMinorUnits != 0L
                    || cleanupPending || replayed)
                    || status == Status.REFUNDED
                    && (returnedMinorUnits <= 0L
                    || refundDestination
                    != RefundDestination.ORIGINAL_INVENTORY)
                    || status != Status.REFUNDED
                    && (returnedMinorUnits != 0L
                    || refundDestination != RefundDestination.NONE)
                    || status == Status.LEGACY_MIGRATION_REQUIRED
                    && legacyMigration.isEmpty()
                    || status != Status.LEGACY_MIGRATION_REQUIRED
                    && legacyMigration.isPresent()) {
                throw new IllegalArgumentException(
                        "Cash deposit result is invalid");
            }
        }

        public boolean successful() {
            return status == Status.SUCCESS;
        }

        public boolean retryable() {
            return status == Status.RATE_LIMITED
                    || status == Status.ESCROW_UNAVAILABLE
                    || status == Status.RECOVERY_REQUIRED
                    || status == Status.RECOVERY_PENDING;
        }

        public long retryAfterSeconds() {
            return retryAfterMillis == 0L ? 0L : Math.addExact(
                    Math.subtractExact(retryAfterMillis, 1L) / 1_000L,
                    1L);
        }
    }

    public record LegacyMigrationFacts(
            long availableMinorUnits,
            int billCount,
            List<LegacyBillFact> bills
    ) {
        public LegacyMigrationFacts {
            bills = List.copyOf(Objects.requireNonNull(bills, "bills"));
            long value = bills.stream().mapToLong(
                    LegacyBillFact::valueMinorUnits).sum();
            int count = bills.stream().mapToInt(
                    LegacyBillFact::availableCount).sum();
            if (availableMinorUnits <= 0L || billCount <= 0
                    || value != availableMinorUnits || count != billCount) {
                throw new IllegalArgumentException(
                        "Legacy migration facts are invalid");
            }
        }
    }

    public record LegacyBillFact(
            InternalBillInventoryPlanner.SlotIdentity slot,
            String mintId,
            long denominationMinorUnits,
            int authorizedCount,
            int availableCount,
            int originalStackCount,
            byte[] exactStackSnapshot
    ) {
        public LegacyBillFact {
            Objects.requireNonNull(slot, "slot");
            mintId = Objects.requireNonNull(mintId, "mintId");
            exactStackSnapshot = Objects.requireNonNull(
                    exactStackSnapshot, "exactStackSnapshot").clone();
            if (mintId.isEmpty() || denominationMinorUnits <= 0L
                    || authorizedCount <= 0 || availableCount <= 0
                    || originalStackCount <= 0
                    || availableCount > originalStackCount
                    || exactStackSnapshot.length == 0) {
                throw new IllegalArgumentException(
                        "Legacy migration bill fact is invalid");
            }
        }

        @Override
        public byte[] exactStackSnapshot() {
            return exactStackSnapshot.clone();
        }

        public long valueMinorUnits() {
            return Math.multiplyExact(denominationMinorUnits,
                    (long) availableCount);
        }

        @Override
        public boolean equals(Object object) {
            return object instanceof LegacyBillFact other
                    && slot.equals(other.slot)
                    && mintId.equals(other.mintId)
                    && denominationMinorUnits
                    == other.denominationMinorUnits
                    && authorizedCount == other.authorizedCount
                    && availableCount == other.availableCount
                    && originalStackCount == other.originalStackCount
                    && Arrays.equals(exactStackSnapshot,
                    other.exactStackSnapshot);
        }

        @Override
        public int hashCode() {
            return 31 * Objects.hash(slot, mintId,
                    denominationMinorUnits, authorizedCount,
                    availableCount, originalStackCount)
                    + Arrays.hashCode(exactStackSnapshot);
        }
    }
}

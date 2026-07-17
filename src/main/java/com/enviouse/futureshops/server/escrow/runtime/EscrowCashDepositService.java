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
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountId;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountType;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;
import com.enviouse.futureshops.server.escrow.model.EscrowOperation;
import com.enviouse.futureshops.server.escrow.model.EscrowState;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;
import com.enviouse.futureshops.server.escrow.redemption.CashDepositEvidenceKeys;
import com.enviouse.futureshops.server.escrow.redemption.ProtectedCashInventoryState;
import com.enviouse.futureshops.server.escrow.redemption.ProtectedCashRedemptionEvidence;
import com.enviouse.futureshops.server.security.ServerRequestAction;
import com.enviouse.futureshops.server.security.ServerRequestSecurityManager;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.Tag;
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
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(request, "request");
        UUID transactionId = transactionId(player.getUUID(),
                request.requestId());
        ServerRequestSecurityManager.GateDecision gate =
                ServerRequestSecurityManager.tryAcquire(
                        player, ServerRequestAction.ATM_DEPOSIT);
        if (!gate.allowed()) {
            return gateFailure(request, gate);
        }
        EscrowRuntimeService runtime = EscrowRuntimeManager.getOrNull();
        if (runtime == null || !runtime.isReady()) {
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
                            runtime, player, request, transactionId));
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

    private static DepositResult depositWithConfigurationLease(
            EscrowRuntimeService runtime,
            ServerPlayer player,
            DepositRequest request,
            UUID transactionId
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
        long walletBalanceLimitMinorUnits =
                Config.economyMaxBalanceMinorUnits;
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
        if (activeEvidence.transactionId().isPresent()) {
            return failure(Status.RECOVERY_REQUIRED, request,
                    activeEvidence.transactionId(), Optional.empty());
        }
        return adapter.isInternal()
                ? depositProtected(runtime, player, request,
                transactionId, protectedConfigurationRevision,
                walletBalanceLimitMinorUnits)
                : depositForeign(runtime, player, request,
                transactionId, adapter,
                walletBalanceLimitMinorUnits);
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
            long walletBalanceLimitMinorUnits
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
                walletBalanceLimitMinorUnits, Instant.now());
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
            long walletBalanceLimitMinorUnits
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
                walletBalanceLimitMinorUnits, Instant.now());
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
                overflowClaim, balance, cleanupPending, false,
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
                0L, 0, 0L, 0L, 0L, false, false, legacy,
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
        ReplayDisposition disposition = classifyReplay(stored, expectedKey);
        switch (disposition) {
            case ABSENT -> {
                return Optional.empty();
            }
            case REQUEST_CONFLICT -> {
                return Optional.of(failure(Status.REQUEST_CONFLICT, request,
                        Optional.of(transactionId), Optional.empty()));
            }
            case CANCELLED -> {
                return Optional.of(failure(Status.CANCELLED, request,
                        Optional.of(transactionId), Optional.empty()));
            }
            case RECOVERY_REQUIRED -> {
                return Optional.of(failure(Status.RECOVERY_REQUIRED, request,
                        Optional.of(transactionId), Optional.empty()));
            }
            case COMPLETED -> {
            }
        }
        EscrowTransaction transaction = stored.orElseThrow();
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
                    requestKey(player, request));
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
        long overflow = claims.stream()
                .filter(claim -> claim.kind() == ClaimKind.MONEY
                        && claim.ownerId().equals(playerId)
                        && claim.transactionId().equals(transactionId))
                .map(EscrowClaim::originalUnits)
                .reduce(0L, Math::addExact);
        if (amount <= 0L || items <= 0
                || items > MAX_ITEMS_CONSUMED || walletCredit < 0L
                || overflow < 0L
                || Math.addExact(walletCredit, overflow) != amount) {
            throw new EscrowRuntimeException(
                    "Cash deposit replay evidence does not conserve");
        }
        return new DepositResult(Status.SUCCESS,
                request.requestId(), Optional.of(transactionId), amount,
                items, walletCredit, overflow, resultingBalanceMinorUnits,
                cleanupPending, true, Optional.empty(), 0L);
    }

    static ReplayDisposition classifyReplay(
            Optional<EscrowTransaction> stored,
            String expectedRequestKey
    ) {
        Objects.requireNonNull(stored, "stored");
        Objects.requireNonNull(expectedRequestKey,
                "expectedRequestKey");
        if (stored.isEmpty()) {
            return ReplayDisposition.ABSENT;
        }
        EscrowTransaction transaction = stored.orElseThrow();
        if (transaction.operation() != EscrowOperation.CURRENCY_DEPOSIT
                || !transaction.requestKey().value().equals(
                expectedRequestKey)) {
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
                return evidence.playerId().equals(player.getUUID())
                        ? ActiveEvidence.transaction(
                        evidence.transactionId())
                        : ActiveEvidence.corruptEvidence();
            }
            if (foreignRaw != null) {
                if (!(foreignRaw instanceof ByteArrayTag bytes)) {
                    return ActiveEvidence.corruptEvidence();
                }
                ForeignCashDepositEvidence evidence =
                        ForeignCashDepositEvidence.decode(
                                bytes.getAsByteArray());
                return evidence.playerId().equals(player.getUUID())
                        ? ActiveEvidence.transaction(
                        evidence.transactionId())
                        : ActiveEvidence.corruptEvidence();
            }
            return ActiveEvidence.none();
        } catch (RuntimeException exception) {
            return ActiveEvidence.corruptEvidence();
        }
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

    private static UUID transactionId(UUID playerId, UUID requestId) {
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
        byte[] digest = ForeignCashDepositReservation.sha256(
                ("futureshops cash deposit payload v2 "
                        + playerId + " " + request.source() + " "
                        + amount + " " + request.currencySignature())
                        .getBytes(StandardCharsets.UTF_8));
        return "cash.deposit." + request.requestId() + "."
                + HexFormat.of().formatHex(digest);
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
            Optional<UUID> transactionId,
            boolean corrupt
    ) {
        private ActiveEvidence {
            transactionId = Objects.requireNonNull(
                    transactionId, "transactionId");
            if (corrupt && transactionId.isPresent()
                    || transactionId.filter(ZERO_UUID::equals).isPresent()) {
                throw new IllegalArgumentException(
                        "Active cash deposit evidence is invalid");
            }
        }

        private static ActiveEvidence none() {
            return new ActiveEvidence(Optional.empty(), false);
        }

        private static ActiveEvidence transaction(UUID transactionId) {
            return new ActiveEvidence(Optional.of(transactionId), false);
        }

        private static ActiveEvidence corruptEvidence() {
            return new ActiveEvidence(Optional.empty(), true);
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
        RECOVERY_REQUIRED
    }

    public record DepositRequest(
            UUID requestId,
            String currencySignature,
            Source source,
            OptionalLong requestedMinorUnits
    ) {
        public DepositRequest {
            Objects.requireNonNull(requestId, "requestId");
            currencySignature = Objects.requireNonNull(
                    currencySignature, "currencySignature");
            Objects.requireNonNull(source, "source");
            requestedMinorUnits = Objects.requireNonNull(
                    requestedMinorUnits, "requestedMinorUnits");
            if (requestId.equals(ZERO_UUID)
                    || !CURRENCY_SIGNATURE.matcher(
                    currencySignature).matches()) {
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
            long resultingBalanceMinorUnits,
            boolean cleanupPending,
            boolean replayed,
            Optional<LegacyMigrationFacts> legacyMigration,
            long retryAfterMillis
    ) {
        public DepositResult {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(requestId, "requestId");
            transactionId = Objects.requireNonNull(
                    transactionId, "transactionId");
            legacyMigration = Objects.requireNonNull(
                    legacyMigration, "legacyMigration");
            if (requestId.equals(ZERO_UUID)
                    || transactionId.filter(ZERO_UUID::equals).isPresent()
                    || retryAfterMillis < 0L
                    || retryAfterMillis > MAX_RETRY_AFTER_MILLIS
                    || (status == Status.RATE_LIMITED)
                    != (retryAfterMillis > 0L)
                    || replayed && status != Status.SUCCESS
                    || status == Status.REQUEST_CONFLICT
                    && transactionId.isEmpty()
                    || status == Status.CANCELLED
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
                    || status == Status.RECOVERY_REQUIRED;
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

package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.event.BalanceChangeEvent;
import com.enviouse.futureshops.money.AtmCurrencyCatalog;
import com.enviouse.futureshops.money.AtmCurrencyRoute;
import com.enviouse.futureshops.money.AtmSelectionPlan;
import com.enviouse.futureshops.money.CurrencyManager;
import com.enviouse.futureshops.money.CurrencyMath;
import com.enviouse.futureshops.money.ItemStackSnapshotCodec;
import com.enviouse.futureshops.money.PhysicalCurrencyAdapter;
import com.enviouse.futureshops.server.economy.BalanceManager;
import com.enviouse.futureshops.server.economy.migration.LegacyBalanceMigrationManager;
import com.enviouse.futureshops.server.escrow.claim.ClaimKind;
import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.model.EscrowAssetLot;
import com.enviouse.futureshops.server.escrow.model.EscrowAssetLotType;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.registries.ForgeRegistries;

import java.time.Clock;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class EscrowAtmWithdrawalService {
    private static final int MAX_AUTOMATIC_CASH_DELIVERY_CLAIMS = 4;
    private EscrowAtmWithdrawalService() {
    }

    public static AtmAccessSnapshot access(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        AtmCurrencyCatalog catalog;
        try (CurrencyManager.ConfigurationReadLease ignored =
                     CurrencyManager.acquireConfigurationReadLease()) {
            catalog = AtmCurrencyCatalog.capture(
                    CurrencyManager.get(), BalanceManager.getProvider());
        }
        EscrowRuntimeService runtime = EscrowRuntimeManager.getOrNull();
        if (LegacyBalanceMigrationManager.isFailed()) {
            return unavailable(catalog, "MIGRATION_FAILED");
        }
        if (!LegacyBalanceMigrationManager.isComplete()) {
            return unavailable(catalog, "MIGRATION_PENDING");
        }
        if (runtime == null) {
            return unavailable(catalog, "ESCROW_UNAVAILABLE");
        }
        EscrowRuntimeState state = runtime.state();
        if (state != EscrowRuntimeState.READY) {
            return unavailable(catalog, switch (state) {
                case STARTING, RECOVERING -> "RECOVERY_PENDING";
                case MAINTENANCE -> "ESCROW_MAINTENANCE";
                case STOPPING, STOPPED -> "ESCROW_UNAVAILABLE";
                case READY -> "AVAILABLE";
            });
        }
        try {
            return new AtmAccessSnapshot(
                    catalog, true,
                    BalanceManager.getBalance(player.getUUID()),
                    true, "AVAILABLE");
        } catch (RuntimeException exception) {
            return unavailable(catalog, "ESCROW_UNAVAILABLE");
        }
    }

    public static AtmWithdrawalOutcome withdraw(
            ServerPlayer player,
            UUID requestId,
            String currencySignature,
            List<Integer> denominationCounts
    ) {
        Objects.requireNonNull(player, "player");
        EscrowRuntimeService runtime = EscrowRuntimeManager.getOrNull();
        LiveBackend backend = new LiveBackend(runtime);
        AtmWithdrawalOrchestrator orchestrator =
                new AtmWithdrawalOrchestrator(
                        backend, new LiveEvents(), Clock.systemUTC(),
                        EscrowAtmWithdrawalService
                                ::acquireCurrencyConfiguration);
        AtmWithdrawalOutcome outcome = orchestrator.submit(
                requestId, player.getUUID(), currencySignature,
                denominationCounts,
                () -> prepare(player, requestId,
                        currencySignature, denominationCounts,
                        Clock.systemUTC().instant()));
        return attemptCashDelivery(player, runtime, orchestrator,
                outcome, currencySignature, denominationCounts);
    }

    public static AtmWithdrawalOutcome withdrawAutomatic(
            ServerPlayer player,
            UUID requestId,
            long amountMinorUnits,
            boolean multipleBills
    ) {
        Objects.requireNonNull(player, "player");
        AtmCurrencyCatalog catalog;
        AutomaticPlan automatic;
        List<Integer> counts;
        try (CurrencyManager.ConfigurationReadLease ignored =
                     CurrencyManager.acquireConfigurationReadLease()) {
            PhysicalCurrencyAdapter currency = CurrencyManager.get();
            catalog = AtmCurrencyCatalog.capture(
                    currency, BalanceManager.getProvider());
            automatic = automaticPlan(catalog, amountMinorUnits);
            if (!automatic.valid()) {
                return AtmWithdrawalOutcome.failure(
                        requestId, automatic.status(), false, false,
                        false, 0L, 0L, 0, catalog.signature());
            }
            counts = automaticCounts(
                    catalog, automatic, multipleBills);
        }
        return withdrawAutomatic(player, requestId, amountMinorUnits,
                multipleBills, catalog.signature(), counts);
    }

    public static AtmWithdrawalOutcome withdrawAutomatic(
            ServerPlayer player,
            UUID requestId,
            long amountMinorUnits,
            boolean multipleBills,
            String currencySignature,
            List<Integer> denominationCounts
    ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(currencySignature, "currencySignature");
        List<Integer> counts = List.copyOf(Objects.requireNonNull(
                denominationCounts, "denominationCounts"));
        EscrowRuntimeService runtime = EscrowRuntimeManager.getOrNull();
        Optional<AtmWithdrawalOutcome> replayConflict =
                automaticReplayConflict(runtime, player, requestId,
                        amountMinorUnits, currencySignature);
        if (replayConflict.isPresent()) {
            return replayConflict.orElseThrow();
        }
        AtmWithdrawalOrchestrator orchestrator =
                new AtmWithdrawalOrchestrator(
                        new LiveBackend(runtime), new LiveEvents(),
                        Clock.systemUTC(), EscrowAtmWithdrawalService
                        ::acquireCurrencyConfiguration);
        AtmWithdrawalOutcome outcome = orchestrator.submit(
                requestId, player.getUUID(), currencySignature, counts,
                () -> {
                    PhysicalCurrencyAdapter currency = CurrencyManager.get();
                    AtmCurrencyCatalog catalog = AtmCurrencyCatalog.capture(
                            currency, BalanceManager.getProvider());
                    if (!catalog.signature().equals(currencySignature)) {
                        throw new AtmPreparationException(
                                AtmWithdrawalStatus.CURRENCY_CHANGED,
                                "Automatic ATM currency catalog changed");
                    }
                    AutomaticPlan automatic = automaticPlan(
                            catalog, amountMinorUnits);
                    if (!automatic.valid()
                            || !automaticCounts(catalog, automatic,
                            multipleBills).equals(counts)) {
                        throw new AtmPreparationException(
                                automatic.valid()
                                        ? AtmWithdrawalStatus.INVALID_PLAN
                                        : automatic.status(),
                                "Automatic ATM plan is invalid");
                    }
                    Instant requestedAt = Clock.systemUTC().instant();
                    if (!multipleBills
                            && catalog.route()
                            == AtmCurrencyRoute.PROTECTED_ESCROW) {
                        ProtectedAtmWithdrawalRequest request =
                                new ProtectedAtmWithdrawalRequest(
                                        requestId, player.getUUID(),
                                        catalog.providerId(),
                                        catalog.signature(),
                                        List.of(new AtmBillSelection(
                                                0, amountMinorUnits, 1)),
                                        requestedAt);
                        return AtmPreparedWithdrawal.protectedPlan(
                                ProtectedAtmWithdrawalPlan.create(request));
                    }
                    AtmSelectionPlan selectionPlan = catalog.plan(counts);
                    if (!selectionPlan.valid()) {
                        throw new AtmPreparationException(
                                AtmWithdrawalStatus.INVALID_PLAN,
                                "Automatic ATM plan is invalid");
                    }
                    return prepareFromSelections(
                            player, requestId, catalog, currency,
                            selectionPlan.selections(), requestedAt);
                });
        return attemptCashDelivery(player, runtime, orchestrator,
                outcome, currencySignature, counts);
    }

    private static Optional<AtmWithdrawalOutcome> automaticReplayConflict(
            EscrowRuntimeService runtime,
            ServerPlayer player,
            UUID requestId,
            long requestedAmount,
            String currencySignature
    ) {
        if (runtime == null) {
            return Optional.empty();
        }
        try {
            Optional<EscrowTransaction> found = runtime.transaction(
                    requestId);
            if (found.isEmpty()) {
                return Optional.empty();
            }
            List<EscrowAssetLot> wallets = found.orElseThrow()
                    .assetLots().stream()
                    .filter(lot -> lot.type()
                            == EscrowAssetLotType.WALLET_MONEY)
                    .toList();
            if (wallets.size() != 1
                    || wallets.get(0).money().isEmpty()) {
                return Optional.of(automaticReplayFailure(
                        player, requestId, requestedAmount,
                        currencySignature,
                        AtmWithdrawalStatus.SERVER_ERROR));
            }
            long recordedAmount = wallets.get(0).money()
                    .orElseThrow().minorUnits();
            if (recordedAmount != requestedAmount) {
                return Optional.of(automaticReplayFailure(
                        player, requestId, requestedAmount,
                        currencySignature,
                        AtmWithdrawalStatus.CONFLICT));
            }
            return Optional.empty();
        } catch (RuntimeException exception) {
            return Optional.of(automaticReplayFailure(
                    player, requestId, requestedAmount,
                    currencySignature,
                    AtmWithdrawalStatus.SERVER_ERROR));
        }
    }

    private static AtmWithdrawalOutcome automaticReplayFailure(
            ServerPlayer player,
            UUID requestId,
            long amount,
            String currencySignature,
            AtmWithdrawalStatus status
    ) {
        try {
            return AtmWithdrawalOutcome.failure(
                    requestId, status, false, true,
                    true, BalanceManager.getBalance(player.getUUID()),
                    Math.max(0L, amount), 0, currencySignature);
        } catch (RuntimeException exception) {
            return AtmWithdrawalOutcome.failure(
                    requestId, status, false, true,
                    false, 0L, Math.max(0L, amount), 0,
                    currencySignature);
        }
    }

    private static List<Integer> automaticCounts(
            AtmCurrencyCatalog catalog,
            AutomaticPlan automatic,
            boolean multipleBills
    ) {
        if (!multipleBills
                && catalog.route()
                == AtmCurrencyRoute.PROTECTED_ESCROW) {
            List<Integer> single = new ArrayList<>(
                    java.util.Collections.nCopies(
                            catalog.denominations().size(), 0));
            single.set(0, 1);
            return List.copyOf(single);
        }
        return automatic.counts();
    }

    private static AtmWithdrawalOutcome attemptCashDelivery(
            ServerPlayer player,
            EscrowRuntimeService runtime,
            AtmWithdrawalOrchestrator orchestrator,
            AtmWithdrawalOutcome outcome,
            String currencySignature,
            List<Integer> denominationCounts
    ) {
        if (!outcome.status().success()
                || runtime == null
                || runtime.state() != EscrowRuntimeState.READY) {
            return outcome;
        }
        boolean attempted = false;
        int attemptedClaims = 0;
        for (EscrowClaim claim : runtime.claimsForTransaction(
                outcome.requestId())) {
            if (claim.status() != ClaimStatus.PENDING
                    || (claim.kind() != ClaimKind.PROTECTED_CASH
                    && claim.kind() != ClaimKind.FOREIGN_CASH)) {
                continue;
            }
            if (attemptedClaims >= MAX_AUTOMATIC_CASH_DELIVERY_CLAIMS) {
                break;
            }
            attemptedClaims = Math.addExact(attemptedClaims, 1);
            attempted = true;
            try {
                runtime.deliverCashClaim(player, claim.claimId(),
                        automaticDeliveryAttemptId(
                                outcome.requestId(), claim.claimId()),
                        Clock.systemUTC().instant());
            } catch (RuntimeException exception) {
                break;
            }
            if (runtime.state() != EscrowRuntimeState.READY) {
                break;
            }
        }
        if (!attempted) {
            return outcome;
        }
        return orchestrator.submit(
                outcome.requestId(), player.getUUID(), currencySignature,
                denominationCounts,
                () -> {
                    throw new IllegalStateException(
                            "Completed ATM request was not found");
                });
    }

    private static AtmPreparedWithdrawal prepare(
            ServerPlayer player,
            UUID requestId,
            String currencySignature,
            List<Integer> denominationCounts,
            Instant requestedAt
    ) {
        PhysicalCurrencyAdapter currency = CurrencyManager.get();
        AtmCurrencyCatalog catalog = AtmCurrencyCatalog.capture(
                currency, BalanceManager.getProvider());
        if (!catalog.signature().equals(currencySignature)) {
            throw new AtmPreparationException(
                    AtmWithdrawalStatus.CURRENCY_CHANGED,
                    "ATM currency catalog changed");
        }
        AtmSelectionPlan selectionPlan = catalog.plan(
                denominationCounts);
        if (!selectionPlan.valid()) {
            throw new AtmPreparationException(
                    selectionPlan.failure()
                            == AtmSelectionPlan.Failure.INVALID_AMOUNT
                            ? AtmWithdrawalStatus.INVALID_AMOUNT
                            : AtmWithdrawalStatus.INVALID_PLAN,
                    "ATM denomination plan is invalid");
        }
        return prepareFromSelections(
                player, requestId, catalog, currency,
                selectionPlan.selections(), requestedAt);
    }

    private static AtmCurrencyConfigurationLease
    acquireCurrencyConfiguration() {
        CurrencyManager.ConfigurationReadLease managerLease =
                CurrencyManager.acquireConfigurationReadLease();
        try {
            AtmCurrencyCatalog catalog = AtmCurrencyCatalog.capture(
                    CurrencyManager.get(), BalanceManager.getProvider());
            return new LiveCurrencyConfigurationLease(
                    managerLease, managerLease.generation(),
                    catalog.signature());
        } catch (RuntimeException exception) {
            managerLease.close();
            throw exception;
        }
    }

    private static UUID automaticDeliveryAttemptId(
            UUID requestId,
            UUID claimId
    ) {
        return UUID.nameUUIDFromBytes((
                "futureshops atm automatic delivery "
                        + requestId + " " + claimId)
                .getBytes(StandardCharsets.UTF_8));
    }

    private static AtmPreparedWithdrawal prepareFromSelections(
            ServerPlayer player,
            UUID requestId,
            AtmCurrencyCatalog catalog,
            PhysicalCurrencyAdapter currency,
            List<AtmBillSelection> selections,
            Instant requestedAt
    ) {
        if (catalog.route() == AtmCurrencyRoute.PROTECTED_ESCROW) {
            ProtectedAtmWithdrawalRequest request =
                    new ProtectedAtmWithdrawalRequest(
                            requestId, player.getUUID(),
                            catalog.providerId(), catalog.signature(),
                            selections, requestedAt);
            return AtmPreparedWithdrawal.protectedPlan(
                    ProtectedAtmWithdrawalPlan.create(request));
        }
        ForeignAtmWithdrawalRequest request =
                new ForeignAtmWithdrawalRequest(
                        requestId, player.getUUID(),
                        catalog.providerId(), catalog.signature(),
                        foreignStacks(currency, catalog,
                                selections),
                        requestedAt);
        return AtmPreparedWithdrawal.foreignPlan(
                ForeignAtmWithdrawalPlan.create(request));
    }

    private static AutomaticPlan automaticPlan(
            AtmCurrencyCatalog catalog,
            long amountMinorUnits
    ) {
        if (amountMinorUnits <= 0L) {
            return AutomaticPlan.failed(
                    AtmWithdrawalStatus.INVALID_AMOUNT);
        }
        long smallest = catalog.denominations().get(
                catalog.denominations().size() - 1).valueMinorUnits();
        if (amountMinorUnits < smallest) {
            return AutomaticPlan.failed(
                    AtmWithdrawalStatus.INVALID_AMOUNT);
        }
        long[] values = catalog.denominations().stream()
                .mapToLong(AtmCurrencyCatalog.Denomination::valueMinorUnits)
                .toArray();
        int[] maximumStacks = catalog.denominations().stream()
                .mapToInt(AtmCurrencyCatalog.Denomination::maximumStackSize)
                .toArray();
        CurrencyMath.BreakResult breakdown =
                CurrencyMath.breakIntoDenominations(
                        amountMinorUnits, values, maximumStacks,
                        AtmCurrencyCatalog.MAXIMUM_BILLS);
        if (breakdown.limitExceeded()
                || breakdown.remainderMinor() != 0L) {
            return AutomaticPlan.failed(
                    AtmWithdrawalStatus.INVALID_PLAN);
        }
        List<Integer> mutable = new ArrayList<>(
                java.util.Collections.nCopies(values.length, 0));
        int total = 0;
        for (CurrencyMath.Portion portion : breakdown.portions()) {
            int next = Math.addExact(
                    mutable.get(portion.denominationIndex()),
                    portion.count());
            mutable.set(portion.denominationIndex(), next);
            total = Math.addExact(total, portion.count());
        }
        if (total <= 0 || total > AtmCurrencyCatalog.MAXIMUM_BILLS) {
            return AutomaticPlan.failed(
                    AtmWithdrawalStatus.INVALID_PLAN);
        }
        return AutomaticPlan.valid(mutable);
    }

    private static List<ForeignAtmStackSelection> foreignStacks(
            PhysicalCurrencyAdapter currency,
            AtmCurrencyCatalog catalog,
            List<AtmBillSelection> selections
    ) {
        if (currency.isInternal()
                || !currency.id().equals(catalog.providerId())) {
            throw new AtmPreparationException(
                    AtmWithdrawalStatus.CURRENCY_CHANGED,
                    "Foreign ATM provider changed");
        }
        List<PhysicalCurrencyAdapter.Denomination> configured =
                currency.denominations();
        List<ForeignAtmStackSelection> stacks = new ArrayList<>();
        for (AtmBillSelection selection : selections) {
            if (selection.denominationIndex() >= configured.size()) {
                throw new AtmPreparationException(
                        AtmWithdrawalStatus.CURRENCY_CHANGED,
                        "Foreign ATM denomination disappeared");
            }
            PhysicalCurrencyAdapter.Denomination denomination =
                    configured.get(selection.denominationIndex());
            AtmCurrencyCatalog.Denomination advertised =
                    catalog.denominations().get(
                            selection.denominationIndex());
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(
                    denomination.item());
            int maximumStack = Math.max(1,
                    new ItemStack(denomination.item()).getMaxStackSize());
            if (itemId == null
                    || !itemId.toString().equals(advertised.itemId())
                    || ForeignCashClaimPayload.PROTECTED_ITEM_ID.equals(
                    itemId.toString())
                    || denomination.valueMinor()
                    != advertised.valueMinorUnits()
                    || maximumStack != advertised.maximumStackSize()) {
                throw new AtmPreparationException(
                        AtmWithdrawalStatus.CURRENCY_CHANGED,
                        "Foreign ATM denomination changed");
            }
            int portionCount = Math.floorDiv(
                    Math.addExact(selection.billCount(),
                            maximumStack - 1), maximumStack);
            int remaining = selection.billCount();
            for (int portionIndex = 0;
                 portionIndex < portionCount;
                 portionIndex++) {
                int count = Math.min(remaining, maximumStack);
                ItemStack stack = new ItemStack(
                        denomination.item(), count);
                stacks.add(new ForeignAtmStackSelection(
                        selection.denominationIndex(), itemId.toString(),
                        denomination.valueMinor(), count,
                        portionIndex, portionCount,
                        ItemStackSnapshotCodec.encode(stack)));
                remaining = Math.subtractExact(remaining, count);
            }
            if (remaining != 0) {
                throw new IllegalStateException(
                        "Foreign ATM stack plan is incomplete");
            }
        }
        return List.copyOf(stacks);
    }

    private static AtmAccessSnapshot unavailable(
            AtmCurrencyCatalog catalog,
            String code
    ) {
        return new AtmAccessSnapshot(
                catalog, false, 0L, false, code);
    }

    private static final class LiveBackend
            implements AtmWithdrawalBackend {
        private final EscrowRuntimeService runtime;

        private LiveBackend(EscrowRuntimeService runtime) {
            this.runtime = runtime;
        }

        @Override
        public Optional<com.enviouse.futureshops.server.escrow.model.EscrowTransaction>
        transaction(UUID requestId) {
            return runtime == null
                    ? Optional.empty()
                    : runtime.transaction(requestId);
        }

        @Override
        public List<com.enviouse.futureshops.server.escrow.claim.EscrowClaim>
        claims(UUID requestId) {
            return runtime == null
                    ? List.of()
                    : runtime.claimsForTransaction(requestId);
        }

        @Override
        public long balance(UUID playerId) {
            return BalanceManager.getBalance(playerId);
        }

        @Override
        public boolean migrationComplete() {
            return LegacyBalanceMigrationManager.isComplete();
        }

        @Override
        public EscrowRuntimeState runtimeState() {
            return runtime == null
                    ? EscrowRuntimeState.STOPPED
                    : runtime.state();
        }

        @Override
        public EscrowCommitResult commitTransaction(
                com.enviouse.futureshops.server.escrow.model.EscrowTransaction transaction
        ) {
            return requireRuntime().commitTransaction(transaction);
        }

        @Override
        public EscrowCommitResult commitProtected(
                AtmWithdrawalCommit commit
        ) {
            return requireRuntime().commitAtmWithdrawal(commit);
        }

        @Override
        public EscrowCommitResult commitForeign(
                ForeignAtmWithdrawalCommit commit
        ) {
            return requireRuntime().commitAtmWithdrawal(commit);
        }

        private EscrowRuntimeService requireRuntime() {
            if (runtime == null) {
                throw new EscrowRuntimeException(
                        "Escrow runtime is unavailable");
            }
            return runtime;
        }
    }

    private static final class LiveCurrencyConfigurationLease
            implements AtmCurrencyConfigurationLease {
        private final CurrencyManager.ConfigurationReadLease managerLease;
        private final long generation;
        private final String signature;

        private LiveCurrencyConfigurationLease(
                CurrencyManager.ConfigurationReadLease managerLease,
                long generation,
                String signature
        ) {
            this.managerLease = managerLease;
            this.generation = generation;
            this.signature = signature;
        }

        @Override
        public long generation() {
            return generation;
        }

        @Override
        public String currencySignature() {
            return signature;
        }

        @Override
        public void close() {
            managerLease.close();
        }
    }

    private static final class LiveEvents
            implements AtmBalanceEventGateway {
        @Override
        public boolean beforeDebit(
                UUID playerId,
                long amountMinorUnits,
                long balanceBefore
        ) {
            try {
                return MinecraftForge.EVENT_BUS.post(
                        new BalanceChangeEvent.Pre(
                                playerId,
                                Math.negateExact(amountMinorUnits),
                                "WITHDRAW",
                                balanceBefore));
            } catch (RuntimeException exception) {
                return true;
            }
        }

        @Override
        public void afterDebit(
                UUID playerId,
                long amountMinorUnits,
                long balanceAfter
        ) {
            try {
                MinecraftForge.EVENT_BUS.post(
                        new BalanceChangeEvent.Post(
                                playerId,
                                Math.negateExact(amountMinorUnits),
                                "WITHDRAW",
                                balanceAfter));
            } catch (RuntimeException ignored) {
            }
        }
    }

    private record AutomaticPlan(
            boolean valid,
            AtmWithdrawalStatus status,
            List<Integer> counts
    ) {
        private AutomaticPlan {
            Objects.requireNonNull(status, "status");
            counts = List.copyOf(Objects.requireNonNull(counts, "counts"));
        }

        private static AutomaticPlan valid(List<Integer> counts) {
            return new AutomaticPlan(
                    true, AtmWithdrawalStatus.CLAIMED, counts);
        }

        private static AutomaticPlan failed(AtmWithdrawalStatus status) {
            return new AutomaticPlan(false, status, List.of());
        }
    }
}

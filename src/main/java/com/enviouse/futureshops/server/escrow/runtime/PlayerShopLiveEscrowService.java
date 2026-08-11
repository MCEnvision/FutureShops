package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.money.AtmCurrencyCatalog;
import com.enviouse.futureshops.money.CurrencyManager;
import com.enviouse.futureshops.money.ItemStackSnapshotCodec;
import com.enviouse.futureshops.money.PhysicalCurrencyAdapter;
import com.enviouse.futureshops.server.economy.BalanceManager;
import com.enviouse.futureshops.server.escrow.claim.ClaimKind;
import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.item.ExactItemClaimPayload;
import com.enviouse.futureshops.server.escrow.item.ExactItemClaimPayloadCodec;
import com.enviouse.futureshops.server.escrow.item.ItemInputMatcher;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryBatchEntry;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryExecutionResult;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryExecutionStatus;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountId;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountType;
import com.enviouse.futureshops.server.escrow.ledger.LedgerLeg;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;
import com.enviouse.futureshops.server.escrow.model.CashDepositMode;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopAssetEndpoint;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopAtomicCommit;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopBackendException;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopClaimCreationEvidence;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopClaimPlan;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopEscrowBackend;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopEscrowIntent;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopEscrowLifecycleEvent;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopEscrowOrchestrator;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopExecutionSnapshot;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopFundingEvidence;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopItemLot;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopItemMutationReceipt;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopItemTransfer;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopMoneyMutationReceipt;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopMoneyTransfer;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopMutationPreparation;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopOperation;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopPreparedExecution;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopRequestIdentity;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopSettlementImportEvidence;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopStorageCustodyReceipt;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopStorageMutationPlan;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

public final class PlayerShopLiveEscrowService {
    private static final org.slf4j.Logger LOGGER =
            com.mojang.logging.LogUtils.getLogger();

    private PlayerShopLiveEscrowService() {
    }

    public static PlayerShopEscrowOrchestrator.Result execute(
            ServerPlayer actor,
            PlayerShopEscrowIntent intent,
            int responseToken,
            StorageAccess storage
    ) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(storage, "storage");
        if (!actor.getUUID().equals(intent.actorId())) {
            throw new IllegalArgumentException(
                    "Player shop actor does not match the intent");
        }
        EscrowRuntimeService runtime = EscrowRuntimeManager.requireReady();
        LiveDriver driver = new LiveDriver(runtime, actor, storage);
        RuntimePlayerShopEscrowBackend backend =
                new RuntimePlayerShopEscrowBackend(runtime, driver);
        return new PlayerShopEscrowOrchestrator(backend,
                Clock.systemUTC()).execute(
                PlayerShopEscrowOrchestrator.Command.of(intent,
                        responseToken));
    }

    public static Optional<PlayerShopEscrowIntent> existingIntent(
            ServerPlayer actor, UUID requestId) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(requestId, "requestId");
        return EscrowRuntimeManager.requireReady()
                .playerShopEscrowEntry(requestId)
                .map(PlayerShopEscrowSavedData.Entry::snapshot)
                .map(PlayerShopExecutionSnapshot::intent)
                .filter(value -> value.actorId().equals(actor.getUUID()));
    }

    public interface StorageAccess {
        boolean revalidate(PlayerShopEscrowIntent intent);

        boolean canExtract(PlayerShopStorageMutationPlan plan);

        StorageObservation observe(PlayerShopStorageMutationPlan plan);

        StorageMutationResult extract(PlayerShopStorageMutationPlan plan);

        StorageMutationResult insert(
                PlayerShopStorageMutationPlan plan,
                ItemStack stack
        );

        boolean applyBuybackCounter(PlayerShopEscrowIntent intent);
    }

    public record StorageObservation(
            StorageState state,
            String beforeFingerprint,
            String afterFingerprint
    ) {
        public StorageObservation {
            state = Objects.requireNonNull(state, "state");
            beforeFingerprint = requireText(beforeFingerprint,
                    "beforeFingerprint");
            afterFingerprint = requireText(afterFingerprint,
                    "afterFingerprint");
        }
    }

    public record StorageMutationResult(
            StorageMutationStatus status,
            String beforeFingerprint,
            String afterFingerprint,
            byte[] evidence,
            String detail
    ) {
        public StorageMutationResult {
            status = Objects.requireNonNull(status, "status");
            beforeFingerprint = requireText(beforeFingerprint,
                    "beforeFingerprint");
            afterFingerprint = requireText(afterFingerprint,
                    "afterFingerprint");
            evidence = Objects.requireNonNull(evidence, "evidence").clone();
            detail = Objects.requireNonNull(detail, "detail");
            if (evidence.length == 0 || evidence.length > 1_048_576
                    || status == StorageMutationStatus.APPLIED
                    && !detail.isEmpty()
                    || status != StorageMutationStatus.APPLIED
                    && detail.isBlank()) {
                throw new IllegalArgumentException(
                        "Player shop storage mutation result is invalid");
            }
        }

        @Override
        public byte[] evidence() {
            return evidence.clone();
        }
    }

    public enum StorageState {
        BEFORE,
        AFTER,
        UNKNOWN
    }

    public enum StorageMutationStatus {
        APPLIED,
        REJECTED,
        RECOVERY_REQUIRED
    }

    private static final class LiveDriver
            implements RuntimePlayerShopEscrowBackend.MutationDriver {
        private final EscrowRuntimeService runtime;
        private final ServerPlayer actor;
        private final StorageAccess storage;

        private LiveDriver(
                EscrowRuntimeService runtime,
                ServerPlayer actor,
                StorageAccess storage
        ) {
            this.runtime = Objects.requireNonNull(runtime, "runtime");
            this.actor = Objects.requireNonNull(actor, "actor");
            this.storage = Objects.requireNonNull(storage, "storage");
        }

        @Override
        public PlayerShopPreparedExecution prepare(
                PlayerShopRequestIdentity requestIdentity,
                PlayerShopEscrowIntent intent
        ) {
            if (!storage.revalidate(intent)) {
                throw rejected("Player shop quote changed");
            }
            for (PlayerShopStorageMutationPlan plan
                    : intent.storageMutations()) {
                if (plan.direction()
                        == PlayerShopStorageMutationPlan.Direction.EXTRACT
                        && !storage.canExtract(plan)) {
                    throw rejected("Player shop stock changed");
                }
            }
            for (PlayerShopMoneyTransfer transfer
                    : intent.moneyTransfers()) {
                long expected = transfer.sourceBalanceBeforeMinorUnits();
                if (transfer.paymentSource()
                        == com.enviouse.futureshops.server.escrow.playershop
                        .PlayerShopPaymentSource.INVENTORY_CASH
                        && expected
                        != PlayerShopMoneyTransfer.BALANCE_NOT_APPLICABLE) {
                    expected = Math.subtractExact(expected,
                            transfer.amountMinorUnits());
                }
                if (expected
                        != PlayerShopMoneyTransfer.BALANCE_NOT_APPLICABLE
                        && runtime.ledgerBalance(
                        moneySourceAccount(transfer)) != expected) {
                    throw rejected(
                            "Player shop wallet balance changed");
                }
            }
            List<PlayerShopMutationPreparation> mutations =
                    new ArrayList<>();
            for (PlayerShopMoneyTransfer transfer : intent.moneyTransfers()) {
                mutations.add(PlayerShopMutationPreparation.money(transfer,
                        bytes("money." + transfer.transferId())));
            }
            for (PlayerShopItemTransfer transfer : intent.itemTransfers()) {
                mutations.add(PlayerShopMutationPreparation.item(transfer,
                        bytes("item." + transfer.transferId())));
            }
            for (PlayerShopStorageMutationPlan plan
                    : intent.storageMutations()) {
                mutations.add(PlayerShopMutationPreparation.storage(plan,
                        bytes("storage." + plan.mutationId())));
            }
            Instant preparedAt = Instant.now();
            if (preparedAt.isBefore(intent.quoteCreatedAt())) {
                preparedAt = intent.quoteCreatedAt();
            }
            return PlayerShopPreparedExecution.create(requestIdentity,
                    intent, preparedAt, mutations);
        }

        @Override
        public PlayerShopFundingEvidence commitFunding(
                PlayerShopPreparedExecution preparation,
                PlayerShopFundingEvidence existing,
                RuntimePlayerShopEscrowBackend.FundingProgress progress
        ) {
            PlayerShopEscrowIntent intent = preparation.intent();
            Progress funding = Progress.from(intent, existing);
            PhysicalFunding physical = fundPhysicalCash(intent);
            fundMoney(intent, funding, progress, physical);
            fundActorInventory(intent, funding, progress);
            fundStorageOutputs(intent, funding, progress);
            fundAdminOutputs(intent, funding, progress);
            prepareStorageInsertions(intent, funding, progress);
            if ((intent.operation() == PlayerShopOperation.BUYBACK
                    || intent.operation()
                    == PlayerShopOperation.ADMIN_BUYBACK)
                    && !storage.applyBuybackCounter(intent)) {
                throw recovery("Player shop buyback counter changed");
            }
            PlayerShopFundingEvidence complete = funding.complete();
            progress.persist(complete);
            return complete;
        }

        @Override
        public PlayerShopClaimCreationEvidence createClaims(
                PlayerShopPreparedExecution preparation,
                PlayerShopFundingEvidence funding
        ) {
            PlayerShopEscrowIntent intent = preparation.intent();
            boolean replayed = false;
            for (PlayerShopClaimPlan plan : intent.claims()) {
                EscrowClaim expected = claim(intent, preparation, plan);
                Optional<EscrowClaim> current = runtime.claim(plan.claimId());
                if (current.isPresent()) {
                    if (!current.orElseThrow().equals(expected)) {
                        throw conflict("Player shop claim conflicts");
                    }
                    replayed = true;
                } else {
                    replayed |= runtime.createClaim(expected).replayed();
                }
            }
            return new PlayerShopClaimCreationEvidence(intent.requestId(),
                    replayed
                            ? PlayerShopClaimCreationEvidence.Status.IDEMPOTENT_REPLAY
                            : PlayerShopClaimCreationEvidence.Status.CREATED,
                    intent.claims(), intent.intentFingerprint(), "");
        }

        @Override
        public PlayerShopEscrowBackend.DeliveryResult deliverClaims(
                PlayerShopAtomicCommit commit,
                PlayerShopPreparedExecution preparation
        ) {
            DeliveryAggregate delivery = new DeliveryAggregate();
            deliverStorageClaims(commit, delivery);
            PlayerShopExecutionSnapshot current = runtime
                    .playerShopEscrowEntry(commit.commitId()).orElseThrow()
                    .snapshot();
            for (PlayerShopClaimPlan plan
                    : current.commit().createdClaims()) {
                if (isStorageClaim(current.intent(), plan.claimId())) {
                    continue;
                }
                if (plan.kind() == PlayerShopClaimPlan.Kind.MONEY) {
                    deliverMoney(plan, delivery);
                } else {
                    deliverItem(plan, delivery);
                }
            }
            return delivery.result();
        }

        @Override
        public PlayerShopEscrowBackend.RecoveryResult recover(
                PlayerShopExecutionSnapshot snapshot
        ) {
            if (!storage.revalidate(snapshot.intent())) {
                return new PlayerShopEscrowBackend.RecoveryResult(
                        PlayerShopEscrowBackend.RecoveryStatus
                                .RECOVERY_REQUIRED,
                        "Player shop evidence changed during recovery");
            }
            return new PlayerShopEscrowBackend.RecoveryResult(
                    PlayerShopEscrowBackend.RecoveryStatus.RESUMABLE,
                    "Player shop escrow can resume");
        }

        @Override
        public void markSettlementImported(
                PlayerShopSettlementImportEvidence settlement,
                PlayerShopAtomicCommit commit
        ) {
            throw conflict("Live player shop trade is not a settlement");
        }

        private void fundAdminOutputs(
                PlayerShopEscrowIntent intent,
                Progress funding,
                RuntimePlayerShopEscrowBackend.FundingProgress progress
        ) {
            for (PlayerShopItemTransfer transfer : intent.itemTransfers()) {
                if (transfer.source().kind()
                        != PlayerShopAssetEndpoint.Kind.ADMIN_MINT
                        || funding.item(transfer.transferId()).isPresent()) {
                    continue;
                }
                funding.put(PlayerShopItemMutationReceipt.funded(
                        intent.requestId(), transfer,
                        PlayerShopItemMutationReceipt.FundingKind.ADMIN_MINT,
                        bytes("admin.mint." + transfer.lot().fingerprint())));
                progress.persist(funding.partial(
                        "Player shop admin output is funded"));
            }
        }

        private void fundStorageOutputs(
                PlayerShopEscrowIntent intent,
                Progress funding,
                RuntimePlayerShopEscrowBackend.FundingProgress progress
        ) {
            for (PlayerShopStorageMutationPlan plan
                    : intent.storageMutations()) {
                if (plan.direction()
                        != PlayerShopStorageMutationPlan.Direction.EXTRACT) {
                    continue;
                }
                PlayerShopStorageCustodyReceipt receipt = funding
                        .storage(plan.mutationId()).orElse(null);
                if (receipt == null) {
                    receipt = PlayerShopStorageCustodyReceipt.prepared(
                            intent.requestId(), plan, Instant.now());
                    funding.put(receipt);
                    progress.persist(funding.partial(
                            "Player shop storage extraction is prepared"));
                }
                if (receipt.state()
                        == PlayerShopStorageCustodyReceipt.RecoveryState.APPLIED) {
                    ensureStorageItemReceipt(intent, plan, funding,
                            receipt.adapterReceipt(), progress);
                    continue;
                }
                StorageObservation observation = storage.observe(plan);
                if (observation.state() == StorageState.UNKNOWN) {
                    throw recovery(
                            "Player shop storage extraction is uncertain");
                }
                byte[] evidence;
                if (observation.state() == StorageState.BEFORE) {
                    StorageMutationResult result = storage.extract(plan);
                    if (result.status() != StorageMutationStatus.APPLIED) {
                        throw result.status()
                                == StorageMutationStatus.REJECTED
                                ? rejected(result.detail())
                                : recovery(result.detail());
                    }
                    observation = new StorageObservation(StorageState.AFTER,
                            result.beforeFingerprint(),
                            result.afterFingerprint());
                    evidence = result.evidence();
                } else {
                    evidence = bytes("recovered.storage."
                            + plan.mutationId());
                }
                PlayerShopStorageCustodyReceipt applied = receipt.applied(
                        observation.beforeFingerprint(),
                        observation.afterFingerprint(), evidence,
                        Instant.now());
                funding.put(applied);
                ensureStorageItemReceipt(intent, plan, funding, evidence,
                        progress);
                progress.persist(funding.partial(
                        "Player shop storage output is funded"));
            }
        }

        private void ensureStorageItemReceipt(
                PlayerShopEscrowIntent intent,
                PlayerShopStorageMutationPlan plan,
                Progress funding,
                byte[] evidence,
                RuntimePlayerShopEscrowBackend.FundingProgress progress
        ) {
            if (funding.item(plan.itemTransferId()).isPresent()) {
                return;
            }
            PlayerShopItemTransfer transfer = intent.itemTransfers().stream()
                    .filter(value -> value.transferId().equals(
                            plan.itemTransferId())).findFirst().orElseThrow();
            funding.put(PlayerShopItemMutationReceipt.funded(
                    intent.requestId(), transfer,
                    PlayerShopItemMutationReceipt.FundingKind
                            .STORAGE_EXTRACTION, evidence));
            progress.persist(funding.partial(
                    "Player shop storage item custody is funded"));
        }

        private void fundActorInventory(
                PlayerShopEscrowIntent intent,
                Progress funding,
                RuntimePlayerShopEscrowBackend.FundingProgress progress
        ) {
            List<PlayerShopItemTransfer> transfers = intent.itemTransfers()
                    .stream().filter(value -> value.source().kind()
                            == PlayerShopAssetEndpoint.Kind.ACTOR_INVENTORY)
                    .toList();
            if (transfers.isEmpty() || transfers.stream().allMatch(value ->
                    funding.item(value.transferId()).isPresent())) {
                return;
            }
            List<ItemInventoryBatchEntry> entries = new ArrayList<>();
            for (PlayerShopItemTransfer transfer : transfers) {
                ItemStack stack = ItemStackSnapshotCodec.decode(
                        transfer.lot().serializedExactStack());
                stack.setCount(1);
                ItemInputMatcher matcher = transfer.lot().matchMode()
                        == com.enviouse.futureshops.server.escrow.playershop
                        .PlayerShopItemMatchMode.EXACT
                        ? ItemInputMatcher.exact(stack)
                        : ItemInputMatcher.itemOnly(
                        transfer.lot().itemId());
                entries.add(ItemInventoryBatchEntry.extract(
                        transfer.transferId(), matcher,
                        transfer.lot().quantity()));
            }
            UUID custodyRequest = deterministicId("inventory custody",
                    intent.requestId());
            ServerShopSellItemCustody custody =
                    runtime.serverShopSellCustody(actor);
            ItemInventoryExecutionResult result = custody.extract(
                    intent.requestId(), custodyRequest, entries);
            if (result.status() != ItemInventoryExecutionStatus.APPLIED
                    && result.status()
                    != ItemInventoryExecutionStatus.REPLAYED) {
                LOGGER.error(
                        "Player shop inventory custody failed for request {} with custody request {} and status {}",
                        intent.requestId(), custodyRequest, result.status());
                if (result.status()
                        == ItemInventoryExecutionStatus.INSUFFICIENT_ITEMS) {
                    throw rejected("Player shop input items are missing");
                }
                if (result.status()
                        == ItemInventoryExecutionStatus.MANUAL_REVIEW) {
                    throw new PlayerShopBackendException(
                            PlayerShopBackendException.Kind.QUARANTINED,
                            "Player shop inventory custody is quarantined");
                }
                throw recovery(
                        "Player shop inventory custody requires recovery");
            }
            byte[] evidence = result.receipt().map(value -> value.digest())
                    .orElseGet(() -> result.token().orElseThrow().digest());
            for (PlayerShopItemTransfer transfer : transfers) {
                funding.put(PlayerShopItemMutationReceipt.funded(
                        intent.requestId(), transfer,
                        PlayerShopItemMutationReceipt.FundingKind
                                .INVENTORY_REMOVAL, evidence));
            }
            progress.persist(funding.partial(
                    "Player shop inventory input is funded"));
        }

        private PhysicalFunding fundPhysicalCash(
                PlayerShopEscrowIntent intent) {
            if (intent.moneyTransfers().stream().noneMatch(value ->
                    value.paymentSource()
                            == com.enviouse.futureshops.server.escrow.playershop
                            .PlayerShopPaymentSource.INVENTORY_CASH)) {
                return PhysicalFunding.none();
            }
            List<PlayerShopMoneyTransfer> transfers = intent
                    .moneyTransfers().stream().filter(value ->
                            value.paymentSource()
                                    == com.enviouse.futureshops.server.escrow.playershop
                                    .PlayerShopPaymentSource.INVENTORY_CASH)
                    .toList();
            if (transfers.size() != 1) {
                throw conflict(
                        "Player shop physical funding shape is invalid");
            }
            long total = intent.moneyTransfers().stream().filter(value ->
                    value.paymentSource()
                            == com.enviouse.futureshops.server.escrow.playershop
                            .PlayerShopPaymentSource.INVENTORY_CASH)
                    .mapToLong(PlayerShopMoneyTransfer::amountMinorUnits)
                    .reduce(0L, Math::addExact);
            UUID fundingRequest = deterministicId("physical funding",
                    intent.requestId());
            try (CurrencyManager.ConfigurationReadLease ignored =
                         CurrencyManager.acquireConfigurationReadLease()) {
                PhysicalCurrencyAdapter adapter = CurrencyManager.get();
                AtmCurrencyCatalog catalog = AtmCurrencyCatalog.capture(
                        adapter, BalanceManager.getProvider());
                EscrowCashDepositService.DepositResult result =
                        EscrowCashDepositService.depositForEscrow(actor,
                                new EscrowCashDepositService.DepositRequest(
                                        fundingRequest, catalog.signature(),
                                        EscrowCashDepositService.Source
                                                .INVENTORY,
                                        OptionalLong.of(total),
                                        CashDepositMode.INTERNAL_ESCROW));
                if (result.status()
                        != EscrowCashDepositService.Status.SUCCESS) {
                    throw result.status()
                            == EscrowCashDepositService.Status.NO_CURRENCY
                            || result.status()
                            == EscrowCashDepositService.Status
                            .NOT_ENOUGH_CURRENCY
                            || result.status()
                            == EscrowCashDepositService.Status
                            .INVALID_DENOMINATION
                            ? rejected(
                            "Player shop physical money is insufficient")
                            : recovery(
                            "Player shop physical funding requires recovery");
                }
                if (result.depositedMinorUnits() != total
                        || result.walletCreditMinorUnits() != 0L
                        || result.overflowClaimMinorUnits() != total) {
                    throw recovery(
                            "Player shop physical funding did not enter custody");
                }
                UUID transactionId = result.transactionId().orElseThrow();
                List<EscrowClaim> claims = runtime.claimsForTransaction(
                        transactionId).stream().filter(value ->
                        internalPhysicalFundingClaim(value,
                                actor.getUUID(), transactionId))
                        .toList();
                long claimed = claims.stream()
                        .mapToLong(EscrowClaim::originalUnits)
                        .reduce(0L, Math::addExact);
                if (claims.isEmpty() || claimed != total) {
                    throw recovery(
                            "Player shop physical funding claim is missing");
                }
                return new PhysicalFunding(transactionId,
                        transfers.get(0).transferId(), claims, total);
            }
        }

        private void fundMoney(
                PlayerShopEscrowIntent intent,
                Progress funding,
                RuntimePlayerShopEscrowBackend.FundingProgress progress,
                PhysicalFunding physical
        ) {
            for (PlayerShopMoneyTransfer transfer : intent.moneyTransfers()) {
                if (funding.money(transfer.transferId()).isPresent()) {
                    continue;
                }
                LedgerTransaction ledger = transfer.paymentSource()
                        == com.enviouse.futureshops.server.escrow.playershop
                        .PlayerShopPaymentSource.INVENTORY_CASH
                        ? physicalMoneyLedger(intent, transfer, physical)
                        : moneyLedger(intent, transfer);
                if (runtime.wasLedgerTransactionApplied(
                        ledger.transactionId())) {
                    if (runtime.ledgerTransaction(ledger.transactionId())
                            .filter(ledger::equals).isEmpty()) {
                        throw conflict(
                                "Player shop money receipt conflicts");
                    }
                } else {
                    if (transfer.paymentSource()
                            == com.enviouse.futureshops.server.escrow.playershop
                            .PlayerShopPaymentSource.INVENTORY_CASH) {
                        validatePhysicalPreimage(physical);
                    } else {
                        validateMoneyPreimage(transfer);
                    }
                    runtime.commitLedger(ledger);
                }
                if (transfer.paymentSource()
                        == com.enviouse.futureshops.server.escrow.playershop
                        .PlayerShopPaymentSource.INVENTORY_CASH) {
                    completePhysicalFundingClaims(physical, transfer);
                }
                PlayerShopMoneyMutationReceipt receipt =
                        PlayerShopMoneyMutationReceipt.applied(
                                intent.requestId(), transfer,
                                transfer.sourceBalanceAfterMinorUnits(),
                                transfer.destinationBalanceAfterMinorUnits(),
                                ledger.fingerprint().getBytes(
                                        StandardCharsets.UTF_8));
                funding.put(receipt);
                progress.persist(funding.partial(
                        "Player shop money is funded"));
            }
        }

        private LedgerTransaction physicalMoneyLedger(
                PlayerShopEscrowIntent intent,
                PlayerShopMoneyTransfer transfer,
                PhysicalFunding physical
        ) {
            if (physical.empty()
                    || !physical.transferId().equals(transfer.transferId())
                    || physical.amountMinorUnits()
                    != transfer.amountMinorUnits()) {
                throw conflict(
                        "Player shop physical funding evidence conflicts");
            }
            List<LedgerLeg> legs = new ArrayList<>();
            for (EscrowClaim claim : physical.claims()) {
                legs.add(new LedgerLeg(new LedgerAccountId(
                        LedgerAccountType.PLAYER_CLAIM,
                        claim.claimId().toString()),
                        Math.negateExact(claim.originalUnits())));
            }
            legs.add(new LedgerLeg(moneyDestinationAccount(transfer),
                    transfer.amountMinorUnits()));
            return new LedgerTransaction(transfer.transferId(),
                    "player.shop.physical.money." + intent.requestId()
                            + "." + transfer.transferId(),
                    "Player shop physical escrow money", legs);
        }

        private void validatePhysicalPreimage(
                PhysicalFunding physical) {
            for (EscrowClaim claim : physical.claims()) {
                long balance = runtime.ledgerBalance(
                        new LedgerAccountId(
                                LedgerAccountType.PLAYER_CLAIM,
                                claim.claimId().toString()));
                if (balance != claim.originalUnits()
                        || claim.status() == ClaimStatus.COMPLETED) {
                    throw recovery(
                            "Player shop physical custody changed");
                }
            }
        }

        private void completePhysicalFundingClaims(
                PhysicalFunding physical,
                PlayerShopMoneyTransfer transfer
        ) {
            for (EscrowClaim evidence : physical.claims()) {
                EscrowClaim claim = runtime.claim(evidence.claimId())
                        .orElseThrow(() -> recovery(
                                "Player shop physical claim is missing"));
                if (!internalPhysicalFundingClaim(claim, actor.getUUID(),
                        physical.transactionId())) {
                    throw conflict(
                            "Player shop physical claim kind conflicts");
                }
                if (claim.status() == ClaimStatus.COMPLETED) {
                    continue;
                }
                ClaimDeliveryCommit delivery = new ClaimDeliveryCommit(
                        claim.ownerId(), claim.claimId(),
                        "player.shop.physical.custody."
                                + transfer.transferId() + "."
                                + claim.claimId(),
                        claim.remainingUnits(), Instant.now());
                runtime.deliverPlayerShopStorageClaim(
                        claim.transactionId(), delivery);
            }
        }

        private void validateMoneyPreimage(PlayerShopMoneyTransfer transfer) {
            if (transfer.sourceBalanceBeforeMinorUnits()
                    == PlayerShopMoneyTransfer.BALANCE_NOT_APPLICABLE) {
                return;
            }
            LedgerAccountId source = moneySourceAccount(transfer);
            if (runtime.ledgerBalance(source)
                    != transfer.sourceBalanceBeforeMinorUnits()) {
                throw rejected("Player shop wallet balance changed");
            }
            if (transfer.destination().kind()
                    == PlayerShopAssetEndpoint.Kind.MONEY_CLAIM
                    && runtime.ledgerBalance(moneyDestinationAccount(
                    transfer))
                    != transfer.destinationBalanceBeforeMinorUnits()) {
                throw conflict("Player shop money claim is not empty");
            }
        }

        private void prepareStorageInsertions(
                PlayerShopEscrowIntent intent,
                Progress funding,
                RuntimePlayerShopEscrowBackend.FundingProgress progress
        ) {
            for (PlayerShopStorageMutationPlan plan
                    : intent.storageMutations()) {
                if (plan.direction()
                        != PlayerShopStorageMutationPlan.Direction.INSERT
                        || funding.storage(plan.mutationId()).isPresent()) {
                    continue;
                }
                funding.put(PlayerShopStorageCustodyReceipt.prepared(
                        intent.requestId(), plan, Instant.now()));
                progress.persist(funding.partial(
                        "Player shop storage delivery is prepared"));
            }
        }

        private EscrowClaim claim(
                PlayerShopEscrowIntent intent,
                PlayerShopPreparedExecution preparation,
                PlayerShopClaimPlan plan
        ) {
            byte[] payload = new byte[0];
            ClaimKind kind = ClaimKind.MONEY;
            if (plan.kind() == PlayerShopClaimPlan.Kind.EXACT_ITEM) {
                PlayerShopItemLot lot = plan.itemLot();
                ExactItemClaimPayload exact =
                        ExactItemClaimPayload.preserveRaw(
                                intent.requestId(), lot.sourceKey(),
                                lot.portionIndex(), lot.portionCount(),
                                lot.itemId(), lot.quantity(),
                                lot.canonicalOneCountTemplate(),
                                lot.serializedExactStack());
                payload = ExactItemClaimPayloadCodec.encode(exact);
                kind = plan.beneficiaryId().equals(intent.ownerId())
                        ? ClaimKind.BARTER_ITEM : ClaimKind.ITEM;
            }
            long units = plan.kind() == PlayerShopClaimPlan.Kind.MONEY
                    ? plan.moneyAmountMinorUnits()
                    : plan.itemLot().quantity();
            Instant createdAt = preparation.preparedAt();
            return new EscrowClaim(plan.claimId(), intent.requestId(),
                    plan.beneficiaryId(),
                    "player.shop." + intent.requestId() + "."
                            + plan.claimId(), kind, units, units, payload,
                    ClaimStatus.PENDING, plan.purpose(), createdAt,
                    createdAt);
        }

        private void deliverStorageClaims(
                PlayerShopAtomicCommit commit,
                DeliveryAggregate delivery
        ) {
            for (PlayerShopStorageMutationPlan plan
                    : commit.committedIntent().storageMutations()) {
                if (plan.direction()
                        != PlayerShopStorageMutationPlan.Direction.INSERT) {
                    continue;
                }
                EscrowClaim claim = runtime.claim(plan.claimId())
                        .orElseThrow(() -> recovery(
                                "Player shop storage claim is missing"));
                if (claim.status() == ClaimStatus.COMPLETED) {
                    continue;
                }
                PlayerShopEscrowSavedData.Entry entry = runtime
                        .playerShopEscrowEntry(commit.commitId())
                        .orElseThrow();
                PlayerShopStorageCustodyReceipt receipt = entry.snapshot()
                        .funding().storageReceipts().stream().filter(value ->
                                value.plan().mutationId().equals(
                                        plan.mutationId())).findFirst()
                        .orElseThrow();
                if (receipt.state()
                        != PlayerShopStorageCustodyReceipt.RecoveryState.APPLIED) {
                    StorageObservation observation = storage.observe(plan);
                    byte[] evidence;
                    if (observation.state() == StorageState.UNKNOWN) {
                        delivery.recovery = true;
                        continue;
                    }
                    if (observation.state() == StorageState.BEFORE) {
                        ExactItemClaimPayload payload =
                                ExactItemClaimDeliveryPlanner.payload(claim);
                        ItemStack stack = payload.resolve().resolvedStack()
                                .orElseThrow(() -> recovery(
                                        "Player shop storage claim payload is invalid"));
                        StorageMutationResult result = storage.insert(plan,
                                stack);
                        if (result.status()
                                != StorageMutationStatus.APPLIED) {
                            if (result.status()
                                    == StorageMutationStatus.REJECTED) {
                                delivery.pending = true;
                            } else {
                                delivery.recovery = true;
                            }
                            continue;
                        }
                        observation = new StorageObservation(
                                StorageState.AFTER,
                                result.beforeFingerprint(),
                                result.afterFingerprint());
                        evidence = result.evidence();
                    } else {
                        evidence = bytes("recovered.delivery."
                                + plan.mutationId());
                    }
                    PlayerShopStorageCustodyReceipt applied =
                            receipt.applied(
                                    observation.beforeFingerprint(),
                                    observation.afterFingerprint(), evidence,
                                    Instant.now());
                    persistStorageDelivery(commit.commitId(), applied);
                }
                EscrowClaim current = runtime.claim(plan.claimId())
                        .orElseThrow();
                if (current.status() != ClaimStatus.COMPLETED) {
                    Instant now = Instant.now();
                    ClaimDeliveryCommit claimDelivery =
                            new ClaimDeliveryCommit(current.ownerId(),
                                    current.claimId(),
                                    "player.shop.storage.delivery."
                                            + plan.mutationId(),
                                    current.remainingUnits(), now);
                    runtime.deliverPlayerShopStorageClaim(
                            current.transactionId(), claimDelivery);
                }
            }
        }

        private void persistStorageDelivery(
                UUID requestId,
                PlayerShopStorageCustodyReceipt applied
        ) {
            PlayerShopEscrowSavedData.Entry entry = runtime
                    .playerShopEscrowEntry(requestId).orElseThrow();
            PlayerShopExecutionSnapshot snapshot = entry.snapshot();
            PlayerShopFundingEvidence funding = snapshot.funding();
            List<PlayerShopStorageCustodyReceipt> receipts =
                    new ArrayList<>(funding.storageReceipts());
            for (int index = 0; index < receipts.size(); index++) {
                if (receipts.get(index).plan().mutationId().equals(
                        applied.plan().mutationId())) {
                    if (receipts.get(index).equals(applied)) {
                        return;
                    }
                    receipts.set(index, applied);
                    PlayerShopFundingEvidence updatedFunding =
                            PlayerShopFundingEvidence.complete(requestId,
                                    funding.moneyReceipts(),
                                    funding.itemReceipts(), receipts);
                    PlayerShopAtomicCommit updatedCommit =
                            PlayerShopAtomicCommit.create(snapshot.intent(),
                                    snapshot.commit().committedAt(),
                                    updatedFunding.moneyReceipts(),
                                    updatedFunding.itemReceipts(),
                                    updatedFunding.storageReceipts());
                    PlayerShopExecutionSnapshot updated = snapshot
                            .withFunding(updatedFunding)
                            .withClaims(snapshot.claimCreation())
                            .withCommit(updatedCommit);
                    runtime.commitPlayerShopEscrowLifecycle(
                            PlayerShopEscrowLifecycleEvent.advance(updated,
                                    entry.revision(),
                                    entry.settlementImported()));
                    return;
                }
            }
            throw conflict("Player shop storage receipt is missing");
        }

        private void deliverMoney(
                PlayerShopClaimPlan plan,
                DeliveryAggregate delivery
        ) {
            ServerPlayer beneficiary = actor.getServer() == null
                    ? null : actor.getServer().getPlayerList().getPlayer(
                    plan.beneficiaryId());
            if (beneficiary == null) {
                delivery.pending = true;
                return;
            }
            EscrowMoneyClaimService.CollectionResult result =
                    EscrowMoneyClaimService.collect(beneficiary,
                            plan.claimId(), deterministicId(
                                    "money delivery", plan.claimId()));
            switch (result.status()) {
                case SUCCESS, ALREADY_COLLECTED -> {
                }
                case WALLET_FULL, CANCELLED, CONFIG_CHANGED,
                        REENTRANT_REQUEST -> delivery.pending = true;
                case NOT_FOUND, REQUEST_CONFLICT, RECOVERY_REQUIRED,
                        ESCROW_UNAVAILABLE -> delivery.recovery = true;
            }
        }

        private void deliverItem(
                PlayerShopClaimPlan plan,
                DeliveryAggregate delivery
        ) {
            ExactItemClaimCollectionResult result = runtime
                    .collectExactItemClaim(plan.beneficiaryId(),
                            plan.claimId(), Instant.now());
            if (result.status()
                    == ExactItemClaimCollectionStatus.RECOVERY_REQUIRED
                    || result.status()
                    == ExactItemClaimCollectionStatus.MANUAL_REVIEW
                    || result.status()
                    == ExactItemClaimCollectionStatus.INVALID_PAYLOAD) {
                LOGGER.error(
                        "Player shop item claim delivery failed for claim {} with status {} and request {}",
                        plan.claimId(), result.status(),
                        result.requestId().orElse(null));
            }
            switch (result.status()) {
                case DELIVERED, REPLAYED, NOT_PENDING -> {
                }
                case PARTIALLY_DELIVERED, FULL_INVENTORY,
                        OFFLINE_PENDING -> delivery.pending = true;
                case INVALID_PAYLOAD, RECOVERY_REQUIRED,
                        MANUAL_REVIEW -> delivery.recovery = true;
            }
        }

        private LedgerTransaction moneyLedger(
                PlayerShopEscrowIntent intent,
                PlayerShopMoneyTransfer transfer
        ) {
            LedgerAccountId source = moneySourceAccount(transfer);
            LedgerAccountId destination = moneyDestinationAccount(transfer);
            return new LedgerTransaction(transfer.transferId(),
                    "player.shop.money." + intent.requestId() + "."
                            + transfer.transferId(),
                    "Player shop escrow money", List.of(
                    new LedgerLeg(source,
                            Math.negateExact(transfer.amountMinorUnits())),
                    new LedgerLeg(destination,
                            transfer.amountMinorUnits())));
        }

        private LedgerAccountId moneySourceAccount(
                PlayerShopMoneyTransfer transfer
        ) {
            return switch (transfer.source().kind()) {
                case ACTOR_WALLET, ACTOR_CASH, OWNER_WALLET ->
                        PlayerPaymentCommit.walletAccount(
                                transfer.source().participantId());
                case ADMIN_MINT -> new LedgerAccountId(
                        LedgerAccountType.ADMIN_SOURCE,
                        "player.shop.admin");
                default -> throw conflict(
                        "Player shop money source is invalid");
            };
        }

        private LedgerAccountId moneyDestinationAccount(
                PlayerShopMoneyTransfer transfer
        ) {
            return switch (transfer.destination().kind()) {
                case MONEY_CLAIM -> new LedgerAccountId(
                        LedgerAccountType.PLAYER_CLAIM,
                        transfer.destination().reference());
                case ADMIN_SINK -> new LedgerAccountId(
                        LedgerAccountType.ADMIN_SINK,
                        "player.shop.admin");
                default -> throw conflict(
                        "Player shop money destination is invalid");
            };
        }

        private static boolean isStorageClaim(
                PlayerShopEscrowIntent intent,
                UUID claimId
        ) {
            return intent.storageMutations().stream().anyMatch(value ->
                    value.direction()
                            == PlayerShopStorageMutationPlan.Direction.INSERT
                            && value.claimId().equals(claimId));
        }
    }

    private static final class Progress {
        private final PlayerShopEscrowIntent intent;
        private final Map<UUID, PlayerShopMoneyMutationReceipt> money =
                new HashMap<>();
        private final Map<UUID, PlayerShopItemMutationReceipt> items =
                new HashMap<>();
        private final Map<UUID, PlayerShopStorageCustodyReceipt> storage =
                new HashMap<>();

        private Progress(PlayerShopEscrowIntent intent) {
            this.intent = intent;
        }

        private static Progress from(
                PlayerShopEscrowIntent intent,
                PlayerShopFundingEvidence existing
        ) {
            Progress progress = new Progress(intent);
            if (existing != null) {
                existing.moneyReceipts().forEach(progress::put);
                existing.itemReceipts().forEach(progress::put);
                existing.storageReceipts().forEach(progress::put);
            }
            return progress;
        }

        private void put(PlayerShopMoneyMutationReceipt receipt) {
            money.put(receipt.transfer().transferId(), receipt);
        }

        private void put(PlayerShopItemMutationReceipt receipt) {
            items.put(receipt.transfer().transferId(), receipt);
        }

        private void put(PlayerShopStorageCustodyReceipt receipt) {
            storage.put(receipt.plan().mutationId(), receipt);
        }

        private Optional<PlayerShopMoneyMutationReceipt> money(UUID id) {
            return Optional.ofNullable(money.get(id));
        }

        private Optional<PlayerShopItemMutationReceipt> item(UUID id) {
            return Optional.ofNullable(items.get(id));
        }

        private Optional<PlayerShopStorageCustodyReceipt> storage(UUID id) {
            return Optional.ofNullable(storage.get(id));
        }

        private PlayerShopFundingEvidence partial(String detail) {
            return new PlayerShopFundingEvidence(intent.requestId(),
                    PlayerShopFundingEvidence.Status.RECOVERY_REQUIRED,
                    orderedMoney(), orderedItems(), orderedStorage(), detail);
        }

        private PlayerShopFundingEvidence complete() {
            return PlayerShopFundingEvidence.complete(intent.requestId(),
                    orderedMoney(), orderedItems(), orderedStorage());
        }

        private List<PlayerShopMoneyMutationReceipt> orderedMoney() {
            return intent.moneyTransfers().stream().map(value ->
                    money.get(value.transferId())).filter(
                    Objects::nonNull).toList();
        }

        private List<PlayerShopItemMutationReceipt> orderedItems() {
            return intent.itemTransfers().stream().map(value ->
                    items.get(value.transferId())).filter(
                    Objects::nonNull).toList();
        }

        private List<PlayerShopStorageCustodyReceipt> orderedStorage() {
            return intent.storageMutations().stream().map(value ->
                    storage.get(value.mutationId())).filter(
                    Objects::nonNull).toList();
        }
    }

    private static final class DeliveryAggregate {
        private boolean pending;
        private boolean recovery;

        private PlayerShopEscrowBackend.DeliveryResult result() {
            if (recovery) {
                return new PlayerShopEscrowBackend.DeliveryResult(
                        PlayerShopEscrowBackend.DeliveryStatus
                                .RECOVERY_REQUIRED,
                        "Player shop claim delivery requires recovery");
            }
            if (pending) {
                return new PlayerShopEscrowBackend.DeliveryResult(
                        PlayerShopEscrowBackend.DeliveryStatus.CLAIMS_PENDING,
                        "Player shop claims remain safely pending");
            }
            return new PlayerShopEscrowBackend.DeliveryResult(
                    PlayerShopEscrowBackend.DeliveryStatus.DELIVERED, "");
        }
    }

    private record PhysicalFunding(
            UUID transactionId,
            UUID transferId,
            List<EscrowClaim> claims,
            long amountMinorUnits
    ) {
        private PhysicalFunding {
            claims = List.copyOf(Objects.requireNonNull(claims, "claims"));
            if (amountMinorUnits < 0L
                    || amountMinorUnits == 0L != claims.isEmpty()
                    || !claims.isEmpty() && (transactionId == null
                    || transferId == null)
                    || claims.stream().anyMatch(claim ->
                    claim.kind() != ClaimKind.INTERNAL_ESCROW_MONEY
                            || transactionId != null
                            && !transactionId.equals(
                            claim.transactionId()))) {
                throw new IllegalArgumentException(
                        "Player shop physical funding is invalid");
            }
        }

        private static PhysicalFunding none() {
            return new PhysicalFunding(null, null, List.of(), 0L);
        }

        private boolean empty() {
            return claims.isEmpty();
        }
    }

    private static String requireText(String value, String label) {
        String text = Objects.requireNonNull(value, label);
        if (text.isBlank() || text.length() > 128) {
            throw new IllegalArgumentException(
                    "Player shop storage fingerprint is invalid");
        }
        return text;
    }

    static boolean internalPhysicalFundingClaim(
            EscrowClaim claim,
            UUID ownerId,
            UUID transactionId
    ) {
        return claim != null
                && claim.kind() == ClaimKind.INTERNAL_ESCROW_MONEY
                && claim.ownerId().equals(
                Objects.requireNonNull(ownerId, "ownerId"))
                && claim.transactionId().equals(
                Objects.requireNonNull(transactionId, "transactionId"));
    }

    private static UUID deterministicId(String purpose, UUID identity) {
        return UUID.nameUUIDFromBytes(("futureshops.player.shop."
                + purpose + "." + identity).getBytes(
                StandardCharsets.UTF_8));
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static PlayerShopBackendException rejected(String detail) {
        return new PlayerShopBackendException(
                PlayerShopBackendException.Kind.REJECTED, detail);
    }

    private static PlayerShopBackendException conflict(String detail) {
        return new PlayerShopBackendException(
                PlayerShopBackendException.Kind.CONFLICT, detail);
    }

    private static PlayerShopBackendException recovery(String detail) {
        return new PlayerShopBackendException(
                PlayerShopBackendException.Kind.RECOVERY_REQUIRED, detail);
    }
}

package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.checkpoint.ActiveEscrowJournal;
import com.enviouse.futureshops.server.escrow.admin.EscrowAdministrativeAuditSavedData;
import com.enviouse.futureshops.server.escrow.admin.EscrowAdministrativeRecord;
import com.enviouse.futureshops.server.escrow.admin.MaintenanceExpectedState;
import com.enviouse.futureshops.server.escrow.admin.MaintenanceRepairCommand;
import com.enviouse.futureshops.server.escrow.admin.MaintenanceRepairPayload;
import com.enviouse.futureshops.server.escrow.admin.MaintenanceRepairTarget;
import com.enviouse.futureshops.server.escrow.admin.MaintenanceRepairTargetType;
import com.enviouse.futureshops.server.escrow.admin.MaintenanceStateFingerprint;
import com.enviouse.futureshops.server.escrow.audit.EscrowConservationReport;
import com.enviouse.futureshops.server.escrow.audit.EscrowCrossDomainConservationAudit;
import com.enviouse.futureshops.server.escrow.checkpoint.EscrowSavedDataCheckpointBundle;
import com.enviouse.futureshops.server.escrow.claim.ClaimSavedData;
import com.enviouse.futureshops.server.escrow.claim.ClaimAttemptResult;
import com.enviouse.futureshops.server.escrow.claim.ClaimKind;
import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.custody.CustodyAdapter;
import com.enviouse.futureshops.server.escrow.custody.CustodyBatchCommit;
import com.enviouse.futureshops.server.escrow.custody.CustodyBatchCommitCodec;
import com.enviouse.futureshops.server.escrow.custody.CustodyBatchExecutionResult;
import com.enviouse.futureshops.server.escrow.custody.CustodyBatchExecutor;
import com.enviouse.futureshops.server.escrow.custody.CustodyBatchPlan;
import com.enviouse.futureshops.server.escrow.custody.CustodyBatchRecovery;
import com.enviouse.futureshops.server.escrow.custody.CustodyPreparedBatch;
import com.enviouse.futureshops.server.escrow.custody.CustodyPreparedOperation;
import com.enviouse.futureshops.server.escrow.custody.CustodyBatchStatus;
import com.enviouse.futureshops.server.escrow.custody.CustodyAdapterInspection;
import com.enviouse.futureshops.server.escrow.custody.CustodyAdapterInspectionStatus;
import com.enviouse.futureshops.server.escrow.custody.CustodyMutation;
import com.enviouse.futureshops.server.escrow.custody.CustodyOperation;
import com.enviouse.futureshops.server.escrow.custody.CustodySavedData;
import com.enviouse.futureshops.server.escrow.custody.CustodyTransferEvidence;
import com.enviouse.futureshops.server.escrow.custody.CashClaimCustodySupport;
import com.enviouse.futureshops.server.escrow.inventory.PlayerInventoryCustodyAdapter;
import com.enviouse.futureshops.server.escrow.inventory.PlayerInventoryDeliveryToken;
import com.enviouse.futureshops.server.escrow.item.runtime.DurableItemInventoryMutationGateway;
import com.enviouse.futureshops.server.escrow.item.runtime.ExactItemInventoryRuntime;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryExecutionResult;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryExecutionStatus;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryJournalEntry;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryJournalStatus;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryJournalSavedData;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryOnlineRecoveryBatch;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryQuarantineAdministration;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryQuarantineAdministrationCodec;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryQuarantineAdministrativeAction;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryQuarantineInspection;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryJournalCompaction;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryJournalCompactionCodec;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryJournalCompactionResult;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryTerminalTombstone;
import com.enviouse.futureshops.server.escrow.item.runtime.ServerPlayerItemInventoryAccess;
import com.enviouse.futureshops.server.escrow.item.ExactItemClaimPayload;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryBatchEntry;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryBatchPlanner;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationPlan;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryState;
import com.enviouse.futureshops.money.InternalBillInventoryPlanner;
import com.enviouse.futureshops.server.escrow.ledger.LedgerSavedData;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;
import com.enviouse.futureshops.server.escrow.model.EscrowOperation;
import com.enviouse.futureshops.server.escrow.model.EscrowAssetLotType;
import com.enviouse.futureshops.server.escrow.model.EscrowPartyType;
import com.enviouse.futureshops.server.escrow.model.EscrowState;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;
import com.enviouse.futureshops.server.escrow.model.EscrowTransactionId;
import com.enviouse.futureshops.server.escrow.model.CashDepositMode;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintEventCodec;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintJournalEvent;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintSavedData;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopEscrowLifecycleEvent;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopEscrowLifecycleEventCodec;
import com.enviouse.futureshops.server.escrow.redemption.ProtectedCashRedemptionCancellation;
import com.enviouse.futureshops.server.escrow.redemption.ProtectedCashRedemptionEvidence;
import com.enviouse.futureshops.server.escrow.redemption.ProtectedCashRedemptionIntentStore;
import com.enviouse.futureshops.server.escrow.redemption.ProtectedCashRedemptionReservation;
import com.enviouse.futureshops.server.escrow.redemption.ProtectedCashRedemptionSettlement;
import com.enviouse.futureshops.server.escrow.store.EscrowTransactionByteCodec;
import com.enviouse.futureshops.server.escrow.store.EscrowTransactionSavedData;
import com.enviouse.futureshops.server.escrow.stock.CatalogStockState;
import com.enviouse.futureshops.server.escrow.stock.StockCommandResult;
import com.enviouse.futureshops.server.escrow.stock.StockConservationReport;
import com.enviouse.futureshops.server.escrow.stock.StockKey;
import com.enviouse.futureshops.server.escrow.stock.StockMutationCommand;
import com.enviouse.futureshops.server.escrow.stock.StockMutationCommandCodec;
import com.enviouse.futureshops.server.escrow.stock.StockMutationReceipt;
import com.enviouse.futureshops.server.escrow.stock.StockReservation;
import com.enviouse.futureshops.server.escrow.stock.StockSavedData;
import com.enviouse.futureshops.server.escrow.stock.StockStoreSnapshot;
import com.enviouse.futureshops.server.market.auction.AuctionHouseMutation;
import com.enviouse.futureshops.server.market.auction.AuctionHouseMutationCodec;
import com.enviouse.futureshops.server.market.auction.AuctionHouseSavedData;
import com.enviouse.futureshops.server.market.auction.AuctionHouseSnapshot;
import com.enviouse.futureshops.server.market.auction.AuctionListing;
import com.enviouse.futureshops.server.market.auction.AuctionRequestReceipt;
import com.enviouse.futureshops.server.market.auction.escrow.AuctionCreateEscrowIntent;
import com.enviouse.futureshops.server.market.auction.escrow.AuctionEscrowCommit;
import com.enviouse.futureshops.server.market.auction.escrow.AuctionEscrowLifecycleEvent;
import com.enviouse.futureshops.server.market.auction.escrow.AuctionEscrowLifecycleEventCodec;
import com.enviouse.futureshops.server.market.auction.escrow.AuctionEscrowLifecycleRepository;
import com.enviouse.futureshops.server.market.bazaar.BazaarFill;
import com.enviouse.futureshops.server.market.bazaar.BazaarMutation;
import com.enviouse.futureshops.server.market.bazaar.BazaarMutationCodec;
import com.enviouse.futureshops.server.market.bazaar.BazaarOrder;
import com.enviouse.futureshops.server.market.bazaar.BazaarOrderBookSnapshot;
import com.enviouse.futureshops.server.market.bazaar.BazaarProduct;
import com.enviouse.futureshops.server.market.bazaar.BazaarRequestReceipt;
import com.enviouse.futureshops.server.market.bazaar.BazaarSavedData;
import com.enviouse.futureshops.server.market.bazaar.escrow.BazaarCreateEscrowIntent;
import com.enviouse.futureshops.server.market.bazaar.escrow.BazaarEscrowLifecycleEvent;
import com.enviouse.futureshops.server.market.bazaar.escrow.BazaarEscrowLifecycleEventCodec;
import com.enviouse.futureshops.server.market.bazaar.escrow.BazaarEscrowLifecycleRepository;
import com.enviouse.futureshops.server.market.control.MarketControlApplyResult;
import com.enviouse.futureshops.server.market.control.MarketControlAuditProjection;
import com.enviouse.futureshops.server.market.control.MarketControlCommitResult;
import com.enviouse.futureshops.server.market.control.MarketControlModule;
import com.enviouse.futureshops.server.market.control.MarketControlMutation;
import com.enviouse.futureshops.server.market.control.MarketControlMutationCodec;
import com.enviouse.futureshops.server.market.control.MarketControlRequestReceipt;
import com.enviouse.futureshops.server.market.control.MarketControlSavedData;
import com.enviouse.futureshops.server.market.control.MarketControlState;
import com.enviouse.futureshops.server.market.control.MarketControlTransitionCommand;
import com.enviouse.futureshops.server.market.control.MarketModuleControl;
import com.enviouse.futureshops.server.market.control.MarketModuleStatus;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public final class EscrowRuntimeService implements AutoCloseable {
    private final MinecraftServer ownerServer;
    private final EscrowRuntimeCoordinator coordinator;
    private final EscrowSavedDataMutationApplier applier;
    private final EscrowTransactionSavedData transactions;
    private final LedgerSavedData ledger;
    private final ClaimSavedData claims;
    private final EscrowAdministrativeAuditSavedData administrativeAudit;
    private final CustodySavedData custody;
    private final ProtectedMintSavedData protectedMints;
    private final StockSavedData stock;
    private final ItemInventoryJournalSavedData itemInventoryJournal;
    private final AuctionHouseSavedData auctionHouse;
    private final BazaarSavedData bazaar;
    private final ServerShopIntentSavedData serverShopIntents;
    private final PlayerShopEscrowSavedData playerShopEscrow;
    private final MarketControlSavedData marketControl;
    private final EscrowItemInventoryMutationGateway itemInventoryGateway;
    private final ExactItemInventoryRuntime exactItemInventoryRuntime;
    private final ExactItemInventoryRuntime exactItemClaimInventoryRuntime;
    private final PlayerInventoryCustodyAdapter playerInventoryAdapter;
    private final ProtectedCashRedemptionIntentStore protectedCashIntentStore;
    private final ProtectedCashRedemptionWorkflow protectedCashWorkflow;
    private final ForeignCashDepositIntentStore foreignCashIntentStore;
    private final ForeignCashDepositWorkflow foreignCashWorkflow;
    private final EscrowRecoveryScheduler recoveryScheduler;
    private final EscrowSavedDataCheckpointBundle checkpointBundle;
    private final PlayerPaymentHistoryProjector paymentHistoryProjector;
    private final EscrowRuntimeMaintenanceController maintenanceController;
    private final EscrowCheckpointSchedule checkpointSchedule =
            new EscrowCheckpointSchedule();
    private volatile EscrowRuntimeState unavailableState;
    private final Throwable startupFailure;
    private boolean domainRecoveryInitialized;
    private final ThreadLocal<Integer> recoveryDepth = ThreadLocal.withInitial(() -> 0);
    private final Map<String, CustodyAdapter> custodyRecoveryAdapters =
            new HashMap<>();
    private CustodyExecutionScope custodyExecutionScope;
    private Throwable custodyRecoveryFailure;
    private EscrowConservationReport conservationReport;
    private StockConservationReport stockConservationReport;
    private Throwable conservationFailure;
    private boolean conservationAuditComplete;
    private final ArrayDeque<ProtectedCashRedemptionIntentStore.Inspection>
            protectedCashDiscoveryWork = new ArrayDeque<>();
    private boolean protectedCashDiscoveryComplete;
    private Throwable protectedCashDiscoveryFailure;
    private int protectedCashDiscoveryFailureCount;
    private final Map<UUID, ProtectedCashCleanupWork>
            protectedCashCleanupWork = new LinkedHashMap<>();
    private Throwable protectedCashCleanupFailure;
    private final ArrayDeque<ForeignCashDepositIntentStore.Inspection>
            foreignCashDiscoveryWork = new ArrayDeque<>();
    private boolean foreignCashDiscoveryComplete;
    private Throwable foreignCashDiscoveryFailure;
    private int foreignCashDiscoveryFailureCount;
    private final Map<UUID, ForeignCashCleanupWork>
            foreignCashCleanupWork = new LinkedHashMap<>();
    private Throwable foreignCashCleanupFailure;
    private Throwable itemInventoryRecoveryFailure;
    private final ArrayDeque<UUID> serverShopSellRecoveryWork =
            new ArrayDeque<>();
    private final ArrayDeque<UUID> serverShopBarterRecoveryWork =
            new ArrayDeque<>();
    private final ArrayDeque<ServerShopFundingRecoveryWork>
            serverShopFundingRecoveryWork = new ArrayDeque<>();
    private Throwable serverShopFundingRecoveryFailure;
    private boolean serverShopRecoveryEnumerated;

    private EscrowRuntimeService(MinecraftServer ownerServer,
                                 EscrowRuntimeCoordinator coordinator,
                                 EscrowSavedDataMutationApplier applier,
                                 EscrowTransactionSavedData transactions,
                                 LedgerSavedData ledger,
                                 ClaimSavedData claims,
                                 EscrowAdministrativeAuditSavedData administrativeAudit,
                                 CustodySavedData custody,
                                 ProtectedMintSavedData protectedMints,
                                 StockSavedData stock,
                                 ItemInventoryJournalSavedData itemInventoryJournal,
                                 AuctionHouseSavedData auctionHouse,
                                 BazaarSavedData bazaar,
                                 ServerShopIntentSavedData serverShopIntents,
                                 PlayerShopEscrowSavedData playerShopEscrow,
                                 MarketControlSavedData marketControl,
                                 EscrowRecoveryScheduler recoveryScheduler,
                                 EscrowSavedDataCheckpointBundle checkpointBundle,
                                 EscrowRuntimeMaintenanceController maintenanceController,
                                 PlayerInventoryCustodyAdapter playerInventoryAdapter,
                                 EscrowRuntimeState unavailableState, Throwable startupFailure) {
        this.ownerServer = Objects.requireNonNull(ownerServer, "ownerServer");
        this.coordinator = coordinator;
        this.applier = applier;
        this.transactions = transactions;
        this.ledger = ledger;
        this.claims = claims;
        this.administrativeAudit = administrativeAudit;
        this.custody = custody;
        this.protectedMints = protectedMints;
        this.stock = stock;
        this.itemInventoryJournal = itemInventoryJournal;
        this.auctionHouse = auctionHouse;
        this.bazaar = bazaar;
        this.serverShopIntents = serverShopIntents;
        this.playerShopEscrow = playerShopEscrow;
        this.marketControl = marketControl;
        this.itemInventoryGateway = coordinator == null
                || itemInventoryJournal == null ? null
                : new EscrowItemInventoryMutationGateway(coordinator,
                itemInventoryJournal, claims, ownerServer::isSameThread);
        this.exactItemInventoryRuntime = itemInventoryGateway == null
                ? null : new ExactItemInventoryRuntime(
                itemInventoryGateway);
        this.exactItemClaimInventoryRuntime = itemInventoryGateway == null
                ? null : new ExactItemInventoryRuntime(
                itemInventoryGateway, (stack, direction) -> true,
                java.time.Clock.systemUTC());
        this.playerInventoryAdapter = playerInventoryAdapter;
        this.recoveryScheduler = recoveryScheduler;
        this.checkpointBundle = checkpointBundle;
        this.paymentHistoryProjector = coordinator == null ? null
                : new PlayerPaymentHistoryProjector(
                ownerServer, transactions, ledger, claims);
        this.maintenanceController = maintenanceController;
        this.unavailableState = unavailableState;
        this.startupFailure = startupFailure;
        if (playerInventoryAdapter != null) {
            custodyRecoveryAdapters.put(
                    playerInventoryAdapter.adapterId(),
                    playerInventoryAdapter);
        }
        this.protectedCashIntentStore = coordinator == null ? null
                : new ProtectedCashRedemptionIntentStore();
        this.protectedCashWorkflow = coordinator == null ? null
                : new ProtectedCashRedemptionWorkflow(ownerServer, this,
                protectedCashIntentStore);
        this.foreignCashIntentStore = coordinator == null ? null
                : new ForeignCashDepositIntentStore();
        this.foreignCashWorkflow = coordinator == null ? null
                : new ForeignCashDepositWorkflow(ownerServer, this,
                foreignCashIntentStore);
    }

    static EscrowRuntimeService open(MinecraftServer server) {
        return open(server, EscrowRuntimeCoordinator.DEFAULT_RECOVERY_BATCH_SIZE);
    }

    static EscrowRuntimeService open(MinecraftServer server, int initialRecoveryBatchSize) {
        Objects.requireNonNull(server, "server");
        if (!server.isSameThread()) {
            throw new EscrowRuntimeException(
                    "Escrow runtime must open on the owning server thread");
        }
        EscrowRuntimeCoordinator openedCoordinator = null;
        try {
            EscrowRuntimeSavedData cursor = EscrowRuntimeSavedData.get(server);
            EscrowMutationPermit mutationPermit = cursor.acquireManagedMutationPermit();
            LedgerSavedData ledger = LedgerSavedData.get(server);
            ClaimSavedData claims = ClaimSavedData.get(server);
            EscrowTransactionSavedData transactions = EscrowTransactionSavedData.get(server);
            EscrowAdministrativeAuditSavedData administrativeAudit =
                    EscrowAdministrativeAuditSavedData.get(server);
            CustodySavedData custody = CustodySavedData.get(server);
            ProtectedMintSavedData protectedMints = ProtectedMintSavedData.get(server);
            StockSavedData stock = StockSavedData.get(server);
            ItemInventoryJournalSavedData itemInventoryJournal =
                    ItemInventoryJournalSavedData.get(server);
            AuctionHouseSavedData auctionHouse =
                    AuctionHouseSavedData.get(server);
            BazaarSavedData bazaar = BazaarSavedData.get(server);
            ServerShopIntentSavedData serverShopIntents =
                    ServerShopIntentSavedData.get(server);
            PlayerShopEscrowSavedData playerShopEscrow =
                    PlayerShopEscrowSavedData.get(server);
            MarketControlSavedData marketControl =
                    MarketControlSavedData.get(server);
            transactions.bindManagedMutationPermit(mutationPermit);
            ledger.bindManagedMutationPermit(mutationPermit);
            claims.bindManagedMutationPermit(mutationPermit);
            administrativeAudit.bindManagedMutationPermit(mutationPermit);
            custody.bindManagedMutationPermit(mutationPermit);
            protectedMints.bindManagedMutationPermit(mutationPermit);
            stock.bindManagedMutationPermit(mutationPermit);
            itemInventoryJournal.bindManagedMutationPermit(mutationPermit);
            auctionHouse.bindManagedMutationPermit(mutationPermit);
            bazaar.bindManagedMutationPermit(mutationPermit);
            serverShopIntents.bindManagedMutationPermit(mutationPermit);
            playerShopEscrow.bindManagedMutationPermit(mutationPermit);
            marketControl.bindManagedMutationPermit(mutationPermit);
            EscrowRuntimeMaintenanceController maintenanceController =
                    new EscrowRuntimeMaintenanceController(cursor, mutationPermit);
            EscrowSavedDataMutationApplier applier = new EscrowSavedDataMutationApplier(
                    transactions, ledger, claims, administrativeAudit, custody, protectedMints,
                    stock, itemInventoryJournal, auctionHouse,
                    bazaar, serverShopIntents, playerShopEscrow,
                    marketControl,
                    maintenanceController,
                    AtmWithdrawalApplyFaultInjector.NONE, mutationPermit);
            EscrowRecoveryScheduler recoveryScheduler = new EscrowRecoveryScheduler(transactions);
            EscrowSavedDataCheckpointBundle checkpointBundle =
                    new EscrowSavedDataCheckpointBundle(
                            transactions, ledger, claims, administrativeAudit, custody,
                            protectedMints, stock, itemInventoryJournal,
                            auctionHouse, bazaar,
                            serverShopIntents, playerShopEscrow,
                            marketControl,
                            cursor, server::isSameThread,
                            mutationPermit);
            openedCoordinator = new EscrowRuntimeCoordinator(
                    journalPath(server), cursor, applier,
                    () -> transactions.hasMaterializedState()
                            || ledger.hasMaterializedState()
                            || claims.hasMaterializedState()
                            || administrativeAudit.hasMaterializedState()
                            || custody.hasMaterializedState()
                            || protectedMints.hasMaterializedState()
                            || stock.hasMaterializedState()
                            || itemInventoryJournal.hasMaterializedState()
                            || auctionHouse.hasMaterializedState()
                            || bazaar.hasMaterializedState()
                            || serverShopIntents.hasMaterializedState()
                            || playerShopEscrow.hasMaterializedState()
                            || marketControl.hasMaterializedState(),
                    checkpointBundle, mutationPermit);
            openedCoordinator.start(initialRecoveryBatchSize);
            EscrowRuntimeService service = new EscrowRuntimeService(
                    server, openedCoordinator, applier, transactions, ledger, claims,
                    administrativeAudit, custody,
                    protectedMints, stock, itemInventoryJournal,
                    auctionHouse, bazaar, serverShopIntents,
                    playerShopEscrow, marketControl,
                    recoveryScheduler, checkpointBundle,
                    maintenanceController,
                    new PlayerInventoryCustodyAdapter(server, claims),
                    null, null);
            maintenanceController.attach(service.maintenanceLiveGuard());
            if (openedCoordinator.isReady()) {
                service.initializeDomainRecovery();
                recoveryScheduler.enumerateBatch(1);
            }
            return service;
        } catch (RuntimeException exception) {
            if (openedCoordinator != null) {
                try {
                    openedCoordinator.close();
                } catch (RuntimeException closeFailure) {
                    exception.addSuppressed(closeFailure);
                }
            }
            return new EscrowRuntimeService(
                    server, null, null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null, null,
                    null,
                    null,
                    EscrowRuntimeState.MAINTENANCE,
                    exception);
        }
    }

    public static Path journalPath(MinecraftServer server) {
        return Objects.requireNonNull(server, "server")
                .getWorldPath(LevelResource.ROOT)
                .resolve("futureshops")
                .resolve("escrow")
                .resolve("journal.wal");
    }

    public synchronized EscrowRuntimeState state() {
        assertServerThread();
        if (coordinator == null) {
            return unavailableState;
        }
        EscrowRuntimeState journalState;
        synchronized (coordinator) {
            journalState = coordinator.state();
        }
        if (journalState == EscrowRuntimeState.READY) {
            if (maintenanceController != null
                    && maintenanceController.maintenanceRequested()) {
                return EscrowRuntimeState.MAINTENANCE;
            }
            if (protectedCashDiscoveryFailure != null
                    || protectedCashCleanupFailure != null
                    || foreignCashDiscoveryFailure != null
                    || foreignCashCleanupFailure != null
                    || itemInventoryRecoveryFailure != null
                    || serverShopFundingRecoveryFailure != null) {
                return EscrowRuntimeState.MAINTENANCE;
            }
            boolean schedulerRecovering;
            boolean schedulerBlocked;
            synchronized (recoveryScheduler) {
                schedulerRecovering = !recoveryScheduler.enumerationComplete()
                        || recoveryScheduler.hasRunnableWork();
                schedulerBlocked = recoveryScheduler.hasBlockingWork()
                        || recoveryScheduler.hasManualReviewWork();
            }
            if (!domainRecoveryInitialized || !protectedCashDiscoveryComplete
                    || !foreignCashDiscoveryComplete
                    || !paymentHistoryProjector.complete()
                    || schedulerRecovering
                    || hasResolvableCustodyRecovery()
                    || !protectedCashCleanupWork.isEmpty()
                    || !foreignCashCleanupWork.isEmpty()
                    || !serverShopFundingRecoveryWork.isEmpty()
                    || hasOnlinePreparedItemMutations()) {
                return EscrowRuntimeState.RECOVERING;
            }
            if (schedulerBlocked
                    || hasBlockedCustodyRecovery()
                    || protectedCashDiscoveryFailure != null) {
                return EscrowRuntimeState.MAINTENANCE;
            }
            if (!startupConservationVerified()) {
                return EscrowRuntimeState.MAINTENANCE;
            }
        }
        return journalState;
    }

    public synchronized boolean isReady() {
        return state() == EscrowRuntimeState.READY;
    }

    public synchronized Optional<Throwable> failure() {
        assertServerThread();
        if (coordinator == null) {
            return Optional.ofNullable(startupFailure);
        }
        Optional<Throwable> journalFailure = coordinator.failure();
        if (journalFailure.isPresent()) {
            return journalFailure;
        }
        if (protectedCashDiscoveryFailure != null) {
            return Optional.of(protectedCashDiscoveryFailure);
        }
        if (protectedCashCleanupFailure != null) {
            return Optional.of(protectedCashCleanupFailure);
        }
        if (foreignCashDiscoveryFailure != null) {
            return Optional.of(foreignCashDiscoveryFailure);
        }
        if (foreignCashCleanupFailure != null) {
            return Optional.of(foreignCashCleanupFailure);
        }
        if (custodyRecoveryFailure != null) {
            return Optional.of(custodyRecoveryFailure);
        }
        if (itemInventoryRecoveryFailure != null) {
            return Optional.of(itemInventoryRecoveryFailure);
        }
        if (serverShopFundingRecoveryFailure != null) {
            return Optional.of(serverShopFundingRecoveryFailure);
        }
        return Optional.ofNullable(conservationFailure);
    }

    public synchronized int recoverBatch(int maximumRecords) {
        assertServerThread();
        if (maximumRecords <= 0
                || maximumRecords > EscrowRuntimeCoordinator.MAX_RECOVERY_BATCH_SIZE) {
            throw new IllegalArgumentException("Invalid escrow recovery work limit");
        }
        EscrowRuntimeCoordinator available = requireCoordinator();
        int remaining = maximumRecords;
        int worked = 0;
        synchronized (available) {
            if (available.state() == EscrowRuntimeState.RECOVERING) {
                int journalWork = available.recoverBatch(remaining);
                worked += journalWork;
                remaining -= journalWork;
            }
            if (available.state() != EscrowRuntimeState.READY) {
                return worked;
            }
        }
        initializeDomainRecovery();
        if (remaining > 0 && !protectedCashDiscoveryComplete) {
            int discovered = withRecoveryLane(
                    this::recoverOneProtectedCashDiscovery);
            worked += discovered;
            remaining -= discovered;
        }
        if (remaining > 0 && !foreignCashDiscoveryComplete) {
            int discovered = withRecoveryLane(
                    this::recoverOneForeignCashDiscovery);
            worked += discovered;
            remaining -= discovered;
        }
        if (remaining > 0
                && !serverShopFundingRecoveryWork.isEmpty()) {
            int released = withRecoveryLane(
                    this::recoverOneServerShopFunding);
            worked += released;
            remaining -= released;
        }
        synchronized (recoveryScheduler) {
            if (remaining > 0 && !recoveryScheduler.enumerationComplete()) {
                int enumerationBudget = recoveryScheduler.hasRunnableWork()
                        ? Math.max(1, remaining / 2) : remaining;
                int enumerated = recoveryScheduler.enumerateBatch(enumerationBudget);
                worked += enumerated;
                remaining -= enumerated;
            }
        }
        if (maintenanceController != null
                && maintenanceController.maintenanceRequested()) {
            if (remaining > 0 && !protectedCashCleanupWork.isEmpty()) {
                worked += recoverOneProtectedCashCleanup();
            } else if (remaining > 0
                    && !foreignCashCleanupWork.isEmpty()) {
                worked += recoverOneForeignCashCleanup();
            }
            return worked;
        }
        synchronized (recoveryScheduler) {
            if (remaining > 0 && recoveryScheduler.hasRunnableWork()) {
                int processingBudget = remaining;
                EscrowRecoveryBatchResult result = withRecoveryLane(
                        () -> recoveryScheduler.processBatch(processingBudget));
                worked += result.examined();
                remaining -= result.examined();
            }
        }
        if (remaining > 0 && hasResolvableCustodyRecovery()) {
            int custodyWork = recoverOneCustodyOperation();
            worked += custodyWork;
            remaining -= custodyWork;
        }
        boolean schedulerComplete;
        synchronized (recoveryScheduler) {
            schedulerComplete = recoveryScheduler.enumerationComplete()
                    && !recoveryScheduler.hasRunnableWork();
        }
        if (remaining > 0
                && schedulerComplete
                && !paymentHistoryProjector.complete()) {
            int projected = paymentHistoryProjector.reconcileBatch(remaining);
            worked += projected;
            remaining -= projected;
        }
        if (remaining > 0 && !protectedCashCleanupWork.isEmpty()) {
            worked += recoverOneProtectedCashCleanup();
            remaining--;
        }
        if (remaining > 0 && !foreignCashCleanupWork.isEmpty()) {
            worked += recoverOneForeignCashCleanup();
            remaining--;
        }
        if (remaining > 0 && itemInventoryRecoveryFailure == null
                && hasOnlinePreparedItemMutations()) {
            int itemWork = recoverOnlineItemInventoryMutations(remaining);
            worked += itemWork;
            remaining -= itemWork;
        }
        if (worked > 0) {
            invalidateConservationAudit();
        }
        return worked;
    }

    public synchronized boolean shouldRunRecovery() {
        assertServerThread();
        if (coordinator == null) {
            return false;
        }
        synchronized (coordinator) {
            if (coordinator.state() == EscrowRuntimeState.RECOVERING) {
                return true;
            }
            if (coordinator.state() != EscrowRuntimeState.READY) {
                return false;
            }
        }
        synchronized (recoveryScheduler) {
            if (maintenanceController != null
                    && maintenanceController.maintenanceRequested()) {
                return !domainRecoveryInitialized
                        || !protectedCashDiscoveryComplete
                        || !foreignCashDiscoveryComplete
                        || !paymentHistoryProjector.complete()
                        || !recoveryScheduler.enumerationComplete()
                        || !protectedCashCleanupWork.isEmpty()
                        || !foreignCashCleanupWork.isEmpty()
                        || !serverShopFundingRecoveryWork.isEmpty()
                        || itemInventoryRecoveryFailure == null
                        && hasOnlinePreparedItemMutations();
            }
            return !domainRecoveryInitialized
                    || !protectedCashDiscoveryComplete
                    || !foreignCashDiscoveryComplete
                    || !paymentHistoryProjector.complete()
                    || !recoveryScheduler.enumerationComplete()
                    || recoveryScheduler.hasRunnableWork()
                    || hasResolvableCustodyRecovery()
                    || !protectedCashCleanupWork.isEmpty()
                    || !foreignCashCleanupWork.isEmpty()
                    || !serverShopFundingRecoveryWork.isEmpty()
                    || itemInventoryRecoveryFailure == null
                    && hasOnlinePreparedItemMutations();
        }
    }

    private int recoverOnlineItemInventoryMutations(int maximumWork) {
        if (maximumWork <= 0
                || maximumWork
                > EscrowRuntimeCoordinator.MAX_RECOVERY_BATCH_SIZE) {
            throw new IllegalArgumentException(
                    "Invalid item inventory recovery work limit");
        }
        if (exactItemInventoryRuntime == null
                || itemInventoryJournal == null) {
            return 0;
        }
        List<ServerPlayerItemInventoryAccess> players =
                ownerServer.getPlayerList().getPlayers().stream()
                .map(ServerPlayerItemInventoryAccess::new)
                .toList();
        try {
            return ItemInventoryOnlineRecoveryBatch.recover(
                    exactItemInventoryRuntime, players, maximumWork);
        } catch (RuntimeException exception) {
            itemInventoryRecoveryFailure = new EscrowRuntimeException(
                    "Online item inventory recovery failed", exception);
            return 0;
        }
    }

    private boolean hasOnlinePreparedItemMutations() {
        if (itemInventoryJournal == null) {
            return false;
        }
        for (ServerPlayer player
                : ownerServer.getPlayerList().getPlayers()) {
            if (!itemInventoryJournal.preparedForPlayer(
                    player.getUUID(), 1).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public synchronized boolean tickCheckpoint(int intervalSeconds) {
        return tickCheckpoint(intervalSeconds, Long.MAX_VALUE, Integer.MAX_VALUE);
    }

    public synchronized boolean tickCheckpoint(int intervalSeconds,
                                                long maximumJournalBytes,
                                                int maximumJournalRecords) {
        assertServerThread();
        if (maximumJournalBytes <= 0L || maximumJournalRecords <= 0) {
            throw new IllegalArgumentException(
                    "Escrow checkpoint journal thresholds must be positive");
        }
        boolean eligible = coordinator != null
                && isReady()
                && coordinator.isReady()
                && !coordinator.isQuiescing()
                && coordinator.supportsCheckpoints();
        try {
            boolean thresholdReached = false;
            if (eligible) {
                EscrowJournalMetrics metrics = coordinator.journalMetrics();
                thresholdReached = metrics.sizeBytes() >= maximumJournalBytes
                        || metrics.recordCount() >= maximumJournalRecords;
            }
            if (!checkpointSchedule.tick(
                    intervalSeconds, eligible, thresholdReached)) {
                return false;
            }
            checkpointNow();
            return true;
        } catch (EscrowRuntimeException exception) {
            return false;
        }
    }

    public synchronized ActiveEscrowJournal checkpointNow() {
        assertServerThread();
        if (!isReady()) {
            throw new EscrowRuntimeException(
                    "Escrow runtime is not ready and is in state " + state(),
                    failure().orElse(null));
        }
        ActiveEscrowJournal active = requireCoordinator().checkpointNow();
        checkpointSchedule.checkpointCompleted();
        return active;
    }

    synchronized EscrowCommitResult commitTransaction(EscrowTransaction transaction) {
        assertServerThread();
        Objects.requireNonNull(transaction, "transaction");
        EscrowJournalEvent event = new EscrowJournalEvent(
                EscrowJournalEventType.TRANSACTION_UPSERT,
                EscrowTransactionByteCodec.encode(transaction));
        EscrowCommitResult result = requireReadyCoordinator().commit(
                transaction.transactionId().value(), event);
        invalidateConservationAudit();
        if (transaction.state() == EscrowState.RECOVERY_REQUIRED
                || transaction.state() == EscrowState.MANUAL_REVIEW) {
            recoveryScheduler.enqueue(transaction);
        }
        return result;
    }

    synchronized EscrowCommitResult commitLedger(LedgerTransaction transaction) {
        assertServerThread();
        Objects.requireNonNull(transaction, "transaction");
        EscrowJournalEvent event = new EscrowJournalEvent(
                EscrowJournalEventType.LEDGER_APPLY, LedgerJournalCodec.encode(transaction));
        EscrowCommitResult result = requireReadyCoordinator().commit(
                transaction.transactionId(), event);
        invalidateConservationAudit();
        return result;
    }

    synchronized EscrowCommitResult commitProtectedCashReservation(
            ProtectedCashRedemptionReservation reservation
    ) {
        assertServerThread();
        Objects.requireNonNull(reservation, "reservation");
        EscrowCommitResult result = requireReadyCoordinator()
                .commitProtectedCashReservation(reservation);
        invalidateConservationAudit();
        return result;
    }

    synchronized EscrowCommitResult commitProtectedCashSettlement(
            ProtectedCashRedemptionSettlement settlement
    ) {
        assertServerThread();
        Objects.requireNonNull(settlement, "settlement");
        EscrowCommitResult result = requireReadyCoordinator()
                .commitProtectedCashSettlement(settlement);
        invalidateConservationAudit();
        return result;
    }

    synchronized EscrowCommitResult commitProtectedCashCancellation(
            ProtectedCashRedemptionCancellation cancellation
    ) {
        assertServerThread();
        Objects.requireNonNull(cancellation, "cancellation");
        EscrowCommitResult result = requireReadyCoordinator()
                .commitProtectedCashCancellation(cancellation);
        invalidateConservationAudit();
        return result;
    }

    synchronized EscrowCommitResult commitForeignCashReservation(
            ForeignCashDepositReservation reservation
    ) {
        assertServerThread();
        Objects.requireNonNull(reservation, "reservation");
        EscrowCommitResult result = requireReadyCoordinator()
                .commitForeignCashReservation(reservation);
        invalidateConservationAudit();
        return result;
    }

    synchronized EscrowCommitResult commitForeignCashSettlement(
            ForeignCashDepositSettlement settlement
    ) {
        assertServerThread();
        Objects.requireNonNull(settlement, "settlement");
        EscrowCommitResult result = requireReadyCoordinator()
                .commitForeignCashSettlement(settlement);
        invalidateConservationAudit();
        return result;
    }

    synchronized EscrowCommitResult commitForeignCashCancellation(
            ForeignCashDepositCancellation cancellation
    ) {
        assertServerThread();
        Objects.requireNonNull(cancellation, "cancellation");
        EscrowCommitResult result = requireReadyCoordinator()
                .commitForeignCashCancellation(cancellation);
        invalidateConservationAudit();
        return result;
    }

    synchronized void enqueueProtectedCashRecovery(UUID transactionId) {
        assertServerThread();
        EscrowTransaction current = transactions.getTransaction(
                new EscrowTransactionId(Objects.requireNonNull(
                        transactionId, "transactionId")));
        if (current == null || current.state().isTerminal()
                || current.operation() != EscrowOperation.CURRENCY_DEPOSIT
                || current.assetLots().stream().noneMatch(lot -> lot.type()
                == EscrowAssetLotType.PROTECTED_PHYSICAL_CURRENCY)) {
            throw new EscrowRuntimeException(
                    "Protected cash recovery transaction is invalid");
        }
        recoveryScheduler.enqueue(current);
    }

    synchronized CashDepositRecoveryEnqueueResult
    enqueueProtectedCashIntentRecovery(
            ProtectedCashRedemptionEvidence evidence
    ) {
        assertServerThread();
        Objects.requireNonNull(evidence, "evidence");
        if (evidence.phase()
                != ProtectedCashRedemptionEvidence.Phase.INTENT) {
            throw new IllegalArgumentException(
                    "Protected cash orphan recovery requires intent evidence");
        }
        ProtectedCashRedemptionIntentStore.Inspection inspection =
                protectedCashIntentStore.inspect(ownerServer,
                        evidence.playerId(), evidence.transactionId());
        if (inspection.status()
                == ProtectedCashRedemptionIntentStore.InspectionStatus.MISSING) {
            return CashDepositRecoveryEnqueueResult.NO_DURABLE_EVIDENCE;
        }
        boolean alreadyQueued = protectedCashDiscoveryWork.stream()
                .anyMatch(queued -> queued.transactionId()
                        .filter(evidence.transactionId()::equals)
                        .isPresent());
        if (!alreadyQueued) {
            protectedCashDiscoveryWork.addLast(inspection);
        }
        protectedCashDiscoveryComplete = false;
        return CashDepositRecoveryEnqueueResult.QUEUED;
    }

    synchronized void scheduleProtectedCashCleanup(
            UUID playerId,
            UUID transactionId
    ) {
        assertServerThread();
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(transactionId, "transactionId");
        EscrowTransaction current = transactions.getTransaction(
                new EscrowTransactionId(transactionId));
        if (current == null || !current.state().isTerminal()
                || current.operation() != EscrowOperation.CURRENCY_DEPOSIT) {
            throw new EscrowRuntimeException(
                    "Protected cash cleanup requires a terminal transaction");
        }
        protectedCashCleanupWork.putIfAbsent(transactionId,
                new ProtectedCashCleanupWork(playerId, transactionId));
    }

    synchronized void enqueueForeignCashRecovery(UUID transactionId) {
        assertServerThread();
        EscrowTransaction current = transactions.getTransaction(
                new EscrowTransactionId(Objects.requireNonNull(
                        transactionId, "transactionId")));
        if (current == null || current.state().isTerminal()
                || current.operation() != EscrowOperation.CURRENCY_DEPOSIT
                || current.assetLots().stream().noneMatch(lot -> lot.type()
                == EscrowAssetLotType.FOREIGN_PHYSICAL_CURRENCY)) {
            throw new EscrowRuntimeException(
                    "Foreign cash recovery transaction is invalid");
        }
        recoveryScheduler.enqueue(current);
    }

    synchronized CashDepositRecoveryEnqueueResult
    enqueueForeignCashIntentRecovery(
            ForeignCashDepositEvidence evidence
    ) {
        assertServerThread();
        Objects.requireNonNull(evidence, "evidence");
        if (evidence.phase() != ForeignCashDepositEvidence.Phase.INTENT) {
            throw new IllegalArgumentException(
                    "Foreign cash orphan recovery requires intent evidence");
        }
        ForeignCashDepositIntentStore.Inspection inspection =
                foreignCashIntentStore.inspect(ownerServer,
                        evidence.playerId(), evidence.transactionId());
        if (inspection.status()
                == ForeignCashDepositIntentStore.InspectionStatus.MISSING) {
            return CashDepositRecoveryEnqueueResult.NO_DURABLE_EVIDENCE;
        }
        boolean alreadyQueued = foreignCashDiscoveryWork.stream()
                .anyMatch(queued -> queued.transactionId()
                        .filter(evidence.transactionId()::equals)
                        .isPresent());
        if (!alreadyQueued) {
            foreignCashDiscoveryWork.addLast(inspection);
        }
        foreignCashDiscoveryComplete = false;
        return CashDepositRecoveryEnqueueResult.QUEUED;
    }

    synchronized void scheduleForeignCashCleanup(
            UUID playerId,
            UUID transactionId
    ) {
        assertServerThread();
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(transactionId, "transactionId");
        EscrowTransaction current = transactions.getTransaction(
                new EscrowTransactionId(transactionId));
        if (current == null || !current.state().isTerminal()
                || current.operation() != EscrowOperation.CURRENCY_DEPOSIT) {
            throw new EscrowRuntimeException(
                    "Foreign cash cleanup requires a terminal transaction");
        }
        foreignCashCleanupWork.putIfAbsent(transactionId,
                new ForeignCashCleanupWork(playerId, transactionId));
    }

    public synchronized ProtectedCashRedemptionResult redeemProtectedCash(
            ServerPlayer player,
            InternalBillInventoryPlanner.ExactPlan plan,
            UUID transactionId,
            String requestKey,
            long configRevision,
            long walletBalanceLimitMinorUnits,
            CashDepositMode depositMode,
            Instant now
    ) {
        assertServerThread();
        if (protectedCashWorkflow == null) {
            throw new EscrowRuntimeException(
                    "Protected cash redemption is unavailable",
                    startupFailure);
        }
        requireReadyCoordinator();
        ProtectedCashRedemptionWorkflow.Outcome outcome =
                protectedCashWorkflow.redeem(player, plan, transactionId,
                requestKey, configRevision, walletBalanceLimitMinorUnits,
                depositMode, now);
        ProtectedCashRedemptionSettlement settlement = outcome.settlement();
        return new ProtectedCashRedemptionResult(transactionId,
                settlement.amountMinorUnits(),
                settlement.walletCreditMinorUnits(),
                settlement.overflowClaimMinorUnits(),
                outcome.cleanupPending());
    }

    synchronized ForeignCashDepositResult redeemForeignCash(
            ServerPlayer player,
            ForeignCashDepositPlan plan,
            UUID requestId,
            UUID transactionId,
            String requestKey,
            long walletBalanceLimitMinorUnits,
            CashDepositMode depositMode,
            Instant now
    ) {
        assertServerThread();
        if (foreignCashWorkflow == null) {
            throw new EscrowRuntimeException(
                    "Foreign cash deposit is unavailable", startupFailure);
        }
        requireReadyCoordinator();
        ForeignCashDepositWorkflow.Outcome outcome =
                foreignCashWorkflow.deposit(player, plan, requestId,
                        transactionId, requestKey,
                        walletBalanceLimitMinorUnits, depositMode, now);
        ForeignCashDepositSettlement settlement = outcome.settlement();
        return new ForeignCashDepositResult(transactionId,
                settlement.amountMinorUnits(),
                settlement.walletCreditMinorUnits(),
                settlement.overflowClaimMinorUnits(),
                outcome.cleanupPending());
    }

    synchronized long ledgerBalance(
            com.enviouse.futureshops.server.escrow.ledger.LedgerAccountId account
    ) {
        assertServerThread();
        return ledger.balance(account);
    }

    synchronized boolean ledgerContainsAccount(
            com.enviouse.futureshops.server.escrow.ledger.LedgerAccountId account
    ) {
        assertServerThread();
        return ledger.containsAccount(account);
    }

    synchronized Map<com.enviouse.futureshops.server.escrow.ledger.LedgerAccountId, Long>
    ledgerSnapshot() {
        assertServerThread();
        return ledger.snapshotBalances();
    }

    synchronized boolean wasLedgerTransactionApplied(UUID transactionId) {
        assertServerThread();
        return ledger.wasApplied(transactionId);
    }

    synchronized Optional<LedgerTransaction> ledgerTransaction(
            UUID transactionId
    ) {
        assertServerThread();
        return ledger.transactionReceipt(transactionId)
                .map(com.enviouse.futureshops.server.escrow.ledger.LedgerTransactionReceipt::transaction);
    }

    public synchronized Optional<RecoveryInspection> inspectRecovery(
            UUID transactionId
    ) {
        assertServerThread();
        Objects.requireNonNull(transactionId, "transactionId");
        EscrowTransaction transaction = transactions.getTransaction(
                new EscrowTransactionId(transactionId));
        if (transaction == null) {
            return Optional.empty();
        }
        List<EscrowClaim> transactionClaims =
                claims.claimsForTransaction(transactionId);
        long amountMinorUnits = transaction.assetLots().stream()
                .flatMap(lot -> lot.money().stream())
                .map(value -> value.minorUnits())
                .reduce(0L, Math::addExact);
        long assetQuantity = transaction.assetLots().stream()
                .mapToLong(lot -> lot.quantity())
                .reduce(0L, Math::addExact);
        long pendingClaimUnits = transactionClaims.stream()
                .filter(claim -> claim.status() == ClaimStatus.PENDING)
                .map(EscrowClaim::remainingUnits)
                .reduce(0L, Math::addExact);
        List<String> participants = transaction.participants().stream()
                .map(participant -> participant.party().type().name()
                        + ":" + participant.party().id()
                        + ":" + participant.roles().stream()
                        .map(Enum::name).sorted()
                        .collect(java.util.stream.Collectors.joining(",")))
                .sorted()
                .toList();
        String provider = cashProvider(transaction);
        String evidence = cashEvidence(transaction, provider);
        String safeAction = transaction.state().isTerminal()
                ? "NO_ACTION"
                : transaction.state() == EscrowState.MANUAL_REVIEW
                ? "ADMIN_REVIEW" : "AUTOMATIC_RECOVERY";
        return Optional.of(new RecoveryInspection(
                transactionId, transaction.requestKey().value(),
                transaction.operation(), transaction.state(),
                transaction.revision(), transaction.configRevision(),
                participants, provider, evidence, amountMinorUnits,
                assetQuantity, transactionClaims.size(),
                pendingClaimUnits,
                transaction.lastError().map(error -> error.code())
                        .orElse("NONE"),
                transaction.lastError().map(error -> error.message())
                        .orElse("NONE"),
                transaction.retryMetadata().attemptCount(),
                transaction.retryMetadata().maxAttempts(),
                transaction.retryMetadata().nextAttemptAt()
                        .map(Instant::toString).orElse("NONE"),
                transaction.retryMetadata().resumeState()
                        .map(Enum::name).orElse("NONE"),
                safeAction));
    }

    synchronized Optional<EscrowTransaction> transaction(UUID transactionId) {
        assertServerThread();
        Objects.requireNonNull(transactionId, "transactionId");
        return Optional.ofNullable(transactions.getTransaction(
                new EscrowTransactionId(transactionId)));
    }

    synchronized List<EscrowClaim> claimsForTransaction(UUID transactionId) {
        assertServerThread();
        return claims.claimsForTransaction(Objects.requireNonNull(
                transactionId, "transactionId"));
    }

    private String cashProvider(EscrowTransaction transaction) {
        if (transaction.assetLots().stream().anyMatch(lot -> lot.type()
                == EscrowAssetLotType.PROTECTED_PHYSICAL_CURRENCY)) {
            return "PROTECTED";
        }
        if (transaction.assetLots().stream().anyMatch(lot -> lot.type()
                == EscrowAssetLotType.FOREIGN_PHYSICAL_CURRENCY)) {
            return "FOREIGN";
        }
        return "NOT_CASH";
    }

    private String cashEvidence(
            EscrowTransaction transaction,
            String provider
    ) {
        Optional<UUID> playerId = transaction.participants().stream()
                .filter(participant -> participant.party().type()
                        == EscrowPartyType.PLAYER)
                .map(participant -> participant.party().id())
                .map(UUID::fromString)
                .findFirst();
        if (playerId.isEmpty() || provider.equals("NOT_CASH")) {
            return "NOT_APPLICABLE";
        }
        UUID transactionId = transaction.transactionId().value();
        try {
            if (provider.equals("PROTECTED")) {
                ProtectedCashRedemptionIntentStore.Inspection inspection =
                        protectedCashIntentStore.inspect(ownerServer,
                                playerId.orElseThrow(), transactionId);
                return inspection.status().name()
                        + inspection.evidence()
                        .map(value -> ":" + value.phase().name())
                        .orElse("");
            }
            ForeignCashDepositIntentStore.Inspection inspection =
                    foreignCashIntentStore.inspect(ownerServer,
                            playerId.orElseThrow(), transactionId);
            return inspection.status().name()
                    + inspection.evidence()
                    .map(value -> ":" + value.phase().name())
                    .orElse("");
        } catch (RuntimeException exception) {
            return "INSPECTION_FAILED:" + exception.getClass().getSimpleName();
        }
    }

    synchronized Optional<ClaimAttemptResult> claimAttempt(
            String requestKey
    ) {
        assertServerThread();
        return claims.attempt(requestKey);
    }

    synchronized Optional<EscrowClaim> claim(UUID claimId) {
        assertServerThread();
        return Optional.ofNullable(claims.getClaim(
                Objects.requireNonNull(claimId, "claimId")));
    }

    synchronized EscrowCommitResult createClaim(EscrowClaim claim) {
        assertServerThread();
        Objects.requireNonNull(claim, "claim");
        EscrowJournalEvent event = new EscrowJournalEvent(
                EscrowJournalEventType.CLAIM_CREATE, ClaimJournalCodec.encodeClaim(claim));
        EscrowCommitResult result = requireReadyCoordinator().commit(
                claim.transactionId(), event);
        invalidateConservationAudit();
        return result;
    }

    synchronized EscrowCommitResult deliverPlayerShopStorageClaim(
            UUID transactionId,
            ClaimDeliveryCommit delivery
    ) {
        assertServerThread();
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(delivery, "delivery");
        EscrowJournalEvent event = new EscrowJournalEvent(
                EscrowJournalEventType.CLAIM_DELIVERY,
                ClaimJournalCodec.encodeDelivery(delivery));
        EscrowCommitResult result = requireReadyCoordinator().commit(
                transactionId, event);
        invalidateConservationAudit();
        return result;
    }

    synchronized EscrowCommitResult settleMoneyClaim(MoneyClaimSettlement settlement) {
        assertServerThread();
        Objects.requireNonNull(settlement, "settlement");
        EscrowJournalEvent event = new EscrowJournalEvent(
                EscrowJournalEventType.MONEY_CLAIM_SETTLEMENT,
                MoneyClaimSettlementCodec.encode(settlement));
        EscrowCommitResult result = requireReadyCoordinator().commit(
                settlement.requestId(), event);
        invalidateConservationAudit();
        return result;
    }

    synchronized EscrowCommitResult quarantineClaim(ClaimQuarantineCommit quarantine) {
        assertServerThread();
        Objects.requireNonNull(quarantine, "quarantine");
        EscrowJournalEvent event = new EscrowJournalEvent(
                EscrowJournalEventType.CLAIM_QUARANTINE,
                ClaimJournalCodec.encodeQuarantine(quarantine));
        EscrowCommitResult result = requireReadyCoordinator().commit(
                quarantine.transactionId(), event);
        invalidateConservationAudit();
        return result;
    }

    synchronized EscrowCommitResult commitAdministrativeAudit(
            EscrowAdministrativeRecord audit) {
        assertServerThread();
        Objects.requireNonNull(audit, "audit");
        EscrowJournalEvent event = new EscrowJournalEvent(
                EscrowJournalEventType.ADMIN_AUDIT,
                AdministrativeAuditJournalCodec.encode(audit));
        EscrowRuntimeCoordinator available = requireCoordinator();
        EscrowCommitResult result;
        synchronized (available) {
            if (!available.isReady()) {
                throw new EscrowRuntimeException(
                        "Administrative audit journal is unavailable in state "
                                + available.state(),
                        available.failure().orElse(null));
            }
            result = available.commit(audit.requestId(), event);
        }
        invalidateConservationAudit();
        return result;
    }

    synchronized Optional<EscrowAdministrativeRecord>
    administrativeAuditRecord(UUID requestId) {
        assertServerThread();
        if (administrativeAudit == null) {
            throw new EscrowRuntimeException(
                    "Administrative audit is unavailable", startupFailure);
        }
        return Optional.ofNullable(administrativeAudit.getRecord(
                Objects.requireNonNull(requestId, "requestId")));
    }

    synchronized EscrowCommitResult commitMaintenanceRepair(
            MaintenanceRepairCommand command
    ) {
        assertServerThread();
        Objects.requireNonNull(command, "command");
        EscrowRuntimeCoordinator available = requireCoordinator();
        EscrowJournalEvent event = applier.planMaintenanceRepair(command);
        MaintenanceRepairCommand plannedCommand = MaintenanceRepairJournalCodec.decode(
                event.body()).command();
        EscrowCommitResult result;
        synchronized (available) {
            if (!available.journalHealthyAndAligned()) {
                throw new EscrowRuntimeException(
                        "Maintenance repair journal is unavailable in state "
                                + available.state(),
                        available.failure().orElse(null));
            }
            result = available.commitMaintenanceRepair(
                    command.commandId(), event);
        }
        invalidateConservationAudit();
        if (plannedCommand.appliesAction()
                && plannedCommand.target().type()
                == MaintenanceRepairTargetType.TRANSACTION) {
            EscrowTransaction current = transactions.getTransaction(
                    new EscrowTransactionId(plannedCommand.target().targetId()));
            if (current != null) {
                recoveryScheduler.enqueue(current);
            }
        }
        return result;
    }

    public synchronized EscrowCommitResult verifyAndResumeMaintenance(
            UUID commandId,
            String actor,
            String reason,
            long expectedRevision,
            EscrowGlobalVerificationSnapshot verification,
            Instant now
    ) {
        Objects.requireNonNull(verification, "verification");
        MaintenanceRepairCommand command = MaintenanceRepairCommand.create(
                commandId, actor, reason, true, now,
                MaintenanceRepairTarget.runtime(),
                MaintenanceExpectedState.revision(expectedRevision),
                new MaintenanceRepairPayload.VerifyAndResume(
                        verification.journalSequence(),
                        verification.fingerprint()),
                true, "Verified escrow maintenance resume");
        return commitMaintenanceRepair(command);
    }

    public synchronized MaintenanceStateFingerprint maintenanceFingerprint(
            MaintenanceRepairTarget target
    ) {
        assertServerThread();
        return requireMaintenanceApplier().maintenanceFingerprint(target);
    }

    public synchronized long maintenanceRevision(MaintenanceRepairTarget target) {
        assertServerThread();
        return requireMaintenanceApplier().maintenanceRevision(target);
    }

    public synchronized EscrowGlobalVerificationSnapshot maintenanceGlobalVerification() {
        assertServerThread();
        EscrowRuntimeCoordinator available = requireCoordinator();
        if (!available.journalHealthyAndAligned()) {
            throw new EscrowRuntimeException(
                    "Escrow journal is not aligned for global verification");
        }
        return maintenanceController.globalVerification();
    }

    public synchronized EscrowConservationReport maintenanceConservationReport() {
        assertServerThread();
        try {
            EscrowConservationReport report = verifyConservation();
            conservationReport = report;
            conservationFailure = report.conserved() ? null
                    : new EscrowRuntimeException(
                    "Escrow cross domain conservation failed");
            conservationAuditComplete = true;
            return report;
        } catch (RuntimeException exception) {
            conservationFailure = exception;
            conservationAuditComplete = true;
            throw exception;
        }
    }

    public synchronized CustodyBatchExecutionResult executeCustodyBatch(
            CustodyAdapter adapter,
            CustodyBatchPlan plan,
            Map<java.util.UUID, CustodyTransferEvidence> plannedEvidence,
            Instant now
    ) {
        assertServerThread();
        Objects.requireNonNull(adapter, "adapter");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(plannedEvidence, "plannedEvidence");
        Objects.requireNonNull(now, "now");
        requireReadyCoordinator();
        if (adapter.adapterId().equals(
                CashClaimCustodySupport.PLAYER_INVENTORY_ADAPTER_ID)) {
            throw new EscrowRuntimeException(
                    "Player inventory cash delivery requires a cash claim");
        }
        if (custodyExecutionScope != null) {
            throw new EscrowRuntimeException("A custody batch is already executing");
        }
        custodyExecutionScope = new CustodyExecutionScope(plan, plannedEvidence);
        try {
            return new CustodyBatchExecutor().execute(
                    adapter, plan, plannedEvidence, now, this::commitScopedCustodyBatch);
        } finally {
            custodyExecutionScope = null;
            invalidateConservationAudit();
        }
    }

    public synchronized CustodyBatchExecutionResult deliverCashClaim(
            ServerPlayer player,
            UUID claimId,
            UUID attemptId,
            Instant now
    ) {
        assertServerThread();
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(claimId, "claimId");
        Objects.requireNonNull(attemptId, "attemptId");
        Objects.requireNonNull(now, "now");
        requireReadyCoordinator();
        if (playerInventoryAdapter == null) {
            throw new EscrowRuntimeException(
                    "Player inventory cash delivery is unavailable");
        }
        if (custodyExecutionScope != null) {
            throw new EscrowRuntimeException(
                    "A custody batch is already executing");
        }
        EscrowClaim claim = claims.getClaim(claimId);
        if (claim == null || !claim.ownerId().equals(player.getUUID())) {
            throw new EscrowRuntimeException(
                    "Cash claim does not belong to the player");
        }
        CashClaimDeliveryPlan delivery = CashClaimDeliveryPlanner.plan(
                claim, protectedMints, attemptId);
        Map<UUID, CustodyTransferEvidence> evidence =
                playerInventoryAdapter.prepare(player, claim.claimId(),
                        delivery.custodyPlan(), delivery.deliveredStack(), now);
        custodyExecutionScope = new CustodyExecutionScope(
                delivery.custodyPlan(), evidence);
        try {
            return new CustodyBatchExecutor().execute(
                    playerInventoryAdapter, delivery.custodyPlan(), evidence,
                    now, commit -> commitCashClaimCustodyBatch(claim, commit));
        } finally {
            custodyExecutionScope = null;
            playerInventoryAdapter.clearPrepared();
            invalidateConservationAudit();
        }
    }

    synchronized EscrowCommitResult commitProtectedMint(
            ProtectedMintJournalEvent mintEvent) {
        assertServerThread();
        Objects.requireNonNull(mintEvent, "mintEvent");
        EscrowJournalEvent event = new EscrowJournalEvent(
                EscrowJournalEventType.PROTECTED_MINT,
                ProtectedMintEventCodec.encode(mintEvent));
        EscrowCommitResult result = requireReadyCoordinator().commit(
                mintEvent.transactionId(), event);
        invalidateConservationAudit();
        return result;
    }

    synchronized EscrowCommitResult commitAtmWithdrawal(AtmWithdrawalCommit commit) {
        assertServerThread();
        Objects.requireNonNull(commit, "commit");
        EscrowJournalEvent event = new EscrowJournalEvent(
                EscrowJournalEventType.ATM_WITHDRAWAL_COMMIT,
                AtmWithdrawalCommitCodec.encode(commit));
        EscrowCommitResult result = requireReadyCoordinator().commitAtmWithdrawal(
                commit.transactionId(), event);
        invalidateConservationAudit();
        return result;
    }

    synchronized EscrowCommitResult commitAtmWithdrawal(
            ForeignAtmWithdrawalCommit commit
    ) {
        assertServerThread();
        Objects.requireNonNull(commit, "commit");
        EscrowJournalEvent event = new EscrowJournalEvent(
                EscrowJournalEventType.FOREIGN_ATM_WITHDRAWAL_COMMIT,
                ForeignAtmWithdrawalCommitCodec.encode(commit));
        EscrowCommitResult result = requireReadyCoordinator()
                .commitAtmWithdrawal(commit.requestId(), event);
        invalidateConservationAudit();
        return result;
    }

    synchronized EscrowCommitResult commitPlayerPayment(
            PlayerPaymentCommit commit
    ) {
        assertServerThread();
        Objects.requireNonNull(commit, "commit");
        EscrowJournalEvent event = new EscrowJournalEvent(
                EscrowJournalEventType.PLAYER_PAYMENT_COMMIT,
                PlayerPaymentCommitCodec.encode(commit));
        EscrowCommitResult result = requireReadyCoordinator()
                .commitPlayerPayment(commit.transactionId(), event);
        invalidateConservationAudit();
        return result;
    }

    synchronized EscrowCommitResult commitServerShopPurchase(
            ServerShopPurchaseCommit commit
    ) {
        assertServerThread();
        Objects.requireNonNull(commit, "commit");
        EscrowJournalEvent event = new EscrowJournalEvent(
                EscrowJournalEventType.SERVER_SHOP_PURCHASE_COMMIT,
                ServerShopPurchaseCommitCodec.encode(commit));
        EscrowCommitResult result = requireReadyCoordinator()
                .commitServerShopPurchase(commit.requestId(), event);
        invalidateConservationAudit();
        return result;
    }

    synchronized EscrowCommitResult commitServerShopFundingRelease(
            ServerShopFundingRelease release
    ) {
        assertServerThread();
        Objects.requireNonNull(release, "release");
        EscrowJournalEvent event = new EscrowJournalEvent(
                EscrowJournalEventType.SERVER_SHOP_FUNDING_RELEASE,
                ServerShopFundingReleaseCodec.encode(release));
        EscrowCommitResult result = requireReadyCoordinator()
                .commitServerShopFundingRelease(release.releaseId(), event);
        invalidateConservationAudit();
        return result;
    }

    synchronized EscrowCommitResult commitServerShopSellLifecycle(
            ServerShopSellLifecycleEvent lifecycle
    ) {
        assertServerThread();
        Objects.requireNonNull(lifecycle, "lifecycle");
        EscrowJournalEvent event = new EscrowJournalEvent(
                EscrowJournalEventType.SERVER_SHOP_SELL_COMMIT,
                ServerShopSellLifecycleEventCodec.encode(lifecycle));
        EscrowCommitResult result = requireReadyCoordinator()
                .commitServerShopSellLifecycle(
                        lifecycle.requestId(), event);
        invalidateConservationAudit();
        return result;
    }

    synchronized EscrowCommitResult commitServerShopBarterLifecycle(
            ServerShopBarterLifecycleEvent lifecycle
    ) {
        assertServerThread();
        Objects.requireNonNull(lifecycle, "lifecycle");
        EscrowJournalEvent event = new EscrowJournalEvent(
                EscrowJournalEventType.SERVER_SHOP_BARTER_COMMIT,
                ServerShopBarterLifecycleEventCodec.encode(lifecycle));
        EscrowCommitResult result = requireReadyCoordinator()
                .commitServerShopBarterLifecycle(
                        lifecycle.requestId(), event);
        invalidateConservationAudit();
        return result;
    }

    synchronized Optional<ServerShopSellIntent> serverShopSellIntent(
            UUID requestId
    ) {
        assertServerThread();
        if (serverShopIntents == null) {
            return Optional.empty();
        }
        return serverShopIntents.sellIntent(requestId);
    }

    synchronized Optional<ServerShopBarterIntent> serverShopBarterIntent(
            UUID requestId
    ) {
        assertServerThread();
        if (serverShopIntents == null) {
            return Optional.empty();
        }
        return serverShopIntents.barterIntent(requestId);
    }

    public synchronized List<ServerShopSellIntent>
    pendingServerShopSellRecovery(int limit) {
        assertServerThread();
        if (serverShopIntents == null) {
            throw new EscrowRuntimeException(
                    "Server shop intent recovery is unavailable",
                    startupFailure);
        }
        return serverShopIntents.preparedSellIntents(limit);
    }

    public synchronized List<ServerShopBarterIntent>
    pendingServerShopBarterRecovery(int limit) {
        assertServerThread();
        if (serverShopIntents == null) {
            throw new EscrowRuntimeException(
                    "Server shop intent recovery is unavailable",
                    startupFailure);
        }
        return serverShopIntents.preparedBarterIntents(limit);
    }

    synchronized AuctionHouseMutation.ApplyResult
    commitAuctionHouseMutation(AuctionHouseMutation mutation) {
        assertServerThread();
        Objects.requireNonNull(mutation, "mutation");
        if (auctionHouse == null) {
            throw new EscrowRuntimeException(
                    "Auction house persistence is unavailable",
                    startupFailure);
        }
        EscrowJournalEvent event = new EscrowJournalEvent(
                EscrowJournalEventType.AUCTION_HOUSE_MUTATION,
                AuctionHouseMutationCodec.encode(mutation));
        EscrowCommitResult committed = requireReadyCoordinator()
                .commitAuctionHouseMutation(mutation.requestId(), event);
        return new AuctionHouseMutation.ApplyResult(
                auctionHouse.snapshot(), committed.replayed());
    }

    public synchronized AuctionEscrowLifecycleRepository.ApplyResult
    commitAuctionEscrowLifecycle(AuctionEscrowLifecycleEvent event) {
        assertServerThread();
        Objects.requireNonNull(event, "event");
        if (auctionHouse == null) {
            throw new EscrowRuntimeException(
                    "Auction escrow persistence is unavailable",
                    startupFailure);
        }
        EscrowJournalEvent journalEvent = new EscrowJournalEvent(
                EscrowJournalEventType.AUCTION_HOUSE_ESCROW_LIFECYCLE,
                AuctionEscrowLifecycleEventCodec.encode(event));
        EscrowCommitResult committed = requireReadyCoordinator()
                .commitAuctionEscrowLifecycle(event.requestId(),
                        journalEvent);
        return new AuctionEscrowLifecycleRepository.ApplyResult(
                auctionHouse.snapshot(),
                auctionHouse.escrowLifecycleSnapshot(),
                committed.replayed());
    }

    public synchronized AuctionHouseSnapshot auctionHouseSnapshot() {
        assertServerThread();
        if (auctionHouse == null) {
            throw new EscrowRuntimeException(
                    "Auction house persistence is unavailable",
                    startupFailure);
        }
        return auctionHouse.snapshot();
    }

    public synchronized Optional<AuctionListing> auctionHouseListing(
            UUID listingId
    ) {
        assertServerThread();
        if (auctionHouse == null) {
            throw new EscrowRuntimeException(
                    "Auction house persistence is unavailable",
                    startupFailure);
        }
        return Optional.ofNullable(auctionHouse.listing(
                Objects.requireNonNull(listingId, "listingId")));
    }

    public synchronized Optional<AuctionRequestReceipt> auctionHouseReceipt(
            UUID requestId
    ) {
        assertServerThread();
        if (auctionHouse == null) {
            throw new EscrowRuntimeException(
                    "Auction house persistence is unavailable",
                    startupFailure);
        }
        return Optional.ofNullable(auctionHouse.receipt(
                Objects.requireNonNull(requestId, "requestId")));
    }

    public synchronized Optional<AuctionCreateEscrowIntent>
    auctionCreateIntent(UUID requestId) {
        assertServerThread();
        if (auctionHouse == null) {
            throw new EscrowRuntimeException(
                    "Auction escrow persistence is unavailable",
                    startupFailure);
        }
        return Optional.ofNullable(auctionHouse.createIntent(
                Objects.requireNonNull(requestId, "requestId")));
    }

    public synchronized Optional<AuctionEscrowCommit>
    auctionEscrowCommit(UUID requestId) {
        assertServerThread();
        if (auctionHouse == null) {
            throw new EscrowRuntimeException(
                    "Auction escrow persistence is unavailable",
                    startupFailure);
        }
        return Optional.ofNullable(auctionHouse.escrowCommit(
                Objects.requireNonNull(requestId, "requestId")));
    }

    /**
     * Full auction escrow lifecycle state (create intents + commits). Settlement-side actions
     * (buy-now, cancel, expire, settle) need the CREATE commit's item custody for the listing
     * they resolve, which is keyed by the original create requestId — callers scan
     * {@code commits()} for the CREATE commit whose listing matches.
     */
    public synchronized com.enviouse.futureshops.server.market.auction.escrow
            .AuctionEscrowLifecycleState auctionEscrowLifecycleState() {
        assertServerThread();
        if (auctionHouse == null) {
            throw new EscrowRuntimeException(
                    "Auction escrow persistence is unavailable",
                    startupFailure);
        }
        return auctionHouse.escrowLifecycleSnapshot();
    }

    public synchronized List<AuctionCreateEscrowIntent>
    pendingAuctionCreateRecovery(int limit) {
        assertServerThread();
        if (auctionHouse == null) {
            throw new EscrowRuntimeException(
                    "Auction escrow recovery is unavailable",
                    startupFailure);
        }
        if (limit <= 0 || limit > 10_000) {
            throw new IllegalArgumentException(
                    "Auction recovery limit is invalid");
        }
        return auctionHouse.escrowLifecycleSnapshot().createIntents()
                .values().stream().filter(intent -> intent.status()
                == AuctionCreateEscrowIntent.Status.PREPARED)
                .sorted(java.util.Comparator.comparing(intent ->
                        intent.requestId().toString())).limit(limit)
                .toList();
    }

    public synchronized BazaarMutation.ApplyResult commitBazaarMutation(
            BazaarMutation mutation
    ) {
        assertServerThread();
        Objects.requireNonNull(mutation, "mutation");
        if (bazaar == null) {
            throw new EscrowRuntimeException(
                    "Bazaar persistence is unavailable", startupFailure);
        }
        EscrowJournalEvent event = new EscrowJournalEvent(
                EscrowJournalEventType.BAZAAR_MUTATION,
                BazaarMutationCodec.encode(mutation));
        EscrowCommitResult committed = requireReadyCoordinator()
                .commitBazaarMutation(mutation.mutationId(), event);
        return new BazaarMutation.ApplyResult(
                bazaar.snapshot(), committed.replayed());
    }

    public synchronized BazaarEscrowLifecycleRepository.ApplyResult
    commitBazaarEscrowLifecycle(BazaarEscrowLifecycleEvent event) {
        assertServerThread();
        Objects.requireNonNull(event, "event");
        if (bazaar == null) {
            throw new EscrowRuntimeException(
                    "Bazaar escrow persistence is unavailable",
                    startupFailure);
        }
        EscrowJournalEvent journalEvent = new EscrowJournalEvent(
                EscrowJournalEventType.BAZAAR_ESCROW_LIFECYCLE,
                BazaarEscrowLifecycleEventCodec.encode(event));
        EscrowCommitResult committed = requireReadyCoordinator()
                .commitBazaarEscrowLifecycle(event.requestId(),
                        journalEvent);
        invalidateConservationAudit();
        return new BazaarEscrowLifecycleRepository.ApplyResult(
                bazaar.snapshot(), bazaar.escrowLifecycleSnapshot(),
                committed.replayed());
    }

    public synchronized BazaarOrderBookSnapshot bazaarSnapshot() {
        assertServerThread();
        if (bazaar == null) {
            throw new EscrowRuntimeException(
                    "Bazaar persistence is unavailable", startupFailure);
        }
        return bazaar.snapshot();
    }

    public synchronized Optional<BazaarProduct> bazaarProduct(
            String productId
    ) {
        assertServerThread();
        if (bazaar == null) {
            throw new EscrowRuntimeException(
                    "Bazaar persistence is unavailable", startupFailure);
        }
        return Optional.ofNullable(bazaar.product(
                Objects.requireNonNull(productId, "productId")));
    }

    public synchronized Optional<BazaarProduct> bazaarProductVersion(
            String productId,
            long version
    ) {
        assertServerThread();
        if (bazaar == null) {
            throw new EscrowRuntimeException(
                    "Bazaar persistence is unavailable", startupFailure);
        }
        return Optional.ofNullable(bazaar.productVersion(
                Objects.requireNonNull(productId, "productId"), version));
    }

    public synchronized Optional<BazaarOrder> bazaarOrder(UUID orderId) {
        assertServerThread();
        if (bazaar == null) {
            throw new EscrowRuntimeException(
                    "Bazaar persistence is unavailable", startupFailure);
        }
        return Optional.ofNullable(bazaar.order(
                Objects.requireNonNull(orderId, "orderId")));
    }

    public synchronized Optional<BazaarFill> bazaarFill(UUID fillId) {
        assertServerThread();
        if (bazaar == null) {
            throw new EscrowRuntimeException(
                    "Bazaar persistence is unavailable", startupFailure);
        }
        return Optional.ofNullable(bazaar.fill(
                Objects.requireNonNull(fillId, "fillId")));
    }

    public synchronized Optional<BazaarRequestReceipt> bazaarReceipt(
            UUID requestId
    ) {
        assertServerThread();
        if (bazaar == null) {
            throw new EscrowRuntimeException(
                    "Bazaar persistence is unavailable", startupFailure);
        }
        return Optional.ofNullable(bazaar.receipt(
                Objects.requireNonNull(requestId, "requestId")));
    }

    public synchronized Optional<String> bazaarLifecycleReceipt(
            UUID mutationId
    ) {
        assertServerThread();
        if (bazaar == null) {
            throw new EscrowRuntimeException(
                    "Bazaar persistence is unavailable", startupFailure);
        }
        return Optional.ofNullable(bazaar.lifecycleReceipt(
                Objects.requireNonNull(mutationId, "mutationId")));
    }

    public synchronized Optional<BazaarCreateEscrowIntent>
    bazaarCreateIntent(UUID requestId) {
        assertServerThread();
        if (bazaar == null) {
            throw new EscrowRuntimeException(
                    "Bazaar escrow persistence is unavailable",
                    startupFailure);
        }
        return Optional.ofNullable(bazaar.createIntent(
                Objects.requireNonNull(requestId, "requestId")));
    }

    public synchronized List<BazaarCreateEscrowIntent>
    pendingBazaarCreateRecovery(int limit) {
        assertServerThread();
        if (bazaar == null) {
            throw new EscrowRuntimeException(
                    "Bazaar escrow recovery is unavailable",
                    startupFailure);
        }
        if (limit <= 0 || limit > 10_000) {
            throw new IllegalArgumentException(
                    "Bazaar recovery limit is invalid");
        }
        return bazaar.escrowLifecycleSnapshot().createIntents().values()
                .stream().filter(intent -> intent.status()
                == BazaarCreateEscrowIntent.Status.PREPARED
                || intent.status()
                == BazaarCreateEscrowIntent.Status.RECOVERY_REQUIRED)
                .sorted(java.util.Comparator.comparing(intent ->
                        intent.requestId().toString())).limit(limit)
                .toList();
    }

    public synchronized EscrowCommitResult commitPlayerShopEscrowLifecycle(
            PlayerShopEscrowLifecycleEvent lifecycle
    ) {
        assertServerThread();
        Objects.requireNonNull(lifecycle, "lifecycle");
        if (playerShopEscrow == null) {
            throw new EscrowRuntimeException(
                    "Player shop escrow persistence is unavailable",
                    startupFailure);
        }
        EscrowJournalEvent event = new EscrowJournalEvent(
                EscrowJournalEventType.PLAYER_SHOP_ESCROW_LIFECYCLE,
                PlayerShopEscrowLifecycleEventCodec.encode(lifecycle));
        EscrowCommitResult committed = requireReadyCoordinator()
                .commitPlayerShopEscrowLifecycle(
                        lifecycle.eventId(), event);
        invalidateConservationAudit();
        return committed;
    }

    public synchronized MarketControlCommitResult
    commitMarketControlTransition(
            MarketControlTransitionCommand command
    ) {
        assertServerThread();
        MarketControlTransitionCommand value = Objects.requireNonNull(
                command, "command");
        if (value.targetStatus()
                == MarketModuleStatus.CANCEL_AND_REFUND) {
            throw new IllegalArgumentException(
                    "Cancel and refund requires a composite cancellation plan");
        }
        if (marketControl == null) {
            throw new EscrowRuntimeException(
                    "Market control persistence is unavailable",
                    startupFailure);
        }
        MarketControlApplyResult planned =
                marketControl.planStandalone(value);
        if (planned.replayed()) {
            return new MarketControlCommitResult(
                    marketControl.snapshot(), planned.auditEntry(), true);
        }
        MarketControlMutation mutation =
                planned.mutation().orElseThrow();
        EscrowJournalEvent event = new EscrowJournalEvent(
                EscrowJournalEventType.MARKET_CONTROL_MUTATION,
                MarketControlMutationCodec.encode(mutation));
        EscrowCommitResult committed = requireReadyCoordinator()
                .commitMarketControlMutation(value.requestId(), event);
        MarketControlRequestReceipt receipt = marketControl.receipt(
                value.requestId());
        if (receipt == null) {
            throw new EscrowRuntimeException(
                    "Market control receipt was not materialized");
        }
        return new MarketControlCommitResult(marketControl.snapshot(),
                receipt.auditEntry(), committed.replayed());
    }

    public synchronized MarketControlState marketControlSnapshot() {
        assertServerThread();
        if (marketControl == null) {
            throw new EscrowRuntimeException(
                    "Market control persistence is unavailable",
                    startupFailure);
        }
        return marketControl.snapshot();
    }

    public synchronized MarketModuleControl marketModuleControl(
            MarketControlModule module
    ) {
        return marketControlSnapshot().module(
                Objects.requireNonNull(module, "module"));
    }

    public synchronized MarketControlAuditProjection
    marketControlAuditProjection() {
        assertServerThread();
        if (marketControl == null) {
            throw new EscrowRuntimeException(
                    "Market control persistence is unavailable",
                    startupFailure);
        }
        return marketControl.auditProjection();
    }

    public synchronized Optional<PlayerShopEscrowSavedData.Entry>
    playerShopEscrowEntry(UUID requestId) {
        assertServerThread();
        if (playerShopEscrow == null) {
            throw new EscrowRuntimeException(
                    "Player shop escrow persistence is unavailable",
                    startupFailure);
        }
        return playerShopEscrow.entry(
                Objects.requireNonNull(requestId, "requestId"));
    }

    public synchronized List<PlayerShopEscrowSavedData.Entry>
    pendingPlayerShopRecovery(int limit) {
        assertServerThread();
        if (playerShopEscrow == null) {
            throw new EscrowRuntimeException(
                    "Player shop escrow recovery is unavailable",
                    startupFailure);
        }
        return playerShopEscrow.pendingRecovery(limit);
    }

    public synchronized StockCommandResult commitStockMutation(
            StockMutationCommand command
    ) {
        assertServerThread();
        Objects.requireNonNull(command, "command");
        if (stock == null) {
            throw new EscrowRuntimeException(
                    "Escrow stock is unavailable", startupFailure);
        }
        EscrowJournalEvent event = new EscrowJournalEvent(
                EscrowJournalEventType.STOCK_MUTATION,
                StockMutationCommandCodec.encode(command));
        EscrowCommitResult committed = requireReadyCoordinator()
                .commitStockMutation(command.requestId(), event);
        invalidateConservationAudit();
        return stock.resultForRequest(command.requestId(),
                committed.replayed());
    }

    public synchronized Optional<CatalogStockState> stockListing(StockKey key) {
        assertServerThread();
        if (stock == null) {
            throw new EscrowRuntimeException(
                    "Escrow stock is unavailable", startupFailure);
        }
        return Optional.ofNullable(stock.listing(
                Objects.requireNonNull(key, "key")));
    }

    public synchronized List<StockReservation> stockReservations(
            UUID transactionId
    ) {
        assertServerThread();
        if (stock == null) {
            throw new EscrowRuntimeException(
                    "Escrow stock is unavailable", startupFailure);
        }
        return stock.reservationsForTransaction(Objects.requireNonNull(
                transactionId, "transactionId"));
    }

    public synchronized Optional<StockMutationReceipt> stockReceipt(
            UUID requestId
    ) {
        assertServerThread();
        if (stock == null) {
            throw new EscrowRuntimeException(
                    "Escrow stock is unavailable", startupFailure);
        }
        return Optional.ofNullable(stock.receipt(Objects.requireNonNull(
                requestId, "requestId")));
    }

    public synchronized StockStoreSnapshot stockSnapshot() {
        assertServerThread();
        if (stock == null) {
            throw new EscrowRuntimeException(
                    "Escrow stock is unavailable", startupFailure);
        }
        return stock.snapshot();
    }

    public synchronized StockConservationReport stockConservationReport() {
        assertServerThread();
        if (stock == null) {
            throw new EscrowRuntimeException(
                    "Escrow stock is unavailable", startupFailure);
        }
        return stock.conservation();
    }

    public synchronized DurableItemInventoryMutationGateway
    itemInventoryMutationGateway() {
        assertServerThread();
        if (itemInventoryGateway == null) {
            throw new EscrowRuntimeException(
                    "Item inventory mutation gateway is unavailable",
                    startupFailure);
        }
        requireReadyCoordinator();
        return itemInventoryGateway;
    }

    public synchronized ExactItemInventoryRuntime exactItemInventoryRuntime() {
        assertServerThread();
        if (exactItemInventoryRuntime == null) {
            throw new EscrowRuntimeException(
                    "Exact item inventory runtime is unavailable",
                    startupFailure);
        }
        requireReadyCoordinator();
        return exactItemInventoryRuntime;
    }

    synchronized boolean serverShopLifecycleReady() {
        assertServerThread();
        return coordinator != null && isReady();
    }

    synchronized ServerShopSellItemCustody serverShopSellCustody(
            ServerPlayer player
    ) {
        assertServerThread();
        Objects.requireNonNull(player, "player");
        if (exactItemInventoryRuntime == null
                || itemInventoryGateway == null) {
            throw new EscrowRuntimeException(
                    "Server shop sell item custody is unavailable",
                    startupFailure);
        }
        return ServerShopSellItemCustody.exact(
                exactItemInventoryRuntime,
                new ServerPlayerItemInventoryAccess(player),
                itemInventoryGateway);
    }

    synchronized ServerShopBarterItemCustody serverShopBarterCustody(
            ServerPlayer player
    ) {
        assertServerThread();
        Objects.requireNonNull(player, "player");
        if (exactItemInventoryRuntime == null
                || itemInventoryGateway == null) {
            throw new EscrowRuntimeException(
                    "Server shop barter item custody is unavailable",
                    startupFailure);
        }
        return ServerShopBarterItemCustody.exact(
                exactItemInventoryRuntime,
                new ServerPlayerItemInventoryAccess(player),
                itemInventoryGateway);
    }

    public synchronized ExactItemClaimCollectionResult collectExactItemClaim(
            UUID playerId,
            UUID claimId,
            Instant now
    ) {
        assertServerThread();
        UUID player = Objects.requireNonNull(playerId, "playerId");
        UUID claimIdentity = Objects.requireNonNull(claimId, "claimId");
        Objects.requireNonNull(now, "now");
        requireReadyCoordinator();
        EscrowClaim claim = claims.getClaim(claimIdentity);
        if (claim == null || !claim.ownerId().equals(player)) {
            throw new EscrowRuntimeException(
                    "Exact item claim does not belong to the player");
        }
        if (!ExactItemClaimDeliveryPlanner.supportedKind(claim.kind())) {
            throw new EscrowRuntimeException(
                    "Claim is not an exact item claim");
        }
        if (claim.status() == ClaimStatus.COMPLETED
                || claim.remainingUnits() == 0L) {
            return ExactItemClaimCollectionResult.pending(claimIdentity,
                    ExactItemClaimCollectionStatus.NOT_PENDING, 0L);
        }
        if (claim.status() == ClaimStatus.QUARANTINED) {
            return ExactItemClaimCollectionResult.pending(claimIdentity,
                    ExactItemClaimCollectionStatus.MANUAL_REVIEW,
                    claim.remainingUnits());
        }
        ExactItemClaimPayload payload;
        ClaimAttemptSelection selection;
        try {
            payload = ExactItemClaimDeliveryPlanner.payload(claim);
            selection = selectExactItemClaimAttempt(claim);
        } catch (RuntimeException exception) {
            return ExactItemClaimCollectionResult.pending(claimIdentity,
                    ExactItemClaimCollectionStatus.INVALID_PAYLOAD,
                    claim.remainingUnits());
        }
        if (selection.entry().isPresent()
                && selection.entry().orElseThrow().status()
                == ItemInventoryJournalStatus.COMMITTED) {
            ItemInventoryJournalEntry entry = selection.entry()
                    .orElseThrow();
            itemInventoryGateway.appendCommittedDurably(
                    entry.committedReceipt().orElseThrow());
            return exactItemClaimAppliedResult(claimIdentity,
                    selection.requestId(), true);
        }
        ServerPlayer online = ownerServer.getPlayerList().getPlayer(player);
        if (online == null) {
            return ExactItemClaimCollectionResult.pending(claimIdentity,
                    ExactItemClaimCollectionStatus.OFFLINE_PENDING,
                    claim.remainingUnits());
        }
        ServerPlayerItemInventoryAccess access =
                new ServerPlayerItemInventoryAccess(online);
        if (selection.entry().isPresent()) {
            ItemInventoryJournalEntry entry = selection.entry()
                    .orElseThrow();
            if (entry.status() == ItemInventoryJournalStatus.QUARANTINED) {
                return new ExactItemClaimCollectionResult(claimIdentity,
                        ExactItemClaimCollectionStatus.MANUAL_REVIEW, 0L,
                        claim.remainingUnits(),
                        Optional.of(selection.requestId()), true);
            }
            return finishExactItemClaimExecution(claimIdentity,
                    selection.requestId(),
                    exactItemClaimInventoryRuntime.recover(access,
                            selection.requestId()));
        }
        int remaining;
        try {
            remaining = Math.toIntExact(claim.remainingUnits());
        } catch (ArithmeticException exception) {
            return ExactItemClaimCollectionResult.pending(claimIdentity,
                    ExactItemClaimCollectionStatus.INVALID_PAYLOAD,
                    claim.remainingUnits());
        }
        ItemInventoryState state = access.capture();
        int deliverable = exactItemClaimCapacity(state, payload, remaining,
                selection.requestId());
        if (deliverable == 0) {
            return ExactItemClaimCollectionResult.pending(claimIdentity,
                    ExactItemClaimCollectionStatus.FULL_INVENTORY,
                    claim.remainingUnits());
        }
        ItemStack portion = ExactItemClaimDeliveryPlanner.portion(
                payload, deliverable);
        ItemInventoryBatchEntry entry = ItemInventoryBatchEntry.insert(
                ExactItemClaimDeliveryPlanner.entryId(
                        selection.requestId()), portion);
        ItemInventoryExecutionResult execution =
                exactItemClaimInventoryRuntime.execute(access,
                        claim.transactionId(), selection.requestId(),
                        List.of(entry));
        return finishExactItemClaimExecution(claimIdentity,
                selection.requestId(), execution);
    }

    public synchronized ExactItemClaimCollectionResult collectExactItemClaim(
            ServerPlayer player,
            UUID claimId,
            Instant now
    ) {
        Objects.requireNonNull(player, "player");
        return collectExactItemClaim(player.getUUID(), claimId, now);
    }

    public synchronized List<ExactItemClaimCollectionResult>
    collectPendingExactItemClaims(
            ServerPlayer player,
            int limit,
            Instant now
    ) {
        assertServerThread();
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(now, "now");
        if (limit <= 0 || limit > 256) {
            throw new IllegalArgumentException(
                    "Exact item claim collection limit is invalid");
        }
        List<ExactItemClaimCollectionResult> results =
                new java.util.ArrayList<>();
        for (EscrowClaim claim : claims.pendingFor(
                player.getUUID(), limit)) {
            if (!ExactItemClaimDeliveryPlanner.supportedKind(
                    claim.kind())) {
                continue;
            }
            results.add(collectExactItemClaim(player, claim.claimId(), now));
            if (results.size() == limit) {
                break;
            }
        }
        return List.copyOf(results);
    }

    private ClaimAttemptSelection selectExactItemClaimAttempt(
            EscrowClaim claim
    ) {
        for (int retryIndex = 0;
             retryIndex <= ExactItemClaimDeliveryPlanner.MAX_RETRY_INDEX;
             retryIndex++) {
            UUID requestId = ExactItemClaimDeliveryPlanner.requestId(
                    claim, claim.remainingUnits(), retryIndex);
            Optional<ItemInventoryJournalEntry> entry =
                    itemInventoryJournal.find(requestId);
            if (entry.isEmpty()) {
                return new ClaimAttemptSelection(requestId, retryIndex,
                        Optional.empty());
            }
            ItemInventoryJournalEntry existing = entry.orElseThrow();
            if (!existing.intent().token().playerId().equals(
                    claim.ownerId())
                    || !existing.intent().token().transactionId().equals(
                    claim.transactionId())) {
                throw new EscrowRuntimeException(
                        "Exact item claim request identity conflicts");
            }
            if (existing.status() != ItemInventoryJournalStatus.ABORTED) {
                return new ClaimAttemptSelection(requestId, retryIndex,
                        entry);
            }
        }
        throw new EscrowRuntimeException(
                "Exact item claim retry capacity is exhausted");
    }

    private int exactItemClaimCapacity(
            ItemInventoryState state,
            ExactItemClaimPayload payload,
            int remaining,
            UUID requestId
    ) {
        ItemStack full = ExactItemClaimDeliveryPlanner.portion(
                payload, remaining);
        if (exactItemClaimPlan(state, full, requestId).applicable()) {
            return remaining;
        }
        CompoundTag saved = full.save(new CompoundTag());
        if (saved.contains("ForgeCaps", Tag.TAG_COMPOUND)
                && !saved.getCompound("ForgeCaps").isEmpty()) {
            return 0;
        }
        int low = 1;
        int high = remaining - 1;
        int result = 0;
        while (low <= high) {
            int middle = low + (high - low) / 2;
            ItemStack portion = ExactItemClaimDeliveryPlanner.portion(
                    payload, middle);
            if (exactItemClaimPlan(state, portion, requestId).applicable()) {
                result = middle;
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }
        return result;
    }

    private static ItemInventoryMutationPlan exactItemClaimPlan(
            ItemInventoryState state,
            ItemStack stack,
            UUID requestId
    ) {
        return ItemInventoryBatchPlanner.plan(state, List.of(
                ItemInventoryBatchEntry.insert(
                        ExactItemClaimDeliveryPlanner.entryId(requestId),
                        stack)));
    }

    private ExactItemClaimCollectionResult finishExactItemClaimExecution(
            UUID claimId,
            UUID requestId,
            ItemInventoryExecutionResult execution
    ) {
        return switch (execution.status()) {
            case APPLIED -> exactItemClaimAppliedResult(
                    claimId, requestId, false);
            case REPLAYED -> exactItemClaimAppliedResult(
                    claimId, requestId, true);
            case INSUFFICIENT_CAPACITY ->
                    ExactItemClaimCollectionResult.pending(claimId,
                            ExactItemClaimCollectionStatus.FULL_INVENTORY,
                            claims.getClaim(claimId).remainingUnits());
            case MANUAL_REVIEW -> new ExactItemClaimCollectionResult(
                    claimId, ExactItemClaimCollectionStatus.MANUAL_REVIEW,
                    0L, claims.getClaim(claimId).remainingUnits(),
                    Optional.of(requestId), execution.replayed());
            case ABORTED, RECOVERY_REQUIRED, INSUFFICIENT_ITEMS,
                 UNSUPPORTED_STACK -> new ExactItemClaimCollectionResult(
                    claimId,
                    ExactItemClaimCollectionStatus.RECOVERY_REQUIRED,
                    0L, claims.getClaim(claimId).remainingUnits(),
                    Optional.of(requestId), execution.replayed());
        };
    }

    private ExactItemClaimCollectionResult exactItemClaimAppliedResult(
            UUID claimId,
            UUID requestId,
            boolean replayed
    ) {
        ClaimAttemptResult attempt = claims.attempt(
                ExactItemClaimDeliveryPlanner.requestKey(
                        claimId, requestId)).orElseThrow(() ->
                new EscrowRuntimeException(
                        "Exact item claim delivery was not materialized"));
        ExactItemClaimCollectionStatus status;
        if (replayed) {
            status = ExactItemClaimCollectionStatus.REPLAYED;
        } else if (attempt.remainingUnits() == 0L) {
            status = ExactItemClaimCollectionStatus.DELIVERED;
        } else {
            status = ExactItemClaimCollectionStatus.PARTIALLY_DELIVERED;
        }
        return new ExactItemClaimCollectionResult(claimId, status,
                attempt.deliveredUnits(), attempt.remainingUnits(),
                Optional.of(requestId), replayed);
    }

    private record ClaimAttemptSelection(
            UUID requestId,
            int retryIndex,
            Optional<ItemInventoryJournalEntry> entry
    ) {
        private ClaimAttemptSelection {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(entry, "entry");
            if (retryIndex < 0
                    || retryIndex
                    > ExactItemClaimDeliveryPlanner.MAX_RETRY_INDEX) {
                throw new IllegalArgumentException(
                        "Exact item claim retry index is invalid");
            }
        }
    }

    public synchronized Optional<ItemInventoryJournalEntry>
    itemInventoryMutation(UUID requestId) {
        assertServerThread();
        if (itemInventoryJournal == null) {
            throw new EscrowRuntimeException(
                    "Item inventory journal is unavailable",
                    startupFailure);
        }
        return itemInventoryJournal.find(Objects.requireNonNull(
                requestId, "requestId"));
    }

    public synchronized List<ItemInventoryJournalEntry>
    itemInventoryMutationsForPlayer(UUID playerId, int limit) {
        assertServerThread();
        if (itemInventoryJournal == null) {
            throw new EscrowRuntimeException(
                    "Item inventory journal is unavailable",
                    startupFailure);
        }
        return itemInventoryJournal.entriesForPlayer(
                Objects.requireNonNull(playerId, "playerId"), limit);
    }

    public synchronized boolean itemInventoryPlayerQuarantined(
            UUID playerId
    ) {
        assertServerThread();
        if (itemInventoryJournal == null) {
            throw new EscrowRuntimeException(
                    "Item inventory journal is unavailable",
                    startupFailure);
        }
        return itemInventoryJournal.playerQuarantined(
                Objects.requireNonNull(playerId, "playerId"));
    }

    public synchronized Optional<ItemInventoryQuarantineInspection>
    inspectItemInventoryQuarantine(UUID requestId) {
        assertServerThread();
        if (itemInventoryJournal == null) {
            throw new EscrowRuntimeException(
                    "Item inventory journal is unavailable",
                    startupFailure);
        }
        return itemInventoryJournal.inspectQuarantine(
                Objects.requireNonNull(requestId, "requestId"));
    }

    public synchronized ItemInventoryQuarantineAdministration
    planItemInventoryQuarantineAdministration(
            UUID commandId,
            UUID requestId,
            UUID actorId,
            ItemInventoryQuarantineAdministrativeAction action,
            Optional<EscrowClaim> refundClaim,
            String reason,
            Instant reviewedAt
    ) {
        assertServerThread();
        ItemInventoryQuarantineInspection inspection =
                inspectItemInventoryQuarantine(requestId).orElseThrow(() ->
                new EscrowRuntimeException(
                        "Item inventory quarantine is unavailable"));
        if (inspection.resolved()) {
            throw new EscrowRuntimeException(
                    "Item inventory quarantine is already resolved");
        }
        return new ItemInventoryQuarantineAdministration(commandId,
                requestId,
                inspection.entry().intent().token().playerId(), actorId,
                action, itemInventoryJournal.revision(),
                ItemInventoryQuarantineAdministration.quarantineDigest(
                        inspection.entry().quarantine().orElseThrow()),
                refundClaim, reason, reviewedAt);
    }

    public synchronized ItemInventoryQuarantineInspection
    administerItemInventoryQuarantine(
            ItemInventoryQuarantineAdministration administration
    ) {
        assertServerThread();
        Objects.requireNonNull(administration, "administration");
        requireReadyCoordinator().commitItemInventoryMutation(
                administration.commandId(), new EscrowJournalEvent(
                        EscrowJournalEventType
                                .ITEM_INVENTORY_QUARANTINE_ADMINISTRATION,
                        ItemInventoryQuarantineAdministrationCodec.encode(
                                administration)));
        return inspectItemInventoryQuarantine(
                administration.requestId()).orElseThrow(() ->
                new EscrowRuntimeException(
                        "Item inventory quarantine review was not materialized"));
    }

    public synchronized ItemInventoryJournalCompaction
    planItemInventoryJournalCompaction(
            UUID commandId,
            int limit
    ) {
        assertServerThread();
        Objects.requireNonNull(commandId, "commandId");
        if (limit <= 0
                || limit > ItemInventoryJournalCompaction
                .MAX_TOMBSTONES_PER_COMPACTION) {
            throw new IllegalArgumentException(
                    "Item inventory compaction limit is invalid");
        }
        EscrowVerifiedItemInventoryCheckpoint verified =
                requireReadyCoordinator().verifiedItemInventoryCheckpoint()
                        .orElseThrow(() -> new EscrowRuntimeException(
                                "A verified checkpoint is required for item inventory compaction"));
        Map<UUID, ItemInventoryJournalEntry> checkpointEntries =
                verified.snapshot().entries().stream().collect(
                        java.util.stream.Collectors.toMap(
                                entry -> entry.intent().token().requestId(),
                                java.util.function.Function.identity()));
        List<ItemInventoryTerminalTombstone> tombstones =
                new java.util.ArrayList<>();
        for (ItemInventoryJournalEntry current
                : itemInventoryJournal.snapshot().entries()) {
            if (current.status() != ItemInventoryJournalStatus.COMMITTED
                    && current.status()
                    != ItemInventoryJournalStatus.ABORTED) {
                continue;
            }
            ItemInventoryJournalEntry checkpoint = checkpointEntries.get(
                    current.intent().token().requestId());
            if (!current.equals(checkpoint)) {
                continue;
            }
            tombstones.add(ItemInventoryTerminalTombstone.fromEntry(
                    current, commandId,
                    verified.reference().checkpointId()));
            if (tombstones.size() == limit) {
                break;
            }
        }
        if (tombstones.isEmpty()) {
            throw new EscrowRuntimeException(
                    "No checkpointed terminal item inventory entries are eligible");
        }
        var reference = verified.reference();
        return new ItemInventoryJournalCompaction(commandId,
                reference.checkpointId(),
                reference.sourceJournalLineageId(),
                reference.replacementJournalLineageId(),
                reference.baseJournalSequence(),
                reference.checkpointSha256(), tombstones);
    }

    public synchronized ItemInventoryJournalCompactionResult
    compactItemInventoryJournal(
            ItemInventoryJournalCompaction compaction
    ) {
        assertServerThread();
        Objects.requireNonNull(compaction, "compaction");
        EscrowVerifiedItemInventoryCheckpoint verified =
                requireReadyCoordinator().verifiedItemInventoryCheckpoint()
                        .orElseThrow(() -> new EscrowRuntimeException(
                                "A verified checkpoint is required for item inventory compaction"));
        var reference = verified.reference();
        if (!compaction.matchesCheckpoint(reference.checkpointId(),
                reference.sourceJournalLineageId(),
                reference.replacementJournalLineageId(),
                reference.baseJournalSequence(),
                reference.checkpointSha256())) {
            throw new EscrowRuntimeException(
                    "Item inventory compaction checkpoint is stale");
        }
        Map<UUID, ItemInventoryJournalEntry> checkpointEntries =
                verified.snapshot().entries().stream().collect(
                        java.util.stream.Collectors.toMap(
                                entry -> entry.intent().token().requestId(),
                                java.util.function.Function.identity()));
        int fullEntries = 0;
        for (ItemInventoryTerminalTombstone tombstone
                : compaction.tombstones()) {
            ItemInventoryJournalEntry checkpoint = checkpointEntries.get(
                    tombstone.requestId());
            if (!tombstone.matchesEntry(checkpoint)) {
                throw new EscrowRuntimeException(
                        "Item inventory compaction is not proven by the checkpoint");
            }
            Optional<ItemInventoryJournalEntry> current =
                    itemInventoryJournal.find(tombstone.requestId());
            if (current.isPresent()) {
                if (!tombstone.matchesEntry(current.orElseThrow())) {
                    throw new EscrowRuntimeException(
                            "Item inventory compaction current evidence conflicts");
                }
                fullEntries++;
            } else if (!itemInventoryJournal.findTombstone(
                    tombstone.requestId()).filter(tombstone::equals)
                    .isPresent()) {
                throw new EscrowRuntimeException(
                        "Item inventory compaction evidence is missing");
            }
        }
        if (fullEntries != 0
                && fullEntries != compaction.tombstones().size()) {
            throw new EscrowRuntimeException(
                    "Item inventory compaction is partially materialized");
        }
        EscrowCommitResult committed = requireReadyCoordinator()
                .commitItemInventoryMutation(compaction.commandId(),
                        new EscrowJournalEvent(EscrowJournalEventType
                                .ITEM_INVENTORY_JOURNAL_COMPACTION,
                                ItemInventoryJournalCompactionCodec.encode(
                                        compaction)));
        return new ItemInventoryJournalCompactionResult(
                committed.replayed() ? 0 : fullEntries,
                committed.replayed());
    }

    public List<CustodyPreparedBatch> unresolvedCustodyRecovery(int limit) {
        if (custody == null) {
            throw new EscrowRuntimeException("Escrow custody is unavailable", startupFailure);
        }
        return custody.unresolvedPreparedBatches(limit);
    }

    public synchronized void registerRecoveryHandler(EscrowOperation operation,
                                                     EscrowRecoveryHandler handler) {
        assertServerThread();
        if (recoveryScheduler == null) {
            throw new EscrowRuntimeException("Escrow recovery is unavailable", startupFailure);
        }
        recoveryScheduler.register(operation, handler);
    }

    public synchronized void registerCustodyRecoveryAdapter(CustodyAdapter adapter) {
        assertServerThread();
        Objects.requireNonNull(adapter, "adapter");
        String normalized = Objects.requireNonNull(adapter.adapterId(), "adapterId").trim();
        if (normalized.isEmpty() || normalized.length() > 256) {
            throw new IllegalArgumentException("Invalid custody recovery adapter ID");
        }
        CustodyAdapter existing = custodyRecoveryAdapters.putIfAbsent(normalized, adapter);
        if (existing != null && existing != adapter) {
            throw new EscrowRuntimeException("Custody recovery adapter is already registered");
        }
        custodyRecoveryFailure = null;
    }

    public synchronized List<EscrowRecoveryWork> pendingTransactionRecovery(int limit) {
        if (recoveryScheduler == null) {
            throw new EscrowRuntimeException("Escrow recovery is unavailable", startupFailure);
        }
        return recoveryScheduler.pending(limit);
    }

    @Override
    public void close() {
        assertServerThread();
        if (coordinator != null) {
            coordinator.close();
        } else {
            unavailableState = EscrowRuntimeState.STOPPED;
        }
    }

    private EscrowRuntimeCoordinator requireCoordinator() {
        if (coordinator == null) {
            throw new EscrowRuntimeException("Escrow runtime is unavailable", startupFailure);
        }
        return coordinator;
    }

    private EscrowRuntimeCoordinator requireReadyCoordinator() {
        EscrowRuntimeCoordinator available = requireCoordinator();
        synchronized (available) {
            if (!isReady()
                    && !(recoveryDepth.get() > 0 && available.isReady())) {
                throw new EscrowRuntimeException(
                        "Escrow runtime is not ready and is in state "
                                + state(), failure().orElse(null));
            }
        }
        return available;
    }

    private EscrowSavedDataMutationApplier requireMaintenanceApplier() {
        if (applier == null || maintenanceController == null) {
            throw new EscrowRuntimeException(
                    "Escrow maintenance repair is unavailable", startupFailure);
        }
        return applier;
    }

    private EscrowMaintenanceLiveGuard maintenanceLiveGuard() {
        return new EscrowMaintenanceLiveGuard() {
            @Override
            public boolean journalHealthyAndAligned() {
                return coordinator != null && coordinator.journalHealthyAndAligned();
            }

            @Override
            public boolean domainMaintenanceActive() {
                return EscrowRuntimeService.this.domainMaintenanceActive();
            }

            @Override
            public boolean recoveryClear() {
                return EscrowRuntimeService.this.maintenanceRecoveryClear();
            }

            @Override
            public boolean conservationVerified() {
                return EscrowRuntimeService.this.maintenanceConservationVerified();
            }

            @Override
            public EscrowGlobalVerificationSnapshot globalVerification() {
                if (coordinator == null || checkpointBundle == null) {
                    throw new EscrowRuntimeException(
                            "Escrow global verification is unavailable");
                }
                return EscrowGlobalStateVerifier.verify(
                        coordinator.lastAppliedSequence(),
                        checkpointBundle.captureSnapshots());
            }
        };
    }

    private boolean domainMaintenanceActive() {
        return coordinator != null && coordinator.state() == EscrowRuntimeState.READY
                && (recoveryScheduler.hasBlockingWork()
                || recoveryScheduler.hasManualReviewWork()
                || hasBlockedCustodyRecovery()
                || custodyRecoveryFailure != null
                || protectedCashDiscoveryFailure != null
                || protectedCashCleanupFailure != null
                || foreignCashDiscoveryFailure != null
                || foreignCashCleanupFailure != null
                || serverShopFundingRecoveryFailure != null
                || itemInventoryRecoveryFailure != null
                || conservationFailure != null);
    }

    public synchronized boolean maintenanceRecoveryClear() {
        return domainRecoveryInitialized
                && protectedCashDiscoveryComplete
                && protectedCashDiscoveryFailure == null
                && foreignCashDiscoveryComplete
                && foreignCashDiscoveryFailure == null
                && paymentHistoryProjector.complete()
                && recoveryScheduler.enumerationComplete()
                && !recoveryScheduler.hasRunnableWork()
                && !recoveryScheduler.hasBlockingWork()
                && !recoveryScheduler.hasScheduledWork()
                && !recoveryScheduler.hasManualReviewWork()
                && !hasUnresolvedCustodyRecovery()
                && custodyRecoveryFailure == null
                && protectedCashCleanupWork.isEmpty()
                && protectedCashCleanupFailure == null
                && foreignCashCleanupWork.isEmpty()
                && foreignCashCleanupFailure == null
                && serverShopFundingRecoveryWork.isEmpty()
                && serverShopFundingRecoveryFailure == null
                && itemInventoryRecoveryFailure == null
                && !hasOnlinePreparedItemMutations();
    }

    private boolean maintenanceConservationVerified() {
        try {
            EscrowConservationReport report = verifyConservation();
            conservationReport = report;
            conservationAuditComplete = true;
            conservationFailure = report.conserved() ? null : new EscrowRuntimeException(
                    "Escrow cross domain conservation failed");
            if (!report.conserved()) {
                return false;
            }
            return true;
        } catch (RuntimeException exception) {
            conservationFailure = exception;
            conservationAuditComplete = true;
            return false;
        }
    }

    private boolean startupConservationVerified() {
        if (conservationAuditComplete) {
            return conservationFailure == null
                    && conservationReport != null
                    && conservationReport.conserved()
                    && stockConservationReport != null
                    && stockConservationReport.conserved();
        }
        return maintenanceConservationVerified();
    }

    private EscrowConservationReport verifyConservation() {
        if (ledger == null || claims == null || custody == null
                || protectedMints == null || stock == null) {
            throw new EscrowRuntimeException(
                    "Escrow cross domain conservation is unavailable", startupFailure);
        }
        StockConservationReport verifiedStock = stock.conservation();
        stockConservationReport = verifiedStock;
        if (!verifiedStock.conserved()) {
            throw new EscrowRuntimeException(
                    "Escrow stock conservation failed. "
                            + String.join(", ", verifiedStock.violations()));
        }
        return EscrowCrossDomainConservationAudit.verify(
                ledger, claims, custody, protectedMints);
    }

    private void invalidateConservationAudit() {
        conservationAuditComplete = false;
        conservationReport = null;
        stockConservationReport = null;
        conservationFailure = null;
    }

    private boolean hasUnresolvedCustodyRecovery() {
        return custody != null && custody.hasUnresolvedPreparedOperations();
    }

    private boolean hasResolvableCustodyRecovery() {
        if (!hasUnresolvedCustodyRecovery() || custodyRecoveryFailure != null) {
            return false;
        }
        for (CustodyPreparedBatch batch : custody.unresolvedPreparedBatches(10_000)) {
            if (batch.status() == CustodyBatchStatus.PREPARED
                    || custodyRecoveryAdapters.containsKey(
                    batch.operations().get(0).adapterId())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasBlockedCustodyRecovery() {
        return hasUnresolvedCustodyRecovery() && !hasResolvableCustodyRecovery();
    }

    private int recoverOneCustodyOperation() {
        CustodyPreparedBatch prepared = null;
        CustodyAdapter adapter = null;
        for (CustodyPreparedBatch candidate : custody.unresolvedPreparedBatches(10_000)) {
            CustodyAdapter candidateAdapter = custodyRecoveryAdapters.get(
                    candidate.operations().get(0).adapterId());
            if (candidate.status() == CustodyBatchStatus.PREPARED
                    || candidateAdapter != null) {
                prepared = candidate;
                adapter = candidateAdapter;
                break;
            }
        }
        if (prepared == null) {
            return 0;
        }
        CustodyPreparedBatch selected = prepared;
        CustodyAdapter selectedAdapter = adapter;
        try {
            return withRecoveryLane(() -> {
                if (selected.status() == CustodyBatchStatus.PREPARED) {
                    commitRecoveryCustodyBatch(selected, CustodyBatchCommit.state(
                            selected.markNotApplied(selected.revision(),
                                    selected.updatedAt(),
                                    "Prepared batch did not reach the custody adapter")));
                } else if (selectedAdapter == playerInventoryAdapter) {
                    recoverCashClaimInventoryBatch(selected);
                } else {
                    CustodyBatchRecovery.recover(selectedAdapter, selected,
                            selected.updatedAt(),
                            commit -> commitRecoveryCustodyBatch(selected, commit));
                }
                return 1;
            });
        } catch (RuntimeException exception) {
            custodyRecoveryFailure = exception;
            return 0;
        }
    }

    private void recoverCashClaimInventoryBatch(
            CustodyPreparedBatch selected
    ) {
        CustodyPreparedOperation operation = selected.operations().get(0);
        CustodyAdapterInspection inspection = playerInventoryAdapter.inspect(
                operation.simulationToken());
        if (inspection.status()
                == CustodyAdapterInspectionStatus.APPLIED
                && inspection.evidenceByLot().equals(
                selected.plannedEvidenceByLot())) {
            CustodyMutation mutation = CustodyMutation.terminal(
                    operation.lotSnapshot(), CustodyOperation.RELEASE,
                    operation.requestKey(),
                    inspection.evidenceByLot().get(
                            operation.lotSnapshot().lotId()),
                    selected.updatedAt());
            CustodyPreparedBatch applied = selected.markApplied(
                    selected.revision(), inspection.evidenceByLot(),
                    selected.updatedAt());
            EscrowClaim claim = requireCashClaim(selected);
            appendCashClaimDelivery(requireCoordinator(), claim,
                    CustodyBatchCommit.applied(applied,
                            List.of(mutation)));
            return;
        }
        if (inspection.status()
                == CustodyAdapterInspectionStatus.NOT_APPLIED) {
            commitRecoveryCustodyBatch(selected, CustodyBatchCommit.state(
                    selected.markNotApplied(selected.revision(),
                            selected.updatedAt(), inspection.detail())));
            return;
        }
        EscrowClaim claim = requireCashClaim(selected);
        appendCashClaimQuarantine(requireCoordinator(), claim,
                selected.updatedAt());
        String detail = inspection.status()
                == CustodyAdapterInspectionStatus.APPLIED
                ? "Player inventory receipt evidence does not match"
                : inspection.detail();
        commitRecoveryCustodyBatch(selected, CustodyBatchCommit.state(
                selected.quarantine(selected.revision(),
                        selected.updatedAt(), detail)));
    }

    private EscrowClaim requireCashClaim(CustodyPreparedBatch batch) {
        try {
            PlayerInventoryDeliveryToken token =
                    PlayerInventoryDeliveryToken.decode(
                            batch.operations().get(0).simulationToken());
            EscrowClaim claim = claims.getClaim(token.claimId());
            if (claim != null && claim.ownerId().equals(token.playerId())
                    && claim.transactionId().equals(batch.transactionId())
                    && token.batchId().equals(batch.batchId())
                    && token.lotId().equals(batch.operations().get(0)
                    .lotSnapshot().lotId())) {
                return claim;
            }
        } catch (RuntimeException ignored) {
        }
        List<EscrowClaim> matches = claims.claimsForTransaction(
                        batch.transactionId()).stream()
                .filter(claim -> CashClaimDeliveryPlanner.lotId(
                        claim.claimId()).equals(batch.operations().get(0)
                        .lotSnapshot().lotId()))
                .toList();
        if (matches.size() != 1) {
            throw new EscrowRuntimeException(
                    "Cash claim recovery cannot identify its claim");
        }
        return matches.get(0);
    }

    private int recoverOneProtectedCashDiscovery() {
        EscrowEvidenceDiscoveryQueue.StepResult result =
                EscrowEvidenceDiscoveryQueue.processOne(
                        protectedCashDiscoveryWork,
                        this::recoverProtectedCashDiscovery);
        protectedCashDiscoveryComplete = result.complete();
        result.failure().ifPresent(exception -> {
            protectedCashDiscoveryFailureCount = Math.addExact(
                    protectedCashDiscoveryFailureCount, 1);
            protectedCashDiscoveryFailure = discoveryFailure(
                    "Protected cash evidence discovery requires maintenance",
                    protectedCashDiscoveryFailureCount,
                    protectedCashDiscoveryFailure, exception);
        });
        return result.examined();
    }

    private void recoverProtectedCashDiscovery(
            ProtectedCashRedemptionIntentStore.Inspection inspection
    ) {
        if (inspection.status()
                == ProtectedCashRedemptionIntentStore.InspectionStatus.UNKNOWN
                || inspection.status()
                == ProtectedCashRedemptionIntentStore.InspectionStatus.MISSING) {
            EscrowTransaction current = inspection.transactionId()
                    .map(EscrowTransactionId::new)
                    .map(transactions::getTransaction)
                    .orElse(null);
            if (current != null && !current.state().isTerminal()) {
                recoveryScheduler.enqueue(current);
                return;
            }
            throw discoveryEntryFailure("Protected cash",
                    inspection.playerId(), inspection.transactionId(),
                    inspection.detail());
        }
        ProtectedCashRedemptionEvidence evidence = inspection.evidence()
                .orElseThrow(() -> new EscrowRuntimeException(
                        "Protected cash discovery evidence is missing"));
        ProtectedCashRedemptionReservation reservation =
                evidence.reservation();
        EscrowTransactionId transactionId = new EscrowTransactionId(
                reservation.transactionId());
        EscrowTransaction current = transactions.getTransaction(
                transactionId);
        if (current == null) {
            commitProtectedCashReservation(reservation);
            current = transactions.getTransaction(transactionId);
        }
        if (current == null) {
            throw new EscrowRuntimeException(
                    "Protected cash reservation did not materialize");
        }
        if (!current.state().isTerminal()) {
            recoveryScheduler.enqueue(current);
            return;
        }
        if (!terminalEvidenceMatches(evidence, current)) {
            throw new EscrowRuntimeException(
                    "Protected cash terminal evidence conflicts with its transaction");
        }
        scheduleProtectedCashCleanup(evidence.playerId(),
                evidence.transactionId());
    }

    private int recoverOneProtectedCashCleanup() {
        Map.Entry<UUID, ProtectedCashCleanupWork> entry =
                protectedCashCleanupWork.entrySet().iterator().next();
        ProtectedCashCleanupWork work = entry.getValue();
        EscrowTransaction current = transactions.getTransaction(
                new EscrowTransactionId(work.transactionId()));
        if (current == null || !current.state().isTerminal()) {
            protectedCashCleanupFailure = new EscrowRuntimeException(
                    "Protected cash cleanup lost its terminal transaction");
            return 1;
        }
        try {
            protectedCashIntentStore.cleanup(ownerServer, work.playerId(),
                    work.transactionId());
            protectedCashCleanupWork.remove(entry.getKey());
            protectedCashCleanupFailure = null;
        } catch (java.io.IOException | RuntimeException exception) {
            protectedCashCleanupFailure = new EscrowRuntimeException(
                    "Protected cash terminal evidence cleanup is uncertain",
                    exception);
        }
        return 1;
    }

    private int recoverOneForeignCashDiscovery() {
        EscrowEvidenceDiscoveryQueue.StepResult result =
                EscrowEvidenceDiscoveryQueue.processOne(
                        foreignCashDiscoveryWork,
                        this::recoverForeignCashDiscovery);
        foreignCashDiscoveryComplete = result.complete();
        result.failure().ifPresent(exception -> {
            foreignCashDiscoveryFailureCount = Math.addExact(
                    foreignCashDiscoveryFailureCount, 1);
            foreignCashDiscoveryFailure = discoveryFailure(
                    "Foreign cash evidence discovery requires maintenance",
                    foreignCashDiscoveryFailureCount,
                    foreignCashDiscoveryFailure, exception);
        });
        return result.examined();
    }

    private void recoverForeignCashDiscovery(
            ForeignCashDepositIntentStore.Inspection inspection
    ) {
        if (inspection.status()
                == ForeignCashDepositIntentStore.InspectionStatus.UNKNOWN
                || inspection.status()
                == ForeignCashDepositIntentStore.InspectionStatus.MISSING) {
            EscrowTransaction current = inspection.transactionId()
                    .map(EscrowTransactionId::new)
                    .map(transactions::getTransaction)
                    .orElse(null);
            if (current != null && !current.state().isTerminal()) {
                recoveryScheduler.enqueue(current);
                return;
            }
            throw discoveryEntryFailure("Foreign cash",
                    inspection.playerId(), inspection.transactionId(),
                    inspection.detail());
        }
        ForeignCashDepositEvidence evidence = inspection.evidence()
                .orElseThrow(() -> new EscrowRuntimeException(
                        "Foreign cash discovery evidence is missing"));
        ForeignCashDepositReservation reservation = evidence.reservation();
        EscrowTransactionId transactionId = new EscrowTransactionId(
                reservation.transactionId());
        EscrowTransaction current = transactions.getTransaction(
                transactionId);
        if (current == null) {
            commitForeignCashReservation(reservation);
            current = transactions.getTransaction(transactionId);
        }
        if (current == null) {
            throw new EscrowRuntimeException(
                    "Foreign cash reservation did not materialize");
        }
        if (!current.state().isTerminal()) {
            recoveryScheduler.enqueue(current);
            return;
        }
        if (!terminalEvidenceMatches(evidence, current)) {
            throw new EscrowRuntimeException(
                    "Foreign cash terminal evidence conflicts with its transaction");
        }
        scheduleForeignCashCleanup(evidence.playerId(),
                evidence.transactionId());
    }

    private int recoverOneForeignCashCleanup() {
        Map.Entry<UUID, ForeignCashCleanupWork> entry =
                foreignCashCleanupWork.entrySet().iterator().next();
        ForeignCashCleanupWork work = entry.getValue();
        EscrowTransaction current = transactions.getTransaction(
                new EscrowTransactionId(work.transactionId()));
        if (current == null || !current.state().isTerminal()) {
            foreignCashCleanupFailure = new EscrowRuntimeException(
                    "Foreign cash cleanup lost its terminal transaction");
            return 1;
        }
        try {
            foreignCashIntentStore.cleanup(ownerServer, work.playerId(),
                    work.transactionId());
            foreignCashCleanupWork.remove(entry.getKey());
            foreignCashCleanupFailure = null;
        } catch (java.io.IOException | RuntimeException exception) {
            foreignCashCleanupFailure = new EscrowRuntimeException(
                    "Foreign cash terminal evidence cleanup is uncertain",
                    exception);
        }
        return 1;
    }

    private static boolean terminalEvidenceMatches(
            ProtectedCashRedemptionEvidence evidence,
            EscrowTransaction transaction
    ) {
        return switch (evidence.phase()) {
            case INTENT -> false;
            case SETTLEMENT -> evidence.settlement().orElseThrow()
                    .completedTransaction().equals(transaction);
            case CANCELLATION -> evidence.cancellation().orElseThrow()
                    .refundedTransaction().equals(transaction);
        };
    }

    private static boolean terminalEvidenceMatches(
            ForeignCashDepositEvidence evidence,
            EscrowTransaction transaction
    ) {
        return switch (evidence.phase()) {
            case INTENT -> false;
            case SETTLEMENT -> evidence.settlement().orElseThrow()
                    .completedTransaction().equals(transaction);
            case CANCELLATION -> evidence.cancellation().orElseThrow()
                    .refundedTransaction().equals(transaction);
        };
    }

    private static EscrowRuntimeException discoveryFailure(
            String summary,
            int failureCount,
            Throwable previous,
            RuntimeException current
    ) {
        Throwable first = previous != null && previous.getCause() != null
                ? previous.getCause() : current;
        EscrowRuntimeException failure = new EscrowRuntimeException(
                summary + ". Failed entries " + failureCount, first);
        if (current != first) {
            failure.addSuppressed(current);
        }
        return failure;
    }

    private static EscrowRuntimeException discoveryEntryFailure(
            String domain,
            UUID playerId,
            Optional<UUID> transactionId,
            String detail
    ) {
        String player = playerId == null ? "unknown" : playerId.toString();
        String transaction = transactionId.map(UUID::toString)
                .orElse("unknown");
        return new EscrowRuntimeException(domain + " evidence for player "
                + player + " and transaction " + transaction
                + " requires maintenance. " + detail);
    }

    private void initializeDomainRecovery() {
        if (!domainRecoveryInitialized) {
            recoveryScheduler.register(
                    EscrowOperation.ATM_WITHDRAWAL,
                    new AtmWithdrawalRecoveryHandler(
                            this, ledger, claims, protectedMints,
                            Clock.systemUTC()));
            recoveryScheduler.register(
                    EscrowOperation.CURRENCY_DEPOSIT,
                    new ProtectedCashRedemptionRecoveryHandler(
                            ownerServer, this, protectedCashIntentStore,
                            Clock.systemUTC(),
                            new ForeignCashDepositRecoveryHandler(
                                    ownerServer, this,
                                    foreignCashIntentStore,
                                    Clock.systemUTC())));
            recoveryScheduler.register(
                    EscrowOperation.PLAYER_PAYMENT,
                    new PlayerPaymentRecoveryHandler(
                            this, ledger, claims, Clock.systemUTC()));
            protectedCashDiscoveryWork.addAll(
                    protectedCashIntentStore.discover(ownerServer));
            protectedCashDiscoveryComplete =
                    protectedCashDiscoveryWork.isEmpty();
            foreignCashDiscoveryWork.addAll(
                    foreignCashIntentStore.discover(ownerServer));
            foreignCashDiscoveryComplete =
                    foreignCashDiscoveryWork.isEmpty();
            if (serverShopIntents != null) {
                serverShopSellRecoveryWork.addAll(serverShopIntents
                        .preparedSellIntents(
                                ServerShopIntentSavedData.MAXIMUM_ENTRIES)
                        .stream().map(ServerShopSellIntent::requestId)
                        .toList());
                serverShopBarterRecoveryWork.addAll(serverShopIntents
                        .preparedBarterIntents(
                                ServerShopIntentSavedData.MAXIMUM_ENTRIES)
                        .stream().map(ServerShopBarterIntent::requestId)
                        .toList());
            }
            enumerateServerShopFundingRecovery();
            serverShopRecoveryEnumerated = true;
            domainRecoveryInitialized = true;
        }
    }

    private void enumerateServerShopFundingRecovery() {
        transactions.snapshotTransactions().values().stream()
                .sorted(java.util.Comparator.comparing(value ->
                        value.transactionId().value().toString()))
                .forEach(transaction -> EscrowCashDepositService
                        .serverShopPurchaseBinding(transaction)
                        .ifPresent(purchaseRequestId -> {
                            List<EscrowClaim> transactionClaims = claims
                                    .claimsForTransaction(transaction
                                            .transactionId().value());
                            boolean purchaseEvidence = transactions
                                    .getTransaction(
                                    new EscrowTransactionId(
                                            purchaseRequestId)) != null
                                    || ledger.wasApplied(purchaseRequestId)
                                    || !claims.claimsForTransaction(
                                    purchaseRequestId).isEmpty()
                                    || !stock.reservationsForTransaction(
                                    purchaseRequestId).isEmpty();
                            try {
                                ServerShopFundingReleaseService
                                        .startupCandidate(transaction,
                                                transactionClaims,
                                                purchaseEvidence,
                                                (playerId, funding) ->
                                                        ServerShopFundingReleaseService
                                                                .startupCompletionExplained(
                                                                        this,
                                                                        playerId,
                                                                        funding))
                                        .ifPresent(funding -> {
                                            EscrowClaim claim = claims
                                                    .getClaim(
                                                            funding
                                                                    .claimId());
                                            serverShopFundingRecoveryWork
                                                    .addLast(
                                                            new ServerShopFundingRecoveryWork(
                                                                    claim.ownerId(),
                                                                    funding));
                                        });
                            } catch (RuntimeException exception) {
                                serverShopFundingRecoveryFailure =
                                        new EscrowRuntimeException(
                                                "Bound server shop funding discovery failed",
                                                exception);
                            }
                        }));
    }

    private int recoverOneServerShopFunding() {
        ServerShopFundingRecoveryWork work =
                serverShopFundingRecoveryWork.removeFirst();
        ServerShopFundingReleaseService.Result result =
                ServerShopFundingReleaseService.release(this,
                        work.playerId(), work.funding());
        if (result.status()
                != ServerShopFundingReleaseService.Status.RELEASED
                && result.status()
                != ServerShopFundingReleaseService.Status
                .PURCHASE_COMMITTED) {
            serverShopFundingRecoveryFailure =
                    new EscrowRuntimeException(
                            "Server shop funding startup release requires maintenance");
        }
        return 1;
    }

    private record ServerShopFundingRecoveryWork(
            UUID playerId,
            ServerShopPurchaseCommit.PhysicalFunding funding
    ) {
        private ServerShopFundingRecoveryWork {
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(funding, "funding");
        }
    }

    private record ProtectedCashCleanupWork(
            UUID playerId,
            UUID transactionId
    ) {
        private ProtectedCashCleanupWork {
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(transactionId, "transactionId");
        }
    }

    private record ForeignCashCleanupWork(
            UUID playerId,
            UUID transactionId
    ) {
        private ForeignCashCleanupWork {
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(transactionId, "transactionId");
        }
    }

    private void commitScopedCustodyBatch(CustodyBatchCommit commit) {
        assertServerThread();
        CustodyExecutionScope scope = custodyExecutionScope;
        if (scope == null) {
            throw new EscrowRuntimeException("Custody batch execution scope is missing");
        }
        scope.validate(commit);
        EscrowRuntimeCoordinator available = scope.phase == 0
                ? requireReadyCoordinator() : requireCoordinator();
        appendCustodyBatch(available, commit);
        scope.accept(commit);
    }

    private void commitCashClaimCustodyBatch(
            EscrowClaim claim,
            CustodyBatchCommit commit
    ) {
        assertServerThread();
        Objects.requireNonNull(claim, "claim");
        CustodyExecutionScope scope = custodyExecutionScope;
        if (scope == null) {
            throw new EscrowRuntimeException(
                    "Cash claim custody execution scope is missing");
        }
        scope.validate(commit);
        EscrowRuntimeCoordinator available = scope.phase == 0
                ? requireReadyCoordinator() : requireCoordinator();
        if (commit.batch().status() == CustodyBatchStatus.APPLIED) {
            appendCashClaimDelivery(available, claim, commit);
            try {
                playerInventoryAdapter.complete(
                        commit.batch().operations().get(0)
                                .simulationToken());
            } catch (RuntimeException ignored) {
            }
        } else {
            if (commit.batch().status()
                    == CustodyBatchStatus.QUARANTINED) {
                appendCashClaimQuarantine(available, claim,
                        commit.batch().updatedAt());
            }
            appendCustodyBatch(available, commit);
        }
        scope.accept(commit);
    }

    private void appendCashClaimDelivery(
            EscrowRuntimeCoordinator available,
            EscrowClaim claim,
            CustodyBatchCommit custodyCommit
    ) {
        if (!available.isReady()) {
            throw new EscrowRuntimeException(
                    "Cash claim delivery journal is unavailable in state "
                            + available.state(),
                    available.failure().orElse(null));
        }
        ClaimDeliveryCommit delivery = new ClaimDeliveryCommit(
                claim.ownerId(), claim.claimId(),
                custodyCommit.batch().operations().get(0).requestKey(),
                claim.originalUnits(), custodyCommit.batch().updatedAt());
        CashClaimDeliveryCommit commit = new CashClaimDeliveryCommit(
                delivery, custodyCommit);
        available.commit(claim.transactionId(), new EscrowJournalEvent(
                EscrowJournalEventType.CASH_CLAIM_DELIVERY_COMMIT,
                CashClaimDeliveryCommitCodec.encode(commit)));
    }

    private void appendCashClaimQuarantine(
            EscrowRuntimeCoordinator available,
            EscrowClaim claim,
            Instant quarantinedAt
    ) {
        if (!available.isReady()) {
            throw new EscrowRuntimeException(
                    "Cash claim quarantine journal is unavailable in state "
                            + available.state(),
                    available.failure().orElse(null));
        }
        ClaimQuarantineCommit quarantine = ClaimQuarantineCommit.create(
                claim.ownerId(), claim.claimId(), claim.transactionId(),
                quarantinedAt, "cash_claim_inventory_delivery_unknown");
        available.commit(claim.transactionId(), new EscrowJournalEvent(
                EscrowJournalEventType.CLAIM_QUARANTINE,
                ClaimJournalCodec.encodeQuarantine(quarantine)));
    }

    private void commitRecoveryCustodyBatch(CustodyPreparedBatch current,
                                            CustodyBatchCommit commit) {
        assertServerThread();
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(commit, "commit");
        if (recoveryDepth.get() <= 0 || !samePreparation(current, commit.batch())
                || commit.batch().revision() != Math.addExact(current.revision(), 1L)) {
            throw new EscrowRuntimeException("Custody recovery batch scope does not match");
        }
        boolean valid = (current.status() == CustodyBatchStatus.PREPARED
                && commit.batch().status() == CustodyBatchStatus.NOT_APPLIED)
                || (current.status() == CustodyBatchStatus.APPLYING
                && (commit.batch().status() == CustodyBatchStatus.APPLIED
                || commit.batch().status() == CustodyBatchStatus.NOT_APPLIED
                || commit.batch().status() == CustodyBatchStatus.QUARANTINED));
        if (!valid) {
            throw new EscrowRuntimeException("Custody recovery batch outcome is invalid");
        }
        appendCustodyBatch(requireCoordinator(), commit);
    }

    private void appendCustodyBatch(EscrowRuntimeCoordinator available,
                                    CustodyBatchCommit commit) {
        if (!available.isReady()) {
            throw new EscrowRuntimeException(
                    "Custody journal is unavailable in state " + available.state(),
                    available.failure().orElse(null));
        }
        EscrowJournalEvent event = new EscrowJournalEvent(
                EscrowJournalEventType.CUSTODY_BATCH,
                CustodyBatchCommitCodec.encode(commit));
        available.commit(commit.batch().transactionId(), event);
    }

    private <T> T withRecoveryLane(Supplier<T> action) {
        assertServerThread();
        int depth = recoveryDepth.get();
        recoveryDepth.set(Math.addExact(depth, 1));
        try {
            return action.get();
        } finally {
            if (depth == 0) {
                recoveryDepth.remove();
            } else {
                recoveryDepth.set(depth);
            }
        }
    }

    private void assertServerThread() {
        if (!ownerServer.isSameThread()) {
            throw new EscrowRuntimeException("Escrow mutation must run on the owning server thread");
        }
    }

    private static boolean samePreparation(CustodyPreparedBatch first,
                                           CustodyPreparedBatch second) {
        return first.batchId().equals(second.batchId())
                && first.transactionId().equals(second.transactionId())
                && first.requestKey().equals(second.requestKey())
                && first.operations().equals(second.operations())
                && first.preparedAt().equals(second.preparedAt());
    }

    public record RecoveryInspection(
            UUID transactionId,
            String requestKey,
            EscrowOperation operation,
            EscrowState state,
            long revision,
            long configRevision,
            List<String> participants,
            String provider,
            String evidence,
            long amountMinorUnits,
            long assetQuantity,
            int claimCount,
            long pendingClaimUnits,
            String lastErrorCode,
            String lastErrorMessage,
            int recoveryAttempts,
            int maximumRecoveryAttempts,
            String nextAttemptAt,
            String resumeState,
            String safeAction
    ) {
        public RecoveryInspection {
            Objects.requireNonNull(transactionId, "transactionId");
            requestKey = Objects.requireNonNull(requestKey, "requestKey");
            Objects.requireNonNull(operation, "operation");
            Objects.requireNonNull(state, "state");
            participants = List.copyOf(Objects.requireNonNull(
                    participants, "participants"));
            provider = Objects.requireNonNull(provider, "provider");
            evidence = Objects.requireNonNull(evidence, "evidence");
            lastErrorCode = Objects.requireNonNull(
                    lastErrorCode, "lastErrorCode");
            lastErrorMessage = Objects.requireNonNull(
                    lastErrorMessage, "lastErrorMessage");
            nextAttemptAt = Objects.requireNonNull(
                    nextAttemptAt, "nextAttemptAt");
            resumeState = Objects.requireNonNull(
                    resumeState, "resumeState");
            safeAction = Objects.requireNonNull(safeAction, "safeAction");
            if (revision < 0L || configRevision < 0L
                    || amountMinorUnits < 0L || assetQuantity < 0L
                    || claimCount < 0 || pendingClaimUnits < 0L
                    || recoveryAttempts < 0
                    || maximumRecoveryAttempts < 0
                    || recoveryAttempts > maximumRecoveryAttempts) {
                throw new IllegalArgumentException(
                        "Recovery inspection is invalid");
            }
        }
    }

    private static final class CustodyExecutionScope {
        private final CustodyBatchPlan plan;
        private final Map<java.util.UUID, CustodyTransferEvidence> plannedEvidence;
        private CustodyPreparedBatch prepared;
        private int phase;

        private CustodyExecutionScope(
                CustodyBatchPlan plan,
                Map<java.util.UUID, CustodyTransferEvidence> plannedEvidence
        ) {
            this.plan = plan;
            this.plannedEvidence = Map.copyOf(plannedEvidence);
        }

        private void validate(CustodyBatchCommit commit) {
            Objects.requireNonNull(commit, "commit");
            CustodyPreparedBatch batch = commit.batch();
            if (phase == 0) {
                if (batch.status() != CustodyBatchStatus.PREPARED
                        || batch.revision() != 0L
                        || !batch.plan().equals(plan)
                        || !batch.plannedEvidenceByLot().equals(plannedEvidence)) {
                    throw new EscrowRuntimeException("Initial custody batch scope does not match");
                }
                return;
            }
            if (!samePreparation(prepared, batch)) {
                throw new EscrowRuntimeException("Custody batch scope preparation changed");
            }
            if (phase == 1 && (batch.status() != CustodyBatchStatus.APPLYING
                    || batch.revision() != 1L || !commit.mutations().isEmpty())) {
                throw new EscrowRuntimeException("Custody batch did not enter applying state");
            }
            if (phase == 2 && ((batch.status() != CustodyBatchStatus.APPLIED
                    && batch.status() != CustodyBatchStatus.NOT_APPLIED
                    && batch.status() != CustodyBatchStatus.QUARANTINED)
                    || batch.revision() != 2L)) {
                throw new EscrowRuntimeException("Custody batch outcome does not match its scope");
            }
            if (phase > 2) {
                throw new EscrowRuntimeException("Custody batch scope is already complete");
            }
        }

        private void accept(CustodyBatchCommit commit) {
            if (phase == 0) {
                prepared = commit.batch();
            }
            phase = Math.addExact(phase, 1);
        }
    }

}

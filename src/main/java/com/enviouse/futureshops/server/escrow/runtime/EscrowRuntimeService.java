package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.checkpoint.ActiveEscrowJournal;
import com.enviouse.futureshops.server.escrow.admin.EscrowAdministrativeAuditSavedData;
import com.enviouse.futureshops.server.escrow.admin.EscrowAdministrativeRecord;
import com.enviouse.futureshops.server.escrow.admin.MaintenanceRepairCommand;
import com.enviouse.futureshops.server.escrow.admin.MaintenanceRepairTarget;
import com.enviouse.futureshops.server.escrow.admin.MaintenanceRepairTargetType;
import com.enviouse.futureshops.server.escrow.admin.MaintenanceStateFingerprint;
import com.enviouse.futureshops.server.escrow.audit.EscrowConservationReport;
import com.enviouse.futureshops.server.escrow.audit.EscrowCrossDomainConservationAudit;
import com.enviouse.futureshops.server.escrow.checkpoint.EscrowSavedDataCheckpointBundle;
import com.enviouse.futureshops.server.escrow.claim.ClaimSavedData;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.custody.CustodyAdapter;
import com.enviouse.futureshops.server.escrow.custody.CustodyBatchCommit;
import com.enviouse.futureshops.server.escrow.custody.CustodyBatchCommitCodec;
import com.enviouse.futureshops.server.escrow.custody.CustodyBatchExecutionResult;
import com.enviouse.futureshops.server.escrow.custody.CustodyBatchExecutor;
import com.enviouse.futureshops.server.escrow.custody.CustodyBatchPlan;
import com.enviouse.futureshops.server.escrow.custody.CustodyBatchRecovery;
import com.enviouse.futureshops.server.escrow.custody.CustodyPreparedBatch;
import com.enviouse.futureshops.server.escrow.custody.CustodyBatchStatus;
import com.enviouse.futureshops.server.escrow.custody.CustodySavedData;
import com.enviouse.futureshops.server.escrow.custody.CustodyTransferEvidence;
import com.enviouse.futureshops.server.escrow.ledger.LedgerSavedData;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;
import com.enviouse.futureshops.server.escrow.model.EscrowOperation;
import com.enviouse.futureshops.server.escrow.model.EscrowState;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;
import com.enviouse.futureshops.server.escrow.model.EscrowTransactionId;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintEventCodec;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintJournalEvent;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintSavedData;
import com.enviouse.futureshops.server.escrow.store.EscrowTransactionByteCodec;
import com.enviouse.futureshops.server.escrow.store.EscrowTransactionSavedData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.HashMap;
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
    private final CustodySavedData custody;
    private final ProtectedMintSavedData protectedMints;
    private final EscrowRecoveryScheduler recoveryScheduler;
    private final EscrowSavedDataCheckpointBundle checkpointBundle;
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
    private Throwable conservationFailure;
    private boolean conservationAuditComplete;

    private EscrowRuntimeService(MinecraftServer ownerServer,
                                 EscrowRuntimeCoordinator coordinator,
                                 EscrowSavedDataMutationApplier applier,
                                 EscrowTransactionSavedData transactions,
                                 LedgerSavedData ledger,
                                 ClaimSavedData claims,
                                 CustodySavedData custody,
                                 ProtectedMintSavedData protectedMints,
                                 EscrowRecoveryScheduler recoveryScheduler,
                                 EscrowSavedDataCheckpointBundle checkpointBundle,
                                 EscrowRuntimeMaintenanceController maintenanceController,
                                 EscrowRuntimeState unavailableState, Throwable startupFailure) {
        this.ownerServer = Objects.requireNonNull(ownerServer, "ownerServer");
        this.coordinator = coordinator;
        this.applier = applier;
        this.transactions = transactions;
        this.ledger = ledger;
        this.claims = claims;
        this.custody = custody;
        this.protectedMints = protectedMints;
        this.recoveryScheduler = recoveryScheduler;
        this.checkpointBundle = checkpointBundle;
        this.maintenanceController = maintenanceController;
        this.unavailableState = unavailableState;
        this.startupFailure = startupFailure;
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
            transactions.bindManagedMutationPermit(mutationPermit);
            ledger.bindManagedMutationPermit(mutationPermit);
            claims.bindManagedMutationPermit(mutationPermit);
            administrativeAudit.bindManagedMutationPermit(mutationPermit);
            custody.bindManagedMutationPermit(mutationPermit);
            protectedMints.bindManagedMutationPermit(mutationPermit);
            EscrowRuntimeMaintenanceController maintenanceController =
                    new EscrowRuntimeMaintenanceController(cursor, mutationPermit);
            EscrowSavedDataMutationApplier applier = new EscrowSavedDataMutationApplier(
                    transactions, ledger, claims, administrativeAudit, custody, protectedMints,
                    maintenanceController, AtmWithdrawalApplyFaultInjector.NONE,
                    mutationPermit);
            EscrowRecoveryScheduler recoveryScheduler = new EscrowRecoveryScheduler(transactions);
            EscrowSavedDataCheckpointBundle checkpointBundle =
                    new EscrowSavedDataCheckpointBundle(
                            transactions, ledger, claims, administrativeAudit, custody,
                            protectedMints, cursor, server::isSameThread, mutationPermit);
            openedCoordinator = new EscrowRuntimeCoordinator(
                    journalPath(server), cursor, applier,
                    () -> transactions.hasMaterializedState()
                            || ledger.hasMaterializedState()
                            || claims.hasMaterializedState()
                            || administrativeAudit.hasMaterializedState()
                            || custody.hasMaterializedState()
                            || protectedMints.hasMaterializedState(),
                    checkpointBundle, mutationPermit);
            openedCoordinator.start(initialRecoveryBatchSize);
            EscrowRuntimeService service = new EscrowRuntimeService(
                    server, openedCoordinator, applier, transactions, ledger, claims, custody,
                    protectedMints, recoveryScheduler, checkpointBundle,
                    maintenanceController, null, null);
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
                    server, null, null, null, null, null, null, null, null, null, null,
                    EscrowRuntimeState.MAINTENANCE, exception);
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
        if (coordinator == null) {
            return unavailableState;
        }
        EscrowRuntimeState journalState = coordinator.state();
        if (journalState == EscrowRuntimeState.READY) {
            if (maintenanceController != null
                    && maintenanceController.maintenanceRequested()) {
                return EscrowRuntimeState.MAINTENANCE;
            }
            if (!domainRecoveryInitialized || !recoveryScheduler.enumerationComplete()
                    || recoveryScheduler.hasRunnableWork()
                    || hasResolvableCustodyRecovery()) {
                return EscrowRuntimeState.RECOVERING;
            }
            if (recoveryScheduler.hasBlockingWork()
                    || hasBlockedCustodyRecovery()) {
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
        if (coordinator == null) {
            return Optional.ofNullable(startupFailure);
        }
        Optional<Throwable> journalFailure = coordinator.failure();
        if (journalFailure.isPresent()) {
            return journalFailure;
        }
        if (custodyRecoveryFailure != null) {
            return Optional.of(custodyRecoveryFailure);
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
        if (available.state() == EscrowRuntimeState.RECOVERING) {
            int journalWork = available.recoverBatch(remaining);
            worked += journalWork;
            remaining -= journalWork;
        }
        if (available.state() != EscrowRuntimeState.READY) {
            return worked;
        }
        initializeDomainRecovery();
        if (remaining > 0 && !recoveryScheduler.enumerationComplete()) {
            int enumerationBudget = recoveryScheduler.hasRunnableWork()
                    ? Math.max(1, remaining / 2) : remaining;
            int enumerated = recoveryScheduler.enumerateBatch(enumerationBudget);
            worked += enumerated;
            remaining -= enumerated;
        }
        if (maintenanceController != null
                && maintenanceController.maintenanceRequested()) {
            return worked;
        }
        if (remaining > 0 && recoveryScheduler.hasRunnableWork()) {
            int processingBudget = remaining;
            EscrowRecoveryBatchResult result = withRecoveryLane(
                    () -> recoveryScheduler.processBatch(processingBudget));
            worked += result.examined();
            remaining -= result.examined();
        }
        if (remaining > 0 && hasResolvableCustodyRecovery()) {
            worked += recoverOneCustodyOperation();
        }
        if (worked > 0) {
            invalidateConservationAudit();
        }
        return worked;
    }

    public synchronized boolean shouldRunRecovery() {
        if (coordinator == null) {
            return false;
        }
        if (coordinator.state() == EscrowRuntimeState.RECOVERING) {
            return true;
        }
        if (coordinator.state() != EscrowRuntimeState.READY) {
            return false;
        }
        if (maintenanceController != null
                && maintenanceController.maintenanceRequested()) {
            return !domainRecoveryInitialized
                    || !recoveryScheduler.enumerationComplete();
        }
        return !domainRecoveryInitialized
                || !recoveryScheduler.enumerationComplete()
                || recoveryScheduler.hasRunnableWork()
                || hasResolvableCustodyRecovery();
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

    synchronized EscrowCommitResult settleMoneyClaim(MoneyClaimSettlement settlement) {
        assertServerThread();
        Objects.requireNonNull(settlement, "settlement");
        EscrowJournalEvent event = new EscrowJournalEvent(
                EscrowJournalEventType.MONEY_CLAIM_SETTLEMENT,
                MoneyClaimSettlementCodec.encode(settlement));
        EscrowCommitResult result = requireReadyCoordinator().commit(
                settlement.ledgerTransaction().transactionId(), event);
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
        if (!available.isReady()) {
            throw new EscrowRuntimeException(
                    "Administrative audit journal is unavailable in state " + available.state(),
                    available.failure().orElse(null));
        }
        EscrowCommitResult result = available.commit(audit.requestId(), event);
        invalidateConservationAudit();
        return result;
    }

    synchronized EscrowCommitResult commitMaintenanceRepair(
            MaintenanceRepairCommand command
    ) {
        assertServerThread();
        Objects.requireNonNull(command, "command");
        EscrowRuntimeCoordinator available = requireCoordinator();
        if (!available.journalHealthyAndAligned()) {
            throw new EscrowRuntimeException(
                    "Maintenance repair journal is unavailable in state "
                            + available.state(), available.failure().orElse(null));
        }
        EscrowJournalEvent event = applier.planMaintenanceRepair(command);
        MaintenanceRepairCommand plannedCommand = MaintenanceRepairJournalCodec.decode(
                event.body()).command();
        EscrowCommitResult result = available.commitMaintenanceRepair(
                command.commandId(), event);
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
        EscrowConservationReport report = verifyConservation();
        conservationReport = report;
        conservationFailure = report.conserved() ? null : new EscrowRuntimeException(
                "Escrow cross domain conservation failed");
        conservationAuditComplete = true;
        return report;
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
        if (!isReady() && !(recoveryDepth.get() > 0 && available.isReady())) {
            throw new EscrowRuntimeException(
                    "Escrow runtime is not ready and is in state " + state(),
                    failure().orElse(null));
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
                || hasBlockedCustodyRecovery()
                || custodyRecoveryFailure != null
                || conservationFailure != null);
    }

    private boolean maintenanceRecoveryClear() {
        return domainRecoveryInitialized
                && recoveryScheduler.enumerationComplete()
                && !recoveryScheduler.hasRunnableWork()
                && !recoveryScheduler.hasBlockingWork()
                && !recoveryScheduler.hasScheduledWork()
                && !recoveryScheduler.hasManualReviewWork()
                && !hasUnresolvedCustodyRecovery()
                && custodyRecoveryFailure == null;
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
            return conservationReport != null && conservationReport.conserved();
        }
        return maintenanceConservationVerified();
    }

    private EscrowConservationReport verifyConservation() {
        if (ledger == null || claims == null || custody == null || protectedMints == null) {
            throw new EscrowRuntimeException(
                    "Escrow cross domain conservation is unavailable", startupFailure);
        }
        return EscrowCrossDomainConservationAudit.verify(
                ledger, claims, custody, protectedMints);
    }

    private void invalidateConservationAudit() {
        conservationAuditComplete = false;
        conservationReport = null;
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
                            selected.markNotApplied(selected.revision(), Instant.now(),
                                    "Prepared batch did not reach the custody adapter")));
                } else {
                    CustodyBatchRecovery.recover(selectedAdapter, selected, Instant.now(),
                            commit -> commitRecoveryCustodyBatch(selected, commit));
                }
                return 1;
            });
        } catch (RuntimeException exception) {
            custodyRecoveryFailure = exception;
            return 0;
        }
    }

    private void initializeDomainRecovery() {
        if (!domainRecoveryInitialized) {
            domainRecoveryInitialized = true;
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

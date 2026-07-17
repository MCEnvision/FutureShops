package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.admin.EscrowAdministrativeAuditSavedData;
import com.enviouse.futureshops.server.escrow.admin.EscrowAdministrativeRecord;
import com.enviouse.futureshops.server.escrow.admin.MaintenanceRepairCommand;
import com.enviouse.futureshops.server.escrow.admin.MaintenanceRepairTarget;
import com.enviouse.futureshops.server.escrow.admin.MaintenanceStateFingerprint;
import com.enviouse.futureshops.server.escrow.claim.ClaimAttemptResult;
import com.enviouse.futureshops.server.escrow.claim.ClaimKind;
import com.enviouse.futureshops.server.escrow.claim.ClaimSavedData;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.custody.CustodyBatchCommit;
import com.enviouse.futureshops.server.escrow.custody.CustodyBatchCommitCodec;
import com.enviouse.futureshops.server.escrow.custody.CustodyMutation;
import com.enviouse.futureshops.server.escrow.custody.CustodyMutationCodec;
import com.enviouse.futureshops.server.escrow.custody.CustodyPreparedOperation;
import com.enviouse.futureshops.server.escrow.custody.CustodyPreparedOperationCodec;
import com.enviouse.futureshops.server.escrow.custody.CustodySavedData;
import com.enviouse.futureshops.server.escrow.journal.JournalRecord;
import com.enviouse.futureshops.server.escrow.ledger.LedgerSavedData;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintEventCodec;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintJournalEvent;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintSavedData;
import com.enviouse.futureshops.server.escrow.store.EscrowTransactionByteCodec;
import com.enviouse.futureshops.server.escrow.store.EscrowTransactionSavedData;
import com.enviouse.futureshops.server.escrow.model.EscrowState;
import com.enviouse.futureshops.server.escrow.model.EscrowTransactionId;

import java.util.Objects;
import java.util.UUID;

public final class EscrowSavedDataMutationApplier implements EscrowMutationApplier {
    private final EscrowTransactionSavedData transactions;
    private final LedgerSavedData ledger;
    private final ClaimSavedData claims;
    private final EscrowAdministrativeAuditSavedData administrativeAudit;
    private final CustodySavedData custody;
    private final ProtectedMintSavedData protectedMints;
    private final MaintenanceRepairProcessor maintenanceRepairs;
    private final AtmWithdrawalApplyFaultInjector atmWithdrawalFaults;
    private final EscrowMutationPermit mutationPermit;

    public EscrowSavedDataMutationApplier(EscrowTransactionSavedData transactions,
                                          LedgerSavedData ledger, ClaimSavedData claims) {
        this(transactions, ledger, claims, new EscrowAdministrativeAuditSavedData(),
                new CustodySavedData(), new ProtectedMintSavedData());
    }

    public EscrowSavedDataMutationApplier(EscrowTransactionSavedData transactions,
                                          LedgerSavedData ledger, ClaimSavedData claims,
                                          EscrowAdministrativeAuditSavedData administrativeAudit) {
        this(transactions, ledger, claims, administrativeAudit, new CustodySavedData(),
                new ProtectedMintSavedData());
    }

    public EscrowSavedDataMutationApplier(EscrowTransactionSavedData transactions,
                                          LedgerSavedData ledger, ClaimSavedData claims,
                                          EscrowAdministrativeAuditSavedData administrativeAudit,
                                          CustodySavedData custody) {
        this(transactions, ledger, claims, administrativeAudit, custody,
                new ProtectedMintSavedData());
    }

    public EscrowSavedDataMutationApplier(EscrowTransactionSavedData transactions,
                                          LedgerSavedData ledger, ClaimSavedData claims,
                                          EscrowAdministrativeAuditSavedData administrativeAudit,
                                          CustodySavedData custody,
                                          ProtectedMintSavedData protectedMints) {
        this(transactions, ledger, claims, administrativeAudit, custody, protectedMints,
                MaintenanceRuntimeMutationHandler.unavailable());
    }

    public EscrowSavedDataMutationApplier(EscrowTransactionSavedData transactions,
                                          LedgerSavedData ledger, ClaimSavedData claims,
                                          EscrowAdministrativeAuditSavedData administrativeAudit,
                                          CustodySavedData custody,
                                          ProtectedMintSavedData protectedMints,
                                          MaintenanceRuntimeMutationHandler runtimeHandler) {
        this(transactions, ledger, claims, administrativeAudit, custody, protectedMints,
                runtimeHandler, AtmWithdrawalApplyFaultInjector.NONE, null);
    }

    EscrowSavedDataMutationApplier(EscrowTransactionSavedData transactions,
                                   LedgerSavedData ledger, ClaimSavedData claims,
                                   EscrowAdministrativeAuditSavedData administrativeAudit,
                                   CustodySavedData custody,
                                   ProtectedMintSavedData protectedMints,
                                   MaintenanceRuntimeMutationHandler runtimeHandler,
                                   AtmWithdrawalApplyFaultInjector atmWithdrawalFaults) {
        this(transactions, ledger, claims, administrativeAudit, custody, protectedMints,
                runtimeHandler, atmWithdrawalFaults, null);
    }

    EscrowSavedDataMutationApplier(EscrowTransactionSavedData transactions,
                                   LedgerSavedData ledger, ClaimSavedData claims,
                                   EscrowAdministrativeAuditSavedData administrativeAudit,
                                   CustodySavedData custody,
                                   ProtectedMintSavedData protectedMints,
                                   MaintenanceRuntimeMutationHandler runtimeHandler,
                                   AtmWithdrawalApplyFaultInjector atmWithdrawalFaults,
                                   EscrowMutationPermit mutationPermit) {
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.claims = Objects.requireNonNull(claims, "claims");
        this.administrativeAudit = Objects.requireNonNull(administrativeAudit, "administrativeAudit");
        this.custody = Objects.requireNonNull(custody, "custody");
        this.protectedMints = Objects.requireNonNull(protectedMints, "protectedMints");
        this.maintenanceRepairs = new MaintenanceRepairProcessor(transactions, claims,
                administrativeAudit, custody, runtimeHandler);
        this.atmWithdrawalFaults = Objects.requireNonNull(
                atmWithdrawalFaults, "atmWithdrawalFaults");
        this.mutationPermit = mutationPermit;
    }

    public synchronized EscrowJournalEvent planMaintenanceRepair(
            MaintenanceRepairCommand command
    ) {
        return maintenanceRepairs.planEvent(command);
    }

    public synchronized MaintenanceStateFingerprint maintenanceFingerprint(
            MaintenanceRepairTarget target
    ) {
        return maintenanceRepairs.currentFingerprint(target);
    }

    public synchronized long maintenanceRevision(MaintenanceRepairTarget target) {
        return maintenanceRepairs.currentRevision(target);
    }

    @Override
    public synchronized EscrowPreflightResult preflight(UUID transactionId,
                                                        EscrowJournalEvent event) {
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(event, "event");
        return switch (event.type()) {
            case TRANSACTION_UPSERT -> preflightTransaction(transactionId, event.body());
            case LEDGER_APPLY -> preflightLedger(transactionId, event.body());
            case CLAIM_CREATE -> preflightClaimCreate(transactionId, event.body());
            case CLAIM_DELIVERY -> preflightClaimDelivery(transactionId, event.body());
            case MONEY_CLAIM_SETTLEMENT -> preflightMoneyClaimSettlement(
                    transactionId, event.body());
            case ADMIN_AUDIT -> preflightAdministrativeAudit(transactionId, event.body());
            case CUSTODY_PREPARE -> preflightCustodyPrepare(transactionId, event.body());
            case CUSTODY_MUTATION -> preflightCustodyMutation(transactionId, event.body());
            case CUSTODY_BATCH -> preflightCustodyBatch(transactionId, event.body());
            case PROTECTED_MINT -> preflightProtectedMint(transactionId, event.body());
            case CLAIM_QUARANTINE -> preflightClaimQuarantine(transactionId, event.body());
            case MAINTENANCE_REPAIR -> preflightMaintenanceRepair(
                    transactionId, event.body());
            case ATM_WITHDRAWAL_COMMIT -> preflightAtmWithdrawal(
                    transactionId, event.body());
            case JOURNAL_LINEAGE -> throw new EscrowRuntimeException(
                    "Journal lineage cannot be preflighted as a mutation");
        };
    }

    @Override
    public synchronized void apply(JournalRecord record, EscrowJournalEvent event) {
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(event, "event");
        if (mutationPermit == null) {
            applyAuthorized(record, event);
            return;
        }
        try (EscrowMutationPermit.Scope ignored = mutationPermit.activate()) {
            applyAuthorized(record, event);
        }
    }

    private void applyAuthorized(JournalRecord record, EscrowJournalEvent event) {
        switch (event.type()) {
            case TRANSACTION_UPSERT -> applyTransaction(record, event.body());
            case LEDGER_APPLY -> applyLedger(record, event.body());
            case CLAIM_CREATE -> applyClaimCreate(record, event.body());
            case CLAIM_DELIVERY -> applyClaimDelivery(record, event.body());
            case MONEY_CLAIM_SETTLEMENT -> applyMoneyClaimSettlement(record, event.body());
            case ADMIN_AUDIT -> applyAdministrativeAudit(record, event.body());
            case CUSTODY_PREPARE -> applyCustodyPrepare(record, event.body());
            case CUSTODY_MUTATION -> applyCustodyMutation(record, event.body());
            case CUSTODY_BATCH -> applyCustodyBatch(record, event.body());
            case PROTECTED_MINT -> applyProtectedMint(record, event.body());
            case CLAIM_QUARANTINE -> applyClaimQuarantine(record, event.body());
            case MAINTENANCE_REPAIR -> applyMaintenanceRepair(record, event.body());
            case ATM_WITHDRAWAL_COMMIT -> applyAtmWithdrawal(record, event.body());
            case JOURNAL_LINEAGE -> throw new EscrowRuntimeException(
                    "Journal lineage cannot be applied as a mutation");
        }
    }

    private void applyTransaction(JournalRecord record, byte[] body) {
        EscrowTransaction transaction = EscrowTransactionByteCodec.decode(body);
        requireRecordIdentity(record, transaction.transactionId().value());
        transactions.applyCommitted(transaction);
    }

    private EscrowPreflightResult preflightTransaction(UUID recordTransactionId, byte[] body) {
        EscrowTransaction transaction = EscrowTransactionByteCodec.decode(body);
        requireRecordIdentity(recordTransactionId, transaction.transactionId().value());
        return result(transactions.preflightCommitted(transaction).replayed());
    }

    private void applyLedger(JournalRecord record, byte[] body) {
        LedgerTransaction transaction = LedgerJournalCodec.decode(body);
        requireRecordIdentity(record, transaction.transactionId());
        ledger.applyCommitted(transaction);
    }

    private EscrowPreflightResult preflightLedger(UUID recordTransactionId, byte[] body) {
        LedgerTransaction transaction = LedgerJournalCodec.decode(body);
        requireRecordIdentity(recordTransactionId, transaction.transactionId());
        return result(ledger.preflightCommitted(transaction).replayed());
    }

    private void applyClaimCreate(JournalRecord record, byte[] body) {
        EscrowClaim claim = ClaimJournalCodec.decodeClaim(body);
        requireRecordIdentity(record, claim.transactionId());
        claims.createCommitted(claim);
    }

    private EscrowPreflightResult preflightClaimCreate(UUID recordTransactionId, byte[] body) {
        EscrowClaim claim = ClaimJournalCodec.decodeClaim(body);
        requireRecordIdentity(recordTransactionId, claim.transactionId());
        boolean replayed = claims.getClaim(claim.claimId()) != null;
        claims.preflightCreateCommitted(claim);
        return result(replayed);
    }

    private void applyClaimDelivery(JournalRecord record, byte[] body) {
        ClaimDeliveryCommit delivery = ClaimJournalCodec.decodeDelivery(body);
        EscrowClaim claim = requireClaim(delivery);
        requireRecordIdentity(record, claim.transactionId());
        ClaimAttemptResult result = claims.deliverCommitted(
                delivery.ownerId(), delivery.claimId(), delivery.requestKey(), delivery.units(),
                delivery.deliveredAt());
        requireDeliveredUnits(result, delivery.units());
    }

    private EscrowPreflightResult preflightClaimDelivery(UUID recordTransactionId, byte[] body) {
        ClaimDeliveryCommit delivery = ClaimJournalCodec.decodeDelivery(body);
        EscrowClaim claim = requireClaim(delivery);
        requireRecordIdentity(recordTransactionId, claim.transactionId());
        ClaimAttemptResult result = claims.preflightDeliveryCommitted(
                delivery.ownerId(), delivery.claimId(), delivery.requestKey(), delivery.units(),
                delivery.deliveredAt());
        requireDeliveredUnits(result, delivery.units());
        return result(result.replayed());
    }

    private void applyClaimQuarantine(JournalRecord record, byte[] body) {
        ClaimQuarantineCommit quarantine = ClaimJournalCodec.decodeQuarantine(body);
        EscrowClaim claim = requireClaim(quarantine.ownerId(), quarantine.claimId());
        requireRecordIdentity(record, quarantine.transactionId());
        requireRecordIdentity(quarantine.transactionId(), claim.transactionId());
        claims.quarantineCommitted(
                quarantine.ownerId(), quarantine.claimId(), quarantine.quarantinedAt());
    }

    private EscrowPreflightResult preflightClaimQuarantine(UUID recordTransactionId, byte[] body) {
        ClaimQuarantineCommit quarantine = ClaimJournalCodec.decodeQuarantine(body);
        EscrowClaim claim = requireClaim(quarantine.ownerId(), quarantine.claimId());
        requireRecordIdentity(recordTransactionId, quarantine.transactionId());
        requireRecordIdentity(quarantine.transactionId(), claim.transactionId());
        boolean replayed = claim.status() == com.enviouse.futureshops.server.escrow.claim.ClaimStatus.QUARANTINED;
        claims.preflightQuarantineCommitted(
                quarantine.ownerId(), quarantine.claimId(), quarantine.quarantinedAt());
        return result(replayed);
    }

    private void applyMoneyClaimSettlement(JournalRecord record, byte[] body) {
        MoneyClaimSettlement settlement = MoneyClaimSettlementCodec.decode(body);
        ClaimDeliveryCommit delivery = settlement.delivery();
        EscrowClaim claim = requireClaim(delivery);
        if (claim.kind() != ClaimKind.MONEY) {
            throw new EscrowRuntimeException("Money claim settlement references a non money claim");
        }
        requireRecordIdentity(record, settlement.ledgerTransaction().transactionId());
        ledger.applyCommitted(settlement.ledgerTransaction());
        ClaimAttemptResult result = claims.deliverCommitted(
                delivery.ownerId(), delivery.claimId(), delivery.requestKey(), delivery.units(),
                delivery.deliveredAt());
        requireDeliveredUnits(result, delivery.units());
    }

    private EscrowPreflightResult preflightMoneyClaimSettlement(UUID recordTransactionId,
                                                                 byte[] body) {
        MoneyClaimSettlement settlement = MoneyClaimSettlementCodec.decode(body);
        ClaimDeliveryCommit delivery = settlement.delivery();
        EscrowClaim claim = requireClaim(delivery);
        if (claim.kind() != ClaimKind.MONEY) {
            throw new EscrowRuntimeException("Money claim settlement references a non money claim");
        }
        requireRecordIdentity(recordTransactionId, settlement.ledgerTransaction().transactionId());
        boolean ledgerReplay = ledger.preflightCommitted(
                settlement.ledgerTransaction()).replayed();
        ClaimAttemptResult claimResult = claims.preflightDeliveryCommitted(
                delivery.ownerId(), delivery.claimId(), delivery.requestKey(), delivery.units(),
                delivery.deliveredAt());
        requireDeliveredUnits(claimResult, delivery.units());
        if (ledgerReplay != claimResult.replayed()) {
            throw new EscrowRuntimeException("Money claim settlement is only partially materialized");
        }
        return result(ledgerReplay);
    }

    private void applyAdministrativeAudit(JournalRecord record, byte[] body) {
        EscrowAdministrativeRecord audit = AdministrativeAuditJournalCodec.decode(body);
        requireRecordIdentity(record, audit.requestId());
        administrativeAudit.append(audit);
    }

    private EscrowPreflightResult preflightAdministrativeAudit(UUID recordTransactionId,
                                                               byte[] body) {
        EscrowAdministrativeRecord audit = AdministrativeAuditJournalCodec.decode(body);
        requireRecordIdentity(recordTransactionId, audit.requestId());
        return result(administrativeAudit.preflightAppend(audit).replayed());
    }

    private void applyCustodyMutation(JournalRecord record, byte[] body) {
        CustodyMutation mutation = CustodyMutationCodec.decode(body);
        requireRecordIdentity(record, mutation.receipt().transactionId());
        requireDurableCustodyPrepare(mutation);
        custody.applyCommitted(mutation);
    }

    private EscrowPreflightResult preflightCustodyMutation(UUID recordTransactionId, byte[] body) {
        CustodyMutation mutation = CustodyMutationCodec.decode(body);
        requireRecordIdentity(recordTransactionId, mutation.receipt().transactionId());
        requireDurableCustodyPrepare(mutation);
        return result(custody.preflightCommitted(mutation).replayed());
    }

    private void applyCustodyPrepare(JournalRecord record, byte[] body) {
        CustodyPreparedOperation intent = CustodyPreparedOperationCodec.decode(body);
        requireRecordIdentity(record, intent.lotSnapshot().transactionId());
        custody.prepareCommitted(intent);
    }

    private EscrowPreflightResult preflightCustodyPrepare(UUID recordTransactionId, byte[] body) {
        CustodyPreparedOperation intent = CustodyPreparedOperationCodec.decode(body);
        requireRecordIdentity(recordTransactionId, intent.lotSnapshot().transactionId());
        return result(custody.preflightPrepareCommitted(intent).replayed());
    }

    private void applyCustodyBatch(JournalRecord record, byte[] body) {
        CustodyBatchCommit commit = CustodyBatchCommitCodec.decode(body);
        requireRecordIdentity(record, commit.batch().transactionId());
        custody.applyBatchCommit(commit);
    }

    private EscrowPreflightResult preflightCustodyBatch(UUID recordTransactionId, byte[] body) {
        CustodyBatchCommit commit = CustodyBatchCommitCodec.decode(body);
        requireRecordIdentity(recordTransactionId, commit.batch().transactionId());
        return result(custody.preflightBatchCommit(commit).replayed());
    }

    private void applyProtectedMint(JournalRecord record, byte[] body) {
        ProtectedMintJournalEvent event = ProtectedMintEventCodec.decode(body);
        requireRecordIdentity(record, event.transactionId());
        protectedMints.applyCommitted(event);
    }

    private EscrowPreflightResult preflightProtectedMint(UUID recordTransactionId, byte[] body) {
        ProtectedMintJournalEvent event = ProtectedMintEventCodec.decode(body);
        requireRecordIdentity(recordTransactionId, event.transactionId());
        return result(protectedMints.preflightCommitted(event).replayed());
    }

    private EscrowPreflightResult preflightAtmWithdrawal(
            UUID recordTransactionId,
            byte[] body
    ) {
        AtmWithdrawalCommit commit = AtmWithdrawalCommitCodec.decode(body);
        requireRecordIdentity(recordTransactionId, commit.transactionId());
        EscrowTransaction current = transactions.getTransaction(
                new EscrowTransactionId(commit.transactionId()));
        if (current == null) {
            throw new EscrowRuntimeException(
                    "ATM withdrawal requires an existing escrow transaction");
        }
        if (current.revision() < commit.committedTransaction().revision()
                && current.state() != EscrowState.HELD) {
            throw new EscrowRuntimeException(
                    "ATM withdrawal transaction is not held");
        }
        protectedMints.preflightIssueBatch(commit.mintIssues());
        claims.preflightCreateBatch(commit.cashClaims());
        CompositeMaterialization materialization = new CompositeMaterialization();
        materialization.accept(transactions.preflightCommitted(
                commit.committedTransaction()).replayed());
        materialization.accept(ledger.preflightCommitted(
                commit.ledgerTransaction()).replayed());
        for (ProtectedMintJournalEvent issue : commit.mintIssues()) {
            materialization.accept(protectedMints.preflightCommitted(issue).replayed());
        }
        for (EscrowClaim claim : commit.cashClaims()) {
            boolean replayed = claims.getClaim(claim.claimId()) != null;
            claims.preflightCreateCommitted(claim);
            materialization.accept(replayed);
        }
        return materialization.result();
    }

    private void applyAtmWithdrawal(JournalRecord record, byte[] body) {
        AtmWithdrawalCommit commit = AtmWithdrawalCommitCodec.decode(body);
        requireRecordIdentity(record, commit.transactionId());
        int step = 0;
        transactions.applyCommitted(commit.committedTransaction());
        atmWithdrawalFaults.afterMutation(step++);
        ledger.applyCommitted(commit.ledgerTransaction());
        atmWithdrawalFaults.afterMutation(step++);
        for (ProtectedMintJournalEvent issue : commit.mintIssues()) {
            protectedMints.applyCommitted(issue);
            atmWithdrawalFaults.afterMutation(step++);
        }
        for (EscrowClaim claim : commit.cashClaims()) {
            claims.createCommitted(claim);
            atmWithdrawalFaults.afterMutation(step++);
        }
    }

    private EscrowPreflightResult preflightMaintenanceRepair(UUID recordTransactionId,
                                                              byte[] body) {
        MaintenanceRepairJournalEntry entry = MaintenanceRepairJournalCodec.decode(body);
        return maintenanceRepairs.preflight(recordTransactionId, entry);
    }

    private void applyMaintenanceRepair(JournalRecord record, byte[] body) {
        MaintenanceRepairJournalEntry entry = MaintenanceRepairJournalCodec.decode(body);
        maintenanceRepairs.apply(record.transactionId(), entry,
                this::applyMaintenanceEffect, administrativeAudit::append);
    }

    private void applyMaintenanceEffect(MaintenanceRepairJournalEntry.Effect effect) {
        if (effect instanceof MaintenanceRepairJournalEntry.TransactionState value) {
            transactions.applyCommitted(value.transaction());
        } else if (effect instanceof MaintenanceRepairJournalEntry.ClaimState value) {
            claims.applyMaintenanceReplace(value.claim());
        } else if (effect instanceof MaintenanceRepairJournalEntry.CustodyBatchState value) {
            custody.applyBatchCommit(value.commit());
        } else if (!(effect instanceof MaintenanceRepairJournalEntry.RuntimeState)
                && !(effect instanceof MaintenanceRepairJournalEntry.AuditOnly)
                && !(effect instanceof MaintenanceRepairJournalEntry.CustodyLotVerification)) {
            throw new EscrowRuntimeException("Unknown maintenance repair effect");
        }
    }

    public synchronized void requireDurableCustodyPrepare(CustodyMutation mutation) {
        custody.validatePreparedProof(Objects.requireNonNull(mutation, "mutation"));
    }

    private EscrowClaim requireClaim(ClaimDeliveryCommit delivery) {
        return requireClaim(delivery.ownerId(), delivery.claimId());
    }

    private EscrowClaim requireClaim(UUID ownerId, UUID claimId) {
        EscrowClaim claim = claims.getClaim(claimId);
        if (claim == null || !claim.ownerId().equals(ownerId)) {
            throw new EscrowRuntimeException("Escrow claim does not match its delivery");
        }
        return claim;
    }

    private static void requireDeliveredUnits(ClaimAttemptResult result, long expected) {
        if (result.deliveredUnits() != expected) {
            throw new EscrowRuntimeException("Escrow claim delivery amount does not match");
        }
    }

    private static EscrowPreflightResult result(boolean replayed) {
        return replayed ? EscrowPreflightResult.REPLAY : EscrowPreflightResult.APPLY;
    }

    private static void requireRecordIdentity(JournalRecord record, UUID expected) {
        requireRecordIdentity(record.transactionId(), expected);
    }

    private static void requireRecordIdentity(UUID actual, UUID expected) {
        if (!actual.equals(expected)) {
            throw new EscrowRuntimeException("Escrow journal transaction identity does not match");
        }
    }

    private static final class CompositeMaterialization {
        private Boolean replayed;

        private void accept(boolean componentReplayed) {
            if (replayed != null && replayed != componentReplayed) {
                throw new EscrowRuntimeException(
                        "ATM withdrawal commit is only partially materialized");
            }
            replayed = componentReplayed;
        }

        private EscrowPreflightResult result() {
            if (replayed == null) {
                throw new EscrowRuntimeException(
                        "ATM withdrawal commit has no materialized components");
            }
            return replayed ? EscrowPreflightResult.REPLAY : EscrowPreflightResult.APPLY;
        }
    }
}

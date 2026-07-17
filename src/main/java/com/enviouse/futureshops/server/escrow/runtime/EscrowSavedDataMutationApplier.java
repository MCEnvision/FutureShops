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
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountId;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountType;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintEventCodec;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintJournalEvent;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintOperation;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintSavedData;
import com.enviouse.futureshops.server.escrow.redemption.ProtectedCashRedemptionConservationValidator;
import com.enviouse.futureshops.server.escrow.redemption.ProtectedCashRedemptionReservation;
import com.enviouse.futureshops.server.escrow.redemption.ProtectedCashRedemptionReservationCodec;
import com.enviouse.futureshops.server.escrow.redemption.ProtectedCashRedemptionSettlement;
import com.enviouse.futureshops.server.escrow.redemption.ProtectedCashRedemptionSettlementCodec;
import com.enviouse.futureshops.server.escrow.redemption.ProtectedCashRedemptionCancellation;
import com.enviouse.futureshops.server.escrow.redemption.ProtectedCashRedemptionCancellationCodec;
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
            case FOREIGN_ATM_WITHDRAWAL_COMMIT ->
                    preflightForeignAtmWithdrawal(
                            transactionId, event.body());
            case CASH_CLAIM_DELIVERY_COMMIT ->
                    preflightCashClaimDelivery(
                            transactionId, event.body());
            case PROTECTED_CASH_REDEMPTION_RESERVATION ->
                    preflightProtectedCashReservation(
                            transactionId, event.body());
            case PROTECTED_CASH_REDEMPTION_SETTLEMENT ->
                    preflightProtectedCashSettlement(
                            transactionId, event.body());
            case PROTECTED_CASH_REDEMPTION_CANCELLATION ->
                    preflightProtectedCashCancellation(
                            transactionId, event.body());
            case FOREIGN_CASH_DEPOSIT_RESERVATION ->
                    preflightForeignCashReservation(
                            transactionId, event.body());
            case FOREIGN_CASH_DEPOSIT_SETTLEMENT ->
                    preflightForeignCashSettlement(
                            transactionId, event.body());
            case FOREIGN_CASH_DEPOSIT_CANCELLATION ->
                    preflightForeignCashCancellation(
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
            case FOREIGN_ATM_WITHDRAWAL_COMMIT ->
                    applyForeignAtmWithdrawal(record, event.body());
            case CASH_CLAIM_DELIVERY_COMMIT ->
                    applyCashClaimDelivery(record, event.body());
            case PROTECTED_CASH_REDEMPTION_RESERVATION ->
                    applyProtectedCashReservation(record, event.body());
            case PROTECTED_CASH_REDEMPTION_SETTLEMENT ->
                    applyProtectedCashSettlement(record, event.body());
            case PROTECTED_CASH_REDEMPTION_CANCELLATION ->
                    applyProtectedCashCancellation(record, event.body());
            case FOREIGN_CASH_DEPOSIT_RESERVATION ->
                    applyForeignCashReservation(record, event.body());
            case FOREIGN_CASH_DEPOSIT_SETTLEMENT ->
                    applyForeignCashSettlement(record, event.body());
            case FOREIGN_CASH_DEPOSIT_CANCELLATION ->
                    applyForeignCashCancellation(record, event.body());
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

    private void applyCashClaimDelivery(
            JournalRecord record,
            byte[] body
    ) {
        CashClaimDeliveryCommit commit =
                CashClaimDeliveryCommitCodec.decode(body);
        EscrowClaim claim = requireClaim(commit.delivery());
        requireRecordIdentity(record, claim.transactionId());
        CashClaimDeliveryValidator.validate(claim, commit, protectedMints);
        custody.applyTransientRelease(commit.custody());
        ClaimAttemptResult result = claims.deliverCommitted(
                commit.delivery().ownerId(), commit.delivery().claimId(),
                commit.delivery().requestKey(), commit.delivery().units(),
                commit.delivery().deliveredAt());
        requireDeliveredUnits(result, commit.delivery().units());
    }

    private EscrowPreflightResult preflightCashClaimDelivery(
            UUID recordTransactionId,
            byte[] body
    ) {
        CashClaimDeliveryCommit commit =
                CashClaimDeliveryCommitCodec.decode(body);
        EscrowClaim claim = requireClaim(commit.delivery());
        requireRecordIdentity(recordTransactionId, claim.transactionId());
        CashClaimDeliveryValidator.validate(claim, commit, protectedMints);
        boolean custodyReplayed = custody.preflightTransientRelease(
                commit.custody()).replayed();
        ClaimAttemptResult result = claims.preflightDeliveryCommitted(
                commit.delivery().ownerId(), commit.delivery().claimId(),
                commit.delivery().requestKey(), commit.delivery().units(),
                commit.delivery().deliveredAt());
        requireDeliveredUnits(result, commit.delivery().units());
        if (result.replayed() && !custodyReplayed) {
            throw new EscrowRuntimeException(
                    "Cash claim delivery custody is missing");
        }
        return result(result.replayed() && custodyReplayed);
    }

    private EscrowPreflightResult preflightProtectedCashReservation(
            UUID recordTransactionId,
            byte[] body
    ) {
        ProtectedCashRedemptionReservation reservation =
                ProtectedCashRedemptionReservationCodec.decode(body);
        requireRecordIdentity(recordTransactionId,
                reservation.transactionId());
        ProtectedCashRedemptionConservationValidator.validateReservation(
                reservation, protectedMints);
        CompositeMaterialization materialization =
                new CompositeMaterialization();
        materialization.accept(transactions.preflightFoldedHeldCommitted(
                reservation.heldTransaction()).replayed());
        materialization.accept(custody.preflightCommittedBatch(
                reservation.custodyReservations()));
        for (var mintResult : protectedMints.preflightTransitionBatch(
                reservation.mintReservations(),
                ProtectedMintOperation.RESERVE)) {
            materialization.accept(mintResult.replayed());
        }
        return materialization.result();
    }

    private void applyProtectedCashReservation(
            JournalRecord record,
            byte[] body
    ) {
        ProtectedCashRedemptionReservation reservation =
                ProtectedCashRedemptionReservationCodec.decode(body);
        requireRecordIdentity(record, reservation.transactionId());
        ProtectedCashRedemptionConservationValidator.validateReservation(
                reservation, protectedMints);
        transactions.applyFoldedHeldCommitted(
                reservation.heldTransaction());
        custody.applyCommittedBatch(reservation.custodyReservations());
        protectedMints.applyTransitionBatch(reservation.mintReservations(),
                ProtectedMintOperation.RESERVE);
    }

    private EscrowPreflightResult preflightProtectedCashSettlement(
            UUID recordTransactionId,
            byte[] body
    ) {
        ProtectedCashRedemptionSettlement settlement =
                ProtectedCashRedemptionSettlementCodec.decode(body);
        requireRecordIdentity(recordTransactionId,
                settlement.transactionId());
        ProtectedCashRedemptionConservationValidator.validateSettlement(
                settlement, protectedMints);
        requireProtectedCashReservationMaterialized(settlement.reservation());
        boolean ledgerReplayed = ledger.preflightCommitted(
                settlement.ledgerTransaction()).replayed();
        requireProtectedCashWalletSnapshot(settlement, ledgerReplayed);
        CompositeMaterialization materialization =
                new CompositeMaterialization();
        materialization.accept(custody.preflightCommittedBatch(
                settlement.custodyConsumptions()));
        for (var mintResult : protectedMints.preflightTransitionBatch(
                settlement.mintCommits(), ProtectedMintOperation.COMMIT)) {
            materialization.accept(mintResult.replayed());
        }
        settlement.overflowClaim().ifPresent(claim -> {
            boolean claimReplayed = claims.getClaim(claim.claimId()) != null;
            claims.preflightCreateCommitted(claim);
            materialization.accept(claimReplayed);
        });
        materialization.accept(ledgerReplayed);
        materialization.accept(
                transactions.preflightFoldedCompletionCommitted(
                        settlement.reservation().heldTransaction(),
                        settlement.completedTransaction()).replayed());
        return materialization.result();
    }

    private void applyProtectedCashSettlement(
            JournalRecord record,
            byte[] body
    ) {
        ProtectedCashRedemptionSettlement settlement =
                ProtectedCashRedemptionSettlementCodec.decode(body);
        requireRecordIdentity(record, settlement.transactionId());
        ProtectedCashRedemptionConservationValidator.validateSettlement(
                settlement, protectedMints);
        boolean ledgerReplayed = ledger.preflightCommitted(
                settlement.ledgerTransaction()).replayed();
        requireProtectedCashWalletSnapshot(settlement, ledgerReplayed);
        custody.applyCommittedBatch(settlement.custodyConsumptions());
        protectedMints.applyTransitionBatch(settlement.mintCommits(),
                ProtectedMintOperation.COMMIT);
        settlement.overflowClaim().ifPresent(claims::createCommitted);
        ledger.applyCommitted(settlement.ledgerTransaction());
        transactions.applyFoldedCompletionCommitted(
                settlement.reservation().heldTransaction(),
                settlement.completedTransaction());
    }

    private void requireProtectedCashWalletSnapshot(
            ProtectedCashRedemptionSettlement settlement,
            boolean ledgerReplayed
    ) {
        if (ledgerReplayed || settlement.destinationAccount().type()
                != LedgerAccountType.PLAYER_WALLET) {
            return;
        }
        String owner = settlement.reservation().playerId().toString();
        LedgerAccountId wallet = new LedgerAccountId(
                LedgerAccountType.PLAYER_WALLET, owner);
        LedgerAccountId reserved = new LedgerAccountId(
                LedgerAccountType.PLAYER_RESERVED, owner);
        if (ledger.balance(wallet)
                != settlement.walletBalanceBeforeMinorUnits()
                || ledger.balance(reserved)
                != settlement.walletReservedBeforeMinorUnits()) {
            throw new EscrowRuntimeException(
                    "Protected cash wallet balance snapshot changed");
        }
    }

    private void requireProtectedCashReservationMaterialized(
            ProtectedCashRedemptionReservation reservation
    ) {
        if (!transactions.preflightFoldedHeldCommitted(
                reservation.heldTransaction()).replayed()
                || !custody.preflightCommittedBatch(
                reservation.custodyReservations())) {
            throw new EscrowRuntimeException(
                    "Protected cash reservation is not materialized");
        }
        for (var mintResult : protectedMints.preflightTransitionBatch(
                reservation.mintReservations(),
                ProtectedMintOperation.RESERVE)) {
            if (!mintResult.replayed()) {
                throw new EscrowRuntimeException(
                        "Protected cash mint reservation is not materialized");
            }
        }
    }

    private EscrowPreflightResult preflightProtectedCashCancellation(
            UUID recordTransactionId,
            byte[] body
    ) {
        ProtectedCashRedemptionCancellation cancellation =
                ProtectedCashRedemptionCancellationCodec.decode(body);
        requireRecordIdentity(recordTransactionId,
                cancellation.transactionId());
        ProtectedCashRedemptionConservationValidator.validateCancellation(
                cancellation, protectedMints);
        requireProtectedCashReservationMaterialized(
                cancellation.reservation());
        CompositeMaterialization materialization =
                new CompositeMaterialization();
        materialization.accept(custody.preflightCommittedBatch(
                cancellation.custodyReleases()));
        for (var mintResult : protectedMints.preflightTransitionBatch(
                cancellation.mintReleases(),
                ProtectedMintOperation.RELEASE)) {
            materialization.accept(mintResult.replayed());
        }
        materialization.accept(
                transactions.preflightFoldedRefundCommitted(
                        cancellation.reservation().heldTransaction(),
                        cancellation.refundedTransaction()).replayed());
        return materialization.result();
    }

    private void applyProtectedCashCancellation(
            JournalRecord record,
            byte[] body
    ) {
        ProtectedCashRedemptionCancellation cancellation =
                ProtectedCashRedemptionCancellationCodec.decode(body);
        requireRecordIdentity(record, cancellation.transactionId());
        ProtectedCashRedemptionConservationValidator.validateCancellation(
                cancellation, protectedMints);
        custody.applyCommittedBatch(cancellation.custodyReleases());
        protectedMints.applyTransitionBatch(cancellation.mintReleases(),
                ProtectedMintOperation.RELEASE);
        transactions.applyFoldedRefundCommitted(
                cancellation.reservation().heldTransaction(),
                cancellation.refundedTransaction());
    }

    private EscrowPreflightResult preflightForeignCashReservation(
            UUID recordTransactionId,
            byte[] body
    ) {
        ForeignCashDepositReservation reservation =
                ForeignCashDepositCodec.decodeReservation(body);
        requireRecordIdentity(recordTransactionId,
                reservation.transactionId());
        ForeignCashDepositConservationValidator.validateReservation(
                reservation);
        CompositeMaterialization materialization =
                new CompositeMaterialization();
        materialization.accept(transactions.preflightFoldedHeldCommitted(
                reservation.heldTransaction()).replayed());
        materialization.accept(custody.preflightCommittedBatch(
                reservation.custodyReservations()));
        return materialization.result();
    }

    private void applyForeignCashReservation(
            JournalRecord record,
            byte[] body
    ) {
        ForeignCashDepositReservation reservation =
                ForeignCashDepositCodec.decodeReservation(body);
        requireRecordIdentity(record, reservation.transactionId());
        ForeignCashDepositConservationValidator.validateReservation(
                reservation);
        transactions.applyFoldedHeldCommitted(
                reservation.heldTransaction());
        custody.applyCommittedBatch(reservation.custodyReservations());
    }

    private EscrowPreflightResult preflightForeignCashSettlement(
            UUID recordTransactionId,
            byte[] body
    ) {
        ForeignCashDepositSettlement settlement =
                ForeignCashDepositCodec.decodeSettlement(body);
        requireRecordIdentity(recordTransactionId,
                settlement.transactionId());
        ForeignCashDepositConservationValidator.validateSettlement(
                settlement);
        requireForeignCashReservationMaterialized(
                settlement.reservation());
        boolean ledgerReplayed = ledger.preflightCommitted(
                settlement.ledgerTransaction()).replayed();
        requireForeignCashWalletSnapshot(settlement, ledgerReplayed);
        CompositeMaterialization materialization =
                new CompositeMaterialization();
        materialization.accept(custody.preflightCommittedBatch(
                settlement.custodyConsumptions()));
        settlement.overflowClaim().ifPresent(claim -> {
            boolean claimReplayed = claims.getClaim(claim.claimId()) != null;
            claims.preflightCreateCommitted(claim);
            materialization.accept(claimReplayed);
        });
        materialization.accept(ledgerReplayed);
        materialization.accept(
                transactions.preflightFoldedCompletionCommitted(
                        settlement.reservation().heldTransaction(),
                        settlement.completedTransaction()).replayed());
        return materialization.result();
    }

    private void applyForeignCashSettlement(
            JournalRecord record,
            byte[] body
    ) {
        ForeignCashDepositSettlement settlement =
                ForeignCashDepositCodec.decodeSettlement(body);
        requireRecordIdentity(record, settlement.transactionId());
        ForeignCashDepositConservationValidator.validateSettlement(
                settlement);
        boolean ledgerReplayed = ledger.preflightCommitted(
                settlement.ledgerTransaction()).replayed();
        requireForeignCashWalletSnapshot(settlement, ledgerReplayed);
        custody.applyCommittedBatch(settlement.custodyConsumptions());
        settlement.overflowClaim().ifPresent(claims::createCommitted);
        ledger.applyCommitted(settlement.ledgerTransaction());
        transactions.applyFoldedCompletionCommitted(
                settlement.reservation().heldTransaction(),
                settlement.completedTransaction());
    }

    private void requireForeignCashWalletSnapshot(
            ForeignCashDepositSettlement settlement,
            boolean ledgerReplayed
    ) {
        if (ledgerReplayed) {
            return;
        }
        String owner = settlement.reservation().playerId().toString();
        LedgerAccountId wallet = new LedgerAccountId(
                LedgerAccountType.PLAYER_WALLET, owner);
        LedgerAccountId reserved = new LedgerAccountId(
                LedgerAccountType.PLAYER_RESERVED, owner);
        if (ledger.balance(wallet)
                != settlement.walletBalanceBeforeMinorUnits()
                || ledger.balance(reserved)
                != settlement.walletReservedBeforeMinorUnits()) {
            throw new EscrowRuntimeException(
                    "Foreign cash wallet balance snapshot changed");
        }
    }

    private void requireForeignCashReservationMaterialized(
            ForeignCashDepositReservation reservation
    ) {
        if (!transactions.preflightFoldedHeldCommitted(
                reservation.heldTransaction()).replayed()
                || !custody.preflightCommittedBatch(
                reservation.custodyReservations())) {
            throw new EscrowRuntimeException(
                    "Foreign cash reservation is not materialized");
        }
    }

    private EscrowPreflightResult preflightForeignCashCancellation(
            UUID recordTransactionId,
            byte[] body
    ) {
        ForeignCashDepositCancellation cancellation =
                ForeignCashDepositCodec.decodeCancellation(body);
        requireRecordIdentity(recordTransactionId,
                cancellation.transactionId());
        ForeignCashDepositConservationValidator.validateCancellation(
                cancellation);
        requireForeignCashReservationMaterialized(
                cancellation.reservation());
        CompositeMaterialization materialization =
                new CompositeMaterialization();
        materialization.accept(custody.preflightCommittedBatch(
                cancellation.custodyReleases()));
        materialization.accept(
                transactions.preflightFoldedRefundCommitted(
                        cancellation.reservation().heldTransaction(),
                        cancellation.refundedTransaction()).replayed());
        return materialization.result();
    }

    private void applyForeignCashCancellation(
            JournalRecord record,
            byte[] body
    ) {
        ForeignCashDepositCancellation cancellation =
                ForeignCashDepositCodec.decodeCancellation(body);
        requireRecordIdentity(record, cancellation.transactionId());
        ForeignCashDepositConservationValidator.validateCancellation(
                cancellation);
        custody.applyCommittedBatch(cancellation.custodyReleases());
        transactions.applyFoldedRefundCommitted(
                cancellation.reservation().heldTransaction(),
                cancellation.refundedTransaction());
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

    private EscrowPreflightResult preflightForeignAtmWithdrawal(
            UUID recordTransactionId,
            byte[] body
    ) {
        ForeignAtmWithdrawalCommit commit =
                ForeignAtmWithdrawalCommitCodec.decode(body);
        requireRecordIdentity(recordTransactionId, commit.requestId());
        EscrowTransaction current = transactions.getTransaction(
                new EscrowTransactionId(commit.requestId()));
        if (current == null) {
            throw new EscrowRuntimeException(
                    "Foreign ATM withdrawal requires an existing escrow transaction");
        }
        if (current.revision()
                < commit.committedTransaction().revision()
                && current.state() != EscrowState.HELD) {
            throw new EscrowRuntimeException(
                    "Foreign ATM withdrawal transaction is not held");
        }
        claims.preflightCreateBatch(commit.cashClaims());
        CompositeMaterialization materialization =
                new CompositeMaterialization();
        materialization.accept(transactions.preflightCommitted(
                commit.committedTransaction()).replayed());
        materialization.accept(ledger.preflightCommitted(
                commit.ledgerTransaction()).replayed());
        for (EscrowClaim claim : commit.cashClaims()) {
            boolean replayed = claims.getClaim(claim.claimId()) != null;
            claims.preflightCreateCommitted(claim);
            materialization.accept(replayed);
        }
        return materialization.result();
    }

    private void applyForeignAtmWithdrawal(
            JournalRecord record,
            byte[] body
    ) {
        ForeignAtmWithdrawalCommit commit =
                ForeignAtmWithdrawalCommitCodec.decode(body);
        requireRecordIdentity(record, commit.requestId());
        int step = 0;
        transactions.applyCommitted(commit.committedTransaction());
        atmWithdrawalFaults.afterMutation(step++);
        ledger.applyCommitted(commit.ledgerTransaction());
        atmWithdrawalFaults.afterMutation(step++);
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
                        "Escrow composite event is only partially materialized");
            }
            replayed = componentReplayed;
        }

        private EscrowPreflightResult result() {
            if (replayed == null) {
                throw new EscrowRuntimeException(
                        "Escrow composite event has no materialized components");
            }
            return replayed ? EscrowPreflightResult.REPLAY : EscrowPreflightResult.APPLY;
        }
    }
}

package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.admin.EscrowAdministrativeAuditSavedData;
import com.enviouse.futureshops.server.escrow.admin.EscrowAdministrativeRecord;
import com.enviouse.futureshops.server.escrow.admin.MaintenanceRepairCommand;
import com.enviouse.futureshops.server.escrow.admin.MaintenanceRepairTarget;
import com.enviouse.futureshops.server.escrow.admin.MaintenanceStateFingerprint;
import com.enviouse.futureshops.server.escrow.claim.ClaimAttemptResult;
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
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryJournalSavedData;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryJournalStatus;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryJournalTransition;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryJournalTransitionCodec;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryQuarantineAdministration;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryQuarantineAdministrationCodec;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryQuarantineAdministrationResult;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryJournalCompaction;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryJournalCompactionCodec;
import com.enviouse.futureshops.server.escrow.ledger.LedgerSavedData;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountId;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountType;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintEventCodec;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintJournalEvent;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintOperation;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintSavedData;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopEscrowLifecycleEvent;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopEscrowLifecycleEventCodec;
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
import com.enviouse.futureshops.server.escrow.stock.StockMutationCommand;
import com.enviouse.futureshops.server.escrow.stock.StockMutationCommandCodec;
import com.enviouse.futureshops.server.escrow.stock.StockCommandResult;
import com.enviouse.futureshops.server.escrow.stock.StockMutationOutcome;
import com.enviouse.futureshops.server.escrow.stock.StockSavedData;
import com.enviouse.futureshops.server.market.auction.AuctionHouseMutation;
import com.enviouse.futureshops.server.market.auction.AuctionHouseMutationCodec;
import com.enviouse.futureshops.server.market.auction.AuctionHouseSavedData;
import com.enviouse.futureshops.server.market.auction.escrow.AuctionEscrowCommit;
import com.enviouse.futureshops.server.market.auction.escrow.AuctionEscrowLedgerAccounts;
import com.enviouse.futureshops.server.market.auction.escrow.AuctionEscrowLifecycleEvent;
import com.enviouse.futureshops.server.market.auction.escrow.AuctionEscrowLifecycleEventCodec;
import com.enviouse.futureshops.server.market.auction.escrow.AuctionEscrowWalletSnapshot;
import com.enviouse.futureshops.server.market.bazaar.BazaarMutation;
import com.enviouse.futureshops.server.market.bazaar.BazaarMutationCodec;
import com.enviouse.futureshops.server.market.bazaar.BazaarSavedData;
import com.enviouse.futureshops.server.market.bazaar.BazaarOrderSide;
import com.enviouse.futureshops.server.market.bazaar.escrow.BazaarBuyFundingEvidence;
import com.enviouse.futureshops.server.market.bazaar.escrow.BazaarCreateEscrowIntent;
import com.enviouse.futureshops.server.market.bazaar.escrow.BazaarEscrowCommit;
import com.enviouse.futureshops.server.market.bazaar.escrow.BazaarEscrowLedgerAccounts;
import com.enviouse.futureshops.server.market.bazaar.escrow.BazaarEscrowLifecycleEvent;
import com.enviouse.futureshops.server.market.bazaar.escrow.BazaarEscrowLifecycleEventCodec;
import com.enviouse.futureshops.server.market.bazaar.escrow.BazaarEscrowWalletSnapshot;
import com.enviouse.futureshops.server.market.control.MarketControlApplyResult;
import com.enviouse.futureshops.server.market.control.MarketControlMutation;
import com.enviouse.futureshops.server.market.control.MarketControlMutationCodec;
import com.enviouse.futureshops.server.market.control.MarketControlSavedData;

import java.util.Objects;
import java.util.UUID;

public final class EscrowSavedDataMutationApplier implements EscrowMutationApplier {
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
        this(transactions, ledger, claims, administrativeAudit, custody,
                protectedMints, new StockSavedData(),
                new ItemInventoryJournalSavedData(), runtimeHandler,
                atmWithdrawalFaults, mutationPermit);
    }

    EscrowSavedDataMutationApplier(EscrowTransactionSavedData transactions,
                                   LedgerSavedData ledger, ClaimSavedData claims,
                                   EscrowAdministrativeAuditSavedData administrativeAudit,
                                   CustodySavedData custody,
                                   ProtectedMintSavedData protectedMints,
                                   StockSavedData stock,
                                   MaintenanceRuntimeMutationHandler runtimeHandler,
                                   AtmWithdrawalApplyFaultInjector atmWithdrawalFaults,
                                   EscrowMutationPermit mutationPermit) {
        this(transactions, ledger, claims, administrativeAudit, custody,
                protectedMints, stock,
                new ItemInventoryJournalSavedData(), runtimeHandler,
                atmWithdrawalFaults, mutationPermit);
    }

    EscrowSavedDataMutationApplier(EscrowTransactionSavedData transactions,
                                   LedgerSavedData ledger, ClaimSavedData claims,
                                   EscrowAdministrativeAuditSavedData administrativeAudit,
                                   CustodySavedData custody,
                                   ProtectedMintSavedData protectedMints,
                                   StockSavedData stock,
                                   ItemInventoryJournalSavedData itemInventoryJournal,
                                   MaintenanceRuntimeMutationHandler runtimeHandler,
                                   AtmWithdrawalApplyFaultInjector atmWithdrawalFaults,
                                   EscrowMutationPermit mutationPermit) {
        this(transactions, ledger, claims, administrativeAudit, custody,
                protectedMints, stock, itemInventoryJournal,
                new AuctionHouseSavedData(), runtimeHandler,
                atmWithdrawalFaults, mutationPermit);
    }

    EscrowSavedDataMutationApplier(EscrowTransactionSavedData transactions,
                                   LedgerSavedData ledger, ClaimSavedData claims,
                                   EscrowAdministrativeAuditSavedData administrativeAudit,
                                   CustodySavedData custody,
                                   ProtectedMintSavedData protectedMints,
                                   StockSavedData stock,
                                   ItemInventoryJournalSavedData itemInventoryJournal,
                                   AuctionHouseSavedData auctionHouse,
                                   MaintenanceRuntimeMutationHandler runtimeHandler,
                                   AtmWithdrawalApplyFaultInjector atmWithdrawalFaults,
                                   EscrowMutationPermit mutationPermit) {
        this(transactions, ledger, claims, administrativeAudit, custody,
                protectedMints, stock, itemInventoryJournal, auctionHouse,
                new ServerShopIntentSavedData(), runtimeHandler,
                atmWithdrawalFaults, mutationPermit);
    }

    EscrowSavedDataMutationApplier(EscrowTransactionSavedData transactions,
                                   LedgerSavedData ledger, ClaimSavedData claims,
                                   EscrowAdministrativeAuditSavedData administrativeAudit,
                                   CustodySavedData custody,
                                   ProtectedMintSavedData protectedMints,
                                   StockSavedData stock,
                                   ItemInventoryJournalSavedData itemInventoryJournal,
                                   AuctionHouseSavedData auctionHouse,
                                   ServerShopIntentSavedData serverShopIntents,
                                   MaintenanceRuntimeMutationHandler runtimeHandler,
                                   AtmWithdrawalApplyFaultInjector atmWithdrawalFaults,
                                   EscrowMutationPermit mutationPermit) {
        this(transactions, ledger, claims, administrativeAudit, custody,
                protectedMints, stock, itemInventoryJournal, auctionHouse,
                new BazaarSavedData(), serverShopIntents, runtimeHandler,
                atmWithdrawalFaults, mutationPermit);
    }

    EscrowSavedDataMutationApplier(EscrowTransactionSavedData transactions,
                                   LedgerSavedData ledger, ClaimSavedData claims,
                                   EscrowAdministrativeAuditSavedData administrativeAudit,
                                   CustodySavedData custody,
                                   ProtectedMintSavedData protectedMints,
                                   StockSavedData stock,
                                   ItemInventoryJournalSavedData itemInventoryJournal,
                                   AuctionHouseSavedData auctionHouse,
                                   BazaarSavedData bazaar,
                                   ServerShopIntentSavedData serverShopIntents,
                                   MaintenanceRuntimeMutationHandler runtimeHandler,
                                   AtmWithdrawalApplyFaultInjector atmWithdrawalFaults,
                                   EscrowMutationPermit mutationPermit) {
        this(transactions, ledger, claims, administrativeAudit, custody,
                protectedMints, stock, itemInventoryJournal, auctionHouse,
                bazaar, serverShopIntents, new PlayerShopEscrowSavedData(),
                runtimeHandler, atmWithdrawalFaults, mutationPermit);
    }

    EscrowSavedDataMutationApplier(EscrowTransactionSavedData transactions,
                                   LedgerSavedData ledger, ClaimSavedData claims,
                                   EscrowAdministrativeAuditSavedData administrativeAudit,
                                   CustodySavedData custody,
                                   ProtectedMintSavedData protectedMints,
                                   StockSavedData stock,
                                   ItemInventoryJournalSavedData itemInventoryJournal,
                                   AuctionHouseSavedData auctionHouse,
                                   BazaarSavedData bazaar,
                                   ServerShopIntentSavedData serverShopIntents,
                                   PlayerShopEscrowSavedData playerShopEscrow,
                                   MaintenanceRuntimeMutationHandler runtimeHandler,
                                   AtmWithdrawalApplyFaultInjector atmWithdrawalFaults,
                                   EscrowMutationPermit mutationPermit) {
        this(transactions, ledger, claims, administrativeAudit, custody,
                protectedMints, stock, itemInventoryJournal, auctionHouse,
                bazaar, serverShopIntents, playerShopEscrow,
                new MarketControlSavedData(), runtimeHandler,
                atmWithdrawalFaults, mutationPermit);
    }

    EscrowSavedDataMutationApplier(EscrowTransactionSavedData transactions,
                                   LedgerSavedData ledger, ClaimSavedData claims,
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
                                   MaintenanceRuntimeMutationHandler runtimeHandler,
                                   AtmWithdrawalApplyFaultInjector atmWithdrawalFaults,
                                   EscrowMutationPermit mutationPermit) {
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.claims = Objects.requireNonNull(claims, "claims");
        this.administrativeAudit = Objects.requireNonNull(administrativeAudit, "administrativeAudit");
        this.custody = Objects.requireNonNull(custody, "custody");
        this.protectedMints = Objects.requireNonNull(protectedMints, "protectedMints");
        this.stock = Objects.requireNonNull(stock, "stock");
        this.itemInventoryJournal = Objects.requireNonNull(
                itemInventoryJournal, "itemInventoryJournal");
        this.auctionHouse = Objects.requireNonNull(
                auctionHouse, "auctionHouse");
        this.bazaar = Objects.requireNonNull(bazaar, "bazaar");
        this.serverShopIntents = Objects.requireNonNull(
                serverShopIntents, "serverShopIntents");
        this.playerShopEscrow = Objects.requireNonNull(
                playerShopEscrow, "playerShopEscrow");
        this.marketControl = Objects.requireNonNull(
                marketControl, "marketControl");
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
            case PLAYER_PAYMENT_COMMIT -> preflightPlayerPayment(
                    transactionId, event.body());
            case STOCK_MUTATION -> preflightStockMutation(
                    transactionId, event.body());
            case SERVER_SHOP_PURCHASE_COMMIT ->
                    preflightServerShopPurchase(
                            transactionId, event.body());
            case SERVER_SHOP_FUNDING_RELEASE ->
                    preflightServerShopFundingRelease(
                            transactionId, event.body());
            case SERVER_SHOP_SELL_COMMIT ->
                    preflightServerShopSellLifecycle(
                            transactionId, event.body());
            case SERVER_SHOP_BARTER_COMMIT ->
                    preflightServerShopBarterLifecycle(
                            transactionId, event.body());
            case ITEM_INVENTORY_MUTATION ->
                    preflightItemInventoryMutation(
                            transactionId, event.body());
            case EXACT_ITEM_CLAIM_DELIVERY_COMMIT ->
                    preflightExactItemClaimDelivery(
                            transactionId, event.body());
            case ITEM_INVENTORY_QUARANTINE_ADMINISTRATION ->
                    preflightItemInventoryQuarantineAdministration(
                            transactionId, event.body());
            case ITEM_INVENTORY_JOURNAL_COMPACTION ->
                    preflightItemInventoryJournalCompaction(
                            transactionId, event.body());
            case AUCTION_HOUSE_MUTATION ->
                    preflightAuctionHouseMutation(
                            transactionId, event.body());
            case AUCTION_HOUSE_ESCROW_LIFECYCLE ->
                    preflightAuctionEscrowLifecycle(
                            transactionId, event.body());
            case BAZAAR_MUTATION -> preflightBazaarMutation(
                    transactionId, event.body());
            case BAZAAR_ESCROW_LIFECYCLE ->
                    preflightBazaarEscrowLifecycle(
                            transactionId, event.body());
            case PLAYER_SHOP_ESCROW_LIFECYCLE ->
                    preflightPlayerShopEscrowLifecycle(
                            transactionId, event.body());
            case MARKET_CONTROL_MUTATION ->
                    preflightMarketControlMutation(
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
            case PLAYER_PAYMENT_COMMIT ->
                    applyPlayerPayment(record, event.body());
            case STOCK_MUTATION ->
                    applyStockMutation(record, event.body());
            case SERVER_SHOP_PURCHASE_COMMIT ->
                    applyServerShopPurchase(record, event.body());
            case SERVER_SHOP_FUNDING_RELEASE ->
                    applyServerShopFundingRelease(record, event.body());
            case SERVER_SHOP_SELL_COMMIT ->
                    applyServerShopSellLifecycle(record, event.body());
            case SERVER_SHOP_BARTER_COMMIT ->
                    applyServerShopBarterLifecycle(record, event.body());
            case ITEM_INVENTORY_MUTATION ->
                    applyItemInventoryMutation(record, event.body());
            case EXACT_ITEM_CLAIM_DELIVERY_COMMIT ->
                    applyExactItemClaimDelivery(record, event.body());
            case ITEM_INVENTORY_QUARANTINE_ADMINISTRATION ->
                    applyItemInventoryQuarantineAdministration(
                            record, event.body());
            case ITEM_INVENTORY_JOURNAL_COMPACTION ->
                    applyItemInventoryJournalCompaction(
                            record, event.body());
            case AUCTION_HOUSE_MUTATION ->
                    applyAuctionHouseMutation(record, event.body());
            case AUCTION_HOUSE_ESCROW_LIFECYCLE ->
                    applyAuctionEscrowLifecycle(record, event.body());
            case BAZAAR_MUTATION ->
                    applyBazaarMutation(record, event.body());
            case BAZAAR_ESCROW_LIFECYCLE ->
                    applyBazaarEscrowLifecycle(record, event.body());
            case PLAYER_SHOP_ESCROW_LIFECYCLE ->
                    applyPlayerShopEscrowLifecycle(record, event.body());
            case MARKET_CONTROL_MUTATION ->
                    applyMarketControlMutation(record, event.body());
            case JOURNAL_LINEAGE -> throw new EscrowRuntimeException(
                    "Journal lineage cannot be applied as a mutation");
        }
    }

    private void applyTransaction(JournalRecord record, byte[] body) {
        EscrowTransaction transaction = EscrowTransactionByteCodec.decode(body);
        requireRecordIdentity(record, transaction.transactionId().value());
        transactions.applyCommitted(transaction);
    }

    private EscrowPreflightResult preflightMarketControlMutation(
            UUID recordTransactionId,
            byte[] body
    ) {
        MarketControlMutation mutation =
                MarketControlMutationCodec.decode(body);
        requireRecordIdentity(recordTransactionId,
                mutation.auditEntry().requestId());
        MarketControlApplyResult applyResult =
                marketControl.preflightCommitted(mutation);
        return result(applyResult.replayed());
    }

    private void applyMarketControlMutation(
            JournalRecord record,
            byte[] body
    ) {
        MarketControlMutation mutation =
                MarketControlMutationCodec.decode(body);
        requireRecordIdentity(record,
                mutation.auditEntry().requestId());
        marketControl.applyCommitted(mutation);
    }

    private void applyStockMutation(JournalRecord record, byte[] body) {
        StockMutationCommand command = StockMutationCommandCodec.decode(body);
        requireRecordIdentity(record, command.requestId());
        stock.applyCommitted(command);
    }

    private EscrowPreflightResult preflightStockMutation(
            UUID recordTransactionId,
            byte[] body
    ) {
        StockMutationCommand command = StockMutationCommandCodec.decode(body);
        requireRecordIdentity(recordTransactionId, command.requestId());
        return result(stock.preflightCommitted(command).replayed());
    }

    private EscrowPreflightResult preflightItemInventoryMutation(
            UUID recordTransactionId,
            byte[] body
    ) {
        ItemInventoryJournalTransition transition =
                ItemInventoryJournalTransitionCodec.decode(body);
        requireRecordIdentity(recordTransactionId,
                transition.requestId());
        return result(itemInventoryJournal.preflightCommitted(
                transition).replayed());
    }

    private void applyItemInventoryMutation(
            JournalRecord record,
            byte[] body
    ) {
        ItemInventoryJournalTransition transition =
                ItemInventoryJournalTransitionCodec.decode(body);
        requireRecordIdentity(record, transition.requestId());
        itemInventoryJournal.applyCommitted(transition);
    }

    private EscrowPreflightResult preflightAuctionHouseMutation(
            UUID recordTransactionId,
            byte[] body
    ) {
        AuctionHouseMutation mutation =
                AuctionHouseMutationCodec.decode(body);
        requireRecordIdentity(recordTransactionId,
                mutation.requestId());
        return result(auctionHouse.preflightCommitted(
                mutation).replayed());
    }

    private void applyAuctionHouseMutation(
            JournalRecord record,
            byte[] body
    ) {
        AuctionHouseMutation mutation =
                AuctionHouseMutationCodec.decode(body);
        requireRecordIdentity(record, mutation.requestId());
        auctionHouse.applyCommitted(mutation);
    }

    private EscrowPreflightResult preflightAuctionEscrowLifecycle(
            UUID recordTransactionId,
            byte[] body
    ) {
        AuctionEscrowLifecycleEvent event =
                AuctionEscrowLifecycleEventCodec.decode(body);
        requireRecordIdentity(recordTransactionId, event.requestId());
        requireAuctionCreateCustodyCommitted(event);
        RecoveryMaterialization materialization =
                new RecoveryMaterialization();
        materialization.accept(auctionHouse
                .preflightEscrowLifecycleCommitted(event).replayed());
        if (!(event instanceof AuctionEscrowLifecycleEvent.Commit value)) {
            return materialization.result();
        }
        AuctionEscrowCommit commit = value.commit();
        commit.completedTransaction().ifPresent(transaction ->
                materialization.accept(transactions
                        .preflightFoldedAtomicCompletionCommitted(
                                transaction).replayed()));
        commit.ledgerTransaction().ifPresent(transaction -> {
            boolean replayed = ledger.preflightCommitted(
                    transaction).replayed();
            if (!replayed) {
                requireAuctionWalletSnapshots(commit.walletSnapshots());
            }
            materialization.accept(replayed);
        });
        java.util.Set<UUID> expectedClaimIds = commit.claims().stream()
                .map(EscrowClaim::claimId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        java.util.List<EscrowClaim> existing =
                claims.claimsForTransaction(commit.requestId());
        if (!existing.stream().map(EscrowClaim::claimId)
                .allMatch(expectedClaimIds::contains)) {
            throw new EscrowRuntimeException(
                    "Auction escrow has unexpected claim evidence");
        }
        claims.preflightCreateBatch(commit.claims());
        for (EscrowClaim claim : commit.claims()) {
            materialization.accept(
                    claims.getClaim(claim.claimId()) != null);
        }
        return materialization.result();
    }

    private void applyAuctionEscrowLifecycle(
            JournalRecord record,
            byte[] body
    ) {
        AuctionEscrowLifecycleEvent event =
                AuctionEscrowLifecycleEventCodec.decode(body);
        requireRecordIdentity(record, event.requestId());
        requireAuctionCreateCustodyCommitted(event);
        if (!(event instanceof AuctionEscrowLifecycleEvent.Commit value)) {
            auctionHouse.applyEscrowLifecycleCommitted(event);
            return;
        }
        AuctionEscrowCommit commit = value.commit();
        int step = 0;
        for (var transaction : commit.completedTransaction().stream()
                .toList()) {
            transactions.applyFoldedAtomicCompletionCommitted(transaction);
            atmWithdrawalFaults.afterMutation(step++);
        }
        for (var transaction : commit.ledgerTransaction().stream()
                .toList()) {
            boolean replayed = ledger.preflightCommitted(
                    transaction).replayed();
            if (!replayed) {
                requireAuctionWalletSnapshots(commit.walletSnapshots());
            }
            ledger.applyCommitted(transaction);
            atmWithdrawalFaults.afterMutation(step++);
        }
        for (EscrowClaim claim : commit.claims()) {
            claims.createCommitted(claim);
            atmWithdrawalFaults.afterMutation(step++);
        }
        auctionHouse.applyEscrowLifecycleCommitted(event);
    }

    private void requireAuctionCreateCustodyCommitted(
            AuctionEscrowLifecycleEvent event
    ) {
        if (!(event instanceof AuctionEscrowLifecycleEvent.Commit value)
                || value.completedIntent().isEmpty()) {
            return;
        }
        var intent = value.completedIntent().orElseThrow();
        var expectedIntent = intent.itemMutationIntent();
        var expectedReceipt = intent.plannedCustody().receipt();
        var entry = itemInventoryJournal.find(
                expectedIntent.token().requestId());
        if (entry.isPresent()) {
            var committed = entry.orElseThrow();
            if (committed.status() != ItemInventoryJournalStatus.COMMITTED
                    || !committed.intent().equals(expectedIntent)
                    || !committed.committedReceipt().filter(
                    expectedReceipt::equals).isPresent()) {
                throw new EscrowRuntimeException(
                        "Auction creation item custody is not committed");
            }
            return;
        }
        var tombstone = itemInventoryJournal.findTombstone(
                expectedIntent.token().requestId()).orElse(null);
        if (tombstone == null
                || tombstone.status()
                != ItemInventoryJournalStatus.COMMITTED
                || !tombstone.token().equals(expectedIntent.token())
                || !tombstone.terminalAt().equals(
                expectedReceipt.appliedAt())
                || !java.security.MessageDigest.isEqual(
                tombstone.terminalDigest(), expectedReceipt.digest())) {
            throw new EscrowRuntimeException(
                    "Auction creation item custody is not committed");
        }
    }

    private void requireAuctionWalletSnapshots(
            java.util.List<AuctionEscrowWalletSnapshot> snapshots
    ) {
        for (AuctionEscrowWalletSnapshot snapshot : snapshots) {
            long wallet = ledger.balance(
                    AuctionEscrowLedgerAccounts.wallet(
                            snapshot.playerId()));
            long debt = ledger.balance(
                    AuctionEscrowLedgerAccounts.debt(
                            snapshot.playerId()));
            long reserved = ledger.balance(new LedgerAccountId(
                    LedgerAccountType.PLAYER_RESERVED,
                    snapshot.playerId().toString()));
            if (wallet != snapshot.walletMinor()
                    || debt != snapshot.debtMinor()
                    || reserved != snapshot.reservedMinor()) {
                throw new EscrowRuntimeException(
                        "Auction wallet snapshot changed before commit");
            }
        }
    }

    private EscrowPreflightResult preflightBazaarMutation(
            UUID recordTransactionId,
            byte[] body
    ) {
        BazaarMutation mutation = BazaarMutationCodec.decode(body);
        requireRecordIdentity(recordTransactionId,
                mutation.mutationId());
        return result(bazaar.preflightCommitted(mutation).replayed());
    }

    private void applyBazaarMutation(
            JournalRecord record,
            byte[] body
    ) {
        BazaarMutation mutation = BazaarMutationCodec.decode(body);
        requireRecordIdentity(record, mutation.mutationId());
        bazaar.applyCommitted(mutation);
    }

    private EscrowPreflightResult preflightBazaarEscrowLifecycle(
            UUID recordTransactionId,
            byte[] body
    ) {
        BazaarEscrowLifecycleEvent event =
                BazaarEscrowLifecycleEventCodec.decode(body);
        requireRecordIdentity(recordTransactionId, event.requestId());
        requireBazaarCreateCustodyCommitted(event);
        RecoveryMaterialization materialization =
                new RecoveryMaterialization();
        materialization.accept(bazaar
                .preflightEscrowLifecycleCommitted(event).replayed());
        if (!(event instanceof BazaarEscrowLifecycleEvent.Commit value)) {
            return materialization.result();
        }
        BazaarEscrowCommit commit = value.commit();
        for (EscrowTransaction transaction
                : commit.completedTransactions()) {
            materialization.accept(transactions
                    .preflightFoldedAtomicCompletionCommitted(
                            transaction).replayed());
        }
        for (LedgerTransaction transaction
                : commit.ledgerTransactions()) {
            boolean replayed = ledger.preflightCommitted(
                    transaction).replayed();
            if (!replayed) {
                requireBazaarWalletSnapshot(value, transaction);
            }
            materialization.accept(replayed);
        }
        java.util.Set<UUID> expectedClaimIds = commit.claims().stream()
                .map(EscrowClaim::claimId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        for (EscrowTransaction transaction
                : commit.completedTransactions()) {
            java.util.List<EscrowClaim> existing =
                    claims.claimsForTransaction(
                            transaction.transactionId().value());
            if (!existing.stream().map(EscrowClaim::claimId)
                    .allMatch(expectedClaimIds::contains)) {
                throw new EscrowRuntimeException(
                        "Bazaar escrow has unexpected claim evidence");
            }
        }
        claims.preflightCreateBatch(commit.claims());
        for (EscrowClaim claim : commit.claims()) {
            materialization.accept(
                    claims.getClaim(claim.claimId()) != null);
        }
        return materialization.result();
    }

    private void applyBazaarEscrowLifecycle(
            JournalRecord record,
            byte[] body
    ) {
        BazaarEscrowLifecycleEvent event =
                BazaarEscrowLifecycleEventCodec.decode(body);
        requireRecordIdentity(record, event.requestId());
        requireBazaarCreateCustodyCommitted(event);
        if (!(event instanceof BazaarEscrowLifecycleEvent.Commit value)) {
            bazaar.applyEscrowLifecycleCommitted(event);
            return;
        }
        BazaarEscrowCommit commit = value.commit();
        int step = 0;
        for (EscrowTransaction transaction
                : commit.completedTransactions()) {
            transactions.applyFoldedAtomicCompletionCommitted(transaction);
            atmWithdrawalFaults.afterMutation(step++);
        }
        for (LedgerTransaction transaction
                : commit.ledgerTransactions()) {
            boolean replayed = ledger.preflightCommitted(
                    transaction).replayed();
            if (!replayed) {
                requireBazaarWalletSnapshot(value, transaction);
            }
            ledger.applyCommitted(transaction);
            atmWithdrawalFaults.afterMutation(step++);
        }
        for (EscrowClaim claim : commit.claims()) {
            claims.createCommitted(claim);
            atmWithdrawalFaults.afterMutation(step++);
        }
        bazaar.applyEscrowLifecycleCommitted(event);
    }

    private void requireBazaarCreateCustodyCommitted(
            BazaarEscrowLifecycleEvent event
    ) {
        if (!(event instanceof BazaarEscrowLifecycleEvent.Commit value)
                || value.completedIntent().isEmpty()) {
            return;
        }
        BazaarCreateEscrowIntent intent = value.completedIntent()
                .orElseThrow();
        if (intent.command().side() != BazaarOrderSide.SELL) {
            return;
        }
        var expectedIntent = intent.itemMutationIntent().orElseThrow();
        var expectedReceipt = intent.sellCustody().orElseThrow().receipt();
        var entry = itemInventoryJournal.find(
                expectedIntent.token().requestId());
        if (entry.isPresent()) {
            var committed = entry.orElseThrow();
            if (committed.status() != ItemInventoryJournalStatus.COMMITTED
                    || !committed.intent().equals(expectedIntent)
                    || !committed.committedReceipt().filter(
                    expectedReceipt::equals).isPresent()) {
                throw new EscrowRuntimeException(
                        "Bazaar creation item custody is not committed");
            }
            return;
        }
        var tombstone = itemInventoryJournal.findTombstone(
                expectedIntent.token().requestId()).orElse(null);
        if (tombstone == null
                || tombstone.status()
                != ItemInventoryJournalStatus.COMMITTED
                || !tombstone.token().equals(expectedIntent.token())
                || !tombstone.terminalAt().equals(
                expectedReceipt.appliedAt())
                || !java.security.MessageDigest.isEqual(
                tombstone.terminalDigest(), expectedReceipt.digest())) {
            throw new EscrowRuntimeException(
                    "Bazaar creation item custody is not committed");
        }
    }

    private void requireBazaarWalletSnapshot(
            BazaarEscrowLifecycleEvent.Commit event,
            LedgerTransaction transaction
    ) {
        if (event.completedIntent().isEmpty()) {
            return;
        }
        BazaarCreateEscrowIntent intent = event.completedIntent()
                .orElseThrow();
        if (intent.command().side() != BazaarOrderSide.BUY
                || !transaction.transactionId().equals(
                intent.command().activationTransactionId())) {
            return;
        }
        BazaarBuyFundingEvidence funding = intent.buyFunding()
                .orElseThrow();
        BazaarEscrowWalletSnapshot snapshot = funding.wallet();
        long current = ledger.balance(BazaarEscrowLedgerAccounts.wallet(
                snapshot.ownerId()));
        if (current != snapshot.walletMinor()) {
            throw new EscrowRuntimeException(
                    "Bazaar wallet snapshot changed before commit");
        }
    }

    private EscrowPreflightResult preflightPlayerShopEscrowLifecycle(
            UUID recordTransactionId,
            byte[] body
    ) {
        PlayerShopEscrowLifecycleEvent event =
                PlayerShopEscrowLifecycleEventCodec.decode(body);
        requireRecordIdentity(recordTransactionId, event.eventId());
        return result(playerShopEscrow.preflight(event)
                == PlayerShopEscrowSavedData.MutationDisposition.REPLAYED);
    }

    private void applyPlayerShopEscrowLifecycle(
            JournalRecord record,
            byte[] body
    ) {
        PlayerShopEscrowLifecycleEvent event =
                PlayerShopEscrowLifecycleEventCodec.decode(body);
        requireRecordIdentity(record, event.eventId());
        playerShopEscrow.apply(event);
    }

    private EscrowPreflightResult preflightExactItemClaimDelivery(
            UUID recordTransactionId,
            byte[] body
    ) {
        ExactItemClaimDeliveryCommit commit =
                ExactItemClaimDeliveryCommitCodec.decode(body);
        requireRecordIdentity(recordTransactionId, commit.requestId());
        EscrowClaim claim = requireClaim(commit.delivery());
        ExactItemClaimDeliveryPlanner.validate(claim, commit);
        boolean itemReplayed = itemInventoryJournal.preflightCommitted(
                commit.itemCommit()).replayed();
        ClaimAttemptResult delivery = claims.preflightDeliveryCommitted(
                commit.delivery().ownerId(), commit.delivery().claimId(),
                commit.delivery().requestKey(), commit.delivery().units(),
                commit.delivery().deliveredAt());
        requireDeliveredUnits(delivery, commit.delivery().units());
        if (delivery.replayed() && !itemReplayed) {
            throw new EscrowRuntimeException(
                    "Exact item claim inventory evidence is missing");
        }
        return result(itemReplayed && delivery.replayed());
    }

    private void applyExactItemClaimDelivery(
            JournalRecord record,
            byte[] body
    ) {
        ExactItemClaimDeliveryCommit commit =
                ExactItemClaimDeliveryCommitCodec.decode(body);
        requireRecordIdentity(record, commit.requestId());
        EscrowClaim claim = requireClaim(commit.delivery());
        ExactItemClaimDeliveryPlanner.validate(claim, commit);
        itemInventoryJournal.applyCommitted(commit.itemCommit());
        ClaimAttemptResult delivery = claims.deliverCommitted(
                commit.delivery().ownerId(), commit.delivery().claimId(),
                commit.delivery().requestKey(), commit.delivery().units(),
                commit.delivery().deliveredAt());
        requireDeliveredUnits(delivery, commit.delivery().units());
    }

    private EscrowPreflightResult
    preflightItemInventoryQuarantineAdministration(
            UUID recordTransactionId,
            byte[] body
    ) {
        ItemInventoryQuarantineAdministration administration =
                ItemInventoryQuarantineAdministrationCodec.decode(body);
        requireRecordIdentity(recordTransactionId,
                administration.commandId());
        requireQuarantineRefundEvidence(administration);
        ItemInventoryQuarantineAdministrationResult itemResult =
                itemInventoryJournal.preflightAdministration(
                        administration);
        boolean refundReplayed = true;
        if (administration.refundClaim().isPresent()) {
            EscrowClaim refund = administration.refundClaim().orElseThrow();
            refundReplayed = claims.getClaim(refund.claimId()) != null;
            claims.preflightCreateCommitted(refund);
        }
        if (refundReplayed && !itemResult.replayed()) {
            throw new EscrowRuntimeException(
                    "Item inventory refund exists without its review");
        }
        return result(itemResult.replayed() && refundReplayed);
    }

    private void applyItemInventoryQuarantineAdministration(
            JournalRecord record,
            byte[] body
    ) {
        ItemInventoryQuarantineAdministration administration =
                ItemInventoryQuarantineAdministrationCodec.decode(body);
        requireRecordIdentity(record, administration.commandId());
        requireQuarantineRefundEvidence(administration);
        itemInventoryJournal.applyAdministration(administration);
        administration.refundClaim().ifPresent(claims::createCommitted);
    }

    private void requireQuarantineRefundEvidence(
            ItemInventoryQuarantineAdministration administration
    ) {
        if (administration.refundClaim().isEmpty()) {
            return;
        }
        var inspection = itemInventoryJournal.inspectQuarantine(
                administration.requestId()).orElseThrow(() ->
                new EscrowRuntimeException(
                        "Item inventory quarantine is missing"));
        EscrowClaim refund = administration.refundClaim().orElseThrow();
        String expectedSource = "item.quarantine.refund."
                + administration.requestId() + "."
                + administration.commandId();
        if (!refund.transactionId().equals(
                inspection.entry().intent().token().transactionId())
                || !refund.sourceKey().equals(expectedSource)) {
            throw new EscrowRuntimeException(
                    "Item inventory quarantine refund evidence conflicts");
        }
    }

    private EscrowPreflightResult preflightItemInventoryJournalCompaction(
            UUID recordTransactionId,
            byte[] body
    ) {
        ItemInventoryJournalCompaction compaction =
                ItemInventoryJournalCompactionCodec.decode(body);
        requireRecordIdentity(recordTransactionId, compaction.commandId());
        return result(itemInventoryJournal.preflightCompaction(
                compaction).replayed());
    }

    private void applyItemInventoryJournalCompaction(
            JournalRecord record,
            byte[] body
    ) {
        ItemInventoryJournalCompaction compaction =
                ItemInventoryJournalCompactionCodec.decode(body);
        requireRecordIdentity(record, compaction.commandId());
        itemInventoryJournal.applyCompaction(compaction);
    }

    private EscrowPreflightResult preflightServerShopPurchase(
            UUID recordTransactionId,
            byte[] body
    ) {
        ServerShopPurchaseCommit commit =
                ServerShopPurchaseCommitCodec.decode(body);
        requireRecordIdentity(recordTransactionId, commit.requestId());
        RecoveryMaterialization materialization =
                new RecoveryMaterialization();
        materialization.accept(transactions
                .preflightFoldedAtomicCompletionCommitted(
                        commit.completedTransaction()).replayed());
        for (EscrowTransaction child
                : commit.completedLineTransactions()) {
            materialization.accept(transactions
                    .preflightFoldedAtomicCompletionCommitted(child)
                    .replayed());
        }
        boolean ledgerReplayed = ledger.preflightCommitted(
                commit.ledgerTransaction()).replayed();
        if (!ledgerReplayed) {
            requireServerShopWalletSnapshot(commit);
        }
        materialization.accept(ledgerReplayed);
        commit.physicalFunding().ifPresent(funding -> {
            ClaimAttemptResult result = claims.preflightDeliveryCommitted(
                    commit.playerId(), funding.claimId(),
                    ServerShopPurchaseCommit.physicalFundingDeliveryKey(
                            commit.requestId(), funding.claimId()),
                    funding.amountMinorUnits(), commit.completedTransaction()
                            .timestamps().createdAt());
            requireServerShopPhysicalFundingResult(commit, funding, result);
            materialization.accept(result.replayed());
        });
        java.util.List<StockCommandResult> stockResults =
                stock.preflightCommittedSequence(java.util.List.of(
                        commit.stockReservation(), commit.stockCommit()));
        requireServerShopStockResult(stockResults.get(0), false);
        requireServerShopStockResult(stockResults.get(1), true);
        for (StockCommandResult result : stockResults) {
            materialization.accept(result.replayed());
        }
        java.util.Set<UUID> expectedClaimIds = commit.itemClaims().stream()
                .map(EscrowClaim::claimId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        java.util.List<EscrowClaim> existing = claims.claimsForTransaction(
                commit.requestId());
        if (!existing.stream().map(EscrowClaim::claimId)
                .allMatch(expectedClaimIds::contains)) {
            throw new EscrowRuntimeException(
                    "Server shop purchase has unexpected claim evidence");
        }
        claims.preflightCreateBatch(commit.itemClaims());
        for (EscrowClaim claim : commit.itemClaims()) {
            materialization.accept(claims.getClaim(claim.claimId()) != null);
        }
        return materialization.result();
    }

    private void applyServerShopPurchase(
            JournalRecord record,
            byte[] body
    ) {
        ServerShopPurchaseCommit commit =
                ServerShopPurchaseCommitCodec.decode(body);
        requireRecordIdentity(record, commit.requestId());
        int step = 0;
        transactions.applyFoldedAtomicCompletionCommitted(
                commit.completedTransaction());
        atmWithdrawalFaults.afterMutation(step++);
        for (EscrowTransaction child
                : commit.completedLineTransactions()) {
            transactions.applyFoldedAtomicCompletionCommitted(child);
            atmWithdrawalFaults.afterMutation(step++);
        }
        ledger.applyCommitted(commit.ledgerTransaction());
        atmWithdrawalFaults.afterMutation(step++);
        commit.physicalFunding().ifPresent(funding -> {
            ClaimAttemptResult result = claims.deliverCommitted(
                    commit.playerId(), funding.claimId(),
                    ServerShopPurchaseCommit.physicalFundingDeliveryKey(
                            commit.requestId(), funding.claimId()),
                    funding.amountMinorUnits(), commit.completedTransaction()
                            .timestamps().createdAt());
            requireServerShopPhysicalFundingResult(commit, funding, result);
        });
        if (commit.physicalFunding().isPresent()) {
            atmWithdrawalFaults.afterMutation(step++);
        }
        requireServerShopStockResult(stock.applyCommitted(
                commit.stockReservation()), false);
        atmWithdrawalFaults.afterMutation(step++);
        requireServerShopStockResult(stock.applyCommitted(
                commit.stockCommit()), true);
        atmWithdrawalFaults.afterMutation(step++);
        for (EscrowClaim claim : commit.itemClaims()) {
            claims.createCommitted(claim);
            atmWithdrawalFaults.afterMutation(step++);
        }
    }

    private EscrowPreflightResult preflightServerShopFundingRelease(
            UUID recordTransactionId,
            byte[] body
    ) {
        ServerShopFundingRelease release =
                ServerShopFundingReleaseCodec.decode(body);
        requireRecordIdentity(recordTransactionId, release.releaseId());
        requireServerShopPurchaseAbsent(release.purchaseRequestId());
        requireServerShopFundingTransaction(release);
        EscrowClaim fundingClaim = claims.getClaim(
                release.fundingClaimId());
        requireServerShopFundingClaim(release, fundingClaim);

        boolean transactionReplayed = transactions
                .preflightFoldedAtomicCompletionCommitted(
                        release.completedTransaction()).replayed();
        boolean ledgerReplayed = ledger.preflightCommitted(
                release.ledgerTransaction()).replayed();
        if (!ledgerReplayed) {
            long fundingBalance = ledger.balance(
                    ServerShopPurchaseCommit.claimAccount(
                            release.fundingClaimId()));
            long refundBalance = ledger.balance(
                    ServerShopPurchaseCommit.claimAccount(
                            release.refundClaim().claimId()));
            if (fundingBalance != release.amountMinorUnits()
                    || refundBalance != 0L) {
                throw new EscrowRuntimeException(
                        "Server shop funding release balance changed");
            }
        }
        ClaimAttemptResult delivery = claims.preflightDeliveryCommitted(
                release.playerId(), release.fundingClaimId(),
                release.fundingClaimDelivery().requestKey(),
                release.amountMinorUnits(), release.releasedAt());
        requireServerShopFundingReleaseDelivery(release, delivery);
        java.util.List<EscrowClaim> releaseClaims =
                claims.claimsForTransaction(release.releaseId());
        if (releaseClaims.stream().anyMatch(claim ->
                !claim.claimId().equals(
                        release.refundClaim().claimId()))) {
            throw new EscrowRuntimeException(
                    "Server shop funding release has unexpected claims");
        }
        boolean refundReplayed = claims.getClaim(
                release.refundClaim().claimId()) != null;
        claims.preflightCreateCommitted(release.refundClaim());
        if (ledgerReplayed && !transactionReplayed
                || delivery.replayed() && !ledgerReplayed
                || refundReplayed && !delivery.replayed()) {
            throw new EscrowRuntimeException(
                    "Server shop funding release is out of order");
        }
        RecoveryMaterialization materialization =
                new RecoveryMaterialization();
        materialization.accept(transactionReplayed);
        materialization.accept(ledgerReplayed);
        materialization.accept(delivery.replayed());
        materialization.accept(refundReplayed);
        return materialization.result();
    }

    private void applyServerShopFundingRelease(
            JournalRecord record,
            byte[] body
    ) {
        ServerShopFundingRelease release =
                ServerShopFundingReleaseCodec.decode(body);
        requireRecordIdentity(record, release.releaseId());
        requireServerShopPurchaseAbsent(release.purchaseRequestId());
        requireServerShopFundingTransaction(release);
        requireServerShopFundingClaim(release,
                claims.getClaim(release.fundingClaimId()));
        int step = 0;
        transactions.applyFoldedAtomicCompletionCommitted(
                release.completedTransaction());
        atmWithdrawalFaults.afterMutation(step++);
        ledger.applyCommitted(release.ledgerTransaction());
        atmWithdrawalFaults.afterMutation(step++);
        ClaimAttemptResult delivery = claims.deliverCommitted(
                release.playerId(), release.fundingClaimId(),
                release.fundingClaimDelivery().requestKey(),
                release.amountMinorUnits(), release.releasedAt());
        requireServerShopFundingReleaseDelivery(release, delivery);
        atmWithdrawalFaults.afterMutation(step++);
        claims.createCommitted(release.refundClaim());
        atmWithdrawalFaults.afterMutation(step);
    }

    private void requireServerShopPurchaseAbsent(UUID purchaseRequestId) {
        if (transactions.getTransaction(new EscrowTransactionId(
                purchaseRequestId)) != null
                || ledger.wasApplied(purchaseRequestId)
                || !claims.claimsForTransaction(
                purchaseRequestId).isEmpty()
                || !stock.reservationsForTransaction(
                purchaseRequestId).isEmpty()) {
            throw new EscrowRuntimeException(
                    "Server shop purchase evidence blocks funding release");
        }
    }

    private static void requireServerShopFundingClaim(
            ServerShopFundingRelease release,
            EscrowClaim claim
    ) {
        if (claim == null
                || !claim.claimId().equals(release.fundingClaimId())
                || !claim.transactionId().equals(
                release.fundingTransactionId())
                || !claim.ownerId().equals(release.playerId())
                || claim.kind()
                != com.enviouse.futureshops.server.escrow.claim.ClaimKind
                .INTERNAL_ESCROW_MONEY
                || claim.originalUnits() != release.amountMinorUnits()
                || claim.payload().length != 0
                || claim.status()
                != com.enviouse.futureshops.server.escrow.claim.ClaimStatus
                .PENDING
                && claim.status()
                != com.enviouse.futureshops.server.escrow.claim.ClaimStatus
                .COMPLETED) {
            throw new EscrowRuntimeException(
                    "Server shop funding release claim conflicts");
        }
    }

    private void requireServerShopFundingTransaction(
            ServerShopFundingRelease release
    ) {
        EscrowTransaction transaction = transactions.getTransaction(
                new EscrowTransactionId(
                        release.fundingTransactionId()));
        if (transaction == null
                || EscrowCashDepositService.serverShopPurchaseBinding(
                transaction).filter(
                release.purchaseRequestId()::equals).isEmpty()
                || !ServerShopFundingReleaseService.depositHasOwner(
                transaction, release.playerId())) {
            throw new EscrowRuntimeException(
                    "Server shop funding release deposit binding conflicts");
        }
    }

    private static void requireServerShopFundingReleaseDelivery(
            ServerShopFundingRelease release,
            ClaimAttemptResult result
    ) {
        if (!result.claimId().equals(release.fundingClaimId())
                || !result.requestKey().equals(
                release.fundingClaimDelivery().requestKey())
                || result.deliveredUnits() != release.amountMinorUnits()
                || result.remainingUnits() != 0L
                || result.status()
                != com.enviouse.futureshops.server.escrow.claim.ClaimStatus
                .COMPLETED
                || !result.deliveredAt().equals(release.releasedAt())) {
            throw new EscrowRuntimeException(
                    "Server shop funding release delivery conflicts");
        }
    }

    private EscrowPreflightResult preflightServerShopSellLifecycle(
            UUID recordTransactionId,
            byte[] body
    ) {
        ServerShopSellLifecycleEvent event =
                ServerShopSellLifecycleEventCodec.decode(body);
        requireRecordIdentity(recordTransactionId, event.requestId());
        if (event instanceof ServerShopSellLifecycleEvent.Prepare value) {
            return result(serverShopIntents.preflightPrepareSell(
                    value.intent())
                    == ServerShopIntentSavedData.MutationDisposition
                    .REPLAYED);
        }
        if (event instanceof ServerShopSellLifecycleEvent.Abort value) {
            return result(serverShopIntents.preflightSellAbort(
                    value.expectedIntent(), value.terminalIntent())
                    == ServerShopIntentSavedData.MutationDisposition
                    .REPLAYED);
        }
        ServerShopSellLifecycleEvent.Commit value =
                (ServerShopSellLifecycleEvent.Commit) event;
        ServerShopSellCommit commit = value.commit();
        RecoveryMaterialization materialization =
                new RecoveryMaterialization();
        materialization.accept(serverShopIntents.preflightCompleteSell(
                value.completedIntent())
                == ServerShopIntentSavedData.MutationDisposition
                .REPLAYED);
        materialization.accept(transactions
                .preflightFoldedAtomicCompletionCommitted(
                        commit.completedTransaction()).replayed());
        boolean ledgerReplayed = ledger.preflightCommitted(
                commit.ledgerTransaction()).replayed();
        if (!ledgerReplayed) {
            requireServerShopSellWalletSnapshot(commit);
        }
        materialization.accept(ledgerReplayed);
        java.util.List<StockCommandResult> stockResults =
                stock.preflightCommittedSequence(java.util.List.of(
                        commit.stockReservation(), commit.stockCommit()));
        requireServerShopStockResult(stockResults.get(0), false);
        requireServerShopStockResult(stockResults.get(1), true);
        stockResults.forEach(result -> materialization.accept(
                result.replayed()));
        java.util.Set<UUID> expectedClaimIds = commit.overflowClaim()
                .stream().map(EscrowClaim::claimId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        java.util.List<EscrowClaim> existing =
                claims.claimsForTransaction(commit.requestId());
        if (!existing.stream().map(EscrowClaim::claimId)
                .allMatch(expectedClaimIds::contains)) {
            throw new EscrowRuntimeException(
                    "Server shop sell has unexpected claim evidence");
        }
        java.util.List<EscrowClaim> expectedClaims =
                commit.overflowClaim().stream().toList();
        claims.preflightCreateBatch(expectedClaims);
        for (EscrowClaim claim : expectedClaims) {
            materialization.accept(
                    claims.getClaim(claim.claimId()) != null);
        }
        return materialization.result();
    }

    private void applyServerShopSellLifecycle(
            JournalRecord record,
            byte[] body
    ) {
        ServerShopSellLifecycleEvent event =
                ServerShopSellLifecycleEventCodec.decode(body);
        requireRecordIdentity(record, event.requestId());
        if (event instanceof ServerShopSellLifecycleEvent.Prepare value) {
            serverShopIntents.applyPrepareSell(value.intent());
            return;
        }
        if (event instanceof ServerShopSellLifecycleEvent.Abort value) {
            serverShopIntents.applySellAbort(value.expectedIntent(),
                    value.terminalIntent());
            return;
        }
        ServerShopSellLifecycleEvent.Commit value =
                (ServerShopSellLifecycleEvent.Commit) event;
        ServerShopSellCommit commit = value.commit();
        int step = 0;
        transactions.applyFoldedAtomicCompletionCommitted(
                commit.completedTransaction());
        atmWithdrawalFaults.afterMutation(step++);
        ledger.applyCommitted(commit.ledgerTransaction());
        atmWithdrawalFaults.afterMutation(step++);
        requireServerShopStockResult(stock.applyCommitted(
                commit.stockReservation()), false);
        atmWithdrawalFaults.afterMutation(step++);
        requireServerShopStockResult(stock.applyCommitted(
                commit.stockCommit()), true);
        atmWithdrawalFaults.afterMutation(step++);
        for (EscrowClaim claim : commit.overflowClaim().stream().toList()) {
            claims.createCommitted(claim);
            atmWithdrawalFaults.afterMutation(step++);
        }
        serverShopIntents.applyCompleteSell(value.completedIntent());
    }

    private EscrowPreflightResult preflightServerShopBarterLifecycle(
            UUID recordTransactionId,
            byte[] body
    ) {
        ServerShopBarterLifecycleEvent event =
                ServerShopBarterLifecycleEventCodec.decode(body);
        requireRecordIdentity(recordTransactionId, event.requestId());
        if (event instanceof ServerShopBarterLifecycleEvent.Prepare value) {
            RecoveryMaterialization materialization =
                    new RecoveryMaterialization();
            materialization.accept(serverShopIntents
                    .preflightPrepareBarter(value.intent())
                    == ServerShopIntentSavedData.MutationDisposition
                    .REPLAYED);
            StockCommandResult stockResult = stock.preflightCommitted(
                    value.stockReservation());
            requireServerShopStockResult(stockResult, false);
            materialization.accept(stockResult.replayed());
            return materialization.result();
        }
        if (event instanceof ServerShopBarterLifecycleEvent.Abort value) {
            RecoveryMaterialization materialization =
                    new RecoveryMaterialization();
            materialization.accept(serverShopIntents
                    .preflightCompleteBarter(value.terminalIntent())
                    == ServerShopIntentSavedData.MutationDisposition
                    .REPLAYED);
            StockCommandResult stockResult = stock.preflightCommitted(
                    value.stockRelease());
            requireServerShopStockResult(stockResult, true);
            materialization.accept(stockResult.replayed());
            return materialization.result();
        }
        ServerShopBarterLifecycleEvent.Commit value =
                (ServerShopBarterLifecycleEvent.Commit) event;
        ServerShopBarterCommit commit = value.commit();
        RecoveryMaterialization materialization =
                new RecoveryMaterialization();
        materialization.accept(serverShopIntents
                .preflightCompleteBarter(value.completedIntent())
                == ServerShopIntentSavedData.MutationDisposition
                .REPLAYED);
        materialization.accept(transactions
                .preflightFoldedAtomicCompletionCommitted(
                        commit.completedTransaction()).replayed());
        java.util.List<StockCommandResult> stockResults =
                stock.preflightCommittedSequence(java.util.List.of(
                        commit.stockReservation(), commit.stockCommit()));
        requireServerShopStockResult(stockResults.get(0), false);
        requireServerShopStockResult(stockResults.get(1), true);
        stockResults.forEach(result -> materialization.accept(
                result.replayed()));
        java.util.Set<UUID> expectedClaimIds = commit.outputClaims()
                .stream().map(EscrowClaim::claimId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        java.util.List<EscrowClaim> existing =
                claims.claimsForTransaction(commit.requestId());
        if (!existing.stream().map(EscrowClaim::claimId)
                .allMatch(expectedClaimIds::contains)) {
            throw new EscrowRuntimeException(
                    "Server shop barter has unexpected claim evidence");
        }
        claims.preflightCreateBatch(commit.outputClaims());
        for (EscrowClaim claim : commit.outputClaims()) {
            materialization.accept(
                    claims.getClaim(claim.claimId()) != null);
        }
        return materialization.result();
    }

    private void applyServerShopBarterLifecycle(
            JournalRecord record,
            byte[] body
    ) {
        ServerShopBarterLifecycleEvent event =
                ServerShopBarterLifecycleEventCodec.decode(body);
        requireRecordIdentity(record, event.requestId());
        if (event instanceof ServerShopBarterLifecycleEvent.Prepare value) {
            int step = 0;
            requireServerShopStockResult(stock.applyCommitted(
                    value.stockReservation()), false);
            atmWithdrawalFaults.afterMutation(step);
            serverShopIntents.applyPrepareBarter(value.intent());
            return;
        }
        if (event instanceof ServerShopBarterLifecycleEvent.Abort value) {
            int step = 0;
            requireServerShopStockResult(stock.applyCommitted(
                    value.stockRelease()), true);
            atmWithdrawalFaults.afterMutation(step);
            serverShopIntents.applyCompleteBarter(
                    value.terminalIntent());
            return;
        }
        ServerShopBarterLifecycleEvent.Commit value =
                (ServerShopBarterLifecycleEvent.Commit) event;
        ServerShopBarterCommit commit = value.commit();
        int step = 0;
        transactions.applyFoldedAtomicCompletionCommitted(
                commit.completedTransaction());
        atmWithdrawalFaults.afterMutation(step++);
        requireServerShopStockResult(stock.applyCommitted(
                commit.stockReservation()), false);
        atmWithdrawalFaults.afterMutation(step++);
        requireServerShopStockResult(stock.applyCommitted(
                commit.stockCommit()), true);
        atmWithdrawalFaults.afterMutation(step++);
        for (EscrowClaim claim : commit.outputClaims()) {
            claims.createCommitted(claim);
            atmWithdrawalFaults.afterMutation(step++);
        }
        serverShopIntents.applyCompleteBarter(
                value.completedIntent());
    }

    private void requireServerShopSellWalletSnapshot(
            ServerShopSellCommit commit
    ) {
        long wallet = ledger.balance(ServerShopSellCommit.walletAccount(
                commit.playerId()));
        long debt = ledger.balance(ServerShopSellCommit.debtAccount(
                commit.playerId()));
        long reserved = ledger.balance(
                ServerShopSellCommit.reservedAccount(commit.playerId()));
        if (wallet != commit.walletBeforeMinorUnits()
                || debt != commit.debtBeforeMinorUnits()
                || reserved != commit.reservedBeforeMinorUnits()) {
            throw new EscrowRuntimeException(
                    "Server shop sell wallet snapshot changed before commit");
        }
    }

    private void requireServerShopWalletSnapshot(
            ServerShopPurchaseCommit commit
    ) {
        if (commit.physicalFunding().isPresent()) {
            ServerShopPurchaseCommit.PhysicalFunding funding =
                    commit.physicalFunding().orElseThrow();
            EscrowClaim claim = claims.getClaim(funding.claimId());
            long claimBalance = ledger.balance(
                    ServerShopPurchaseCommit.claimAccount(funding.claimId()));
            if (claim == null
                    || !claim.transactionId().equals(funding.transactionId())
                    || !claim.ownerId().equals(commit.playerId())
                    || claim.kind()
                    != com.enviouse.futureshops.server.escrow.claim.ClaimKind
                    .INTERNAL_ESCROW_MONEY
                    || claim.status()
                    != com.enviouse.futureshops.server.escrow.claim.ClaimStatus
                    .PENDING
                    || claim.originalUnits() != funding.amountMinorUnits()
                    || claim.remainingUnits() != funding.amountMinorUnits()
                    || claimBalance != funding.amountMinorUnits()) {
                throw new EscrowRuntimeException(
                        "Server shop physical funding snapshot changed before commit");
            }
            return;
        }
        long wallet = ledger.balance(ServerShopPurchaseCommit.walletAccount(
                commit.playerId()));
        long debt = ledger.balance(ServerShopPurchaseCommit.debtAccount(
                commit.playerId()));
        if (wallet != commit.walletBeforeMinorUnits()
                || debt != commit.debtBeforeMinorUnits()) {
            throw new EscrowRuntimeException(
                    "Server shop wallet snapshot changed before commit");
        }
    }

    private static void requireServerShopPhysicalFundingResult(
            ServerShopPurchaseCommit commit,
            ServerShopPurchaseCommit.PhysicalFunding funding,
            ClaimAttemptResult result
    ) {
        if (!result.claimId().equals(funding.claimId())
                || result.deliveredUnits() != funding.amountMinorUnits()
                || result.remainingUnits() != 0L
                || result.status()
                != com.enviouse.futureshops.server.escrow.claim.ClaimStatus
                .COMPLETED
                || !result.deliveredAt().equals(commit.completedTransaction()
                .timestamps().createdAt())) {
            throw new EscrowRuntimeException(
                    "Server shop physical funding claim was not consumed");
        }
    }

    private static void requireServerShopStockResult(
            StockCommandResult result,
            boolean resolution
    ) {
        StockMutationOutcome outcome = result.receipt().outcome();
        if (outcome != StockMutationOutcome.APPLIED
                && !(resolution
                && outcome == StockMutationOutcome.UNCHANGED)) {
            throw new EscrowRuntimeException(resolution
                    ? "Server shop stock commit was not applied"
                    : "Server shop stock reservation was not applied");
        }
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
        if (!EscrowMoneyClaimService.isMonetaryClaim(claim)) {
            throw new EscrowRuntimeException("Money claim settlement references a non money claim");
        }
        requireRecordIdentity(record, settlement.requestId());
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
        if (!EscrowMoneyClaimService.isMonetaryClaim(claim)) {
            throw new EscrowRuntimeException("Money claim settlement references a non money claim");
        }
        requireRecordIdentity(recordTransactionId, settlement.requestId());
        boolean ledgerReplay = ledger.preflightCommitted(
                settlement.ledgerTransaction()).replayed();
        ClaimAttemptResult claimResult = claims.preflightDeliveryCommitted(
                delivery.ownerId(), delivery.claimId(), delivery.requestKey(), delivery.units(),
                delivery.deliveredAt());
        requireDeliveredUnits(claimResult, delivery.units());
        CompositeMaterialization materialization =
                new CompositeMaterialization();
        materialization.accept(ledgerReplay);
        materialization.accept(claimResult.replayed());
        EscrowPreflightResult result = materialization.result();
        if (result == EscrowPreflightResult.APPLY
                && !settlement.legacyFormat()) {
            requireMoneyClaimSnapshot(settlement, claim);
        }
        return result;
    }

    private void requireMoneyClaimSnapshot(
            MoneyClaimSettlement settlement,
            EscrowClaim claim
    ) {
        long wallet = ledger.balance(PlayerPaymentCommit.walletAccount(
                settlement.delivery().ownerId()));
        long debt = ledger.balance(PlayerPaymentCommit.debtAccount(
                settlement.delivery().ownerId()));
        long reserved = ledger.balance(PlayerPaymentCommit.reservedAccount(
                settlement.delivery().ownerId()));
        if (wallet != settlement.walletBeforeMinorUnits()
                || debt != settlement.debtBeforeMinorUnits()
                || reserved != settlement.reservedBeforeMinorUnits()
                || claim.remainingUnits()
                != settlement.claimRemainingBeforeUnits()) {
            throw new EscrowRuntimeException(
                    "Money claim settlement snapshot changed before commit");
        }
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

    private EscrowPreflightResult preflightPlayerPayment(
            UUID recordTransactionId,
            byte[] body
    ) {
        PlayerPaymentCommit commit = PlayerPaymentCommitCodec.decode(body);
        requireRecordIdentity(recordTransactionId, commit.transactionId());
        PlayerPaymentConservationValidator.validate(commit);
        java.util.List<EscrowClaim> existingClaims =
                claims.claimsForTransaction(commit.transactionId());
        if (commit.overflowClaim().isEmpty()) {
            if (!existingClaims.isEmpty()) {
                throw new EscrowRuntimeException(
                        "Player payment has unexpected claim evidence");
            }
        } else if (!existingClaims.isEmpty()
                && !existingClaims.equals(java.util.List.of(
                commit.overflowClaim().orElseThrow()))) {
            throw new EscrowRuntimeException(
                    "Player payment claim evidence conflicts");
        }
        boolean ledgerReplayed = ledger.preflightCommitted(
                commit.ledgerTransaction()).replayed();
        if (!ledgerReplayed) {
            requirePaymentSnapshot(commit);
        }
        CompositeMaterialization materialization =
                new CompositeMaterialization();
        materialization.accept(transactions
                .preflightFoldedAtomicCompletionCommitted(
                        commit.completedTransaction()).replayed());
        materialization.accept(ledgerReplayed);
        if (commit.overflowClaim().isPresent()) {
            EscrowClaim claim = commit.overflowClaim().orElseThrow();
            boolean claimReplayed = claims.getClaim(claim.claimId()) != null;
            claims.preflightCreateCommitted(claim);
            materialization.accept(claimReplayed);
        }
        return materialization.result();
    }

    private void applyPlayerPayment(JournalRecord record, byte[] body) {
        PlayerPaymentCommit commit = PlayerPaymentCommitCodec.decode(body);
        requireRecordIdentity(record, commit.transactionId());
        int step = 0;
        transactions.applyFoldedAtomicCompletionCommitted(
                commit.completedTransaction());
        atmWithdrawalFaults.afterMutation(step++);
        ledger.applyCommitted(commit.ledgerTransaction());
        atmWithdrawalFaults.afterMutation(step++);
        if (commit.overflowClaim().isPresent()) {
            claims.createCommitted(commit.overflowClaim().orElseThrow());
            atmWithdrawalFaults.afterMutation(step);
        }
    }

    private void requirePaymentSnapshot(PlayerPaymentCommit commit) {
        long payerWallet = ledger.balance(PlayerPaymentCommit.walletAccount(
                commit.payerId()));
        long payerDebt = ledger.balance(PlayerPaymentCommit.debtAccount(
                commit.payerId()));
        long recipientWallet = ledger.balance(
                PlayerPaymentCommit.walletAccount(commit.recipientId()));
        long recipientDebt = ledger.balance(
                PlayerPaymentCommit.debtAccount(commit.recipientId()));
        long recipientReserved = ledger.balance(
                PlayerPaymentCommit.reservedAccount(commit.recipientId()));
        if (payerWallet != commit.payerWalletBeforeMinorUnits()
                || payerDebt != commit.payerDebtBeforeMinorUnits()
                || recipientWallet
                != commit.recipientWalletBeforeMinorUnits()
                || recipientDebt
                != commit.recipientDebtBeforeMinorUnits()
                || recipientReserved
                != commit.recipientReservedBeforeMinorUnits()) {
            throw new EscrowRuntimeException(
                    "Player payment wallet snapshot changed before commit");
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

    private static final class RecoveryMaterialization {
        private boolean component;
        private boolean fresh;

        private void accept(boolean replayed) {
            component = true;
            fresh |= !replayed;
        }

        private EscrowPreflightResult result() {
            if (!component) {
                throw new EscrowRuntimeException(
                        "Escrow recovery composite event has no components");
            }
            return fresh ? EscrowPreflightResult.APPLY
                    : EscrowPreflightResult.REPLAY;
        }
    }
}

package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.admin.EscrowAdministrativeAuditSavedData;
import com.enviouse.futureshops.server.escrow.claim.ClaimAttemptResult;
import com.enviouse.futureshops.server.escrow.claim.ClaimKind;
import com.enviouse.futureshops.server.escrow.claim.ClaimSavedData;
import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.custody.CustodySavedData;
import com.enviouse.futureshops.server.escrow.journal.JournalRecord;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountId;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountType;
import com.enviouse.futureshops.server.escrow.ledger.LedgerLeg;
import com.enviouse.futureshops.server.escrow.ledger.LedgerSavedData;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintSavedData;
import com.enviouse.futureshops.server.escrow.model.EscrowAssetLot;
import com.enviouse.futureshops.server.escrow.model.EscrowAssetLotType;
import com.enviouse.futureshops.server.escrow.model.EscrowOperation;
import com.enviouse.futureshops.server.escrow.model.EscrowParticipant;
import com.enviouse.futureshops.server.escrow.model.EscrowParticipantRole;
import com.enviouse.futureshops.server.escrow.model.EscrowParty;
import com.enviouse.futureshops.server.escrow.model.EscrowProtectionLevel;
import com.enviouse.futureshops.server.escrow.model.EscrowRequestKey;
import com.enviouse.futureshops.server.escrow.model.EscrowState;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;
import com.enviouse.futureshops.server.escrow.model.EscrowTransactionId;
import com.enviouse.futureshops.server.escrow.model.MoneyAmount;
import com.enviouse.futureshops.server.escrow.stock.StockReservation;
import com.enviouse.futureshops.server.escrow.store.EscrowTransactionSavedData;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerShopFundingReleaseCompositeTest {
    private static final Instant NOW = Instant.parse(
            "2026-07-17T20:00:00Z");
    private static final long AMOUNT = 125L;

    @Test
    void releaseAppliesOnceAndExactRetryCannotReopenFunding() {
        Fixture fixture = Fixture.seeded();
        AtomicInteger commits = new AtomicInteger();
        ServerShopFundingReleaseService.Backend backend =
                fixture.backend(commits);

        ServerShopFundingReleaseService.Result first =
                ServerShopFundingReleaseService.release(
                        fixture.playerId(), fixture.funding(), backend);
        ServerShopFundingReleaseService.Result retry =
                ServerShopFundingReleaseService.release(
                        fixture.playerId(), fixture.funding(), backend);

        assertTrue(first.successful());
        assertTrue(retry.successful());
        assertTrue(retry.replayed());
        assertEquals(1, commits.get());
        assertMaterialized(fixture, first.releaseId());
    }

    @Test
    void crashAfterEveryMutationConvergesDuringWalReplay() {
        for (int failedStep = 0; failedStep < 4; failedStep++) {
            Fixture fixture = Fixture.seeded();
            ServerShopFundingRelease release = fixture.release(NOW);
            EscrowJournalEvent event = event(release);
            AtomicBoolean failed = new AtomicBoolean();
            int selected = failedStep;
            EscrowSavedDataMutationApplier crashing = fixture.applier(
                    step -> {
                        if (step == selected
                                && failed.compareAndSet(false, true)) {
                            throw new IllegalStateException(
                                    "Simulated funding release crash");
                        }
                    });

            assertThrows(IllegalStateException.class,
                    () -> crashing.apply(record(release, event), event));
            assertTrue(failed.get());
            EscrowSavedDataMutationApplier recovery = fixture.applier(
                    AtmWithdrawalApplyFaultInjector.NONE);
            recovery.apply(record(release, event), event);

            assertEquals(EscrowPreflightResult.REPLAY,
                    recovery.preflight(release.releaseId(), event));
            assertMaterialized(fixture, release.releaseId());
        }
    }

    @Test
    void purchaseEvidenceAndUnboundDepositsFailClosed() {
        Fixture purchase = Fixture.seeded();
        purchase.transactions().applyFoldedAtomicCompletionCommitted(
                terminalTransaction(purchase.funding()
                        .purchaseRequestId(), purchase.playerId(),
                        "purchase evidence"));
        ServerShopFundingRelease release = purchase.release(NOW);
        assertThrows(EscrowRuntimeException.class,
                () -> purchase.applier(
                        AtmWithdrawalApplyFaultInjector.NONE)
                        .preflight(release.releaseId(), event(release)));

        Fixture unbound = Fixture.seeded(false);
        ServerShopFundingRelease unboundRelease = unbound.release(NOW);
        assertThrows(EscrowRuntimeException.class,
                () -> unbound.applier(
                        AtmWithdrawalApplyFaultInjector.NONE)
                        .preflight(unboundRelease.releaseId(),
                                event(unboundRelease)));
    }

    @Test
    void boundDepositIsSelfDescribingForStartupRecovery() {
        Fixture fixture = Fixture.seeded();
        EscrowTransaction deposit = fixture.transactions().getTransaction(
                new EscrowTransactionId(fixture.funding()
                        .transactionId()));

        assertEquals(Optional.of(
                        fixture.funding().purchaseRequestId()),
                EscrowCashDepositService.serverShopPurchaseBinding(
                        deposit));
        assertEquals(Optional.of(fixture.funding()),
                ServerShopFundingReleaseService.startupCandidate(deposit,
                        fixture.claims().claimsForTransaction(
                                fixture.funding().transactionId()), false,
                        (player, funding) -> false));
        assertThrows(EscrowRuntimeException.class,
                () -> ServerShopFundingReleaseService.startupCandidate(
                        deposit, fixture.claims().claimsForTransaction(
                                fixture.funding().transactionId()), true,
                        (player, funding) -> false));

        assertThrows(EscrowRuntimeException.class,
                () -> ServerShopFundingReleaseService.startupCandidate(
                        deposit, List.of(), false,
                        (player, funding) -> false));
        EscrowClaim original = fixture.claims().getClaim(
                fixture.funding().claimId());
        EscrowClaim partial = new EscrowClaim(original.claimId(),
                original.transactionId(), original.ownerId(),
                original.sourceKey(), original.kind(),
                original.originalUnits(), 1L, original.payload(),
                ClaimStatus.PARTIALLY_DELIVERED, original.label(),
                original.createdAt(), original.updatedAt());
        assertThrows(EscrowRuntimeException.class,
                () -> ServerShopFundingReleaseService.startupCandidate(
                        deposit, List.of(partial), false,
                        (player, funding) -> false));
        EscrowClaim quarantined = original.quarantine(NOW);
        assertThrows(EscrowRuntimeException.class,
                () -> ServerShopFundingReleaseService.startupCandidate(
                        deposit, List.of(quarantined), false,
                        (player, funding) -> false));
        EscrowClaim completed = original.deliver(
                original.originalUnits(), NOW);
        assertThrows(EscrowRuntimeException.class,
                () -> ServerShopFundingReleaseService.startupCandidate(
                        deposit, List.of(completed), false,
                        (player, funding) -> false));
        assertEquals(Optional.empty(),
                ServerShopFundingReleaseService.startupCandidate(deposit,
                        List.of(completed), false,
                        (player, funding) -> true));
    }

    private static EscrowJournalEvent event(
            ServerShopFundingRelease release
    ) {
        return new EscrowJournalEvent(
                EscrowJournalEventType.SERVER_SHOP_FUNDING_RELEASE,
                ServerShopFundingReleaseCodec.encode(release));
    }

    private static JournalRecord record(
            ServerShopFundingRelease release,
            EscrowJournalEvent event
    ) {
        return new JournalRecord(2L, release.releaseId(),
                EscrowStepIds.forEvent(release.releaseId(), event),
                EscrowJournalEventCodec.encode(event));
    }

    private static void assertMaterialized(
            Fixture fixture,
            UUID releaseId
    ) {
        ServerShopFundingRelease release = fixture.releaseById(releaseId);
        EscrowClaim funding = fixture.claims().getClaim(
                fixture.funding().claimId());
        EscrowClaim refund = fixture.claims().getClaim(
                release.refundClaim().claimId());
        assertEquals(ClaimStatus.COMPLETED, funding.status());
        assertEquals(0L, funding.remainingUnits());
        assertEquals(release.refundClaim(), refund);
        assertEquals(0L, fixture.ledger().balance(
                ServerShopPurchaseCommit.claimAccount(
                        fixture.funding().claimId())));
        assertEquals(AMOUNT, fixture.ledger().balance(
                ServerShopPurchaseCommit.claimAccount(
                        release.refundClaim().claimId())));
        assertEquals(EscrowState.COMPLETED,
                fixture.transactions().getTransaction(
                        new EscrowTransactionId(releaseId)).state());
        ClaimAttemptResult attempt = fixture.claims().attempt(
                release.fundingClaimDelivery().requestKey()).orElseThrow();
        assertEquals(AMOUNT, attempt.deliveredUnits());
    }

    private static EscrowTransaction terminalTransaction(
            UUID transactionId,
            UUID playerId,
            String requestKey
    ) {
        EscrowParty player = EscrowParty.player(playerId);
        EscrowParty system = EscrowParty.system("cash custody");
        EscrowTransaction created = EscrowTransaction.create(
                new EscrowTransactionId(transactionId), Optional.empty(),
                new EscrowRequestKey(requestKey),
                EscrowOperation.CURRENCY_DEPOSIT, Set.of(
                new EscrowParticipant(player, Set.of(
                        EscrowParticipantRole.INITIATOR,
                        EscrowParticipantRole.RECIPIENT)),
                new EscrowParticipant(system, Set.of(
                        EscrowParticipantRole.PAYER,
                        EscrowParticipantRole.CUSTODIAN))), List.of(
                new EscrowAssetLot(UUID.randomUUID(),
                        EscrowAssetLotType.WALLET_MONEY,
                        EscrowProtectionLevel.PROTECTED, system, player,
                        1L, Optional.of(new MoneyAmount(
                        ServerShopPurchaseCommit.CURRENCY_ID, AMOUNT)),
                        new byte[0], Map.of("deposit_mode",
                        "INTERNAL_ESCROW"))), NOW, 0L,
                Optional.empty());
        return created.transitionTo(EscrowState.VALIDATED, NOW)
                .transitionTo(EscrowState.HOLDING, NOW)
                .transitionTo(EscrowState.HELD, NOW)
                .transitionTo(EscrowState.COMMIT_DECIDED, NOW)
                .transitionTo(EscrowState.COMMITTED, NOW)
                .transitionTo(EscrowState.CLAIMS_CREATED, NOW)
                .transitionTo(EscrowState.COMPLETED, NOW);
    }

    private record Fixture(
            UUID playerId,
            ServerShopPurchaseCommit.PhysicalFunding funding,
            EscrowTransactionSavedData transactions,
            LedgerSavedData ledger,
            ClaimSavedData claims,
            EscrowAdministrativeAuditSavedData audit,
            CustodySavedData custody,
            ProtectedMintSavedData mints,
            Map<UUID, ServerShopFundingRelease> releases
    ) {
        private static Fixture seeded() {
            return seeded(true);
        }

        private static Fixture seeded(boolean bound) {
            UUID playerId = UUID.randomUUID();
            UUID purchaseRequestId = UUID.randomUUID();
            UUID fundingRequestId = ServerShopFundingRelease
                    .fundingRequestId(purchaseRequestId);
            UUID fundingTransactionId = EscrowCashDepositService
                    .transactionIdForRequest(playerId, fundingRequestId);
            UUID fundingClaimId = UUID.randomUUID();
            String key = bound
                    ? "cash.deposit." + fundingRequestId + "."
                    + "0".repeat(64) + ".shop." + purchaseRequestId
                    : "cash.deposit." + fundingRequestId + "."
                    + "0".repeat(64);
            EscrowTransaction deposit = terminalTransaction(
                    fundingTransactionId, playerId, key);
            EscrowTransactionSavedData transactions =
                    new EscrowTransactionSavedData();
            LedgerSavedData ledger = new LedgerSavedData();
            ClaimSavedData claims = new ClaimSavedData();
            transactions.applyFoldedAtomicCompletionCommitted(deposit);
            EscrowClaim claim = new EscrowClaim(fundingClaimId,
                    fundingTransactionId, playerId,
                    "server.shop.test.funding." + fundingClaimId,
                    ClaimKind.INTERNAL_ESCROW_MONEY, AMOUNT, AMOUNT,
                    new byte[0], ClaimStatus.PENDING,
                    "Internal physical funding", NOW, NOW);
            claims.createCommitted(claim);
            ledger.applyCommitted(new LedgerTransaction(UUID.randomUUID(),
                    "server.shop.test.funding." + fundingClaimId,
                    "Fund claim", List.of(
                    new LedgerLeg(LedgerAccountId.system(
                            LedgerAccountType.ADMIN_SOURCE), -AMOUNT),
                    new LedgerLeg(ServerShopPurchaseCommit.claimAccount(
                            fundingClaimId), AMOUNT))));
            return new Fixture(playerId,
                    new ServerShopPurchaseCommit.PhysicalFunding(
                            purchaseRequestId, fundingTransactionId,
                            fundingClaimId, AMOUNT), transactions, ledger,
                    claims, new EscrowAdministrativeAuditSavedData(),
                    new CustodySavedData(), new ProtectedMintSavedData(),
                    new java.util.HashMap<>());
        }

        private ServerShopFundingRelease release(Instant at) {
            ServerShopFundingRelease release =
                    ServerShopFundingRelease.create(playerId, funding, at);
            releases.put(release.releaseId(), release);
            return release;
        }

        private ServerShopFundingRelease releaseById(UUID releaseId) {
            ServerShopFundingRelease known = releases.get(releaseId);
            if (known != null) {
                return known;
            }
            EscrowTransaction transaction = transactions.getTransaction(
                    new EscrowTransactionId(releaseId));
            ServerShopFundingRelease release =
                    ServerShopFundingRelease.create(playerId, funding,
                            transaction.timestamps().createdAt());
            releases.put(releaseId, release);
            return release;
        }

        private EscrowSavedDataMutationApplier applier(
                AtmWithdrawalApplyFaultInjector faults
        ) {
            return new EscrowSavedDataMutationApplier(transactions, ledger,
                    claims, audit, custody, mints,
                    MaintenanceRuntimeMutationHandler.unavailable(),
                    faults);
        }

        private ServerShopFundingReleaseService.Backend backend(
                AtomicInteger commits
        ) {
            return new ServerShopFundingReleaseService.Backend() {
                @Override
                public Optional<EscrowTransaction> transaction(UUID id) {
                    return Optional.ofNullable(transactions.getTransaction(
                            new EscrowTransactionId(id)));
                }

                @Override
                public Optional<LedgerTransaction> ledgerTransaction(
                        UUID id
                ) {
                    return ledger.transactionReceipt(id)
                            .map(value -> value.transaction());
                }

                @Override
                public Optional<EscrowClaim> claim(UUID id) {
                    return Optional.ofNullable(claims.getClaim(id));
                }

                @Override
                public List<EscrowClaim> claimsForTransaction(UUID id) {
                    return claims.claimsForTransaction(id);
                }

                @Override
                public Optional<ClaimAttemptResult> claimAttempt(
                        String requestKey
                ) {
                    return claims.attempt(requestKey);
                }

                @Override
                public List<StockReservation> stockReservations(UUID id) {
                    return List.of();
                }

                @Override
                public long ledgerBalance(LedgerAccountId account) {
                    return ledger.balance(account);
                }

                @Override
                public EscrowCommitResult commit(
                        ServerShopFundingRelease release
                ) {
                    commits.incrementAndGet();
                    releases.put(release.releaseId(), release);
                    EscrowJournalEvent event = event(release);
                    EscrowSavedDataMutationApplier applier = applier(
                            AtmWithdrawalApplyFaultInjector.NONE);
                    if (applier.preflight(release.releaseId(), event)
                            == EscrowPreflightResult.REPLAY) {
                        return EscrowCommitResult.replay();
                    }
                    JournalRecord record = record(release, event);
                    applier.apply(record, event);
                    return EscrowCommitResult.applied(record);
                }
            };
        }
    }
}

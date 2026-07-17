package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.claim.ClaimKind;
import com.enviouse.futureshops.server.escrow.claim.ClaimSavedData;
import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountId;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountType;
import com.enviouse.futureshops.server.escrow.ledger.LedgerLeg;
import com.enviouse.futureshops.server.escrow.ledger.LedgerSavedData;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;
import com.enviouse.futureshops.server.escrow.store.EscrowTransactionSavedData;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MoneyClaimSettlementInvariantTest {
    @Test
    void factorySealsTheExactDebtFirstSplit() {
        Stores stores = Stores.seeded();

        assertEquals(List.of(
                        new LedgerLeg(stores.claimAccount(), -150L),
                        new LedgerLeg(PlayerPaymentCommit.debtAccount(
                                stores.ownerId()), 50L),
                        new LedgerLeg(PlayerPaymentCommit.walletAccount(
                                stores.ownerId()), 100L)),
                stores.settlement().ledgerTransaction().legs());
    }

    @Test
    void rejectsWrongSplitOverLimitAndZeroDelivery() {
        Stores stores = Stores.seeded();
        MoneyClaimSettlement valid = stores.settlement();
        LedgerTransaction wrongSplit = new LedgerTransaction(
                valid.requestId(), valid.delivery().requestKey(),
                MoneyClaimSettlement.LEDGER_REASON, List.of(
                new LedgerLeg(stores.claimAccount(), -150L),
                new LedgerLeg(PlayerPaymentCommit.debtAccount(
                        stores.ownerId()), 40L),
                new LedgerLeg(PlayerPaymentCommit.walletAccount(
                        stores.ownerId()), 110L)));

        assertThrows(IllegalArgumentException.class,
                () -> new MoneyClaimSettlement(
                        valid.requestId(), 0L, -50L, 0L,
                        200L, 100L, 1L,
                        valid.delivery(), wrongSplit));
        assertThrows(IllegalArgumentException.class,
                () -> MoneyClaimSettlement.create(
                        UUID.randomUUID(), stores.ownerId(),
                        stores.claimId(), 100L, 0L, 0L,
                        200L, 100L, 1L,
                        PlayerPaymentTestFixtures.NOW.plusSeconds(1)));
        assertThrows(IllegalArgumentException.class,
                () -> MoneyClaimSettlement.create(
                        UUID.randomUUID(), stores.ownerId(),
                        stores.claimId(), 0L, 0L, 0L,
                        0L, 100L, 1L,
                        PlayerPaymentTestFixtures.NOW.plusSeconds(1)));
    }

    @Test
    void supportsTheMinimumDebtWithoutOverflow() {
        UUID ownerId = UUID.randomUUID();
        UUID claimId = UUID.randomUUID();
        MoneyClaimSettlement settlement = MoneyClaimSettlement.create(
                UUID.randomUUID(), ownerId, claimId,
                0L, Long.MIN_VALUE, 0L, Long.MAX_VALUE,
                Long.MAX_VALUE, 1L,
                PlayerPaymentTestFixtures.NOW.plusSeconds(1));

        assertEquals(Long.MAX_VALUE, settlement.deliveredUnits());
        assertEquals(List.of(
                        new LedgerLeg(new LedgerAccountId(
                                LedgerAccountType.PLAYER_CLAIM,
                                claimId.toString()), -Long.MAX_VALUE),
                        new LedgerLeg(PlayerPaymentCommit.debtAccount(
                                ownerId), Long.MAX_VALUE)),
                settlement.ledgerTransaction().legs());
    }

    @Test
    void preflightRejectsStaleSnapshotAndRequestMismatch() {
        Stores stores = Stores.seeded();
        MoneyClaimSettlement stale = MoneyClaimSettlement.create(
                UUID.randomUUID(), stores.ownerId(), stores.claimId(),
                0L, -40L, 0L, 200L, 100L, 1L,
                PlayerPaymentTestFixtures.NOW.plusSeconds(1));
        EscrowSavedDataMutationApplier applier = stores.applier();

        assertThrows(EscrowRuntimeException.class,
                () -> applier.preflight(stale.requestId(), event(stale)));
        assertThrows(EscrowRuntimeException.class,
                () -> applier.preflight(UUID.randomUUID(),
                        event(stores.settlement())));
    }

    @Test
    void everyPartialMaterializationFailsClosed() {
        Stores ledgerOnly = Stores.seeded();
        ledgerOnly.ledger().applyCommitted(
                ledgerOnly.settlement().ledgerTransaction());
        assertThrows(EscrowRuntimeException.class,
                () -> ledgerOnly.applier().preflight(
                        ledgerOnly.settlement().requestId(),
                        event(ledgerOnly.settlement())));

        Stores attemptOnly = Stores.seeded();
        ClaimDeliveryCommit delivery = attemptOnly.settlement().delivery();
        attemptOnly.claims().deliverCommitted(
                delivery.ownerId(), delivery.claimId(),
                delivery.requestKey(), delivery.units(),
                delivery.deliveredAt());
        assertThrows(EscrowRuntimeException.class,
                () -> attemptOnly.applier().preflight(
                        attemptOnly.settlement().requestId(),
                        event(attemptOnly.settlement())));
    }

    private static EscrowJournalEvent event(
            MoneyClaimSettlement settlement
    ) {
        return new EscrowJournalEvent(
                EscrowJournalEventType.MONEY_CLAIM_SETTLEMENT,
                MoneyClaimSettlementCodec.encode(settlement));
    }

    private record Stores(
            UUID ownerId,
            UUID claimId,
            LedgerAccountId claimAccount,
            LedgerSavedData ledger,
            ClaimSavedData claims,
            MoneyClaimSettlement settlement
    ) {
        private static Stores seeded() {
            UUID ownerId = UUID.randomUUID();
            UUID claimId = UUID.randomUUID();
            UUID sourceTransaction = UUID.randomUUID();
            LedgerAccountId claimAccount = new LedgerAccountId(
                    LedgerAccountType.PLAYER_CLAIM,
                    claimId.toString());
            LedgerSavedData ledger = new LedgerSavedData();
            ClaimSavedData claims = new ClaimSavedData();
            claims.createCommitted(new EscrowClaim(
                    claimId, sourceTransaction, ownerId,
                    "money.claim.invariant." + claimId,
                    ClaimKind.MONEY, 200L, 200L, new byte[0],
                    ClaimStatus.PENDING, "Money claim invariant",
                    PlayerPaymentTestFixtures.NOW,
                    PlayerPaymentTestFixtures.NOW));
            ledger.applyCommitted(new LedgerTransaction(
                    UUID.randomUUID(), "money claim invariant value",
                    "seed", List.of(
                    new LedgerLeg(LedgerAccountId.system(
                            LedgerAccountType.ADMIN_SOURCE), -200L),
                    new LedgerLeg(claimAccount, 200L))));
            ledger.applyCommitted(new LedgerTransaction(
                    UUID.randomUUID(), "money claim invariant debt",
                    "seed", List.of(
                    new LedgerLeg(PlayerPaymentCommit.debtAccount(ownerId),
                            -50L),
                    new LedgerLeg(LedgerAccountId.system(
                            LedgerAccountType.ADMIN_SINK), 50L))));
            MoneyClaimSettlement settlement =
                    MoneyClaimSettlement.create(
                            UUID.randomUUID(), ownerId, claimId,
                            0L, -50L, 0L, 200L,
                            100L, 1L,
                            PlayerPaymentTestFixtures.NOW.plusSeconds(1));
            return new Stores(ownerId, claimId, claimAccount,
                    ledger, claims, settlement);
        }

        private EscrowSavedDataMutationApplier applier() {
            return new EscrowSavedDataMutationApplier(
                    new EscrowTransactionSavedData(), ledger, claims);
        }
    }
}

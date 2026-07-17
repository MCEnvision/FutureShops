package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.admin.EscrowAdministrativeAuditSavedData;
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
import com.enviouse.futureshops.server.escrow.store.EscrowTransactionSavedData;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MoneyClaimDebtSettlementTest {
    @Test
    void collectionPaysDebtThenWalletAndLeavesClaimRemainder() {
        UUID owner = PlayerPaymentTestFixtures.RECIPIENT_ID;
        UUID claimId = UUID.randomUUID();
        UUID sourceTransaction = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        ClaimSavedData claims = new ClaimSavedData();
        LedgerSavedData ledger = new LedgerSavedData();
        EscrowClaim claim = new EscrowClaim(
                claimId, sourceTransaction, owner,
                "money.claim.debt.test." + claimId,
                ClaimKind.MONEY, 200L, 200L, new byte[0],
                ClaimStatus.PENDING, "Debt collection test",
                PlayerPaymentTestFixtures.NOW,
                PlayerPaymentTestFixtures.NOW);
        claims.createCommitted(claim);
        ledger.applyCommitted(new LedgerTransaction(
                UUID.randomUUID(), "money claim seed", "seed",
                List.of(
                        new LedgerLeg(new LedgerAccountId(
                                LedgerAccountType.PLAYER_CLAIM,
                                claimId.toString()), 200L),
                        new LedgerLeg(LedgerAccountId.system(
                                LedgerAccountType.ADMIN_SOURCE), -200L))));
        ledger.applyCommitted(new LedgerTransaction(
                UUID.randomUUID(), "money claim debt", "debt",
                List.of(
                        new LedgerLeg(PlayerPaymentCommit.debtAccount(owner),
                                -50L),
                        new LedgerLeg(LedgerAccountId.system(
                                LedgerAccountType.ADMIN_SINK), 50L))));
        MoneyClaimSettlement settlement = MoneyClaimSettlement.create(
                requestId, owner, claimId,
                0L, -50L, 0L, 200L,
                100L, 1L,
                PlayerPaymentTestFixtures.NOW.plusSeconds(1));
        EscrowJournalEvent event = new EscrowJournalEvent(
                EscrowJournalEventType.MONEY_CLAIM_SETTLEMENT,
                MoneyClaimSettlementCodec.encode(settlement));
        EscrowSavedDataMutationApplier applier =
                new EscrowSavedDataMutationApplier(
                        new EscrowTransactionSavedData(), ledger, claims,
                        new EscrowAdministrativeAuditSavedData(),
                        new CustodySavedData(),
                        new ProtectedMintSavedData());
        JournalRecord record = new JournalRecord(
                1L, requestId,
                EscrowStepIds.forEvent(requestId, event),
                EscrowJournalEventCodec.encode(event));

        applier.apply(record, event);

        assertEquals(0L, ledger.balance(
                PlayerPaymentCommit.debtAccount(owner)));
        assertEquals(100L, ledger.balance(
                PlayerPaymentCommit.walletAccount(owner)));
        assertEquals(50L, ledger.balance(new LedgerAccountId(
                LedgerAccountType.PLAYER_CLAIM,
                claimId.toString())));
        assertEquals(50L, claims.getClaim(claimId).remainingUnits());
    }
}

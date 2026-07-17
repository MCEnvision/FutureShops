package com.enviouse.futureshops.server.escrow.ledger;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryLedgerTest {
    private static final LedgerAccountId SOURCE = LedgerAccountId.system(LedgerAccountType.ADMIN_SOURCE);
    private static final LedgerAccountId WALLET = new LedgerAccountId(LedgerAccountType.PLAYER_WALLET, "player one");
    private static final LedgerAccountId ESCROW = new LedgerAccountId(LedgerAccountType.TRANSACTION_ESCROW, "transaction one");

    @Test
    void balancedTransactionAppliesOnce() {
        InMemoryLedger ledger = new InMemoryLedger();
        UUID transactionId = UUID.randomUUID();
        LedgerTransaction seed = transaction(transactionId, "seed", SOURCE, -100L, WALLET, 100L);

        LedgerApplyResult first = ledger.apply(seed);
        LedgerApplyResult second = ledger.apply(seed);

        assertTrue(first.applied());
        assertFalse(first.replayed());
        assertFalse(second.applied());
        assertTrue(second.replayed());
        assertEquals(100L, ledger.balance(WALLET));
        assertEquals(-100L, ledger.balance(SOURCE));
    }

    @Test
    void holdMovesMoneyWithoutChangingSupply() {
        InMemoryLedger ledger = new InMemoryLedger();
        ledger.apply(transaction(UUID.randomUUID(), "seed", SOURCE, -100L, WALLET, 100L));
        ledger.apply(transaction(UUID.randomUUID(), "hold", WALLET, -75L, ESCROW, 75L));

        assertEquals(25L, ledger.balance(WALLET));
        assertEquals(75L, ledger.balance(ESCROW));
        assertEquals(0L, Math.addExact(ledger.balance(SOURCE),
                Math.addExact(ledger.balance(WALLET), ledger.balance(ESCROW))));
    }

    @Test
    void unbalancedTransactionIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new LedgerTransaction(
                UUID.randomUUID(), "bad", "bad", List.of(
                new LedgerLeg(WALLET, 10L),
                new LedgerLeg(ESCROW, -9L))));
    }

    @Test
    void protectedAccountCannotBecomeNegative() {
        InMemoryLedger ledger = new InMemoryLedger();
        LedgerTransaction hold = transaction(UUID.randomUUID(), "hold", WALLET, -1L, ESCROW, 1L);

        assertThrows(LedgerConflictException.class, () -> ledger.apply(hold));
        assertEquals(0L, ledger.balance(WALLET));
        assertEquals(0L, ledger.balance(ESCROW));
    }

    @Test
    void transactionIdCannotBeReusedWithDifferentLegs() {
        InMemoryLedger ledger = new InMemoryLedger();
        UUID transactionId = UUID.randomUUID();
        ledger.apply(transaction(transactionId, "seed", SOURCE, -100L, WALLET, 100L));

        LedgerTransaction conflicting = transaction(transactionId, "seed", SOURCE, -99L, WALLET, 99L);
        assertThrows(LedgerConflictException.class, () -> ledger.apply(conflicting));
    }

    @Test
    void idempotencyKeyCannotMoveToAnotherTransaction() {
        InMemoryLedger ledger = new InMemoryLedger();
        ledger.apply(transaction(UUID.randomUUID(), "seed", SOURCE, -100L, WALLET, 100L));

        LedgerTransaction duplicateKey = transaction(UUID.randomUUID(), "seed", SOURCE, -100L, WALLET, 100L);
        assertThrows(LedgerConflictException.class, () -> ledger.apply(duplicateKey));
    }

    @Test
    void repeatedAccountLegsAreValidatedByTheirNetChange() {
        InMemoryLedger ledger = new InMemoryLedger();
        LedgerTransaction transaction = new LedgerTransaction(
                UUID.randomUUID(), "net zero", "net zero", List.of(
                new LedgerLeg(WALLET, -10L),
                new LedgerLeg(WALLET, 10L)));

        assertTrue(ledger.apply(transaction).applied());
        assertEquals(0L, ledger.balance(WALLET));
    }

    @Test
    void preflightChecksFundsWithoutChangingBalancesOrIdempotency() {
        InMemoryLedger ledger = new InMemoryLedger();
        LedgerTransaction seed = transaction(UUID.randomUUID(), "preflight seed",
                SOURCE, -100L, WALLET, 100L);

        assertTrue(ledger.preflight(seed).applied());
        assertEquals(0L, ledger.balance(WALLET));
        assertFalse(ledger.wasApplied(seed.transactionId()));
        assertTrue(ledger.apply(seed).applied());

        LedgerTransaction tooLarge = transaction(UUID.randomUUID(), "preflight debit",
                WALLET, -101L, ESCROW, 101L);
        assertThrows(LedgerConflictException.class, () -> ledger.preflight(tooLarge));
        assertEquals(100L, ledger.balance(WALLET));
        assertFalse(ledger.wasApplied(tooLarge.transactionId()));
    }

    @Test
    void runtimeCapacityIsRejectedDuringPreflight() {
        InMemoryLedger ledger = new InMemoryLedger(2, 1);
        LedgerTransaction seed = transaction(UUID.randomUUID(), "capacity seed",
                SOURCE, -100L, WALLET, 100L);
        ledger.apply(seed);

        LedgerTransaction second = transaction(UUID.randomUUID(), "capacity second",
                WALLET, -1L, ESCROW, 1L);
        assertThrows(LedgerConflictException.class, () -> ledger.preflight(second));
        assertEquals(100L, ledger.balance(WALLET));
    }

    private static LedgerTransaction transaction(UUID id, String key,
                                                 LedgerAccountId debitAccount, long debit,
                                                 LedgerAccountId creditAccount, long credit) {
        return new LedgerTransaction(id, key, key, List.of(
                new LedgerLeg(debitAccount, debit),
                new LedgerLeg(creditAccount, credit)));
    }
}

package com.enviouse.futureshops.server.escrow.ledger;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LedgerConservationPropertyTest {
    @Test
    void randomBalancedTransfersPreserveSupply() {
        InMemoryLedger ledger = new InMemoryLedger();
        LedgerAccountId source = LedgerAccountId.system(LedgerAccountType.ADMIN_SOURCE);
        List<LedgerAccountId> accounts = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            LedgerAccountId account = new LedgerAccountId(LedgerAccountType.PLAYER_WALLET, "player " + index);
            accounts.add(account);
            ledger.apply(transfer(source, account, 10_000L, "seed " + index));
        }

        SplittableRandom random = new SplittableRandom(928374L);
        for (int index = 0; index < 5_000; index++) {
            LedgerAccountId from = accounts.get(random.nextInt(accounts.size()));
            LedgerAccountId to = accounts.get(random.nextInt(accounts.size()));
            if (from.equals(to)) {
                continue;
            }
            long available = ledger.balance(from);
            long amount = available == 0L ? 0L : random.nextLong(available + 1L);
            if (amount == 0L) {
                continue;
            }
            ledger.apply(transfer(from, to, amount, "move " + index));
            long total = ledger.snapshotBalances().values().stream()
                    .reduce(0L, Math::addExact);
            assertEquals(0L, total);
        }
    }

    @Test
    void overflowLeavesEveryAccountUnchanged() {
        InMemoryLedger ledger = new InMemoryLedger();
        LedgerAccountId source = LedgerAccountId.system(LedgerAccountType.ADMIN_SOURCE);
        LedgerAccountId first = new LedgerAccountId(LedgerAccountType.PLAYER_WALLET, "first");
        LedgerAccountId second = new LedgerAccountId(LedgerAccountType.PLAYER_WALLET, "second");
        ledger.apply(transfer(source, first, Long.MAX_VALUE, "seed maximum"));
        ledger.apply(transfer(source, second, 1L, "seed one"));

        LedgerTransaction overflow = new LedgerTransaction(UUID.randomUUID(), "overflow", "overflow", List.of(
                new LedgerLeg(second, -1L),
                new LedgerLeg(first, 1L)));

        assertThrows(ArithmeticException.class, () -> ledger.apply(overflow));
        assertEquals(Long.MIN_VALUE, ledger.balance(source));
        assertEquals(Long.MAX_VALUE, ledger.balance(first));
        assertEquals(1L, ledger.balance(second));
    }

    private static LedgerTransaction transfer(
            LedgerAccountId from,
            LedgerAccountId to,
            long amount,
            String key
    ) {
        return new LedgerTransaction(UUID.randomUUID(), key, key, List.of(
                new LedgerLeg(from, -amount),
                new LedgerLeg(to, amount)));
    }
}

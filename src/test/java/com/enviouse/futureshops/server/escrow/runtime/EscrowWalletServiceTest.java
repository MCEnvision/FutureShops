package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountId;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountType;
import com.enviouse.futureshops.server.escrow.ledger.LedgerSavedData;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EscrowWalletServiceTest {
    private static final UUID FIRST = UUID.fromString(
            "00000000-0000-0000-0000-000000000001");
    private static final UUID SECOND = UUID.fromString(
            "00000000-0000-0000-0000-000000000002");

    @Test
    void zeroInitializationCreatesDurableIdentityAndReplays() {
        EscrowWalletService service = service();
        UUID requestId = UUID.randomUUID();

        WalletMutationResult applied = service.initialize(
                requestId, FIRST, 0L, false, "legacy");
        WalletMutationResult replayed = service.initialize(
                requestId, FIRST, 0L, false, "legacy");

        assertEquals(WalletMutationStatus.APPLIED, applied.status());
        assertEquals(WalletMutationStatus.REPLAYED, replayed.status());
        assertTrue(service.isInitialized(FIRST));
        assertEquals(0L, service.balance(FIRST));
    }

    @Test
    void changedInitializationPayloadConflicts() {
        EscrowWalletService service = service();
        UUID requestId = UUID.randomUUID();
        assertTrue(service.initialize(
                requestId, FIRST, 25L, false, "legacy").success());

        WalletMutationResult result = service.initialize(
                requestId, FIRST, 30L, false, "legacy");

        assertEquals(WalletMutationStatus.CONFLICT, result.status());
        assertEquals(25L, result.primaryBalance());
    }

    @Test
    void anotherInitializationCannotOverwriteWallet() {
        EscrowWalletService service = service();
        assertTrue(service.initialize(
                UUID.randomUUID(), FIRST, 25L, false, "legacy").success());

        WalletMutationResult result = service.initialize(
                UUID.randomUUID(), FIRST, 80L, false, "starting");

        assertEquals(WalletMutationStatus.ALREADY_INITIALIZED, result.status());
        assertEquals(25L, result.primaryBalance());
    }

    @Test
    void adminDebtIsSeparateAndCreditsPayItFirst() {
        EscrowWalletService service = service();
        assertTrue(service.initialize(
                UUID.randomUUID(), FIRST, -50L, true, "legacy").success());
        assertEquals(-50L, service.balance(FIRST));

        assertTrue(service.credit(UUID.randomUUID(), FIRST,
                30L, 100L, "admin").success());
        assertEquals(-20L, service.balance(FIRST));
        assertTrue(service.credit(UUID.randomUUID(), FIRST,
                50L, 100L, "admin").success());

        assertEquals(30L, service.balance(FIRST));
    }

    @Test
    void ordinaryDebitCannotDeepenDebt() {
        EscrowWalletService service = service();
        assertTrue(service.initialize(
                UUID.randomUUID(), FIRST, 20L, false, "starting").success());

        WalletMutationResult denied = service.debit(
                UUID.randomUUID(), FIRST, 21L, false, "buy");
        WalletMutationResult admin = service.debit(
                UUID.randomUUID(), FIRST, 30L, true, "admin");

        assertEquals(WalletMutationStatus.INSUFFICIENT_FUNDS, denied.status());
        assertTrue(admin.success());
        assertEquals(-10L, service.balance(FIRST));
    }

    @Test
    void transferIsAtomicAndPaysRecipientDebt() {
        EscrowWalletService service = service();
        assertTrue(service.initialize(
                UUID.randomUUID(), FIRST, 100L, false, "starting").success());
        assertTrue(service.initialize(
                UUID.randomUUID(), SECOND, -25L, true, "legacy").success());

        WalletMutationResult result = service.transfer(
                UUID.randomUUID(), FIRST, SECOND, 40L, 100L, "transfer");

        assertTrue(result.success());
        assertEquals(60L, result.primaryBalance());
        assertEquals(15L, result.secondaryBalance().orElseThrow());
    }

    @Test
    void failedTransferChangesNeitherWallet() {
        EscrowWalletService service = service();
        assertTrue(service.initialize(
                UUID.randomUUID(), FIRST, 100L, false, "starting").success());
        assertTrue(service.initialize(
                UUID.randomUUID(), SECOND, 95L, false, "starting").success());

        WalletMutationResult result = service.transfer(
                UUID.randomUUID(), FIRST, SECOND, 10L, 100L, "transfer");

        assertEquals(WalletMutationStatus.MAX_BALANCE_EXCEEDED, result.status());
        assertEquals(100L, service.balance(FIRST));
        assertEquals(95L, service.balance(SECOND));
    }

    @Test
    void debitReplayIgnoresChangedCurrentBalance() {
        EscrowWalletService service = service();
        assertTrue(service.initialize(
                UUID.randomUUID(), FIRST, 100L, false, "starting").success());
        UUID requestId = UUID.randomUUID();
        assertTrue(service.debit(
                requestId, FIRST, 80L, false, "buy").success());

        WalletMutationResult replay = service.debit(
                requestId, FIRST, 80L, false, "buy");

        assertEquals(WalletMutationStatus.REPLAYED, replay.status());
        assertEquals(20L, replay.primaryBalance());
    }

    @Test
    void transferReplayIgnoresChangedLimitsAndFundsOnlyWhenPayloadMatches() {
        EscrowWalletService service = service();
        assertTrue(service.initialize(
                UUID.randomUUID(), FIRST, 100L, false, "starting").success());
        assertTrue(service.initialize(
                UUID.randomUUID(), SECOND, 90L, false, "starting").success());
        UUID requestId = UUID.randomUUID();
        assertTrue(service.transfer(requestId, FIRST, SECOND,
                10L, 100L, "transfer").success());

        WalletMutationResult replay = service.transfer(
                requestId, FIRST, SECOND, 10L, 90L, "transfer");
        WalletMutationResult conflict = service.transfer(
                requestId, FIRST, SECOND, 11L, 100L, "transfer");

        assertEquals(WalletMutationStatus.REPLAYED, replay.status());
        assertEquals(WalletMutationStatus.CONFLICT, conflict.status());
        assertEquals(90L, service.balance(FIRST));
        assertEquals(100L, service.balance(SECOND));
    }

    @Test
    void setBalanceNormalizesWalletAndDebt() {
        MemoryBackend backend = new MemoryBackend();
        EscrowWalletService service = new EscrowWalletService(backend);
        assertTrue(service.initialize(
                UUID.randomUUID(), FIRST, 75L, false, "starting").success());
        assertTrue(service.setBalance(
                UUID.randomUUID(), FIRST, -12L, true, "admin").success());

        assertEquals(-12L, service.balance(FIRST));
        assertEquals(0L, backend.balance(account(
                LedgerAccountType.PLAYER_WALLET, FIRST)));
        assertEquals(-12L, backend.balance(account(
                LedgerAccountType.PLAYER_DEBT, FIRST)));
    }

    @Test
    void snapshotContainsOnlyPlayerNetBalances() {
        MemoryBackend backend = new MemoryBackend();
        EscrowWalletService service = new EscrowWalletService(backend);
        assertTrue(service.initialize(
                UUID.randomUUID(), FIRST, 10L, false, "starting").success());
        assertTrue(service.initialize(
                UUID.randomUUID(), SECOND, -5L, true, "legacy").success());

        Map<UUID, Long> balances = service.snapshotBalances();

        assertEquals(Map.of(FIRST, 10L, SECOND, -5L), balances);
        assertFalse(balances.containsKey(UUID.randomUUID()));
    }

    @Test
    void storedBalanceReadDoesNotInitializeMissingWallet() {
        LedgerSavedData ledger = new LedgerSavedData();
        EscrowWalletService service = new EscrowWalletService(
                new MemoryBackend(ledger));

        assertEquals(OptionalLong.empty(),
                EscrowWalletService.storedBalance(ledger, FIRST));
        assertTrue(service.initialize(
                UUID.randomUUID(), FIRST, 75L, false, "starting").success());
        assertTrue(service.initialize(
                UUID.randomUUID(), SECOND, -20L, true, "legacy").success());

        assertEquals(OptionalLong.of(75L),
                EscrowWalletService.storedBalance(ledger, FIRST));
        assertEquals(OptionalLong.of(-20L),
                EscrowWalletService.storedBalance(ledger, SECOND));
    }

    private static EscrowWalletService service() {
        return new EscrowWalletService(new MemoryBackend());
    }

    private static LedgerAccountId account(LedgerAccountType type,
                                           UUID playerId) {
        return new LedgerAccountId(type, playerId.toString());
    }

    private static final class MemoryBackend implements WalletLedgerBackend {
        private final LedgerSavedData ledger;

        private MemoryBackend() {
            this(new LedgerSavedData());
        }

        private MemoryBackend(LedgerSavedData ledger) {
            this.ledger = ledger;
        }

        @Override
        public long balance(LedgerAccountId account) {
            return ledger.balance(account);
        }

        @Override
        public boolean containsAccount(LedgerAccountId account) {
            return ledger.containsAccount(account);
        }

        @Override
        public Map<LedgerAccountId, Long> snapshotBalances() {
            return ledger.snapshotBalances();
        }

        @Override
        public boolean wasApplied(UUID transactionId) {
            return ledger.wasApplied(transactionId);
        }

        @Override
        public Optional<LedgerTransaction> appliedTransaction(
                UUID transactionId
        ) {
            return ledger.transactionReceipt(transactionId)
                    .map(com.enviouse.futureshops.server.escrow.ledger.LedgerTransactionReceipt::transaction);
        }

        @Override
        public boolean commit(LedgerTransaction transaction) {
            return ledger.applyCommitted(transaction).replayed();
        }
    }
}

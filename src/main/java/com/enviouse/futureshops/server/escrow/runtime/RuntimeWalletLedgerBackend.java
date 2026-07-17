package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountId;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

final class RuntimeWalletLedgerBackend implements WalletLedgerBackend {
    private final EscrowRuntimeService runtime;

    RuntimeWalletLedgerBackend(EscrowRuntimeService runtime) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    @Override
    public long balance(LedgerAccountId account) {
        return runtime.ledgerBalance(account);
    }

    @Override
    public boolean containsAccount(LedgerAccountId account) {
        return runtime.ledgerContainsAccount(account);
    }

    @Override
    public Map<LedgerAccountId, Long> snapshotBalances() {
        return runtime.ledgerSnapshot();
    }

    @Override
    public boolean wasApplied(UUID transactionId) {
        return runtime.wasLedgerTransactionApplied(transactionId);
    }

    @Override
    public Optional<LedgerTransaction> appliedTransaction(UUID transactionId) {
        return runtime.ledgerTransaction(transactionId);
    }

    @Override
    public boolean commit(LedgerTransaction transaction) {
        return runtime.commitLedger(transaction).replayed();
    }
}

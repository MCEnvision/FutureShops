package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountId;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

interface WalletLedgerBackend {
    long balance(LedgerAccountId account);

    boolean containsAccount(LedgerAccountId account);

    Map<LedgerAccountId, Long> snapshotBalances();

    boolean wasApplied(UUID transactionId);

    Optional<LedgerTransaction> appliedTransaction(UUID transactionId);

    boolean commit(LedgerTransaction transaction);
}

package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface AtmWithdrawalBackend {
    Optional<EscrowTransaction> transaction(UUID requestId);

    List<EscrowClaim> claims(UUID requestId);

    long balance(UUID playerId);

    boolean migrationComplete();

    EscrowRuntimeState runtimeState();

    EscrowCommitResult commitTransaction(EscrowTransaction transaction);

    EscrowCommitResult commitProtected(AtmWithdrawalCommit commit);

    EscrowCommitResult commitForeign(ForeignAtmWithdrawalCommit commit);
}

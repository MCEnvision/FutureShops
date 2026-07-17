package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.journal.JournalRecord;

import java.util.UUID;

@FunctionalInterface
public interface EscrowMutationApplier {
    default EscrowPreflightResult preflight(UUID transactionId, EscrowJournalEvent event) {
        return EscrowPreflightResult.APPLY;
    }

    void apply(JournalRecord record, EscrowJournalEvent event);
}

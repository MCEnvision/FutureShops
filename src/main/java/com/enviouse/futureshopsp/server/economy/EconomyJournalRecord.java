package com.enviouse.futureshopsp.server.economy;

import com.enviouse.futureshopsp.api.economy.MutationReceipt;
import com.enviouse.futureshopsp.api.economy.MutationRequest;
import com.enviouse.futureshopsp.api.economy.ProviderResult;
import com.enviouse.futureshopsp.api.economy.ProviderResultStatus;

import java.util.Objects;
import java.util.Optional;

/** Immutable journal entry with no external balance field. */
public record EconomyJournalRecord(
        MutationRequest request,
        EconomyTransactionState state,
        Optional<MutationReceipt> receipt,
        ProviderResultStatus resultStatus,
        String diagnostic) {
    public EconomyJournalRecord {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(receipt, "receipt");
        Objects.requireNonNull(resultStatus, "resultStatus");
        diagnostic = diagnostic == null ? "" : diagnostic;
        if (diagnostic.length() > 256 || diagnostic.indexOf('\n') >= 0 || diagnostic.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("diagnostic must be a single line of at most 256 characters");
        }
    }

    public boolean incomplete() {
        return state != EconomyTransactionState.RESOLVED
                && state != EconomyTransactionState.DELIVERED
                && state != EconomyTransactionState.CLAIMED;
    }
}

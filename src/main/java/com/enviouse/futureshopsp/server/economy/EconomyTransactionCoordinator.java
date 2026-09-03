package com.enviouse.futureshopsp.server.economy;

import com.enviouse.futureshopsp.api.economy.BalanceSnapshot;
import com.enviouse.futureshopsp.api.economy.EconomyCapability;
import com.enviouse.futureshopsp.api.economy.EconomyProvider;
import com.enviouse.futureshopsp.api.economy.MutationKind;
import com.enviouse.futureshopsp.api.economy.MutationReceipt;
import com.enviouse.futureshopsp.api.economy.MutationRequest;
import com.enviouse.futureshopsp.api.economy.ProviderCapabilities;
import com.enviouse.futureshopsp.api.economy.ProviderError;
import com.enviouse.futureshopsp.api.economy.ProviderLifecycle;
import com.enviouse.futureshopsp.api.economy.ProviderResult;
import com.enviouse.futureshopsp.api.economy.ProviderResultStatus;
import com.enviouse.futureshopsp.api.economy.RequestId;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One server authoritative transaction boundary for provider reads and mutations.
 * The journal is written before a provider effect and every ambiguous outcome freezes admission.
 */
public final class EconomyTransactionCoordinator {
    private final EconomyProvider provider;
    private final EconomyLifecycleController lifecycle;
    private final EconomyTransactionJournal journal;
    private final Object lock = new Object();

    public EconomyTransactionCoordinator(EconomyProvider provider,
                                         EconomyLifecycleController lifecycle,
                                         EconomyTransactionJournal journal) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.journal = Objects.requireNonNull(journal, "journal");
    }

    public EconomyLifecycleSnapshot lifecycle() {
        return lifecycle.snapshot();
    }

    public ProviderResult<BalanceSnapshot> balance(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (!lifecycle.admitQuery()) {
            return unavailableForLifecycle();
        }
        if (!supports(EconomyCapability.BALANCE_QUERY)) {
            return ProviderResult.unavailable(ProviderError.CAPABILITY_MISSING,
                    "provider does not support balance queries");
        }
        try {
            ProviderResult<BalanceSnapshot> result = provider.balance(playerId);
            return result == null ? ProviderResult.unavailable(ProviderError.PROVIDER_EXCEPTION,
                    "provider returned no balance result") : result;
        } catch (RuntimeException exception) {
            lifecycle.markFailed("balance query failed");
            return ProviderResult.unavailable(ProviderError.PROVIDER_EXCEPTION, "balance query failed");
        }
    }

    public ProviderResult<MutationReceipt> withdraw(MutationRequest request) {
        return execute(request, MutationKind.WITHDRAW);
    }

    public ProviderResult<MutationReceipt> deposit(MutationRequest request) {
        return execute(request, MutationKind.DEPOSIT);
    }

    public ProviderResult<MutationReceipt> transfer(UUID from, UUID to, long amountMinorUnits) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.equals(to)) {
            return ProviderResult.rejected(ProviderError.INVALID_REQUEST, "transfer target must differ");
        }
        RequestId root = RequestId.random();
        MutationRequest debit = new MutationRequest(root, from, Optional.of(to), amountMinorUnits,
                MutationKind.TRANSFER_DEBIT);
        ProviderResult<MutationReceipt> debitResult = execute(debit, MutationKind.TRANSFER_DEBIT);
        if (!debitResult.confirmed()) {
            return debitResult;
        }
        MutationRequest credit = new MutationRequest(root, to, Optional.of(from), amountMinorUnits,
                MutationKind.TRANSFER_CREDIT);
        ProviderResult<MutationReceipt> creditResult = execute(credit, MutationKind.TRANSFER_CREDIT);
        if (creditResult.confirmed()) {
            return creditResult;
        }
        if (supportsAllMutationCapabilities()) {
            MutationRequest compensation = new MutationRequest(RequestId.random(), from, Optional.of(to),
                    amountMinorUnits, MutationKind.COMPENSATION);
            execute(compensation, MutationKind.COMPENSATION);
        }
        return creditResult;
    }

    public ProviderResult<MutationReceipt> recover(RequestId requestId) {
        Objects.requireNonNull(requestId, "requestId");
        synchronized (lock) {
            EconomyJournalRecord record = journal.find(requestId).orElse(null);
            if (record == null) {
                return ProviderResult.rejected(ProviderError.RECEIPT_NOT_FOUND, "transaction is not journaled");
            }
            if (!record.incomplete()) {
                return record.receipt().map(ProviderResult::confirmed)
                        .orElseGet(() -> ProviderResult.rejected(ProviderError.DUPLICATE_REQUEST,
                                "transaction is already resolved"));
            }
            if (!supports(EconomyCapability.RECEIPT_LOOKUP)) {
                lifecycle.markAmbiguous("provider cannot look up pending transaction");
                return ProviderResult.recoveryRequired("durable receipt lookup is unavailable");
            }
            ProviderResult<MutationReceipt> lookup;
            try {
                lookup = provider.lookup(requestId);
            } catch (RuntimeException exception) {
                lifecycle.markAmbiguous("provider receipt lookup failed");
                replace(record, EconomyTransactionState.UNCERTAIN, Optional.empty(),
                        ProviderResultStatus.AMBIGUOUS, "receipt lookup failed");
                return ProviderResult.recoveryRequired("receipt lookup failed");
            }
            if (lookup != null && lookup.confirmed() && validReceipt(record.request(), lookup.value().orElse(null))) {
                replace(record, EconomyTransactionState.EXTERNAL_CONFIRMED, lookup.receipt(),
                        ProviderResultStatus.CONFIRMED, "");
                replace(record, EconomyTransactionState.RESOLVED, lookup.receipt(),
                        ProviderResultStatus.CONFIRMED, "");
                lifecycle.markRecovered();
                return lookup;
            }
            if (lookup != null && lookup.status() == ProviderResultStatus.REJECTED) {
                replace(record, EconomyTransactionState.RESOLVED, Optional.empty(),
                        ProviderResultStatus.REJECTED, lookup.diagnostic());
                lifecycle.markRecovered();
                return lookup;
            }
            replace(record, EconomyTransactionState.UNCERTAIN, Optional.empty(),
                    ProviderResultStatus.AMBIGUOUS, "provider outcome remains unknown");
            lifecycle.markAmbiguous("provider outcome remains unknown");
            return ProviderResult.recoveryRequired("provider outcome remains unknown");
        }
    }

    private ProviderResult<MutationReceipt> execute(MutationRequest request, MutationKind expectedKind) {
        Objects.requireNonNull(request, "request");
        if (request.kind() != expectedKind && !(expectedKind == MutationKind.COMPENSATION
                && request.kind() == MutationKind.COMPENSATION)) {
            return ProviderResult.rejected(ProviderError.INVALID_REQUEST, "mutation kind does not match route");
        }
        synchronized (lock) {
            EconomyJournalRecord existing = journal.find(request.requestId()).orElse(null);
            if (existing != null) {
                return replay(existing);
            }
            ProviderResult<MutationReceipt> admission = admit(request);
            if (admission != null) {
                return admission;
            }
            EconomyJournalRecord prepared = new EconomyJournalRecord(request,
                    EconomyTransactionState.PREPARED, Optional.empty(), ProviderResultStatus.REJECTED, "");
            journal.append(prepared);
            EconomyJournalRecord pending = new EconomyJournalRecord(request,
                    EconomyTransactionState.EXTERNAL_PENDING, Optional.empty(), ProviderResultStatus.UNAVAILABLE, "");
            journal.replace(pending);

            ProviderResult<MutationReceipt> result;
            try {
                result = expectedKind == MutationKind.DEPOSIT || expectedKind == MutationKind.TRANSFER_CREDIT
                        ? provider.deposit(request) : provider.withdraw(request);
            } catch (RuntimeException exception) {
                return ambiguous(pending, "provider mutation failed after pending state");
            }
            if (result == null) {
                return ambiguous(pending, "provider returned no mutation result");
            }
            if (result.confirmed()) {
                MutationReceipt receipt = result.receipt().orElse(result.value().orElse(null));
                if (!validReceipt(request, receipt)) {
                    return ambiguous(pending, "provider receipt does not match request");
                }
                replace(pending, EconomyTransactionState.EXTERNAL_CONFIRMED, Optional.of(receipt),
                        ProviderResultStatus.CONFIRMED, "");
                replace(pending, EconomyTransactionState.RESOLVED, Optional.of(receipt),
                        ProviderResultStatus.CONFIRMED, "");
                return ProviderResult.confirmed(receipt);
            }
            if (result.status() == ProviderResultStatus.REJECTED) {
                replace(pending, EconomyTransactionState.RESOLVED, Optional.empty(),
                        ProviderResultStatus.REJECTED, result.diagnostic());
                return result;
            }
            return ambiguous(pending, result.diagnostic().isBlank()
                    ? "provider outcome is not definitive" : result.diagnostic());
        }
    }

    private ProviderResult<MutationReceipt> admit(MutationRequest request) {
        EconomyLifecycleSnapshot state = lifecycle.snapshot();
        if (!state.acceptsMutations()) {
            if (state.lifecycle() == ProviderLifecycle.RECOVERING || state.lifecycle() == ProviderLifecycle.FROZEN) {
                return ProviderResult.recoveryRequired(state.diagnostic());
            }
            return ProviderResult.unavailable(ProviderError.NOT_READY,
                    state.diagnostic().isBlank() ? "provider is not ready" : state.diagnostic());
        }
        if (!supports(EconomyCapability.PRECHECK)
                || !supports(EconomyCapability.RECEIPT_LOOKUP)
                || !supports(EconomyCapability.IDEMPOTENT_RETRY)
                || !supports(request.kind() == MutationKind.DEPOSIT || request.kind() == MutationKind.TRANSFER_CREDIT
                ? EconomyCapability.DEPOSIT : EconomyCapability.WITHDRAW)) {
            return ProviderResult.unavailable(ProviderError.CAPABILITY_MISSING,
                    "provider lacks the capabilities required by this mutation");
        }
        ProviderResult<BalanceSnapshot> precheck;
        try {
            precheck = provider.precheck(request);
        } catch (RuntimeException exception) {
            lifecycle.markFailed("provider precheck failed");
            return ProviderResult.unavailable(ProviderError.PROVIDER_EXCEPTION, "provider precheck failed");
        }
        if (precheck == null) {
            lifecycle.markFailed("provider returned no precheck result");
            return ProviderResult.unavailable(ProviderError.PROVIDER_EXCEPTION, "provider returned no precheck result");
        }
        if (!precheck.confirmed()) {
            return copyFailure(precheck);
        }
        return null;
    }

    private ProviderResult<MutationReceipt> replay(EconomyJournalRecord record) {
        if (record.state() == EconomyTransactionState.RESOLVED
                || record.state() == EconomyTransactionState.EXTERNAL_CONFIRMED) {
            return record.receipt().map(ProviderResult::confirmed)
                    .orElseGet(() -> ProviderResult.rejected(ProviderError.DUPLICATE_REQUEST,
                            record.diagnostic()));
        }
        if (record.state() == EconomyTransactionState.UNCERTAIN) {
            return ProviderResult.recoveryRequired("transaction requires operator recovery");
        }
        return ProviderResult.recoveryRequired("transaction is already pending recovery");
    }

    private ProviderResult<MutationReceipt> ambiguous(EconomyJournalRecord pending, String diagnostic) {
        replace(pending, EconomyTransactionState.UNCERTAIN, Optional.empty(),
                ProviderResultStatus.AMBIGUOUS, diagnostic);
        lifecycle.markAmbiguous(diagnostic);
        return ProviderResult.ambiguous(diagnostic);
    }

    private boolean supports(EconomyCapability capability) {
        ProviderCapabilities capabilities = provider.capabilities();
        return capabilities != null && capabilities.supports(capability);
    }

    private boolean supportsAllMutationCapabilities() {
        return supports(EconomyCapability.PRECHECK) && supports(EconomyCapability.RECEIPT_LOOKUP)
                && supports(EconomyCapability.IDEMPOTENT_RETRY) && supports(EconomyCapability.WITHDRAW)
                && supports(EconomyCapability.DEPOSIT);
    }

    private void replace(EconomyJournalRecord source, EconomyTransactionState state,
                         Optional<MutationReceipt> receipt, ProviderResultStatus status, String diagnostic) {
        journal.replace(new EconomyJournalRecord(source.request(), state, receipt, status, diagnostic));
    }

    private static boolean validReceipt(MutationRequest request, MutationReceipt receipt) {
        return receipt != null && request.requestId().equals(receipt.requestId())
                && request.kind() == receipt.kind() && request.amountMinorUnits() == receipt.amountMinorUnits()
                && receipt.externalOperationId() != null && !receipt.externalOperationId().isBlank();
    }

    private static <T> ProviderResult<T> copyFailure(ProviderResult<?> source) {
        return new ProviderResult<>(source.status(), source.error(), Optional.empty(), Optional.empty(), source.diagnostic());
    }

    private <T> ProviderResult<T> unavailableForLifecycle() {
        EconomyLifecycleSnapshot state = lifecycle.snapshot();
        if (state.lifecycle() == ProviderLifecycle.RECOVERING || state.lifecycle() == ProviderLifecycle.FROZEN) {
            return ProviderResult.recoveryRequired(state.diagnostic());
        }
        return ProviderResult.unavailable(ProviderError.NOT_READY,
                state.diagnostic().isBlank() ? "provider is not ready" : state.diagnostic());
    }
}

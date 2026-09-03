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
import com.enviouse.futureshopsp.event.BalanceChangeEvent;
import net.neoforged.neoforge.common.NeoForge;

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
    private final EconomyCustodyStore custody;
    private final EconomyClaimStore claims;
    private final Object lock = new Object();

    public EconomyTransactionCoordinator(EconomyProvider provider,
                                         EconomyLifecycleController lifecycle,
                                         EconomyTransactionJournal journal) {
        this(provider, lifecycle, journal, new InMemoryEconomyCustodyStore(), new InMemoryEconomyClaimStore());
    }

    public EconomyTransactionCoordinator(EconomyProvider provider,
                                         EconomyLifecycleController lifecycle,
                                         EconomyTransactionJournal journal,
                                         EconomyCustodyStore custody,
                                         EconomyClaimStore claims) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.journal = Objects.requireNonNull(journal, "journal");
        this.custody = Objects.requireNonNull(custody, "custody");
        this.claims = Objects.requireNonNull(claims, "claims");
    }

    public EconomyLifecycleSnapshot lifecycle() {
        return lifecycle.snapshot();
    }

    /** Freezes admission when a confirmed provider leg cannot be finalized locally. */
    public void markRecoveryRequired(String diagnostic) {
        lifecycle.markAmbiguous(diagnostic == null || diagnostic.isBlank()
                ? "economy recovery is required"
                : diagnostic);
    }

    public Optional<CustodyRecord> custody(RequestId requestId) {
        return custody.find(Objects.requireNonNull(requestId, "requestId"));
    }

    public CustodyRecord holdCustody(RequestId requestId, UUID owner, String itemKey,
                                     long quantity, String contentHash) {
        requireReadyMutation();
        synchronized (lock) {
            Optional<CustodyRecord> existing = custody.find(requestId);
            if (existing.isPresent()) {
                CustodyRecord record = existing.orElseThrow();
                if (!record.owner().equals(owner) || !record.itemKey().equals(itemKey)
                        || record.quantity() != quantity || !record.contentHash().equals(contentHash)) {
                    throw new IllegalStateException("custody request conflicts with existing record");
                }
                return record;
            }
            return custody.hold(requestId, owner, itemKey, quantity, contentHash);
        }
    }

    public CustodyRecord deliverCustody(RequestId requestId) {
        requireCustodyAccess();
        synchronized (lock) {
            CustodyRecord current = custody.find(requestId).orElseThrow(() ->
                    new IllegalStateException("custody does not exist"));
            if (current.state() == CustodyState.DELIVERED || current.state() == CustodyState.CLAIMED) {
                return current;
            }
            return custody.transition(requestId, CustodyState.HELD, CustodyState.DELIVERED);
        }
    }

    public CustodyRecord claimCustody(RequestId requestId) {
        requireCustodyAccess();
        synchronized (lock) {
            CustodyRecord current = custody.find(requestId).orElseThrow(() ->
                    new IllegalStateException("custody does not exist"));
            if (current.state() == CustodyState.CLAIMED) {
                return current;
            }
            return custody.transition(requestId, CustodyState.DELIVERED, CustodyState.CLAIMED);
        }
    }

    public CustodyRecord releaseCustody(RequestId requestId) {
        requireCustodyAccess();
        synchronized (lock) {
            CustodyRecord current = custody.find(requestId).orElseThrow(() ->
                    new IllegalStateException("custody does not exist"));
            if (current.state() == CustodyState.RELEASED) {
                return current;
            }
            return custody.transition(requestId, CustodyState.HELD, CustodyState.RELEASED);
        }
    }

    public Optional<ClaimRecord> claim(RequestId requestId) {
        return claims.find(Objects.requireNonNull(requestId, "requestId"));
    }

    public ClaimRecord createClaim(RequestId requestId, UUID claimant, long amountMinorUnits, String description) {
        requireCustodyAccess();
        synchronized (lock) {
            Optional<ClaimRecord> existing = claims.find(requestId);
            if (existing.isPresent()) {
                ClaimRecord record = existing.orElseThrow();
                if (!record.claimant().equals(claimant) || record.amountMinorUnits() != amountMinorUnits
                        || !record.description().equals(description == null ? "" : description.trim())) {
                    throw new IllegalStateException("claim request conflicts with existing record");
                }
                return record;
            }
            return claims.create(requestId, claimant, amountMinorUnits, description);
        }
    }

    public ClaimRecord deliverClaim(RequestId requestId) {
        requireCustodyAccess();
        synchronized (lock) {
            ClaimRecord current = claims.find(requestId).orElseThrow(() ->
                    new IllegalStateException("claim does not exist"));
            if (current.state() == ClaimState.DELIVERED || current.state() == ClaimState.RESOLVED) {
                return current;
            }
            return claims.transition(requestId, ClaimState.PENDING, ClaimState.DELIVERED);
        }
    }

    public ClaimRecord resolveClaim(RequestId requestId) {
        requireCustodyAccess();
        synchronized (lock) {
            ClaimRecord current = claims.find(requestId).orElseThrow(() ->
                    new IllegalStateException("claim does not exist"));
            if (current.state() == ClaimState.RESOLVED) {
                return current;
            }
            return claims.transition(requestId, current.state(), ClaimState.RESOLVED);
        }
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

    public ProviderResult<BalanceSnapshot> preflight(MutationRequest request) {
        Objects.requireNonNull(request, "request");
        synchronized (lock) {
            return preflightInternal(request);
        }
    }

    public ProviderResult<MutationReceipt> withdraw(MutationRequest request) {
        return execute(request, MutationKind.WITHDRAW);
    }

    public ProviderResult<MutationReceipt> deposit(MutationRequest request) {
        return execute(request, MutationKind.DEPOSIT);
    }

    /** Executes one durable refund leg with its own request identity. */
    public ProviderResult<MutationReceipt> refund(MutationRequest request) {
        return execute(request, MutationKind.REFUND);
    }

    /** Executes one durable compensation leg with its own request identity. */
    public ProviderResult<MutationReceipt> compensate(MutationRequest request) {
        return execute(request, MutationKind.COMPENSATION);
    }

    public ProviderResult<MutationReceipt> executeWithCustody(MutationRequest request, UUID owner,
                                                               String itemKey, long quantity,
                                                               String contentHash, CustodyState terminalState) {
        return executeWithCustody(request, owner, itemKey, quantity, contentHash, terminalState, true);
    }

    /**
     * Executes a custodied provider leg with an explicit definitive-failure custody policy.
     * Callers that retain custody must restore or resolve it after a proven provider rejection.
     */
    public ProviderResult<MutationReceipt> executeWithCustody(MutationRequest request, UUID owner,
                                                               String itemKey, long quantity,
                                                               String contentHash, CustodyState terminalState,
                                                               boolean releaseCustodyOnDefinitiveFailure) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(terminalState, "terminalState");
        if (terminalState != CustodyState.HELD && terminalState != CustodyState.DELIVERED && terminalState != CustodyState.CLAIMED
                && terminalState != CustodyState.RELEASED) {
            return ProviderResult.rejected(ProviderError.INVALID_REQUEST, "invalid custody terminal state");
        }
        synchronized (lock) {
            EconomyJournalRecord existing;
            try {
                existing = journal.find(request.requestId()).orElse(null);
            } catch (RuntimeException exception) {
                return journalFailure("transaction journal lookup failed");
            }
            if (existing != null) {
                ProviderResult<MutationReceipt> replayed = replay(existing);
                if (replayed.confirmed()) {
                    CustodyRecord custodyRecord;
                    try {
                        custodyRecord = custody.find(request.requestId().child("custody")).orElse(null);
                    } catch (RuntimeException exception) {
                        return journalFailure("custody lookup failed during replay");
                    }
                    if (custodyRecord == null || !custodyTerminalStateMatches(custodyRecord.state(), terminalState)) {
                        return journalFailure("custody finalization requires recovery");
                    }
                }
                return replayed;
            }
            ProviderResult<BalanceSnapshot> preflight = preflightInternal(request);
            if (!preflight.confirmed()) {
                return copyFailure(preflight);
            }
            EconomyJournalRecord prepared = new EconomyJournalRecord(request,
                    EconomyTransactionState.PREPARED, Optional.empty(), ProviderResultStatus.REJECTED, "");
            try {
                journal.append(prepared);
            } catch (RuntimeException exception) {
                return journalFailure("transaction intent could not be persisted");
            }
            RequestId custodyId = request.requestId().child("custody");
            try {
                holdCustody(custodyId, owner, itemKey, quantity, contentHash);
            } catch (RuntimeException exception) {
                try {
                    replace(prepared, EconomyTransactionState.RESOLVED, Optional.empty(),
                            ProviderResultStatus.REJECTED, "custody could not be persisted");
                } catch (RuntimeException ignored) {
                    return journalFailure("custody and transaction state require recovery");
                }
                return journalFailure("custody persistence failed before provider mutation");
            }
            ProviderResult<MutationReceipt> result = executeAfterPrepared(request, request.kind());
            if (!result.confirmed()) {
                if (releaseCustodyOnDefinitiveFailure
                        && result.status() != ProviderResultStatus.AMBIGUOUS
                        && result.status() != ProviderResultStatus.RECOVERY_REQUIRED) {
                    try {
                        releaseCustody(custodyId);
                    } catch (RuntimeException exception) {
                        lifecycle.markAmbiguous("custody release failed after provider rejection");
                        return ProviderResult.recoveryRequired("custody release requires recovery");
                    }
                }
                return result;
            }
            try {
                if (terminalState == CustodyState.HELD) {
                    return result;
                }
                if (terminalState == CustodyState.CLAIMED) {
                    deliverCustody(custodyId);
                    claimCustody(custodyId);
                } else if (terminalState == CustodyState.DELIVERED) {
                    deliverCustody(custodyId);
                } else {
                    releaseCustody(custodyId);
                }
            } catch (RuntimeException exception) {
                lifecycle.markAmbiguous("custody finalization failed after provider confirmation");
                return ProviderResult.recoveryRequired("custody finalization requires recovery");
            }
            return result;
        }
    }

    public ProviderResult<MutationReceipt> transfer(UUID from, UUID to, long amountMinorUnits) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.equals(to)) {
            return ProviderResult.rejected(ProviderError.INVALID_REQUEST, "transfer target must differ");
        }
        if (!supportsAllMutationCapabilities()) {
            return ProviderResult.unavailable(ProviderError.CAPABILITY_MISSING,
                    "provider lacks the capabilities required for an atomic transfer");
        }
        RequestId root = RequestId.random();
        MutationRequest debit = new MutationRequest(root.child("transfer debit"), from, Optional.of(to), amountMinorUnits,
                MutationKind.TRANSFER_DEBIT);
        ProviderResult<MutationReceipt> debitResult = execute(debit, MutationKind.TRANSFER_DEBIT);
        if (!debitResult.confirmed()) {
            return debitResult;
        }
        MutationRequest credit = new MutationRequest(root.child("transfer credit"), to, Optional.of(from), amountMinorUnits,
                MutationKind.TRANSFER_CREDIT);
        ProviderResult<MutationReceipt> creditResult = execute(credit, MutationKind.TRANSFER_CREDIT);
        if (creditResult.confirmed()) {
            return creditResult;
        }
        MutationRequest compensation = new MutationRequest(root.child("transfer compensation"), from, Optional.of(to),
                amountMinorUnits, MutationKind.COMPENSATION);
        ProviderResult<MutationReceipt> compensationResult = compensate(compensation);
        if (!compensationResult.confirmed()) {
            lifecycle.markAmbiguous("transfer compensation requires recovery");
            return ProviderResult.recoveryRequired("transfer compensation requires recovery");
        }
        return creditResult;
    }

    public ProviderResult<MutationReceipt> recover(RequestId requestId) {
        Objects.requireNonNull(requestId, "requestId");
        synchronized (lock) {
            EconomyJournalRecord record;
            try {
                record = journal.find(requestId).orElse(null);
            } catch (RuntimeException exception) {
                return journalFailure("transaction journal lookup failed during recovery");
            }
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
                return markUncertainOrFreeze(record, "receipt lookup failed");
            }
            MutationReceipt recoveredReceipt = lookup == null ? null
                    : lookup.receipt().orElse(lookup.value().orElse(null));
            if (lookup != null && lookup.confirmed() && validReceipt(record.request(), recoveredReceipt)) {
                try {
                    replace(record, EconomyTransactionState.EXTERNAL_CONFIRMED, Optional.of(recoveredReceipt),
                            ProviderResultStatus.CONFIRMED, "");
                    replace(new EconomyJournalRecord(record.request(), EconomyTransactionState.EXTERNAL_CONFIRMED,
                                    Optional.of(recoveredReceipt), ProviderResultStatus.CONFIRMED, ""),
                            EconomyTransactionState.RESOLVED, Optional.of(recoveredReceipt),
                            ProviderResultStatus.CONFIRMED, "");
                } catch (RuntimeException exception) {
                    return journalFailure("recovered provider outcome could not be finalized");
                }
                publishConfirmedBalanceChange(record.request(), recoveredReceipt);
                lifecycle.markRecovered();
                return lookup;
            }
            if (lookup != null && lookup.status() == ProviderResultStatus.REJECTED
                    && lookup.error() != ProviderError.RECEIPT_NOT_FOUND) {
                try {
                    replace(record, EconomyTransactionState.RESOLVED, Optional.empty(),
                            ProviderResultStatus.REJECTED, lookup.diagnostic());
                } catch (RuntimeException exception) {
                    return journalFailure("rejected provider outcome could not be persisted");
                }
                lifecycle.markRecovered();
                return lookup;
            }
            return markUncertainOrFreeze(record, "provider outcome remains unknown");
        }
    }

    private ProviderResult<MutationReceipt> execute(MutationRequest request, MutationKind expectedKind) {
        Objects.requireNonNull(request, "request");
        if (request.kind() != expectedKind && !(expectedKind == MutationKind.COMPENSATION
                && request.kind() == MutationKind.COMPENSATION)) {
            return ProviderResult.rejected(ProviderError.INVALID_REQUEST, "mutation kind does not match route");
        }
        synchronized (lock) {
            EconomyJournalRecord existing;
            try {
                existing = journal.find(request.requestId()).orElse(null);
            } catch (RuntimeException exception) {
                return journalFailure("transaction journal lookup failed");
            }
            if (existing != null) {
                return replay(existing);
            }
            ProviderResult<MutationReceipt> admission = admit(request);
            if (admission != null) {
                return admission;
            }
            EconomyJournalRecord prepared = new EconomyJournalRecord(request,
                    EconomyTransactionState.PREPARED, Optional.empty(), ProviderResultStatus.REJECTED, "");
            try {
                journal.append(prepared);
            } catch (RuntimeException exception) {
                return journalFailure("transaction intent could not be persisted");
            }
            return executeAfterPrepared(request, expectedKind);
        }
    }

    private ProviderResult<MutationReceipt> executeAfterPrepared(MutationRequest request, MutationKind expectedKind) {
        EconomyJournalRecord pending = new EconomyJournalRecord(request,
                EconomyTransactionState.EXTERNAL_PENDING, Optional.empty(), ProviderResultStatus.UNAVAILABLE, "");
        try {
            journal.replace(pending);
        } catch (RuntimeException exception) {
            return journalFailure("pending transaction state could not be persisted");
        }

        ProviderResult<MutationReceipt> result;
        try {
            result = expectedKind == MutationKind.DEPOSIT || expectedKind == MutationKind.TRANSFER_CREDIT
                    || expectedKind == MutationKind.REFUND || expectedKind == MutationKind.COMPENSATION
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
            try {
                replace(pending, EconomyTransactionState.EXTERNAL_CONFIRMED, Optional.of(receipt),
                        ProviderResultStatus.CONFIRMED, "");
            } catch (RuntimeException exception) {
                return journalFailure("confirmed provider outcome could not be persisted");
            }
            try {
                replace(new EconomyJournalRecord(request, EconomyTransactionState.EXTERNAL_CONFIRMED,
                                Optional.of(receipt), ProviderResultStatus.CONFIRMED, ""),
                        EconomyTransactionState.RESOLVED, Optional.of(receipt),
                        ProviderResultStatus.CONFIRMED, "");
            } catch (RuntimeException exception) {
                return journalFailure("confirmed provider outcome could not be finalized");
            }
            publishConfirmedBalanceChange(request, receipt);
            return ProviderResult.confirmed(receipt);
        }
        if (result.status() == ProviderResultStatus.REJECTED) {
            try {
                replace(pending, EconomyTransactionState.RESOLVED, Optional.empty(),
                        ProviderResultStatus.REJECTED, result.diagnostic());
            } catch (RuntimeException exception) {
                return journalFailure("rejected provider outcome could not be persisted");
            }
            return result;
        }
        return ambiguous(pending, result.diagnostic().isBlank()
                ? "provider outcome is not definitive" : result.diagnostic());
    }

    private ProviderResult<MutationReceipt> admit(MutationRequest request) {
        ProviderResult<BalanceSnapshot> preflight = preflightInternal(request);
        return preflight.confirmed() ? null : copyFailure(preflight);
    }

    private ProviderResult<BalanceSnapshot> preflightInternal(MutationRequest request) {
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
                || request.kind() == MutationKind.REFUND || request.kind() == MutationKind.COMPENSATION
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
            return precheck;
        }
        return precheck;
    }

    private void requireReadyMutation() {
        if (!lifecycle.admitMutation()) {
            throw new IllegalStateException("economy mutations are not ready");
        }
    }

    private void requireCustodyAccess() {
        if (lifecycle.snapshot().lifecycle() == ProviderLifecycle.STOPPED) {
            throw new IllegalStateException("economy custody is stopped");
        }
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

    private static boolean custodyTerminalStateMatches(CustodyState actual, CustodyState expected) {
        return switch (expected) {
            case HELD -> actual == CustodyState.HELD || actual == CustodyState.DELIVERED
                    || actual == CustodyState.CLAIMED;
            case DELIVERED -> actual == CustodyState.DELIVERED || actual == CustodyState.CLAIMED;
            case CLAIMED -> actual == CustodyState.CLAIMED;
            case RELEASED -> actual == CustodyState.RELEASED;
        };
    }

    private ProviderResult<MutationReceipt> ambiguous(EconomyJournalRecord pending, String diagnostic) {
        return markUncertainOrFreeze(pending, diagnostic, true);
    }

    private ProviderResult<MutationReceipt> markUncertainOrFreeze(EconomyJournalRecord pending, String diagnostic) {
        return markUncertainOrFreeze(pending, diagnostic, false);
    }

    private ProviderResult<MutationReceipt> markUncertainOrFreeze(EconomyJournalRecord pending, String diagnostic,
                                                                  boolean ambiguousResult) {
        try {
            replace(pending, EconomyTransactionState.UNCERTAIN, Optional.empty(),
                    ProviderResultStatus.AMBIGUOUS, diagnostic);
        } catch (RuntimeException exception) {
            return journalFailure("transaction outcome could not be persisted");
        }
        lifecycle.markAmbiguous(diagnostic);
        return ambiguousResult ? ProviderResult.ambiguous(diagnostic) : ProviderResult.recoveryRequired(diagnostic);
    }

    private ProviderResult<MutationReceipt> journalFailure(String diagnostic) {
        lifecycle.markAmbiguous(diagnostic);
        return ProviderResult.recoveryRequired(diagnostic);
    }

    private boolean supports(EconomyCapability capability) {
        try {
            ProviderCapabilities capabilities = provider.capabilities();
            return capabilities != null && capabilities.supports(capability);
        } catch (RuntimeException exception) {
            lifecycle.markFailed("provider capability lookup failed");
            return false;
        }
    }

    private static void publishConfirmedBalanceChange(MutationRequest request, MutationReceipt receipt) {
        if (receipt == null || receipt.resultingBalanceMinorUnits().isEmpty()) {
            return;
        }
        long delta = switch (request.kind()) {
            case DEPOSIT, TRANSFER_CREDIT, REFUND -> request.amountMinorUnits();
            case WITHDRAW, TRANSFER_DEBIT, FEE, COMPENSATION -> -request.amountMinorUnits();
        };
        String reason = switch (request.kind()) {
            case DEPOSIT -> "DEPOSIT";
            case WITHDRAW -> "WITHDRAW";
            case TRANSFER_DEBIT, TRANSFER_CREDIT -> "TRANSFER";
            case FEE -> "FEE";
            case REFUND -> "REFUND";
            case COMPENSATION -> "COMPENSATION";
        };
        NeoForge.EVENT_BUS.post(new BalanceChangeEvent.Post(request.actor(), delta, reason,
                receipt.resultingBalanceMinorUnits().getAsLong()));
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

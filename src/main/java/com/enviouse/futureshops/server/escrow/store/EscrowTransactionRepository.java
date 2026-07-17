package com.enviouse.futureshops.server.escrow.store;

import com.enviouse.futureshops.server.escrow.model.EscrowRequestKey;
import com.enviouse.futureshops.server.escrow.model.EscrowState;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;
import com.enviouse.futureshops.server.escrow.model.EscrowTransactionId;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

public final class EscrowTransactionRepository {
    private final int maximumRecords;
    private final Map<EscrowTransactionId, EscrowTransaction> transactions = new LinkedHashMap<>();
    private final Map<EscrowRequestKey, EscrowTransactionId> requestKeys = new LinkedHashMap<>();
    private final NavigableMap<EscrowTransactionId, EscrowTransaction> recoveryCandidates =
            new TreeMap<>(Comparator.comparing(EscrowTransactionId::toString));

    public EscrowTransactionRepository(int maximumRecords) {
        if (maximumRecords <= 0) {
            throw new IllegalArgumentException("Escrow store record limit must be positive");
        }
        this.maximumRecords = maximumRecords;
    }

    public synchronized EscrowStoreApplyResult apply(EscrowTransaction incoming) {
        return evaluate(incoming, true);
    }

    public synchronized EscrowStoreApplyResult preflight(EscrowTransaction incoming) {
        return evaluate(incoming, false);
    }

    public synchronized EscrowStoreApplyResult applyFoldedHeld(
            EscrowTransaction incoming
    ) {
        return evaluateFoldedHeld(incoming, true);
    }

    public synchronized EscrowStoreApplyResult preflightFoldedHeld(
            EscrowTransaction incoming
    ) {
        return evaluateFoldedHeld(incoming, false);
    }

    public synchronized EscrowStoreApplyResult applyFoldedCompletion(
            EscrowTransaction held,
            EscrowTransaction completed
    ) {
        return evaluateFoldedCompletion(held, completed, true);
    }

    public synchronized EscrowStoreApplyResult preflightFoldedCompletion(
            EscrowTransaction held,
            EscrowTransaction completed
    ) {
        return evaluateFoldedCompletion(held, completed, false);
    }

    public synchronized EscrowStoreApplyResult applyFoldedRefund(
            EscrowTransaction held,
            EscrowTransaction refunded
    ) {
        return evaluateFoldedRefund(held, refunded, true);
    }

    public synchronized EscrowStoreApplyResult preflightFoldedRefund(
            EscrowTransaction held,
            EscrowTransaction refunded
    ) {
        return evaluateFoldedRefund(held, refunded, false);
    }

    private EscrowStoreApplyResult evaluate(EscrowTransaction incoming, boolean commit) {
        Objects.requireNonNull(incoming, "incoming");
        EscrowTransactionNbtCodec.validateBounds(incoming);
        EscrowTransaction existing = transactions.get(incoming.transactionId());
        EscrowTransactionId requestOwner = requestKeys.get(incoming.requestKey());
        if (requestOwner != null && !requestOwner.equals(incoming.transactionId())) {
            throw new EscrowStoreConflictException("Escrow request key belongs to another transaction");
        }
        if (existing == null) {
            requireNewTransaction(incoming);
            requireCapacity();
            if (commit) {
                transactions.put(incoming.transactionId(), incoming);
                requestKeys.put(incoming.requestKey(), incoming.transactionId());
                recoveryCandidates.put(incoming.transactionId(), incoming);
            }
            return new EscrowStoreApplyResult(incoming, true, false);
        }

        requireImmutableFields(existing, incoming);
        if (incoming.revision() < existing.revision()) {
            if (incoming.timestamps().updatedAt().isAfter(existing.timestamps().updatedAt())) {
                throw new EscrowStoreConflictException("Older escrow revision has a newer update time");
            }
            return new EscrowStoreApplyResult(existing, false, true);
        }
        if (incoming.revision() == existing.revision()) {
            if (!incoming.equals(existing)) {
                throw new EscrowStoreConflictException("Escrow revision contains conflicting data");
            }
            return new EscrowStoreApplyResult(existing, false, true);
        }
        long expectedRevision;
        try {
            expectedRevision = Math.addExact(existing.revision(), 1L);
        } catch (ArithmeticException exception) {
            throw new EscrowStoreConflictException("Escrow revision is exhausted", exception);
        }
        if (incoming.revision() != expectedRevision) {
            throw new EscrowStoreConflictException("Escrow revision skips persisted state");
        }
        EscrowTransaction expected = expectedTransition(existing, incoming);
        if (!expected.equals(incoming)) {
            throw new EscrowStoreConflictException("Escrow transition data does not match its previous revision");
        }
        if (commit) {
            transactions.put(incoming.transactionId(), incoming);
            if (incoming.state().isTerminal()) {
                recoveryCandidates.remove(incoming.transactionId());
            } else {
                recoveryCandidates.put(incoming.transactionId(), incoming);
            }
        }
        return new EscrowStoreApplyResult(incoming, true, false);
    }

    private EscrowStoreApplyResult evaluateFoldedHeld(
            EscrowTransaction incoming,
            boolean commit
    ) {
        Objects.requireNonNull(incoming, "incoming");
        EscrowTransactionNbtCodec.validateBounds(incoming);
        if (incoming.state() != EscrowState.HELD) {
            throw new EscrowStoreConflictException(
                    "Folded escrow reservation must end held");
        }
        EscrowTransaction existing = transactions.get(incoming.transactionId());
        EscrowTransactionId requestOwner = requestKeys.get(incoming.requestKey());
        if (requestOwner != null
                && !requestOwner.equals(incoming.transactionId())) {
            throw new EscrowStoreConflictException(
                    "Escrow request key belongs to another transaction");
        }
        if (existing != null
                && existing.revision() >= incoming.revision()) {
            return evaluate(incoming, commit);
        }
        EscrowTransaction candidate = existing == null
                ? EscrowTransaction.create(incoming.transactionId(),
                incoming.parentTransactionId(), incoming.requestKey(),
                incoming.operation(), incoming.participants(),
                incoming.assetLots(), incoming.timestamps().createdAt(),
                incoming.configRevision(), incoming.shopReference())
                : existing;
        if (existing != null) {
            requireImmutableFields(existing, incoming);
        }
        while (candidate.state() != EscrowState.HELD) {
            EscrowState next = switch (candidate.state()) {
                case CREATED -> EscrowState.VALIDATED;
                case VALIDATED -> EscrowState.HOLDING;
                case HOLDING -> EscrowState.HELD;
                default -> throw new EscrowStoreConflictException(
                        "Folded escrow reservation prefix is invalid");
            };
            try {
                candidate = candidate.transitionTo(
                        next, incoming.timestamps().updatedAt());
            } catch (RuntimeException exception) {
                throw new EscrowStoreConflictException(
                        "Folded escrow reservation transition is invalid",
                        exception);
            }
        }
        if (!candidate.equals(incoming)) {
            throw new EscrowStoreConflictException(
                    "Folded escrow reservation does not match its lifecycle");
        }
        if (existing == null) {
            requireCapacity();
        }
        if (commit) {
            transactions.put(incoming.transactionId(), incoming);
            requestKeys.put(incoming.requestKey(), incoming.transactionId());
            recoveryCandidates.put(incoming.transactionId(), incoming);
        }
        return new EscrowStoreApplyResult(incoming, true, false);
    }

    private EscrowStoreApplyResult evaluateFoldedCompletion(
            EscrowTransaction held,
            EscrowTransaction completed,
            boolean commit
    ) {
        Objects.requireNonNull(held, "held");
        Objects.requireNonNull(completed, "completed");
        EscrowTransactionNbtCodec.validateBounds(held);
        EscrowTransactionNbtCodec.validateBounds(completed);
        if (held.state() != EscrowState.HELD
                || completed.state() != EscrowState.COMPLETED
                || !held.transactionId().equals(completed.transactionId())) {
            throw new EscrowStoreConflictException(
                    "Folded escrow completion identity is invalid");
        }
        requireImmutableFields(held, completed);
        java.time.Instant decisionAt = completed.timestamps()
                .commitDecidedAt().orElseThrow(() ->
                        new EscrowStoreConflictException(
                                "Folded escrow completion lacks a decision time"));
        java.time.Instant terminalAt = completed.timestamps()
                .terminalAt().orElseThrow(() ->
                        new EscrowStoreConflictException(
                                "Folded escrow completion lacks a terminal time"));
        List<EscrowTransaction> lifecycle;
        try {
            EscrowTransaction decided = held.transitionTo(
                    EscrowState.COMMIT_DECIDED, decisionAt);
            EscrowTransaction committed = decided.transitionTo(
                    EscrowState.COMMITTED, decisionAt);
            EscrowTransaction claimsCreated = committed.transitionTo(
                    EscrowState.CLAIMS_CREATED, decisionAt);
            EscrowTransaction terminal = claimsCreated.transitionTo(
                    EscrowState.COMPLETED, terminalAt);
            lifecycle = List.of(held, decided, committed, claimsCreated,
                    terminal);
        } catch (RuntimeException exception) {
            throw new EscrowStoreConflictException(
                    "Folded escrow completion transition is invalid",
                    exception);
        }
        if (!lifecycle.get(lifecycle.size() - 1).equals(completed)) {
            throw new EscrowStoreConflictException(
                    "Folded escrow completion does not match its lifecycle");
        }
        EscrowTransaction existing = transactions.get(completed.transactionId());
        if (existing == null) {
            throw new EscrowStoreConflictException(
                    "Folded escrow completion lacks its held transaction");
        }
        if (existing.revision() >= completed.revision()) {
            return evaluate(completed, commit);
        }
        if (!lifecycle.contains(existing)) {
            throw new EscrowStoreConflictException(
                    "Folded escrow completion prefix is invalid");
        }
        if (commit) {
            transactions.put(completed.transactionId(), completed);
            recoveryCandidates.remove(completed.transactionId());
        }
        return new EscrowStoreApplyResult(completed, true, false);
    }

    private EscrowStoreApplyResult evaluateFoldedRefund(
            EscrowTransaction held,
            EscrowTransaction refunded,
            boolean commit
    ) {
        Objects.requireNonNull(held, "held");
        Objects.requireNonNull(refunded, "refunded");
        EscrowTransactionNbtCodec.validateBounds(held);
        EscrowTransactionNbtCodec.validateBounds(refunded);
        if (held.state() != EscrowState.HELD
                || refunded.state() != EscrowState.REFUNDED
                || !held.transactionId().equals(refunded.transactionId())) {
            throw new EscrowStoreConflictException(
                    "Folded escrow refund identity is invalid");
        }
        requireImmutableFields(held, refunded);
        java.time.Instant terminalAt = refunded.timestamps()
                .terminalAt().orElseThrow(() ->
                        new EscrowStoreConflictException(
                                "Folded escrow refund lacks a terminal time"));
        List<EscrowTransaction> lifecycle;
        try {
            EscrowTransaction aborting = held.transitionTo(
                    EscrowState.ABORTING, terminalAt);
            EscrowTransaction pending = aborting.transitionTo(
                    EscrowState.REFUND_PENDING, terminalAt);
            EscrowTransaction terminal = pending.transitionTo(
                    EscrowState.REFUNDED, terminalAt);
            lifecycle = List.of(held, aborting, pending, terminal);
        } catch (RuntimeException exception) {
            throw new EscrowStoreConflictException(
                    "Folded escrow refund transition is invalid", exception);
        }
        if (!lifecycle.get(lifecycle.size() - 1).equals(refunded)) {
            throw new EscrowStoreConflictException(
                    "Folded escrow refund does not match its lifecycle");
        }
        EscrowTransaction existing = transactions.get(
                refunded.transactionId());
        if (existing == null) {
            throw new EscrowStoreConflictException(
                    "Folded escrow refund lacks its held transaction");
        }
        if (existing.revision() >= refunded.revision()) {
            if (!existing.equals(refunded)) {
                throw new EscrowStoreConflictException(
                        "Folded escrow refund conflicts with terminal state");
            }
            return evaluate(refunded, commit);
        }
        if (!lifecycle.contains(existing)) {
            throw new EscrowStoreConflictException(
                    "Folded escrow refund prefix is invalid");
        }
        if (commit) {
            transactions.put(refunded.transactionId(), refunded);
            recoveryCandidates.remove(refunded.transactionId());
        }
        return new EscrowStoreApplyResult(refunded, true, false);
    }

    public synchronized EscrowTransaction get(EscrowTransactionId transactionId) {
        return transactions.get(Objects.requireNonNull(transactionId, "transactionId"));
    }

    public synchronized EscrowTransaction getByRequestKey(EscrowRequestKey requestKey) {
        EscrowTransactionId transactionId = requestKeys.get(Objects.requireNonNull(requestKey, "requestKey"));
        return transactionId == null ? null : transactions.get(transactionId);
    }

    public synchronized Map<EscrowTransactionId, EscrowTransaction> snapshot() {
        return Map.copyOf(transactions);
    }

    public synchronized int size() {
        return transactions.size();
    }

    public synchronized List<EscrowTransaction> recoveryCandidatesAfter(
            Optional<EscrowTransactionId> after,
            int limit
    ) {
        Objects.requireNonNull(after, "after");
        if (limit <= 0 || limit > 10_000) {
            throw new IllegalArgumentException("Invalid escrow recovery query limit");
        }
        NavigableMap<EscrowTransactionId, EscrowTransaction> selected = after.isPresent()
                ? recoveryCandidates.tailMap(after.orElseThrow(), false)
                : recoveryCandidates;
        return selected.values().stream().limit(limit).toList();
    }

    synchronized void restore(Collection<EscrowTransaction> restored) {
        Objects.requireNonNull(restored, "restored");
        if (restored.size() > maximumRecords) {
            throw new IllegalStateException("Escrow transaction store exceeds its record limit");
        }
        Map<EscrowTransactionId, EscrowTransaction> restoredTransactions = new LinkedHashMap<>();
        Map<EscrowRequestKey, EscrowTransactionId> restoredRequestKeys = new LinkedHashMap<>();
        for (EscrowTransaction transaction : restored) {
            Objects.requireNonNull(transaction, "restored transaction");
            EscrowTransactionNbtCodec.validateBounds(transaction);
            EscrowTransaction previous = restoredTransactions.put(transaction.transactionId(), transaction);
            if (previous != null) {
                throw new IllegalStateException("Duplicate escrow transaction ID");
            }
            EscrowTransactionId previousRequest = restoredRequestKeys.put(
                    transaction.requestKey(), transaction.transactionId());
            if (previousRequest != null) {
                throw new IllegalStateException("Duplicate escrow request key");
            }
        }
        transactions.clear();
        transactions.putAll(restoredTransactions);
        requestKeys.clear();
        requestKeys.putAll(restoredRequestKeys);
        recoveryCandidates.clear();
        restoredTransactions.values().stream()
                .filter(transaction -> !transaction.state().isTerminal())
                .forEach(transaction -> recoveryCandidates.put(
                        transaction.transactionId(), transaction));
    }

    private void requireNewTransaction(EscrowTransaction transaction) {
        if (transaction.revision() != 0L || transaction.state() != EscrowState.CREATED) {
            throw new EscrowStoreConflictException("New escrow transaction must begin at created revision zero");
        }
    }

    private void requireCapacity() {
        if (transactions.size() >= maximumRecords) {
            throw new IllegalStateException("Escrow transaction store exceeds its record limit");
        }
    }

    private static void requireImmutableFields(EscrowTransaction existing, EscrowTransaction incoming) {
        boolean matches = existing.transactionId().equals(incoming.transactionId())
                && existing.parentTransactionId().equals(incoming.parentTransactionId())
                && existing.requestKey().equals(incoming.requestKey())
                && existing.operation() == incoming.operation()
                && existing.participants().equals(incoming.participants())
                && existing.assetLots().equals(incoming.assetLots())
                && existing.timestamps().createdAt().equals(incoming.timestamps().createdAt())
                && existing.configRevision() == incoming.configRevision()
                && existing.shopReference().equals(incoming.shopReference());
        if (!matches) {
            throw new EscrowStoreConflictException("Escrow transaction immutable data changed");
        }
    }

    private static EscrowTransaction expectedTransition(
            EscrowTransaction existing,
            EscrowTransaction incoming
    ) {
        try {
            if (incoming.state() == EscrowState.RECOVERY_REQUIRED) {
                return existing.requireRecoveryFrom(
                        incoming.retryMetadata().resumeState().orElseThrow(),
                        incoming.lastError().orElseThrow(),
                        incoming.retryMetadata().maxAttempts(),
                        incoming.retryMetadata().nextAttemptAt().orElseThrow(),
                        incoming.timestamps().updatedAt());
            }
            if (existing.state() == EscrowState.MANUAL_REVIEW) {
                return existing.resolveManualReviewTo(incoming.state(), incoming.timestamps().updatedAt());
            }
            return existing.transitionTo(incoming.state(), incoming.timestamps().updatedAt());
        } catch (RuntimeException exception) {
            throw new EscrowStoreConflictException("Escrow state transition is invalid", exception);
        }
    }
}

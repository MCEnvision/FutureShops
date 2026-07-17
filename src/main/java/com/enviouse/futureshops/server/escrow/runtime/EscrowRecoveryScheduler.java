package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.model.EscrowOperation;
import com.enviouse.futureshops.server.escrow.model.EscrowState;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;
import com.enviouse.futureshops.server.escrow.model.EscrowTransactionId;
import com.enviouse.futureshops.server.escrow.store.EscrowTransactionSavedData;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;

public final class EscrowRecoveryScheduler {
    private static final Set<EscrowOperation> LONG_LIVED_HELD_OPERATIONS = EnumSet.of(
            EscrowOperation.AUCTION_LISTING,
            EscrowOperation.AUCTION_BID,
            EscrowOperation.BAZAAR_BUY_ORDER,
            EscrowOperation.BAZAAR_SELL_ORDER);

    private final EscrowTransactionSavedData transactions;
    private final Clock clock;
    private final Map<EscrowOperation, EscrowRecoveryHandler> handlers =
            new EnumMap<>(EscrowOperation.class);
    private final Map<EscrowTransactionId, WorkEntry> work = new LinkedHashMap<>();
    private final Map<EscrowRecoveryWorkStatus, Integer> statusCounts =
            new EnumMap<>(EscrowRecoveryWorkStatus.class);
    private final Map<EscrowOperation, LinkedHashSet<EscrowTransactionId>> blockedByOperation =
            new EnumMap<>(EscrowOperation.class);
    private final ArrayDeque<EscrowTransactionId> ready = new ArrayDeque<>();
    private final Set<EscrowTransactionId> readyIds = new HashSet<>();
    private final PriorityQueue<ScheduledEntry> scheduled = new PriorityQueue<>(
            Comparator.comparing(ScheduledEntry::dueAt)
                    .thenComparing(value -> value.transactionId().toString()));

    private Optional<EscrowTransactionId> enumerationCursor = Optional.empty();
    private boolean enumerationComplete;

    public EscrowRecoveryScheduler(EscrowTransactionSavedData transactions) {
        this(transactions, Clock.systemUTC());
    }

    EscrowRecoveryScheduler(EscrowTransactionSavedData transactions, Clock clock) {
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public synchronized void register(EscrowOperation operation, EscrowRecoveryHandler handler) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(handler, "handler");
        EscrowRecoveryHandler existing = handlers.putIfAbsent(operation, handler);
        if (existing != null && existing != handler) {
            throw new EscrowRuntimeException("Escrow recovery handler is already registered");
        }
    }

    public synchronized int enumerateBatch(int maximumRecords) {
        requireLimit(maximumRecords);
        if (enumerationComplete) {
            return 0;
        }
        List<EscrowTransaction> candidates = transactions.recoveryCandidatesAfter(
                enumerationCursor, maximumRecords);
        Instant now = clock.instant();
        for (EscrowTransaction transaction : candidates) {
            classify(transaction, now, initialDetail(transaction));
            enumerationCursor = Optional.of(transaction.transactionId());
        }
        if (candidates.size() < maximumRecords) {
            enumerationComplete = true;
        }
        return candidates.size();
    }

    public synchronized void enqueue(EscrowTransaction transaction) {
        Objects.requireNonNull(transaction, "transaction");
        if (transaction.state().isTerminal()) {
            remove(transaction.transactionId());
        } else {
            classify(transaction, clock.instant(), initialDetail(transaction));
        }
    }

    public synchronized EscrowRecoveryBatchResult processBatch(int maximumRecords) {
        requireLimit(maximumRecords);
        Instant now = clock.instant();
        promoteDue(now);
        if (statusCount(EscrowRecoveryWorkStatus.READY) == 0) {
            int activated = activateBlocked(maximumRecords, now);
            if (activated > 0) {
                return new EscrowRecoveryBatchResult(
                        activated, 0, 0, activeOrBlockingCount());
            }
        }
        int examined = 0;
        int invoked = 0;
        int resolved = 0;
        int availableAtStart = ready.size();
        int polled = 0;
        while (examined < maximumRecords && polled < availableAtStart && !ready.isEmpty()) {
            EscrowTransactionId transactionId = ready.removeFirst();
            polled++;
            readyIds.remove(transactionId);
            WorkEntry entry = work.get(transactionId);
            if (entry == null || entry.status() != EscrowRecoveryWorkStatus.READY) {
                continue;
            }
            EscrowTransaction transaction = transactions.getTransaction(transactionId);
            examined++;
            if (transaction == null) {
                block(transactionId, "Recovery transaction is missing from materialized state");
                continue;
            }
            if (transaction.state().isTerminal()) {
                remove(transactionId);
                resolved++;
                continue;
            }
            EscrowRecoveryHandler handler = handlers.get(transaction.operation());
            if (handler == null) {
                classify(transaction, now,
                        "No recovery handler is registered for this operation");
                continue;
            }
            invoked++;
            try {
                EscrowRecoveryAttempt attempt = Objects.requireNonNull(
                        handler.recover(transaction), "recovery attempt");
                EscrowTransaction current = transactions.getTransaction(transactionId);
                if (current != null && current.state().isTerminal()) {
                    remove(transactionId);
                    resolved++;
                    continue;
                }
                switch (attempt.disposition()) {
                    case RESOLVED -> {
                        remove(transactionId);
                        resolved++;
                    }
                    case STABLE -> park(transactionId, EscrowRecoveryWorkStatus.STABLE,
                            attempt.detail(), Optional.empty());
                    case MANUAL_REVIEW -> park(transactionId,
                            EscrowRecoveryWorkStatus.MANUAL_REVIEW,
                            attempt.detail(), Optional.empty());
                    case RETRY_LATER -> retryLater(transaction, current, attempt, now);
                    case PROGRESSED -> progressed(transaction, current, attempt.detail(), now);
                }
            } catch (RuntimeException exception) {
                String message = exception.getMessage();
                block(transactionId, message == null || message.isBlank()
                        ? exception.getClass().getSimpleName() : truncate(message));
            }
        }
        return new EscrowRecoveryBatchResult(
                examined, invoked, resolved, activeOrBlockingCount());
    }

    public synchronized boolean hasPendingOrEnumeration() {
        promoteDue(clock.instant());
        return !enumerationComplete
                || statusCount(EscrowRecoveryWorkStatus.READY) > 0;
    }

    public synchronized boolean hasBlockingWork() {
        return statusCount(EscrowRecoveryWorkStatus.BLOCKED) > 0;
    }

    public synchronized boolean hasRunnableWork() {
        promoteDue(clock.instant());
        return statusCount(EscrowRecoveryWorkStatus.READY) > 0
                || hasActivatableBlocked();
    }

    public synchronized boolean hasScheduledWork() {
        return statusCount(EscrowRecoveryWorkStatus.SCHEDULED) > 0;
    }

    public synchronized boolean hasManualReviewWork() {
        return statusCount(EscrowRecoveryWorkStatus.MANUAL_REVIEW) > 0;
    }

    public synchronized boolean enumerationComplete() {
        return enumerationComplete;
    }

    public synchronized int pendingCount() {
        return work.size();
    }

    public synchronized List<EscrowRecoveryWork> pending(int limit) {
        requireLimit(limit);
        List<EscrowRecoveryWork> result = new ArrayList<>(Math.min(limit, work.size()));
        for (Map.Entry<EscrowTransactionId, WorkEntry> value : work.entrySet()) {
            if (result.size() == limit) {
                break;
            }
            EscrowTransaction transaction = transactions.getTransaction(value.getKey());
            if (transaction != null && !transaction.state().isTerminal()) {
                WorkEntry entry = value.getValue();
                result.add(new EscrowRecoveryWork(
                        transaction,
                        handlers.containsKey(transaction.operation()),
                        entry.status(),
                        entry.nextAttemptAt(),
                        entry.status() == EscrowRecoveryWorkStatus.BLOCKED,
                        entry.detail()));
            }
        }
        return List.copyOf(result);
    }

    private void classify(EscrowTransaction transaction, Instant now, String detail) {
        EscrowTransactionId id = transaction.transactionId();
        if (transaction.state().isTerminal()) {
            remove(id);
            return;
        }
        if (transaction.state() == EscrowState.MANUAL_REVIEW) {
            park(id, EscrowRecoveryWorkStatus.MANUAL_REVIEW, detail, Optional.empty());
            return;
        }
        if (transaction.state() == EscrowState.HELD
                && LONG_LIVED_HELD_OPERATIONS.contains(transaction.operation())) {
            park(id, EscrowRecoveryWorkStatus.STABLE,
                    "Long lived held transaction is stable", Optional.empty());
            return;
        }
        Optional<Instant> nextAttempt = transaction.retryMetadata().nextAttemptAt();
        if (transaction.state() == EscrowState.RECOVERY_REQUIRED
                && nextAttempt.filter(value -> value.isAfter(now)).isPresent()) {
            schedule(id, nextAttempt.orElseThrow(), detail);
            return;
        }
        if (!handlers.containsKey(transaction.operation())) {
            block(id, detail.startsWith("No recovery handler")
                    ? detail : "No recovery handler is registered for this operation");
            return;
        }
        makeReady(id, detail);
    }

    private void retryLater(EscrowTransaction before, EscrowTransaction current,
                            EscrowRecoveryAttempt attempt, Instant now) {
        if (current == null) {
            block(before.transactionId(), "Recovery transaction disappeared after retry attempt");
            return;
        }
        if (current.state() == EscrowState.RECOVERY_REQUIRED
                && current.retryMetadata().attemptCount()
                >= current.retryMetadata().maxAttempts()) {
            park(current.transactionId(), EscrowRecoveryWorkStatus.MANUAL_REVIEW,
                    "Recovery retry attempts are exhausted. " + attempt.detail(),
                    Optional.empty());
            return;
        }
        Instant due = attempt.nextAttemptAt().orElseThrow();
        if (!due.isAfter(now)) {
            block(current.transactionId(), "Recovery retry time must be in the future");
            return;
        }
        if (current.revision() <= before.revision()
                || current.state() != EscrowState.RECOVERY_REQUIRED
                || !current.retryMetadata().nextAttemptAt().equals(Optional.of(due))) {
            block(current.transactionId(),
                    "Recovery retry was not durably scheduled on the transaction");
            return;
        }
        schedule(current.transactionId(), due, attempt.detail());
    }

    private void progressed(EscrowTransaction before, EscrowTransaction current,
                            String detail, Instant now) {
        if (current == null || current.revision() <= before.revision()) {
            block(before.transactionId(),
                    "Recovery reported progress without a durable transaction revision");
            return;
        }
        classify(current, now, detail);
    }

    private void makeReady(EscrowTransactionId id, String detail) {
        clearBlockedIndex(id);
        putWork(id, new WorkEntry(EscrowRecoveryWorkStatus.READY,
                truncate(detail), Optional.empty()));
        if (readyIds.add(id)) {
            ready.addLast(id);
        }
    }

    private void schedule(EscrowTransactionId id, Instant dueAt, String detail) {
        clearBlockedIndex(id);
        putWork(id, new WorkEntry(EscrowRecoveryWorkStatus.SCHEDULED,
                truncate(detail), Optional.of(dueAt)));
        readyIds.remove(id);
        scheduled.add(new ScheduledEntry(id, dueAt));
    }

    private void park(EscrowTransactionId id, EscrowRecoveryWorkStatus status,
                      String detail, Optional<Instant> nextAttemptAt) {
        if (status != EscrowRecoveryWorkStatus.BLOCKED) {
            clearBlockedIndex(id);
        }
        putWork(id, new WorkEntry(status, truncate(detail), nextAttemptAt));
        readyIds.remove(id);
    }

    private void block(EscrowTransactionId id, String detail) {
        park(id, EscrowRecoveryWorkStatus.BLOCKED, detail, Optional.empty());
        EscrowTransaction transaction = transactions.getTransaction(id);
        if (transaction != null) {
            blockedByOperation.computeIfAbsent(
                    transaction.operation(), ignored -> new LinkedHashSet<>()).add(id);
        }
    }

    private void remove(EscrowTransactionId id) {
        clearBlockedIndex(id);
        removeWork(id);
        readyIds.remove(id);
    }

    private void promoteDue(Instant now) {
        while (!scheduled.isEmpty() && !scheduled.peek().dueAt().isAfter(now)) {
            ScheduledEntry candidate = scheduled.remove();
            WorkEntry entry = work.get(candidate.transactionId());
            if (entry == null || entry.status() != EscrowRecoveryWorkStatus.SCHEDULED
                    || !entry.nextAttemptAt().equals(Optional.of(candidate.dueAt()))) {
                continue;
            }
            EscrowTransaction transaction = transactions.getTransaction(candidate.transactionId());
            if (transaction == null) {
                block(candidate.transactionId(),
                        "Recovery transaction is missing from materialized state");
            } else {
                classify(transaction, now, entry.detail());
            }
        }
    }

    private int activeOrBlockingCount() {
        return Math.addExact(statusCount(EscrowRecoveryWorkStatus.READY),
                statusCount(EscrowRecoveryWorkStatus.BLOCKED));
    }

    private int activateBlocked(int maximumRecords, Instant now) {
        int activated = 0;
        for (EscrowOperation operation : EscrowOperation.values()) {
            if (activated == maximumRecords || !handlers.containsKey(operation)) {
                continue;
            }
            LinkedHashSet<EscrowTransactionId> blocked = blockedByOperation.get(operation);
            while (activated < maximumRecords && blocked != null && !blocked.isEmpty()) {
                EscrowTransactionId id = blocked.iterator().next();
                blocked.remove(id);
                WorkEntry entry = work.get(id);
                EscrowTransaction transaction = transactions.getTransaction(id);
                if (entry != null && entry.status() == EscrowRecoveryWorkStatus.BLOCKED
                        && transaction != null && transaction.operation() == operation) {
                    classify(transaction, now, entry.detail());
                    activated++;
                }
            }
        }
        return activated;
    }

    private boolean hasActivatableBlocked() {
        for (Map.Entry<EscrowOperation, LinkedHashSet<EscrowTransactionId>> value
                : blockedByOperation.entrySet()) {
            if (handlers.containsKey(value.getKey()) && !value.getValue().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private void clearBlockedIndex(EscrowTransactionId id) {
        for (LinkedHashSet<EscrowTransactionId> blocked : blockedByOperation.values()) {
            blocked.remove(id);
        }
    }

    private void putWork(EscrowTransactionId id, WorkEntry entry) {
        WorkEntry previous = work.put(id, entry);
        if (previous != null) {
            changeStatusCount(previous.status(), -1);
        }
        changeStatusCount(entry.status(), 1);
    }

    private void removeWork(EscrowTransactionId id) {
        WorkEntry removed = work.remove(id);
        if (removed != null) {
            changeStatusCount(removed.status(), -1);
        }
    }

    private int statusCount(EscrowRecoveryWorkStatus status) {
        return statusCounts.getOrDefault(status, 0);
    }

    private void changeStatusCount(EscrowRecoveryWorkStatus status, int delta) {
        int next = Math.addExact(statusCount(status), delta);
        if (next < 0) {
            throw new EscrowRuntimeException("Escrow recovery status count is invalid");
        }
        if (next == 0) {
            statusCounts.remove(status);
        } else {
            statusCounts.put(status, next);
        }
    }

    private static String initialDetail(EscrowTransaction transaction) {
        if (transaction.state() == EscrowState.RECOVERY_REQUIRED) {
            return "Transaction is marked recovery required";
        }
        if (transaction.state() == EscrowState.MANUAL_REVIEW) {
            return "Transaction requires manual review";
        }
        return "Nonterminal transaction was recovered after restart";
    }

    private static String truncate(String value) {
        String normalized = Objects.requireNonNull(value, "detail").trim();
        if (normalized.isEmpty()) {
            normalized = "Recovery detail was not provided";
        }
        return normalized.length() <= 1024 ? normalized : normalized.substring(0, 1024);
    }

    private static void requireLimit(int limit) {
        if (limit <= 0 || limit > EscrowRuntimeCoordinator.MAX_RECOVERY_BATCH_SIZE) {
            throw new IllegalArgumentException("Invalid escrow recovery work limit");
        }
    }

    private record WorkEntry(EscrowRecoveryWorkStatus status, String detail,
                             Optional<Instant> nextAttemptAt) {
    }

    private record ScheduledEntry(EscrowTransactionId transactionId, Instant dueAt) {
    }
}

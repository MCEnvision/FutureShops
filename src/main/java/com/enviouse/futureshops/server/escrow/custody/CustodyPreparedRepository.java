package com.enviouse.futureshops.server.escrow.custody;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

final class CustodyPreparedRepository {
    static final int MAX_QUERY_LIMIT = 10_000;
    static final int MAX_INTENTS = 1_000_000;

    private final Map<UUID, CustodyPreparedOperation> intents = new HashMap<>();
    private final Map<String, UUID> requestIndex = new HashMap<>();
    private final NavigableMap<PreparedOrderKey, UUID> unresolved = new TreeMap<>(
            Comparator.comparing(PreparedOrderKey::preparedAt)
                    .thenComparing(value -> value.intentId().toString()));

    synchronized CustodyPreparedResult prepare(CustodyPreparedOperation intent) {
        return evaluatePrepare(intent, true);
    }

    synchronized CustodyPreparedResult preflight(CustodyPreparedOperation intent) {
        return evaluatePrepare(intent, false);
    }

    synchronized void preflightAdditional(int count) {
        if (count <= 0 || count > CustodyBatchPlan.MAX_BATCH_LOTS
                || Math.addExact(intents.size(), count) > MAX_INTENTS) {
            throw new CustodyConflictException("Prepared custody intent limit is exceeded");
        }
    }

    private CustodyPreparedResult evaluatePrepare(CustodyPreparedOperation intent,
                                                   boolean commit) {
        Objects.requireNonNull(intent, "intent");
        if (intent.status() != CustodyPreparedStatus.PREPARED) {
            throw new CustodyConflictException("Only unresolved custody intents can be prepared");
        }
        UUID indexedId = requestIndex.get(intent.requestKey());
        if (indexedId != null) {
            CustodyPreparedOperation existing = intents.get(indexedId);
            if (existing == null || !samePreparation(existing, intent)) {
                throw new CustodyConflictException("Custody prepare request was reused with different content");
            }
            return new CustodyPreparedResult(existing, true);
        }
        CustodyPreparedOperation existing = intents.get(intent.intentId());
        if (existing != null) {
            throw new CustodyConflictException("Prepared custody intent ID already exists");
        }
        if (intents.size() >= MAX_INTENTS) {
            throw new CustodyConflictException("Prepared custody intent limit is exceeded");
        }
        if (commit) {
            intents.put(intent.intentId(), intent);
            requestIndex.put(intent.requestKey(), intent.intentId());
            unresolved.put(PreparedOrderKey.from(intent), intent.intentId());
        }
        return new CustodyPreparedResult(intent, false);
    }

    synchronized CustodyPreparedResult resolve(CustodyOperationReceipt receipt, Instant now) {
        Objects.requireNonNull(receipt, "receipt");
        Objects.requireNonNull(now, "now");
        UUID intentId = requestIndex.get(receipt.requestKey());
        if (intentId == null) {
            throw new CustodyConflictException("Custody mutation has no prepared intent");
        }
        CustodyPreparedOperation current = intents.get(intentId);
        CustodyPreparedOperation resolved = current.resolve(receipt, now);
        boolean replayed = current.status() == CustodyPreparedStatus.RESOLVED;
        unresolved.remove(PreparedOrderKey.from(current));
        intents.put(intentId, resolved);
        return new CustodyPreparedResult(resolved, replayed);
    }

    synchronized CustodyPreparedOperation forRequest(String requestKey) {
        UUID intentId = requestIndex.get(CustodyLot.requireRequestKey(requestKey));
        return intentId == null ? null : intents.get(intentId);
    }

    synchronized List<CustodyPreparedOperation> unresolved(int limit) {
        if (limit <= 0 || limit > MAX_QUERY_LIMIT) {
            throw new IllegalArgumentException("Invalid custody intent query limit");
        }
        return unresolved.values().stream()
                .map(intents::get)
                .limit(limit)
                .toList();
    }

    synchronized boolean hasUnresolved() {
        return !unresolved.isEmpty();
    }

    synchronized Map<UUID, CustodyPreparedOperation> snapshot() {
        return Map.copyOf(intents);
    }

    synchronized void restore(Map<UUID, CustodyPreparedOperation> restored) {
        Objects.requireNonNull(restored, "restored");
        if (restored.size() > MAX_INTENTS) {
            throw new CustodyConflictException("Persisted custody intents exceed their limit");
        }
        intents.clear();
        requestIndex.clear();
        unresolved.clear();
        for (Map.Entry<UUID, CustodyPreparedOperation> entry : restored.entrySet()) {
            CustodyPreparedOperation intent = entry.getValue();
            if (!entry.getKey().equals(intent.intentId())
                    || intents.put(intent.intentId(), intent) != null
                    || requestIndex.put(intent.requestKey(), intent.intentId()) != null) {
                intents.clear();
                requestIndex.clear();
                throw new CustodyConflictException("Duplicate persisted custody intent");
            }
            if (intent.status() == CustodyPreparedStatus.PREPARED) {
                unresolved.put(PreparedOrderKey.from(intent), intent.intentId());
            }
        }
    }

    private static boolean samePreparation(CustodyPreparedOperation existing,
                                           CustodyPreparedOperation candidate) {
        if (existing.status() == CustodyPreparedStatus.PREPARED) {
            return existing.equals(candidate);
        }
        return existing.intentId().equals(candidate.intentId())
                && existing.operation() == candidate.operation()
                && existing.requestKey().equals(candidate.requestKey())
                && existing.lotSnapshot().equals(candidate.lotSnapshot())
                && existing.adapterId().equals(candidate.adapterId())
                && existing.adapterCapability() == candidate.adapterCapability()
                && existing.simulationToken().equals(candidate.simulationToken())
                && existing.plannedEvidence().equals(candidate.plannedEvidence())
                && existing.preparedAt().equals(candidate.preparedAt());
    }

    private record PreparedOrderKey(Instant preparedAt, UUID intentId) {
        private static PreparedOrderKey from(CustodyPreparedOperation intent) {
            return new PreparedOrderKey(intent.preparedAt(), intent.intentId());
        }
    }
}

package com.enviouse.futureshopsp.server.economy;

import com.enviouse.futureshopsp.api.economy.RequestId;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Thread safe custody fixture store with explicit conservation transitions. */
public final class InMemoryEconomyCustodyStore implements EconomyCustodyStore {
    private final Map<RequestId, CustodyRecord> records = new LinkedHashMap<>();

    @Override
    public synchronized Optional<CustodyRecord> find(RequestId requestId) {
        return Optional.ofNullable(records.get(Objects.requireNonNull(requestId, "requestId")));
    }

    @Override
    public synchronized CustodyRecord hold(RequestId requestId, UUID owner, String itemKey,
                                            long quantity, String contentHash) {
        if (records.containsKey(requestId)) {
            throw new IllegalStateException("custody already exists");
        }
        CustodyRecord record = new CustodyRecord(requestId, requestId.value().toString(), owner,
                itemKey, quantity, contentHash, CustodyState.HELD);
        records.put(requestId, record);
        return record;
    }

    @Override
    public synchronized CustodyRecord transition(RequestId requestId, CustodyState expected, CustodyState next) {
        CustodyRecord current = records.get(requestId);
        if (current == null || current.state() != expected || !allowed(expected, next)) {
            throw new IllegalStateException("invalid custody transition");
        }
        CustodyRecord updated = new CustodyRecord(current.requestId(), current.custodyId(), current.owner(),
                current.itemKey(), current.quantity(), current.contentHash(), next);
        records.put(requestId, updated);
        return updated;
    }

    @Override
    public synchronized List<CustodyRecord> snapshot() {
        return List.copyOf(new ArrayList<>(records.values()));
    }

    @Override
    public synchronized boolean hasIncompleteRecords() {
        return records.values().stream().anyMatch(record -> record.state() != CustodyState.CLAIMED
                && record.state() != CustodyState.RELEASED);
    }

    private static boolean allowed(CustodyState expected, CustodyState next) {
        return switch (expected) {
            case HELD -> next == CustodyState.DELIVERED || next == CustodyState.RELEASED;
            case DELIVERED -> next == CustodyState.CLAIMED;
            case CLAIMED, RELEASED -> false;
        };
    }
}

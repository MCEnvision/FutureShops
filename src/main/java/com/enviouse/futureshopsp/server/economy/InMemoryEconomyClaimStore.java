package com.enviouse.futureshopsp.server.economy;

import com.enviouse.futureshopsp.api.economy.RequestId;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Thread safe claim fixture store. Claims never expire. */
public final class InMemoryEconomyClaimStore implements EconomyClaimStore {
    private final Map<RequestId, ClaimRecord> records = new LinkedHashMap<>();

    @Override
    public synchronized Optional<ClaimRecord> find(RequestId requestId) {
        return Optional.ofNullable(records.get(Objects.requireNonNull(requestId, "requestId")));
    }

    @Override
    public synchronized ClaimRecord create(RequestId requestId, UUID claimant, long amountMinorUnits,
                                            String description) {
        if (records.containsKey(requestId)) {
            throw new IllegalStateException("claim already exists");
        }
        ClaimRecord record = new ClaimRecord(requestId, claimant, amountMinorUnits, description, ClaimState.PENDING);
        records.put(requestId, record);
        return record;
    }

    @Override
    public synchronized ClaimRecord transition(RequestId requestId, ClaimState expected, ClaimState next) {
        ClaimRecord current = records.get(requestId);
        if (current == null || current.state() != expected || !allowed(expected, next)) {
            throw new IllegalStateException("invalid claim transition");
        }
        ClaimRecord updated = new ClaimRecord(current.requestId(), current.claimant(), current.amountMinorUnits(),
                current.description(), next);
        records.put(requestId, updated);
        return updated;
    }

    @Override
    public synchronized List<ClaimRecord> snapshot() {
        return List.copyOf(new ArrayList<>(records.values()));
    }

    @Override
    public synchronized boolean hasIncompleteRecords() {
        return records.values().stream().anyMatch(record -> record.state() != ClaimState.RESOLVED);
    }

    private static boolean allowed(ClaimState expected, ClaimState next) {
        return switch (expected) {
            case PENDING -> next == ClaimState.DELIVERED || next == ClaimState.RESOLVED;
            case DELIVERED -> next == ClaimState.RESOLVED;
            case RESOLVED -> false;
        };
    }
}

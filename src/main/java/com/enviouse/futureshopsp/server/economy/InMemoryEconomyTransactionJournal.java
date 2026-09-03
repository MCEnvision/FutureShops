package com.enviouse.futureshopsp.server.economy;

import com.enviouse.futureshopsp.api.economy.RequestId;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Thread safe deterministic journal for fixtures and providers without a world. */
public final class InMemoryEconomyTransactionJournal implements EconomyTransactionJournal {
    private final Map<RequestId, EconomyJournalRecord> records = new LinkedHashMap<>();
    private boolean cleanMarkerValid = true;

    @Override
    public synchronized Optional<EconomyJournalRecord> find(RequestId requestId) {
        return Optional.ofNullable(records.get(Objects.requireNonNull(requestId, "requestId")));
    }

    @Override
    public synchronized void append(EconomyJournalRecord record) {
        Objects.requireNonNull(record, "record");
        if (records.containsKey(record.request().requestId())) {
            throw new IllegalStateException("transaction request already exists");
        }
        records.put(record.request().requestId(), record);
    }

    @Override
    public synchronized void replace(EconomyJournalRecord record) {
        Objects.requireNonNull(record, "record");
        if (!records.containsKey(record.request().requestId())) {
            throw new IllegalStateException("transaction request does not exist");
        }
        records.put(record.request().requestId(), record);
    }

    @Override
    public synchronized List<EconomyJournalRecord> snapshot() {
        return List.copyOf(new ArrayList<>(records.values()));
    }

    @Override
    public synchronized boolean cleanMarkerValid() {
        return cleanMarkerValid;
    }

    @Override
    public synchronized void markUnclean() {
        cleanMarkerValid = false;
    }

    @Override
    public synchronized void markCleanMarker() {
        cleanMarkerValid = true;
    }
}

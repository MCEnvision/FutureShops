package com.enviouse.futureshopsp.server.economy;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** In memory receipt audit journal used by fixtures and server free tests. */
public final class InMemoryEconomyReceiptAuditJournal implements EconomyReceiptAuditJournal {
    private final List<EconomyJournalRecord> records = new ArrayList<>();
    private boolean integrityValid = true;
    private boolean cleanMarkerValid = true;

    @Override
    public synchronized void append(EconomyJournalRecord record) {
        Objects.requireNonNull(record, "record");
        if (!ReceiptAuditValidation.valid(record)) {
            integrityValid = false;
            throw new IllegalStateException("receipt audit record is invalid");
        }
        EconomyJournalRecord previous = records.isEmpty() ? null : latest(record.request().requestId()).orElse(null);
        if (previous != null && !ReceiptAuditValidation.allowed(previous, record)) {
            integrityValid = false;
            throw new IllegalStateException("receipt audit transition is contradictory");
        }
        records.add(record);
        cleanMarkerValid = false;
    }

    @Override
    public synchronized List<EconomyJournalRecord> snapshot() {
        return List.copyOf(records);
    }

    @Override
    public synchronized boolean integrityValid() {
        return integrityValid;
    }

    @Override
    public synchronized boolean cleanMarkerValid() {
        return cleanMarkerValid;
    }

    @Override
    public synchronized boolean flush() {
        return integrityValid;
    }

    @Override
    public synchronized void markUnclean() {
        cleanMarkerValid = false;
    }

    @Override
    public synchronized void markCleanMarker() {
        cleanMarkerValid = integrityValid;
    }
}

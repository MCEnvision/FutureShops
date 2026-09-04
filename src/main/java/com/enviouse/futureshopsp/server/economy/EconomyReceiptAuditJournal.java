package com.enviouse.futureshopsp.server.economy;

import com.enviouse.futureshopsp.api.economy.RequestId;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** Durable local audit boundary for FutureShops transaction transitions. */
public interface EconomyReceiptAuditJournal {
    void append(EconomyJournalRecord record);

    List<EconomyJournalRecord> snapshot();

    default Optional<EconomyJournalRecord> latest(RequestId requestId) {
        EconomyJournalRecord latest = null;
        for (EconomyJournalRecord record : snapshot()) {
            if (record.request().requestId().equals(requestId)) {
                latest = record;
            }
        }
        return Optional.ofNullable(latest);
    }

    default boolean hasIncompleteRecords() {
        return snapshot().stream().collect(Collectors.toMap(
                record -> record.request().requestId(), record -> record,
                (first, second) -> second, java.util.LinkedHashMap::new)).values().stream()
                .anyMatch(EconomyJournalRecord::incomplete);
    }

    /** Returns false when a local audit record is missing, extra, or ahead of the journal. */
    default boolean matches(EconomyTransactionJournal journal) {
        Map<RequestId, EconomyJournalRecord> current = journal.snapshot().stream().collect(Collectors.toMap(
                record -> record.request().requestId(), record -> record,
                (first, second) -> second));
        for (EconomyJournalRecord record : snapshot()) {
            EconomyJournalRecord currentRecord = current.get(record.request().requestId());
            if (currentRecord == null) {
                return false;
            }
        }
        for (EconomyJournalRecord record : current.values()) {
            if (latest(record.request().requestId()).filter(record::equals).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    boolean integrityValid();

    boolean cleanMarkerValid();

    boolean flush();

    void markUnclean();

    void markCleanMarker();
}

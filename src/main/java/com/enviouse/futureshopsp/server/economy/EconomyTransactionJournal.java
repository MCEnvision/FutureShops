package com.enviouse.futureshopsp.server.economy;

import com.enviouse.futureshopsp.api.economy.RequestId;

import java.util.List;
import java.util.Optional;

/** Durable transaction journal boundary used by the coordinator. */
public interface EconomyTransactionJournal {
    Optional<EconomyJournalRecord> find(RequestId requestId);

    void append(EconomyJournalRecord record);

    void replace(EconomyJournalRecord record);

    List<EconomyJournalRecord> snapshot();

    default boolean hasIncompleteRecords() {
        return snapshot().stream().anyMatch(EconomyJournalRecord::incomplete);
    }

    default boolean integrityValid() {
        return true;
    }

    default boolean flush() {
        return true;
    }

    default boolean cleanMarkerValid() {
        return true;
    }

    default void markUnclean() {
    }

    default void markCleanMarker() {
    }
}

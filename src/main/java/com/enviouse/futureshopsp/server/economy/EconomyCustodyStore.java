package com.enviouse.futureshopsp.server.economy;

import com.enviouse.futureshopsp.api.economy.RequestId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Durable item custody boundary independent from provider balances. */
public interface EconomyCustodyStore {
    Optional<CustodyRecord> find(RequestId requestId);

    CustodyRecord hold(RequestId requestId, UUID owner, String itemKey, long quantity, String contentHash);

    CustodyRecord transition(RequestId requestId, CustodyState expected, CustodyState next);

    List<CustodyRecord> snapshot();

    default boolean integrityValid() {
        return true;
    }

    default boolean cleanMarkerValid() {
        return true;
    }

    default boolean hasIncompleteRecords() {
        return snapshot().stream().anyMatch(record -> record.state() != CustodyState.CLAIMED
                && record.state() != CustodyState.RELEASED);
    }

    default boolean flush() {
        return true;
    }

    default void markUnclean() {
    }

    default void markCleanMarker() {
    }
}

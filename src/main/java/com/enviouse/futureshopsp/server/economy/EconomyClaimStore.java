package com.enviouse.futureshopsp.server.economy;

import com.enviouse.futureshopsp.api.economy.RequestId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Durable claim boundary for offline delivery and proceeds. */
public interface EconomyClaimStore {
    Optional<ClaimRecord> find(RequestId requestId);

    ClaimRecord create(RequestId requestId, UUID claimant, long amountMinorUnits, String description);

    ClaimRecord transition(RequestId requestId, ClaimState expected, ClaimState next);

    List<ClaimRecord> snapshot();

    default boolean integrityValid() {
        return true;
    }

    default boolean cleanMarkerValid() {
        return true;
    }

    default boolean hasIncompleteRecords() {
        return snapshot().stream().anyMatch(record -> record.state() != ClaimState.RESOLVED);
    }

    default boolean flush() {
        return true;
    }

    default void markUnclean() {
    }

    default void markCleanMarker() {
    }
}

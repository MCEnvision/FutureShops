package com.enviouse.futureshopsp.server.economy;

import com.enviouse.futureshopsp.api.economy.MutationReceipt;
import com.enviouse.futureshopsp.api.economy.RequestId;

import java.util.Optional;

/** Durable receipt boundary for the internal provider. */
public interface InternalEconomyReceiptStore {
    Optional<MutationReceipt> find(RequestId requestId);

    void put(MutationReceipt receipt);

    default boolean integrityValid() {
        return true;
    }

    default boolean cleanMarkerValid() {
        return true;
    }

    default boolean flush() {
        return true;
    }

    default void markUnclean() {
    }

    default void markCleanMarker() {
    }
}

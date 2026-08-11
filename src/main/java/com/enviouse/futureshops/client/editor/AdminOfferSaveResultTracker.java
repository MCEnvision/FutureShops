package com.enviouse.futureshops.client.editor;

import com.enviouse.futureshops.network.packets
        .S2CAdminOfferSaveResultPacket;

import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.UUID;

public final class AdminOfferSaveResultTracker {
    private static final int MAXIMUM_RESULTS = 16;
    private final LinkedHashMap<UUID, S2CAdminOfferSaveResultPacket>
            results = new LinkedHashMap<>();

    public synchronized void record(
            S2CAdminOfferSaveResultPacket packet
    ) {
        results.putIfAbsent(packet.requestId(), packet);
        while (results.size() > MAXIMUM_RESULTS) {
            results.remove(results.keySet().iterator().next());
        }
    }

    public synchronized Optional<S2CAdminOfferSaveResultPacket> take(
            UUID requestId
    ) {
        if (requestId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(results.remove(requestId));
    }

    synchronized int size() {
        return results.size();
    }
}

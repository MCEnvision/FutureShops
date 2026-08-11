package com.enviouse.futureshops.server.market.bazaar.escrow;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record BazaarEscrowLifecycleState(
        Map<UUID, BazaarCreateEscrowIntent> createIntents,
        Map<UUID, String> commitFingerprints,
        Map<UUID, BazaarEscrowOrderBacking> activeBackings
) {
    public static final int MAX_CREATE_INTENTS = 200_000;
    public static final int MAX_COMMITS = 200_000;
    public static final int MAX_ACTIVE_BACKINGS = 200_000;

    public BazaarEscrowLifecycleState {
        createIntents = Map.copyOf(new HashMap<>(Objects.requireNonNull(
                createIntents, "createIntents")));
        commitFingerprints = Map.copyOf(new HashMap<>(
                Objects.requireNonNull(commitFingerprints,
                        "commitFingerprints")));
        activeBackings = Map.copyOf(new HashMap<>(Objects.requireNonNull(
                activeBackings, "activeBackings")));
        if (createIntents.size() > MAX_CREATE_INTENTS
                || commitFingerprints.size() > MAX_COMMITS
                || activeBackings.size() > MAX_ACTIVE_BACKINGS) {
            throw new IllegalArgumentException(
                    "Bazaar escrow lifecycle state exceeds its limit");
        }
        for (Map.Entry<UUID, BazaarCreateEscrowIntent> entry
                : createIntents.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().requestId())) {
                throw new IllegalArgumentException(
                        "Bazaar creation intent index is invalid");
            }
        }
        for (Map.Entry<UUID, String> entry
                : commitFingerprints.entrySet()) {
            if (!entry.getValue().matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException(
                        "Bazaar commit fingerprint index is invalid");
            }
        }
        for (Map.Entry<UUID, BazaarEscrowOrderBacking> entry
                : activeBackings.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().orderId())) {
                throw new IllegalArgumentException(
                        "Bazaar order backing index is invalid");
            }
        }
    }

    public static BazaarEscrowLifecycleState empty() {
        return new BazaarEscrowLifecycleState(Map.of(), Map.of(), Map.of());
    }

    public boolean hasMaterializedState() {
        return !createIntents.isEmpty() || !commitFingerprints.isEmpty()
                || !activeBackings.isEmpty();
    }
}

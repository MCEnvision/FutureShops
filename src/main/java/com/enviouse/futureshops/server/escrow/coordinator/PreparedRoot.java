package com.enviouse.futureshops.server.escrow.coordinator;

import com.enviouse.futureshops.api.economy.RootId;

import java.util.List;
import java.util.Objects;

/** Immutable admission result. It is safe to retain and pass to execute once. */
public record PreparedRoot(
        RootId rootId,
        RouteContext route,
        List<BoundLeg> legs,
        CustodyPlan custody,
        boolean replayed) {
    public PreparedRoot {
        rootId = Objects.requireNonNull(rootId, "rootId");
        route = Objects.requireNonNull(route, "route");
        legs = List.copyOf(Objects.requireNonNull(legs, "legs"));
        custody = Objects.requireNonNull(custody, "custody");
        if (legs.isEmpty()) {
            throw new IllegalArgumentException("at least one leg is required");
        }
    }
}

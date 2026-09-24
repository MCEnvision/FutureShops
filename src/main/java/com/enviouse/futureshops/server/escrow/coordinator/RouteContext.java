package com.enviouse.futureshops.server.escrow.coordinator;

import com.enviouse.futureshops.api.economy.BindingV1;

import java.util.Objects;
import java.util.UUID;

/** Admission facts supplied by the owning route. */
public record RouteContext(
        String routeId,
        UUID actor,
        BindingV1 binding,
        boolean authorized,
        boolean capabilityAvailable) {
    public RouteContext {
        if (routeId == null || routeId.isBlank() || routeId.length() > 128
                || routeId.indexOf('\n') >= 0 || routeId.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("routeId must be a bounded single line");
        }
        actor = Objects.requireNonNull(actor, "actor");
        binding = Objects.requireNonNull(binding, "binding");
        if (actor.equals(new UUID(0L, 0L))) {
            throw new IllegalArgumentException("actor must not be zero");
        }
    }
}

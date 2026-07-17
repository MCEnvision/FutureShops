package com.enviouse.futureshops.server.escrow.model;

import java.util.Objects;
import java.util.Set;

public record EscrowParticipant(EscrowParty party, Set<EscrowParticipantRole> roles) {
    public EscrowParticipant {
        Objects.requireNonNull(party, "party");
        Objects.requireNonNull(roles, "roles");
        roles = Set.copyOf(roles);
        if (roles.isEmpty()) {
            throw new IllegalArgumentException("Escrow participant roles cannot be empty");
        }
    }

    public boolean hasRole(EscrowParticipantRole role) {
        return roles.contains(Objects.requireNonNull(role, "role"));
    }
}

package com.enviouse.futureshops.server.escrow.custody;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record CustodyConservationReport(
        Map<CustodyLiabilityKey, Long> reserved,
        Map<CustodyLiabilityKey, Long> held,
        Map<CustodyLiabilityKey, Long> released,
        Map<CustodyLiabilityKey, Long> consumed,
        Map<CustodyLiabilityKey, Long> quarantined,
        boolean conserved,
        List<String> violations
) {
    public CustodyConservationReport {
        Objects.requireNonNull(reserved, "reserved");
        Objects.requireNonNull(held, "held");
        Objects.requireNonNull(released, "released");
        Objects.requireNonNull(consumed, "consumed");
        Objects.requireNonNull(quarantined, "quarantined");
        Objects.requireNonNull(violations, "violations");
        reserved = Map.copyOf(reserved);
        held = Map.copyOf(held);
        released = Map.copyOf(released);
        consumed = Map.copyOf(consumed);
        quarantined = Map.copyOf(quarantined);
        violations = List.copyOf(violations);
        if (conserved != violations.isEmpty()) {
            throw new IllegalArgumentException("Custody conservation status does not match violations");
        }
    }
}

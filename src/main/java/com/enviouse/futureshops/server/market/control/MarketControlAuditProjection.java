package com.enviouse.futureshops.server.market.control;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record MarketControlAuditProjection(
        long revision,
        Map<MarketControlModule, MarketModuleControl> modules,
        List<MarketControlAuditEntry> entries
) {
    public MarketControlAuditProjection {
        if (revision < 0L) {
            throw new IllegalArgumentException(
                    "Market audit projection revision is invalid");
        }
        EnumMap<MarketControlModule, MarketModuleControl> moduleCopy =
                new EnumMap<>(MarketControlModule.class);
        moduleCopy.putAll(Objects.requireNonNull(modules, "modules"));
        modules = Map.copyOf(moduleCopy);
        entries = List.copyOf(new ArrayList<>(
                Objects.requireNonNull(entries, "entries")));
        if (revision != entries.size()
                || modules.size()
                != MarketControlModule.values().length) {
            throw new IllegalArgumentException(
                    "Market audit projection is inconsistent");
        }
    }

    public static MarketControlAuditProjection from(
            MarketControlState state
    ) {
        Objects.requireNonNull(state, "state");
        return new MarketControlAuditProjection(state.globalRevision(),
                state.modules(), state.auditEntries());
    }

    public List<MarketControlAuditEntry> entriesFor(
            MarketControlModule module
    ) {
        Objects.requireNonNull(module, "module");
        return entries.stream().filter(entry -> entry.module() == module)
                .toList();
    }

    public Optional<MarketControlAuditEntry> entryFor(UUID requestId) {
        Objects.requireNonNull(requestId, "requestId");
        return entries.stream().filter(entry ->
                entry.requestId().equals(requestId)).findFirst();
    }
}

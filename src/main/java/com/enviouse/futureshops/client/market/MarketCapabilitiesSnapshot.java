package com.enviouse.futureshops.client.market;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record MarketCapabilitiesSnapshot(
    UUID requestId,
    long revision,
    boolean showNavigation,
    MarketModule defaultModule,
    List<MarketModuleCapability> modules
) {
    public MarketCapabilitiesSnapshot {
        if (requestId == null || requestId.equals(new UUID(0L, 0L))) {
            throw new IllegalArgumentException("Market capability request identifier is required.");
        }
        if (revision < 0L) {
            throw new IllegalArgumentException("Market capability revision must not be negative.");
        }
        Objects.requireNonNull(defaultModule, "defaultModule");
        modules = List.copyOf(Objects.requireNonNull(modules, "modules"));
        if (modules.isEmpty() || modules.size() > MarketModule.values().length) {
            throw new IllegalArgumentException("Market capabilities must contain a bounded module set.");
        }
        EnumMap<MarketModule, MarketModuleCapability> unique = new EnumMap<>(MarketModule.class);
        for (MarketModuleCapability capability : modules) {
            if (unique.put(capability.module(), capability) != null) {
                throw new IllegalArgumentException("Market capabilities contain a duplicate module.");
            }
        }
        MarketModuleCapability defaultCapability = unique.get(defaultModule);
        if (defaultCapability == null || !defaultCapability.availability().allowsBrowse()) {
            defaultModule = unique.values().stream()
                .filter(capability -> capability.availability().allowsBrowse())
                .map(MarketModuleCapability::module)
                .findFirst()
                .orElseGet(() -> unique.values().stream()
                    .filter(capability -> capability.availability().allowsClaims())
                    .map(MarketModuleCapability::module)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("At least one market module must be openable.")));
        }
    }

    public Map<MarketModule, MarketModuleCapability> byModule() {
        EnumMap<MarketModule, MarketModuleCapability> result = new EnumMap<>(MarketModule.class);
        for (MarketModuleCapability capability : modules) {
            result.put(capability.module(), capability);
        }
        return Map.copyOf(result);
    }
}

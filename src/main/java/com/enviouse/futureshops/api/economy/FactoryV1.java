package com.enviouse.futureshops.api.economy;

/** Public API v1 factory alias for independently compiled server addons. */
@FunctionalInterface
public interface FactoryV1 {
    EconomyProvider create(EconomyProviderContext context);
}

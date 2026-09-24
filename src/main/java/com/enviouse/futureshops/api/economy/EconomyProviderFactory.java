package com.enviouse.futureshops.api.economy;

/** Creates one provider for one server lifecycle. */
@FunctionalInterface
public interface EconomyProviderFactory {
    EconomyProvider create(EconomyProviderContext context);
}

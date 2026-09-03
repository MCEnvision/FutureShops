package com.enviouse.futureshopsp.api.economy;

/** Creates one provider for one server lifecycle. */
@FunctionalInterface
public interface EconomyProviderFactory {
    EconomyProvider create(EconomyProviderContext context);
}

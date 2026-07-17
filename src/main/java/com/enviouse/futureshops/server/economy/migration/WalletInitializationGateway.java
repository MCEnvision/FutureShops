package com.enviouse.futureshops.server.economy.migration;

@FunctionalInterface
public interface WalletInitializationGateway {
    WalletInitializationResult initialize(WalletInitializationRequest request);

    default boolean supportsNegativeLegacyBalances() {
        return false;
    }
}

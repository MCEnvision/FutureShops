package com.enviouse.futureshops.server.escrow.runtime;

interface AtmCurrencyConfigurationLease extends AutoCloseable {
    long generation();

    String currencySignature();

    @Override
    void close();
}

@FunctionalInterface
interface AtmCurrencyConfigurationLeaseProvider {
    AtmCurrencyConfigurationLease acquire();
}

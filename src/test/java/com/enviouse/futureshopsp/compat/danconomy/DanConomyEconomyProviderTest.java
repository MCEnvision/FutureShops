package com.enviouse.futureshopsp.compat.danconomy;

import com.enviouse.futureshopsp.api.economy.EconomyApi;
import com.enviouse.futureshopsp.api.economy.EconomyCapability;
import com.enviouse.futureshopsp.api.economy.ProviderLifecycle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DanConomyEconomyProviderTest {
    @Test
    void requiresTheExactSupportedVersion() {
        assertTrue(DanConomyEconomyProviderRegistration.isSupportedVersion("1.2.1"));
        assertFalse(DanConomyEconomyProviderRegistration.isSupportedVersion("1.2.1+build"));
        assertFalse(DanConomyEconomyProviderRegistration.isSupportedVersion("1.2.0"));
        assertFalse(DanConomyEconomyProviderRegistration.isSupportedVersion(null));
    }

    @Test
    void remainsSafelyUnavailableWithoutDanconomyClasses() {
        DanConomyEconomyProvider provider = new DanConomyEconomyProvider(null);

        assertEquals(EconomyApi.DANCONOMY_PROVIDER_ID, provider.providerId());
        assertEquals(EconomyApi.COMPATIBILITY_VERSION, provider.compatibilityVersion());
        assertEquals(ProviderLifecycle.INCOMPATIBLE, provider.readiness().lifecycle());
        assertTrue(provider.capabilities().supports(EconomyCapability.BALANCE_QUERY));
        assertTrue(provider.capabilities().supports(EconomyCapability.PRECHECK));
        assertFalse(provider.capabilities().supports(EconomyCapability.WITHDRAW));
        assertFalse(provider.capabilities().supports(EconomyCapability.DEPOSIT));
        assertFalse(provider.capabilities().supports(EconomyCapability.RECEIPT_LOOKUP));
        assertFalse(provider.capabilities().supports(EconomyCapability.IDEMPOTENT_RETRY));
    }
}

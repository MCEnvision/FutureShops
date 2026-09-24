package com.enviouse.futureshops.api.economy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EconomyProviderRegistryTest {
    @BeforeEach
    void reset() {
        EconomyProviderRegistry.resetForTests();
    }

    @AfterEach
    void cleanup() {
        EconomyProviderRegistry.resetForTests();
    }

    @Test
    void rejectsInvalidDuplicateReservedAndLateRegistrations() {
        assertEquals(RegistrationStatus.INVALID_IDENTIFIER,
                EconomyProviderRegistry.register("bad-id", 1, context -> null).status());
        assertEquals(RegistrationStatus.ACCEPTED,
                EconomyProviderRegistry.register("zeta", 1, context -> null).status());
        assertEquals(RegistrationStatus.DUPLICATE,
                EconomyProviderRegistry.register("zeta", 1, context -> null).status());
        assertEquals(RegistrationStatus.RESERVED,
                EconomyProviderRegistry.register(EconomyApi.INTERNAL_PROVIDER_ID, 1, context -> null).status());
        assertEquals(RegistrationStatus.ACCEPTED,
                EconomyProviderRegistry.register("alpha", 1, context -> null).status());
        assertEquals(java.util.List.of("alpha", "zeta"),
                java.util.List.copyOf(EconomyProviderRegistry.snapshot().keySet()));

        EconomyProviderRegistry.freeze();
        assertTrue(EconomyProviderRegistry.isFrozen());
        assertEquals(RegistrationStatus.LATE,
                EconomyProviderRegistry.register("late", 1, context -> null).status());
    }

    @Test
    void rejectsInvalidArgumentsBeforeChangingRegistry() {
        assertEquals(RegistrationStatus.INVALID_ARGUMENT,
                EconomyProviderRegistry.register("valid", 0, context -> null).status());
        assertEquals(RegistrationStatus.INVALID_ARGUMENT,
                EconomyProviderRegistry.register("valid", 1, null).status());
        assertTrue(EconomyProviderRegistry.snapshot().isEmpty());
    }

    @Test
    void acceptsTheVersionedProviderAndFactoryAliases() {
        ProviderId providerId = new ProviderId("fixture");
        FactoryV1 factory = context -> null;

        RegistrationResult result = EconomyProviderRegistry.register(providerId, factory);

        assertEquals(RegistrationStatus.ACCEPTED, result.status());
        assertEquals("fixture", result.providerId());
        assertTrue(EconomyProviderRegistry.snapshot().containsKey("fixture"));
    }
}

package com.enviouse.futureshopsp.api.economy;

import net.minecraft.server.MinecraftServer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(EphemeralTestServerProvider.class)
class EconomyProviderRegistryTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000002");

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
                EconomyProviderRegistry.register("bad-id", 1, context -> provider("bad-id")).status());
        assertEquals(RegistrationStatus.ACCEPTED,
                EconomyProviderRegistry.register("zeta", 1, context -> provider("zeta")).status());
        assertEquals(RegistrationStatus.DUPLICATE,
                EconomyProviderRegistry.register("zeta", 1, context -> provider("zeta")).status());
        assertEquals(RegistrationStatus.RESERVED,
                EconomyProviderRegistry.register(EconomyApi.INTERNAL_PROVIDER_ID, 1, context -> provider("internal")).status());
        assertEquals(RegistrationStatus.ACCEPTED,
                EconomyProviderRegistry.registerVault(1, context -> provider(EconomyApi.VAULT_PROVIDER_ID)).status());

        EconomyProviderRegistry.freeze();
        assertTrue(EconomyProviderRegistry.isFrozen());
        assertEquals(RegistrationStatus.LATE,
                EconomyProviderRegistry.register("late", 1, context -> provider("late")).status());
    }

    @Test
    void snapshotsAreSortedAndResolutionIsFrozen(MinecraftServer server) {
        EconomyProviderRegistry.register("zeta", 1, context -> provider("zeta"));
        EconomyProviderRegistry.register("alpha", 1, context -> provider("alpha"));
        assertEquals(List.of("alpha", "zeta"), List.copyOf(EconomyProviderRegistry.snapshot().keySet()));

        EconomyProviderRegistry.freeze();
        ProviderResolution resolution = EconomyProviderRegistry.resolve(
                "alpha", new EconomyProviderContext(server));
        assertEquals(ProviderLifecycle.READY, resolution.lifecycle());
        assertTrue(resolution.ready());
        assertEquals("alpha", resolution.provider().orElseThrow().providerId());
        assertTrue(resolution.capabilities().orElseThrow().supports(EconomyCapability.IDEMPOTENT_RETRY));
    }

    @Test
    void rejectsFactoryFailuresAndMismatchedProviderMetadata(MinecraftServer server) {
        EconomyProviderRegistry.register("throws", 1, context -> {
            throw new IllegalStateException("fixture");
        });
        EconomyProviderRegistry.register("wrong", 1, context -> provider("other"));
        EconomyProviderRegistry.register("old", 2, context -> provider("old"));
        EconomyProviderRegistry.freeze();

        assertEquals(ProviderLifecycle.FAILED,
                EconomyProviderRegistry.resolve("throws", new EconomyProviderContext(server)).lifecycle());
        assertEquals(ProviderLifecycle.INCOMPATIBLE,
                EconomyProviderRegistry.resolve("wrong", new EconomyProviderContext(server)).lifecycle());
        assertEquals(ProviderLifecycle.INCOMPATIBLE,
                EconomyProviderRegistry.resolve("old", new EconomyProviderContext(server)).lifecycle());
        assertEquals(ProviderLifecycle.MISSING,
                EconomyProviderRegistry.resolve("missing", new EconomyProviderContext(server)).lifecycle());
        assertEquals(ProviderLifecycle.UNRESOLVED,
                unresolvedResolution(server).lifecycle());
    }

    private static ProviderResolution unresolvedResolution(MinecraftServer server) {
        EconomyProviderRegistry.resetForTests();
        return EconomyProviderRegistry.resolve("alpha", new EconomyProviderContext(server));
    }

    private static EconomyProvider provider(String id) {
        return new EconomyProvider() {
            @Override
            public String providerId() {
                return id;
            }

            @Override
            public int compatibilityVersion() {
                return EconomyApi.COMPATIBILITY_VERSION;
            }

            @Override
            public CurrencyMetadata currency() {
                return new CurrencyMetadata("Coin", "Coins", 2);
            }

            @Override
            public ProviderCapabilities capabilities() {
                return ProviderCapabilities.all();
            }

            @Override
            public ProviderReadiness readiness() {
                return new ProviderReadiness(ProviderLifecycle.READY, "");
            }

            @Override
            public ProviderResult<BalanceSnapshot> balance(UUID playerId) {
                return ProviderResult.confirmed(new BalanceSnapshot(playerId, 1L));
            }

            @Override
            public ProviderResult<BalanceSnapshot> precheck(MutationRequest request) {
                return ProviderResult.confirmed(new BalanceSnapshot(request.actor(), 1L));
            }

            @Override
            public ProviderResult<MutationReceipt> withdraw(MutationRequest request) {
                return ProviderResult.recoveryRequired("fixture");
            }

            @Override
            public ProviderResult<MutationReceipt> deposit(MutationRequest request) {
                return ProviderResult.recoveryRequired("fixture");
            }

            @Override
            public ProviderResult<MutationReceipt> lookup(RequestId requestId) {
                return ProviderResult.rejected(ProviderError.RECEIPT_NOT_FOUND, "fixture");
            }

            @Override
            public ProviderResult<MutationReceipt> retry(MutationRequest request) {
                return ProviderResult.recoveryRequired("fixture");
            }
        };
    }
}

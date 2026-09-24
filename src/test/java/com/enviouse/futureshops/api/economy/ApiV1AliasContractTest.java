package com.enviouse.futureshops.api.economy;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiV1AliasContractTest {
    @Test
    void validatesBindingAndBoundRequestIdentity() {
        BindingV1 binding = new BindingV1(
                "fixture", 1, "fixture_adapter", "v1",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789",
                "fixture lineage", UUID.randomUUID(), "coins", 2, "fixture manager", 1L, 1);
        BoundRequestV1 request = new BoundRequestV1(
                binding, RequestId.random(), RequestId.random(), "withdraw", 100L,
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        assertEquals(100L, request.minorUnits());
        assertThrows(IllegalArgumentException.class, () -> new BindingV1(
                "fixture", 1, "fixture_adapter", "v1", "not-a-fingerprint",
                "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789",
                "fixture lineage", UUID.randomUUID(), "coins", 2, "fixture manager", 1L, 1));
    }

    @Test
    void typedQueryAndOutcomeNeverUseImplicitZero() {
        QueryResult<BalanceSnapshot> unavailable = QueryResult.unavailable(
                ProviderError.NOT_READY, "provider is not ready");
        assertTrue(unavailable.value().isEmpty());
        assertEquals(ProviderError.NOT_READY, unavailable.error());
        MutationOutcome refused = MutationOutcome.refused(
                ProviderError.NOT_READY, "coordinator is not ready");
        assertTrue(refused.receipt().isEmpty());
        assertEquals(ProviderResultStatus.REJECTED, refused.status());
        assertEquals(Optional.empty(), refused.receipt());
    }

    @Test
    void frozenProviderMethodProjectionsRemainTypedAndFailClosed() {
        EconomyProvider provider = new ProjectionProvider();
        BindingV1 binding = new BindingV1(
                "fixture", 1, "fixture_adapter", "v1",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789",
                "fixture lineage", UUID.randomUUID(), "coins", 2,
                "fixture manager", 1L, 1);
        assertEquals(100L, provider.query(binding).value().orElseThrow().balanceMinorUnits());
        assertTrue(provider.precheck(new BoundRequestV1(
                binding, RootId.random(), LegId.random(), "withdraw", 10L,
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"))
                .ready());
        MutationOutcome outcome = provider.mutate(new BoundRequestV1(
                binding, RootId.random(), LegId.random(), "withdraw", 10L,
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"));
        assertEquals(ProviderError.NOT_READY, outcome.error());
        assertTrue(provider.lookup(binding, RootId.random(), LegId.random())
                .receipt().isEmpty());
    }

    private static final class ProjectionProvider implements EconomyProvider {
        @Override
        public String providerId() {
            return "fixture";
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
            return ProviderResult.confirmed(new BalanceSnapshot(playerId, 100L));
        }

        @Override
        public ProviderResult<BalanceSnapshot> precheck(MutationRequest request) {
            return ProviderResult.confirmed(new BalanceSnapshot(request.actor(), 100L));
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
    }
}

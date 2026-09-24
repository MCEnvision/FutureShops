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
}

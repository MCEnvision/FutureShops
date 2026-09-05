package com.enviouse.futureshopsp.api.economy;

import java.util.UUID;

/**
 * Public provider contract for server authoritative economy integrations.
 *
 * <p>Implementations run on the logical server thread and must not block that thread on remote
 * or unbounded work. Every mutation request identity must be durable in the provider or in an exact
 * adapter that can look up the outcome and safely retry the same logical operation. A local request
 * UUID alone does not make a boolean external call idempotent.
 */
public interface EconomyProvider {
    /** Stable lowercase provider identifier. */
    String providerId();

    /** Compatibility version implemented by this provider. */
    int compatibilityVersion();

    /** Immutable currency metadata for this provider. */
    CurrencyMetadata currency();

    /** Immutable capabilities proven for this provider. */
    ProviderCapabilities capabilities();

    /** Current server lifecycle and readiness state. */
    ProviderReadiness readiness();

    /** Returns the authoritative balance, or an explicit non-confirmed result. */
    ProviderResult<BalanceSnapshot> balance(UUID playerId);

    /** Performs a non-mutating funds and capability precheck for one request. */
    ProviderResult<BalanceSnapshot> precheck(MutationRequest request);

    /** Withdraws one request amount after the caller has persisted intent and custody. */
    ProviderResult<MutationReceipt> withdraw(MutationRequest request);

    /** Deposits one request amount after the caller has persisted intent and custody. */
    ProviderResult<MutationReceipt> deposit(MutationRequest request);

    /** Looks up a durable outcome by the original request identity. */
    ProviderResult<MutationReceipt> lookup(RequestId requestId);

    /** Looks up a durable outcome with the persisted account binding when the provider requires it. */
    default ProviderResult<MutationReceipt> lookup(MutationRequest request) {
        if (request == null) {
            return ProviderResult.rejected(ProviderError.INVALID_REQUEST, "mutation request is required");
        }
        return lookup(request.requestId());
    }

    /** Retries the same request identity only when the provider proves idempotent retry. */
    ProviderResult<MutationReceipt> retry(MutationRequest request);
}

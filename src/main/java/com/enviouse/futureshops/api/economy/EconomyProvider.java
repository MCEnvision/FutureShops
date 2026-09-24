package com.enviouse.futureshops.api.economy;

import java.util.UUID;
import java.util.Locale;
import java.util.List;

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

    /** Creates an immutable binding when this provider has verified its backend identity. */
    default BindingV1 bind(AccountRef account, CurrencyV1 currency) {
        throw new UnsupportedOperationException(
                "provider has not supplied a verified account binding");
    }

    /** Queries an account through a previously verified binding. */
    default QueryResult<BalanceSnapshot> query(BindingV1 binding) {
        if (!validBinding(binding)) {
            return QueryResult.unavailable(ProviderError.BINDING_CHANGED,
                    "account binding does not match provider");
        }
        ProviderResult<BalanceSnapshot> result = balance(binding.accountUuid());
        return result.confirmed()
                ? QueryResult.confirmed(result.value().orElseThrow())
                : QueryResult.unavailable(result.error(), result.diagnostic());
    }

    /** Returns a provider leaderboard or an explicit unsupported result. */
    default QueryResult<List<BalanceSnapshot>> leaderboard(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            return QueryResult.unavailable(ProviderError.INVALID_REQUEST,
                    "leaderboard page bounds are invalid");
        }
        return QueryResult.unavailable(ProviderError.CAPABILITY_MISSING,
                "leaderboard is unavailable for this provider");
    }

    /** Performs readiness and capability admission without dispatching a mutation. */
    default Readiness precheck(BoundRequestV1 request) {
        if (request == null || !validBinding(request.binding())) {
            return new Readiness(ProviderLifecycle.FAILED,
                    "bound request does not match provider");
        }
        EconomyCapability required = requiredCapability(request.operation());
        if (required == null || !capabilities().precheck()
                || !capabilities().supports(required)) {
            return new Readiness(ProviderLifecycle.FROZEN,
                    "provider capability is unavailable for this operation");
        }
        ProviderReadiness readiness = readiness();
        return new Readiness(readiness.lifecycle(), readiness.diagnostic());
    }

    /** Mutation projection remains closed until the durable coordinator is active. */
    default MutationOutcome mutate(BoundRequestV1 request) {
        if (request == null || !validBinding(request.binding())) {
            return MutationOutcome.refused(ProviderError.BINDING_CHANGED,
                    "bound request does not match provider");
        }
        EconomyCapability required = requiredCapability(request.operation());
        if (required == null || !capabilities().supports(required)) {
            return MutationOutcome.refused(ProviderError.CAPABILITY_MISSING,
                    "provider capability is unavailable for this operation");
        }
        return MutationOutcome.refused(ProviderError.NOT_READY,
                "durable economy coordinator is not ready");
    }

    /** Looks up a receipt by the bound root and leg identities. */
    default ReceiptOutcome lookup(BindingV1 binding, RootId rootId, LegId legId) {
        if (!validBinding(binding) || rootId == null || legId == null) {
            return ReceiptOutcome.notFound("receipt binding is invalid");
        }
        ProviderResult<MutationReceipt> result = lookup(legId.requestId());
        if (result.confirmed()) {
            return new ReceiptOutcome(ProviderResultStatus.CONFIRMED,
                    ProviderError.NONE, result.value(), result.diagnostic());
        }
        return new ReceiptOutcome(result.status(), result.error(),
                java.util.Optional.empty(), result.diagnostic());
    }

    private boolean validBinding(BindingV1 binding) {
        return binding != null
                && providerId().equals(binding.providerId())
                && compatibilityVersion() == binding.apiVersion();
    }

    private static EconomyCapability requiredCapability(String operation) {
        if (operation == null) {
            return null;
        }
        return switch (operation.toLowerCase(Locale.ROOT)) {
            case "withdraw", "transfer_debit", "fee" -> EconomyCapability.WITHDRAW;
            case "deposit", "transfer_credit", "refund", "compensation" -> EconomyCapability.DEPOSIT;
            default -> null;
        };
    }
}

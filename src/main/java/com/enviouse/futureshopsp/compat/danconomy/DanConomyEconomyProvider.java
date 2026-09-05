package com.enviouse.futureshopsp.compat.danconomy;

import com.enviouse.futureshopsp.api.economy.BalanceSnapshot;
import com.enviouse.futureshopsp.api.economy.CurrencyMetadata;
import com.enviouse.futureshopsp.api.economy.EconomyApi;
import com.enviouse.futureshopsp.api.economy.MutationKind;
import com.enviouse.futureshopsp.api.economy.MutationReceipt;
import com.enviouse.futureshopsp.api.economy.MutationRequest;
import com.enviouse.futureshopsp.api.economy.ProviderCapabilities;
import com.enviouse.futureshopsp.api.economy.ProviderError;
import com.enviouse.futureshopsp.api.economy.ProviderLifecycle;
import com.enviouse.futureshopsp.api.economy.ProviderReadiness;
import com.enviouse.futureshopsp.api.economy.ProviderResult;
import com.enviouse.futureshopsp.api.economy.RequestId;
import com.enviouse.futureshopsp.server.debug.DebugDiagnostics;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;

public final class DanConomyEconomyProvider implements com.enviouse.futureshopsp.api.economy.EconomyProvider {
    public static final String PROVIDER_ID = EconomyApi.DANCONOMY_PROVIDER_ID;
    public static final String SUPPORTED_VERSION = "1.2.1";
    private static final CurrencyMetadata UNAVAILABLE_CURRENCY =
            new CurrencyMetadata("DanConomy unit", "DanConomy units", 0);
    private static final ProviderCapabilities QUERY_CAPABILITIES =
            new ProviderCapabilities(true, true, false, false, false, false);

    private final MinecraftServer server;
    private final RuntimeAccess runtime;
    private final CurrencyResolution currency;

    public DanConomyEconomyProvider(MinecraftServer server) {
        this.server = server;
        this.runtime = RuntimeAccess.load();
        this.currency = runtime.resolveCurrency();
    }

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public int compatibilityVersion() {
        return EconomyApi.COMPATIBILITY_VERSION;
    }

    @Override
    public CurrencyMetadata currency() {
        return currency.metadata();
    }

    @Override
    public ProviderCapabilities capabilities() {
        return currency.valid() && runtime.mixinTargetAvailable()
                ? ProviderCapabilities.all() : QUERY_CAPABILITIES;
    }

    @Override
    public ProviderReadiness readiness() {
        ProviderReadiness readiness = readinessInternal();
        DebugDiagnostics.provider(PROVIDER_ID, "readiness", null, null, currency.classification(), null,
                capabilities(), readiness.lifecycle() == ProviderLifecycle.READY
                        ? "query or precheck before mutation" : "keep provider unavailable");
        return readiness;
    }

    @Override
    public ProviderResult<BalanceSnapshot> balance(UUID playerId) {
        if (playerId == null) {
            return ProviderResult.rejected(ProviderError.INVALID_REQUEST, "player id is required");
        }
        if (server == null || !server.isSameThread()) {
            return ProviderResult.unavailable(ProviderError.NOT_READY,
                    "danconomy balance query must run on the server thread");
        }
        ProviderReadiness readiness = readinessInternal();
        if (readiness.lifecycle() != ProviderLifecycle.READY) {
            return unavailable(readiness);
        }
        ProviderResult<BalanceSnapshot> result;
        try {
            result = ledger().futureshopsBalance(playerId, currency.currencyId());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            result = ProviderResult.unavailable(ProviderError.PROVIDER_EXCEPTION,
                    "danconomy balance query failed");
        }
        DebugDiagnostics.provider(PROVIDER_ID, "balance", playerId, result, currency.classification(), null,
                capabilities(), result.confirmed() ? "balance query has no value mutation"
                        : "keep the account unavailable");
        return result;
    }

    @Override
    public ProviderResult<BalanceSnapshot> precheck(MutationRequest request) {
        if (request == null) {
            return ProviderResult.rejected(ProviderError.INVALID_REQUEST, "mutation request is required");
        }
        ProviderResult<BalanceSnapshot> balance = balance(request.actor());
        if (!balance.confirmed()) {
            return balance;
        }
        long current = balance.value().orElseThrow().balanceMinorUnits();
        if (requiresFunds(request.kind()) && current < request.amountMinorUnits()) {
            ProviderResult<BalanceSnapshot> result = ProviderResult.rejected(ProviderError.INSUFFICIENT_FUNDS,
                    "insufficient danconomy funds");
            DebugDiagnostics.provider(PROVIDER_ID, "precheck", request.actor(), result,
                    currency.classification(), capabilities(), capabilities(),
                    "leave journal and custody unchanged");
            return result;
        }
        DebugDiagnostics.provider(PROVIDER_ID, "precheck", request.actor(), balance,
                currency.classification(), capabilities(), capabilities(),
                "continue through the durable coordinator");
        return balance;
    }

    @Override
    public ProviderResult<MutationReceipt> withdraw(MutationRequest request) {
        return mutate(request, MutationKind.WITHDRAW);
    }

    @Override
    public ProviderResult<MutationReceipt> deposit(MutationRequest request) {
        return mutate(request, MutationKind.DEPOSIT);
    }

    @Override
    public ProviderResult<MutationReceipt> lookup(RequestId requestId) {
        if (requestId == null) {
            return ProviderResult.rejected(ProviderError.INVALID_REQUEST, "receipt request is required");
        }
        if (server == null || !server.isSameThread()) {
            return ProviderResult.unavailable(ProviderError.NOT_READY,
                    "danconomy receipt lookup must run on the server thread");
        }
        ProviderReadiness readiness = readinessInternal();
        if (readiness.lifecycle() != ProviderLifecycle.READY
                && readiness.lifecycle() != ProviderLifecycle.RECOVERING) {
            return unavailable(readiness);
        }
        try {
            return ledger().futureshopsLookup(level(), requestId);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            return ProviderResult.unavailable(ProviderError.PROVIDER_EXCEPTION,
                    "danconomy receipt lookup failed");
        }
    }

    @Override
    public ProviderResult<MutationReceipt> retry(MutationRequest request) {
        return mutate(request, request == null ? MutationKind.WITHDRAW : mutationRoute(request.kind()));
    }

    private ProviderResult<MutationReceipt> mutate(MutationRequest request, MutationKind expectedRoute) {
        if (request == null || request.amountMinorUnits() <= 0L) {
            return ProviderResult.rejected(ProviderError.INVALID_REQUEST, "danconomy mutation request is invalid");
        }
        if (mutationRoute(request.kind()) != expectedRoute) {
            return ProviderResult.rejected(ProviderError.INVALID_REQUEST,
                    "danconomy mutation kind does not match route");
        }
        if (server == null || !server.isSameThread()) {
            return ProviderResult.unavailable(ProviderError.NOT_READY,
                    "danconomy mutation must run on the server thread");
        }
        ProviderReadiness readiness = readinessInternal();
        if (readiness.lifecycle() != ProviderLifecycle.READY
                && readiness.lifecycle() != ProviderLifecycle.RECOVERING) {
            return unavailable(readiness);
        }
        ProviderResult<MutationReceipt> result;
        try {
            result = ledger().futureshopsMutate(level(), request.actor(), currency.currencyId(),
                    request.requestId(), request.kind(), request.amountMinorUnits());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            result = ProviderResult.unavailable(ProviderError.PROVIDER_EXCEPTION,
                    "danconomy request aware mutation failed");
        }
        DebugDiagnostics.provider(PROVIDER_ID, "mutation", request.requestId(), request.actor(), result,
                currency.classification(),
                capabilities(), capabilities(), result.confirmed() ? "finalize the coordinator record"
                        : "follow the typed result and do not guess");
        return result;
    }

    private ProviderReadiness readinessInternal() {
        if (!runtime.available()) {
            return new ProviderReadiness(ProviderLifecycle.INCOMPATIBLE, runtime.diagnostic());
        }
        if (!currency.valid()) {
            return new ProviderReadiness(ProviderLifecycle.INCOMPATIBLE, currency.diagnostic());
        }
        if (server == null || !server.isSameThread()) {
            return new ProviderReadiness(ProviderLifecycle.MISSING,
                    "danconomy readiness must run on the server thread");
        }
        if (server == null || server.overworld() == null) {
            return new ProviderReadiness(ProviderLifecycle.MISSING, "danconomy server context is unavailable");
        }
        if (!runtime.mixinTargetAvailable()) {
            return new ProviderReadiness(ProviderLifecycle.INCOMPATIBLE,
                    "danconomy ledger is missing the request aware mixin");
        }
        try {
            DanConomyLedgerAccess ledger = ledger();
            if (!ledger.futureshopsReceiptIntegrityValid()) {
                return new ProviderReadiness(ProviderLifecycle.RECOVERING,
                        "danconomy receipt data is unknown or contradictory");
            }
            if (ledger.futureshopsHasPendingReceipts()) {
                return new ProviderReadiness(ProviderLifecycle.RECOVERING,
                        "danconomy receipt durability requires reconciliation");
            }
            if (!ledger.futureshopsReceiptCapacityAvailable()) {
                return new ProviderReadiness(ProviderLifecycle.FAILED,
                        "danconomy receipt capacity is exhausted");
            }
            return new ProviderReadiness(ProviderLifecycle.READY, "");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            return new ProviderReadiness(ProviderLifecycle.FAILED, "danconomy ledger readiness failed");
        }
    }

    private ServerLevel level() {
        return server.overworld();
    }

    private DanConomyLedgerAccess ledger() throws ReflectiveOperationException {
        Object ledger = runtime.ledger(level());
        if (!(ledger instanceof DanConomyLedgerAccess access)) {
            throw new ReflectiveOperationException("danconomy ledger mixin is unavailable");
        }
        return access;
    }

    private static boolean requiresFunds(MutationKind kind) {
        return kind == MutationKind.WITHDRAW || kind == MutationKind.TRANSFER_DEBIT
                || kind == MutationKind.FEE;
    }

    private static MutationKind mutationRoute(MutationKind kind) {
        return switch (kind) {
            case DEPOSIT, TRANSFER_CREDIT, REFUND, COMPENSATION -> MutationKind.DEPOSIT;
            default -> MutationKind.WITHDRAW;
        };
    }

    private static <T> ProviderResult<T> unavailable(ProviderReadiness readiness) {
        if (readiness.lifecycle() == ProviderLifecycle.RECOVERING
                || readiness.lifecycle() == ProviderLifecycle.FROZEN) {
            return ProviderResult.recoveryRequired(readiness.diagnostic());
        }
        ProviderError error = readiness.lifecycle() == ProviderLifecycle.INCOMPATIBLE
                ? ProviderError.INCOMPATIBLE : ProviderError.NOT_READY;
        return ProviderResult.unavailable(error, readiness.diagnostic());
    }

    private record CurrencyResolution(String currencyId, CurrencyMetadata metadata, boolean valid,
                                      String classification, String diagnostic) {
        static CurrencyResolution unavailable(String classification, String diagnostic) {
            return new CurrencyResolution("", UNAVAILABLE_CURRENCY, false, classification, diagnostic);
        }
    }

    private static final class RuntimeAccess {
        private static final String REGISTRY_CLASS = "com.danners45.danconomy.currency.CurrencyRegistry";
        private static final String CURRENCY_CLASS = "com.danners45.danconomy.currency.Currency";
        private static final String LEDGER_CLASS = "com.danners45.danconomy.data.LedgerData";

        private final Method getDefaultCurrencyId;
        private final Method getAllCurrencies;
        private final Method getCurrency;
        private final Method getCurrencyId;
        private final Method getSingularName;
        private final Method getPluralName;
        private final Method getDecimalPlaces;
        private final Method getBackingType;
        private final Method getLedger;
        private final boolean mixinTargetAvailable;
        private final String diagnostic;

        private RuntimeAccess(Method getDefaultCurrencyId, Method getAllCurrencies, Method getCurrency,
                              Method getCurrencyId, Method getSingularName, Method getPluralName,
                              Method getDecimalPlaces, Method getBackingType, Method getLedger,
                              boolean mixinTargetAvailable, String diagnostic) {
            this.getDefaultCurrencyId = getDefaultCurrencyId;
            this.getAllCurrencies = getAllCurrencies;
            this.getCurrency = getCurrency;
            this.getCurrencyId = getCurrencyId;
            this.getSingularName = getSingularName;
            this.getPluralName = getPluralName;
            this.getDecimalPlaces = getDecimalPlaces;
            this.getBackingType = getBackingType;
            this.getLedger = getLedger;
            this.mixinTargetAvailable = mixinTargetAvailable;
            this.diagnostic = diagnostic;
        }

        static RuntimeAccess load() {
            try {
                ClassLoader loader = DanConomyEconomyProvider.class.getClassLoader();
                Class<?> registry = Class.forName(REGISTRY_CLASS, false, loader);
                Class<?> currency = Class.forName(CURRENCY_CLASS, false, loader);
                Class<?> ledger = Class.forName(LEDGER_CLASS, false, loader);
                return new RuntimeAccess(registry.getMethod("getDefaultCurrencyId"),
                        registry.getMethod("getAll"), registry.getMethod("get", String.class),
                        currency.getMethod("getId"), currency.getMethod("getDisplayNameSingular"),
                        currency.getMethod("getDisplayNamePlural"), currency.getMethod("getDecimalPlaces"),
                        currency.getMethod("getBackingType"), ledger.getMethod("get", ServerLevel.class),
                        DanConomyLedgerAccess.class.isAssignableFrom(ledger), "");
            } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
                return new RuntimeAccess(null, null, null, null, null, null, null, null, null, false,
                        "danconomy 1.2.1 api is unavailable");
            }
        }

        boolean available() {
            return getDefaultCurrencyId != null;
        }

        boolean mixinTargetAvailable() {
            return mixinTargetAvailable;
        }

        String diagnostic() {
            return diagnostic;
        }

        CurrencyResolution resolveCurrency() {
            if (!available()) {
                return CurrencyResolution.unavailable("api_unavailable", diagnostic);
            }
            try {
                Object defaultValue = invoke(getDefaultCurrencyId, null);
                String defaultId = defaultValue instanceof String value ? value.trim() : "";
                if (defaultId.isBlank()) {
                    return CurrencyResolution.unavailable("missing_default",
                            "danconomy default currency is missing or ambiguous");
                }
                Object currenciesValue = invoke(getAllCurrencies, null);
                if (!(currenciesValue instanceof Map<?, ?> currencies) || currencies.isEmpty()) {
                    return CurrencyResolution.unavailable("missing_currency",
                            "danconomy has no registered currency");
                }
                Object selected = invoke(getCurrency, null, defaultId);
                if (selected == null) {
                    return CurrencyResolution.unavailable("missing_default",
                            "danconomy default currency is not registered");
                }
                String resolvedId = String.valueOf(invoke(getCurrencyId, selected));
                if (!defaultId.equalsIgnoreCase(resolvedId)) {
                    return CurrencyResolution.unavailable("ambiguous_default",
                            "danconomy default currency identity is ambiguous");
                }
                String backing = String.valueOf(invoke(getBackingType, selected));
                if (!"LEDGER".equals(backing)) {
                    return CurrencyResolution.unavailable("backing_" + backing.toLowerCase(java.util.Locale.ROOT),
                            "danconomy default currency is not ledger backed");
                }
                CurrencyMetadata metadata = new CurrencyMetadata(
                        String.valueOf(invoke(getSingularName, selected)),
                        String.valueOf(invoke(getPluralName, selected)),
                        ((Number) invoke(getDecimalPlaces, selected)).intValue());
                return new CurrencyResolution(resolvedId, metadata, true, "ledger:" + resolvedId, "");
            } catch (ReflectiveOperationException | RuntimeException exception) {
                return CurrencyResolution.unavailable("currency_error",
                        "danconomy currency metadata could not be resolved");
            }
        }

        Object ledger(ServerLevel level) throws ReflectiveOperationException {
            return invoke(getLedger, null, level);
        }

        private static Object invoke(Method method, Object target, Object... arguments)
                throws ReflectiveOperationException {
            try {
                return method.invoke(target, arguments);
            } catch (InvocationTargetException exception) {
                Throwable cause = exception.getCause();
                if (cause instanceof ReflectiveOperationException reflective) {
                    throw reflective;
                }
                if (cause instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw new ReflectiveOperationException("danconomy call failed", cause);
            }
        }
    }
}

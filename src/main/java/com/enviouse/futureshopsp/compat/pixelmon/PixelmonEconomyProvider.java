package com.enviouse.futureshopsp.compat.pixelmon;

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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/** Optional read only Pixelmon adapter. */
public final class PixelmonEconomyProvider implements com.enviouse.futureshopsp.api.economy.EconomyProvider {
    public static final String PROVIDER_ID = "pixelmon";
    public static final String SUPPORTED_VERSION = "9.4.0";
    private static final String PROXY_CLASS = "com.pixelmonmod.pixelmon.api.economy.BankAccountProxy";
    private static final String ACCOUNT_CLASS = "com.pixelmonmod.pixelmon.api.economy.BankAccount";
    private static final CurrencyMetadata CURRENCY = new CurrencyMetadata("PokéDollar", "PokéDollars", 0);
    private static final ProviderCapabilities CAPABILITIES = new ProviderCapabilities(true, true, false, false,
            false, false);

    private final RuntimeAccess runtime;

    public PixelmonEconomyProvider() {
        this.runtime = RuntimeAccess.load();
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
        return CURRENCY;
    }

    @Override
    public ProviderCapabilities capabilities() {
        return CAPABILITIES;
    }

    @Override
    public ProviderReadiness readiness() {
        if (!runtime.available()) {
            return new ProviderReadiness(ProviderLifecycle.FAILED, runtime.diagnostic());
        }
        try {
            if (!runtime.hasImplementation()) {
                return new ProviderReadiness(ProviderLifecycle.MISSING,
                        "pixelmon economy implementation is unavailable");
            }
            return new ProviderReadiness(ProviderLifecycle.READY, "");
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return new ProviderReadiness(ProviderLifecycle.FAILED,
                    "pixelmon economy readiness check failed");
        }
    }

    @Override
    public ProviderResult<BalanceSnapshot> balance(UUID playerId) {
        if (playerId == null) {
            return ProviderResult.rejected(ProviderError.INVALID_REQUEST, "player id is required");
        }
        AccountRead account = readAccount(playerId);
        if (!account.available()) {
            return ProviderResult.unavailable(account.error(), account.diagnostic());
        }
        try {
            return ProviderResult.confirmed(new BalanceSnapshot(playerId, toMinorUnits(account.balance())));
        } catch (ArithmeticException | IllegalArgumentException exception) {
            return ProviderResult.unavailable(ProviderError.PROVIDER_EXCEPTION,
                    "pixelmon balance is not an exact integer amount");
        }
    }

    @Override
    public ProviderResult<BalanceSnapshot> precheck(MutationRequest request) {
        if (request == null) {
            return ProviderResult.rejected(ProviderError.INVALID_REQUEST, "mutation request is required");
        }
        AccountRead account = readAccount(request.actor());
        if (!account.available()) {
            return ProviderResult.unavailable(account.error(), account.diagnostic());
        }
        long balance;
        try {
            balance = toMinorUnits(account.balance());
        } catch (ArithmeticException | IllegalArgumentException exception) {
            return ProviderResult.unavailable(ProviderError.PROVIDER_EXCEPTION,
                    "pixelmon balance is not an exact integer amount");
        }
        if (requiresFunds(request.kind())) {
            try {
                if (!runtime.hasBalance(account.value(), BigDecimal.valueOf(request.amountMinorUnits()))) {
                    return ProviderResult.rejected(ProviderError.INSUFFICIENT_FUNDS, "insufficient PokéDollars");
                }
            } catch (ReflectiveOperationException | RuntimeException exception) {
                return ProviderResult.unavailable(ProviderError.PROVIDER_EXCEPTION,
                        "pixelmon funds check failed");
            }
        }
        return ProviderResult.confirmed(new BalanceSnapshot(request.actor(), balance));
    }

    @Override
    public ProviderResult<MutationReceipt> withdraw(MutationRequest request) {
        return mutationRefused();
    }

    @Override
    public ProviderResult<MutationReceipt> deposit(MutationRequest request) {
        return mutationRefused();
    }

    @Override
    public ProviderResult<MutationReceipt> lookup(RequestId requestId) {
        return ProviderResult.rejected(ProviderError.CAPABILITY_MISSING,
                "pixelmon does not expose durable economy receipts");
    }

    @Override
    public ProviderResult<MutationReceipt> retry(MutationRequest request) {
        return mutationRefused();
    }

    private static boolean requiresFunds(MutationKind kind) {
        return kind == MutationKind.WITHDRAW || kind == MutationKind.TRANSFER_DEBIT
                || kind == MutationKind.FEE;
    }

    private static ProviderResult<MutationReceipt> mutationRefused() {
        return ProviderResult.rejected(ProviderError.CAPABILITY_MISSING,
                "pixelmon direct mutations require durable receipts");
    }

    private AccountRead readAccount(UUID playerId) {
        if (!runtime.available()) {
            return AccountRead.unavailable(ProviderError.PROVIDER_EXCEPTION, runtime.diagnostic());
        }
        try {
            if (!runtime.hasImplementation()) {
                return AccountRead.unavailable(ProviderError.NOT_READY,
                        "pixelmon economy implementation is unavailable");
            }
            Object account = runtime.account(playerId);
            if (account == null) {
                return AccountRead.unavailable(ProviderError.PROVIDER_EXCEPTION,
                        "pixelmon account is unavailable");
            }
            UUID identifier = runtime.identifier(account);
            if (!playerId.equals(identifier)) {
                return AccountRead.unavailable(ProviderError.PROVIDER_EXCEPTION,
                        "pixelmon account identity did not match player");
            }
            BigDecimal balance = runtime.balance(account);
            if (balance == null) {
                return AccountRead.unavailable(ProviderError.PROVIDER_EXCEPTION,
                        "pixelmon balance is unavailable");
            }
            return AccountRead.available(account, balance);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return AccountRead.unavailable(ProviderError.PROVIDER_EXCEPTION,
                    "pixelmon economy query failed");
        }
    }

    private static long toMinorUnits(BigDecimal balance) {
        return balance.setScale(0, RoundingMode.UNNECESSARY).longValueExact();
    }

    private record AccountRead(Object value, BigDecimal balance, ProviderError error, String diagnostic) {
        static AccountRead available(Object value, BigDecimal balance) {
            return new AccountRead(value, balance, ProviderError.NONE, "");
        }

        static AccountRead unavailable(ProviderError error, String diagnostic) {
            return new AccountRead(null, null, error, diagnostic);
        }

        boolean available() {
            return value != null && balance != null;
        }
    }

    private static final class RuntimeAccess {
        private final Method hasImplementation;
        private final Method getBankAccountNow;
        private final Method getIdentifier;
        private final Method getBalance;
        private final Method hasBalance;
        private final String diagnostic;

        private RuntimeAccess(Method hasImplementation, Method getBankAccountNow, Method getIdentifier,
                              Method getBalance, Method hasBalance, String diagnostic) {
            this.hasImplementation = hasImplementation;
            this.getBankAccountNow = getBankAccountNow;
            this.getIdentifier = getIdentifier;
            this.getBalance = getBalance;
            this.hasBalance = hasBalance;
            this.diagnostic = diagnostic;
        }

        static RuntimeAccess load() {
            try {
                Class<?> proxy = Class.forName(PROXY_CLASS, false, PixelmonEconomyProvider.class.getClassLoader());
                Class<?> account = Class.forName(ACCOUNT_CLASS, false, PixelmonEconomyProvider.class.getClassLoader());
                return new RuntimeAccess(
                        proxy.getMethod("hasImplementation"),
                        proxy.getMethod("getBankAccountNow", UUID.class),
                        account.getMethod("getIdentifier"),
                        account.getMethod("getBalance"),
                        account.getMethod("hasBalance", BigDecimal.class), "");
            } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
                return new RuntimeAccess(null, null, null, null, null,
                        "pixelmon 9.4.0 economy api is unavailable");
            }
        }

        boolean available() {
            return hasImplementation != null;
        }

        String diagnostic() {
            return diagnostic;
        }

        boolean hasImplementation() throws ReflectiveOperationException {
            return (Boolean) invokeStatic(hasImplementation);
        }

        Object account(UUID playerId) throws ReflectiveOperationException {
            return invoke(getBankAccountNow, null, playerId);
        }

        UUID identifier(Object account) throws ReflectiveOperationException {
            return (UUID) invoke(getIdentifier, account);
        }

        BigDecimal balance(Object account) throws ReflectiveOperationException {
            return (BigDecimal) invoke(getBalance, account);
        }

        boolean hasBalance(Object account, BigDecimal amount) throws ReflectiveOperationException {
            return (Boolean) invoke(hasBalance, account, amount);
        }

        private static Object invokeStatic(Method method, Object... arguments) throws ReflectiveOperationException {
            return invoke(method, null, arguments);
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
                throw new ReflectiveOperationException("pixelmon economy call failed", cause);
            }
        }
    }
}

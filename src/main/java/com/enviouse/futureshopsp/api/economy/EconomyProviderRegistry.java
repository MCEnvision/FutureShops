package com.enviouse.futureshopsp.api.economy;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Thread safe deterministic registry for optional economy providers.
 *
 * <p>Registration is allowed only before {@link #freeze()}. The registry sorts identifiers before
 * exposing snapshots, rejects duplicates instead of using load order, and creates each provider at
 * most once for a server lifecycle. The built in providers own reserved identifiers and are not
 * registered through the generic method.
 */
public final class EconomyProviderRegistry {
    private static final Object LOCK = new Object();
    private static final Map<String, ProviderRegistration> REGISTRATIONS = new TreeMap<>();
    private static boolean frozen;

    private EconomyProviderRegistry() {
    }

    public static RegistrationResult register(
            String providerId, int compatibilityVersion, EconomyProviderFactory factory) {
        if (!EconomyApi.isValidProviderId(providerId)) {
            return new RegistrationResult(RegistrationStatus.INVALID_IDENTIFIER,
                    String.valueOf(providerId), "provider identifier is invalid");
        }
        if (factory == null || compatibilityVersion < 1) {
            return new RegistrationResult(RegistrationStatus.INVALID_ARGUMENT,
                    providerId, "registration arguments are invalid");
        }
        if (EconomyApi.isReservedProviderId(providerId)) {
            return new RegistrationResult(RegistrationStatus.RESERVED,
                    providerId, "provider identifier is reserved");
        }
        return registerValidated(providerId, compatibilityVersion, factory);
    }

    /** Registers the separately installed Vault bridge under its reserved identifier. */
    public static RegistrationResult registerVault(
            int compatibilityVersion, EconomyProviderFactory factory) {
        if (factory == null || compatibilityVersion < 1) {
            return new RegistrationResult(RegistrationStatus.INVALID_ARGUMENT,
                    EconomyApi.VAULT_PROVIDER_ID, "registration arguments are invalid");
        }
        return registerValidated(EconomyApi.VAULT_PROVIDER_ID, compatibilityVersion, factory);
    }

    private static RegistrationResult registerValidated(
            String providerId, int compatibilityVersion, EconomyProviderFactory factory) {
        synchronized (LOCK) {
            if (frozen) {
                return new RegistrationResult(RegistrationStatus.LATE,
                        providerId, "provider registry is already frozen");
            }
            if (REGISTRATIONS.containsKey(providerId)) {
                return new RegistrationResult(RegistrationStatus.DUPLICATE,
                        providerId, "provider identifier is already registered");
            }
            REGISTRATIONS.put(providerId, new ProviderRegistration(providerId, compatibilityVersion, factory));
            return new RegistrationResult(RegistrationStatus.ACCEPTED, providerId, "");
        }
    }

    public static void freeze() {
        synchronized (LOCK) {
            frozen = true;
        }
    }

    public static boolean isFrozen() {
        synchronized (LOCK) {
            return frozen;
        }
    }

    public static Map<String, ProviderRegistration> snapshot() {
        synchronized (LOCK) {
            return Collections.unmodifiableMap(new TreeMap<>(REGISTRATIONS));
        }
    }

    public static ProviderResolution resolve(String providerId, EconomyProviderContext context) {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(context, "context");
        ProviderRegistration registration;
        synchronized (LOCK) {
            if (!frozen) {
                return unresolved(providerId, "provider registry is not frozen");
            }
            registration = REGISTRATIONS.get(providerId);
        }
        if (registration == null) {
            return new ProviderResolution(providerId, ProviderLifecycle.MISSING,
                    java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty(),
                    "provider is not registered");
        }
        if (registration.compatibilityVersion() != EconomyApi.COMPATIBILITY_VERSION) {
            return new ProviderResolution(providerId, ProviderLifecycle.INCOMPATIBLE,
                    java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty(),
                    "provider compatibility version is unsupported");
        }

        EconomyProvider provider;
        try {
            provider = registration.factory().create(context);
        } catch (RuntimeException exception) {
            return new ProviderResolution(providerId, ProviderLifecycle.FAILED,
                    java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty(),
                    "provider factory failed");
        }
        if (provider == null || !providerId.equals(provider.providerId())
                || provider.compatibilityVersion() != EconomyApi.COMPATIBILITY_VERSION) {
            return new ProviderResolution(providerId, ProviderLifecycle.INCOMPATIBLE,
                    java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty(),
                    "provider identity or compatibility version does not match registration");
        }

        CurrencyMetadata currency;
        ProviderCapabilities capabilities;
        ProviderReadiness readiness;
        try {
            currency = Objects.requireNonNull(provider.currency(), "currency");
            capabilities = Objects.requireNonNull(provider.capabilities(), "capabilities");
            readiness = Objects.requireNonNull(provider.readiness(), "readiness");
        } catch (RuntimeException exception) {
            return new ProviderResolution(providerId, ProviderLifecycle.FAILED,
                    java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty(),
                    "provider metadata or readiness failed validation");
        }
        ProviderLifecycle lifecycle = readiness.lifecycle();
        if (lifecycle != ProviderLifecycle.READY) {
            return new ProviderResolution(providerId, lifecycle,
                    java.util.Optional.of(provider), java.util.Optional.of(currency),
                    java.util.Optional.of(capabilities), readiness.diagnostic());
        }
        return new ProviderResolution(providerId, ProviderLifecycle.READY,
                java.util.Optional.of(provider), java.util.Optional.of(currency),
                java.util.Optional.of(capabilities), "");
    }

    static void resetForTests() {
        synchronized (LOCK) {
            REGISTRATIONS.clear();
            frozen = false;
        }
    }

    private static ProviderResolution unresolved(String providerId, String diagnostic) {
        return new ProviderResolution(providerId, ProviderLifecycle.UNRESOLVED,
                java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty(), diagnostic);
    }
}

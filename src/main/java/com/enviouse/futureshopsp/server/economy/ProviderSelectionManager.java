package com.enviouse.futureshopsp.server.economy;

import com.enviouse.futureshopsp.api.economy.EconomyApi;

/** Server owned restart only provider selection. */
public final class ProviderSelectionManager {
    private static final Object LOCK = new Object();
    private static String activeProviderId = "";
    private static String stagedProviderId = "";
    private static boolean resolved;
    private static String diagnostic = "";

    private ProviderSelectionManager() {
    }

    public static ProviderSelectionSnapshot resolveAtStartup(String configuredProviderId) {
        synchronized (LOCK) {
            String selected = defaultIfAbsent(configuredProviderId);
            activeProviderId = selected;
            stagedProviderId = selected;
            resolved = true;
            diagnostic = validateDiagnostic(selected);
            return snapshotLocked();
        }
    }

    public static ProviderSelectionSnapshot stageReload(String configuredProviderId) {
        synchronized (LOCK) {
            stagedProviderId = defaultIfAbsent(configuredProviderId);
            diagnostic = validateDiagnostic(stagedProviderId);
            return snapshotLocked();
        }
    }

    public static ProviderSelectionSnapshot snapshot() {
        synchronized (LOCK) {
            return snapshotLocked();
        }
    }

    static void resetForTests() {
        synchronized (LOCK) {
            activeProviderId = "";
            stagedProviderId = "";
            resolved = false;
            diagnostic = "";
        }
    }

    private static String defaultIfAbsent(String configuredProviderId) {
        return configuredProviderId == null || configuredProviderId.isBlank()
                ? EconomyApi.INTERNAL_PROVIDER_ID
                : configuredProviderId;
    }

    private static String validateDiagnostic(String selected) {
        return EconomyApi.isValidProviderId(selected) ? "" : "configured provider identifier is invalid";
    }

    private static ProviderSelectionSnapshot snapshotLocked() {
        return new ProviderSelectionSnapshot(activeProviderId, stagedProviderId, resolved,
                resolved && !activeProviderId.equals(stagedProviderId), diagnostic);
    }
}

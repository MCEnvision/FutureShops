package com.enviouse.futureshopsp.api.economy;

import java.util.Locale;

/** Stable constants and identifier validation for the FutureShops economy API. */
public final class EconomyApi {
    public static final int COMPATIBILITY_VERSION = 1;
    public static final String INTERNAL_PROVIDER_ID = "internal";
    public static final String VAULT_PROVIDER_ID = "vault";

    private EconomyApi() {
    }

    public static boolean isValidProviderId(String providerId) {
        if (providerId == null || providerId.length() < 2 || providerId.length() > 64) {
            return false;
        }
        if (providerId.charAt(0) < 'a' || providerId.charAt(0) > 'z') {
            return false;
        }
        for (int index = 1; index < providerId.length(); index++) {
            char character = providerId.charAt(index);
            if (!((character >= 'a' && character <= 'z')
                    || (character >= '0' && character <= '9')
                    || character == '_')) {
                return false;
            }
        }
        return providerId.equals(providerId.toLowerCase(Locale.ROOT));
    }

    public static boolean isReservedProviderId(String providerId) {
        return INTERNAL_PROVIDER_ID.equals(providerId) || VAULT_PROVIDER_ID.equals(providerId);
    }
}

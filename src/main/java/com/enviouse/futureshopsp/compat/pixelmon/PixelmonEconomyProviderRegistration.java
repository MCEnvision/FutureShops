package com.enviouse.futureshopsp.compat.pixelmon;

import com.enviouse.futureshopsp.api.economy.EconomyApi;
import com.enviouse.futureshopsp.api.economy.EconomyProviderRegistry;
import com.enviouse.futureshopsp.api.economy.RegistrationResult;
import com.enviouse.futureshopsp.api.economy.RegistrationStatus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;

/** Registers the exact Pixelmon adapter. */
public final class PixelmonEconomyProviderRegistration {
    private PixelmonEconomyProviderRegistration() {
    }

    public static RegistrationResult register() {
        try {
            if (!ModList.get().isLoaded(PixelmonEconomyProvider.PROVIDER_ID)) {
                return new RegistrationResult(RegistrationStatus.INCOMPATIBLE,
                        PixelmonEconomyProvider.PROVIDER_ID, "pixelmon is not installed");
            }
            ModContainer container = ModList.get()
                    .getModContainerById(PixelmonEconomyProvider.PROVIDER_ID)
                    .orElse(null);
            String version = container == null || container.getModInfo() == null
                    || container.getModInfo().getVersion() == null
                    ? ""
                    : container.getModInfo().getVersion().toString();
            if (!isSupportedVersion(version)) {
                return new RegistrationResult(RegistrationStatus.INCOMPATIBLE,
                        PixelmonEconomyProvider.PROVIDER_ID, "pixelmon version is unsupported");
            }
            return EconomyProviderRegistry.register(PixelmonEconomyProvider.PROVIDER_ID,
                    EconomyApi.COMPATIBILITY_VERSION, context -> new PixelmonEconomyProvider(context.server()));
        } catch (RuntimeException exception) {
            return new RegistrationResult(RegistrationStatus.INCOMPATIBLE,
                    PixelmonEconomyProvider.PROVIDER_ID, "pixelmon compatibility check failed");
        }
    }

    public static boolean isSupportedVersion(String version) {
        return PixelmonEconomyProvider.SUPPORTED_VERSION.equals(version);
    }
}

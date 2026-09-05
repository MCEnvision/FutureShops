package com.enviouse.futureshopsp.compat.danconomy;

import com.enviouse.futureshopsp.api.economy.EconomyApi;
import com.enviouse.futureshopsp.api.economy.EconomyProviderRegistry;
import com.enviouse.futureshopsp.api.economy.RegistrationResult;
import com.enviouse.futureshopsp.api.economy.RegistrationStatus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;

public final class DanConomyEconomyProviderRegistration {
    private DanConomyEconomyProviderRegistration() {
    }

    public static RegistrationResult register() {
        try {
            if (!ModList.get().isLoaded(DanConomyEconomyProvider.PROVIDER_ID)) {
                return new RegistrationResult(RegistrationStatus.INCOMPATIBLE,
                        DanConomyEconomyProvider.PROVIDER_ID, "danconomy is not installed");
            }
            ModContainer container = ModList.get()
                    .getModContainerById(DanConomyEconomyProvider.PROVIDER_ID)
                    .orElse(null);
            String version = container == null || container.getModInfo() == null
                    || container.getModInfo().getVersion() == null
                    ? ""
                    : container.getModInfo().getVersion().toString();
            if (!isSupportedVersion(version)) {
                return new RegistrationResult(RegistrationStatus.INCOMPATIBLE,
                        DanConomyEconomyProvider.PROVIDER_ID, "danconomy version is unsupported");
            }
            return EconomyProviderRegistry.registerDanconomy(EconomyApi.COMPATIBILITY_VERSION,
                    context -> new DanConomyEconomyProvider(context.server()));
        } catch (RuntimeException exception) {
            return new RegistrationResult(RegistrationStatus.INCOMPATIBLE,
                    DanConomyEconomyProvider.PROVIDER_ID, "danconomy compatibility check failed");
        }
    }

    public static boolean isSupportedVersion(String version) {
        return DanConomyEconomyProvider.SUPPORTED_VERSION.equals(version);
    }
}

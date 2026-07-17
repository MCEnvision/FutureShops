package com.enviouse.futureshops.server.market.bazaar;

import com.enviouse.futureshops.config.BazaarConfig;
import com.enviouse.futureshops.server.escrow.runtime.EscrowRuntimeService;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public final class BazaarProductCatalogRuntime {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static BazaarProductCatalogCoordinator coordinator =
            new BazaarProductCatalogCoordinator(
                    new BazaarProductCatalogRegistry());
    private static BazaarProductCatalogCoordinator.ReconciliationResult
            lastResult;

    private BazaarProductCatalogRuntime() {
    }

    public static synchronized BazaarProductCatalogCoordinator
            .ReconciliationResult reload(EscrowRuntimeService runtime) {
        Objects.requireNonNull(runtime, "runtime");
        Path directory = productDirectory();
        try {
            Files.createDirectories(directory);
        } catch (IOException exception) {
            LOGGER.error("Unable to create FutureShops Bazaar product directory.",
                    exception);
        }
        BazaarConfig.Settings settings = BazaarConfig.settings();
        lastResult = coordinator.reloadAndReconcile(directory,
                settings.productDefaults(),
                settings.orders().maximumQuantity(),
                BazaarProductCatalogRuntime::registeredItem,
                new RuntimeBackend(runtime));
        if (!lastResult.complete()) {
            LOGGER.error("Rejected FutureShops Bazaar product reload. {}",
                    lastResult.failure().orElse(
                            "Bazaar product reconciliation failed"));
        } else {
            LOGGER.info("Loaded {} FutureShops Bazaar product definitions.",
                    lastResult.reload().snapshot().definitions().size());
        }
        return lastResult;
    }

    public static synchronized BazaarProductCatalogSnapshot current() {
        return coordinator.current();
    }

    public static synchronized Optional<BazaarProductCatalogCoordinator
            .ReconciliationResult> lastResult() {
        return Optional.ofNullable(lastResult);
    }

    public static synchronized void clear() {
        coordinator = new BazaarProductCatalogCoordinator(
                new BazaarProductCatalogRegistry());
        lastResult = null;
    }

    public static Path productDirectory() {
        return FMLPaths.CONFIGDIR.get().resolve("futureshops")
                .resolve("bazaar").resolve("products");
    }

    private static boolean registeredItem(String registryId) {
        ResourceLocation key = ResourceLocation.tryParse(registryId);
        return key != null && ForgeRegistries.ITEMS.containsKey(key)
                && ForgeRegistries.ITEMS.getValue(key) != null
                && ForgeRegistries.ITEMS.getValue(key) != Items.AIR;
    }

    private record RuntimeBackend(EscrowRuntimeService runtime)
            implements BazaarProductCatalogCoordinator.MutationBackend {
        private RuntimeBackend {
            Objects.requireNonNull(runtime, "runtime");
        }

        @Override
        public BazaarOrderBookSnapshot snapshot() {
            return runtime.bazaarSnapshot();
        }

        @Override
        public BazaarMutation.ApplyResult commit(BazaarMutation mutation) {
            return runtime.commitBazaarMutation(mutation);
        }
    }
}

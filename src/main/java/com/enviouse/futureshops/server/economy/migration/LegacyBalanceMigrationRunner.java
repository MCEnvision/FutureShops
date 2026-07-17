package com.enviouse.futureshops.server.economy.migration;

import com.enviouse.futureshops.Config;
import com.enviouse.futureshops.server.economy.InternalBalanceSavedData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.Objects;

public final class LegacyBalanceMigrationRunner {
    private LegacyBalanceMigrationRunner() {
    }

    public static LegacyBalanceMigrationBatchResult runNextBatch(
            MinecraftServer server,
            int batchSize
    ) {
        Objects.requireNonNull(server, "server");
        if (!server.isSameThread()) {
            throw new IllegalStateException(
                    "Legacy migration must run on the server thread");
        }
        DimensionDataStorage storage = server.overworld().getDataStorage();
        InternalBalanceSavedData legacy = storage.computeIfAbsent(
                InternalBalanceSavedData::load,
                InternalBalanceSavedData::new,
                InternalBalanceSavedData.DATA_NAME);
        LegacyBalanceMigrationSavedData migration =
                LegacyBalanceMigrationSavedData.get(server);
        LegacyBalanceMigrator migrator = new LegacyBalanceMigrator(
                new LiveEscrowWalletInitializationGateway(),
                new LegacyBalanceMigrationPolicy(
                        Config.economyAllowNegative));
        return migrator.runBatch(
                legacy, migration, batchSize, storage::save);
    }
}

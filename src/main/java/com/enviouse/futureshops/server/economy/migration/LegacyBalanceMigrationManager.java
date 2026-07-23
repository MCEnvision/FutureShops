package com.enviouse.futureshops.server.economy.migration;

import com.enviouse.futureshops.config.EscrowConfig;
import com.enviouse.futureshops.server.economy.InternalBalanceSavedData;
import com.enviouse.futureshops.server.escrow.runtime.EscrowRuntimeManager;
import com.enviouse.futureshops.server.escrow.runtime.EscrowRuntimeService;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class LegacyBalanceMigrationManager {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static MinecraftServer activeServer;
    private static LegacyBalanceMigrationBatchResult lastResult;
    private static Throwable failure;

    private LegacyBalanceMigrationManager() {
    }

    public static synchronized void initialize(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        requireServerThread(server);
        if (activeServer != null) {
            throw new IllegalStateException(
                    "Legacy wallet migration is already initialized");
        }
        activeServer = server;
        lastResult = null;
        failure = null;
        tick(server);
    }

    public static synchronized void tick(MinecraftServer server) {
        requireActiveServer(server);
        if (isComplete() || isFailed()) {
            return;
        }
        EscrowRuntimeService runtime = EscrowRuntimeManager.getOrNull();
        if (runtime == null || !runtime.isReady()) {
            return;
        }
        try {
            lastResult = LegacyBalanceMigrationRunner.runNextBatch(
                    server,
                    EscrowConfig.settings().walletMigrationEntriesPerTick());
            if (lastResult.stage() == LegacyBalanceMigrationStage.FAILED) {
                LOGGER.error(
                        "FutureShops legacy wallet migration failed. {}. Players {}.",
                        lastResult.detail(), lastResult.affectedPlayers());
            } else if (lastResult.stage()
                    == LegacyBalanceMigrationStage.COMPLETE) {
                LOGGER.info(
                        "FutureShops legacy wallet migration completed with {} entries.",
                        lastResult.totalEntries());
            }
        } catch (RuntimeException exception) {
            failure = exception;
            LOGGER.error("FutureShops legacy wallet migration stopped.",
                    exception);
        }
    }

    public static synchronized boolean isComplete() {
        return lastResult != null
                && lastResult.stage() == LegacyBalanceMigrationStage.COMPLETE;
    }

    public static synchronized boolean isFailed() {
        return failure != null || lastResult != null
                && lastResult.stage() == LegacyBalanceMigrationStage.FAILED;
    }

    public static synchronized long displayBalance(
            MinecraftServer server,
            UUID playerId,
            long defaultBalance
    ) {
        requireActiveServer(server);
        InternalBalanceSavedData legacy = server.overworld().getDataStorage()
                .computeIfAbsent(
                        InternalBalanceSavedData::load,
                        InternalBalanceSavedData::new,
                        InternalBalanceSavedData.DATA_NAME);
        return legacy.findBalance(Objects.requireNonNull(
                        playerId, "playerId"))
                .orElse(defaultBalance);
    }

    public static synchronized void requireComplete() {
        if (!isComplete()) {
            String state = failure != null
                    ? failure.getClass().getSimpleName()
                    : lastResult == null
                    ? "WAITING_FOR_ESCROW"
                    : lastResult.stage().name();
            throw new IllegalStateException(
                    "Legacy wallet migration is not complete. " + state);
        }
    }

    public static synchronized Optional<LegacyBalanceMigrationBatchResult>
    lastResult() {
        return Optional.ofNullable(lastResult);
    }

    public static synchronized Optional<Throwable> failure() {
        return Optional.ofNullable(failure);
    }

    public static synchronized void shutdown(MinecraftServer server) {
        requireActiveServer(server);
        activeServer = null;
        lastResult = null;
        failure = null;
    }

    private static void requireActiveServer(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        requireServerThread(server);
        if (activeServer == null || activeServer != server) {
            throw new IllegalStateException(
                    "Legacy wallet migration belongs to another server");
        }
    }

    private static void requireServerThread(MinecraftServer server) {
        if (!server.isSameThread()) {
            throw new IllegalStateException(
                    "Legacy wallet migration must run on the server thread");
        }
    }
}

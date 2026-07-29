package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.config.EscrowConfig;
import net.minecraft.server.MinecraftServer;

import java.util.Objects;

public final class EscrowRuntimeManager {
    private static MinecraftServer activeServer;
    private static EscrowRuntimeService runtime;
    private static boolean quiescing;

    private EscrowRuntimeManager() {
    }

    public static synchronized EscrowRuntimeService initialize(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        requireServerThread(server);
        if (runtime != null || activeServer != null || quiescing) {
            throw new EscrowRuntimeException("Escrow runtime is already initialized");
        }
        EscrowRuntimeService opened = EscrowRuntimeService.open(
                server, recoveryWorkPerTick());
        activeServer = server;
        runtime = opened;
        return opened;
    }

    public static synchronized void tick(MinecraftServer server) {
        requireActiveServer(server);
        if (quiescing) {
            return;
        }
        if (runtime.shouldRunRecovery()) {
            runtime.recoverBatch(recoveryWorkPerTick());
        }
        EscrowConfig.Settings settings = EscrowConfig.settings();
        runtime.tickCheckpoint(settings.checkpointIntervalSeconds(),
                settings.checkpointMaximumJournalBytes(),
                settings.checkpointMaximumJournalRecords());
    }

    public static synchronized EscrowRuntimeService getOrNull() {
        return runtime;
    }

    public static synchronized EscrowRuntimeService requireReady() {
        if (runtime == null || quiescing) {
            throw new EscrowRuntimeException("Escrow runtime is not initialized");
        }
        if (!runtime.isReady()) {
            Throwable cause = runtime.failure().orElse(null);
            throw new EscrowRuntimeException(
                    "Escrow runtime is not ready and is in state " + runtime.state(), cause);
        }
        return runtime;
    }

    public static synchronized void shutdown(MinecraftServer server) {
        requireActiveServer(server);
        quiescing = true;
        RuntimeException failure = null;
        try {
            runtime.close();
        } catch (RuntimeException exception) {
            failure = exception;
        }
        try {
            ServerShopOfferReplayLedger.shutdown(server);
        } catch (RuntimeException exception) {
            if (failure == null) {
                failure = exception;
            } else {
                failure.addSuppressed(exception);
            }
        } finally {
            runtime = null;
            activeServer = null;
            quiescing = false;
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static void requireActiveServer(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        requireServerThread(server);
        if (runtime == null || activeServer == null) {
            throw new EscrowRuntimeException("Escrow runtime is not initialized");
        }
        if (activeServer != server) {
            throw new EscrowRuntimeException("Escrow runtime belongs to another server");
        }
    }

    private static void requireServerThread(MinecraftServer server) {
        if (!server.isSameThread()) {
            throw new EscrowRuntimeException(
                    "Escrow runtime lifecycle must run on the owning server thread");
        }
    }

    private static int recoveryWorkPerTick() {
        return Math.min(EscrowConfig.settings().recoveryWorkPerTick(),
                EscrowRuntimeCoordinator.MAX_RECOVERY_BATCH_SIZE);
    }
}

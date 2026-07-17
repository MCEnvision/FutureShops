package com.enviouse.futureshops.server.security;

import com.enviouse.futureshops.config.EscrowConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

public final class ServerRequestSecurityManager {
    private static final ServerRequestSecurityLifecycle<
            ServerRequestSecurityManager> LIFECYCLE =
            new ServerRequestSecurityLifecycle<>();

    private final MinecraftServer server;
    private final ServerRequestRateLimiter limiter;
    private boolean closed;

    private ServerRequestSecurityManager(
            MinecraftServer server,
            ServerRequestSecuritySettings settings
    ) {
        this.server = Objects.requireNonNull(server, "server");
        this.limiter = ServerRequestSecurityPolicy.createLimiter(
                System::nanoTime, settings);
    }

    public static void initialize(MinecraftServer server) {
        MinecraftServer exactServer = Objects.requireNonNull(server, "server");
        ServerRequestSecuritySettings settings = Objects.requireNonNull(
                EscrowConfig.settings().requestSecurity(),
                "requestSecurity");
        LIFECYCLE.initialize(exactServer,
                () -> new ServerRequestSecurityManager(
                        exactServer, settings));
    }

    public static void shutdown(MinecraftServer server) {
        MinecraftServer exactServer = Objects.requireNonNull(server, "server");
        LIFECYCLE.clear(exactServer).ifPresent(
                manager -> manager.close(exactServer));
    }

    public static GateDecision tryAcquire(
            ServerPlayer player,
            ServerRequestAction action
    ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(action, "action");
        MinecraftServer playerServer = player.getServer();
        if (playerServer == null) {
            return GateDecision.denyUnavailable();
        }
        return LIFECYCLE.find(playerServer)
                .map(manager -> manager.acquire(
                        playerServer, player.getUUID(), action))
                .orElseGet(GateDecision::denyUnavailable);
    }

    public static int removePlayer(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        MinecraftServer playerServer = player.getServer();
        if (playerServer == null) {
            return 0;
        }
        return LIFECYCLE.find(playerServer)
                .map(manager -> manager.removePlayer(
                        playerServer, player.getUUID()))
                .orElse(0);
    }

    private synchronized GateDecision acquire(
            MinecraftServer requestServer,
            UUID playerId,
            ServerRequestAction action
    ) {
        if (closed || requestServer != server) {
            return GateDecision.denyUnavailable();
        }

        ServerRequestRateLimiter.Decision decision =
                limiter.tryAcquire(playerId, action.code());
        if (decision.allowed()) {
            return GateDecision.allow();
        }
        return switch (decision.reason()) {
            case RATE_LIMITED, CACHE_FULL ->
                    GateDecision.denyRateLimited(decision.retryAfter());
            case UNKNOWN_ACTION -> GateDecision.denyUnavailable();
            case ALLOWED -> throw new IllegalStateException(
                    "Allowed rate decision was rejected");
        };
    }

    private synchronized int removePlayer(
            MinecraftServer requestServer,
            UUID playerId
    ) {
        if (closed || requestServer != server) {
            return 0;
        }
        return limiter.removePlayer(playerId);
    }

    private synchronized void close(MinecraftServer requestServer) {
        if (requestServer != server) {
            throw new IllegalStateException(
                    "Server request security belongs to another server");
        }
        if (!closed) {
            closed = true;
            limiter.clear();
        }
    }

    public record GateDecision(
            boolean allowed,
            GateStatus status,
            Duration retryAfter
    ) {
        public GateDecision {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(retryAfter, "retryAfter");
            if (allowed != (status == GateStatus.ALLOWED)
                    || retryAfter.isNegative()
                    || status != GateStatus.RATE_LIMITED
                    && !retryAfter.isZero()) {
                throw new IllegalArgumentException(
                        "Server request gate decision is invalid");
            }
        }

        private static GateDecision allow() {
            return new GateDecision(
                    true, GateStatus.ALLOWED, Duration.ZERO);
        }

        private static GateDecision denyRateLimited(Duration retryAfter) {
            return new GateDecision(
                    false, GateStatus.RATE_LIMITED, retryAfter);
        }

        private static GateDecision denyUnavailable() {
            return new GateDecision(
                    false, GateStatus.UNAVAILABLE, Duration.ZERO);
        }
    }

    public enum GateStatus {
        ALLOWED,
        RATE_LIMITED,
        UNAVAILABLE
    }
}

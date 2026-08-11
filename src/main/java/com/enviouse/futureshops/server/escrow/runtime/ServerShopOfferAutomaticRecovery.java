package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.Futureshops;
import com.enviouse.futureshops.server.transaction
        .ServerShopOfferUsageSavedData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

@Mod.EventBusSubscriber(
        modid = Futureshops.MODID,
        bus = Mod.EventBusSubscriber.Bus.FORGE
)
public final class ServerShopOfferAutomaticRecovery {
    private static final org.slf4j.Logger LOGGER =
            com.mojang.logging.LogUtils.getLogger();
    static final int LOGIN_ATTEMPT_LIMIT = 16;
    static final int TICK_ATTEMPT_LIMIT = 8;
    static final int PLAYER_TICK_ATTEMPT_LIMIT = 2;
    static final int TICK_INTERVAL = 40;
    private static boolean replayRecoveryFailureLogged;

    private ServerShopOfferAutomaticRecovery() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(
            PlayerEvent.PlayerLoggedInEvent event
    ) {
        if (event.getEntity() instanceof ServerPlayer player) {
            recoverPlayer(player, LOGIN_ATTEMPT_LIMIT);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        MinecraftServer server = event.getServer();
        if (event.phase != TickEvent.Phase.END
                || server == null
                || server.getTickCount() % TICK_INTERVAL != 0) {
            return;
        }
        EscrowRuntimeService runtime = EscrowRuntimeManager.getOrNull();
        if (runtime == null || !runtime.isReady()) {
            return;
        }
        try {
            ServerShopOfferUsageSavedData.get(server);
            replayRecoveryFailureLogged = false;
        } catch (RuntimeException exception) {
            if (!replayRecoveryFailureLogged) {
                replayRecoveryFailureLogged = true;
                LOGGER.error(
                        "Server shop replay usage recovery failed",
                        exception);
            }
            return;
        }
        List<ServerPlayer> players =
                server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            return;
        }
        int cycle = server.getTickCount() / TICK_INTERVAL;
        int start = (int) Math.floorMod(
                (long) cycle * (TICK_ATTEMPT_LIMIT
                        / PLAYER_TICK_ATTEMPT_LIMIT),
                players.size());
        int remaining = TICK_ATTEMPT_LIMIT;
        int examined = 0;
        while (remaining > 0 && examined < players.size()) {
            ServerPlayer player = players.get(
                    Math.floorMod(start + examined, players.size()));
            int limit = Math.min(
                    PLAYER_TICK_ATTEMPT_LIMIT, remaining);
            recoverPlayer(player, limit);
            remaining -= limit;
            examined++;
        }
    }

    static int recoverPlayer(ServerPlayer player, int limit) {
        if (limit <= 0 || limit > LOGIN_ATTEMPT_LIMIT
                || player.getServer() == null) {
            return 0;
        }
        EscrowRuntimeService runtime = EscrowRuntimeManager.getOrNull();
        if (runtime == null || !runtime.isReady()) {
            return 0;
        }
        int singleLimit = Math.max(1, limit / 2);
        int cartLimit = limit - singleLimit;
        int attempts = recoverSingles(player, singleLimit);
        attempts += recoverCarts(player, cartLimit);
        return attempts;
    }

    private static int recoverSingles(
            ServerPlayer player,
            int limit
    ) {
        if (limit <= 0) {
            return 0;
        }
        List<ServerShopOfferPreparedSavedData.Entry> entries =
                ServerShopOfferPreparedSavedData.get(
                        player.getServer()).takeUnresolvedForPlayer(
                        player.getUUID(), limit);
        for (ServerShopOfferPreparedSavedData.Entry entry : entries) {
            ServerShopOfferService.recoverPersisted(
                    player, entry.requestId());
        }
        return entries.size();
    }

    private static int recoverCarts(
            ServerPlayer player,
            int limit
    ) {
        if (limit <= 0) {
            return 0;
        }
        List<ServerShopOfferCartPreparedSavedData.Entry> entries =
                ServerShopOfferCartPreparedSavedData.get(
                        player.getServer()).takeUnresolvedForPlayer(
                        player.getUUID(), limit);
        for (ServerShopOfferCartPreparedSavedData.Entry entry : entries) {
            ServerShopOfferCartService.recoverPersisted(
                    player, entry.requestId());
        }
        return entries.size();
    }
}

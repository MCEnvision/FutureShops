package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.Futureshops;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.time.Instant;

@Mod.EventBusSubscriber(
        modid = Futureshops.MODID,
        bus = Mod.EventBusSubscriber.Bus.FORGE
)
public final class ExactItemClaimLoginCollectionHandler {
    private static final int LOGIN_COLLECTION_LIMIT = 16;

    private ExactItemClaimLoginCollectionHandler() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(
            PlayerEvent.PlayerLoggedInEvent event
    ) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        EscrowRuntimeService runtime = EscrowRuntimeManager.getOrNull();
        if (runtime == null || !runtime.isReady()) {
            return;
        }
        runtime.collectPendingExactItemClaims(player,
                LOGIN_COLLECTION_LIMIT, Instant.now());
    }
}

package com.enviouse.futureshops.server.session;

import com.enviouse.futureshops.Config;
import com.enviouse.futureshops.Futureshops;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Forge gameplay-event handler that enforces shop-session lifecycle rules.
 *
 * <p>Auto-registered to the Forge event bus via {@link Mod.EventBusSubscriber}.
 *
 * <h3>Triggers</h3>
 * <ul>
 *   <li>Player logout → silent close (no packet; connection is already going away).</li>
 *   <li>Player death  → force-close with reason {@code "DEATH"}.</li>
 *   <li>Player damage → force-close with reason {@code "DAMAGE"} if
 *       {@code session.close_on_damage = true}.</li>
 *   <li>Player tick (every 20 ticks) → distance check against the shop block;
 *       force-close with reason {@code "DISTANCE"} when the player exceeds
 *       {@code session.max_distance_blocks}.  Skipped for command-opened sessions
 *       (no block position) or when max distance ≤ 0 (disabled).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Futureshops.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SessionEventHandler {

    private SessionEventHandler() {
    }

    // -------------------------------------------------------------------------
    // Player disconnect
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        // Silent close — no force-close packet needed, the player is already leaving.
        ShopSessionManager.close(event.getEntity().getUUID());
    }

    // -------------------------------------------------------------------------
    // Player death
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ShopSessionManager.closeAndForceClose(player, "DEATH");
        }
    }

    // -------------------------------------------------------------------------
    // Player damage (configurable)
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public static void onPlayerHurt(LivingHurtEvent event) {
        if (Config.sessionCloseOnDamage && event.getEntity() instanceof ServerPlayer player) {
            ShopSessionManager.closeAndForceClose(player, "DAMAGE");
        }
    }

    // -------------------------------------------------------------------------
    // Distance auto-close (every 20 ticks)
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        // Process only at the END phase and only for server-side players.
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        // Sample once per second (every 20 ticks).
        if (player.tickCount % 20 != 0) return;

        ShopSessionManager.get(player.getUUID()).ifPresent(session -> {
            // Command-opened sessions have no block anchor → skip distance check.
            if (session.shopBlockPos() == null) return;

            int maxDist = Config.sessionMaxDistanceBlocks;
            if (maxDist <= 0) return; // Distance check disabled via config.

            double distSq = player.distanceToSqr(
                    session.shopBlockPos().getX() + 0.5,
                    session.shopBlockPos().getY() + 0.5,
                    session.shopBlockPos().getZ() + 0.5);

            if (distSq > (double) maxDist * maxDist) {
                ShopSessionManager.closeAndForceClose(player, "DISTANCE");
            }
        });
    }
}


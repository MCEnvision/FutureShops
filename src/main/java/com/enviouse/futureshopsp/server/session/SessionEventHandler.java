package com.enviouse.futureshopsp.server.session;

import com.enviouse.futureshopsp.Config;
import com.enviouse.futureshopsp.Futureshops;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * Forge gameplay-event handler that enforces shop-session lifecycle rules.
 *
 * <p>Auto-registered to the Forge event bus via {@link EventBusSubscriber}.
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
@EventBusSubscriber(modid = Futureshops.MODID, bus = EventBusSubscriber.Bus.GAME)
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
    public static void onPlayerHurt(LivingIncomingDamageEvent event) {
        if (Config.sessionCloseOnDamage && event.getEntity() instanceof ServerPlayer player) {
            ShopSessionManager.closeAndForceClose(player, "DAMAGE");
        }
    }

    // -------------------------------------------------------------------------
    // Distance auto-close (every 20 ticks)
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        // Cheapest guards first — skip entirely before any map lookups or Optional allocs.
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.tickCount % 20 != 0) return;
        int maxDist = Config.sessionMaxDistanceBlocks;
        if (maxDist <= 0) return;

        ShopSession session = ShopSessionManager.get(player.getUUID()).orElse(null);
        if (session == null) return;
        if (session.shopBlockPos() == null) return; // Command-opened sessions — no anchor.

        double distSq = player.distanceToSqr(
                session.shopBlockPos().getX() + 0.5,
                session.shopBlockPos().getY() + 0.5,
                session.shopBlockPos().getZ() + 0.5);
        if (distSq > (double) maxDist * maxDist) {
            ShopSessionManager.closeAndForceClose(player, "DISTANCE");
        }
    }
}


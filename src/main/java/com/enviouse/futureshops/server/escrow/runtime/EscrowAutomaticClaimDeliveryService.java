package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.Futureshops;
import com.enviouse.futureshops.config.EscrowConfig;
import com.enviouse.futureshops.server.escrow.claim.ClaimSavedData;
import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;

@Mod.EventBusSubscriber(
        modid = Futureshops.MODID,
        bus = Mod.EventBusSubscriber.Bus.FORGE
)
public final class EscrowAutomaticClaimDeliveryService {
    static final int DELIVERY_INTERVAL_TICKS = 10;
    private static final int FAILURE_LOG_INTERVAL_TICKS = 1_200;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<UUID, Integer> FAILURE_LOG_TICKS =
            new java.util.LinkedHashMap<>();

    private EscrowAutomaticClaimDeliveryService() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        MinecraftServer server = event.getServer();
        EscrowConfig.Settings settings = EscrowConfig.settings();
        if (event.phase != TickEvent.Phase.END
                || server == null
                || server.getTickCount() % DELIVERY_INTERVAL_TICKS != 0
                || !settings.automaticClaimDelivery()) {
            return;
        }
        EscrowRuntimeService runtime = EscrowRuntimeManager.getOrNull();
        if (runtime == null || !runtime.isReady()) {
            return;
        }
        List<ServerPlayer> players =
                server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            return;
        }
        ClaimSavedData claims = ClaimSavedData.get(server);
        int remaining = settings.claimDeliveryWorkPerTick();
        int exactItemOperationLimit =
                settings.exactItemDeliveryOperationsPerTick();
        int remainingExactItemOperations =
                ExactItemDeliveryTickBudget.remaining(
                        server, exactItemOperationLimit);
        int playerStart = Math.floorMod(
                server.getTickCount() / DELIVERY_INTERVAL_TICKS,
                players.size());
        for (int offset = 0;
             offset < players.size() && remaining > 0;
             offset++) {
            ServerPlayer player = players.get(
                    Math.floorMod(playerStart + offset,
                            players.size()));
            DeliveryWork work = deliverPlayerClaims(
                    player, runtime, claims, remaining,
                    remainingExactItemOperations,
                    exactItemOperationLimit,
                    server.getTickCount());
            remaining -= work.attempts();
            remainingExactItemOperations -= work.exactItemAttempts();
        }
    }

    static DeliveryWork deliverPlayerClaims(
            ServerPlayer player,
            EscrowRuntimeService runtime,
            ClaimSavedData claims,
            int limit,
            int exactItemLimit,
            int exactItemOperationLimit,
            int tickCount
    ) {
        if (limit <= 0) {
            return DeliveryWork.NONE;
        }
        int scanLimit = Math.min(1024,
                Math.max(64, Math.multiplyExact(
                        Math.min(limit, 256), 4)));
        List<EscrowClaim> pending = claims.pendingFor(
                player.getUUID(), scanLimit);
        if (pending.isEmpty()) {
            return DeliveryWork.NONE;
        }
        int start = Math.floorMod(
                tickCount / DELIVERY_INTERVAL_TICKS,
                pending.size());
        List<EscrowClaim> selected = selectDeliveries(
                pending, start, limit, exactItemLimit);
        int attempts = 0;
        int exactItemAttempts = 0;
        for (EscrowClaim claim : selected) {
            if (isExactItemClaim(claim)) {
                MinecraftServer server = player.getServer();
                if (server == null
                        || !ExactItemDeliveryTickBudget.tryAcquire(
                                server, exactItemOperationLimit)) {
                    continue;
                }
                exactItemAttempts++;
            }
            attempts++;
            try {
                if (EscrowMoneyClaimService.isMonetaryClaim(claim)) {
                    EscrowMoneyClaimService.collect(
                            player, claim.claimId(),
                            moneyRequestId(claim));
                } else {
                    runtime.collectExactItemClaim(
                            player, claim.claimId(), Instant.now());
                }
                FAILURE_LOG_TICKS.remove(claim.claimId());
            } catch (RuntimeException exception) {
                int nextLogTick = FAILURE_LOG_TICKS.getOrDefault(
                        claim.claimId(), Integer.MIN_VALUE);
                if (tickCount >= nextLogTick) {
                    LOGGER.error(
                            "Automatic delivery failed for escrow claim {}",
                            claim.claimId(), exception);
                    if (FAILURE_LOG_TICKS.size() >= 1_024) {
                        UUID first = FAILURE_LOG_TICKS.keySet()
                                .iterator().next();
                        FAILURE_LOG_TICKS.remove(first);
                    }
                    FAILURE_LOG_TICKS.put(claim.claimId(),
                            tickCount + FAILURE_LOG_INTERVAL_TICKS);
                }
            }
        }
        return new DeliveryWork(attempts, exactItemAttempts);
    }

    static List<EscrowClaim> selectDeliveries(
            List<EscrowClaim> pending,
            int start,
            int limit,
            int exactItemLimit
    ) {
        if (limit <= 0 || pending.isEmpty()) {
            return List.of();
        }
        int exactItemAttempts = 0;
        java.util.ArrayList<EscrowClaim> selected =
                new java.util.ArrayList<>(Math.min(limit, pending.size()));
        for (int offset = 0;
             offset < pending.size() && selected.size() < limit;
             offset++) {
            EscrowClaim claim = pending.get(
                    Math.floorMod(start + offset, pending.size()));
            if (!automaticallyDeliverable(claim)) {
                continue;
            }
            if (isExactItemClaim(claim)) {
                if (exactItemAttempts >= Math.max(0, exactItemLimit)) {
                    continue;
                }
                exactItemAttempts++;
            }
            selected.add(claim);
        }
        return List.copyOf(selected);
    }

    static boolean automaticallyDeliverable(EscrowClaim claim) {
        return claim != null
                && (claim.status() == ClaimStatus.PENDING
                || claim.status()
                == ClaimStatus.PARTIALLY_DELIVERED)
                && claim.remainingUnits() > 0L
                && (EscrowMoneyClaimService.isMonetaryClaim(claim)
                || ExactItemClaimDeliveryPlanner.supportedKind(
                        claim.kind()));
    }

    static boolean isExactItemClaim(EscrowClaim claim) {
        return claim != null
                && !EscrowMoneyClaimService.isMonetaryClaim(claim)
                && ExactItemClaimDeliveryPlanner.supportedKind(claim.kind());
    }

    static UUID moneyRequestId(EscrowClaim claim) {
        String identity = "futureshops automatic money claim "
                + claim.claimId() + " " + claim.remainingUnits();
        return UUID.nameUUIDFromBytes(
                identity.getBytes(StandardCharsets.UTF_8));
    }

    record DeliveryWork(int attempts, int exactItemAttempts) {
        static final DeliveryWork NONE = new DeliveryWork(0, 0);

        DeliveryWork {
            if (attempts < 0 || exactItemAttempts < 0
                    || exactItemAttempts > attempts) {
                throw new IllegalArgumentException(
                        "Automatic delivery work is invalid");
            }
        }
    }
}

package com.enviouse.futureshops.server.economy;

import com.enviouse.futureshops.network.packets.C2SAtmCollectCashPacket;
import com.enviouse.futureshops.network.packets.S2CAtmCollectCashResultPacket;
import com.enviouse.futureshops.network.packets.S2CAtmDataPacket;
import com.enviouse.futureshops.server.escrow.claim.ClaimSavedData;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.runtime.EscrowRuntimeManager;
import com.enviouse.futureshops.server.escrow.runtime.EscrowRuntimeService;
import com.enviouse.futureshops.server.escrow.runtime.EscrowRuntimeState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class AtmCashClaimCenter {
    private AtmCashClaimCenter() {
    }

    public static CashClaimSummary summary(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        List<EscrowClaim> pending = claims(player).pendingCashFor(
                player.getUUID());
        List<S2CAtmDataPacket.CashClaimSummary> visible =
                AtmCashClaimCollectionPolicy.selectForSummary(
                                pending, C2SAtmCollectCashPacket.MAX_CLAIMS)
                        .stream()
                        .map(claim -> new S2CAtmDataPacket.CashClaimSummary(
                                claim.claimId(), claim.kind().name(),
                                claim.billCount()))
                        .toList();
        return new CashClaimSummary(pending.size(), visible);
    }

    public static S2CAtmCollectCashResultPacket collect(
            ServerPlayer player,
            UUID requestId,
            List<UUID> suppliedClaimIds
    ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(requestId, "requestId");
        List<UUID> claimIds = List.copyOf(Objects.requireNonNull(
                suppliedClaimIds, "claimIds"));
        new C2SAtmCollectCashPacket(requestId, claimIds);
        if (!validRequestIdentity(player.getUUID(), requestId, claimIds)) {
            return conflict(player, requestId);
        }
        ClaimSavedData savedClaims;
        AtmCashClaimCollectionPolicy.Snapshot before;
        try {
            savedClaims = claims(player);
            before = inspect(savedClaims, player.getUUID(), claimIds);
        } catch (RuntimeException exception) {
            return conflict(player, requestId);
        }
        if (before.pendingClaimIds().isEmpty()) {
            return result(player, requestId,
                    AtmCashClaimCollectionPolicy.outcome(
                            before, false, false));
        }
        EscrowRuntimeService runtime = EscrowRuntimeManager.getOrNull();
        if (runtime == null || runtime.state() != EscrowRuntimeState.READY) {
            return result(player, requestId,
                    AtmCashClaimCollectionPolicy.outcome(
                            before, true, false));
        }
        boolean deliveryInterrupted = false;
        for (UUID claimId : before.pendingClaimIds()) {
            EscrowClaim pendingClaim = savedClaims.getClaim(claimId);
            try {
                runtime.deliverCashClaim(player, claimId,
                        attemptId(requestId, claimId),
                        pendingClaim.updatedAt());
            } catch (RuntimeException ignored) {
                deliveryInterrupted = true;
                break;
            }
            if (runtime.state() != EscrowRuntimeState.READY) {
                deliveryInterrupted = true;
                break;
            }
        }
        AtmCashClaimCollectionPolicy.Snapshot after;
        try {
            after = inspect(savedClaims, player.getUUID(), claimIds);
        } catch (RuntimeException exception) {
            return conflict(player, requestId);
        }
        boolean changed = after.completedBillCount()
                != before.completedBillCount()
                || !after.quarantinedClaimIds().equals(
                before.quarantinedClaimIds())
                || !after.pendingClaimIds().equals(
                before.pendingClaimIds());
        return result(player, requestId,
                AtmCashClaimCollectionPolicy.outcome(after,
                        deliveryInterrupted
                                || runtime.state()
                                != EscrowRuntimeState.READY,
                        changed));
    }

    static boolean validRequestIdentity(
            UUID playerId,
            UUID requestId,
            List<UUID> claimIds
    ) {
        return C2SAtmCollectCashPacket.matchesRequestId(
                requestId, playerId, claimIds);
    }

    private static UUID attemptId(UUID requestId, UUID claimId) {
        return UUID.nameUUIDFromBytes(("futureshops atm cash collect "
                + requestId + " " + claimId)
                .getBytes(StandardCharsets.UTF_8));
    }

    private static ClaimSavedData claims(ServerPlayer player) {
        MinecraftServer server = Objects.requireNonNull(
                player.getServer(), "server");
        return ClaimSavedData.get(server);
    }

    public static int pendingClaimCount(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        return claims(player).pendingCashFor(player.getUUID()).size();
    }

    private static AtmCashClaimCollectionPolicy.Snapshot inspect(
            ClaimSavedData savedClaims,
            UUID playerId,
            List<UUID> claimIds
    ) {
        List<EscrowClaim> exactClaims = new ArrayList<>(claimIds.size());
        for (UUID claimId : claimIds) {
            exactClaims.add(savedClaims.getClaim(claimId));
        }
        return AtmCashClaimCollectionPolicy.inspect(
                playerId, claimIds, exactClaims);
    }

    private static S2CAtmCollectCashResultPacket conflict(
            ServerPlayer player,
            UUID requestId
    ) {
        return new S2CAtmCollectCashResultPacket(requestId,
                "CONFLICT", false, false, 0,
                pendingClaimCount(player), List.of());
    }

    private static S2CAtmCollectCashResultPacket result(
            ServerPlayer player,
            UUID requestId,
            AtmCashClaimCollectionPolicy.Outcome outcome
    ) {
        return new S2CAtmCollectCashResultPacket(requestId,
                outcome.status(), outcome.retryable(), outcome.replayed(),
                outcome.completedBillCount(), pendingClaimCount(player),
                outcome.quarantinedClaimIds());
    }

    public record CashClaimSummary(
            int pendingClaimCount,
            List<S2CAtmDataPacket.CashClaimSummary> collectibleClaims
    ) {
        public CashClaimSummary {
            collectibleClaims = List.copyOf(Objects.requireNonNull(
                    collectibleClaims, "collectibleClaims"));
            if (pendingClaimCount < collectibleClaims.size()
                    || collectibleClaims.size()
                    > C2SAtmCollectCashPacket.MAX_CLAIMS) {
                throw new IllegalArgumentException(
                        "ATM cash claim center summary is invalid");
            }
        }
    }

}

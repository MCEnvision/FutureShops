package com.enviouse.futureshops.server.market;

import com.enviouse.futureshops.Config;
import com.enviouse.futureshops.client.market.MarketModule;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.C2SAtmCollectCashPacket;
import com.enviouse.futureshops.network.packets.C2SMarketClaimCollectionPacket;
import com.enviouse.futureshops.network.packets.S2CAtmCollectCashResultPacket;
import com.enviouse.futureshops.network.packets.S2CMarketClaimCollectionPacket;
import com.enviouse.futureshops.server.economy.AtmCashClaimCenter;
import com.enviouse.futureshops.server.escrow.claim.ClaimSavedData;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.runtime.EscrowMoneyClaimService;
import com.enviouse.futureshops.server.escrow.runtime.EscrowRuntimeManager;
import com.enviouse.futureshops.server.escrow.runtime.EscrowRuntimeService;
import com.enviouse.futureshops.server.escrow.runtime.ExactItemClaimCollectionResult;
import com.enviouse.futureshops.server.market.claim.MarketClaimCollectionCode;
import com.enviouse.futureshops.server.market.claim.MarketClaimCollectionCommand;
import com.enviouse.futureshops.server.market.claim.MarketClaimCollectionResult;
import com.enviouse.futureshops.server.market.claim.MarketClaimDeliveryOutcome;
import com.enviouse.futureshops.server.market.control.MarketControlModule;
import com.enviouse.futureshops.server.market.control.MarketControlSavedData;
import com.enviouse.futureshops.server.market.control.MarketModuleControl;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

public final class MarketClaimCollectionService {
    private static final Logger LOGGER = LogUtils.getLogger();

    private MarketClaimCollectionService() {
    }

    public static void collect(
            ServerPlayer player,
            C2SMarketClaimCollectionPacket packet
    ) {
        if (player == null || packet == null) {
            return;
        }
        MarketClaimCollectionCommand command = packet.command();
        MarketClaimCollectionResult result;
        try {
            MinecraftServer server = Objects.requireNonNull(
                    player.getServer(), "server");
            EscrowRuntimeService runtime =
                    EscrowRuntimeManager.getOrNull();
            MarketClaimCollectionProcessor processor =
                    new MarketClaimCollectionProcessor(
                            MarketModuleService.sessions(),
                            new LiveCollectionBackend(player,
                                    ClaimSavedData.get(server)));
            result = processor.process(player.getUUID(), command,
                    Math.max(0L, System.currentTimeMillis()),
                    new MarketClaimCollectionProcessor.AccessState(
                            configured(command.module()),
                            runtime != null && runtime.isReady(),
                            control(server, command.module())));
        } catch (RuntimeException exception) {
            LOGGER.error("FutureShops claim collection processing failed for player {} and request {}",
                    player.getUUID(), command.requestId(), exception);
            result = MarketClaimCollectionResult.failure(command,
                    MarketClaimCollectionCode.SERVER_ERROR);
        }
        try {
            ShopPackets.sendToPlayer(player,
                    new S2CMarketClaimCollectionPacket(result));
        } catch (RuntimeException exception) {
            LOGGER.error("FutureShops claim collection response failed for player {} and request {}",
                    player.getUUID(), command.requestId(), exception);
        }
    }

    static MarketClaimDeliveryOutcome moneyOutcome(
            EscrowMoneyClaimService.CollectionResult result,
            EscrowClaim current
    ) {
        Objects.requireNonNull(result, "result");
        long remaining = remaining(current);
        OptionalLong balance = result.status()
                == EscrowMoneyClaimService.Status.ESCROW_UNAVAILABLE
                ? OptionalLong.empty()
                : OptionalLong.of(result.resultingBalanceMinorUnits());
        return switch (result.status()) {
            case SUCCESS -> new MarketClaimDeliveryOutcome(
                    remaining == 0L
                            ? MarketClaimCollectionCode.COLLECTED
                            : MarketClaimCollectionCode
                            .PARTIALLY_COLLECTED,
                    result.collectedMinorUnits(), remaining, balance,
                    result.replayed());
            case ALREADY_COLLECTED -> remaining == 0L
                    ? new MarketClaimDeliveryOutcome(
                    MarketClaimCollectionCode.ALREADY_COLLECTED,
                    0L, 0L, balance, false)
                    : recovery(remaining);
            case WALLET_FULL -> failureWithBalance(
                    MarketClaimCollectionCode.WALLET_FULL,
                    remaining, balance);
            case CANCELLED -> failureWithBalance(
                    MarketClaimCollectionCode.CANCELLED,
                    remaining, balance);
            case CONFIG_CHANGED -> failureWithBalance(
                    MarketClaimCollectionCode.CONFIG_CHANGED,
                    remaining, balance);
            case REENTRANT_REQUEST -> failureWithBalance(
                    MarketClaimCollectionCode.REENTRANT_REQUEST,
                    remaining, balance);
            case REQUEST_CONFLICT -> failureWithBalance(
                    MarketClaimCollectionCode.REQUEST_CONFLICT,
                    remaining, balance);
            case RECOVERY_REQUIRED -> failureWithBalance(
                    MarketClaimCollectionCode.RECOVERY_REQUIRED,
                    remaining, balance);
            case ESCROW_UNAVAILABLE ->
                    MarketClaimDeliveryOutcome.failure(
                            MarketClaimCollectionCode
                            .ESCROW_UNAVAILABLE, remaining);
            case NOT_FOUND -> MarketClaimDeliveryOutcome.failure(
                    MarketClaimCollectionCode.NOT_FOUND, remaining);
        };
    }

    static MarketClaimDeliveryOutcome itemOutcome(
            ExactItemClaimCollectionResult result
    ) {
        Objects.requireNonNull(result, "result");
        return switch (result.status()) {
            case DELIVERED -> new MarketClaimDeliveryOutcome(
                    MarketClaimCollectionCode.COLLECTED,
                    result.deliveredUnits(), result.remainingUnits(),
                    OptionalLong.empty(), false);
            case PARTIALLY_DELIVERED ->
                    new MarketClaimDeliveryOutcome(
                            MarketClaimCollectionCode
                            .PARTIALLY_COLLECTED,
                            result.deliveredUnits(),
                            result.remainingUnits(),
                            OptionalLong.empty(), false);
            case REPLAYED -> new MarketClaimDeliveryOutcome(
                    result.remainingUnits() == 0L
                            ? MarketClaimCollectionCode.COLLECTED
                            : MarketClaimCollectionCode
                            .PARTIALLY_COLLECTED,
                    result.deliveredUnits(), result.remainingUnits(),
                    OptionalLong.empty(), true);
            case FULL_INVENTORY ->
                    MarketClaimDeliveryOutcome.failure(
                            MarketClaimCollectionCode.INVENTORY_FULL,
                            result.remainingUnits());
            case OFFLINE_PENDING ->
                    MarketClaimDeliveryOutcome.failure(
                            MarketClaimCollectionCode.RETRYABLE,
                            result.remainingUnits());
            case NOT_PENDING -> new MarketClaimDeliveryOutcome(
                    MarketClaimCollectionCode.ALREADY_COLLECTED,
                    0L, 0L, OptionalLong.empty(), false);
            case INVALID_PAYLOAD, RECOVERY_REQUIRED, MANUAL_REVIEW ->
                    MarketClaimDeliveryOutcome.failure(
                            MarketClaimCollectionCode.RECOVERY_REQUIRED,
                            result.remainingUnits());
        };
    }

    static MarketClaimDeliveryOutcome cashOutcome(
            S2CAtmCollectCashResultPacket result,
            EscrowClaim current
    ) {
        Objects.requireNonNull(result, "result");
        long remaining = remaining(current);
        return switch (result.status()) {
            case "DELIVERED" -> new MarketClaimDeliveryOutcome(
                    MarketClaimCollectionCode.COLLECTED,
                    result.deliveredBillCount(), 0L,
                    OptionalLong.empty(), result.replayed());
            case "PARTIALLY_DELIVERED" ->
                    new MarketClaimDeliveryOutcome(
                            MarketClaimCollectionCode
                            .PARTIALLY_COLLECTED,
                            result.deliveredBillCount(), remaining,
                            OptionalLong.empty(), result.replayed());
            case "MANUAL_REVIEW" ->
                    MarketClaimDeliveryOutcome.failure(
                            MarketClaimCollectionCode.RECOVERY_REQUIRED,
                            remaining);
            case "RATE_LIMITED", "RETRYABLE" ->
                    MarketClaimDeliveryOutcome.failure(
                            MarketClaimCollectionCode.RETRYABLE,
                            remaining);
            case "UNAVAILABLE" ->
                    MarketClaimDeliveryOutcome.failure(
                            MarketClaimCollectionCode
                            .ESCROW_UNAVAILABLE, remaining);
            case "CONFLICT" ->
                    MarketClaimDeliveryOutcome.failure(
                            MarketClaimCollectionCode.REQUEST_CONFLICT,
                            remaining);
            default -> MarketClaimDeliveryOutcome.failure(
                    MarketClaimCollectionCode.SERVER_ERROR, remaining);
        };
    }

    private static MarketClaimDeliveryOutcome failureWithBalance(
            MarketClaimCollectionCode code,
            long remaining,
            OptionalLong balance
    ) {
        return new MarketClaimDeliveryOutcome(code, 0L, remaining,
                balance, false);
    }

    private static MarketClaimDeliveryOutcome recovery(long remaining) {
        return MarketClaimDeliveryOutcome.failure(
                MarketClaimCollectionCode.RECOVERY_REQUIRED, remaining);
    }

    private static long remaining(EscrowClaim claim) {
        return claim == null ? 0L : claim.remainingUnits();
    }

    private static boolean configured(MarketModule module) {
        return switch (module) {
            case SHOP -> true;
            case BAZAAR -> Config.bazaarEnabled();
            case AUCTION_HOUSE -> Config.auctionHouseEnabled();
        };
    }

    private static Optional<MarketModuleControl> control(
            MinecraftServer server,
            MarketModule module
    ) {
        try {
            return Optional.of(MarketControlSavedData.get(server)
                    .snapshot().module(switch (module) {
                        case SHOP -> MarketControlModule.SHOP;
                        case BAZAAR -> MarketControlModule.BAZAAR;
                        case AUCTION_HOUSE ->
                                MarketControlModule.AUCTION_HOUSE;
                    }));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private record LiveCollectionBackend(
            ServerPlayer player,
            ClaimSavedData claims
    ) implements MarketClaimCollectionProcessor.CollectionBackend {
        private LiveCollectionBackend {
            Objects.requireNonNull(player, "player");
            Objects.requireNonNull(claims, "claims");
        }

        @Override
        public EscrowClaim claim(UUID claimId) {
            return claims.getClaim(claimId);
        }

        @Override
        public MarketClaimDeliveryOutcome collectMoney(
                UUID playerId,
                EscrowClaim claim,
                UUID requestId
        ) {
            EscrowMoneyClaimService.CollectionResult result =
                    EscrowMoneyClaimService.collect(player,
                            claim.claimId(), requestId);
            return moneyOutcome(result,
                    claims.getClaim(claim.claimId()));
        }

        @Override
        public MarketClaimDeliveryOutcome collectItem(
                UUID playerId,
                EscrowClaim claim,
                UUID requestId
        ) {
            EscrowRuntimeService runtime =
                    EscrowRuntimeManager.getOrNull();
            if (runtime == null || !runtime.isReady()) {
                return MarketClaimDeliveryOutcome.failure(
                        MarketClaimCollectionCode.ESCROW_UNAVAILABLE,
                        claim.remainingUnits());
            }
            ExactItemClaimCollectionResult result =
                    runtime.collectExactItemClaim(player,
                            claim.claimId(), Instant.now());
            return itemOutcome(result);
        }

        @Override
        public MarketClaimDeliveryOutcome collectCash(
                UUID playerId,
                EscrowClaim claim,
                UUID requestId
        ) {
            List<UUID> claimIds = List.of(claim.claimId());
            UUID internalRequestId =
                    C2SAtmCollectCashPacket.deriveRequestId(
                            playerId, claimIds);
            S2CAtmCollectCashResultPacket result =
                    AtmCashClaimCenter.collect(player,
                            internalRequestId, claimIds);
            return cashOutcome(result,
                    claims.getClaim(claim.claimId()));
        }
    }
}

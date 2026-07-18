package com.enviouse.futureshops.server.market;

import com.enviouse.futureshops.Config;
import com.enviouse.futureshops.client.market.MarketModule;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.C2SMarketPageQueryPacket;
import com.enviouse.futureshops.network.packets.S2CMarketPagePacket;
import com.enviouse.futureshops.server.escrow.claim.ClaimSavedData;
import com.enviouse.futureshops.server.escrow.claim.OpenClaimPage;
import com.enviouse.futureshops.server.escrow.runtime.EscrowRuntimeManager;
import com.enviouse.futureshops.server.escrow.runtime.EscrowRuntimeService;
import com.enviouse.futureshops.server.market.auction.AuctionHouseSavedData;
import com.enviouse.futureshops.server.market.bazaar.BazaarSavedData;
import com.enviouse.futureshops.server.market.control.MarketControlModule;
import com.enviouse.futureshops.server.market.control.MarketControlSavedData;
import com.enviouse.futureshops.server.market.control.MarketModuleControl;
import com.enviouse.futureshops.server.market.profile.MarketProfileSavedData;
import com.enviouse.futureshops.server.market.query.MarketPageQuery;
import com.enviouse.futureshops.server.market.query.MarketPageProjector;
import com.enviouse.futureshops.server.market.query.MarketPageSnapshot;
import com.enviouse.futureshops.server.market.session.MarketSessionDecision;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class MarketPageService {
    private MarketPageService() {
    }

    public static void query(
            ServerPlayer player,
            C2SMarketPageQueryPacket packet
    ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(packet, "packet");
        long now = Math.max(0L, System.currentTimeMillis());
        MarketModule module = MarketModule.fromId(packet.moduleId());
        MarketSessionDecision decision = MarketModuleService.sessions()
                .accept(player.getUUID(), packet.routeNonce(), module,
                        packet.view(), packet.requestId(),
                        packet.fingerprint(), now);
        if (decision != MarketSessionDecision.ACCEPT
                && decision != MarketSessionDecision.REPLAY) {
            send(player, decision.name(), packet.toQuery(now), null);
            return;
        }
        MinecraftServer server = Objects.requireNonNull(
                player.getServer(), "server");
        MarketPageQuery query = packet.toQuery(now);
        if (!"claims".equals(query.view())
                && !MarketPermissions.canUse(player, module)) {
            send(player, "PERMISSION_DENIED", query, null);
            return;
        }
        EscrowRuntimeService runtime = EscrowRuntimeManager.getOrNull();
        MarketModuleAccessPolicy.PageAccess access =
                MarketModuleAccessPolicy.pageAccess(module,
                        query.view(), configured(module),
                        runtime != null && runtime.isReady(),
                        control(server, module));
        if (!access.allowed()) {
            send(player, access.denialCode(), query, null);
            return;
        }
        MarketProfileSavedData.Snapshot profile =
                MarketProfileSavedData.get(server).snapshot(
                        player.getUUID());
        MarketPageSnapshot projected;
        if ("claims".equals(query.view())) {
            String sourcePrefix = module == MarketModule.BAZAAR
                    ? "bazaar." : "auction.";
            OpenClaimPage claims = ClaimSavedData.get(server)
                    .openPageFor(player.getUUID(), sourcePrefix,
                            query.pageIndex(), query.pageSize());
            projected = module == MarketModule.BAZAAR
                    ? MarketPageProjector.bazaar(query,
                    player.getUUID(),
                    BazaarSavedData.get(server).snapshot(), profile,
                    claims)
                    : MarketPageProjector.auction(query,
                    player.getUUID(), AuctionHouseSavedData.get(server)
                    .snapshot(), profile, claims);
        } else {
            projected = module == MarketModule.BAZAAR
                    ? MarketPageProjector.bazaar(query,
                    player.getUUID(),
                    BazaarSavedData.get(server).snapshot(), profile,
                    List.of())
                    : MarketPageProjector.auction(query,
                    player.getUUID(), AuctionHouseSavedData.get(server)
                    .snapshot(), profile, List.of());
        }
        MarketPageSnapshot page = MarketModuleAccessPolicy
                .applyPageActions(projected, access.availability());
        send(player, "OK", query, page);
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

    private static void send(
            ServerPlayer player,
            String resultCode,
            MarketPageQuery query,
            MarketPageSnapshot page
    ) {
        MarketPageSnapshot response = page == null
                ? new MarketPageSnapshot(query.requestId(),
                query.routeNonce(), query.module(), query.view(),
                query.pageIndex(), query.pageSize(), 0, 0, 0L,
                query.serverTimeMillis(), 0, 0L, 0L,
                List.of(), List.of()) : page;
        ShopPackets.sendToPlayer(player,
                new S2CMarketPagePacket(resultCode, response));
    }
}

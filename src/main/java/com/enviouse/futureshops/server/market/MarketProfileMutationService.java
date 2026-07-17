package com.enviouse.futureshops.server.market;

import com.enviouse.futureshops.Config;
import com.enviouse.futureshops.client.market.MarketModule;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.C2SMarketProfileMutationPacket;
import com.enviouse.futureshops.network.packets.S2CMarketProfileMutationPacket;
import com.enviouse.futureshops.server.escrow.runtime.EscrowRuntimeManager;
import com.enviouse.futureshops.server.escrow.runtime.EscrowRuntimeService;
import com.enviouse.futureshops.server.market.auction.AuctionHouseSavedData;
import com.enviouse.futureshops.server.market.bazaar.BazaarSavedData;
import com.enviouse.futureshops.server.market.control.MarketControlModule;
import com.enviouse.futureshops.server.market.control.MarketControlSavedData;
import com.enviouse.futureshops.server.market.control.MarketModuleControl;
import com.enviouse.futureshops.server.market.profile.MarketProfileMutationProcessor;
import com.enviouse.futureshops.server.market.profile.MarketProfileMutationResult;
import com.enviouse.futureshops.server.market.profile.MarketProfileSavedData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class MarketProfileMutationService {
    private static final MarketProfileMutationProcessor PROCESSOR =
            new MarketProfileMutationProcessor(
                    MarketModuleService.sessions());

    private MarketProfileMutationService() {
    }

    public static void mutate(
            ServerPlayer player,
            C2SMarketProfileMutationPacket packet
    ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(packet, "packet");
        MinecraftServer server = Objects.requireNonNull(
                player.getServer(), "server");
        MarketModule module = packet.command().module();
        EscrowRuntimeService runtime = EscrowRuntimeManager.getOrNull();
        MarketProfileMutationProcessor.AccessState access =
                new MarketProfileMutationProcessor.AccessState(
                        configured(module),
                        runtime != null && runtime.isReady(),
                        control(server, module));
        MarketProfileMutationResult result = PROCESSOR.process(
                player.getUUID(), packet.command(),
                Math.max(0L, System.currentTimeMillis()),
                MarketProfileSavedData.get(server),
                targets(server), access);
        ShopPackets.sendToPlayer(player,
                new S2CMarketProfileMutationPacket(result));
    }

    private static MarketProfileMutationProcessor.TargetCatalog targets(
            MinecraftServer server
    ) {
        return new MarketProfileMutationProcessor.TargetCatalog() {
            @Override
            public boolean auctionListingExists(UUID listingId) {
                return AuctionHouseSavedData.get(server).snapshot()
                        .listings().containsKey(listingId);
            }

            @Override
            public boolean bazaarProductExists(
                    MarketProfileSavedData.ProductKey product
            ) {
                return BazaarSavedData.get(server).snapshot().products()
                        .stream().anyMatch(value ->
                                value.productId().equals(
                                        product.productId())
                                        && value.version()
                                        == product.version());
            }
        };
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
}

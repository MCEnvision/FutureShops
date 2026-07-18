package com.enviouse.futureshops.server.market;

import com.enviouse.futureshops.Config;
import com.enviouse.futureshops.Futureshops;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.permission.PermissionAPI;
import net.minecraftforge.server.permission.events.PermissionGatherEvent;
import net.minecraftforge.server.permission.nodes.PermissionNode;
import net.minecraftforge.server.permission.nodes.PermissionTypes;

import java.util.List;

@Mod.EventBusSubscriber(modid = Futureshops.MODID,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MarketPermissions {
    public static final PermissionNode<Boolean> AUCTION_USE = publicNode(
            "auction.use");
    public static final PermissionNode<Boolean> AUCTION_CREATE = publicNode(
            "auction.create");
    public static final PermissionNode<Boolean> AUCTION_BID = publicNode(
            "auction.bid");
    public static final PermissionNode<Boolean> AUCTION_BUY = publicNode(
            "auction.buy");
    public static final PermissionNode<Boolean> AUCTION_CLAIM = claimNode(
            "auction.claim");
    public static final PermissionNode<Boolean> AUCTION_ADMIN = adminNode(
            "auction.admin");
    public static final PermissionNode<Boolean> BAZAAR_USE = publicNode(
            "bazaar.use");
    public static final PermissionNode<Boolean> BAZAAR_ORDER = publicNode(
            "bazaar.order");
    public static final PermissionNode<Boolean> BAZAAR_INSTANT = publicNode(
            "bazaar.instant");
    public static final PermissionNode<Boolean> BAZAAR_CLAIM = claimNode(
            "bazaar.claim");
    public static final PermissionNode<Boolean> BAZAAR_ADMIN = adminNode(
            "bazaar.admin");
    public static final PermissionNode<Boolean> ESCROW_CLAIM = claimNode(
            "escrow.claim");
    public static final PermissionNode<Boolean> ESCROW_ADMIN = adminNode(
            "escrow.admin");

    private MarketPermissions() {
    }

    @SubscribeEvent
    public static void register(PermissionGatherEvent.Nodes event) {
        event.addNodes(List.of(AUCTION_USE, AUCTION_CREATE, AUCTION_BID,
                AUCTION_BUY, AUCTION_CLAIM, AUCTION_ADMIN, BAZAAR_USE,
                BAZAAR_ORDER, BAZAAR_INSTANT, BAZAAR_CLAIM,
                BAZAAR_ADMIN, ESCROW_CLAIM, ESCROW_ADMIN));
    }

    public static boolean canUse(ServerPlayer player,
                                 com.enviouse.futureshops.client.market
                                         .MarketModule module) {
        return allowed(player, module
                == com.enviouse.futureshops.client.market.MarketModule
                .AUCTION_HOUSE ? AUCTION_USE : BAZAAR_USE,
                Config.permissionsMarketUseOpLevel);
    }

    public static boolean canAuctionCreate(ServerPlayer player) {
        return allowed(player, AUCTION_CREATE,
                Config.permissionsMarketUseOpLevel);
    }

    public static boolean canAuctionBid(ServerPlayer player) {
        return allowed(player, AUCTION_BID,
                Config.permissionsMarketUseOpLevel);
    }

    public static boolean canAuctionBuy(ServerPlayer player) {
        return allowed(player, AUCTION_BUY,
                Config.permissionsMarketUseOpLevel);
    }

    public static boolean canBazaarOrder(ServerPlayer player,
                                         boolean instant) {
        return allowed(player, instant ? BAZAAR_INSTANT : BAZAAR_ORDER,
                Config.permissionsMarketUseOpLevel);
    }

    public static boolean canAdmin(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return true;
        }
        return allowed(player, ESCROW_ADMIN,
                Config.permissionsMarketAdminOpLevel)
                || allowed(player, AUCTION_ADMIN,
                Config.permissionsMarketAdminOpLevel)
                || allowed(player, BAZAAR_ADMIN,
                Config.permissionsMarketAdminOpLevel);
    }

    public static boolean canEscrowAdmin(CommandSourceStack source) {
        return adminAllowed(source, ESCROW_ADMIN);
    }

    public static boolean canAuctionAdmin(CommandSourceStack source) {
        return adminAllowed(source, AUCTION_ADMIN);
    }

    public static boolean canBazaarAdmin(CommandSourceStack source) {
        return adminAllowed(source, BAZAAR_ADMIN);
    }

    private static boolean adminAllowed(CommandSourceStack source,
                                        PermissionNode<Boolean> node) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return true;
        }
        return allowed(player, node,
                Config.permissionsMarketAdminOpLevel);
    }

    static boolean allowed(ServerPlayer player,
                           PermissionNode<Boolean> node,
                           int fallbackLevel) {
        if (player == null) {
            return false;
        }
        try {
            if (PermissionAPI.getActivePermissionHandler() != null) {
                return PermissionAPI.getPermission(player, node);
            }
        } catch (RuntimeException ignored) {
        }
        return player.hasPermissions(fallbackLevel);
    }

    private static PermissionNode<Boolean> publicNode(String name) {
        return new PermissionNode<>(Futureshops.MODID, name,
                PermissionTypes.BOOLEAN,
                (player, playerId, context) -> player != null
                        && player.hasPermissions(
                        Config.permissionsMarketUseOpLevel));
    }

    private static PermissionNode<Boolean> claimNode(String name) {
        return new PermissionNode<>(Futureshops.MODID, name,
                PermissionTypes.BOOLEAN,
                (player, playerId, context) -> true);
    }

    private static PermissionNode<Boolean> adminNode(String name) {
        return new PermissionNode<>(Futureshops.MODID, name,
                PermissionTypes.BOOLEAN,
                (player, playerId, context) -> player != null
                        && player.hasPermissions(
                        Config.permissionsMarketAdminOpLevel));
    }
}

package com.enviouse.futureshops.server.market;

import com.enviouse.futureshops.Config;
import com.enviouse.futureshops.client.market.MarketModule;
import com.enviouse.futureshops.config.AuctionHouseConfig;
import com.enviouse.futureshops.config.BazaarConfig;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.S2COpenMarketModulePacket;
import com.enviouse.futureshops.server.escrow.runtime.EscrowRuntimeManager;
import com.enviouse.futureshops.server.escrow.runtime.EscrowRuntimeService;
import com.enviouse.futureshops.server.escrow.runtime.EscrowRuntimeState;
import com.enviouse.futureshops.server.market.control.MarketControlModule;
import com.enviouse.futureshops.server.market.control.MarketControlSavedData;
import com.enviouse.futureshops.server.market.control.MarketModuleControl;
import com.enviouse.futureshops.server.shop.ShopDataService;
import com.enviouse.futureshops.server.market.session.MarketServerSessionRegistry;
import com.enviouse.futureshops.server.session.ShopSessionManager;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class MarketModuleService {
    private static final MarketServerSessionRegistry SESSIONS =
            MarketServerSessionRegistry.defaults();
    private static final Set<String> AUCTION_VIEWS = Set.of(
            "browse", "create", "mine", "bids", "watched", "claims",
            "history", "listing_detail");
    private static final Set<String> BAZAAR_VIEWS = Set.of(
            "products", "buy_orders", "sell_orders", "orders",
            "portfolio", "watched", "claims", "history",
            "product_detail");

    private MarketModuleService() {
    }

    public static void open(ServerPlayer player, String moduleId,
                            String requestedView, UUID requestId) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(requestId, "requestId");
        MarketModule module = MarketModule.fromId(moduleId);
        if (module == MarketModule.SHOP) {
            ShopDataService.openShop(player, "default");
            return;
        }
        ShopSessionManager.close(player.getUUID());
        boolean configured = module == MarketModule.BAZAAR
                ? Config.bazaarEnabled() : Config.auctionHouseEnabled();
        String view = normalizeView(module, requestedView);
        String displayName;
        String accent;
        if (module == MarketModule.BAZAAR) {
            BazaarConfig.Branding branding =
                    BazaarConfig.settings().branding();
            displayName = branding.displayName();
            accent = branding.accentColor();
        } else {
            AuctionHouseConfig.Branding branding =
                    AuctionHouseConfig.settings().branding();
            displayName = branding.displayName();
            accent = branding.accentColor();
        }
        EscrowRuntimeService runtime = EscrowRuntimeManager.getOrNull();
        boolean escrowReady = runtime != null
                && runtime.state() == EscrowRuntimeState.READY;
        Optional<MarketModuleControl> control = control(player, module);
        MarketModuleAccessPolicy.PageAccess access =
                MarketModuleAccessPolicy.pageAccess(module, view,
                        configured, escrowReady, control);
        if (!access.allowed()) {
            view = MarketModuleAccessPolicy.preferredView(module,
                    access.availability());
            MarketModuleAccessPolicy.PageAccess fallback =
                    MarketModuleAccessPolicy.pageAccess(module, view,
                            configured, escrowReady, control);
            if (!fallback.allowed()) {
                view = "claims";
                fallback = MarketModuleAccessPolicy.pageAccess(module,
                        view, configured, escrowReady, control);
            }
            access = fallback;
        }
        boolean enabled = access.availability()
                .allowsNewValueOperations();
        UUID routeNonce = openSession(player.getUUID(), module, view);
        ShopPackets.sendToPlayer(player, new S2COpenMarketModulePacket(
                requestId, routeNonce, module.id(), view, displayName,
                accent, enabled,
                escrowReady, Config.showModuleNavigation(),
                Config.bazaarEnabled(), Config.auctionHouseEnabled()));
    }

    private static Optional<MarketModuleControl> control(
            ServerPlayer player,
            MarketModule module
    ) {
        try {
            return Optional.of(MarketControlSavedData.get(
                    Objects.requireNonNull(player.getServer(),
                            "server")).snapshot().module(
                    switch (module) {
                        case SHOP -> MarketControlModule.SHOP;
                        case BAZAAR -> MarketControlModule.BAZAAR;
                        case AUCTION_HOUSE ->
                                MarketControlModule.AUCTION_HOUSE;
                    }));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    static MarketServerSessionRegistry sessions() {
        return SESSIONS;
    }

    public static void close(UUID playerId) {
        SESSIONS.close(Objects.requireNonNull(playerId, "playerId"));
    }

    public static boolean close(UUID playerId, UUID routeNonce) {
        return SESSIONS.close(Objects.requireNonNull(playerId,
                "playerId"), Objects.requireNonNull(routeNonce,
                "routeNonce"));
    }

    public static void clearSessions() {
        SESSIONS.clear();
    }

    private static UUID openSession(
            UUID playerId,
            MarketModule module,
            String view
    ) {
        long now = System.currentTimeMillis();
        for (int attempt = 0; attempt < 16; attempt++) {
            UUID route = UUID.randomUUID();
            try {
                SESSIONS.open(playerId, module, view, route, now);
                return route;
            } catch (IllegalArgumentException exception) {
                if (attempt == 15) {
                    throw exception;
                }
            }
        }
        throw new IllegalStateException(
                "Market route generation failed");
    }

    static String normalizeView(MarketModule module, String view) {
        String normalized = view == null ? ""
                : view.strip().toLowerCase(Locale.ROOT);
        Set<String> allowed = module == MarketModule.BAZAAR
                ? BAZAAR_VIEWS : AUCTION_VIEWS;
        return allowed.contains(normalized) ? normalized : module.rootView();
    }
}

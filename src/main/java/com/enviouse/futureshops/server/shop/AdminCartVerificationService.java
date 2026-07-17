package com.enviouse.futureshops.server.shop;

import com.enviouse.futureshops.catalog.ItemDef;
import com.enviouse.futureshops.catalog.ShopCatalog;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.C2SVerifyAdminCartPacket;
import com.enviouse.futureshops.network.packets.S2CVerifyCartResponsePacket;
import com.enviouse.futureshops.network.packets.S2CVerifyCartResponsePacket.CartWarning;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-authoritative cart verification for admin shop carts.
 * Checks each cart entry against the current catalog state (item exists, price, stock).
 */
public final class AdminCartVerificationService {
    private AdminCartVerificationService() {}

    public static void verify(ServerPlayer player, String shopId, List<C2SVerifyAdminCartPacket.AdminCartLine> lines) {
        List<CartWarning> warnings = new ArrayList<>();
        // Warnings carry a stable code + args (never English) so the client localizes them.
        final String SEP = S2CVerifyCartResponsePacket.WARNING_ARG_SEP;

        for (int i = 0; i < lines.size(); i++) {
            C2SVerifyAdminCartPacket.AdminCartLine line = lines.get(i);

            // Check listing still exists in catalog (resolution key, not registry id)
            var itemOpt = ShopCatalog.getItem(shopId, line.listingId());
            if (itemOpt.isEmpty()) {
                warnings.add(new CartWarning(i, "ITEM_REMOVED", ""));
                continue;
            }

            ItemDef item = itemOpt.get();

            // Check price changed — use effective buy price (includes promos)
            long currentPrice = ShopCatalog.getEffectiveBuyPrice(shopId, line.listingId());
            if (line.expectedPriceMinor() > 0 && currentPrice != line.expectedPriceMinor()) {
                warnings.add(new CartWarning(i, "PRICE_CHANGED", ""));
            }

            // Check NBT payload changed (variant-swap protection, mirrors the
            // player-shop cart): compare the tag the buyer saw at add-to-cart time
            // against the listing's CURRENT nbt — /shopadmin items edit can re-stamp
            // a listingId's tag while it sits in a cart.
            String currentNbtJson = item.nbtJson() == null ? "" : item.nbtJson();
            if (!CartVerificationService.nbtJsonEquals(line.expectedNbtJson(), currentNbtJson)) {
                warnings.add(new CartWarning(i, "NBT_CHANGED", ""));
            }

            // Check stock
            if (!item.isUnlimited()) {
                int stock = ShopCatalog.getCurrentStock(shopId, line.listingId());
                if (stock >= 0 && stock < line.quantity()) {
                    warnings.add(new CartWarning(i, "LOW_STOCK",
                            stock + SEP + line.quantity()));
                }
            }
        }

        boolean allOk = warnings.isEmpty();
        ShopPackets.sendToPlayer(player, new S2CVerifyCartResponsePacket(allOk, warnings));
    }
}


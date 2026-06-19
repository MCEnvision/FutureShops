package com.enviouse.futureshopsp.server.shop;

import com.enviouse.futureshopsp.catalog.ItemDef;
import com.enviouse.futureshopsp.catalog.ShopCatalog;
import com.enviouse.futureshopsp.network.ShopPackets;
import com.enviouse.futureshopsp.network.packets.C2SVerifyAdminCartPacket;
import com.enviouse.futureshopsp.network.packets.S2CVerifyCartResponsePacket;
import com.enviouse.futureshopsp.network.packets.S2CVerifyCartResponsePacket.CartWarning;
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

        for (int i = 0; i < lines.size(); i++) {
            C2SVerifyAdminCartPacket.AdminCartLine line = lines.get(i);

            // Check listing still exists in catalog (resolution key, not registry id)
            var itemOpt = ShopCatalog.getItem(shopId, line.listingId());
            if (itemOpt.isEmpty()) {
                warnings.add(new CartWarning(i, "ITEM_REMOVED", "Item no longer available in shop"));
                continue;
            }

            ItemDef item = itemOpt.get();

            // Check price changed — use effective buy price (includes promos)
            long currentPrice = ShopCatalog.getEffectiveBuyPrice(shopId, line.listingId());
            if (line.expectedPriceMinor() > 0 && currentPrice != line.expectedPriceMinor()) {
                warnings.add(new CartWarning(i, "PRICE_CHANGED", "Price changed"));
            }

            // Check stock
            if (!item.isUnlimited()) {
                int stock = ShopCatalog.getCurrentStock(shopId, line.listingId());
                if (stock >= 0 && stock < line.quantity()) {
                    warnings.add(new CartWarning(i, "LOW_STOCK",
                            "Only " + stock + " available (you want " + line.quantity() + ")"));
                }
            }
        }

        boolean allOk = warnings.isEmpty();
        ShopPackets.sendToPlayer(player, new S2CVerifyCartResponsePacket(allOk, warnings));
    }
}


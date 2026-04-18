package com.enviouse.futureshops.server.shop;

import com.enviouse.futureshops.block.ShopBlockEntity;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.C2SVerifyCartPacket;
import com.enviouse.futureshops.network.packets.S2CVerifyCartResponsePacket;
import com.enviouse.futureshops.network.packets.S2CVerifyCartResponsePacket.CartWarning;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-authoritative cart verification service.
 * Checks each cart entry against current listing state and reports changes.
 * This prevents NBT bait-and-switch, price changes, and removed listings.
 */
public final class CartVerificationService {
    private CartVerificationService() {}

    public static void verify(ServerPlayer player, List<C2SVerifyCartPacket.CartLine> lines) {
        Level level = player.level();
        List<CartWarning> warnings = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            C2SVerifyCartPacket.CartLine line = lines.get(i);

            BlockEntity be = level.getBlockEntity(line.shopPos());
            if (!(be instanceof ShopBlockEntity shop)) {
                warnings.add(new CartWarning(i, "SHOP_REMOVED", "Shop block no longer exists"));
                continue;
            }

            ShopBlockEntity.Listing listing = shop.getListing(line.listingIndex());
            if (listing == null || listing.itemId().isBlank()) {
                warnings.add(new CartWarning(i, "LISTING_REMOVED", "Listing was removed"));
                continue;
            }

            // Check item changed
            if (!listing.itemId().equals(line.expectedItemId())) {
                warnings.add(new CartWarning(i, "ITEM_CHANGED",
                        "Item changed from " + line.expectedItemId() + " to " + listing.itemId()));
                continue;
            }

            // Check NBT awareness changed (bait-and-switch protection)
            if (line.expectedNbtAware() && !listing.nbtAware()) {
                warnings.add(new CartWarning(i, "NBT_DISABLED",
                        "NBT matching was disabled — you may not receive the exact item variant"));
            }

            // Check trade mode changed
            if (!line.expectedTradeMode().isBlank()
                    && !listing.tradeMode().name().equalsIgnoreCase(line.expectedTradeMode())) {
                warnings.add(new CartWarning(i, "MODE_CHANGED",
                        "Trade mode changed from " + line.expectedTradeMode() + " to " + listing.tradeMode().name()));
            }

            // Check price changed
            long currentPrice = listing.moneyPriceMinor();
            if (listing.promo().active()) {
                currentPrice = listing.promo().applyUnitPrice(currentPrice);
            }
            if (line.expectedPriceMinor() > 0 && currentPrice != line.expectedPriceMinor()) {
                warnings.add(new CartWarning(i, "PRICE_CHANGED",
                        "Price changed"));
            }

            // Check stock
            int stock = PlayerShopBlockService.countStock(level, shop, line.shopPos(), listing);
            if (stock < line.quantity()) {
                warnings.add(new CartWarning(i, "LOW_STOCK",
                        "Only " + stock + " available (you want " + line.quantity() + ")"));
            }
        }

        boolean allOk = warnings.isEmpty();
        ShopPackets.sendToPlayer(player, new S2CVerifyCartResponsePacket(allOk, warnings));
    }
}


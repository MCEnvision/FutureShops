package com.enviouse.futureshops.server.shop;

import com.enviouse.futureshops.block.ShopBlockEntity;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.C2SVerifyCartPacket;
import com.enviouse.futureshops.network.packets.S2CVerifyCartResponsePacket;
import com.enviouse.futureshops.network.packets.S2CVerifyCartResponsePacket.CartWarning;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.nbt.TagParser;
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
        // Warnings carry a stable code + args (never English) so the CLIENT localizes each one
        // in the viewer's own language (see ShopUiUtil.cartWarningComponent).
        final String SEP = S2CVerifyCartResponsePacket.WARNING_ARG_SEP;

        for (int i = 0; i < lines.size(); i++) {
            C2SVerifyCartPacket.CartLine line = lines.get(i);

            BlockEntity be = level.getBlockEntity(line.shopPos());
            if (!(be instanceof ShopBlockEntity shop)) {
                warnings.add(new CartWarning(i, "SHOP_REMOVED", ""));
                continue;
            }

            ShopBlockEntity.Listing listing = shop.getListing(line.listingIndex());
            if (listing == null || listing.itemId().isBlank()) {
                warnings.add(new CartWarning(i, "LISTING_REMOVED", ""));
                continue;
            }

            // A listing the owner concealed (hidden) or made display-only (showcase) AFTER it was
            // added to the cart is no longer purchasable — flag it so checkout doesn't silently
            // fail line-by-line at buy() (which rejects hidden/showcase for visitors regardless).
            if (listing.hidden() || listing.showcase()) {
                warnings.add(new CartWarning(i, "LISTING_REMOVED", ""));
                continue;
            }

            // Check item changed
            if (!listing.itemId().equals(line.expectedItemId())) {
                warnings.add(new CartWarning(i, "ITEM_CHANGED",
                        line.expectedItemId() + SEP + listing.itemId()));
                continue;
            }

            // Check NBT awareness changed (bait-and-switch protection)
            if (line.expectedNbtAware() && !listing.nbtAware()) {
                warnings.add(new CartWarning(i, "NBT_DISABLED", ""));
            }

            // Check NBT payload changed (variant-swap protection): compare the tag the
            // buyer saw at add-to-cart time against the listing's CURRENT tag. Parsed
            // via TagParser so key-order differences don't false-positive; blank means
            // "no NBT" on both sides. Localized on the CLIENT by warning code.
            String currentNbtJson = listing.nbtTag() != null ? listing.nbtTag().toString() : "";
            if (!nbtJsonEquals(line.expectedNbtJson(), currentNbtJson)) {
                warnings.add(new CartWarning(i, "NBT_CHANGED", ""));
            }

            // Check trade mode changed
            if (!line.expectedTradeMode().isBlank()
                    && !listing.tradeMode().name().equalsIgnoreCase(line.expectedTradeMode())) {
                warnings.add(new CartWarning(i, "MODE_CHANGED",
                        line.expectedTradeMode() + SEP + listing.tradeMode().name()));
            }

            // Check price changed
            long currentPrice = listing.moneyPriceMinor();
            if (listing.promo().active()) {
                currentPrice = listing.promo().applyUnitPrice(currentPrice);
            }
            if (line.expectedPriceMinor() > 0 && currentPrice != line.expectedPriceMinor()) {
                warnings.add(new CartWarning(i, "PRICE_CHANGED", ""));
            }

            // Check stock
            int stock = PlayerShopBlockService.countStock(level, shop, line.shopPos(), listing);
            if (stock < line.quantity()) {
                warnings.add(new CartWarning(i, "LOW_STOCK",
                        stock + SEP + line.quantity()));
            }
        }

        boolean allOk = warnings.isEmpty();
        ShopPackets.sendToPlayer(player, new S2CVerifyCartResponsePacket(allOk, warnings));
    }

    /**
     * Compares two SNBT strings semantically. Blank/null on both sides means "no NBT"
     * and is equal; blank vs. non-blank is a mismatch. Both sides are parsed with
     * {@link TagParser} so key-order differences in the serialized form don't
     * false-positive; if either side fails to parse, falls back to a raw string compare.
     */
    static boolean nbtJsonEquals(String expected, String current) {
        String a = expected == null ? "" : expected.trim();
        String b = current == null ? "" : current.trim();
        if (a.isEmpty() && b.isEmpty()) return true;
        if (a.isEmpty() || b.isEmpty()) return false;
        try {
            return TagParser.parseTag(a).equals(TagParser.parseTag(b));
        } catch (CommandSyntaxException e) {
            return a.equals(b);
        }
    }
}


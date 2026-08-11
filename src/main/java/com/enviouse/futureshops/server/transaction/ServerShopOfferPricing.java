package com.enviouse.futureshops.server.transaction;

import com.enviouse.futureshops.catalog.ItemDef;
import com.enviouse.futureshops.catalog.ShopCatalog;
import com.enviouse.futureshops.catalog.offer.AcquireOfferOption;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferListing;
import net.minecraft.server.MinecraftServer;

import java.util.Objects;

public final class ServerShopOfferPricing {
    private ServerShopOfferPricing() {
    }

    public static long moneyTotal(
            MinecraftServer server,
            String shopId,
            ServerShopOfferListing listing,
            AcquireOfferOption option,
            int quantity
    ) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(listing, "listing");
        Objects.requireNonNull(option, "option");
        if (quantity <= 0 || !option.moneyCostPresent()) {
            return 0L;
        }
        ItemDef projection = ShopCatalog.getItem(
                shopId, listing.listingId()).orElse(null);
        if (projection != null
                && projection.buyPriceMinorUnits()
                == option.moneyCostMinorUnits()) {
            return ShopCatalog.calculateLineCost(
                    shopId, listing.listingId(), quantity, server);
        }
        return Math.multiplyExact(
                option.moneyCostMinorUnits(), quantity);
    }
}

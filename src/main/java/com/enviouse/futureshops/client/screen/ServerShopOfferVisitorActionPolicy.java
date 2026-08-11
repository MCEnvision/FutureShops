package com.enviouse.futureshops.client.screen;

import com.enviouse.futureshops.catalog.offer.ServerShopOfferListing;

import java.util.Objects;

public final class ServerShopOfferVisitorActionPolicy {
    private ServerShopOfferVisitorActionPolicy() {
    }

    public static boolean showsAcquireCart(
            ServerShopOfferListing listing
    ) {
        return !Objects.requireNonNull(
                listing, "listing").acquireOptions().isEmpty();
    }
}

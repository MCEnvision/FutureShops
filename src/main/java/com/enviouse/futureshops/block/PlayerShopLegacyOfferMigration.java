package com.enviouse.futureshops.block;

import com.enviouse.futureshops.catalog.offer.AcquireOfferOption;
import com.enviouse.futureshops.catalog.offer.OfferComponentNormalizer;
import com.enviouse.futureshops.catalog.offer.OfferItemComponent;
import com.enviouse.futureshops.catalog.offer.OfferLimitPolicy;
import com.enviouse.futureshops.catalog.offer.OfferSchedule;
import com.enviouse.futureshops.catalog.offer.OfferStockPolicy;
import com.enviouse.futureshops.catalog.offer.SellOfferOption;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferListing;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferRevision;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferValidator;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class PlayerShopLegacyOfferMigration {
    private PlayerShopLegacyOfferMigration() {
    }

    static Optional<ServerShopOfferListing> compile(
            ShopBlockEntity.Listing listing
    ) {
        if (listing == null || listing.itemId().isBlank()
                || listing.baseQuantity() <= 0) {
            return Optional.empty();
        }
        List<OfferItemComponent> outputs = outputs(listing);
        List<AcquireOfferOption> acquire = new ArrayList<>();
        if (listing.allowsSell()) {
            switch (listing.tradeMode()) {
                case MONEY -> acquire.add(money(listing));
                case BARTER -> acquire.add(barter(listing));
                case BOTH -> {
                    acquire.add(money(listing));
                    acquire.add(barter(listing));
                }
                case MONEY_AND_BARTER ->
                        acquire.add(compound(listing));
            }
        }
        List<SellOfferOption> sell = new ArrayList<>();
        if (listing.allowsBuy()
                && listing.buybackPriceMinor() > 0L) {
            sell.add(new SellOfferOption(
                    "sell_money", "Sell to Shop",
                    List.of(primaryInput(listing)),
                    listing.buybackPriceMinor(),
                    listing.buybackCap(),
                    OfferLimitPolicy.defaults(),
                    OfferSchedule.always(), ""));
        }
        ServerShopOfferListing unversioned =
                new ServerShopOfferListing(
                        listing.listingId(), 0L,
                        listing.itemId(),
                        listing.listingDescription(),
                        listing.department().isBlank()
                                ? "all" : listing.department(),
                        listing.itemId(), nbt(listing.nbtTag()),
                        !listing.hidden() && !listing.showcase(),
                        0L, "", outputs, acquire, sell,
                        OfferStockPolicy.unlimited(),
                        OfferLimitPolicy.defaults(),
                        OfferSchedule.always(), List.of());
        ServerShopOfferListing versioned =
                unversioned.withRevision(
                        ServerShopOfferRevision.compute(unversioned));
        if (!ServerShopOfferValidator.validate(versioned).valid()) {
            return Optional.empty();
        }
        PlayerShopOfferPersistenceCodec.encode(versioned);
        return Optional.of(versioned);
    }

    private static List<OfferItemComponent> outputs(
            ShopBlockEntity.Listing listing
    ) {
        if (listing.bundleOutputs().isEmpty()) {
            return List.of(primaryInput(listing));
        }
        List<OfferItemComponent> outputs = new ArrayList<>();
        for (int index = 0;
             index < listing.bundleOutputs().size(); index++) {
            ShopBlockEntity.BundleEntry entry =
                    listing.bundleOutputs().get(index);
            outputs.add(new OfferItemComponent(
                    "output_" + (index + 1),
                    entry.itemId(), entry.count(),
                    nbt(entry.nbtTag())));
        }
        return OfferComponentNormalizer.normalize(outputs);
    }

    private static OfferItemComponent primaryInput(
            ShopBlockEntity.Listing listing
    ) {
        return new OfferItemComponent(
                "primary", listing.itemId(),
                listing.baseQuantity(),
                listing.nbtAware() ? nbt(listing.nbtTag()) : "");
    }

    private static AcquireOfferOption money(
            ShopBlockEntity.Listing listing
    ) {
        return new AcquireOfferOption(
                "money", "Money", false, true,
                listing.moneyPriceMinor(), List.of(), 1,
                OfferLimitPolicy.defaults(),
                OfferSchedule.always(), "");
    }

    private static AcquireOfferOption barter(
            ShopBlockEntity.Listing listing
    ) {
        return new AcquireOfferOption(
                "barter", "Barter", false, false, 0L,
                List.of(barterInput(listing)), 1,
                OfferLimitPolicy.defaults(),
                OfferSchedule.always(), "");
    }

    private static AcquireOfferOption compound(
            ShopBlockEntity.Listing listing
    ) {
        return new AcquireOfferOption(
                "money_and_barter", "Money and Barter",
                false, true, listing.moneyPriceMinor(),
                List.of(barterInput(listing)), 1,
                OfferLimitPolicy.defaults(),
                OfferSchedule.always(), "");
    }

    private static OfferItemComponent barterInput(
            ShopBlockEntity.Listing listing
    ) {
        return new OfferItemComponent(
                "payment_1", listing.barterItemId(),
                listing.barterItemCount(),
                listing.barterNbtAware()
                        ? nbt(listing.barterNbtTag()) : "");
    }

    private static String nbt(CompoundTag tag) {
        return tag == null ? "" : tag.toString();
    }
}

package com.enviouse.futureshops.server.transaction;

import com.enviouse.futureshops.catalog.offer.AcquireOfferOption;
import com.enviouse.futureshops.catalog.offer.OfferItemComponent;
import com.enviouse.futureshops.catalog.offer.SellOfferOption;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferListing;
import com.enviouse.futureshops.event.BarterTradeEvent;
import com.enviouse.futureshops.event.ShopTransactionEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;

import java.util.List;
import java.util.Objects;

public final class NormalizedOfferTransactionEvents {
    private NormalizedOfferTransactionEvents() {
    }

    public static Decision fireAcquirePre(
            ServerPlayer player,
            String shopId,
            ServerShopOfferListing listing,
            AcquireOfferOption option,
            int quantity,
            long quotedMoneyMinorUnits
    ) {
        Objects.requireNonNull(player, "player");
        requireListing(listing, quantity);
        Objects.requireNonNull(option, "option");
        List<BarterTradeEvent.IngredientEntry> ingredients;
        try {
            ingredients = option.hasItemCosts()
                    ? barterEntries(option.itemCosts(), quantity)
                    : List.of();
        } catch (ArithmeticException | IllegalArgumentException exception) {
            return Decision.invalid();
        }
        ShopTransactionEvent.Pre transaction =
                new ShopTransactionEvent.Pre(
                        player, shopId,
                        listing.outputs().get(0).itemId(),
                        quantity, acquireType(option),
                        quotedMoneyMinorUnits);
        if (MinecraftForge.EVENT_BUS.post(transaction)) {
            return Decision.cancelled();
        }
        long authorized;
        try {
            authorized = requireAuthorizedMoney(
                    option.free(), option.moneyCostPresent(),
                    quotedMoneyMinorUnits,
                    transaction.getPriceMinor());
        } catch (IllegalArgumentException exception) {
            return Decision.invalid();
        }
        if (option.hasItemCosts()) {
            BarterTradeEvent.Pre barter = new BarterTradeEvent.Pre(
                    player.getUUID(), shopId,
                    listing.listingId() + "." + option.optionId(),
                    listing.outputs().get(0).itemId(),
                    quantity,
                    ingredients);
            if (MinecraftForge.EVENT_BUS.post(barter)) {
                return Decision.cancelled();
            }
        }
        return Decision.accepted(authorized);
    }

    public static Decision fireSellPre(
            ServerPlayer player,
            String shopId,
            ServerShopOfferListing listing,
            SellOfferOption option,
            int quantity,
            long quotedMoneyMinorUnits
    ) {
        Objects.requireNonNull(player, "player");
        requireListing(listing, quantity);
        Objects.requireNonNull(option, "option");
        ShopTransactionEvent.Pre transaction =
                new ShopTransactionEvent.Pre(
                        player, shopId,
                        option.itemInputs().get(0).itemId(),
                        quantity, "SELL_TO_SHOP",
                        quotedMoneyMinorUnits);
        if (MinecraftForge.EVENT_BUS.post(transaction)) {
            return Decision.cancelled();
        }
        try {
            return Decision.accepted(requireAuthorizedMoney(
                    false, true, quotedMoneyMinorUnits,
                    transaction.getPriceMinor()));
        } catch (IllegalArgumentException exception) {
            return Decision.invalid();
        }
    }

    public static void fireAcquirePost(
            ServerPlayer player,
            String shopId,
            ServerShopOfferListing listing,
            AcquireOfferOption option,
            int quantity,
            long committedMoneyMinorUnits,
            long resultingBalanceMinorUnits
    ) {
        Objects.requireNonNull(player, "player");
        requireListing(listing, quantity);
        Objects.requireNonNull(option, "option");
        requireAuthorizedMoney(
                option.free(), option.moneyCostPresent(),
                committedMoneyMinorUnits, committedMoneyMinorUnits);
        MinecraftForge.EVENT_BUS.post(
                new ShopTransactionEvent.Post(
                        player.getUUID(), shopId,
                        listing.outputs().get(0).itemId(),
                        quantity, acquireType(option),
                        committedMoneyMinorUnits,
                        resultingBalanceMinorUnits));
        if (option.hasItemCosts()) {
            MinecraftForge.EVENT_BUS.post(
                    new BarterTradeEvent.Post(
                            player.getUUID(), shopId,
                            listing.listingId() + "."
                                    + option.optionId(),
                            listing.outputs().get(0).itemId(),
                            quantity,
                            barterEntries(option.itemCosts(), quantity)));
        }
    }

    public static void fireSellPost(
            ServerPlayer player,
            String shopId,
            ServerShopOfferListing listing,
            SellOfferOption option,
            int quantity,
            long committedMoneyMinorUnits,
            long resultingBalanceMinorUnits
    ) {
        Objects.requireNonNull(player, "player");
        requireListing(listing, quantity);
        Objects.requireNonNull(option, "option");
        requireAuthorizedMoney(
                false, true, committedMoneyMinorUnits,
                committedMoneyMinorUnits);
        MinecraftForge.EVENT_BUS.post(
                new ShopTransactionEvent.Post(
                        player.getUUID(), shopId,
                        option.itemInputs().get(0).itemId(),
                        quantity, "SELL_TO_SHOP",
                        committedMoneyMinorUnits,
                        resultingBalanceMinorUnits));
    }

    static long requireAuthorizedMoney(
            boolean explicitlyFree,
            boolean moneyPresent,
            long quotedMoneyMinorUnits,
            long authorizedMoneyMinorUnits
    ) {
        if (quotedMoneyMinorUnits < 0L
                || authorizedMoneyMinorUnits < 0L
                || explicitlyFree
                && (moneyPresent || quotedMoneyMinorUnits != 0L
                || authorizedMoneyMinorUnits != 0L)
                || moneyPresent
                && (quotedMoneyMinorUnits <= 0L
                || authorizedMoneyMinorUnits <= 0L)
                || !explicitlyFree && !moneyPresent
                && (quotedMoneyMinorUnits != 0L
                || authorizedMoneyMinorUnits != 0L)) {
            throw new IllegalArgumentException(
                    "Normalized offer event money is invalid");
        }
        return authorizedMoneyMinorUnits;
    }

    static List<BarterTradeEvent.IngredientEntry> barterEntries(
            List<OfferItemComponent> components,
            int quantity
    ) {
        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "Normalized offer event quantity is invalid");
        }
        return List.copyOf(components).stream().map(component ->
                new BarterTradeEvent.IngredientEntry(
                        component.itemId(),
                        Math.multiplyExact(
                                component.count(), quantity)))
                .toList();
    }

    private static void requireListing(
            ServerShopOfferListing listing,
            int quantity
    ) {
        Objects.requireNonNull(listing, "listing");
        if (listing.outputs().isEmpty() || quantity <= 0) {
            throw new IllegalArgumentException(
                    "Normalized offer event listing is invalid");
        }
    }

    private static String acquireType(AcquireOfferOption option) {
        if (option.free()) {
            return "FREE";
        }
        if (option.compound()) {
            return "MONEY_AND_BARTER";
        }
        return option.hasItemCosts() ? "BARTER" : "BUY";
    }

    public record Decision(
            Status status,
            long authorizedMoneyMinorUnits
    ) {
        public Decision {
            Objects.requireNonNull(status, "status");
            if (status == Status.ACCEPTED
                    != (authorizedMoneyMinorUnits >= 0L)
                    || status != Status.ACCEPTED
                    && authorizedMoneyMinorUnits != -1L) {
                throw new IllegalArgumentException(
                        "Normalized offer event decision is invalid");
            }
        }

        private static Decision accepted(long authorized) {
            return new Decision(Status.ACCEPTED, authorized);
        }

        private static Decision cancelled() {
            return new Decision(Status.CANCELLED, -1L);
        }

        private static Decision invalid() {
            return new Decision(Status.INVALID, -1L);
        }
    }

    public enum Status {
        ACCEPTED,
        CANCELLED,
        INVALID
    }
}

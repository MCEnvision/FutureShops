package com.enviouse.futureshops.server.escrow.playershop;

import com.enviouse.futureshops.catalog.offer.OfferAction;
import com.enviouse.futureshops.catalog.offer.OfferLimitPolicy;

import java.util.Objects;
import java.util.List;
import java.util.Arrays;

public record PlayerShopOfferSelection(
        String listingId,
        long offerRevision,
        String optionId,
        OfferAction action,
        OfferLimitPolicy listingLimits,
        OfferLimitPolicy optionLimits,
        long capacity,
        List<PlayerShopListingSnapshot.ItemTemplate> outputComponents,
        List<PlayerShopListingSnapshot.ItemTemplate> inputComponents
) {
    public PlayerShopOfferSelection {
        listingId = PlayerShopBinarySupport.requireString(
                listingId, PlayerShopEscrowConstants.MAX_IDENTIFIER_LENGTH,
                "offer listing id");
        optionId = PlayerShopBinarySupport.requireString(
                optionId, PlayerShopEscrowConstants.MAX_IDENTIFIER_LENGTH,
                "offer option id");
        action = Objects.requireNonNull(action, "action");
        listingLimits = Objects.requireNonNull(
                listingLimits, "listingLimits");
        optionLimits = Objects.requireNonNull(
                optionLimits, "optionLimits");
        outputComponents = bounded(
                outputComponents, "outputComponents");
        inputComponents = bounded(
                inputComponents, "inputComponents");
        if (offerRevision < 0L || capacity < 0L
                || !valid(listingLimits)
                || !valid(optionLimits)
                || action == OfferAction.ACQUIRE_FROM_SHOP
                && outputComponents.isEmpty()
                || action == OfferAction.SELL_TO_SHOP
                && inputComponents.isEmpty()) {
            throw new IllegalArgumentException(
                    "Player shop offer revision is invalid");
        }
    }

    private static List<PlayerShopListingSnapshot.ItemTemplate> bounded(
            List<PlayerShopListingSnapshot.ItemTemplate> values,
            String field
    ) {
        List<PlayerShopListingSnapshot.ItemTemplate> copy =
                List.copyOf(Objects.requireNonNull(values, field));
        if (copy.size()
                > PlayerShopEscrowConstants.MAX_LISTING_OUTPUTS) {
            throw new IllegalArgumentException(
                    "Player shop offer components are too large");
        }
        for (int first = 0; first < copy.size(); first++) {
            for (int second = first + 1;
                 second < copy.size(); second++) {
                PlayerShopListingSnapshot.ItemTemplate left =
                        copy.get(first);
                PlayerShopListingSnapshot.ItemTemplate right =
                        copy.get(second);
                if (left.itemId().equals(right.itemId())
                        && left.matchMode() == right.matchMode()
                        && Arrays.equals(
                        left.canonicalOneCountTemplate(),
                        right.canonicalOneCountTemplate())) {
                    throw new IllegalArgumentException(
                            "Player shop offer components are duplicated");
                }
            }
        }
        return copy;
    }

    private static boolean valid(OfferLimitPolicy limits) {
        return limits.maximumPerRequest() >= 1
                && limits.maximumPerRequest()
                <= OfferLimitPolicy.DEFAULT_MAXIMUM_PER_REQUEST
                && limits.lifetimeLimit() >= 0L
                && limits.periodLimit() >= 0L
                && limits.periodSeconds() >= 0L
                && limits.cooldownSeconds() >= 0L
                && (limits.periodLimit() == 0L)
                == (limits.periodSeconds() == 0L);
    }
}

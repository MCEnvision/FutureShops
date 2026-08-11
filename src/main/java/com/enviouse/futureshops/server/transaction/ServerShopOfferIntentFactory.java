package com.enviouse.futureshops.server.transaction;

import com.enviouse.futureshops.catalog.offer.AcquireOfferOption;
import com.enviouse.futureshops.catalog.offer.OfferAction;
import com.enviouse.futureshops.catalog.offer.OfferItemComponent;
import com.enviouse.futureshops.catalog.offer.OfferLimitPolicy;
import com.enviouse.futureshops.catalog.offer.SellOfferOption;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferListing;
import com.enviouse.futureshops.money.ItemStackSnapshotCodec;
import com.enviouse.futureshops.money.PaymentSource;
import com.enviouse.futureshops.server.escrow.model.DimensionAwareShopReference;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopAssetEndpoint;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopClaimPlan;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopEscrowIntent;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopIdentity;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopItemLot;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopItemMatchMode;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopItemTransfer;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopListingSnapshot;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopMoneyTransfer;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopOperation;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopOfferSelection;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopPaymentSource;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopTradeMethod;
import net.minecraft.world.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ServerShopOfferIntentFactory {
    private static final long BALANCE_NOT_APPLICABLE =
            PlayerShopMoneyTransfer.BALANCE_NOT_APPLICABLE;

    private ServerShopOfferIntentFactory() {
    }

    public static Prepared acquire(
            UUID requestId,
            UUID playerId,
            String shopId,
            ServerShopOfferListing listing,
            AcquireOfferOption option,
            int quantity,
            PaymentSource paymentSource,
            long walletBeforeMinorUnits,
            DimensionAwareShopReference shopReference,
            Instant quotedAt
    ) {
        return acquire(requestId, playerId, shopId, listing, option,
                quantity, option.moneyCostPresent()
                        ? Math.multiplyExact(
                        option.moneyCostMinorUnits(), quantity)
                        : 0L,
                paymentSource, walletBeforeMinorUnits, shopReference,
                quotedAt);
    }

    public static Prepared acquire(
            UUID requestId,
            UUID playerId,
            String shopId,
            ServerShopOfferListing listing,
            AcquireOfferOption option,
            int quantity,
            long quotedMoneyTotalMinorUnits,
            PaymentSource paymentSource,
            long walletBeforeMinorUnits,
            DimensionAwareShopReference shopReference,
            Instant quotedAt
    ) {
        Objects.requireNonNull(option, "option");
        requireCommon(requestId, playerId, shopId, listing, option.optionId(),
                quantity, shopReference, quotedAt);
        if (!listing.acquireOptions().contains(option)) {
            throw new IllegalArgumentException(
                    "Server shop acquire option is not in the listing");
        }
        PlayerShopPaymentSource source = option.moneyCostPresent()
                ? mapSource(paymentSource)
                : PlayerShopPaymentSource.NONE;
        if (!option.moneyCostPresent()
                && paymentSource != null) {
            throw new IllegalArgumentException(
                    "Server shop nonmoney offer has a payment source");
        }
        if (option.moneyCostPresent()
                != (quotedMoneyTotalMinorUnits > 0L)) {
            throw new IllegalArgumentException(
                    "Server shop quoted money total is invalid");
        }
        List<OfferItemComponent> outputs = listing.outputs().stream()
                .map(component -> scaled(component,
                        option.outputMultiplier())).toList();
        PlayerShopListingSnapshot snapshot = listingSnapshot(
                listing, option, outputs, 0L,
                PlayerShopListingSnapshot.Direction.SELL);
        PlayerShopIdentity identity = identity(
                shopId, listing.revision(), shopReference);
        UUID systemOwner = identity.ownerId();
        List<PlayerShopMoneyTransfer> money = new ArrayList<>();
        List<PlayerShopItemTransfer> items = new ArrayList<>();
        List<PlayerShopClaimPlan> claims = new ArrayList<>();
        if (option.moneyCostPresent()) {
            UUID transferId = deterministic(requestId, "money debit");
            money.add(new PlayerShopMoneyTransfer(
                    transferId,
                    PlayerShopAssetEndpoint.participant(
                            source == PlayerShopPaymentSource.WALLET
                                    ? PlayerShopAssetEndpoint.Kind.ACTOR_WALLET
                                    : PlayerShopAssetEndpoint.Kind.ACTOR_CASH,
                            playerId, "server shop offer funding"),
                    PlayerShopAssetEndpoint.system(
                            PlayerShopAssetEndpoint.Kind.ADMIN_SINK,
                            "server shop offer " + shopId),
                    quotedMoneyTotalMinorUnits, source,
                    source == PlayerShopPaymentSource.WALLET
                            ? walletBeforeMinorUnits
                            : BALANCE_NOT_APPLICABLE,
                    BALANCE_NOT_APPLICABLE));
        }
        addInputTransfers(requestId, playerId, option.itemCosts(),
                quantity, items);
        addOutputTransfers(requestId, playerId, outputs, quantity,
                items, claims);
        PlayerShopEscrowIntent intent = PlayerShopEscrowIntent.prepared(
                requestId, playerId, systemOwner, identity,
                PlayerShopOperation.SERVER_SHOP_OFFER_ACQUIRE,
                tradeMethod(option), source, quantity, quotedAt, snapshot,
                money, items, claims, List.of(),
                Optional.of(new PlayerShopOfferSelection(
                        listing.listingId(), listing.revision(),
                        option.optionId(),
                        OfferAction.ACQUIRE_FROM_SHOP,
                        listing.limits(), option.limits(), 0L,
                        snapshot.outputs(),
                        option.itemCosts().stream()
                                .map(ServerShopOfferIntentFactory::template)
                                .toList())));
        return new Prepared(OfferAction.ACQUIRE_FROM_SHOP,
                listing.listingId(), option.optionId(), listing.revision(),
                intent);
    }

    public static Prepared sell(
            UUID requestId,
            UUID playerId,
            String shopId,
            ServerShopOfferListing listing,
            SellOfferOption option,
            int quantity,
            DimensionAwareShopReference shopReference,
            Instant quotedAt
    ) {
        return sell(requestId, playerId, shopId, listing, option,
                quantity, Math.multiplyExact(
                        option.moneyPayoutMinorUnits(), quantity),
                shopReference, quotedAt);
    }

    public static Prepared sell(
            UUID requestId,
            UUID playerId,
            String shopId,
            ServerShopOfferListing listing,
            SellOfferOption option,
            int quantity,
            long quotedMoneyTotalMinorUnits,
            DimensionAwareShopReference shopReference,
            Instant quotedAt
    ) {
        Objects.requireNonNull(option, "option");
        requireCommon(requestId, playerId, shopId, listing, option.optionId(),
                quantity, shopReference, quotedAt);
        if (!listing.sellOptions().contains(option)) {
            throw new IllegalArgumentException(
                    "Server shop sell option is not in the listing");
        }
        if (quotedMoneyTotalMinorUnits <= 0L) {
            throw new IllegalArgumentException(
                    "Server shop quoted payout is invalid");
        }
        PlayerShopListingSnapshot snapshot = listingSnapshot(
                listing, null, option.itemInputs(),
                option.moneyPayoutMinorUnits(),
                PlayerShopListingSnapshot.Direction.BUY);
        PlayerShopIdentity identity = identity(
                shopId, listing.revision(), shopReference);
        List<PlayerShopItemTransfer> items = new ArrayList<>();
        addInputTransfers(requestId, playerId, option.itemInputs(),
                quantity, items);
        PlayerShopClaimPlan moneyClaim = PlayerShopClaimPlan.money(
                requestId, "server shop offer payout", playerId,
                quotedMoneyTotalMinorUnits,
                "Server shop offer payout");
        PlayerShopMoneyTransfer money = new PlayerShopMoneyTransfer(
                deterministic(requestId, "money payout"),
                PlayerShopAssetEndpoint.system(
                        PlayerShopAssetEndpoint.Kind.ADMIN_MINT,
                        "server shop offer " + shopId),
                PlayerShopAssetEndpoint.participant(
                        PlayerShopAssetEndpoint.Kind.MONEY_CLAIM,
                        playerId, moneyClaim.claimId().toString()),
                quotedMoneyTotalMinorUnits,
                PlayerShopPaymentSource.NONE,
                BALANCE_NOT_APPLICABLE, 0L);
        PlayerShopEscrowIntent intent = PlayerShopEscrowIntent.prepared(
                requestId, playerId, identity.ownerId(), identity,
                PlayerShopOperation.SERVER_SHOP_OFFER_SELL,
                PlayerShopTradeMethod.BUYBACK,
                PlayerShopPaymentSource.NONE, quantity, quotedAt, snapshot,
                List.of(money), items, List.of(moneyClaim), List.of(),
                Optional.of(new PlayerShopOfferSelection(
                        listing.listingId(), listing.revision(),
                        option.optionId(), OfferAction.SELL_TO_SHOP,
                        listing.limits(), option.limits(),
                        option.capacity(), List.of(),
                        snapshot.outputs())));
        return new Prepared(OfferAction.SELL_TO_SHOP,
                listing.listingId(), option.optionId(), listing.revision(),
                intent);
    }

    public static CartPrepared acquireCart(
            UUID requestId,
            UUID playerId,
            String shopId,
            List<AcquireLine> requestedLines,
            PaymentSource paymentSource,
            long walletBeforeMinorUnits,
            DimensionAwareShopReference shopReference,
            Instant quotedAt
    ) {
        Objects.requireNonNull(requestedLines, "requestedLines");
        if (requestedLines.isEmpty() || requestedLines.size() > 256) {
            throw new IllegalArgumentException(
                    "Server shop offer cart line count is invalid");
        }
        List<AcquireLine> lines = requestedLines.stream()
                .sorted(Comparator.comparing((AcquireLine value) ->
                                value.listing().listingId())
                        .thenComparing(value ->
                                value.option().optionId()))
                .toList();
        LinkedHashMap<ComponentKey, Integer> outputTotals =
                new LinkedHashMap<>();
        LinkedHashMap<ComponentKey, Integer> inputTotals =
                new LinkedHashMap<>();
        long moneyTotal = 0L;
        boolean hasMoney = false;
        for (AcquireLine line : lines) {
            requireCommon(requestId, playerId, shopId,
                    line.listing(), line.option().optionId(),
                    line.quantity(), shopReference, quotedAt);
            if (!line.listing().acquireOptions()
                    .contains(line.option())) {
                throw new IllegalArgumentException(
                        "Server shop cart option is not in the listing");
            }
            if (line.option().moneyCostPresent()) {
                hasMoney = true;
                moneyTotal = Math.addExact(moneyTotal,
                        line.quotedMoneyTotalMinorUnits());
            }
            for (OfferItemComponent component
                    : line.listing().outputs()) {
                int total = Math.multiplyExact(
                        Math.multiplyExact(component.count(),
                                line.option().outputMultiplier()),
                        line.quantity());
                mergeComponent(outputTotals, component, total);
            }
            for (OfferItemComponent component
                    : line.option().itemCosts()) {
                mergeComponent(inputTotals, component,
                        Math.multiplyExact(
                                component.count(), line.quantity()));
            }
        }
        if (hasMoney != (paymentSource != null)) {
            throw new IllegalArgumentException(
                    "Server shop cart payment source is invalid");
        }
        List<OfferItemComponent> outputs =
                components("cart.output", outputTotals);
        List<OfferItemComponent> inputs =
                components("cart.input", inputTotals);
        if (outputs.isEmpty()) {
            throw new IllegalArgumentException(
                    "Server shop cart output is empty");
        }
        PlayerShopPaymentSource source = hasMoney
                ? mapSource(paymentSource)
                : PlayerShopPaymentSource.NONE;
        List<PlayerShopMoneyTransfer> money = new ArrayList<>();
        if (hasMoney) {
            money.add(new PlayerShopMoneyTransfer(
                    deterministic(requestId, "cart money debit"),
                    PlayerShopAssetEndpoint.participant(
                            source == PlayerShopPaymentSource.WALLET
                                    ? PlayerShopAssetEndpoint.Kind.ACTOR_WALLET
                                    : PlayerShopAssetEndpoint.Kind.ACTOR_CASH,
                            playerId, "server shop offer cart funding"),
                    PlayerShopAssetEndpoint.system(
                            PlayerShopAssetEndpoint.Kind.ADMIN_SINK,
                            "server shop offer " + shopId),
                    moneyTotal, source,
                    source == PlayerShopPaymentSource.WALLET
                            ? walletBeforeMinorUnits
                            : BALANCE_NOT_APPLICABLE,
                    BALANCE_NOT_APPLICABLE));
        }
        List<PlayerShopItemTransfer> items = new ArrayList<>();
        List<PlayerShopClaimPlan> claims = new ArrayList<>();
        addInputTransfers(requestId, playerId, inputs, 1, items);
        addOutputTransfers(requestId, playerId, outputs, 1,
                items, claims);
        PlayerShopTradeMethod method = hasMoney
                ? inputs.isEmpty()
                ? PlayerShopTradeMethod.MONEY
                : PlayerShopTradeMethod.MONEY_AND_BARTER
                : inputs.isEmpty()
                ? PlayerShopTradeMethod.FREE
                : PlayerShopTradeMethod.BARTER;
        PlayerShopListingSnapshot.ConfiguredTradeMode configured =
                inputs.isEmpty()
                        ? PlayerShopListingSnapshot.ConfiguredTradeMode.MONEY
                        : hasMoney
                        ? PlayerShopListingSnapshot.ConfiguredTradeMode
                        .MONEY_AND_BARTER
                        : PlayerShopListingSnapshot.ConfiguredTradeMode.BARTER;
        List<PlayerShopListingSnapshot.ItemTemplate> templates =
                outputs.stream()
                        .map(ServerShopOfferIntentFactory::template)
                        .toList();
        PlayerShopListingSnapshot.ItemTemplate barter =
                inputs.isEmpty() ? null : template(inputs.get(0));
        PlayerShopListingSnapshot snapshot =
                PlayerShopListingSnapshot.capture(
                        "offer_cart", 0,
                        PlayerShopListingSnapshot.Direction.SELL,
                        configured, templates.get(0).unitsPerPurchase(),
                        moneyTotal, barter,
                        barter == null ? 0 : barter.unitsPerPurchase(),
                        0L, 0, 0, templates,
                        new PlayerShopListingSnapshot.PromotionSnapshot(
                                "", 0.0D, 0, 0, 0L, 0L,
                                false, false),
                        false, false, true);
        PlayerShopIdentity identity =
                identity(shopId, 0L, shopReference);
        PlayerShopEscrowIntent intent = PlayerShopEscrowIntent.prepared(
                requestId, playerId, identity.ownerId(), identity,
                PlayerShopOperation.SERVER_SHOP_OFFER_ACQUIRE,
                method, source, 1, quotedAt, snapshot,
                money, items, claims, List.of(),
                Optional.of(new PlayerShopOfferSelection(
                        "offer_cart", 0L, "cart",
                        OfferAction.ACQUIRE_FROM_SHOP,
                        OfferLimitPolicy.defaults(),
                        OfferLimitPolicy.defaults(), 0L,
                        snapshot.outputs(),
                        inputs.stream()
                                .map(ServerShopOfferIntentFactory::template)
                                .toList())));
        return new CartPrepared(lines, intent);
    }

    private static void mergeComponent(
            Map<ComponentKey, Integer> totals,
            OfferItemComponent component,
            int amount
    ) {
        ComponentKey key = new ComponentKey(
                component.itemId(), component.exactNbt());
        totals.merge(key, amount, Math::addExact);
    }

    private static List<OfferItemComponent> components(
            String prefix,
            Map<ComponentKey, Integer> totals
    ) {
        List<OfferItemComponent> result =
                new ArrayList<>(totals.size());
        int index = 0;
        for (Map.Entry<ComponentKey, Integer> entry
                : totals.entrySet()) {
            result.add(new OfferItemComponent(
                    prefix + "." + index,
                    entry.getKey().itemId(),
                    entry.getValue(),
                    entry.getKey().exactNbt()));
            index++;
        }
        return List.copyOf(result);
    }

    private static void addInputTransfers(
            UUID requestId,
            UUID playerId,
            List<OfferItemComponent> components,
            int quantity,
            List<PlayerShopItemTransfer> target
    ) {
        int componentIndex = 0;
        for (OfferItemComponent component : components) {
            int total = Math.multiplyExact(component.count(), quantity);
            List<PlayerShopItemLot> portions = lots(
                    requestId, "input." + componentIndex,
                    component, total);
            for (int portionIndex = 0; portionIndex < portions.size();
                 portionIndex++) {
                PlayerShopItemLot lot = portions.get(portionIndex);
                target.add(new PlayerShopItemTransfer(
                        deterministic(requestId, "input transfer "
                                + componentIndex + " " + portionIndex),
                        PlayerShopAssetEndpoint.participant(
                                PlayerShopAssetEndpoint.Kind.ACTOR_INVENTORY,
                                playerId, component.componentId()),
                        PlayerShopAssetEndpoint.system(
                                PlayerShopAssetEndpoint.Kind.ADMIN_SINK,
                                "server shop offer item sink"),
                        lot));
            }
            componentIndex++;
        }
    }

    private static void addOutputTransfers(
            UUID requestId,
            UUID playerId,
            List<OfferItemComponent> components,
            int quantity,
            List<PlayerShopItemTransfer> transfers,
            List<PlayerShopClaimPlan> claims
    ) {
        int componentIndex = 0;
        for (OfferItemComponent component : components) {
            int total = Math.multiplyExact(component.count(), quantity);
            List<PlayerShopItemLot> portions = lots(
                    requestId, "output." + componentIndex,
                    component, total);
            for (int portionIndex = 0; portionIndex < portions.size();
                 portionIndex++) {
                PlayerShopItemLot lot = portions.get(portionIndex);
                String key = "server shop offer output "
                        + componentIndex + " " + portionIndex;
                PlayerShopClaimPlan claim = PlayerShopClaimPlan.item(
                        requestId, key, playerId, lot,
                        "Server shop offer output");
                claims.add(claim);
                transfers.add(new PlayerShopItemTransfer(
                        deterministic(requestId, "output transfer "
                                + componentIndex + " " + portionIndex),
                        PlayerShopAssetEndpoint.system(
                                PlayerShopAssetEndpoint.Kind.ADMIN_MINT,
                                "server shop offer item source"),
                        PlayerShopAssetEndpoint.participant(
                                PlayerShopAssetEndpoint.Kind.ITEM_CLAIM,
                                playerId, claim.claimId().toString()),
                        lot));
            }
            componentIndex++;
        }
    }

    private static List<PlayerShopItemLot> lots(
            UUID requestId,
            String sourceKey,
            OfferItemComponent component,
            int total
    ) {
        ItemStack prototype = ShopEscrowItemEvidence.exactStack(
                component.itemId(), component.exactNbt(), 1);
        byte[] template = ItemStackSnapshotCodec.encode(prototype);
        PlayerShopItemMatchMode matchMode = component.exactMatch()
                ? PlayerShopItemMatchMode.EXACT
                : PlayerShopItemMatchMode.ITEM_ONLY;
        int maximum = Math.max(1, Math.min(
                Byte.MAX_VALUE, prototype.getMaxStackSize()));
        int portionCount = Math.floorDiv(
                Math.addExact(total, maximum - 1), maximum);
        List<PlayerShopItemLot> lots = new ArrayList<>(portionCount);
        int remaining = total;
        for (int index = 0; index < portionCount; index++) {
            int count = Math.min(remaining, maximum);
            ItemStack exact = prototype.copy();
            exact.setCount(count);
            lots.add(PlayerShopItemLot.captureRaw(
                    requestId, sourceKey, index, portionCount,
                    component.itemId(), count, matchMode, template,
                    ItemStackSnapshotCodec.encode(exact)));
            remaining -= count;
        }
        if (remaining != 0 || lots.isEmpty()) {
            throw new IllegalArgumentException(
                    "Server shop offer item portions are invalid");
        }
        return List.copyOf(lots);
    }

    private static PlayerShopListingSnapshot listingSnapshot(
            ServerShopOfferListing listing,
            AcquireOfferOption option,
            List<OfferItemComponent> components,
            long payoutMinorUnits,
            PlayerShopListingSnapshot.Direction direction
    ) {
        List<PlayerShopListingSnapshot.ItemTemplate> templates =
                components.stream().map(
                        ServerShopOfferIntentFactory::template).toList();
        PlayerShopListingSnapshot.ConfiguredTradeMode mode =
                configuredMode(option);
        PlayerShopListingSnapshot.ItemTemplate barter =
                option != null && option.hasItemCosts()
                        ? template(option.itemCosts().get(0)) : null;
        int barterUnits = barter == null ? 0 : barter.unitsPerPurchase();
        long price = option != null && option.moneyCostPresent()
                ? option.moneyCostMinorUnits() : 0L;
        return PlayerShopListingSnapshot.capture(
                listing.listingId(), 0, direction, mode,
                templates.get(0).unitsPerPurchase(), price,
                barter, barterUnits, payoutMinorUnits, 0, 0,
                templates,
                new PlayerShopListingSnapshot.PromotionSnapshot(
                        "", 0.0D, 0, 0, 0L, 0L, false, false),
                !listing.active(), false, true);
    }

    private static PlayerShopListingSnapshot.ItemTemplate template(
            OfferItemComponent component
    ) {
        ItemStack stack = ShopEscrowItemEvidence.exactStack(
                component.itemId(), component.exactNbt(), 1);
        return new PlayerShopListingSnapshot.ItemTemplate(
                component.itemId(), component.count(),
                component.exactMatch() ? PlayerShopItemMatchMode.EXACT
                        : PlayerShopItemMatchMode.ITEM_ONLY,
                ItemStackSnapshotCodec.encode(stack));
    }

    private static PlayerShopListingSnapshot.ConfiguredTradeMode
    configuredMode(AcquireOfferOption option) {
        if (option == null || !option.hasItemCosts()) {
            return PlayerShopListingSnapshot.ConfiguredTradeMode.MONEY;
        }
        return option.moneyCostPresent()
                ? PlayerShopListingSnapshot.ConfiguredTradeMode
                .MONEY_AND_BARTER
                : PlayerShopListingSnapshot.ConfiguredTradeMode.BARTER;
    }

    private static PlayerShopTradeMethod tradeMethod(
            AcquireOfferOption option
    ) {
        if (option.free()) {
            return PlayerShopTradeMethod.FREE;
        }
        if (option.compound()) {
            return PlayerShopTradeMethod.MONEY_AND_BARTER;
        }
        return option.moneyCostPresent()
                ? PlayerShopTradeMethod.MONEY
                : PlayerShopTradeMethod.BARTER;
    }

    private static PlayerShopPaymentSource mapSource(PaymentSource source) {
        return switch (Objects.requireNonNull(source, "paymentSource")) {
            case WALLET -> PlayerShopPaymentSource.WALLET;
            case PHYSICAL -> PlayerShopPaymentSource.INVENTORY_CASH;
        };
    }

    private static PlayerShopIdentity identity(
            String shopId,
            long revision,
            DimensionAwareShopReference reference
    ) {
        UUID registryId = named("server shop identity " + shopId);
        UUID ownerId = named("server shop owner " + shopId);
        return new PlayerShopIdentity(registryId, revision, shopId,
                reference.dimensionId(), reference.blockX(),
                reference.blockY(), reference.blockZ(), ownerId);
    }

    private static OfferItemComponent scaled(
            OfferItemComponent component,
            int multiplier
    ) {
        return new OfferItemComponent(component.componentId(),
                component.itemId(),
                Math.multiplyExact(component.count(), multiplier),
                component.exactNbt());
    }

    public record AcquireLine(
            ServerShopOfferListing listing,
            AcquireOfferOption option,
            int quantity,
            long quotedMoneyTotalMinorUnits
    ) {
        public AcquireLine {
            Objects.requireNonNull(listing, "listing");
            Objects.requireNonNull(option, "option");
            if (quantity <= 0 || quantity > 2304) {
                throw new IllegalArgumentException(
                        "Server shop cart line quantity is invalid");
            }
            if (option.moneyCostPresent()
                    != (quotedMoneyTotalMinorUnits > 0L)) {
                throw new IllegalArgumentException(
                        "Server shop cart line money total is invalid");
            }
        }

        public AcquireLine(
                ServerShopOfferListing listing,
                AcquireOfferOption option,
                int quantity
        ) {
            this(listing, option, quantity,
                    option.moneyCostPresent()
                            ? Math.multiplyExact(
                            option.moneyCostMinorUnits(), quantity)
                            : 0L);
        }
    }

    public record CartPrepared(
            List<AcquireLine> lines,
            PlayerShopEscrowIntent intent
    ) {
        public CartPrepared {
            lines = List.copyOf(lines);
            Objects.requireNonNull(intent, "intent");
        }
    }

    private record ComponentKey(String itemId, String exactNbt) {
    }

    private static void requireCommon(
            UUID requestId,
            UUID playerId,
            String shopId,
            ServerShopOfferListing listing,
            String optionId,
            int quantity,
            DimensionAwareShopReference reference,
            Instant quotedAt
    ) {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(shopId, "shopId");
        Objects.requireNonNull(listing, "listing");
        Objects.requireNonNull(optionId, "optionId");
        Objects.requireNonNull(reference, "shopReference");
        Objects.requireNonNull(quotedAt, "quotedAt");
        if (requestId.equals(new UUID(0L, 0L))
                || playerId.equals(new UUID(0L, 0L))
                || shopId.isBlank() || optionId.isBlank()
                || quantity <= 0
                || !shopId.equals(reference.shopId())) {
            throw new IllegalArgumentException(
                    "Server shop offer request is invalid");
        }
    }

    private static UUID deterministic(UUID requestId, String key) {
        return named("server shop offer " + requestId + " " + key);
    }

    private static UUID named(String value) {
        return UUID.nameUUIDFromBytes(
                value.getBytes(StandardCharsets.UTF_8));
    }

    public record Prepared(
            OfferAction action,
            String listingId,
            String optionId,
            long offerRevision,
            PlayerShopEscrowIntent intent
    ) {
        public Prepared {
            Objects.requireNonNull(action, "action");
            listingId = Objects.requireNonNull(listingId, "listingId");
            optionId = Objects.requireNonNull(optionId, "optionId");
            Objects.requireNonNull(intent, "intent");
            if (listingId.isBlank() || optionId.isBlank()
                    || offerRevision < 0L
                    || intent.shopIdentity().identityRevision()
                    != offerRevision
                    || !intent.listing().listingId().equals(listingId)) {
                throw new IllegalArgumentException(
                        "Server shop prepared offer is invalid");
            }
        }
    }
}

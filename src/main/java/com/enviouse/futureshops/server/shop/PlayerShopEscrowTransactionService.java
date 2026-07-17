package com.enviouse.futureshops.server.shop;

import com.enviouse.futureshops.Config;
import com.enviouse.futureshops.block.ShopBlockEntity;
import com.enviouse.futureshops.money.ItemStackSnapshotCodec;
import com.enviouse.futureshops.money.PaymentSource;
import com.enviouse.futureshops.network.packets.C2SPlayerShopSellPacket;
import com.enviouse.futureshops.server.economy.BalanceManager;
import com.enviouse.futureshops.server.economy.EconomyProvider;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopAssetEndpoint;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopClaimPlan;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopEscrowIntent;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopEscrowOrchestrator;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopIdentity;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopItemLot;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopItemMatchMode;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopItemTransfer;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopListingSnapshot;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopMoneyTransfer;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopOperation;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopPaymentSource;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopStorageEndpoint;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopStorageMutationPlan;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopTradeMethod;
import com.enviouse.futureshops.server.escrow.runtime.PlayerShopLiveEscrowService;
import com.enviouse.futureshops.server.transaction.NbtMatchUtil;
import com.enviouse.futureshops.server.transaction.ShopTransactionUtil;
import com.enviouse.futureshops.server.transaction.TransactionHistoryService;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

final class PlayerShopEscrowTransactionService {
    private static final UUID ZERO_UUID = new UUID(0L, 0L);

    private PlayerShopEscrowTransactionService() {
    }

    static void buy(
            ServerPlayer buyer,
            BlockPos pos,
            int listingIndex,
            int quantity,
            String paymentMethod,
            String paymentSourceWire,
            UUID requestId,
            int responseToken
    ) {
        if (!ShopTransactionUtil.isValidBuyQuantity(quantity)) {
            PlayerShopBlockService.sendResult(buyer, false,
                    ShopResultCode.INVALID_AMOUNT);
            return;
        }
        if (buyer.getServer() == null
                || ZERO_UUID.equals(requestId)) {
            PlayerShopBlockService.sendResult(buyer, false,
                    ShopResultCode.INVALID_REQUEST);
            return;
        }
        ReentrantLock lock = PlayerShopBlockService.transactionLock(pos);
        lock.lock();
        try {
            Optional<PlayerShopEscrowIntent> existing =
                    PlayerShopLiveEscrowService.existingIntent(
                            buyer, requestId);
            if (existing.isPresent()) {
                resumeExisting(buyer, pos, listingIndex, responseToken,
                        existing.orElseThrow(), PlayerShopOperation.PURCHASE,
                        PlayerShopOperation.ADMIN_PURCHASE_SINK);
                return;
            }
            PurchaseQuote quote = quotePurchase(buyer, pos, listingIndex,
                    quantity, paymentMethod, paymentSourceWire, requestId);
            PlayerShopEscrowOrchestrator.Result result =
                    PlayerShopLiveEscrowService.execute(buyer, quote.intent(),
                            responseToken, quote.storage());
            finishPurchase(buyer, pos, quote, result);
        } catch (QuoteFailure failure) {
            failure.send(buyer);
        } catch (RuntimeException exception) {
            PlayerShopBlockService.sendResult(buyer, false,
                    ShopResultCode.SERVER_ERROR);
        } finally {
            lock.unlock();
        }
    }

    static void sell(
            ServerPlayer seller,
            C2SPlayerShopSellPacket packet
    ) {
        if (seller.getServer() == null
                || ZERO_UUID.equals(packet.requestId())
                || packet.quantity() < 1
                || packet.quantity()
                > ShopTransactionUtil.MAX_SELL_QUANTITY) {
            PlayerShopBlockService.sendResult(seller, false,
                    ShopResultCode.INVALID_REQUEST);
            return;
        }
        BlockPos pos = packet.shopPos();
        ReentrantLock lock = PlayerShopBlockService.transactionLock(pos);
        lock.lock();
        try {
            Optional<PlayerShopEscrowIntent> existing =
                    PlayerShopLiveEscrowService.existingIntent(
                            seller, packet.requestId());
            if (existing.isPresent()) {
                resumeExisting(seller, pos, packet.listingIndex(),
                        packet.responseToken(), existing.orElseThrow(),
                        PlayerShopOperation.BUYBACK,
                        PlayerShopOperation.ADMIN_BUYBACK);
                return;
            }
            BuybackQuote quote = quoteBuyback(seller, packet);
            PlayerShopEscrowOrchestrator.Result result =
                    PlayerShopLiveEscrowService.execute(seller,
                            quote.intent(), packet.responseToken(),
                            quote.storage());
            finishBuyback(seller, pos, quote, result);
        } catch (QuoteFailure failure) {
            failure.send(seller);
        } catch (RuntimeException exception) {
            PlayerShopBlockService.sendResult(seller, false,
                    ShopResultCode.SERVER_ERROR);
        } finally {
            lock.unlock();
        }
    }

    private static void resumeExisting(
            ServerPlayer actor,
            BlockPos pos,
            int requestedListingIndex,
            int responseToken,
            PlayerShopEscrowIntent intent,
            PlayerShopOperation first,
            PlayerShopOperation second
    ) {
        if (intent.operation() != first && intent.operation() != second
                || intent.listing() == null
                || !samePosition(intent.shopIdentity(), actor, pos)) {
            PlayerShopBlockService.sendResult(actor, false,
                    ShopResultCode.INVALID_REQUEST);
            return;
        }
        if (!(actor.level().getBlockEntity(pos)
                instanceof ShopBlockEntity shop)) {
            PlayerShopBlockService.sendResult(actor, false,
                    ShopResultCode.INVALID_TARGET);
            return;
        }
        int resolved = PlayerShopBlockService.resolveVisitorListingIndex(
                shop, actor, requestedListingIndex);
        if (resolved != intent.listing().listingIndex()) {
            PlayerShopBlockService.sendResult(actor, false,
                    ShopResultCode.INVALID_REQUEST);
            return;
        }
        LiveStorageAccess storage = new LiveStorageAccess(actor, pos,
                resolved, intent);
        PlayerShopEscrowOrchestrator.Result result =
                PlayerShopLiveEscrowService.execute(actor, intent,
                        responseToken, storage);
        if (intent.operation() == PlayerShopOperation.PURCHASE
                || intent.operation()
                == PlayerShopOperation.ADMIN_PURCHASE_SINK) {
            finishExistingPurchase(actor, pos, result);
        } else {
            finishExistingBuyback(actor, pos, result);
        }
    }

    private static PurchaseQuote quotePurchase(
            ServerPlayer buyer,
            BlockPos pos,
            int requestedListingIndex,
            int quantity,
            String paymentMethod,
            String paymentSourceWire,
            UUID requestId
    ) {
        if (!(buyer.level().getBlockEntity(pos)
                instanceof ShopBlockEntity shop)) {
            throw failure(ShopResultCode.INVALID_TARGET);
        }
        int listingIndex = PlayerShopBlockService
                .resolveVisitorListingIndex(shop, buyer,
                        requestedListingIndex);
        ShopBlockEntity.Listing listing = shop.getListing(listingIndex);
        requireConfigured(shop, listing, buyer, true);
        if (!listing.allowsSell()) {
            throw failure(ShopResultCode.UNCONFIGURED);
        }
        PaymentSource paymentSource = PaymentSource.fromWire(
                paymentSourceWire).orElseThrow(() ->
                failure(ShopResultCode.INVALID_REQUEST));
        Item saleItem = resolveItem(listing.itemId());
        PlayerShopBlockService.PurchasePlan plan;
        try {
            plan = PlayerShopBlockService.preparePurchasePlan(
                    listing, saleItem, quantity);
        } catch (PlayerShopBlockService.PurchasePlanException exception) {
            throw failure(exception.code());
        } catch (ArithmeticException | IllegalArgumentException exception) {
            throw failure(ShopResultCode.INVALID_AMOUNT);
        }

        TradeSelection selection = selectTrade(listing, paymentMethod,
                paymentSource);
        long cost = plan.cost();
        String shopIdForEvent = "player_shop:" + pos.asLong();
        if (Config.eventsTransactionEnabled) {
            var event = new com.enviouse.futureshops.event
                    .ShopTransactionEvent.Pre(
                    buyer, shopIdForEvent, listing.itemId(), quantity,
                    selection.eventType(), cost);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event);
            if (event.isCanceled()) {
                throw failure(ShopResultCode.CANCELLED_BY_EVENT);
            }
            cost = event.getPriceMinor();
        }
        if (cost < 0L) {
            throw failure(ShopResultCode.INVALID_AMOUNT);
        }

        Item barterItem = null;
        int barterAmount = 0;
        if (selection.needsBarter()) {
            barterItem = resolveItem(listing.barterItemId());
            try {
                barterAmount = PlayerShopBuyMath.checkedBarterTotal(
                        listing.effectiveBarterItemCount(), quantity);
            } catch (ArithmeticException | IllegalArgumentException exception) {
                throw failure(ShopResultCode.INVALID_AMOUNT);
            }
            if (Config.eventsTransactionEnabled) {
                var event = new com.enviouse.futureshops.event
                        .BarterTradeEvent.Pre(
                        buyer.getUUID(), shopIdForEvent,
                        listing.itemId() + (selection.compound()
                                ? ".compound" : ".barter"),
                        listing.itemId(), quantity,
                        List.of(new com.enviouse.futureshops.event
                                .BarterTradeEvent.IngredientEntry(
                                listing.barterItemId(), barterAmount)));
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                        event);
                if (event.isCanceled()) {
                    throw failure(ShopResultCode.CANCELLED_BY_EVENT);
                }
            }
        }

        PlayerShopListingSnapshot snapshot = captureListing(shop, listing,
                listingIndex, false, null, null);
        PlayerShopIdentity identity = captureIdentity(buyer, shop, pos);
        PlayerShopOperation operation = shop.isAdminShopMode()
                ? PlayerShopOperation.ADMIN_PURCHASE_SINK
                : PlayerShopOperation.PURCHASE;
        PlayerShopPaymentSource escrowSource = selection.needsMoney()
                ? paymentSource == PaymentSource.PHYSICAL
                ? PlayerShopPaymentSource.INVENTORY_CASH
                : PlayerShopPaymentSource.WALLET
                : PlayerShopPaymentSource.NONE;

        IntentAssembly assembly = new IntentAssembly(requestId,
                buyer.getUUID(), shop.getOwnerUuid(), identity,
                operation, selection.method(), escrowSource, quantity,
                snapshot);
        LiveStorageAccess storage = new LiveStorageAccess(buyer, pos,
                listingIndex, assembly.previewIntent());

        for (int outputIndex = 0;
             outputIndex < plan.outputs().size(); outputIndex++) {
            PlayerShopBlockService.PurchaseOutput output =
                    plan.outputs().get(outputIndex);
            PlayerShopListingSnapshot.ItemTemplate template =
                    snapshot.outputs().get(outputIndex);
            List<ItemStack> exactStacks = shop.isAdminShopMode()
                    ? splitExact(output.item(), output.count(),
                    output.nbtTag())
                    : PlayerShopBlockService.previewExtractComposite(
                    storage.stockStorages(), output.item(), output.count(),
                    output.nbtAware(), output.nbtTag());
            if (exactStacks.isEmpty()) {
                throw failure(ShopResultCode.OUT_OF_STOCK);
            }
            List<PlayerShopItemLot> lots = captureLots(requestId,
                    "purchase.output." + outputIndex, exactStacks,
                    template);
            assembly.addPurchaseOutputs(lots, storage,
                    shop.isAdminShopMode());
        }

        if (selection.needsBarter() && barterAmount > 0) {
            List<ItemStack> selected = selectInventoryStacks(buyer,
                    barterItem, barterAmount, listing.barterNbtAware(),
                    listing.barterNbtTag());
            if (selected.isEmpty()) {
                throw failureWithChat(
                        ShopResultCode.MISSING_BARTER_ITEMS,
                        "command.futureshops.shop.barter_not_enough");
            }
            List<PlayerShopItemLot> lots = captureLots(requestId,
                    "purchase.barter", selected,
                    snapshot.barterTemplate());
            if (!shop.isAdminShopMode()
                    && !storage.canInsertBarter(selected)) {
                throw failureWithChat(ShopResultCode.STORAGE_FULL,
                        "command.futureshops.shop.barter_storage_full");
            }
            assembly.addPurchaseInputs(lots, storage,
                    shop.isAdminShopMode());
        }

        if (selection.needsMoney() && cost > 0L) {
            long balance = BalanceManager.getProvider().getBalance(
                    buyer.getUUID());
            long sourceBefore = escrowSource
                    == PlayerShopPaymentSource.INVENTORY_CASH
                    ? PlayerShopMoneyTransfer.BALANCE_NOT_APPLICABLE
                    : balance;
            if (escrowSource == PlayerShopPaymentSource.WALLET
                    && balance < cost) {
                throw failure(ShopResultCode.INSUFFICIENT_FUNDS);
            }
            assembly.addPurchaseMoney(cost, sourceBefore,
                    shop.isAdminShopMode());
        }

        PlayerShopEscrowIntent intent = assembly.build();
        storage.bind(intent);
        return new PurchaseQuote(intent, storage, listing,
                selection, cost, barterItem, barterAmount,
                shopIdForEvent);
    }

    private static BuybackQuote quoteBuyback(
            ServerPlayer seller,
            C2SPlayerShopSellPacket packet
    ) {
        BlockPos pos = packet.shopPos();
        if (!(seller.level().getBlockEntity(pos)
                instanceof ShopBlockEntity shop)) {
            throw failure(ShopResultCode.INVALID_TARGET);
        }
        int listingIndex = PlayerShopBlockService
                .resolveVisitorListingIndex(shop, seller,
                        packet.listingIndex());
        ShopBlockEntity.Listing listing = shop.getListing(listingIndex);
        requireConfigured(shop, listing, seller, false);
        if (!listing.allowsBuy()) {
            throw failure(ShopResultCode.UNCONFIGURED);
        }
        int quantity = packet.quantity();
        int requiredItems;
        long total;
        try {
            requiredItems = Math.multiplyExact(listing.baseQuantity(),
                    quantity);
            total = Math.multiplyExact(listing.buybackPriceMinor(),
                    (long) quantity);
        } catch (ArithmeticException exception) {
            throw failure(ShopResultCode.INVALID_AMOUNT);
        }
        if (requiredItems <= 0 || total <= 0L) {
            throw failure(ShopResultCode.UNCONFIGURED);
        }
        if (listing.buybackRemaining() < quantity) {
            throw failureWithChat(ShopResultCode.BUYBACK_CAP_REACHED,
                    "command.futureshops.shop.buyback_cap_reached");
        }
        long configuredTotal = total;
        String shopIdForEvent = "player_shop:" + pos.asLong();
        if (Config.eventsTransactionEnabled) {
            var event = new com.enviouse.futureshops.event
                    .ShopTransactionEvent.Pre(
                    seller, shopIdForEvent, listing.itemId(), quantity,
                    "SELL_TO_SHOP", total);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event);
            if (event.isCanceled()) {
                throw failure(ShopResultCode.CANCELLED_BY_EVENT);
            }
            total = event.getPriceMinor();
        }
        if (total != configuredTotal) {
            throw failure(ShopResultCode.INVALID_AMOUNT);
        }

        Item item = resolveItem(listing.itemId());
        List<ItemStack> selected = selectInventoryStacks(seller, item,
                requiredItems, listing.nbtAware(), listing.nbtTag());
        if (selected.isEmpty()) {
            throw failureWithChat(ShopResultCode.MISSING_ITEMS,
                    "command.futureshops.shop.missing_items");
        }
        EconomyProvider economy = BalanceManager.getProvider();
        long sellerBalance = economy.getBalance(seller.getUUID());
        try {
            if (Math.addExact(sellerBalance, total)
                    > Config.economyMaxBalanceMinorUnits) {
                throw failure(ShopResultCode.MAX_BALANCE_EXCEEDED);
            }
        } catch (ArithmeticException exception) {
            throw failure(ShopResultCode.MAX_BALANCE_EXCEEDED);
        }
        long ownerBalance = PlayerShopMoneyTransfer
                .BALANCE_NOT_APPLICABLE;
        if (!shop.isAdminShopMode()) {
            ownerBalance = economy.getBalance(shop.getOwnerUuid());
            if (ownerBalance < total) {
                throw failureWithChat(
                        ShopResultCode.SHOP_OUT_OF_MONEY,
                        "command.futureshops.shop.shop_out_of_money");
            }
        }

        PlayerShopListingSnapshot snapshot = captureListing(shop, listing,
                listingIndex, true, listing.buybackPriceMinor(), null);
        PlayerShopIdentity identity = captureIdentity(seller, shop, pos);
        PlayerShopOperation operation = shop.isAdminShopMode()
                ? PlayerShopOperation.ADMIN_BUYBACK
                : PlayerShopOperation.BUYBACK;
        IntentAssembly assembly = new IntentAssembly(packet.requestId(),
                seller.getUUID(), shop.getOwnerUuid(), identity, operation,
                PlayerShopTradeMethod.BUYBACK,
                PlayerShopPaymentSource.NONE, quantity, snapshot);
        LiveStorageAccess storage = new LiveStorageAccess(seller, pos,
                listingIndex, assembly.previewIntent());
        if (!shop.isAdminShopMode()
                && !storage.canInsertStock(selected)) {
            throw failureWithChat(ShopResultCode.STORAGE_FULL,
                    "command.futureshops.shop.storage_full_sell");
        }
        List<PlayerShopItemLot> lots = captureLots(packet.requestId(),
                "buyback.input", selected, snapshot.outputs().get(0));
        assembly.addBuybackInputs(lots, storage,
                shop.isAdminShopMode());
        assembly.addBuybackMoney(total, ownerBalance,
                shop.isAdminShopMode());
        PlayerShopEscrowIntent intent = assembly.build();
        storage.bind(intent);
        return new BuybackQuote(intent, storage, listing, total,
                shopIdForEvent);
    }

    private static void finishPurchase(
            ServerPlayer buyer,
            BlockPos pos,
            PurchaseQuote quote,
            PlayerShopEscrowOrchestrator.Result result
    ) {
        switch (result.status()) {
            case COMMITTED -> {
                String tradeType = quote.selection().eventType();
                String note = quote.selection().needsBarter()
                        && quote.barterItem() != null
                        ? "paid=" + itemId(quote.barterItem()) + "."
                        + quote.barterAmount()
                        : "PLAYER_SHOP_ESCROW";
                TransactionHistoryService.record(buyer,
                        result.commit().committedIntent().shopIdentity()
                                .shopId(),
                        tradeType, quote.listing().itemId(),
                        result.commit().committedIntent().requestedUnits(),
                        quote.selection().method()
                                == PlayerShopTradeMethod.BARTER
                                ? 0L : quote.cost(),
                        note,
                        PlayerShopBlockService.listingNbtJson(
                                quote.listing()));
                firePurchasePost(buyer, quote);
                PlayerShopBlockService.openFor(buyer, pos, false);
                PlayerShopBlockService.sendResult(buyer, true,
                        ShopResultCode.BOUGHT);
            }
            case REPLAYED, COMMITTED_WITH_PENDING_DELIVERY -> {
                PlayerShopBlockService.openFor(buyer, pos, false);
                PlayerShopBlockService.sendResult(buyer, true,
                        ShopResultCode.BOUGHT);
            }
            case REJECTED -> PlayerShopBlockService.sendResult(buyer,
                    false, mapRejected(result.detail(), false));
            case CONFLICT -> PlayerShopBlockService.sendResult(buyer,
                    false, ShopResultCode.INVALID_REQUEST);
            case RECOVERY_REQUIRED, QUARANTINED ->
                    PlayerShopBlockService.sendResult(buyer, false,
                            ShopResultCode.SERVER_ERROR);
        }
    }

    private static void finishExistingPurchase(
            ServerPlayer buyer,
            BlockPos pos,
            PlayerShopEscrowOrchestrator.Result result
    ) {
        switch (result.status()) {
            case COMMITTED, REPLAYED, COMMITTED_WITH_PENDING_DELIVERY -> {
                PlayerShopBlockService.openFor(buyer, pos, false);
                PlayerShopBlockService.sendResult(buyer, true,
                        ShopResultCode.BOUGHT);
            }
            case REJECTED -> PlayerShopBlockService.sendResult(buyer,
                    false, mapRejected(result.detail(), false));
            case CONFLICT -> PlayerShopBlockService.sendResult(buyer,
                    false, ShopResultCode.INVALID_REQUEST);
            case RECOVERY_REQUIRED, QUARANTINED ->
                    PlayerShopBlockService.sendResult(buyer, false,
                            ShopResultCode.SERVER_ERROR);
        }
    }

    private static void finishBuyback(
            ServerPlayer seller,
            BlockPos pos,
            BuybackQuote quote,
            PlayerShopEscrowOrchestrator.Result result
    ) {
        switch (result.status()) {
            case COMMITTED -> {
                String source = result.commit().committedIntent()
                        .operation() == PlayerShopOperation.ADMIN_BUYBACK
                        ? "ADMIN_SHOP_BUYBACK_ESCROW"
                        : "BUYBACK_ESCROW";
                PlayerShopBlockService.recordSellHistory(seller,
                        currentShop(seller, pos), quote.listing(),
                        result.commit().committedIntent().requestedUnits(),
                        quote.total(), source);
                PlayerShopBlockService.firePostSellEvent(seller,
                        quote.shopEventId(), quote.listing().itemId(),
                        result.commit().committedIntent().requestedUnits(),
                        quote.total(), BalanceManager.getProvider());
                PlayerShopBlockService.openFor(seller, pos, false);
                PlayerShopBlockService.sendResultWithChat(seller, true,
                        ShopResultCode.SOLD,
                        Component.translatable(
                                "command.futureshops.shop.sold_to_shop"));
            }
            case REPLAYED, COMMITTED_WITH_PENDING_DELIVERY -> {
                PlayerShopBlockService.openFor(seller, pos, false);
                PlayerShopBlockService.sendResult(seller, true,
                        ShopResultCode.SOLD);
            }
            case REJECTED -> PlayerShopBlockService.sendResult(seller,
                    false, mapRejected(result.detail(), true));
            case CONFLICT -> PlayerShopBlockService.sendResult(seller,
                    false, ShopResultCode.INVALID_REQUEST);
            case RECOVERY_REQUIRED, QUARANTINED ->
                    PlayerShopBlockService.sendResult(seller, false,
                            ShopResultCode.SERVER_ERROR);
        }
    }

    private static void finishExistingBuyback(
            ServerPlayer seller,
            BlockPos pos,
            PlayerShopEscrowOrchestrator.Result result
    ) {
        switch (result.status()) {
            case COMMITTED, REPLAYED, COMMITTED_WITH_PENDING_DELIVERY -> {
                PlayerShopBlockService.openFor(seller, pos, false);
                PlayerShopBlockService.sendResult(seller, true,
                        ShopResultCode.SOLD);
            }
            case REJECTED -> PlayerShopBlockService.sendResult(seller,
                    false, mapRejected(result.detail(), true));
            case CONFLICT -> PlayerShopBlockService.sendResult(seller,
                    false, ShopResultCode.INVALID_REQUEST);
            case RECOVERY_REQUIRED, QUARANTINED ->
                    PlayerShopBlockService.sendResult(seller, false,
                            ShopResultCode.SERVER_ERROR);
        }
    }

    private static void firePurchasePost(
            ServerPlayer buyer, PurchaseQuote quote) {
        if (!Config.eventsTransactionEnabled) return;
        long amount = quote.selection().method()
                == PlayerShopTradeMethod.BARTER ? 0L : quote.cost();
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                new com.enviouse.futureshops.event
                        .ShopTransactionEvent.Post(
                        buyer.getUUID(), quote.shopEventId(),
                        quote.listing().itemId(),
                        quote.intent().requestedUnits(),
                        quote.selection().eventType(), amount,
                        BalanceManager.getProvider().getBalance(
                                buyer.getUUID())));
        if (quote.selection().needsBarter()) {
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                    new com.enviouse.futureshops.event
                            .BarterTradeEvent.Post(
                            buyer.getUUID(), quote.shopEventId(),
                            quote.listing().itemId()
                                    + (quote.selection().compound()
                                    ? ".compound" : ".barter"),
                            quote.listing().itemId(),
                            quote.intent().requestedUnits(),
                            List.of(new com.enviouse.futureshops.event
                                    .BarterTradeEvent.IngredientEntry(
                                    quote.listing().barterItemId(),
                                    quote.barterAmount()))));
        }
    }

    private static PlayerShopListingSnapshot captureListing(
            ShopBlockEntity shop,
            ShopBlockEntity.Listing listing,
            int listingIndex,
            boolean buybackShape,
            @Nullable Long buybackPriceOverride,
            @Nullable Boolean promotionActiveOverride
    ) {
        List<PlayerShopListingSnapshot.ItemTemplate> outputs =
                new ArrayList<>();
        if (buybackShape || listing.bundleOutputs().isEmpty()) {
            Item item = resolveItem(listing.itemId());
            outputs.add(template(item, listing.baseQuantity(),
                    listing.nbtAware(), listing.nbtTag()));
        } else {
            for (ShopBlockEntity.BundleEntry entry
                    : listing.bundleOutputs()) {
                outputs.add(template(resolveItem(entry.itemId()),
                        entry.count(), entry.nbtTag() != null,
                        entry.nbtTag()));
            }
        }
        PlayerShopListingSnapshot.ItemTemplate barter = null;
        int barterUnits = 0;
        if (listing.tradeMode() != ShopBlockEntity.TradeMode.MONEY) {
            Item barterItem = resolveItem(listing.barterItemId());
            barter = template(barterItem,
                    Math.max(1, listing.barterItemCount()),
                    listing.barterNbtAware(), listing.barterNbtTag());
            barterUnits = Math.max(1,
                    listing.effectiveBarterItemCount());
        }
        ShopBlockEntity.Promo promo = listing.promo();
        boolean active = promotionActiveOverride != null
                ? promotionActiveOverride : promo.active();
        return PlayerShopListingSnapshot.capture(
                "listing." + listingIndex,
                listingIndex,
                switch (listing.direction()) {
                    case SELL -> PlayerShopListingSnapshot.Direction.SELL;
                    case BUY -> PlayerShopListingSnapshot.Direction.BUY;
                    case BOTH -> PlayerShopListingSnapshot.Direction.BOTH;
                },
                switch (listing.tradeMode()) {
                    case MONEY -> PlayerShopListingSnapshot
                            .ConfiguredTradeMode.MONEY;
                    case BARTER -> PlayerShopListingSnapshot
                            .ConfiguredTradeMode.BARTER;
                    case BOTH -> PlayerShopListingSnapshot
                            .ConfiguredTradeMode.BOTH;
                    case MONEY_AND_BARTER -> PlayerShopListingSnapshot
                            .ConfiguredTradeMode.MONEY_AND_BARTER;
                },
                listing.baseQuantity(), listing.moneyPriceMinor(), barter,
                barterUnits,
                buybackPriceOverride == null
                        ? listing.buybackPriceMinor()
                        : buybackPriceOverride,
                listing.buybackCap(), listing.buybackBought(), outputs,
                new PlayerShopListingSnapshot.PromotionSnapshot(
                        promo.configured() ? promo.promoType() : "",
                        promo.promoValue(), promo.buyX(), promo.buyY(),
                        promo.startEpochSeconds(), promo.endEpochSeconds(),
                        promo.flash(), active),
                listing.hidden(), listing.showcase(),
                shop.isAdminShopMode());
    }

    private static PlayerShopListingSnapshot.ItemTemplate template(
            Item item, int units, boolean exact,
            @Nullable CompoundTag tag) {
        ItemStack stack = new ItemStack(item, 1);
        if (exact && tag != null) stack.setTag(tag.copy());
        return new PlayerShopListingSnapshot.ItemTemplate(itemId(item),
                units, exact ? PlayerShopItemMatchMode.EXACT
                : PlayerShopItemMatchMode.ITEM_ONLY,
                ItemStackSnapshotCodec.encode(stack));
    }

    private static PlayerShopIdentity captureIdentity(
            ServerPlayer actor, ShopBlockEntity shop, BlockPos pos) {
        shop.reconcileRegistryIdentity();
        UUID registryId = shop.getRegistryShopId();
        if (registryId == null || shop.getOwnerUuid() == null) {
            throw failure(ShopResultCode.UNCONFIGURED);
        }
        String shopId = shop.getShopId();
        if (shopId == null || shopId.isBlank() || shopId.length() > 160) {
            shopId = "player_shop." + registryId;
        }
        return new PlayerShopIdentity(registryId,
                shop.getRegistryIdentityRevision(), shopId,
                actor.level().dimension().location().toString(),
                pos.getX(), pos.getY(), pos.getZ(),
                shop.getOwnerUuid());
    }

    private static void requireConfigured(
            ShopBlockEntity shop,
            @Nullable ShopBlockEntity.Listing listing,
            ServerPlayer actor,
            boolean purchase
    ) {
        if (shop.getOwnerUuid() == null || listing == null
                || listing.itemId().isBlank()
                || listing.baseQuantity() <= 0) {
            throw failure(ShopResultCode.UNCONFIGURED);
        }
        boolean privileged = shop.getOwnerUuid().equals(actor.getUUID());
        if ((listing.hidden() || listing.showcase()) && !privileged) {
            throw failure(ShopResultCode.UNCONFIGURED);
        }
        if (purchase && listing.direction()
                == ShopBlockEntity.Direction.BUY) {
            throw failure(ShopResultCode.UNCONFIGURED);
        }
    }

    private static TradeSelection selectTrade(
            ShopBlockEntity.Listing listing,
            String paymentMethod,
            PaymentSource source
    ) {
        return switch (listing.tradeMode()) {
            case MONEY -> new TradeSelection(PlayerShopTradeMethod.MONEY,
                    true, false, false, "BUY", source);
            case BARTER -> new TradeSelection(
                    PlayerShopTradeMethod.BARTER, false, true, false,
                    "BARTER", source);
            case MONEY_AND_BARTER -> new TradeSelection(
                    PlayerShopTradeMethod.MONEY_AND_BARTER, true, true,
                    true, "MONEY_AND_BARTER", source);
            case BOTH -> {
                if ("MONEY".equalsIgnoreCase(paymentMethod)) {
                    yield new TradeSelection(PlayerShopTradeMethod.MONEY,
                            true, false, false, "BUY", source);
                }
                if ("BARTER".equalsIgnoreCase(paymentMethod)) {
                    yield new TradeSelection(PlayerShopTradeMethod.BARTER,
                            false, true, false, "BARTER", source);
                }
                throw failure(ShopResultCode.INVALID_REQUEST);
            }
        };
    }

    private static List<ItemStack> selectInventoryStacks(
            ServerPlayer player, Item item, int amount,
            boolean nbtAware, @Nullable CompoundTag requiredTag) {
        if (amount <= 0) return List.of();
        List<ItemStack> selected = new ArrayList<>();
        int remaining = amount;
        for (ItemStack stack : player.getInventory().items) {
            remaining = selectFromStack(selected, stack, item,
                    remaining, nbtAware, requiredTag);
            if (remaining == 0) break;
        }
        if (remaining > 0) {
            for (ItemStack stack : player.getInventory().offhand) {
                remaining = selectFromStack(selected, stack, item,
                        remaining, nbtAware, requiredTag);
                if (remaining == 0) break;
            }
        }
        return remaining == 0 ? List.copyOf(selected) : List.of();
    }

    private static int selectFromStack(
            List<ItemStack> selected,
            ItemStack stack,
            Item item,
            int remaining,
            boolean nbtAware,
            @Nullable CompoundTag requiredTag
    ) {
        if (remaining <= 0 || !NbtMatchUtil.matches(stack, item,
                nbtAware, requiredTag)) return remaining;
        int take = Math.min(remaining, stack.getCount());
        ItemStack copy = stack.copy();
        copy.setCount(take);
        selected.add(copy);
        return remaining - take;
    }

    private static List<ItemStack> splitExact(
            Item item, int amount, @Nullable CompoundTag tag) {
        List<ItemStack> result = new ArrayList<>();
        int remaining = amount;
        int maximum = Math.max(1, Math.min(Byte.MAX_VALUE,
                item.getMaxStackSize()));
        while (remaining > 0) {
            int count = Math.min(remaining, maximum);
            ItemStack stack = new ItemStack(item, count);
            if (tag != null) stack.setTag(tag.copy());
            result.add(stack);
            remaining -= count;
        }
        return List.copyOf(result);
    }

    private static List<PlayerShopItemLot> captureLots(
            UUID requestId,
            String sourceKey,
            List<ItemStack> stacks,
            PlayerShopListingSnapshot.ItemTemplate template
    ) {
        List<ItemStack> portions = new ArrayList<>();
        for (ItemStack input : stacks) {
            if (input == null || input.isEmpty()
                    || !itemId(input.getItem()).equals(template.itemId())) {
                throw failure(ShopResultCode.INVALID_ITEM);
            }
            ItemStack remaining = input.copy();
            while (!remaining.isEmpty()) {
                int count = Math.min(Byte.MAX_VALUE,
                        remaining.getCount());
                ItemStack portion = remaining.copy();
                portion.setCount(count);
                portions.add(portion);
                remaining.shrink(count);
            }
        }
        if (portions.isEmpty()) {
            throw failure(ShopResultCode.MISSING_ITEMS);
        }
        List<PlayerShopItemLot> lots = new ArrayList<>();
        for (int index = 0; index < portions.size(); index++) {
            ItemStack portion = portions.get(index);
            lots.add(PlayerShopItemLot.captureRaw(requestId, sourceKey,
                    index, portions.size(), itemId(portion.getItem()),
                    portion.getCount(), template.matchMode(),
                    template.canonicalOneCountTemplate(),
                    ItemStackSnapshotCodec.encode(portion)));
        }
        return List.copyOf(lots);
    }

    private static Item resolveItem(String id) {
        Item item;
        try {
            item = ShopTransactionUtil.resolveItem(id);
        } catch (RuntimeException exception) {
            throw failure(ShopResultCode.INVALID_ITEM);
        }
        if (item == null || item == Items.AIR) {
            throw failure(ShopResultCode.INVALID_ITEM);
        }
        return item;
    }

    private static String itemId(Item item) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        if (id == null) throw failure(ShopResultCode.INVALID_ITEM);
        return id.toString();
    }

    private static ShopBlockEntity currentShop(
            ServerPlayer player, BlockPos pos) {
        if (player.level().getBlockEntity(pos)
                instanceof ShopBlockEntity shop) return shop;
        throw failure(ShopResultCode.INVALID_TARGET);
    }

    private static boolean samePosition(
            PlayerShopIdentity identity,
            ServerPlayer player,
            BlockPos pos
    ) {
        return identity.dimensionId().equals(
                player.level().dimension().location().toString())
                && identity.blockX() == pos.getX()
                && identity.blockY() == pos.getY()
                && identity.blockZ() == pos.getZ();
    }

    private static ShopResultCode mapRejected(
            String detail, boolean buyback) {
        String value = detail == null ? "" : detail.toLowerCase();
        if (value.contains("stock")) return ShopResultCode.OUT_OF_STOCK;
        if (value.contains("physical")) {
            return ShopResultCode.INSUFFICIENT_PHYSICAL_FUNDS;
        }
        if (value.contains("wallet")) {
            return buyback ? ShopResultCode.SHOP_OUT_OF_MONEY
                    : ShopResultCode.INSUFFICIENT_FUNDS;
        }
        if (value.contains("input item")) {
            return buyback ? ShopResultCode.MISSING_ITEMS
                    : ShopResultCode.MISSING_BARTER_ITEMS;
        }
        return ShopResultCode.SERVER_ERROR;
    }

    private static UUID deterministic(
            UUID requestId, String key) {
        return UUID.nameUUIDFromBytes(("futureshops.player.shop."
                + requestId + "." + key).getBytes(
                StandardCharsets.UTF_8));
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(
                    "SHA-256").digest(value.getBytes(
                    StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static QuoteFailure failure(ShopResultCode code) {
        return new QuoteFailure(code, null);
    }

    private static QuoteFailure failureWithChat(
            ShopResultCode code, String translationKey) {
        return new QuoteFailure(code,
                Component.translatable(translationKey));
    }

    private record PurchaseQuote(
            PlayerShopEscrowIntent intent,
            LiveStorageAccess storage,
            ShopBlockEntity.Listing listing,
            TradeSelection selection,
            long cost,
            Item barterItem,
            int barterAmount,
            String shopEventId
    ) {
    }

    private record BuybackQuote(
            PlayerShopEscrowIntent intent,
            LiveStorageAccess storage,
            ShopBlockEntity.Listing listing,
            long total,
            String shopEventId
    ) {
    }

    private record TradeSelection(
            PlayerShopTradeMethod method,
            boolean needsMoney,
            boolean needsBarter,
            boolean compound,
            String eventType,
            PaymentSource requestedSource
    ) {
    }

    private static final class QuoteFailure extends RuntimeException {
        private final ShopResultCode code;
        private final Component chat;

        private QuoteFailure(ShopResultCode code, Component chat) {
            this.code = Objects.requireNonNull(code, "code");
            this.chat = chat;
        }

        private void send(ServerPlayer player) {
            if (chat == null) {
                PlayerShopBlockService.sendResult(player, false, code);
            } else {
                PlayerShopBlockService.sendResultWithChat(player, false,
                        code, chat);
            }
        }
    }

    private static final class IntentAssembly {
        private final UUID requestId;
        private final UUID actorId;
        private final UUID ownerId;
        private final PlayerShopIdentity identity;
        private final PlayerShopOperation operation;
        private final PlayerShopTradeMethod tradeMethod;
        private final PlayerShopPaymentSource paymentSource;
        private final int units;
        private final PlayerShopListingSnapshot listing;
        private final Instant quotedAt = Instant.now();
        private final List<PlayerShopMoneyTransfer> money =
                new ArrayList<>();
        private final List<PlayerShopItemTransfer> items =
                new ArrayList<>();
        private final List<PlayerShopClaimPlan> claims =
                new ArrayList<>();
        private final List<PlayerShopStorageMutationPlan> storage =
                new ArrayList<>();

        private IntentAssembly(
                UUID requestId,
                UUID actorId,
                UUID ownerId,
                PlayerShopIdentity identity,
                PlayerShopOperation operation,
                PlayerShopTradeMethod tradeMethod,
                PlayerShopPaymentSource paymentSource,
                int units,
                PlayerShopListingSnapshot listing
        ) {
            this.requestId = requestId;
            this.actorId = actorId;
            this.ownerId = ownerId;
            this.identity = identity;
            this.operation = operation;
            this.tradeMethod = tradeMethod;
            this.paymentSource = paymentSource;
            this.units = units;
            this.listing = listing;
        }

        private PlayerShopEscrowIntent previewIntent() {
            return PlayerShopEscrowIntent.prepared(requestId, actorId,
                    ownerId, identity, operation, tradeMethod,
                    paymentSource, units, quotedAt, listing, money, items,
                    claims, storage);
        }

        private void addPurchaseOutputs(
                List<PlayerShopItemLot> lots,
                LiveStorageAccess access,
                boolean admin
        ) {
            for (PlayerShopItemLot lot : lots) {
                String key = "purchase.output." + items.size();
                PlayerShopClaimPlan claim = PlayerShopClaimPlan.item(
                        requestId, key, actorId, lot,
                        "Player shop purchase output");
                claims.add(claim);
                UUID transferId = deterministic(requestId,
                        "item.transfer." + key);
                PlayerShopAssetEndpoint source = admin
                        ? PlayerShopAssetEndpoint.system(
                        PlayerShopAssetEndpoint.Kind.ADMIN_MINT,
                        "player.shop.admin")
                        : PlayerShopAssetEndpoint.participant(
                        PlayerShopAssetEndpoint.Kind.LINKED_STOCK,
                        ownerId, access.stockEndpoint().stableKey());
                PlayerShopItemTransfer transfer =
                        new PlayerShopItemTransfer(transferId, source,
                                PlayerShopAssetEndpoint.participant(
                                        PlayerShopAssetEndpoint.Kind
                                                .ITEM_CLAIM,
                                        actorId,
                                        claim.claimId().toString()), lot);
                items.add(transfer);
                if (!admin) {
                    storage.add(access.extraction(storage.size(),
                            transfer, lot));
                }
            }
        }

        private void addPurchaseInputs(
                List<PlayerShopItemLot> lots,
                LiveStorageAccess access,
                boolean admin
        ) {
            for (PlayerShopItemLot lot : lots) {
                String key = "purchase.input." + items.size();
                UUID transferId = deterministic(requestId,
                        "item.transfer." + key);
                PlayerShopAssetEndpoint destination;
                PlayerShopClaimPlan claim = null;
                if (admin) {
                    destination = PlayerShopAssetEndpoint.system(
                            PlayerShopAssetEndpoint.Kind.ADMIN_SINK,
                            "player.shop.admin");
                } else {
                    claim = PlayerShopClaimPlan.item(requestId, key,
                            ownerId, lot,
                            "Player shop barter proceeds");
                    claims.add(claim);
                    destination = PlayerShopAssetEndpoint.participant(
                            PlayerShopAssetEndpoint.Kind.ITEM_CLAIM,
                            ownerId, claim.claimId().toString());
                }
                PlayerShopItemTransfer transfer =
                        new PlayerShopItemTransfer(transferId,
                                PlayerShopAssetEndpoint.participant(
                                        PlayerShopAssetEndpoint.Kind
                                                .ACTOR_INVENTORY,
                                        actorId, "player.inventory"),
                                destination, lot);
                items.add(transfer);
                if (!admin) {
                    storage.add(access.insertion(storage.size(),
                            transfer, claim.claimId(), lot, true));
                }
            }
        }

        private void addPurchaseMoney(
                long amount, long sourceBefore, boolean admin) {
            String key = "purchase.money";
            UUID transferId = deterministic(requestId,
                    "money.transfer." + key);
            PlayerShopAssetEndpoint source =
                    PlayerShopAssetEndpoint.participant(
                            paymentSource
                                    == PlayerShopPaymentSource.INVENTORY_CASH
                                    ? PlayerShopAssetEndpoint.Kind.ACTOR_CASH
                                    : PlayerShopAssetEndpoint.Kind
                                    .ACTOR_WALLET,
                            actorId, paymentSource.name());
            PlayerShopAssetEndpoint destination;
            if (admin) {
                destination = PlayerShopAssetEndpoint.system(
                        PlayerShopAssetEndpoint.Kind.ADMIN_SINK,
                        "player.shop.admin");
            } else {
                PlayerShopClaimPlan claim = PlayerShopClaimPlan.money(
                        requestId, key, ownerId, amount,
                        "Player shop sale proceeds");
                claims.add(claim);
                destination = PlayerShopAssetEndpoint.participant(
                        PlayerShopAssetEndpoint.Kind.MONEY_CLAIM, ownerId,
                        claim.claimId().toString());
            }
            money.add(new PlayerShopMoneyTransfer(transferId, source,
                    destination, amount, paymentSource, sourceBefore,
                    PlayerShopMoneyTransfer.BALANCE_NOT_APPLICABLE));
        }

        private void addBuybackInputs(
                List<PlayerShopItemLot> lots,
                LiveStorageAccess access,
                boolean admin
        ) {
            for (PlayerShopItemLot lot : lots) {
                String key = "buyback.input." + items.size();
                UUID transferId = deterministic(requestId,
                        "item.transfer." + key);
                PlayerShopAssetEndpoint destination;
                PlayerShopClaimPlan claim = null;
                if (admin) {
                    destination = PlayerShopAssetEndpoint.system(
                            PlayerShopAssetEndpoint.Kind.ADMIN_SINK,
                            "player.shop.admin");
                } else {
                    claim = PlayerShopClaimPlan.item(requestId, key,
                            ownerId, lot,
                            "Player shop buyback stock");
                    claims.add(claim);
                    destination = PlayerShopAssetEndpoint.participant(
                            PlayerShopAssetEndpoint.Kind.ITEM_CLAIM,
                            ownerId, claim.claimId().toString());
                }
                PlayerShopItemTransfer transfer =
                        new PlayerShopItemTransfer(transferId,
                                PlayerShopAssetEndpoint.participant(
                                        PlayerShopAssetEndpoint.Kind
                                                .ACTOR_INVENTORY,
                                        actorId, "player.inventory"),
                                destination, lot);
                items.add(transfer);
                if (!admin) {
                    storage.add(access.insertion(storage.size(),
                            transfer, claim.claimId(), lot, false));
                }
            }
        }

        private void addBuybackMoney(
                long amount, long ownerBalance, boolean admin) {
            String key = "buyback.money";
            PlayerShopClaimPlan claim = PlayerShopClaimPlan.money(
                    requestId, key, actorId, amount,
                    "Player shop buyback payment");
            claims.add(claim);
            PlayerShopAssetEndpoint source = admin
                    ? PlayerShopAssetEndpoint.system(
                    PlayerShopAssetEndpoint.Kind.ADMIN_MINT,
                    "player.shop.admin")
                    : PlayerShopAssetEndpoint.participant(
                    PlayerShopAssetEndpoint.Kind.OWNER_WALLET,
                    ownerId, "player.wallet");
            money.add(new PlayerShopMoneyTransfer(
                    deterministic(requestId, "money.transfer." + key),
                    source,
                    PlayerShopAssetEndpoint.participant(
                            PlayerShopAssetEndpoint.Kind.MONEY_CLAIM,
                            actorId, claim.claimId().toString()),
                    amount, PlayerShopPaymentSource.NONE, ownerBalance,
                    PlayerShopMoneyTransfer.BALANCE_NOT_APPLICABLE));
        }

        private PlayerShopEscrowIntent build() {
            return PlayerShopEscrowIntent.prepared(requestId, actorId,
                    ownerId, identity, operation, tradeMethod,
                    paymentSource, units, quotedAt, listing, money, items,
                    claims, storage);
        }
    }

    private static final class LiveStorageAccess
            implements PlayerShopLiveEscrowService.StorageAccess {
        private final ServerPlayer actor;
        private final BlockPos shopPos;
        private final int listingIndex;
        private PlayerShopEscrowIntent intent;
        private final Map<String, Integer> plannedCounts =
                new HashMap<>();

        private LiveStorageAccess(
                ServerPlayer actor,
                BlockPos shopPos,
                int listingIndex,
                PlayerShopEscrowIntent intent
        ) {
            this.actor = actor;
            this.shopPos = shopPos.immutable();
            this.listingIndex = listingIndex;
            this.intent = intent;
        }

        private void bind(PlayerShopEscrowIntent value) {
            intent = Objects.requireNonNull(value, "value");
        }

        private List<PlayerShopBlockService.LinkedStorage>
        stockStorages() {
            ShopBlockEntity shop = shop();
            if (shop == null) return List.of();
            return PlayerShopBlockService.resolveLinkedStorages(
                    actor.level(), shop, shopPos);
        }

        private List<PlayerShopBlockService.LinkedStorage>
        barterStorages() {
            ShopBlockEntity shop = shop();
            if (shop == null) return List.of();
            PlayerShopBlockService.LinkedStorage storage =
                    PlayerShopBlockService.resolveBarterStorage(
                            actor.level(), shop, shopPos);
            return storage == null ? List.of() : List.of(storage);
        }

        private PlayerShopStorageEndpoint stockEndpoint() {
            return endpoint(stockStorages(), "futureshops.stock");
        }

        private PlayerShopStorageEndpoint barterEndpoint() {
            return endpoint(barterStorages(), "futureshops.barter");
        }

        private PlayerShopStorageEndpoint endpoint(
                List<PlayerShopBlockService.LinkedStorage> storages,
                String adapterId
        ) {
            if (storages.isEmpty()) {
                throw failure(ShopResultCode.NO_LINK);
            }
            PlayerShopBlockService.LinkedStorage first = storages.get(0);
            return new PlayerShopStorageEndpoint(
                    actor.level().dimension().location().toString(),
                    first.pos().getX(), first.pos().getY(),
                    first.pos().getZ(), 0,
                    intent.shopIdentity().identityRevision(), adapterId);
        }

        private PlayerShopStorageMutationPlan extraction(
                int sequence,
                PlayerShopItemTransfer transfer,
                PlayerShopItemLot lot
        ) {
            List<PlayerShopBlockService.LinkedStorage> storages =
                    stockStorages();
            PlayerShopStorageEndpoint endpoint = stockEndpoint();
            Expected expected = nextExpected(endpoint, lot, storages,
                    false);
            return PlayerShopStorageMutationPlan.extraction(
                    intent.requestId(), sequence, endpoint,
                    transfer.transferId(), lot, expected.plan());
        }

        private PlayerShopStorageMutationPlan insertion(
                int sequence,
                PlayerShopItemTransfer transfer,
                UUID claimId,
                PlayerShopItemLot lot,
                boolean barter
        ) {
            List<PlayerShopBlockService.LinkedStorage> storages = barter
                    ? barterStorages() : stockStorages();
            PlayerShopStorageEndpoint endpoint = barter
                    ? barterEndpoint() : stockEndpoint();
            Expected expected = nextExpected(endpoint, lot, storages, true);
            return PlayerShopStorageMutationPlan.insertion(
                    intent.requestId(), sequence, endpoint,
                    transfer.transferId(), claimId, lot,
                    expected.plan());
        }

        private Expected nextExpected(
                PlayerShopStorageEndpoint endpoint,
                PlayerShopItemLot lot,
                List<PlayerShopBlockService.LinkedStorage> storages,
                boolean insertion
        ) {
            String key = stateKey(endpoint, lot, storages);
            int before = plannedCounts.computeIfAbsent(key,
                    ignored -> countExact(storages, lot));
            int after;
            try {
                after = insertion
                        ? Math.addExact(before, lot.quantity())
                        : Math.subtractExact(before, lot.quantity());
            } catch (ArithmeticException exception) {
                throw failure(ShopResultCode.INVALID_AMOUNT);
            }
            if (after < 0) throw failure(ShopResultCode.OUT_OF_STOCK);
            plannedCounts.put(key, after);
            return Expected.create(before, after, key);
        }

        private boolean canInsertStock(List<ItemStack> stacks) {
            List<PlayerShopBlockService.LinkedStorage> storages =
                    stockStorages();
            return !storages.isEmpty()
                    && PlayerShopBlockService.canInsertComposite(storages,
                    stacks);
        }

        private boolean canInsertBarter(List<ItemStack> stacks) {
            List<PlayerShopBlockService.LinkedStorage> storages =
                    barterStorages();
            return !storages.isEmpty()
                    && PlayerShopBlockService.canInsertComposite(storages,
                    stacks);
        }

        @Override
        public boolean revalidate(PlayerShopEscrowIntent expectedIntent) {
            if (!intent.intentFingerprint().equals(
                    expectedIntent.intentFingerprint())) return false;
            ShopBlockEntity shop = shop();
            if (shop == null || shop.getOwnerUuid() == null
                    || !shop.getOwnerUuid().equals(expectedIntent.ownerId())
                    || shop.getRegistryShopId() == null
                    || !shop.getRegistryShopId().equals(
                    expectedIntent.shopIdentity().registryShopId())
                    || shop.getRegistryIdentityRevision()
                    != expectedIntent.shopIdentity().identityRevision()) {
                return false;
            }
            ShopBlockEntity.Listing current = shop.getListing(listingIndex);
            if (current == null || expectedIntent.listing() == null) {
                return false;
            }
            PlayerShopListingSnapshot quoted = expectedIntent.listing();
            try {
                PlayerShopListingSnapshot currentSnapshot = captureListing(
                        shop, current, listingIndex,
                        expectedIntent.operation() == PlayerShopOperation
                                .BUYBACK
                                || expectedIntent.operation()
                                == PlayerShopOperation.ADMIN_BUYBACK,
                        quoted.buybackPriceMinorUnits(),
                        quoted.promotion().activeAtQuote());
                currentSnapshot = new PlayerShopListingSnapshot(
                        currentSnapshot.listingId(),
                        currentSnapshot.listingIndex(),
                        currentSnapshot.direction(),
                        currentSnapshot.configuredTradeMode(),
                        currentSnapshot.baseQuantity(),
                        currentSnapshot.moneyPriceMinorUnits(),
                        currentSnapshot.barterTemplate(),
                        currentSnapshot.barterUnitsPerPurchase(),
                        currentSnapshot.buybackPriceMinorUnits(),
                        currentSnapshot.buybackCap(),
                        quoted.buybackBought(),
                        currentSnapshot.outputs(),
                        currentSnapshot.promotion(),
                        currentSnapshot.hidden(),
                        currentSnapshot.showcase(),
                        currentSnapshot.adminShop(),
                        quoted.revisionFingerprint());
                if (!currentSnapshot.equals(quoted)) return false;
            } catch (RuntimeException exception) {
                return false;
            }
            if (expectedIntent.operation() == PlayerShopOperation.BUYBACK
                    || expectedIntent.operation()
                    == PlayerShopOperation.ADMIN_BUYBACK) {
                int before = quoted.buybackBought();
                int target;
                try {
                    target = Math.addExact(before,
                            expectedIntent.requestedUnits());
                } catch (ArithmeticException exception) {
                    return false;
                }
                int actual = current.buybackBought();
                return actual == before || actual >= target;
            }
            return current.buybackBought() == quoted.buybackBought();
        }

        @Override
        public boolean canExtract(PlayerShopStorageMutationPlan plan) {
            if (plan.direction()
                    != PlayerShopStorageMutationPlan.Direction.EXTRACT) {
                return true;
            }
            Expected expected = Expected.parse(
                    plan.expectedStateFingerprint());
            List<PlayerShopBlockService.LinkedStorage> storages =
                    storages(plan);
            return !storages.isEmpty()
                    && expected.matches(plan.endpoint(), plan.lot(),
                    storages)
                    && countExact(storages, plan.lot())
                    == expected.before();
        }

        @Override
        public PlayerShopLiveEscrowService.StorageObservation observe(
                PlayerShopStorageMutationPlan plan) {
            Expected expected;
            try {
                expected = Expected.parse(
                        plan.expectedStateFingerprint());
            } catch (RuntimeException exception) {
                return unknown(plan);
            }
            List<PlayerShopBlockService.LinkedStorage> storages =
                    storages(plan);
            if (storages.isEmpty()
                    || !expected.matches(plan.endpoint(), plan.lot(),
                    storages)) return unknown(plan);
            int count = countExact(storages, plan.lot());
            PlayerShopLiveEscrowService.StorageState state =
                    count == expected.before()
                            ? PlayerShopLiveEscrowService.StorageState.BEFORE
                            : count == expected.after()
                            ? PlayerShopLiveEscrowService.StorageState.AFTER
                            : PlayerShopLiveEscrowService.StorageState.UNKNOWN;
            return new PlayerShopLiveEscrowService.StorageObservation(state,
                    expected.beforeState(), expected.afterState());
        }

        @Override
        public PlayerShopLiveEscrowService.StorageMutationResult extract(
                PlayerShopStorageMutationPlan plan) {
            PlayerShopLiveEscrowService.StorageObservation before =
                    observe(plan);
            if (before.state()
                    != PlayerShopLiveEscrowService.StorageState.BEFORE) {
                return storageFailure(before,
                        PlayerShopLiveEscrowService
                                .StorageMutationStatus.RECOVERY_REQUIRED,
                        "Player shop extraction preimage changed");
            }
            List<PlayerShopBlockService.LinkedStorage> storages =
                    storages(plan);
            ItemStack exact = ItemStackSnapshotCodec.decode(
                    plan.lot().serializedExactStack());
            List<ItemStack> extracted = PlayerShopBlockService
                    .extractComposite(storages, exact.getItem(),
                            plan.lot().quantity(), true, exact.getTag());
            if (!exactStacks(extracted, exact, plan.lot().quantity())) {
                for (ItemStack stack : extracted) {
                    PlayerShopBlockService.reinsertComposite(storages,
                            stack);
                }
                return storageFailure(before,
                        PlayerShopLiveEscrowService
                                .StorageMutationStatus.RECOVERY_REQUIRED,
                        "Player shop exact extraction failed");
            }
            PlayerShopLiveEscrowService.StorageObservation after =
                    observe(plan);
            if (after.state()
                    != PlayerShopLiveEscrowService.StorageState.AFTER) {
                for (ItemStack stack : extracted) {
                    PlayerShopBlockService.reinsertComposite(storages,
                            stack);
                }
                return storageFailure(before,
                        PlayerShopLiveEscrowService
                                .StorageMutationStatus.RECOVERY_REQUIRED,
                        "Player shop extraction result is uncertain");
            }
            return new PlayerShopLiveEscrowService.StorageMutationResult(
                    PlayerShopLiveEscrowService.StorageMutationStatus.APPLIED,
                    before.beforeFingerprint(), after.afterFingerprint(),
                    evidence(plan, before, after), "");
        }

        @Override
        public PlayerShopLiveEscrowService.StorageMutationResult insert(
                PlayerShopStorageMutationPlan plan,
                ItemStack stack
        ) {
            PlayerShopLiveEscrowService.StorageObservation before =
                    observe(plan);
            if (before.state()
                    != PlayerShopLiveEscrowService.StorageState.BEFORE) {
                return storageFailure(before,
                        PlayerShopLiveEscrowService
                                .StorageMutationStatus.RECOVERY_REQUIRED,
                        "Player shop insertion preimage changed");
            }
            List<PlayerShopBlockService.LinkedStorage> storages =
                    storages(plan);
            ItemStack exact = ItemStackSnapshotCodec.decode(
                    plan.lot().serializedExactStack());
            if (!sameExact(stack, exact)
                    || stack.getCount() != plan.lot().quantity()) {
                return storageFailure(before,
                        PlayerShopLiveEscrowService
                                .StorageMutationStatus.RECOVERY_REQUIRED,
                        "Player shop insertion payload changed");
            }
            if (!PlayerShopBlockService.canInsertComposite(storages,
                    List.of(stack))) {
                return storageFailure(before,
                        PlayerShopLiveEscrowService
                                .StorageMutationStatus.REJECTED,
                        "Player shop destination storage is full");
            }
            boolean inserted = PlayerShopBlockService.insertComposite(
                    storages, List.of(stack));
            PlayerShopLiveEscrowService.StorageObservation after =
                    observe(plan);
            if (!inserted || after.state()
                    != PlayerShopLiveEscrowService.StorageState.AFTER) {
                return storageFailure(after,
                        PlayerShopLiveEscrowService
                                .StorageMutationStatus.RECOVERY_REQUIRED,
                        "Player shop insertion result is uncertain");
            }
            return new PlayerShopLiveEscrowService.StorageMutationResult(
                    PlayerShopLiveEscrowService.StorageMutationStatus.APPLIED,
                    before.beforeFingerprint(), after.afterFingerprint(),
                    evidence(plan, before, after), "");
        }

        @Override
        public boolean applyBuybackCounter(
                PlayerShopEscrowIntent expectedIntent) {
            if (expectedIntent.operation() != PlayerShopOperation.BUYBACK
                    && expectedIntent.operation()
                    != PlayerShopOperation.ADMIN_BUYBACK) return true;
            ShopBlockEntity shop = shop();
            if (shop == null) return false;
            ShopBlockEntity.Listing listing = shop.getListing(listingIndex);
            if (listing == null) return false;
            int before = expectedIntent.listing().buybackBought();
            int target;
            try {
                target = Math.addExact(before,
                        expectedIntent.requestedUnits());
            } catch (ArithmeticException exception) {
                return false;
            }
            if (listing.buybackCap() > 0
                    && target > listing.buybackCap()) return false;
            if (listing.buybackBought() == before) {
                listing.setBuybackBought(target);
                shop.setChanged();
                return true;
            }
            return listing.buybackBought() >= target;
        }

        private ShopBlockEntity shop() {
            return actor.level().getBlockEntity(shopPos)
                    instanceof ShopBlockEntity value ? value : null;
        }

        private List<PlayerShopBlockService.LinkedStorage> storages(
                PlayerShopStorageMutationPlan plan) {
            return plan.endpoint().adapterId().equals(
                    "futureshops.barter")
                    ? barterStorages() : stockStorages();
        }

        private PlayerShopLiveEscrowService.StorageObservation unknown(
                PlayerShopStorageMutationPlan plan) {
            String digest = sha256(plan.expectedStateFingerprint());
            return new PlayerShopLiveEscrowService.StorageObservation(
                    PlayerShopLiveEscrowService.StorageState.UNKNOWN,
                    "s1.unknown.before." + digest,
                    "s1.unknown.after." + digest);
        }

        private PlayerShopLiveEscrowService.StorageMutationResult
        storageFailure(
                PlayerShopLiveEscrowService.StorageObservation observation,
                PlayerShopLiveEscrowService.StorageMutationStatus status,
                String detail
        ) {
            return new PlayerShopLiveEscrowService.StorageMutationResult(
                    status, observation.beforeFingerprint(),
                    observation.afterFingerprint(),
                    detail.getBytes(StandardCharsets.UTF_8), detail);
        }

        private byte[] evidence(
                PlayerShopStorageMutationPlan plan,
                PlayerShopLiveEscrowService.StorageObservation before,
                PlayerShopLiveEscrowService.StorageObservation after
        ) {
            return sha256(plan.mutationId() + "."
                    + before.beforeFingerprint() + "."
                    + after.afterFingerprint()).getBytes(
                    StandardCharsets.UTF_8);
        }
    }

    private static int countExact(
            List<PlayerShopBlockService.LinkedStorage> storages,
            PlayerShopItemLot lot
    ) {
        ItemStack stack = ItemStackSnapshotCodec.decode(
                lot.serializedExactStack());
        int total = 0;
        for (PlayerShopBlockService.LinkedStorage storage : storages) {
            total = Math.addExact(total,
                    PlayerShopBlockService.countInStorage(storage,
                            stack.getItem(), true, stack.getTag()));
        }
        return total;
    }

    private static String stateKey(
            PlayerShopStorageEndpoint endpoint,
            PlayerShopItemLot lot,
            List<PlayerShopBlockService.LinkedStorage> storages
    ) {
        ItemStack exact = ItemStackSnapshotCodec.decode(
                lot.serializedExactStack());
        exact.setCount(1);
        StringBuilder value = new StringBuilder(endpoint.stableKey())
                .append('.').append(HexFormat.of().formatHex(
                        ItemStackSnapshotCodec.encode(exact)));
        for (PlayerShopBlockService.LinkedStorage storage : storages) {
            value.append('.').append(storage.pos().asLong())
                    .append('.').append(storage.adapter() == null
                    ? "handler" : storage.adapter().getClass().getName());
        }
        return sha256(value.toString());
    }

    private static boolean exactStacks(
            List<ItemStack> stacks,
            ItemStack expected,
            int quantity
    ) {
        int total = 0;
        for (ItemStack stack : stacks) {
            if (!sameExact(stack, expected)) return false;
            total = Math.addExact(total, stack.getCount());
        }
        return total == quantity;
    }

    private static boolean sameExact(ItemStack first, ItemStack second) {
        ItemStack a = first.copy();
        ItemStack b = second.copy();
        a.setCount(1);
        b.setCount(1);
        return Arrays.equals(ItemStackSnapshotCodec.encode(a),
                ItemStackSnapshotCodec.encode(b));
    }

    private record Expected(
            int before,
            int after,
            String key,
            String plan,
            String beforeState,
            String afterState
    ) {
        private static Expected create(
                int before, int after, String key) {
            return new Expected(before, after, key,
                    "p1." + before + "." + after + "." + key,
                    "s1." + before + "." + key,
                    "s1." + after + "." + key);
        }

        private static Expected parse(String value) {
            String[] parts = value.split("\\.", 4);
            if (parts.length != 4 || !"p1".equals(parts[0])) {
                throw new IllegalArgumentException(
                        "Player shop storage plan is invalid");
            }
            int before = Integer.parseInt(parts[1]);
            int after = Integer.parseInt(parts[2]);
            if (before < 0 || after < 0 || parts[3].length() != 64) {
                throw new IllegalArgumentException(
                        "Player shop storage plan is invalid");
            }
            return create(before, after, parts[3]);
        }

        private boolean matches(
                PlayerShopStorageEndpoint endpoint,
                PlayerShopItemLot lot,
                List<PlayerShopBlockService.LinkedStorage> storages
        ) {
            return key.equals(stateKey(endpoint, lot, storages));
        }
    }
}

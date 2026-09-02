package com.enviouse.futureshops.server.transaction;

import com.enviouse.futureshops.catalog.ItemDef;
import com.enviouse.futureshops.catalog.ShopCatalog;
import com.enviouse.futureshops.config.EscrowConfig;
import com.enviouse.futureshops.event.ShopTransactionEvent;
import com.enviouse.futureshops.money.PaymentSource;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.C2SBuyRequestPacket;
import com.enviouse.futureshops.network.packets.S2CBuyResponsePacket;
import com.enviouse.futureshops.server.economy.BalanceManager;
import com.enviouse.futureshops.server.economy.EconomyProvider;
import com.enviouse.futureshops.server.economy.WalletMutationGuard;
import com.enviouse.futureshops.server.escrow.item.ExactItemClaimResolution;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.runtime.ExactItemDeliveryTickBudget;
import com.enviouse.futureshops.server.escrow.runtime.EscrowRuntimeManager;
import com.enviouse.futureshops.server.escrow.runtime.EscrowRuntimeService;
import com.enviouse.futureshops.server.escrow.runtime.ServerShopPurchaseCommit;
import com.enviouse.futureshops.server.escrow.runtime.ServerShopPurchaseService;
import com.enviouse.futureshops.server.escrow.runtime.ServerShopFundingReleaseService;
import com.enviouse.futureshops.server.escrow.stock.CatalogStockState;
import com.enviouse.futureshops.server.escrow.stock.CatalogStockStatus;
import com.enviouse.futureshops.server.escrow.stock.StockKey;
import com.enviouse.futureshops.server.pricing.DynamicPricingEngine;
import com.enviouse.futureshops.server.session.ShopSession;
import com.enviouse.futureshops.server.session.ShopSessionManager;
import com.enviouse.futureshops.server.shop.AdminShopToggleSavedData;
import com.enviouse.futureshops.server.shop.InventorySyncService;
import com.enviouse.futureshops.server.shop.ShopDataService;
import com.enviouse.futureshops.server.shop.ShopResultCode;
import com.enviouse.futureshops.server.escrow.model.DimensionAwareShopReference;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

public final class ShopBuyService {
    private static final UUID ZERO_UUID = new UUID(0L, 0L);
    private static final Logger LOGGER = LoggerFactory.getLogger(
            ShopBuyService.class);

    private ShopBuyService() {
    }

    public static void handleBuyRequest(
            ServerPlayer player,
            C2SBuyRequestPacket packet
    ) {
        BuyResult result = execute(player, packet);
        ShopPackets.sendToPlayer(player, new S2CBuyResponsePacket(
                result.success(), packet.cartCheckout(), result.shopId(),
                result.errorCode(), result.resultingBalance(),
                result.totalQuantity(), result.totalCost(),
                packet.requestId()));
        if (!result.success() || player.getServer() == null) {
            return;
        }
        for (ServerShopPurchaseCommit.Line line : result.lines()) {
            TransactionHistoryService.recordServerPurchase(
                    player.getServer(), player.getUUID(), result.shopId(),
                    result.transactionId(), line.lineIndex(), line.itemId(),
                    line.quantity(), line.lineCostMinorUnits(),
                    packet.cartCheckout() ? "CART" : "DETAIL",
                    historyNbt(line), result.occurredAt());
            if (result.replayed()) {
                continue;
            }
            DynamicPricingEngine.recordBuy(player.getServer(),
                    result.shopId(), line.listingId(), line.quantity());
            MinecraftForge.EVENT_BUS.post(new ShopTransactionEvent.Post(
                    player.getUUID(), result.shopId(), line.itemId(),
                    line.quantity(), "BUY", line.lineCostMinorUnits(),
                    result.resultingBalance()));
        }
        if (!result.replayed()) {
            InventorySyncService.sendOwnedCounts(player, result.shopId());
            ShopDataService.resendSessionsViewingShop(player.getServer(),
                    result.shopId());
        }
    }

    private static BuyResult execute(
            ServerPlayer player,
            C2SBuyRequestPacket packet
    ) {
        PaymentSource paymentSource = PaymentSource.fromWire(
                packet.paymentSource()).orElse(null);
        if (paymentSource == null || packet.requestId() == null
                || ZERO_UUID.equals(packet.requestId())) {
            return BuyResult.error(candidateShopId(packet.shopId()),
                    safeBalance(player), ShopResultCode.INVALID_REQUEST);
        }
        Map<String, Integer> mergedLines;
        try {
            mergedLines = mergeLines(packet.lineItems());
        } catch (ArithmeticException exception) {
            return BuyResult.error(candidateShopId(packet.shopId()),
                    safeBalance(player), ShopResultCode.INVALID_ITEM);
        }
        if (mergedLines.isEmpty()) {
            return BuyResult.error(candidateShopId(packet.shopId()),
                    safeBalance(player), ShopResultCode.INVALID_ITEM);
        }
        String candidateShopId = candidateShopId(packet.shopId());
        ServerShopPurchaseService.Identity identity;
        try {
            identity = identity(packet, player.getUUID(), candidateShopId,
                    paymentSource, mergedLines);
        } catch (RuntimeException exception) {
            return BuyResult.error(candidateShopId, safeBalance(player),
                    ShopResultCode.INVALID_REQUEST);
        }
        Optional<ServerShopPurchaseService.Result> replay =
                ServerShopPurchaseService.resolveReplay(identity);
        if (replay.isPresent()) {
            return mapResult(player, candidateShopId,
                    replay.orElseThrow());
        }
        if (paymentSource == PaymentSource.PHYSICAL
                && ServerShopFundingReleaseService.resolvePurchase(
                player.getUUID(), packet.requestId()).isPresent()) {
            return BuyResult.error(candidateShopId, safeBalance(player),
                    ShopResultCode.SERVER_ERROR);
        }
        String shopId = ShopDataService.resolveShopId(candidateShopId);
        if (!shopId.equals(candidateShopId)) {
            return BuyResult.error(shopId, safeBalance(player),
                    ShopResultCode.INVALID_REQUEST);
        }
        if (!freshAccessAllowed(player, shopId)) {
            return BuyResult.error(shopId, safeBalance(player),
                    ShopResultCode.SHOP_CLOSED);
        }
        ReentrantLock lock = ShopTransactionUtil.lockFor(player.getUUID());
        if (!lock.tryLock()) {
            return BuyResult.error(shopId, safeBalance(player),
                    ShopResultCode.COOLDOWN);
        }
        Optional<ServerShopPurchaseCommit.PhysicalFunding>
                physicalFunding = Optional.empty();
        try {
            Optional<WalletMutationGuard.Lease> walletGuard =
                    WalletMutationGuard.tryAcquire(List.of(
                            player.getUUID()));
            if (walletGuard.isEmpty()) {
                return BuyResult.error(shopId, safeBalance(player),
                        ShopResultCode.COOLDOWN);
            }
            try (WalletMutationGuard.Lease ignored =
                         walletGuard.orElseThrow()) {
                BalanceManager.getProvider().getBalance(player.getUUID());
                QuoteResult firstResult = prepareQuote(player, shopId,
                        mergedLines, packet.requestId());
                if (firstResult.quote().isEmpty()) {
                    return BuyResult.error(shopId, safeBalance(player),
                            firstResult.errorCode());
                }
                PreparedQuote first = firstResult.quote().orElseThrow();
                List<Long> authorizedCosts = new ArrayList<>(
                        first.lines().size());
                for (ServerShopPurchaseCommit.Line line : first.lines()) {
                    ShopTransactionEvent.Pre event =
                            new ShopTransactionEvent.Pre(player, shopId,
                                    line.itemId(), line.quantity(), "BUY",
                                    line.lineCostMinorUnits());
                    if (MinecraftForge.EVENT_BUS.post(event)) {
                        return BuyResult.error(shopId, safeBalance(player),
                                ShopResultCode.CANCELLED_BY_EVENT);
                    }
                    if (event.getPriceMinor() <= 0L) {
                        return BuyResult.error(shopId, safeBalance(player),
                                ShopResultCode.INVALID_AMOUNT);
                    }
                    authorizedCosts.add(event.getPriceMinor());
                }
                QuoteResult revalidatedResult = prepareQuote(player, shopId,
                        mergedLines, packet.requestId());
                if (revalidatedResult.quote().isEmpty()) {
                    return BuyResult.error(shopId, safeBalance(player),
                            revalidatedResult.errorCode());
                }
                PreparedQuote revalidated = revalidatedResult.quote()
                        .orElseThrow();
                if (!first.equals(revalidated)) {
                    return BuyResult.error(shopId, safeBalance(player),
                            ShopResultCode.INVALID_ITEM);
                }
                if (!freshAccessAllowed(player, shopId)) {
                    return BuyResult.error(shopId, safeBalance(player),
                            ShopResultCode.SHOP_CLOSED);
                }
                PreparedQuote authorized;
                try {
                    authorized = revalidated.withCosts(authorizedCosts);
                } catch (ArithmeticException
                         | IllegalArgumentException exception) {
                    return BuyResult.error(shopId, safeBalance(player),
                            ShopResultCode.INVALID_AMOUNT);
                }
                if (paymentSource == PaymentSource.PHYSICAL) {
                    ServerShopPhysicalFundingBridge.Result funding =
                            ServerShopPhysicalFundingBridge.fund(player,
                                    packet.requestId(),
                                    authorized.totalCostMinorUnits());
                    if (!funding.successful()) {
                        ShopResultCode code = switch (funding.status()) {
                            case INSUFFICIENT ->
                                    ShopResultCode
                                            .INSUFFICIENT_PHYSICAL_FUNDS;
                            case BUSY -> ShopResultCode.COOLDOWN;
                            case INVALID -> ShopResultCode.INVALID_AMOUNT;
                            case UNAVAILABLE, RECOVERY_REQUIRED, RELEASED ->
                                    ShopResultCode.SERVER_ERROR;
                            case FUNDED -> throw new IllegalStateException(
                                    "Physical funding status is invalid");
                        };
                        return BuyResult.error(shopId, safeBalance(player),
                                code);
                    }
                    physicalFunding = funding.funding();
                    QuoteResult afterFunding = prepareQuote(player, shopId,
                            mergedLines, packet.requestId());
                    if (afterFunding.quote().isEmpty()
                            || !first.equals(afterFunding.quote()
                            .orElseThrow())) {
                        releasePhysicalFundingIfPurchaseAbsent(
                                identity, physicalFunding);
                        return BuyResult.error(shopId, safeBalance(player),
                                ShopResultCode.SERVER_ERROR);
                    }
                    if (!freshAccessAllowed(player, shopId)) {
                        releasePhysicalFundingIfPurchaseAbsent(
                                identity, physicalFunding);
                        return BuyResult.error(shopId, safeBalance(player),
                                ShopResultCode.SERVER_ERROR);
                    }
                }
                DimensionAwareShopReference reference =
                        new DimensionAwareShopReference(shopId,
                                player.serverLevel().dimension().location()
                                        .toString(),
                                player.blockPosition().getX(),
                                player.blockPosition().getY(),
                                player.blockPosition().getZ());
                EconomyProvider provider = BalanceManager.getProvider();
                ServerShopPurchaseService.PreparedRequest request =
                        new ServerShopPurchaseService.PreparedRequest(
                                identity, authorized.lines(),
                                provider.getCurrencyName(),
                                provider.getDecimalPlaces(), reference,
                                Instant.now(), physicalFunding);
                ServerShopPurchaseService.Result purchase =
                        ServerShopPurchaseService.purchase(request);
                if (purchase.status()
                        != ServerShopPurchaseService.Status.SUCCESS
                        && physicalFunding.isPresent()) {
                    Optional<ServerShopPurchaseService.Result>
                            purchaseReplay =
                            ServerShopPurchaseService.resolveReplay(identity);
                    if (purchaseReplay.isPresent()
                            && purchaseReplay.orElseThrow().status()
                            == ServerShopPurchaseService.Status.SUCCESS) {
                        return mapResult(player, shopId,
                                purchaseReplay.orElseThrow());
                    }
                    if (purchaseReplay.isEmpty()
                            && !releasePhysicalFundingIfPurchaseAbsent(
                            identity, physicalFunding)) {
                        return BuyResult.error(shopId,
                                safeBalance(player),
                                ShopResultCode.SERVER_ERROR);
                    }
                }
                return mapResult(player, shopId, purchase);
            }
        } catch (RuntimeException exception) {
            releasePhysicalFundingIfPurchaseAbsent(identity,
                    physicalFunding);
            return BuyResult.error(shopId, safeBalance(player),
                    ShopResultCode.SERVER_ERROR);
        } finally {
            lock.unlock();
        }
    }

    private static boolean releasePhysicalFundingIfPurchaseAbsent(
            ServerShopPurchaseService.Identity identity,
            Optional<ServerShopPurchaseCommit.PhysicalFunding> funding
    ) {
        if (funding.isEmpty()) {
            return true;
        }
        Optional<ServerShopPurchaseService.Result> purchase =
                ServerShopPurchaseService.resolveReplay(identity);
        if (purchase.isPresent()) {
            return false;
        }
        return ServerShopFundingReleaseService.release(
                identity.playerId(), funding.orElseThrow()).successful();
    }

    private static QuoteResult prepareQuote(
            ServerPlayer player,
            String shopId,
            Map<String, Integer> mergedLines,
            UUID requestId
    ) {
        EscrowRuntimeService runtime;
        try {
            runtime = EscrowRuntimeManager.requireReady();
        } catch (RuntimeException exception) {
            return QuoteResult.error(ShopResultCode.SERVER_ERROR);
        }
        List<Map.Entry<String, Integer>> canonical = mergedLines.entrySet()
                .stream().sorted(Map.Entry.comparingByKey()).toList();
        List<ServerShopPurchaseCommit.Line> lines = new ArrayList<>(
                canonical.size());
        long nowSeconds = System.currentTimeMillis() / 1000L;
        for (int lineIndex = 0;
             lineIndex < canonical.size(); lineIndex++) {
            Map.Entry<String, Integer> entry = canonical.get(lineIndex);
            String listingId = entry.getKey();
            int quantity = entry.getValue();
            if (!ShopTransactionUtil.isValidBuyQuantity(quantity)) {
                return QuoteResult.error(ShopResultCode.INVALID_ITEM);
            }
            ItemDef definition = ShopCatalog.getItem(shopId,
                    listingId).orElse(null);
            if (definition == null || definition.buyPriceMinorUnits() <= 0L
                    || definition.isExpired(nowSeconds)) {
                return QuoteResult.error(ShopResultCode.INVALID_ITEM);
            }
            String itemId = definition.itemId();
            if (itemId == null || itemId.isBlank()
                    || "minecraft:air".equals(itemId)) {
                return QuoteResult.error(ShopResultCode.INVALID_ITEM);
            }
            CatalogStockState stock = runtime.stockListing(
                    new StockKey(shopId, listingId)).orElse(null);
            if (stock == null) {
                LOGGER.warn(
                        "Server shop purchase request {} has no stock listing for shop {} listing {}.",
                        requestId, shopId, listingId);
                return QuoteResult.error(ShopResultCode.SERVER_ERROR);
            }
            if (stock.status() != CatalogStockStatus.ACTIVE) {
                LOGGER.warn(
                        "Server shop purchase request {} found stock listing {} in status {}.",
                        requestId, listingId, stock.status());
                return QuoteResult.error(ShopResultCode.SERVER_ERROR);
            }
            if (!stock.unlimited()
                    && stock.availableQuantity() < quantity) {
                LOGGER.info(
                        "Server shop purchase request {} is out of stock for shop {} listing {}. Available {}, requested {}.",
                        requestId, shopId, listingId,
                        stock.availableQuantity(), quantity);
                return QuoteResult.error(ShopResultCode.OUT_OF_STOCK);
            }
            Item item = ShopTransactionUtil.resolveItem(itemId);
            if (item == null) {
                return QuoteResult.error(ShopResultCode.INVALID_ITEM);
            }
            long lineCost = ShopCatalog.calculateLineCost(shopId,
                    listingId, quantity);
            if (lineCost <= 0L) {
                return QuoteResult.error(ShopResultCode.INVALID_ITEM);
            }
            CompoundTag tag = parseTag(definition.nbtJson(), itemId,
                    listingId, shopId);
            List<ItemStack> outputs = splitOutputs(item, quantity, tag);
            try {
                lines.add(ServerShopPurchaseService.captureLine(requestId,
                        lineIndex, listingId, itemId, quantity, lineCost,
                        stock.revision(), outputs));
            } catch (RuntimeException exception) {
                return QuoteResult.error(ShopResultCode.SERVER_ERROR);
            }
        }
        try {
            return QuoteResult.success(new PreparedQuote(lines));
        } catch (ArithmeticException | IllegalArgumentException exception) {
            return QuoteResult.error(ShopResultCode.SERVER_ERROR);
        }
    }

    private static CompoundTag parseTag(
            String nbt,
            String itemId,
            String listingId,
            String shopId
    ) {
        if (nbt == null || nbt.isBlank()) {
            return null;
        }
        try {
            return TagParser.parseTag(nbt);
        } catch (Exception exception) {
            com.mojang.logging.LogUtils.getLogger().warn(
                    "FutureShops invalid catalog item data for item {} listing {} shop {}.",
                    itemId, listingId, shopId);
            return null;
        }
    }

    private static List<ItemStack> splitOutputs(
            Item item,
            int quantity,
            CompoundTag tag
    ) {
        List<ItemStack> outputs = new ArrayList<>();
        int remaining = quantity;
        while (remaining > 0) {
            ItemStack stack = new ItemStack(item, 1);
            int portion = Math.min(remaining, stack.getMaxStackSize());
            stack.setCount(portion);
            if (tag != null) {
                stack.setTag(tag.copy());
            }
            outputs.add(stack);
            remaining -= portion;
        }
        return List.copyOf(outputs);
    }

    private static ServerShopPurchaseService.Identity identity(
            C2SBuyRequestPacket packet,
            UUID playerId,
            String shopId,
            PaymentSource paymentSource,
            Map<String, Integer> mergedLines
    ) {
        List<ServerShopPurchaseCommit.IdentityLine> lines =
                mergedLines.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry ->
                                new ServerShopPurchaseCommit.IdentityLine(
                                        entry.getKey(), entry.getValue()))
                        .toList();
        return new ServerShopPurchaseService.Identity(packet.requestId(),
                playerId, shopId, packet.cartCheckout(), paymentSource,
                lines);
    }

    static Map<String, Integer> mergeLines(
            List<C2SBuyRequestPacket.LineItem> lineItems
    ) {
        Map<String, Integer> merged = new LinkedHashMap<>();
        for (C2SBuyRequestPacket.LineItem lineItem : lineItems) {
            if (lineItem == null || lineItem.listingId() == null
                    || lineItem.listingId().isBlank()) {
                continue;
            }
            merged.merge(lineItem.listingId(), lineItem.quantity(),
                    Math::addExact);
        }
        return merged;
    }

    private static BuyResult mapResult(
            ServerPlayer player,
            String shopId,
            ServerShopPurchaseService.Result result
    ) {
        if (!result.successful()) {
            ShopResultCode code = switch (result.status()) {
                case INSUFFICIENT_FUNDS ->
                        ShopResultCode.INSUFFICIENT_FUNDS;
                case OUT_OF_STOCK -> ShopResultCode.OUT_OF_STOCK;
                case INVALID_REQUEST, REQUEST_CONFLICT ->
                        ShopResultCode.INVALID_REQUEST;
                case QUOTE_CHANGED -> ShopResultCode.INVALID_ITEM;
                case ESCROW_UNAVAILABLE, RECOVERY_REQUIRED ->
                        ShopResultCode.SERVER_ERROR;
                case SUCCESS -> throw new IllegalStateException(
                        "Server shop result status is invalid");
            };
            return BuyResult.error(shopId, result.resultingBalanceMinorUnits(),
                    code);
        }
        deliverExactItemClaims(player, result.itemClaims());
        return BuyResult.success(shopId,
                result.resultingBalanceMinorUnits(),
                result.totalCostMinorUnits(), result.totalQuantity(),
                result.transactionId().orElseThrow(), result.lines(),
                result.occurredAt(), result.replayed());
    }

    private static void deliverExactItemClaims(
            ServerPlayer player,
            List<EscrowClaim> claims
    ) {
        EscrowConfig.Settings settings = EscrowConfig.settings();
        if (!settings.automaticClaimDelivery()) {
            return;
        }
        EscrowRuntimeService runtime = EscrowRuntimeManager.getOrNull();
        if (runtime == null || !runtime.isReady()) {
            return;
        }
        if (player.getServer() == null) {
            return;
        }
        int operationLimit = settings.exactItemDeliveryOperationsPerTick();
        int limit = Math.min(ExactItemDeliveryTickBudget.remaining(
                player.getServer(), operationLimit), claims.size());
        Instant now = Instant.now();
        for (int index = 0; index < limit; index++) {
            if (!ExactItemDeliveryTickBudget.tryAcquire(
                    player.getServer(), operationLimit)) {
                return;
            }
            try {
                runtime.collectExactItemClaim(player,
                        claims.get(index).claimId(), now);
            } catch (RuntimeException ignored) {
                return;
            }
        }
    }

    private static String historyNbt(
            ServerShopPurchaseCommit.Line line
    ) {
        ExactItemClaimResolution resolution = line.outputs().get(0)
                .resolve();
        return resolution.resolvedStack().map(ItemStack::getTag)
                .map(CompoundTag::toString).orElse("");
    }

    private static long safeBalance(ServerPlayer player) {
        try {
            return BalanceManager.getProvider().getBalance(
                    player.getUUID());
        } catch (RuntimeException exception) {
            return 0L;
        }
    }

    private static boolean freshAccessAllowed(
            ServerPlayer player,
            String shopId
    ) {
        ShopSession session = ShopSessionManager.get(
                player.getUUID()).orElse(null);
        return session != null && session.shopId().equals(shopId)
                && player.getServer() != null
                && AdminShopToggleSavedData.get(player.getServer())
                .isAdminShopEnabled();
    }

    private static String candidateShopId(String requested) {
        return requested == null || requested.isBlank()
                ? "default" : requested.strip();
    }

    private record QuoteResult(
            Optional<PreparedQuote> quote,
            ShopResultCode errorCode
    ) {
        private static QuoteResult success(PreparedQuote quote) {
            return new QuoteResult(Optional.of(quote), ShopResultCode.OK);
        }

        private static QuoteResult error(ShopResultCode errorCode) {
            return new QuoteResult(Optional.empty(), errorCode);
        }
    }

    private record PreparedQuote(
            List<ServerShopPurchaseCommit.Line> lines,
            long totalCostMinorUnits,
            int totalQuantity
    ) {
        private PreparedQuote(List<ServerShopPurchaseCommit.Line> lines) {
            this(List.copyOf(lines), totalCost(lines), totalQuantity(lines));
        }

        private PreparedQuote {
            lines = List.copyOf(lines);
            if (lines.isEmpty() || totalCostMinorUnits <= 0L
                    || totalQuantity <= 0) {
                throw new IllegalArgumentException(
                        "Server shop quote is invalid");
            }
        }

        private PreparedQuote withCosts(List<Long> costs) {
            if (costs.size() != lines.size()) {
                throw new IllegalArgumentException(
                        "Server shop authorized costs are invalid");
            }
            List<ServerShopPurchaseCommit.Line> authorized =
                    new ArrayList<>(lines.size());
            for (int index = 0; index < lines.size(); index++) {
                ServerShopPurchaseCommit.Line line = lines.get(index);
                authorized.add(new ServerShopPurchaseCommit.Line(
                        line.lineIndex(), line.listingId(), line.itemId(),
                        line.quantity(), costs.get(index),
                        line.expectedStockRevision(), line.outputs()));
            }
            return new PreparedQuote(authorized);
        }

        private static long totalCost(
                List<ServerShopPurchaseCommit.Line> lines
        ) {
            long total = 0L;
            for (ServerShopPurchaseCommit.Line line : lines) {
                total = Math.addExact(total, line.lineCostMinorUnits());
            }
            return total;
        }

        private static int totalQuantity(
                List<ServerShopPurchaseCommit.Line> lines
        ) {
            int total = 0;
            for (ServerShopPurchaseCommit.Line line : lines) {
                total = Math.addExact(total, line.quantity());
            }
            return total;
        }
    }

    private record BuyResult(
            boolean success,
            String shopId,
            ShopResultCode errorCode,
            long resultingBalance,
            long totalCost,
            int totalQuantity,
            UUID transactionId,
            List<ServerShopPurchaseCommit.Line> lines,
            Instant occurredAt,
            boolean replayed
    ) {
        private static BuyResult success(
                String shopId,
                long resultingBalance,
                long totalCost,
                int totalQuantity,
                UUID transactionId,
                List<ServerShopPurchaseCommit.Line> lines,
                Instant occurredAt,
                boolean replayed
        ) {
            return new BuyResult(true, shopId, ShopResultCode.OK,
                    resultingBalance, totalCost, totalQuantity,
                    transactionId, List.copyOf(lines), occurredAt, replayed);
        }

        private static BuyResult error(
                String shopId,
                long resultingBalance,
                ShopResultCode errorCode
        ) {
            return new BuyResult(false, shopId, errorCode,
                    resultingBalance, 0L, 0, ZERO_UUID, List.of(),
                    Instant.EPOCH, false);
        }
    }
}

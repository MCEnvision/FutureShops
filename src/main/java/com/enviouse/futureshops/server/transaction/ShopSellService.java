package com.enviouse.futureshops.server.transaction;

import com.enviouse.futureshops.catalog.ItemDef;
import com.enviouse.futureshops.catalog.ShopCatalog;
import com.enviouse.futureshops.event.ShopTransactionEvent;
import com.enviouse.futureshops.money.ItemStackSnapshotCodec;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.C2SSellRequestPacket;
import com.enviouse.futureshops.network.packets.S2CSellResponsePacket;
import com.enviouse.futureshops.server.economy.BalanceManager;
import com.enviouse.futureshops.server.economy.WalletMutationGuard;
import com.enviouse.futureshops.server.escrow.runtime.EscrowRuntimeManager;
import com.enviouse.futureshops.server.escrow.runtime.EscrowRuntimeService;
import com.enviouse.futureshops.server.escrow.runtime.ServerShopSellCommit;
import com.enviouse.futureshops.server.escrow.runtime.ServerShopSellService;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

public final class ShopSellService {
    private ShopSellService() {
    }

    public static void handleSellRequest(
            ServerPlayer player,
            C2SSellRequestPacket packet
    ) {
        SellResult result = execute(player, packet);
        ShopPackets.sendToPlayer(player, new S2CSellResponsePacket(
                result.success(), result.shopId(), result.itemId(),
                result.errorCode(), result.resultingBalance(),
                packet.quantity(), result.totalValue(), packet.requestId()));
        if (!result.success() || player.getServer() == null) {
            return;
        }
        TransactionHistoryService.recordServerSell(player.getServer(),
                player.getUUID(), result.shopId(), result.transactionId(),
                result.itemId(), packet.quantity(), result.totalValue(),
                result.nbtJson(), result.occurredAt());
        if (!result.replayed()) {
            DynamicPricingEngine.recordSell(player.getServer(),
                    result.shopId(), result.listingId(), packet.quantity());
            MinecraftForge.EVENT_BUS.post(new ShopTransactionEvent.Post(
                    player.getUUID(), result.shopId(), result.itemId(),
                    packet.quantity(), "SELL", result.totalValue(),
                    result.resultingBalance()));
            InventorySyncService.sendOwnedCounts(player, result.shopId());
            ShopDataService.resendSessionsViewingShop(player.getServer(),
                    result.shopId());
        }
    }

    private static SellResult execute(
            ServerPlayer player,
            C2SSellRequestPacket packet
    ) {
        String candidateShop = candidateShopId(packet.shopId());
        ServerShopSellService.Identity identity;
        try {
            identity = new ServerShopSellService.Identity(packet.requestId(),
                    player.getUUID(), candidateShop, packet.listingId(),
                    packet.quantity());
        } catch (RuntimeException exception) {
            return SellResult.error(candidateShop, safeBalance(player),
                    ShopResultCode.INVALID_REQUEST);
        }
        Optional<ServerShopSellService.Result> replay =
                ServerShopSellService.resolveReplay(player, identity);
        if (replay.isPresent()) {
            return mapResult(candidateShop, safeBalance(player),
                    replay.orElseThrow());
        }
        String shopId = ShopDataService.resolveShopId(candidateShop);
        if (!shopId.equals(candidateShop)) {
            return SellResult.error(shopId, safeBalance(player),
                    ShopResultCode.INVALID_REQUEST);
        }
        if (!freshAccessAllowed(player, shopId)) {
            return SellResult.error(shopId, safeBalance(player),
                    ShopResultCode.SHOP_CLOSED);
        }
        ReentrantLock lock = ShopTransactionUtil.lockFor(player.getUUID());
        if (!lock.tryLock()) {
            return SellResult.error(shopId, safeBalance(player),
                    ShopResultCode.COOLDOWN);
        }
        try {
            Optional<WalletMutationGuard.Lease> walletGuard =
                    WalletMutationGuard.tryAcquire(List.of(player.getUUID()));
            if (walletGuard.isEmpty()) {
                return SellResult.error(shopId, safeBalance(player),
                        ShopResultCode.COOLDOWN);
            }
            try (WalletMutationGuard.Lease ignored =
                         walletGuard.orElseThrow()) {
                PreparedQuote first = prepareQuote(shopId,
                        packet.listingId()).orElse(null);
                if (first == null) {
                    return SellResult.error(shopId, safeBalance(player),
                            ShopResultCode.INVALID_ITEM);
                }
                long total;
                try {
                    total = Math.multiplyExact(first.item()
                            .sellPriceMinorUnits(), packet.quantity());
                } catch (ArithmeticException exception) {
                    return SellResult.error(shopId, safeBalance(player),
                            ShopResultCode.INVALID_AMOUNT);
                }
                ShopTransactionEvent.Pre event =
                        new ShopTransactionEvent.Pre(player, shopId,
                                first.item().itemId(), packet.quantity(),
                                "SELL", total);
                if (MinecraftForge.EVENT_BUS.post(event)) {
                    return SellResult.error(shopId, safeBalance(player),
                            ShopResultCode.CANCELLED_BY_EVENT);
                }
                if (event.getPriceMinor() <= 0L
                        || event.getPriceMinor() % packet.quantity() != 0L) {
                    return SellResult.error(shopId, safeBalance(player),
                            ShopResultCode.INVALID_AMOUNT);
                }
                PreparedQuote revalidated = prepareQuote(shopId,
                        packet.listingId()).orElse(null);
                if (!first.equals(revalidated)
                        || !freshAccessAllowed(player, shopId)) {
                    return SellResult.error(shopId, safeBalance(player),
                            ShopResultCode.INVALID_ITEM);
                }
                long unitPrice = event.getPriceMinor()
                        / packet.quantity();
                ServerShopSellService.PreparedRequest request =
                        new ServerShopSellService.PreparedRequest(identity,
                                first.item().itemId(), unitPrice,
                                first.quoteRevision(),
                                first.stockRevision(), Instant.now(),
                                first.exactTemplate(),
                                ShopEscrowItemEvidence.shopReference(
                                        player, shopId));
                return mapResult(shopId, safeBalance(player),
                        ServerShopSellService.sell(player, request));
            }
        } catch (RuntimeException exception) {
            return SellResult.error(shopId, safeBalance(player),
                    ShopResultCode.SERVER_ERROR);
        } finally {
            lock.unlock();
        }
    }

    private static Optional<PreparedQuote> prepareQuote(
            String shopId,
            String listingId
    ) {
        ItemDef item = ShopCatalog.getItem(shopId, listingId)
                .orElse(null);
        if (item == null || item.sellPriceMinorUnits() <= 0L
                || item.isExpired(Instant.now().getEpochSecond())) {
            return Optional.empty();
        }
        EscrowRuntimeService runtime = EscrowRuntimeManager.getOrNull();
        if (runtime == null || !runtime.isReady()) {
            return Optional.empty();
        }
        CatalogStockState stock = runtime.stockListing(
                new StockKey(shopId, item.resolutionKey())).orElse(null);
        if (stock == null || stock.status() != CatalogStockStatus.ACTIVE) {
            return Optional.empty();
        }
        byte[] template = ShopEscrowItemEvidence.exactTemplate(
                item.itemId(), item.nbtJson());
        return Optional.of(new PreparedQuote(item,
                ShopCatalogEvidenceRevision.item(item),
                stock.revision(), template));
    }

    private static SellResult mapResult(
            String shopId,
            long fallbackBalance,
            ServerShopSellService.Result result
    ) {
        if (!result.success()) {
            ShopResultCode code = switch (result.status()) {
                case INVALID_REQUEST, REQUEST_CONFLICT ->
                        ShopResultCode.INVALID_REQUEST;
                case MISSING_ITEMS -> ShopResultCode.MISSING_ITEMS;
                case UNSUPPORTED_ITEM -> ShopResultCode.INVALID_ITEM;
                case STOCK_UNAVAILABLE, STOCK_CHANGED ->
                        ShopResultCode.SERVER_ERROR;
                case ITEM_CUSTODY_ABORTED, ESCROW_UNAVAILABLE,
                     RECOVERY_REQUIRED -> ShopResultCode.SERVER_ERROR;
                case SUCCESS -> throw new IllegalStateException(
                        "Server shop sell result status is invalid");
            };
            return SellResult.error(shopId, fallbackBalance, code);
        }
        ServerShopSellCommit commit = result.commit().orElseThrow();
        ItemStack template = ItemStackSnapshotCodec.decode(
                commit.exactItemTemplate());
        String nbt = template.getTag() == null
                ? "" : template.getTag().toString();
        return SellResult.success(commit.shopId(), commit.listingId(),
                commit.itemId(), nbt, result.resultingBalanceMinorUnits(),
                result.payoutMinorUnits(), commit.requestId(),
                commit.completedTransaction().timestamps().updatedAt(),
                result.replayed());
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

    private record PreparedQuote(
            ItemDef item,
            long quoteRevision,
            long stockRevision,
            byte[] exactTemplate
    ) {
        private PreparedQuote {
            exactTemplate = exactTemplate.clone();
        }

        @Override
        public byte[] exactTemplate() {
            return exactTemplate.clone();
        }

        @Override
        public boolean equals(Object object) {
            return object instanceof PreparedQuote other
                    && item.equals(other.item)
                    && quoteRevision == other.quoteRevision
                    && stockRevision == other.stockRevision
                    && Arrays.equals(exactTemplate, other.exactTemplate);
        }

        @Override
        public int hashCode() {
            return 31 * java.util.Objects.hash(item, quoteRevision,
                    stockRevision) + Arrays.hashCode(exactTemplate);
        }
    }

    private record SellResult(
            boolean success,
            String shopId,
            String listingId,
            String itemId,
            String nbtJson,
            ShopResultCode errorCode,
            long resultingBalance,
            long totalValue,
            UUID transactionId,
            Instant occurredAt,
            boolean replayed
    ) {
        private static SellResult success(
                String shopId,
                String listingId,
                String itemId,
                String nbtJson,
                long resultingBalance,
                long totalValue,
                UUID transactionId,
                Instant occurredAt,
                boolean replayed
        ) {
            return new SellResult(true, shopId, listingId, itemId,
                    nbtJson, ShopResultCode.OK, resultingBalance,
                    totalValue, transactionId, occurredAt, replayed);
        }

        private static SellResult error(
                String shopId,
                long resultingBalance,
                ShopResultCode errorCode
        ) {
            return new SellResult(false, shopId, "", "", "",
                    errorCode, resultingBalance, 0L,
                    new UUID(0L, 0L), Instant.EPOCH, false);
        }
    }
}

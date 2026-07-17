package com.enviouse.futureshops.server.transaction;

import com.enviouse.futureshops.catalog.BarterIngredientDef;
import com.enviouse.futureshops.catalog.BarterRecipeDef;
import com.enviouse.futureshops.catalog.ItemDef;
import com.enviouse.futureshops.catalog.ShopCatalog;
import com.enviouse.futureshops.config.EscrowConfig;
import com.enviouse.futureshops.event.BarterTradeEvent;
import com.enviouse.futureshops.event.ShopTransactionEvent;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.C2SBarterRequestPacket;
import com.enviouse.futureshops.network.packets.S2CBarterResponsePacket;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.item.ExactItemClaimResolution;
import com.enviouse.futureshops.server.escrow.item.ItemMatchMode;
import com.enviouse.futureshops.server.escrow.runtime.EscrowRuntimeManager;
import com.enviouse.futureshops.server.escrow.runtime.EscrowRuntimeService;
import com.enviouse.futureshops.server.escrow.runtime.ServerShopBarterCommit;
import com.enviouse.futureshops.server.escrow.runtime.ServerShopBarterService;
import com.enviouse.futureshops.server.escrow.stock.CatalogStockState;
import com.enviouse.futureshops.server.escrow.stock.CatalogStockStatus;
import com.enviouse.futureshops.server.escrow.stock.StockKey;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

public final class ShopBarterService {
    private ShopBarterService() {
    }

    public static void handleBarterRequest(
            ServerPlayer player,
            C2SBarterRequestPacket packet
    ) {
        BarterResult result = execute(player, packet);
        ShopPackets.sendToPlayer(player, new S2CBarterResponsePacket(
                result.success(), result.shopId(), packet.recipeId(),
                result.errorCode(), packet.multiplier(),
                result.outputQuantity(), packet.requestId()));
        if (!result.success() || player.getServer() == null) {
            return;
        }
        TransactionHistoryService.recordServerBarter(player.getServer(),
                player.getUUID(), result.shopId(), result.transactionId(),
                result.targetItemId(), result.outputQuantity(),
                result.paidNote(), result.targetNbtJson(),
                result.occurredAt());
        if (!result.replayed()) {
            MinecraftForge.EVENT_BUS.post(new ShopTransactionEvent.Post(
                    player.getUUID(), result.shopId(),
                    result.targetItemId(), result.outputQuantity(),
                    "BARTER", 0L, 0L));
            MinecraftForge.EVENT_BUS.post(new BarterTradeEvent.Post(
                    player.getUUID(), result.shopId(), packet.recipeId(),
                    result.targetItemId(), result.outputQuantity(),
                    result.ingredientEntries()));
            InventorySyncService.sendOwnedCounts(player, result.shopId());
            ShopDataService.resendSessionsViewingShop(player.getServer(),
                    result.shopId());
        }
    }

    private static BarterResult execute(
            ServerPlayer player,
            C2SBarterRequestPacket packet
    ) {
        String candidateShop = candidateShopId(packet.shopId());
        ServerShopBarterService.Identity identity;
        try {
            identity = new ServerShopBarterService.Identity(
                    packet.requestId(), player.getUUID(), candidateShop,
                    packet.recipeId(), packet.multiplier());
        } catch (RuntimeException exception) {
            return BarterResult.error(candidateShop,
                    ShopResultCode.INVALID_REQUEST);
        }
        Optional<ServerShopBarterService.Result> replay =
                ServerShopBarterService.resolveReplay(player, identity);
        if (replay.isPresent()) {
            return mapResult(player, candidateShop,
                    replay.orElseThrow());
        }
        String shopId = ShopDataService.resolveShopId(candidateShop);
        if (!shopId.equals(candidateShop)) {
            return BarterResult.error(shopId,
                    ShopResultCode.INVALID_REQUEST);
        }
        if (!freshAccessAllowed(player, shopId)) {
            return BarterResult.error(shopId, ShopResultCode.SHOP_CLOSED);
        }
        ReentrantLock lock = ShopTransactionUtil.lockFor(player.getUUID());
        if (!lock.tryLock()) {
            return BarterResult.error(shopId, ShopResultCode.COOLDOWN);
        }
        try {
            PreparedQuote first = prepareQuote(identity).orElse(null);
            if (first == null) {
                return BarterResult.error(shopId,
                        ShopResultCode.INVALID_RECIPE);
            }
            BarterTradeEvent.Pre barterEvent = new BarterTradeEvent.Pre(
                    player.getUUID(), shopId, packet.recipeId(),
                    first.target().itemId(), first.outputQuantity(),
                    first.ingredientEntries());
            if (MinecraftForge.EVENT_BUS.post(barterEvent)) {
                return BarterResult.error(shopId,
                        ShopResultCode.CANCELLED_BY_EVENT);
            }
            ShopTransactionEvent.Pre transactionEvent =
                    new ShopTransactionEvent.Pre(player, shopId,
                            first.target().itemId(),
                            first.outputQuantity(), "BARTER", 0L);
            if (MinecraftForge.EVENT_BUS.post(transactionEvent)) {
                return BarterResult.error(shopId,
                        ShopResultCode.CANCELLED_BY_EVENT);
            }
            PreparedQuote revalidated = prepareQuote(identity)
                    .orElse(null);
            if (!first.equals(revalidated)
                    || !freshAccessAllowed(player, shopId)) {
                return BarterResult.error(shopId,
                        ShopResultCode.INVALID_RECIPE);
            }
            ServerShopBarterService.PreparedRequest request =
                    new ServerShopBarterService.PreparedRequest(identity,
                            first.quoteRevision(),
                            first.recipeRevision(), Instant.now(),
                            first.ingredients(), first.outputs(),
                            ShopEscrowItemEvidence.shopReference(
                                    player, shopId));
            return mapResult(player, shopId,
                    ServerShopBarterService.barter(player, request));
        } catch (RuntimeException exception) {
            return BarterResult.error(shopId,
                    ShopResultCode.SERVER_ERROR);
        } finally {
            lock.unlock();
        }
    }

    private static Optional<PreparedQuote> prepareQuote(
            ServerShopBarterService.Identity identity
    ) {
        BarterRecipeDef recipe = ShopCatalog.getBarterRecipe(
                identity.shopId(), identity.recipeId()).orElse(null);
        if (recipe == null || recipe.ingredients() == null
                || recipe.ingredients().isEmpty()
                || recipe.outputCount() <= 0) {
            return Optional.empty();
        }
        ItemDef target = ShopCatalog.resolveBarterTarget(
                identity.shopId(), recipe.targetItemId()).orElse(null);
        if (target == null
                || target.isExpired(Instant.now().getEpochSecond())) {
            return Optional.empty();
        }
        EscrowRuntimeService runtime = EscrowRuntimeManager.getOrNull();
        if (runtime == null || !runtime.isReady()) {
            return Optional.empty();
        }
        CatalogStockState stock = runtime.stockListing(new StockKey(
                identity.shopId(), target.resolutionKey())).orElse(null);
        if (stock == null || stock.status() != CatalogStockStatus.ACTIVE) {
            return Optional.empty();
        }
        int outputQuantity = Math.multiplyExact(recipe.outputCount(),
                identity.multiplier());
        ItemStack prototype = ShopEscrowItemEvidence.exactStack(
                target.itemId(), target.nbtJson(), 1);
        String outputSource = ServerShopBarterCommit.outputSourceKey(
                identity.requestId(), 0);
        ServerShopBarterCommit.OutputLine output =
                new ServerShopBarterCommit.OutputLine(0,
                        target.resolutionKey(), target.itemId(),
                        recipe.outputCount(), stock.revision(),
                        ShopEscrowItemEvidence.captureOutput(
                                identity.requestId(), outputSource,
                                prototype, outputQuantity));
        List<ServerShopBarterCommit.Ingredient> ingredients =
                prepareIngredients(recipe);
        List<BarterTradeEvent.IngredientEntry> eventIngredients =
                ingredients.stream().map(ingredient ->
                        new BarterTradeEvent.IngredientEntry(
                                ingredient.itemId(),
                                ingredient.totalQuantity(
                                        identity.multiplier())))
                        .toList();
        return Optional.of(new PreparedQuote(recipe, target,
                ShopCatalogEvidenceRevision.item(target),
                ShopCatalogEvidenceRevision.barter(recipe, target),
                ingredients, List.of(output), eventIngredients,
                outputQuantity));
    }

    private static List<ServerShopBarterCommit.Ingredient>
    prepareIngredients(BarterRecipeDef recipe) {
        Map<IngredientKey, Integer> merged = new LinkedHashMap<>();
        for (BarterIngredientDef ingredient : recipe.ingredients()) {
            if (ingredient == null || ingredient.count() <= 0) {
                throw new IllegalArgumentException(
                        "Barter ingredient configuration is invalid");
            }
            IngredientKey key = new IngredientKey(ingredient.itemId(),
                    ingredient.nbtJson() == null
                            ? "" : ingredient.nbtJson());
            merged.merge(key, ingredient.count(), Math::addExact);
        }
        if (merged.isEmpty()
                || merged.size() > ServerShopBarterCommit.MAX_INGREDIENTS) {
            throw new IllegalArgumentException(
                    "Barter ingredient configuration exceeds its limit");
        }
        List<ServerShopBarterCommit.Ingredient> prepared =
                new ArrayList<>(merged.size());
        int index = 0;
        for (Map.Entry<IngredientKey, Integer> entry
                : merged.entrySet()) {
            IngredientKey key = entry.getKey();
            ItemMatchMode mode = key.nbtJson().isBlank()
                    ? ItemMatchMode.ITEM_ONLY : ItemMatchMode.EXACT;
            prepared.add(new ServerShopBarterCommit.Ingredient(index,
                    "ingredient." + index, key.itemId(),
                    entry.getValue(), mode,
                    ShopEscrowItemEvidence.exactTemplate(
                            key.itemId(), key.nbtJson())));
            index++;
        }
        return List.copyOf(prepared);
    }

    private static BarterResult mapResult(
            ServerPlayer player,
            String shopId,
            ServerShopBarterService.Result result
    ) {
        if (!result.success()) {
            ShopResultCode code = switch (result.status()) {
                case REQUEST_CONFLICT -> ShopResultCode.INVALID_REQUEST;
                case MISSING_INGREDIENTS ->
                        ShopResultCode.MISSING_INGREDIENTS;
                case UNSUPPORTED_ITEM -> ShopResultCode.INVALID_RECIPE;
                case STOCK_UNAVAILABLE, STOCK_CHANGED ->
                        ShopResultCode.OUT_OF_STOCK;
                case ITEM_CUSTODY_ABORTED, ESCROW_UNAVAILABLE,
                     RECOVERY_REQUIRED -> ShopResultCode.SERVER_ERROR;
                case SUCCESS -> throw new IllegalStateException(
                        "Server shop barter result status is invalid");
            };
            return BarterResult.error(shopId, code);
        }
        ServerShopBarterCommit commit = result.commit().orElseThrow();
        deliverExactItemClaims(player, result.outputClaims());
        ServerShopBarterCommit.OutputLine output =
                commit.outputs().get(0);
        ExactItemClaimResolution resolved = output.portions().get(0)
                .resolve();
        String nbt = resolved.resolvedStack().map(ItemStack::getTag)
                .map(Object::toString).orElse("");
        List<BarterTradeEvent.IngredientEntry> ingredients =
                commit.ingredients().stream().map(ingredient ->
                        new BarterTradeEvent.IngredientEntry(
                                ingredient.itemId(),
                                ingredient.totalQuantity(
                                        commit.multiplier())))
                        .toList();
        StringBuilder paid = new StringBuilder("paid=");
        for (int index = 0; index < ingredients.size(); index++) {
            BarterTradeEvent.IngredientEntry ingredient =
                    ingredients.get(index);
            if (index > 0) {
                paid.append(',');
            }
            paid.append(ingredient.itemId()).append('\u00d7')
                    .append(ingredient.count());
        }
        return BarterResult.success(commit.shopId(), output.itemId(),
                nbt, commit.totalOutputQuantity(), ingredients,
                paid.toString(), commit.requestId(),
                commit.completedTransaction().timestamps().updatedAt(),
                result.replayed());
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
        int limit = Math.min(settings.claimDeliveryWorkPerTick(),
                claims.size());
        Instant now = Instant.now();
        for (int index = 0; index < limit; index++) {
            try {
                runtime.collectExactItemClaim(player,
                        claims.get(index).claimId(), now);
            } catch (RuntimeException ignored) {
                return;
            }
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

    private record IngredientKey(String itemId, String nbtJson) {
    }

    private record PreparedQuote(
            BarterRecipeDef recipe,
            ItemDef target,
            long quoteRevision,
            long recipeRevision,
            List<ServerShopBarterCommit.Ingredient> ingredients,
            List<ServerShopBarterCommit.OutputLine> outputs,
            List<BarterTradeEvent.IngredientEntry> ingredientEntries,
            int outputQuantity
    ) {
        private PreparedQuote {
            ingredients = List.copyOf(ingredients);
            outputs = List.copyOf(outputs);
            ingredientEntries = List.copyOf(ingredientEntries);
        }
    }

    private record BarterResult(
            boolean success,
            String shopId,
            ShopResultCode errorCode,
            String targetItemId,
            String targetNbtJson,
            int outputQuantity,
            List<BarterTradeEvent.IngredientEntry> ingredientEntries,
            String paidNote,
            UUID transactionId,
            Instant occurredAt,
            boolean replayed
    ) {
        private BarterResult {
            ingredientEntries = List.copyOf(ingredientEntries);
        }

        private static BarterResult success(
                String shopId,
                String targetItemId,
                String targetNbtJson,
                int outputQuantity,
                List<BarterTradeEvent.IngredientEntry> ingredientEntries,
                String paidNote,
                UUID transactionId,
                Instant occurredAt,
                boolean replayed
        ) {
            return new BarterResult(true, shopId, ShopResultCode.OK,
                    targetItemId, targetNbtJson, outputQuantity,
                    ingredientEntries, paidNote, transactionId,
                    occurredAt, replayed);
        }

        private static BarterResult error(
                String shopId,
                ShopResultCode errorCode
        ) {
            return new BarterResult(false, shopId, errorCode, "", "",
                    0, List.of(), "", new UUID(0L, 0L),
                    Instant.EPOCH, false);
        }
    }
}

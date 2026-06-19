package com.enviouse.futureshopsp.command;

import com.enviouse.futureshopsp.Config;
import com.enviouse.futureshopsp.catalog.AdminShopConfigWriter;
import com.enviouse.futureshopsp.catalog.AdminShopItemSpec;
import com.enviouse.futureshopsp.catalog.ItemDef;
import com.enviouse.futureshopsp.catalog.ShopCatalog;
import com.enviouse.futureshopsp.money.MoneyMintRecord;
import com.enviouse.futureshopsp.money.SpentMintsSavedData;
import com.enviouse.futureshopsp.server.economy.BalanceManager;
import com.enviouse.futureshopsp.server.economy.EconomyProvider;
import com.enviouse.futureshopsp.server.economy.TransactionResult;
import com.enviouse.futureshopsp.server.session.ShopSessionManager;
import com.enviouse.futureshopsp.server.shop.AdminShopToggleSavedData;
import com.enviouse.futureshopsp.server.shop.AdminCategorySavedData;
import com.enviouse.futureshopsp.server.shop.MarketplaceAnalyticsService;
import com.enviouse.futureshopsp.server.shop.ShopDataService;
import com.enviouse.futureshopsp.server.shop.ShopLimitsSavedData;
import com.enviouse.futureshopsp.server.shop.PlayerShopRegistrySavedData;
import com.enviouse.futureshopsp.block.ShopBlockEntity;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Registers the {@code /shopadmin} command tree.
 *
 * <p>Currently implemented subcommands:
 * <ul>
 *   <li>{@code /shopadmin coinaudit <player>} — shows per-player active vs consumed
 *       mint statistics from {@link SpentMintsSavedData#snapshotRegistry()}.
 * </ul>
 *
 * <p>Requires permission level 2 (op).
 */
public final class ShopAdminCommand {

    private static final DateTimeFormatter TS_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("UTC"));

    /** Suggests existing admin category names (quoted for names with spaces). */
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_CATEGORIES = (ctx, builder) -> {
        MinecraftServer server = ctx.getSource().getServer();
        List<String> cats = AdminCategorySavedData.get(server).getAllSorted();
        return SharedSuggestionProvider.suggest(cats.stream().map(c -> c.contains(" ") ? "\"" + c + "\"" : c), builder);
    };

    /**
     * Suggests both runtime admin categories and bundled (JSON-defined) categories — used by
     * {@code remove}, which can act on either set.
     */
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_REMOVABLE_CATEGORIES = (ctx, builder) -> {
        MinecraftServer server = ctx.getSource().getServer();
        AdminCategorySavedData data = AdminCategorySavedData.get(server);
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>(data.getAllSorted());
        for (com.enviouse.futureshopsp.catalog.ShopDefinition def : ShopCatalog.getAllDefinitions()) {
            for (com.enviouse.futureshopsp.catalog.CategoryDef cat : def.categories()) {
                if ("all".equalsIgnoreCase(cat.id())) continue;
                if (data.isBaseCategoryHidden(cat.id())) continue;
                names.add(cat.displayName());
            }
        }
        return SharedSuggestionProvider.suggest(
                names.stream().map(c -> c.contains(" ") ? "\"" + c + "\"" : c), builder);
    };

    /** Suggests bundled categories that have been hidden — used by {@code restore}. */
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_HIDDEN_CATEGORIES = (ctx, builder) -> {
        MinecraftServer server = ctx.getSource().getServer();
        AdminCategorySavedData data = AdminCategorySavedData.get(server);
        java.util.Set<String> hiddenIds = data.getHiddenBaseCategoryIds();
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
        for (com.enviouse.futureshopsp.catalog.ShopDefinition def : ShopCatalog.getAllDefinitions()) {
            for (com.enviouse.futureshopsp.catalog.CategoryDef cat : def.categories()) {
                if (hiddenIds.contains(cat.id().toLowerCase(java.util.Locale.ROOT))) {
                    names.add(cat.displayName());
                }
            }
        }
        // Fall back to raw ids if the bundled definition is gone but the tombstone remains
        for (String id : hiddenIds) {
            if (names.stream().noneMatch(n -> n.equalsIgnoreCase(id))) names.add(id);
        }
        return SharedSuggestionProvider.suggest(
                names.stream().map(c -> c.contains(" ") ? "\"" + c + "\"" : c), builder);
    };

    /** Suggests all registered item resource locations (e.g. minecraft:diamond_sword). */
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_ALL_ITEMS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(
                    BuiltInRegistries.ITEM.keySet().stream().map(net.minecraft.resources.ResourceLocation::toString),
                    builder);

    /** Suggests only item IDs that currently have an admin category assignment. */
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_ASSIGNED_ITEMS = (ctx, builder) -> {
        MinecraftServer server = ctx.getSource().getServer();
        return SharedSuggestionProvider.suggest(
                AdminCategorySavedData.get(server).getAllAssignments().keySet(), builder);
    };

    /** The admin/server-shop id that {@code admin.json} loads as. */
    private static final String ADMIN_SHOP_ID = "default";

    /**
     * Suggests the stable listing ids of the admin shop (from the in-memory catalog). Ids containing
     * characters Brigadier won't read unquoted (e.g. the ':' of a legacy itemId-as-listingId) are
     * offered pre-quoted so the suggestion parses back cleanly.
     */
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_LISTING_IDS = (ctx, builder) -> {
        java.util.List<String> ids = new java.util.ArrayList<>();
        ShopCatalog.get(ADMIN_SHOP_ID).ifPresent(def -> {
            for (ItemDef it : def.items()) {
                String id = it.resolutionKey();
                ids.add(id.matches("[a-zA-Z0-9_.+-]+") ? id : "\"" + id + "\"");
            }
        });
        return SharedSuggestionProvider.suggest(ids, builder);
    };

    private ShopAdminCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("shopadmin")
                .requires(src -> src.hasPermission(2))

                .then(Commands.literal("reload")
                        .executes(ctx -> reloadCatalog(ctx.getSource())))

                .then(Commands.literal("promo")
                        .then(Commands.literal("set")
                                .then(Commands.argument("shopId", StringArgumentType.word())
                                        .then(Commands.argument("itemId", StringArgumentType.word())
                                                .then(Commands.argument("type", StringArgumentType.word())
                                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.01D))
                                                                .executes(ctx -> setPromo(
                                                                        ctx.getSource(),
                                                                        StringArgumentType.getString(ctx, "shopId"),
                                                                        StringArgumentType.getString(ctx, "itemId"),
                                                                        StringArgumentType.getString(ctx, "type"),
                                                                        DoubleArgumentType.getDouble(ctx, "value"))))))))
                        .then(Commands.literal("clear")
                                .then(Commands.argument("shopId", StringArgumentType.word())
                                        .then(Commands.argument("itemId", StringArgumentType.word())
                                                .executes(ctx -> clearPromo(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "shopId"),
                                                        StringArgumentType.getString(ctx, "itemId")))))))

                // /shopadmin coinaudit <playerName>
                .then(Commands.literal("coinaudit")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(ctx -> coinAudit(
                                        ctx, StringArgumentType.getString(ctx, "player")))))

                // /shopadmin adminshop toggle | add | remove
                .then(Commands.literal("adminshop")
                        .then(Commands.literal("toggle")
                                .executes(ctx -> toggleAdminShop(ctx.getSource())))
                        .then(Commands.literal("add")
                                .executes(ctx -> startAdminShopAdd(ctx.getSource())))
                        .then(Commands.literal("remove")
                                .executes(ctx -> adminShopRemoveHeld(ctx.getSource()))))

                // /shopadmin items add|edit|remove|list|info|refresh|sale — single-command admin shop
                // listing management with stable per-listing ids (supports multiple NBT variants of
                // one base item). Top-level sibling; distinct from /shopadmin category items.
                .then(Commands.literal("items")
                        .then(Commands.literal("add")
                                .then(Commands.argument("price", StringArgumentType.word())
                                        .then(Commands.argument("quantity", StringArgumentType.word())
                                                .then(Commands.argument("expire", StringArgumentType.word())
                                                        .then(Commands.argument("nbt", StringArgumentType.word())
                                                                .then(Commands.argument("category", StringArgumentType.string())
                                                                        .executes(ctx -> itemsAddOrEdit(ctx, null, null))
                                                                        .then(Commands.argument("sellprice", StringArgumentType.word())
                                                                                .executes(ctx -> itemsAddOrEdit(ctx, null,
                                                                                        StringArgumentType.getString(ctx, "sellprice"))))))))))
                        .then(Commands.literal("edit")
                                .then(Commands.argument("id", StringArgumentType.string()).suggests(SUGGEST_LISTING_IDS)
                                        .then(Commands.argument("price", StringArgumentType.word())
                                                .then(Commands.argument("quantity", StringArgumentType.word())
                                                        .then(Commands.argument("expire", StringArgumentType.word())
                                                                .then(Commands.argument("nbt", StringArgumentType.word())
                                                                        .then(Commands.argument("category", StringArgumentType.string())
                                                                                .executes(ctx -> itemsAddOrEdit(ctx,
                                                                                        StringArgumentType.getString(ctx, "id"), null))
                                                                                .then(Commands.argument("sellprice", StringArgumentType.word())
                                                                                        .executes(ctx -> itemsAddOrEdit(ctx,
                                                                                                StringArgumentType.getString(ctx, "id"),
                                                                                                StringArgumentType.getString(ctx, "sellprice")))))))))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("id", StringArgumentType.string()).suggests(SUGGEST_LISTING_IDS)
                                        .executes(ctx -> itemsRemove(ctx.getSource(), StringArgumentType.getString(ctx, "id")))))
                        .then(Commands.literal("list")
                                .executes(ctx -> itemsList(ctx.getSource(), null))
                                .then(Commands.argument("category", StringArgumentType.greedyString())
                                        .executes(ctx -> itemsList(ctx.getSource(), StringArgumentType.getString(ctx, "category")))))
                        .then(Commands.literal("info")
                                .then(Commands.argument("id", StringArgumentType.string()).suggests(SUGGEST_LISTING_IDS)
                                        .executes(ctx -> itemsInfo(ctx.getSource(), StringArgumentType.getString(ctx, "id")))))
                        .then(Commands.literal("refresh")
                                .then(Commands.argument("id", StringArgumentType.string()).suggests(SUGGEST_LISTING_IDS)
                                        .then(Commands.argument("interval", StringArgumentType.word())
                                                .executes(ctx -> itemsRefresh(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "id"),
                                                        StringArgumentType.getString(ctx, "interval"))))))
                        .then(Commands.literal("sale")
                                .then(Commands.argument("id", StringArgumentType.string()).suggests(SUGGEST_LISTING_IDS)
                                        .then(Commands.argument("price", StringArgumentType.word())
                                                .executes(ctx -> itemsSale(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "id"),
                                                        StringArgumentType.getString(ctx, "price")))))))

                // /shopadmin respond <text> — answers the active adminshop add wizard
                .then(Commands.literal("respond")
                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                .executes(ctx -> respondToWizard(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "text")))))

                // /shopadmin bal add|remove|set|check|reset <player> [amount]
                .then(Commands.literal("bal")
                        .then(Commands.literal("add")
                                .then(Commands.argument("target", GameProfileArgument.gameProfile())
                                        .then(Commands.argument("amount", StringArgumentType.word())
                                                .executes(ctx -> adminBalAdd(ctx.getSource(),
                                                        GameProfileArgument.getGameProfiles(ctx, "target"),
                                                        StringArgumentType.getString(ctx, "amount"))))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("target", GameProfileArgument.gameProfile())
                                        .then(Commands.argument("amount", StringArgumentType.word())
                                                .executes(ctx -> adminBalRemove(ctx.getSource(),
                                                        GameProfileArgument.getGameProfiles(ctx, "target"),
                                                        StringArgumentType.getString(ctx, "amount"))))))
                        .then(Commands.literal("set")
                                .then(Commands.argument("target", GameProfileArgument.gameProfile())
                                        .then(Commands.argument("amount", StringArgumentType.word())
                                                .executes(ctx -> adminBalSet(ctx.getSource(),
                                                        GameProfileArgument.getGameProfiles(ctx, "target"),
                                                        StringArgumentType.getString(ctx, "amount"))))))
                        .then(Commands.literal("check")
                                .then(Commands.argument("target", GameProfileArgument.gameProfile())
                                        .executes(ctx -> adminBalCheck(ctx.getSource(),
                                                GameProfileArgument.getGameProfiles(ctx, "target")))))
                        .then(Commands.literal("reset")
                                .then(Commands.argument("target", GameProfileArgument.gameProfile())
                                        .executes(ctx -> adminBalReset(ctx.getSource(),
                                                GameProfileArgument.getGameProfiles(ctx, "target"))))))

                // /shopadmin view <player> — opens that player's marketplace dashboard for the admin
                .then(Commands.literal("view")
                        .then(Commands.argument("target", GameProfileArgument.gameProfile())
                                .executes(ctx -> adminView(ctx.getSource(),
                                        GameProfileArgument.getGameProfiles(ctx, "target")))))

                // /shopadmin category add|remove|list|assign|unassign|items
                .then(Commands.literal("category")
                        .then(Commands.literal("add")
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(ctx -> addCategory(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .suggests(SUGGEST_REMOVABLE_CATEGORIES)
                                        .executes(ctx -> removeCategory(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                        .then(Commands.literal("restore")
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .suggests(SUGGEST_HIDDEN_CATEGORIES)
                                        .executes(ctx -> restoreCategory(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                        .then(Commands.literal("list")
                                .executes(ctx -> listCategories(ctx.getSource())))
                        .then(Commands.literal("assign")
                                .then(Commands.argument("categoryName", StringArgumentType.string())
                                        .suggests(SUGGEST_CATEGORIES)
                                        .then(Commands.argument("itemId", StringArgumentType.string())
                                                .suggests(SUGGEST_ALL_ITEMS)
                                                .executes(ctx -> assignItem(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "categoryName"),
                                                        StringArgumentType.getString(ctx, "itemId"))))))
                        .then(Commands.literal("unassign")
                                .then(Commands.argument("itemId", StringArgumentType.string())
                                        .suggests(SUGGEST_ASSIGNED_ITEMS)
                                        .executes(ctx -> unassignItem(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "itemId")))))
                        .then(Commands.literal("items")
                                .then(Commands.argument("categoryName", StringArgumentType.greedyString())
                                        .suggests(SUGGEST_CATEGORIES)
                                        .executes(ctx -> listCategoryItems(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "categoryName"))))))

                // /shopadmin limits maxlistings <amount> — set per-block listing cap (look at shop block)
                // /shopadmin limits maxblocks <player> <amount> — set per-player shop block cap
                // /shopadmin limits info — show limits for looked-at shop block
                // /shopadmin limits info <player> — show per-player shop block limit
                .then(Commands.literal("limits")
                        .then(Commands.literal("maxlistings")
                                .then(Commands.argument("amount", IntegerArgumentType.integer(-1))
                                        .executes(ctx -> setMaxListings(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "amount")))))
                        .then(Commands.literal("maxblocks")
                                .then(Commands.argument("target", GameProfileArgument.gameProfile())
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(-1))
                                                .executes(ctx -> setMaxBlocks(ctx.getSource(),
                                                        GameProfileArgument.getGameProfiles(ctx, "target"),
                                                        IntegerArgumentType.getInteger(ctx, "amount"))))))
                        .then(Commands.literal("info")
                                .executes(ctx -> limitsInfo(ctx.getSource()))
                                .then(Commands.argument("target", GameProfileArgument.gameProfile())
                                        .executes(ctx -> limitsInfoPlayer(ctx.getSource(),
                                                GameProfileArgument.getGameProfiles(ctx, "target")))))));
    }

    // -------------------------------------------------------------------------
    // /shopadmin reload
    // -------------------------------------------------------------------------

    private static int reloadCatalog(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        int activeSessions = ShopSessionManager.snapshotSessions().size();

        ShopCatalog.reload(server);
        ShopDataService.resendActiveSessions(server);

        source.sendSuccess(() -> Component.translatable(
                "command.futureshops.admin.reload.success", activeSessions)
                .withStyle(net.minecraft.ChatFormatting.GREEN), true);
        return 1;
    }

    // -------------------------------------------------------------------------
    // /shopadmin promo set|clear
    // -------------------------------------------------------------------------

    private static int setPromo(CommandSourceStack source, String shopId, String itemId, String type, double value) {
        if (!ShopCatalog.setRuntimePromo(shopId, itemId, type, value)) {
            source.sendFailure(Component.translatable("command.futureshops.admin.promo.set_failed")
                    .withStyle(net.minecraft.ChatFormatting.RED));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(
                "command.futureshops.admin.promo.set_success", shopId, itemId, type, value)
                .withStyle(net.minecraft.ChatFormatting.GREEN), true);
        return 1;
    }

    private static int clearPromo(CommandSourceStack source, String shopId, String itemId) {
        if (!ShopCatalog.clearRuntimePromo(shopId, itemId)) {
            source.sendFailure(Component.translatable(
                    "command.futureshops.admin.promo.clear_none", shopId, itemId)
                    .withStyle(net.minecraft.ChatFormatting.RED));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(
                "command.futureshops.admin.promo.clear_success", shopId, itemId)
                .withStyle(net.minecraft.ChatFormatting.GREEN), true);
        return 1;
    }

    // -------------------------------------------------------------------------
    // /shopadmin adminshop toggle
    // -------------------------------------------------------------------------

    private static int toggleAdminShop(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        AdminShopToggleSavedData data = AdminShopToggleSavedData.get(server);
        boolean newState = data.toggle();
        String messageKey = newState
                ? "command.futureshops.admin.adminshop.enabled"
                : "command.futureshops.admin.adminshop.disabled";
        source.sendSuccess(() -> Component.translatable(messageKey)
                .withStyle(net.minecraft.ChatFormatting.YELLOW), true);
        // Refresh all active sessions so they immediately see the change
        ShopDataService.resendActiveSessions(server);
        return 1;
    }

    // -------------------------------------------------------------------------
    // /shopadmin adminshop add | remove
    // -------------------------------------------------------------------------

    private static int startAdminShopAdd(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.translatable("command.futureshops.admin.adminshop.player_only")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        return AdminShopWizard.start(player) ? 1 : 0;
    }

    private static int respondToWizard(CommandSourceStack source, String text) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.translatable("command.futureshops.admin.adminshop.player_only")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        return AdminShopWizard.respond(player, text);
    }

    private static int adminShopRemoveHeld(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.translatable("command.futureshops.admin.adminshop.player_only")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        net.minecraft.world.item.ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            source.sendFailure(Component.translatable("command.futureshops.admin.adminshop.remove.no_held")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        net.minecraft.resources.ResourceLocation key = BuiltInRegistries.ITEM.getKey(held.getItem());
        if (key == null) {
            source.sendFailure(Component.translatable("command.futureshops.admin.adminshop.add.unknown_item")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        String itemId = key.toString();
        boolean removed = com.enviouse.futureshopsp.catalog.AdminShopConfigWriter.removeItem(source.getServer(), itemId);
        if (!removed) {
            source.sendFailure(Component.translatable(
                    "command.futureshops.admin.adminshop.remove.not_found", itemId)
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        ShopDataService.resendActiveSessions(source.getServer());
        source.sendSuccess(() -> Component.translatable(
                "command.futureshops.admin.adminshop.remove.success", itemId)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    // -------------------------------------------------------------------------
    // /shopadmin items add|edit|remove|list|info|refresh|sale
    // -------------------------------------------------------------------------

    /**
     * Handles {@code items add} ({@code editId == null}) and {@code items edit <id>}. Both capture the
     * held item's registry id + NBT and read the price / quantity / expire / nbt-flag / category
     * positionals; {@code sellPriceStr} is the optional trailing sell price (null ⇒ not sellable).
     */
    private static int itemsAddOrEdit(CommandContext<CommandSourceStack> ctx, String editId, String sellPriceStr) {
        CommandSourceStack source = ctx.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.translatable("command.futureshops.admin.items.player_only").withStyle(ChatFormatting.RED));
            return 0;
        }
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            source.sendFailure(Component.translatable("command.futureshops.admin.items.no_held").withStyle(ChatFormatting.RED));
            return 0;
        }
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(held.getItem());
        if (key == null) {
            source.sendFailure(Component.translatable("command.futureshops.admin.items.unknown_item").withStyle(ChatFormatting.RED));
            return 0;
        }

        long buyMinor;
        try {
            buyMinor = parsePriceMinor(StringArgumentType.getString(ctx, "price"));
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.translatable("command.futureshops.admin.items.invalid_price",
                    StringArgumentType.getString(ctx, "price")).withStyle(ChatFormatting.RED));
            return 0;
        }
        long sellMinor = 0L;
        if (sellPriceStr != null && !sellPriceStr.isBlank()) {
            try {
                sellMinor = parsePriceMinor(sellPriceStr);
            } catch (IllegalArgumentException e) {
                source.sendFailure(Component.translatable("command.futureshops.admin.items.invalid_price", sellPriceStr)
                        .withStyle(ChatFormatting.RED));
                return 0;
            }
        }
        int stock;
        try {
            stock = parseQuantity(StringArgumentType.getString(ctx, "quantity"));
        } catch (NumberFormatException e) {
            source.sendFailure(Component.translatable("command.futureshops.admin.items.invalid_quantity",
                    StringArgumentType.getString(ctx, "quantity")).withStyle(ChatFormatting.RED));
            return 0;
        }
        long expiresAt;
        try {
            expiresAt = parseExpiry(StringArgumentType.getString(ctx, "expire"));
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.translatable("command.futureshops.admin.items.invalid_expire",
                    StringArgumentType.getString(ctx, "expire")).withStyle(ChatFormatting.RED));
            return 0;
        }
        Boolean nbtOn = parseOnOff(StringArgumentType.getString(ctx, "nbt"));
        if (nbtOn == null) {
            source.sendFailure(Component.translatable("command.futureshops.admin.items.invalid_nbt",
                    StringArgumentType.getString(ctx, "nbt")).withStyle(ChatFormatting.RED));
            return 0;
        }

        String nbtJson = (nbtOn && !held.getComponentsPatch().isEmpty()) ? com.enviouse.futureshopsp.server.transaction.NbtMatchUtil.patchToSnbt(source.getServer().registryAccess(), held.getComponentsPatch()) : "";
        String rawCategory = StringArgumentType.getString(ctx, "category");
        String categoryId = normalizeId(rawCategory);
        ensureCategory(source.getServer(), rawCategory);
        String displayName = held.getHoverName().getString();
        String itemId = key.toString();

        MinecraftServer server = source.getServer();
        AdminShopItemSpec spec = new AdminShopItemSpec(
                editId == null ? "" : editId, itemId, displayName, buyMinor, sellMinor,
                stock, 0, categoryId, nbtJson, expiresAt);

        if (editId == null) {
            String assignedId = AdminShopConfigWriter.addWithGeneratedId(server, spec);
            if (assignedId == null || assignedId.isBlank()) {
                source.sendFailure(Component.translatable("command.futureshops.admin.items.write_failed").withStyle(ChatFormatting.RED));
                return 0;
            }
            ShopDataService.resendActiveSessions(server);
            final String fid = assignedId;
            final String fbuy = EconomyCommandUtil.formatMinorUnits(buyMinor, Config.economyCurrencyDecimals);
            final String fsell = EconomyCommandUtil.formatMinorUnits(sellMinor, Config.economyCurrencyDecimals);
            final String fstock = stockDisplay(stock);
            final String fexpire = expireDisplay(expiresAt);
            source.sendSuccess(() -> Component.translatable("command.futureshops.admin.items.added",
                    fid, itemId, fbuy, fsell, fstock, fexpire, categoryId).withStyle(ChatFormatting.GREEN), true);
            return 1;
        }

        boolean ok = AdminShopConfigWriter.editById(server, editId, spec);
        if (!ok) {
            source.sendFailure(Component.translatable("command.futureshops.admin.items.not_found", editId).withStyle(ChatFormatting.RED));
            return 0;
        }
        ShopDataService.resendActiveSessions(server);
        source.sendSuccess(() -> Component.translatable("command.futureshops.admin.items.edited", editId).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int itemsRemove(CommandSourceStack source, String id) {
        if (!AdminShopConfigWriter.removeById(source.getServer(), id)) {
            source.sendFailure(Component.translatable("command.futureshops.admin.items.not_found", id).withStyle(ChatFormatting.RED));
            return 0;
        }
        ShopDataService.resendActiveSessions(source.getServer());
        source.sendSuccess(() -> Component.translatable("command.futureshops.admin.items.removed", id).withStyle(ChatFormatting.YELLOW), true);
        return 1;
    }

    private static int itemsList(CommandSourceStack source, String categoryFilter) {
        List<AdminShopConfigWriter.AdminListingView> views = AdminShopConfigWriter.listItems(source.getServer(), categoryFilter);
        if (views.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.futureshops.admin.items.list_empty").withStyle(ChatFormatting.GRAY), false);
            return 1;
        }
        source.sendSuccess(() -> Component.translatable("command.futureshops.admin.items.list_header", views.size()).withStyle(ChatFormatting.GOLD), false);
        for (AdminShopConfigWriter.AdminListingView v : views) {
            String buy = EconomyCommandUtil.formatMinorUnits(v.buyPriceMinor(), Config.economyCurrencyDecimals);
            String stockStr = stockDisplay(v.stock());
            String nbtFlag = nbtDisplay(v.nbtPresent());
            source.sendSuccess(() -> Component.translatable("command.futureshops.admin.items.list_row",
                    v.listingId(), v.itemId(), buy, stockStr, nbtFlag).withStyle(ChatFormatting.WHITE), false);
        }
        return 1;
    }

    private static int itemsInfo(CommandSourceStack source, String id) {
        AdminShopConfigWriter.AdminListingView found = null;
        for (AdminShopConfigWriter.AdminListingView v : AdminShopConfigWriter.listItems(source.getServer(), null)) {
            if (v.listingId().equalsIgnoreCase(id)) {
                found = v;
                break;
            }
        }
        if (found == null) {
            source.sendFailure(Component.translatable("command.futureshops.admin.items.not_found", id).withStyle(ChatFormatting.RED));
            return 0;
        }
        AdminShopConfigWriter.AdminListingView v = found;
        String buy = EconomyCommandUtil.formatMinorUnits(v.buyPriceMinor(), Config.economyCurrencyDecimals);
        String sell = v.sellPriceMinor() > 0L
                ? EconomyCommandUtil.formatMinorUnits(v.sellPriceMinor(), Config.economyCurrencyDecimals)
                : Component.translatable("command.futureshops.admin.items.sell_none").getString();
        source.sendSuccess(() -> Component.translatable("command.futureshops.admin.items.info_header", v.listingId()).withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.translatable("command.futureshops.admin.items.info_item", v.itemId(), v.displayName()).withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.translatable("command.futureshops.admin.items.info_price", buy, sell).withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.translatable("command.futureshops.admin.items.info_stock", stockDisplay(v.stock()), refreshDisplay(v.stockRefreshSeconds())).withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.translatable("command.futureshops.admin.items.info_expire", expireDisplay(v.expiresAtEpoch())).withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.translatable("command.futureshops.admin.items.info_category", v.categoryId()).withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.translatable("command.futureshops.admin.items.info_nbt", nbtDisplay(v.nbtPresent())).withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int itemsRefresh(CommandSourceStack source, String id, String intervalStr) {
        String t = intervalStr.trim().toLowerCase(java.util.Locale.ROOT);
        boolean off = t.equals("off") || t.equals("0") || t.equals("none") || t.equals("false");
        int secs = 0;
        if (!off) {
            long parsed;
            try {
                parsed = parseDurationSeconds(t);
            } catch (IllegalArgumentException e) {
                source.sendFailure(Component.translatable("command.futureshops.admin.items.invalid_interval", intervalStr).withStyle(ChatFormatting.RED));
                return 0;
            }
            if (parsed <= 0L || parsed > Integer.MAX_VALUE) {
                source.sendFailure(Component.translatable("command.futureshops.admin.items.invalid_interval", intervalStr).withStyle(ChatFormatting.RED));
                return 0;
            }
            secs = (int) parsed;
        }
        if (!AdminShopConfigWriter.setStockRefresh(source.getServer(), id, secs)) {
            source.sendFailure(Component.translatable("command.futureshops.admin.items.not_found", id).withStyle(ChatFormatting.RED));
            return 0;
        }
        ShopDataService.resendActiveSessions(source.getServer());
        if (off) {
            source.sendSuccess(() -> Component.translatable("command.futureshops.admin.items.refresh_off", id).withStyle(ChatFormatting.YELLOW), true);
        } else {
            final String shown = secs + "s";
            source.sendSuccess(() -> Component.translatable("command.futureshops.admin.items.refresh_set", id, shown).withStyle(ChatFormatting.GREEN), true);
        }
        return 1;
    }

    private static int itemsSale(CommandSourceStack source, String id, String priceStr) {
        MinecraftServer server = source.getServer();
        String t = priceStr.trim().toLowerCase(java.util.Locale.ROOT);
        if (t.equals("off") || t.equals("none") || t.equals("0") || t.equals("clear")) {
            if (!ShopCatalog.clearRuntimePromo(ADMIN_SHOP_ID, id)) {
                source.sendFailure(Component.translatable("command.futureshops.admin.items.sale_none", id).withStyle(ChatFormatting.RED));
                return 0;
            }
            ShopDataService.resendActiveSessions(server);
            source.sendSuccess(() -> Component.translatable("command.futureshops.admin.items.sale_off", id).withStyle(ChatFormatting.YELLOW), true);
            return 1;
        }

        ItemDef def = ShopCatalog.getItem(ADMIN_SHOP_ID, id).orElse(null);
        if (def == null) {
            source.sendFailure(Component.translatable("command.futureshops.admin.items.not_found", id).withStyle(ChatFormatting.RED));
            return 0;
        }
        long saleMinor;
        try {
            saleMinor = parsePriceMinor(priceStr);
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.translatable("command.futureshops.admin.items.invalid_price", priceStr).withStyle(ChatFormatting.RED));
            return 0;
        }
        long basePrice = def.buyPriceMinorUnits();
        if (saleMinor <= 0L || saleMinor >= basePrice) {
            source.sendFailure(Component.translatable("command.futureshops.admin.items.sale_failed", id).withStyle(ChatFormatting.RED));
            return 0;
        }
        // FLAT promo: discountValue is amount-off in MAJOR units (see ShopCatalog.calculateLineCost),
        // so converting a desired final sale price -> (base - sale) / 10^decimals. Runtime promo,
        // not persisted (mirrors /shopadmin promo set); clears on reload/restart.
        double discountMajor = (basePrice - saleMinor) / Math.pow(10, Config.economyCurrencyDecimals);
        if (!ShopCatalog.setRuntimePromo(ADMIN_SHOP_ID, id, "FLAT", discountMajor)) {
            source.sendFailure(Component.translatable("command.futureshops.admin.items.sale_failed", id).withStyle(ChatFormatting.RED));
            return 0;
        }
        ShopDataService.resendActiveSessions(server);
        final String fsale = EconomyCommandUtil.formatMinorUnits(saleMinor, Config.economyCurrencyDecimals);
        source.sendSuccess(() -> Component.translatable("command.futureshops.admin.items.sale_set", id, fsale).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    // ─── items parsing / display helpers ─────────────────────────────────────

    /** Parses a price in major units to minor; accepts {@code 0}/{@code free}/{@code none} as 0 (not buyable/sellable). */
    private static long parsePriceMinor(String raw) {
        String t = raw.trim();
        if (t.equals("0") || t.equalsIgnoreCase("free") || t.equalsIgnoreCase("none")) return 0L;
        return EconomyCommandUtil.parseAmountToMinorUnits(t, Config.economyCurrencyDecimals);
    }

    /** Parses a stock quantity; {@code inf}/{@code infinity}/{@code unlimited}/{@code -1} ⇒ unlimited (-1). */
    private static int parseQuantity(String raw) {
        String t = raw.trim().toLowerCase(java.util.Locale.ROOT);
        if (t.equals("inf") || t.equals("infinity") || t.equals("infinite") || t.equals("unlimited") || t.equals("-1")) {
            return -1;
        }
        int v = Integer.parseInt(t);
        return v < 0 ? -1 : v;
    }

    /** Parses the availability window into an absolute epoch; {@code inf}/{@code never}/{@code 0} ⇒ 0 (never). */
    private static long parseExpiry(String raw) {
        String t = raw.trim().toLowerCase(java.util.Locale.ROOT);
        if (t.isEmpty() || t.equals("inf") || t.equals("infinity") || t.equals("never") || t.equals("0") || t.equals("none")) {
            return 0L;
        }
        long durationSec = parseDurationSeconds(t);
        if (durationSec <= 0L) throw new IllegalArgumentException("expire must be > 0");
        try {
            return Math.addExact(System.currentTimeMillis() / 1000L, durationSec);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("expire overflow", e);
        }
    }

    /** Plain seconds, or a suffixed shorthand: {@code 30s}, {@code 5m}, {@code 1h}, {@code 7d}. */
    private static long parseDurationSeconds(String raw) {
        String t = raw.trim().toLowerCase(java.util.Locale.ROOT);
        if (t.isEmpty()) return 0L;
        char last = t.charAt(t.length() - 1);
        long mult;
        String numPart;
        if (last == 's') { mult = 1L; numPart = t.substring(0, t.length() - 1); }
        else if (last == 'm') { mult = 60L; numPart = t.substring(0, t.length() - 1); }
        else if (last == 'h') { mult = 3600L; numPart = t.substring(0, t.length() - 1); }
        else if (last == 'd') { mult = 86400L; numPart = t.substring(0, t.length() - 1); }
        else { mult = 1L; numPart = t; }
        try {
            return Math.multiplyExact(Long.parseLong(numPart.trim()), mult);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("duration overflow", e);
        }
    }

    /** {@code on/true/yes/1} ⇒ TRUE, {@code off/false/no/0} ⇒ FALSE, otherwise {@code null}. */
    private static Boolean parseOnOff(String raw) {
        String t = raw.trim().toLowerCase(java.util.Locale.ROOT);
        if (t.equals("on") || t.equals("true") || t.equals("yes") || t.equals("1")) return Boolean.TRUE;
        if (t.equals("off") || t.equals("false") || t.equals("no") || t.equals("0")) return Boolean.FALSE;
        return null;
    }

    private static String normalizeId(String s) {
        return s == null ? "all" : s.trim().toLowerCase(java.util.Locale.ROOT).replace(' ', '_');
    }

    /** Registers a category (admin.json + saved data + department) so its tab appears — mirrors the wizard. */
    private static void ensureCategory(MinecraftServer server, String rawCategory) {
        if (rawCategory == null) return;
        String trimmed = rawCategory.trim();
        if (trimmed.isEmpty() || "all".equalsIgnoreCase(trimmed)) return;
        AdminShopConfigWriter.addCategory(server, trimmed);
        AdminCategorySavedData.get(server).addCategory(trimmed);
        com.enviouse.futureshopsp.server.shop.DepartmentSavedData.get(server).addDepartment(trimmed);
    }

    private static String stockDisplay(int stock) {
        return stock < 0
                ? Component.translatable("command.futureshops.admin.limits.unlimited").getString()
                : Integer.toString(stock);
    }

    private static String refreshDisplay(int secs) {
        return secs > 0 ? secs + "s"
                : Component.translatable("command.futureshops.admin.items.refresh_none").getString();
    }

    private static String expireDisplay(long epoch) {
        return epoch <= 0L
                ? Component.translatable("command.futureshops.admin.items.expire_never").getString()
                : TS_FMT.format(Instant.ofEpochSecond(epoch));
    }

    private static String nbtDisplay(boolean present) {
        return Component.translatable(present
                ? "command.futureshops.admin.items.nbt_present"
                : "command.futureshops.admin.items.nbt_absent").getString();
    }

    // -------------------------------------------------------------------------
    // /shopadmin bal add|remove|set|check|reset
    // -------------------------------------------------------------------------

    private static int adminBalAdd(CommandSourceStack source, Collection<GameProfile> targets, String amountStr) {
        EconomyProvider provider = BalanceManager.getProvider();
        long amountMinor;
        try {
            amountMinor = EconomyCommandUtil.parseAmountToMinorUnits(amountStr, provider.getDecimalPlaces());
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.translatable("command.futureshops.admin.bal.invalid_amount", amountStr)
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        int successCount = 0;
        for (GameProfile profile : targets) {
            UUID uuid = profile.getId();
            TransactionResult result = provider.deposit(uuid, amountMinor, "ADMIN");
            String formatted = EconomyCommandUtil.formatMinorUnits(amountMinor, provider.getDecimalPlaces());
            if (result.success()) {
                String newBal = EconomyCommandUtil.formatMinorUnits(result.resultingBalance(), provider.getDecimalPlaces());
                source.sendSuccess(() -> Component.translatable(
                        "command.futureshops.admin.bal.add_success",
                        formatted, provider.getCurrencyName(), profile.getName(), newBal)
                        .withStyle(ChatFormatting.GREEN), true);
                successCount++;
            } else {
                source.sendFailure(Component.translatable(
                        "command.futureshops.admin.bal.failed", profile.getName(), result.errorCode())
                        .withStyle(ChatFormatting.RED));
            }
        }
        return successCount;
    }

    private static int adminBalRemove(CommandSourceStack source, Collection<GameProfile> targets, String amountStr) {
        EconomyProvider provider = BalanceManager.getProvider();
        long amountMinor;
        try {
            amountMinor = EconomyCommandUtil.parseAmountToMinorUnits(amountStr, provider.getDecimalPlaces());
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.translatable("command.futureshops.admin.bal.invalid_amount", amountStr)
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        int successCount = 0;
        for (GameProfile profile : targets) {
            UUID uuid = profile.getId();
            TransactionResult result = provider.withdraw(uuid, amountMinor, "ADMIN");
            String formatted = EconomyCommandUtil.formatMinorUnits(amountMinor, provider.getDecimalPlaces());
            if (result.success()) {
                String newBal = EconomyCommandUtil.formatMinorUnits(result.resultingBalance(), provider.getDecimalPlaces());
                source.sendSuccess(() -> Component.translatable(
                        "command.futureshops.admin.bal.remove_success",
                        formatted, provider.getCurrencyName(), profile.getName(), newBal)
                        .withStyle(ChatFormatting.YELLOW), true);
                successCount++;
            } else {
                source.sendFailure(Component.translatable(
                        "command.futureshops.admin.bal.failed", profile.getName(), result.errorCode())
                        .withStyle(ChatFormatting.RED));
            }
        }
        return successCount;
    }

    private static int adminBalSet(CommandSourceStack source, Collection<GameProfile> targets, String amountStr) {
        EconomyProvider provider = BalanceManager.getProvider();
        long amountMinor;
        try {
            // Allow 0 for set
            java.math.BigDecimal parsed = new java.math.BigDecimal(amountStr.trim());
            if (parsed.signum() < 0) {
                throw new IllegalArgumentException("NEGATIVE");
            }
            java.math.BigDecimal scaled = parsed.movePointRight(provider.getDecimalPlaces());
            if (scaled.stripTrailingZeros().scale() > 0) {
                throw new IllegalArgumentException("TOO_MANY_DECIMALS");
            }
            amountMinor = scaled.longValueExact();
        } catch (Exception e) {
            source.sendFailure(Component.translatable("command.futureshops.admin.bal.invalid_amount", amountStr)
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        // Direct set via the saved data — bypasses max_balance checks (admin override)
        MinecraftServer server = source.getServer();
        net.minecraft.server.level.ServerLevel overworld = server.overworld();
        com.enviouse.futureshopsp.server.economy.InternalBalanceSavedData balData = overworld.getDataStorage()
                .computeIfAbsent(new net.minecraft.world.level.saveddata.SavedData.Factory<>(com.enviouse.futureshopsp.server.economy.InternalBalanceSavedData::new, com.enviouse.futureshopsp.server.economy.InternalBalanceSavedData::load, null), com.enviouse.futureshopsp.server.economy.InternalBalanceSavedData.DATA_NAME);

        int successCount = 0;
        String formatted = EconomyCommandUtil.formatMinorUnits(amountMinor, provider.getDecimalPlaces());
        final long setAmount = amountMinor;
        for (GameProfile profile : targets) {
            long oldBalance = provider.getBalance(profile.getId());
            balData.setBalance(profile.getId(), setAmount);
            // Fire BalanceChangeEvent.Post (spec §33) — admin set bypasses Pre since it's non-cancellable admin action
            long delta = setAmount - oldBalance;
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                    new com.enviouse.futureshopsp.event.BalanceChangeEvent.Post(profile.getId(), delta, "ADMIN", setAmount));
            source.sendSuccess(() -> Component.translatable(
                    "command.futureshops.admin.bal.set_success",
                    profile.getName(), formatted, provider.getCurrencyName())
                    .withStyle(ChatFormatting.YELLOW), true);
            successCount++;
        }
        return successCount;
    }

    private static int adminBalCheck(CommandSourceStack source, Collection<GameProfile> targets) {
        EconomyProvider provider = BalanceManager.getProvider();
        for (GameProfile profile : targets) {
            long balance = provider.getBalance(profile.getId());
            String formatted = EconomyCommandUtil.formatMinorUnits(balance, provider.getDecimalPlaces());
            source.sendSuccess(() -> Component.translatable(
                    "command.futureshops.admin.bal.check_line",
                    profile.getName(), formatted, provider.getCurrencyName())
                    .withStyle(ChatFormatting.GRAY), false);
        }
        return targets.size();
    }

    private static int adminBalReset(CommandSourceStack source, Collection<GameProfile> targets) {
        EconomyProvider provider = BalanceManager.getProvider();
        long startingBalance = com.enviouse.futureshopsp.Config.economyStartingBalanceMinorUnits;
        String formatted = EconomyCommandUtil.formatMinorUnits(startingBalance, provider.getDecimalPlaces());

        MinecraftServer server = source.getServer();
        net.minecraft.server.level.ServerLevel overworld = server.overworld();
        com.enviouse.futureshopsp.server.economy.InternalBalanceSavedData balData = overworld.getDataStorage()
                .computeIfAbsent(new net.minecraft.world.level.saveddata.SavedData.Factory<>(com.enviouse.futureshopsp.server.economy.InternalBalanceSavedData::new, com.enviouse.futureshopsp.server.economy.InternalBalanceSavedData::load, null), com.enviouse.futureshopsp.server.economy.InternalBalanceSavedData.DATA_NAME);

        for (GameProfile profile : targets) {
            long oldBalance = provider.getBalance(profile.getId());
            balData.setBalance(profile.getId(), startingBalance);
            // Fire BalanceChangeEvent.Post (spec §33) — admin reset
            long delta = startingBalance - oldBalance;
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                    new com.enviouse.futureshopsp.event.BalanceChangeEvent.Post(profile.getId(), delta, "ADMIN", startingBalance));
            source.sendSuccess(() -> Component.translatable(
                    "command.futureshops.admin.bal.reset_success",
                    profile.getName(), formatted, provider.getCurrencyName())
                    .withStyle(ChatFormatting.YELLOW), true);
        }
        return targets.size();
    }

    // -------------------------------------------------------------------------
    // /shopadmin view <player>
    // -------------------------------------------------------------------------

    private static int adminView(CommandSourceStack source, Collection<GameProfile> targets) {
        if (!(source.getEntity() instanceof ServerPlayer admin)) {
            source.sendFailure(Component.translatable("command.futureshops.admin.view.player_only")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        MinecraftServer server = source.getServer();

        // Take the first target profile
        GameProfile targetProfile = targets.iterator().next();
        UUID targetUuid = targetProfile.getId();
        String targetName = targetProfile.getName();

        // Check if the target is online — if so, send their full dashboard
        ServerPlayer targetPlayer = server.getPlayerList().getPlayer(targetUuid);
        if (targetPlayer != null) {
            // Build and send the target player's dashboard to the admin
            MarketplaceAnalyticsService.sendDashboardForViewer(admin, targetPlayer);
            source.sendSuccess(() -> Component.translatable(
                    "command.futureshops.admin.view.opening", targetName)
                    .withStyle(ChatFormatting.GRAY), false);
        } else {
            // Offline player — send a lightweight balance-only view
            EconomyProvider provider = BalanceManager.getProvider();
            long balance = provider.getBalance(targetUuid);
            String formatted = EconomyCommandUtil.formatMinorUnits(balance, provider.getDecimalPlaces());
            source.sendSuccess(() -> Component.translatable(
                    "command.futureshops.admin.view.offline_header", targetName)
                    .withStyle(ChatFormatting.GOLD), false);
            source.sendSuccess(() -> Component.translatable(
                    "command.futureshops.admin.view.offline_balance", formatted, provider.getCurrencyName())
                    .withStyle(ChatFormatting.GRAY), false);
            source.sendSuccess(() -> Component.translatable(
                    "command.futureshops.admin.view.offline_uuid", targetUuid)
                    .withStyle(ChatFormatting.DARK_GRAY), false);
            source.sendSuccess(() -> Component.translatable(
                    "command.futureshops.admin.view.offline_hint")
                    .withStyle(ChatFormatting.DARK_GRAY), false);
        }
        return 1;
    }

    // -------------------------------------------------------------------------
    // /shopadmin coinaudit <player>
    // -------------------------------------------------------------------------

    private static int addCategory(CommandSourceStack source, String name) {
        MinecraftServer server = source.getServer();
        AdminCategorySavedData data = AdminCategorySavedData.get(server);
        boolean addedSaved = data.addCategory(name);
        boolean addedJson = com.enviouse.futureshopsp.catalog.AdminShopConfigWriter.addCategory(server, name);
        if (addedSaved || addedJson) {
            // Also register in the player-shop department registry so they appear in autocomplete
            com.enviouse.futureshopsp.server.shop.DepartmentSavedData.get(server).addDepartment(name);
            source.sendSuccess(() -> Component.translatable(
                    "command.futureshops.admin.category.added", name.trim())
                    .withStyle(net.minecraft.ChatFormatting.GREEN), true);
            ShopDataService.resendActiveSessions(server);
        } else {
            source.sendFailure(Component.translatable(
                    "command.futureshops.admin.category.add_failed", name)
                    .withStyle(net.minecraft.ChatFormatting.RED));
        }
        return 1;
    }

    private static int removeCategory(CommandSourceStack source, String name) {
        MinecraftServer server = source.getServer();
        AdminCategorySavedData data = AdminCategorySavedData.get(server);

        boolean removedSaved = data.removeCategory(name);
        boolean removedJson = com.enviouse.futureshopsp.catalog.AdminShopConfigWriter.removeCategory(server, name);

        if (removedSaved || removedJson) {
            source.sendSuccess(() -> Component.translatable(
                    "command.futureshops.admin.category.removed", name.trim())
                    .withStyle(net.minecraft.ChatFormatting.YELLOW), true);
            ShopDataService.resendActiveSessions(server);
            return 1;
        }
        // Fall back to hiding a bundled (JSON) category that wasn't physically removable
        // (e.g. extra shop file the admin doesn't want to edit directly).
        Optional<com.enviouse.futureshopsp.catalog.CategoryDef> bundled =
                ShopCatalog.findBundledCategory(name);
        if (bundled.isPresent() && data.hideBaseCategory(bundled.get().id())) {
            String shown = bundled.get().displayName();
            source.sendSuccess(() -> Component.translatable(
                    "command.futureshops.admin.category.hidden", shown)
                    .withStyle(net.minecraft.ChatFormatting.YELLOW), true);
            ShopDataService.resendActiveSessions(server);
            return 1;
        }
        source.sendFailure(Component.translatable(
                "command.futureshops.admin.category.remove_failed", name)
                .withStyle(net.minecraft.ChatFormatting.RED));
        return 0;
    }

    private static int restoreCategory(CommandSourceStack source, String name) {
        MinecraftServer server = source.getServer();
        AdminCategorySavedData data = AdminCategorySavedData.get(server);
        Optional<com.enviouse.futureshopsp.catalog.CategoryDef> bundled =
                ShopCatalog.findBundledCategory(name);
        String idToRestore = bundled.map(com.enviouse.futureshopsp.catalog.CategoryDef::id).orElse(name);
        if (data.restoreBaseCategory(idToRestore)) {
            String shown = bundled.map(com.enviouse.futureshopsp.catalog.CategoryDef::displayName).orElse(name.trim());
            source.sendSuccess(() -> Component.translatable(
                    "command.futureshops.admin.category.restored", shown)
                    .withStyle(net.minecraft.ChatFormatting.GREEN), true);
            ShopDataService.resendActiveSessions(server);
            return 1;
        }
        source.sendFailure(Component.translatable(
                "command.futureshops.admin.category.restore_failed", name)
                .withStyle(net.minecraft.ChatFormatting.RED));
        return 0;
    }

    private static int listCategories(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        AdminCategorySavedData data = AdminCategorySavedData.get(server);
        List<String> categories = data.getAllSorted();
        if (categories.isEmpty()) {
            source.sendSuccess(() -> Component.translatable(
                    "command.futureshops.admin.category.list_empty")
                    .withStyle(net.minecraft.ChatFormatting.GRAY), false);
        } else {
            source.sendSuccess(() -> Component.translatable(
                    "command.futureshops.admin.category.list_header", categories.size())
                    .withStyle(net.minecraft.ChatFormatting.GOLD), false);
            for (String cat : categories) {
                int itemCount = data.getItemsInCategory(cat).size();
                source.sendSuccess(() -> Component.translatable(
                        "command.futureshops.admin.category.list_row", cat, itemCount)
                        .withStyle(net.minecraft.ChatFormatting.WHITE), false);
            }
        }

        java.util.Set<String> hiddenIds = data.getHiddenBaseCategoryIds();
        if (!hiddenIds.isEmpty()) {
            source.sendSuccess(() -> Component.translatable(
                    "command.futureshops.admin.category.hidden_header", hiddenIds.size())
                    .withStyle(net.minecraft.ChatFormatting.GOLD), false);
            java.util.LinkedHashSet<String> shown = new java.util.LinkedHashSet<>();
            for (com.enviouse.futureshopsp.catalog.ShopDefinition def : ShopCatalog.getAllDefinitions()) {
                for (com.enviouse.futureshopsp.catalog.CategoryDef cat : def.categories()) {
                    if (hiddenIds.contains(cat.id().toLowerCase(java.util.Locale.ROOT))) {
                        shown.add(cat.displayName());
                    }
                }
            }
            for (String id : hiddenIds) {
                if (shown.stream().noneMatch(n -> n.equalsIgnoreCase(id))) shown.add(id);
            }
            for (String name : shown) {
                source.sendSuccess(() -> Component.translatable(
                        "command.futureshops.admin.category.hidden_row", name)
                        .withStyle(net.minecraft.ChatFormatting.GRAY), false);
            }
        }
        return 1;
    }

    private static int assignItem(CommandSourceStack source, String categoryName, String itemId) {
        MinecraftServer server = source.getServer();
        AdminCategorySavedData data = AdminCategorySavedData.get(server);
        if (data.assignItem(itemId, categoryName)) {
            source.sendSuccess(() -> Component.translatable(
                    "command.futureshops.admin.category.assigned", itemId, categoryName)
                    .withStyle(net.minecraft.ChatFormatting.GREEN), true);
            ShopDataService.resendActiveSessions(server);
        } else {
            source.sendFailure(Component.translatable(
                    "command.futureshops.admin.category.assign_failed", categoryName)
                    .withStyle(net.minecraft.ChatFormatting.RED));
        }
        return 1;
    }

    private static int unassignItem(CommandSourceStack source, String itemId) {
        MinecraftServer server = source.getServer();
        AdminCategorySavedData data = AdminCategorySavedData.get(server);
        if (data.unassignItem(itemId)) {
            source.sendSuccess(() -> Component.translatable(
                    "command.futureshops.admin.category.unassigned", itemId)
                    .withStyle(net.minecraft.ChatFormatting.YELLOW), true);
            ShopDataService.resendActiveSessions(server);
        } else {
            source.sendFailure(Component.translatable(
                    "command.futureshops.admin.category.unassign_failed", itemId)
                    .withStyle(net.minecraft.ChatFormatting.RED));
        }
        return 1;
    }

    private static int listCategoryItems(CommandSourceStack source, String categoryName) {
        MinecraftServer server = source.getServer();
        AdminCategorySavedData data = AdminCategorySavedData.get(server);
        List<String> items = data.getItemsInCategory(categoryName);
        if (items.isEmpty()) {
            source.sendSuccess(() -> Component.translatable(
                    "command.futureshops.admin.category.items_empty", categoryName, categoryName)
                    .withStyle(net.minecraft.ChatFormatting.GRAY), false);
        } else {
            source.sendSuccess(() -> Component.translatable(
                    "command.futureshops.admin.category.items_header", categoryName, items.size())
                    .withStyle(net.minecraft.ChatFormatting.GOLD), false);
            for (String item : items) {
                source.sendSuccess(() -> Component.translatable(
                        "command.futureshops.admin.category.items_row", item)
                        .withStyle(net.minecraft.ChatFormatting.WHITE), false);
            }
        }
        return 1;
    }

    private static int coinAudit(CommandContext<CommandSourceStack> ctx, String playerName) {
        CommandSourceStack src    = ctx.getSource();
        MinecraftServer    server = src.getServer();

        // 1. Resolve player name → UUID (online first, then profile cache)
        UUID targetUUID = resolveUUID(server, playerName);
        if (targetUUID == null) {
            src.sendFailure(Component.translatable(
                    "command.futureshops.admin.coinaudit.unknown_player", playerName)
                    .withStyle(net.minecraft.ChatFormatting.RED));
            return 0;
        }

        // 2. Pull the immutable snapshot of the mint registry
        SpentMintsSavedData mintData = SpentMintsSavedData.get(server);
        Map<String, MoneyMintRecord> snapshot = mintData.snapshotRegistry();

        // 3. Filter and sort by minted-at descending
        List<MoneyMintRecord> playerMints = snapshot.values().stream()
                .filter(r -> targetUUID.equals(r.playerUUID()))
                .sorted(Comparator.comparingLong(MoneyMintRecord::mintedAt).reversed())
                .toList();

        // 4. Aggregate stats
        long activeCount   = playerMints.stream().filter(r -> !r.consumed()).count();
        long consumedCount = playerMints.stream().filter(MoneyMintRecord::consumed).count();
        long activeValue   = playerMints.stream()
                .filter(r -> !r.consumed())
                .mapToLong(r -> r.denomination() * r.remainingCount())
                .sum();

        // 5. Send output
        src.sendSuccess(() -> Component.translatable(
                "command.futureshops.admin.coinaudit.header", playerName)
                .withStyle(net.minecraft.ChatFormatting.AQUA), false);
        src.sendSuccess(() -> Component.translatable(
                "command.futureshops.admin.coinaudit.counts",
                playerMints.size(), activeCount, consumedCount)
                .withStyle(net.minecraft.ChatFormatting.GRAY), false);
        src.sendSuccess(() -> Component.translatable(
                "command.futureshops.admin.coinaudit.active_value", activeValue, targetUUID)
                .withStyle(net.minecraft.ChatFormatting.GREEN), false);

        if (playerMints.isEmpty()) {
            src.sendSuccess(() -> Component.translatable(
                    "command.futureshops.admin.coinaudit.no_mints")
                    .withStyle(net.minecraft.ChatFormatting.GOLD), false);
            return 1;
        }

        src.sendSuccess(() -> Component.translatable(
                "command.futureshops.admin.coinaudit.recent_header")
                .withStyle(net.minecraft.ChatFormatting.DARK_AQUA), false);

        int shown = Math.min(playerMints.size(), 15);
        for (int i = 0; i < shown; i++) {
            MoneyMintRecord r = playerMints.get(i);
            String status  = r.consumed() ? "§cCONSUMED§r" : "§aACTIVE  §r";
            String mintTs  = TS_FMT.format(Instant.ofEpochSecond(r.mintedAt()));
            String eatTs   = r.consumed()
                    ? " → " + TS_FMT.format(Instant.ofEpochSecond(r.consumedAt()))
                    : "";
            // Show first 8 chars of mintId to keep lines tidy
            String shortId = r.mintId().length() > 8 ? r.mintId().substring(0, 8) + "…" : r.mintId();
            final String countDisplay = r.remainingCount() + "/" + r.authorizedCount();
            src.sendSuccess(() -> Component.translatable(
                    "command.futureshops.admin.coinaudit.row",
                    status, shortId, r.denomination(), countDisplay, mintTs, eatTs)
                    .withStyle(net.minecraft.ChatFormatting.GRAY), false);
        }

        if (playerMints.size() > shown) {
            int extra = playerMints.size() - shown;
            src.sendSuccess(() -> Component.translatable(
                    "command.futureshops.admin.coinaudit.more", extra)
                    .withStyle(net.minecraft.ChatFormatting.DARK_GRAY), false);
        }

        return 1;
    }

    // -------------------------------------------------------------------------
    // /shopadmin limits maxlistings|maxblocks|info
    // -------------------------------------------------------------------------

    private static int setMaxListings(CommandSourceStack source, int amount) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.translatable("command.futureshops.admin.limits.player_only")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        // Raycast to find the shop block the player is looking at
        net.minecraft.world.phys.BlockHitResult hit = (net.minecraft.world.phys.BlockHitResult)
                player.pick(6.0D, 0.0F, false);
        if (!(player.level().getBlockEntity(hit.getBlockPos()) instanceof ShopBlockEntity shop)) {
            source.sendFailure(Component.translatable("command.futureshops.admin.limits.no_shop_block")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        shop.setMaxListings(amount);
        Component display = unlimitedOr(amount);
        source.sendSuccess(() -> Component.translatable(
                "command.futureshops.admin.limits.maxlistings_set", display)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setMaxBlocks(CommandSourceStack source, Collection<GameProfile> targets, int amount) {
        MinecraftServer server = source.getServer();
        ShopLimitsSavedData limits = ShopLimitsSavedData.get(server);
        Component display = unlimitedOr(amount);
        for (GameProfile profile : targets) {
            limits.setMaxShopBlocks(profile.getId(), amount);
            source.sendSuccess(() -> Component.translatable(
                    "command.futureshops.admin.limits.maxblocks_set", profile.getName(), display)
                    .withStyle(ChatFormatting.GREEN), true);
        }
        return targets.size();
    }

    private static int limitsInfo(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.translatable("command.futureshops.admin.limits.player_only")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        net.minecraft.world.phys.BlockHitResult hit = (net.minecraft.world.phys.BlockHitResult)
                player.pick(6.0D, 0.0F, false);
        if (!(player.level().getBlockEntity(hit.getBlockPos()) instanceof ShopBlockEntity shop)) {
            source.sendFailure(Component.translatable("command.futureshops.admin.limits.no_shop_block")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        int maxL = shop.getMaxListings();
        Component listingDisplay = unlimitedOr(maxL);
        int currentListings = shop.getListings().size();
        source.sendSuccess(() -> Component.translatable("command.futureshops.admin.limits.info_header")
                .withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.translatable(
                "command.futureshops.admin.limits.info_listings", listingDisplay, currentListings)
                .withStyle(ChatFormatting.GRAY), false);
        if (shop.getOwnerUuid() != null) {
            MinecraftServer server = source.getServer();
            ShopLimitsSavedData limits = ShopLimitsSavedData.get(server);
            int maxB = limits.getMaxShopBlocks(shop.getOwnerUuid());
            Component blockDisplay = unlimitedOr(maxB);
            int owned = PlayerShopRegistrySavedData.get(server).getOwnedShops(shop.getOwnerUuid()).size();
            source.sendSuccess(() -> Component.translatable(
                    "command.futureshops.admin.limits.info_blocks", blockDisplay, owned)
                    .withStyle(ChatFormatting.GRAY), false);
        }
        return 1;
    }

    private static int limitsInfoPlayer(CommandSourceStack source, Collection<GameProfile> targets) {
        MinecraftServer server = source.getServer();
        ShopLimitsSavedData limits = ShopLimitsSavedData.get(server);
        for (GameProfile profile : targets) {
            int max = limits.getMaxShopBlocks(profile.getId());
            Component display = unlimitedOr(max);
            int owned = PlayerShopRegistrySavedData.get(server).getOwnedShops(profile.getId()).size();
            source.sendSuccess(() -> Component.translatable(
                    "command.futureshops.admin.limits.info_player_row",
                    profile.getName(), display, owned)
                    .withStyle(ChatFormatting.GRAY), false);
        }
        return targets.size();
    }

    /**
     * Renders a shop-limit amount — negative values mean "no cap" and resolve through the
     * {@code command.futureshops.admin.limits.unlimited} lang key so translators can
     * localize the fallback word ("unlimited" / "illimité" / "無制限" / etc.) without a
     * code change.
     */
    private static Component unlimitedOr(int amount) {
        if (amount < 0) {
            return Component.translatable("command.futureshops.admin.limits.unlimited");
        }
        return Component.literal(String.valueOf(amount));
    }

    // -------------------------------------------------------------------------
    // Player resolution helpers
    // -------------------------------------------------------------------------

    /**
     * Resolves a player name to a UUID.
     * Checks online players first, then falls back to the server's profile cache
     * (covers any player who has logged in at least once).
     *
     * @return the UUID, or {@code null} if the player cannot be identified
     */
    private static UUID resolveUUID(MinecraftServer server, String playerName) {
        // Online lookup (fastest path)
        ServerPlayer online = server.getPlayerList().getPlayerByName(playerName);
        if (online != null) {
            return online.getUUID();
        }

        // Profile cache lookup (offline players who have logged in before)
        try {
            if (server.getProfileCache() != null) {
                Optional<GameProfile> profile = server.getProfileCache().get(playerName);
                if (profile.isPresent()) {
                    return profile.get().getId();
                }
            }
        } catch (Exception ignored) {
            // Profile cache unavailable (e.g., offline-mode server without cache)
        }

        return null;
    }
}


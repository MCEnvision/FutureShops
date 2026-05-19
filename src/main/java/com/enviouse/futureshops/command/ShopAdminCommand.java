package com.enviouse.futureshops.command;

import com.enviouse.futureshops.catalog.ShopCatalog;
import com.enviouse.futureshops.money.MoneyMintRecord;
import com.enviouse.futureshops.money.SpentMintsSavedData;
import com.enviouse.futureshops.server.economy.BalanceManager;
import com.enviouse.futureshops.server.economy.EconomyProvider;
import com.enviouse.futureshops.server.economy.TransactionResult;
import com.enviouse.futureshops.server.session.ShopSessionManager;
import com.enviouse.futureshops.server.shop.AdminShopToggleSavedData;
import com.enviouse.futureshops.server.shop.AdminCategorySavedData;
import com.enviouse.futureshops.server.shop.MarketplaceAnalyticsService;
import com.enviouse.futureshops.server.shop.ShopDataService;
import com.enviouse.futureshops.server.shop.ShopLimitsSavedData;
import com.enviouse.futureshops.server.shop.PlayerShopRegistrySavedData;
import com.enviouse.futureshops.block.ShopBlockEntity;
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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.registries.ForgeRegistries;

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
        for (com.enviouse.futureshops.catalog.ShopDefinition def : ShopCatalog.getAllDefinitions()) {
            for (com.enviouse.futureshops.catalog.CategoryDef cat : def.categories()) {
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
        for (com.enviouse.futureshops.catalog.ShopDefinition def : ShopCatalog.getAllDefinitions()) {
            for (com.enviouse.futureshops.catalog.CategoryDef cat : def.categories()) {
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
                    ForgeRegistries.ITEMS.getKeys().stream().map(net.minecraft.resources.ResourceLocation::toString),
                    builder);

    /** Suggests only item IDs that currently have an admin category assignment. */
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_ASSIGNED_ITEMS = (ctx, builder) -> {
        MinecraftServer server = ctx.getSource().getServer();
        return SharedSuggestionProvider.suggest(
                AdminCategorySavedData.get(server).getAllAssignments().keySet(), builder);
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
        net.minecraft.resources.ResourceLocation key = ForgeRegistries.ITEMS.getKey(held.getItem());
        if (key == null) {
            source.sendFailure(Component.translatable("command.futureshops.admin.adminshop.add.unknown_item")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        String itemId = key.toString();
        boolean removed = com.enviouse.futureshops.catalog.AdminShopConfigWriter.removeItem(source.getServer(), itemId);
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
        com.enviouse.futureshops.server.economy.InternalBalanceSavedData balData = overworld.getDataStorage()
                .computeIfAbsent(com.enviouse.futureshops.server.economy.InternalBalanceSavedData::load,
                        com.enviouse.futureshops.server.economy.InternalBalanceSavedData::new,
                        com.enviouse.futureshops.server.economy.InternalBalanceSavedData.DATA_NAME);

        int successCount = 0;
        String formatted = EconomyCommandUtil.formatMinorUnits(amountMinor, provider.getDecimalPlaces());
        final long setAmount = amountMinor;
        for (GameProfile profile : targets) {
            long oldBalance = provider.getBalance(profile.getId());
            balData.setBalance(profile.getId(), setAmount);
            // Fire BalanceChangeEvent.Post (spec §33) — admin set bypasses Pre since it's non-cancellable admin action
            long delta = setAmount - oldBalance;
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                    new com.enviouse.futureshops.event.BalanceChangeEvent.Post(profile.getId(), delta, "ADMIN", setAmount));
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
        long startingBalance = com.enviouse.futureshops.Config.economyStartingBalanceMinorUnits;
        String formatted = EconomyCommandUtil.formatMinorUnits(startingBalance, provider.getDecimalPlaces());

        MinecraftServer server = source.getServer();
        net.minecraft.server.level.ServerLevel overworld = server.overworld();
        com.enviouse.futureshops.server.economy.InternalBalanceSavedData balData = overworld.getDataStorage()
                .computeIfAbsent(com.enviouse.futureshops.server.economy.InternalBalanceSavedData::load,
                        com.enviouse.futureshops.server.economy.InternalBalanceSavedData::new,
                        com.enviouse.futureshops.server.economy.InternalBalanceSavedData.DATA_NAME);

        for (GameProfile profile : targets) {
            long oldBalance = provider.getBalance(profile.getId());
            balData.setBalance(profile.getId(), startingBalance);
            // Fire BalanceChangeEvent.Post (spec §33) — admin reset
            long delta = startingBalance - oldBalance;
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                    new com.enviouse.futureshops.event.BalanceChangeEvent.Post(profile.getId(), delta, "ADMIN", startingBalance));
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
        boolean addedJson = com.enviouse.futureshops.catalog.AdminShopConfigWriter.addCategory(server, name);
        if (addedSaved || addedJson) {
            // Also register in the player-shop department registry so they appear in autocomplete
            com.enviouse.futureshops.server.shop.DepartmentSavedData.get(server).addDepartment(name);
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
        boolean removedJson = com.enviouse.futureshops.catalog.AdminShopConfigWriter.removeCategory(server, name);

        if (removedSaved || removedJson) {
            source.sendSuccess(() -> Component.translatable(
                    "command.futureshops.admin.category.removed", name.trim())
                    .withStyle(net.minecraft.ChatFormatting.YELLOW), true);
            ShopDataService.resendActiveSessions(server);
            return 1;
        }
        // Fall back to hiding a bundled (JSON) category that wasn't physically removable
        // (e.g. extra shop file the admin doesn't want to edit directly).
        Optional<com.enviouse.futureshops.catalog.CategoryDef> bundled =
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
        Optional<com.enviouse.futureshops.catalog.CategoryDef> bundled =
                ShopCatalog.findBundledCategory(name);
        String idToRestore = bundled.map(com.enviouse.futureshops.catalog.CategoryDef::id).orElse(name);
        if (data.restoreBaseCategory(idToRestore)) {
            String shown = bundled.map(com.enviouse.futureshops.catalog.CategoryDef::displayName).orElse(name.trim());
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
            for (com.enviouse.futureshops.catalog.ShopDefinition def : ShopCatalog.getAllDefinitions()) {
                for (com.enviouse.futureshops.catalog.CategoryDef cat : def.categories()) {
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


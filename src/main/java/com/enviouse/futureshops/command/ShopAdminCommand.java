package com.enviouse.futureshops.command;

import com.enviouse.futureshops.catalog.ShopCatalog;
import com.enviouse.futureshops.coin.CoinMintRecord;
import com.enviouse.futureshops.coin.SpentMintsSavedData;
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

                // /shopadmin adminshop toggle
                .then(Commands.literal("adminshop")
                        .then(Commands.literal("toggle")
                                .executes(ctx -> toggleAdminShop(ctx.getSource()))))

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
                                        .suggests(SUGGEST_CATEGORIES)
                                        .executes(ctx -> removeCategory(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
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

        source.sendSuccess(() -> Component.literal(
                "Reloaded shop catalog and refreshed " + activeSessions + " active session(s).").withStyle(net.minecraft.ChatFormatting.GREEN), true);
        return 1;
    }

    // -------------------------------------------------------------------------
    // /shopadmin promo set|clear
    // -------------------------------------------------------------------------

    private static int setPromo(CommandSourceStack source, String shopId, String itemId, String type, double value) {
        if (!ShopCatalog.setRuntimePromo(shopId, itemId, type, value)) {
            source.sendFailure(Component.literal("Failed to set promo. Check shopId/itemId/type/value.")
                    .withStyle(net.minecraft.ChatFormatting.RED));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Promo override applied: " + shopId + " :: " + itemId + " [" + type + "=" + value + "]")
                .withStyle(net.minecraft.ChatFormatting.GREEN), true);
        return 1;
    }

    private static int clearPromo(CommandSourceStack source, String shopId, String itemId) {
        if (!ShopCatalog.clearRuntimePromo(shopId, itemId)) {
            source.sendFailure(Component.literal("No runtime promo override found for " + shopId + " :: " + itemId)
                    .withStyle(net.minecraft.ChatFormatting.RED));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Cleared runtime promo override for " + shopId + " :: " + itemId)
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
        String stateText = newState ? "§aENABLED" : "§cDISABLED";
        source.sendSuccess(() -> Component.literal("Admin shop is now " + stateText + "§r. "
                + (newState ? "Players will see the admin catalog in /shop." : "Players will only see nearby player shops in /shop."))
                .withStyle(net.minecraft.ChatFormatting.YELLOW), true);
        // Refresh all active sessions so they immediately see the change
        ShopDataService.resendActiveSessions(server);
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
            source.sendFailure(Component.literal("Invalid amount: " + amountStr)
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
                source.sendSuccess(() -> Component.literal("§a+ " + formatted + " " + provider.getCurrencyName()
                        + " §7→ §f" + profile.getName() + " §7(new balance: §a" + newBal + "§7)")
                        .withStyle(ChatFormatting.GREEN), true);
                successCount++;
            } else {
                source.sendFailure(Component.literal("Failed for " + profile.getName() + ": " + result.errorCode())
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
            source.sendFailure(Component.literal("Invalid amount: " + amountStr)
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
                source.sendSuccess(() -> Component.literal("§c- " + formatted + " " + provider.getCurrencyName()
                        + " §7→ §f" + profile.getName() + " §7(new balance: §a" + newBal + "§7)")
                        .withStyle(ChatFormatting.YELLOW), true);
                successCount++;
            } else {
                source.sendFailure(Component.literal("Failed for " + profile.getName() + ": " + result.errorCode())
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
            source.sendFailure(Component.literal("Invalid amount: " + amountStr)
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
            source.sendSuccess(() -> Component.literal("§eSet §f" + profile.getName() + "§e balance to §a"
                    + formatted + " " + provider.getCurrencyName())
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
            source.sendSuccess(() -> Component.literal("§f" + profile.getName() + "§7 balance: §a"
                    + formatted + " " + provider.getCurrencyName())
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
            source.sendSuccess(() -> Component.literal("§eReset §f" + profile.getName()
                    + "§e balance to starting value: §a" + formatted + " " + provider.getCurrencyName())
                    .withStyle(ChatFormatting.YELLOW), true);
        }
        return targets.size();
    }

    // -------------------------------------------------------------------------
    // /shopadmin view <player>
    // -------------------------------------------------------------------------

    private static int adminView(CommandSourceStack source, Collection<GameProfile> targets) {
        if (!(source.getEntity() instanceof ServerPlayer admin)) {
            source.sendFailure(Component.literal("This command can only be run by a player.")
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
            source.sendSuccess(() -> Component.literal("§7Opening marketplace dashboard for §f" + targetName + "§7...")
                    .withStyle(ChatFormatting.GRAY), false);
        } else {
            // Offline player — send a lightweight balance-only view
            EconomyProvider provider = BalanceManager.getProvider();
            long balance = provider.getBalance(targetUuid);
            String formatted = EconomyCommandUtil.formatMinorUnits(balance, provider.getDecimalPlaces());
            source.sendSuccess(() -> Component.literal("§6═══ Profile: " + targetName + " (offline) ═══")
                    .withStyle(ChatFormatting.GOLD), false);
            source.sendSuccess(() -> Component.literal("  §7Balance: §a" + formatted + " " + provider.getCurrencyName())
                    .withStyle(ChatFormatting.GRAY), false);
            source.sendSuccess(() -> Component.literal("  §7UUID: §f" + targetUuid)
                    .withStyle(ChatFormatting.DARK_GRAY), false);
            source.sendSuccess(() -> Component.literal("  §8(Full dashboard requires the player to be online)")
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
        if (data.addCategory(name)) {
            // Also register in the player-shop department registry so they appear in autocomplete
            com.enviouse.futureshops.server.shop.DepartmentSavedData.get(server).addDepartment(name);
            source.sendSuccess(() -> Component.literal("§aAdded catalog category: §f" + name.trim())
                    .withStyle(net.minecraft.ChatFormatting.GREEN), true);
            ShopDataService.resendActiveSessions(server);
        } else {
            source.sendFailure(Component.literal("Category already exists or invalid: " + name)
                    .withStyle(net.minecraft.ChatFormatting.RED));
        }
        return 1;
    }

    private static int removeCategory(CommandSourceStack source, String name) {
        MinecraftServer server = source.getServer();
        AdminCategorySavedData data = AdminCategorySavedData.get(server);
        if (data.removeCategory(name)) {
            source.sendSuccess(() -> Component.literal("§cRemoved catalog category: §f" + name.trim())
                    .withStyle(net.minecraft.ChatFormatting.YELLOW), true);
            ShopDataService.resendActiveSessions(server);
        } else {
            source.sendFailure(Component.literal("Category not found: " + name)
                    .withStyle(net.minecraft.ChatFormatting.RED));
        }
        return 1;
    }

    private static int listCategories(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        AdminCategorySavedData data = AdminCategorySavedData.get(server);
        List<String> categories = data.getAllSorted();
        if (categories.isEmpty()) {
            source.sendSuccess(() -> Component.literal("§7No admin catalog categories defined. Use §f/shopadmin category add <name>§7 to create one.")
                    .withStyle(net.minecraft.ChatFormatting.GRAY), false);
        } else {
            source.sendSuccess(() -> Component.literal("§6═══ Admin Catalog Categories (" + categories.size() + ") ═══")
                    .withStyle(net.minecraft.ChatFormatting.GOLD), false);
            for (String cat : categories) {
                int itemCount = data.getItemsInCategory(cat).size();
                source.sendSuccess(() -> Component.literal("  • " + cat + " §7(" + itemCount + " items)")
                        .withStyle(net.minecraft.ChatFormatting.WHITE), false);
            }
        }
        return 1;
    }

    private static int assignItem(CommandSourceStack source, String categoryName, String itemId) {
        MinecraftServer server = source.getServer();
        AdminCategorySavedData data = AdminCategorySavedData.get(server);
        if (data.assignItem(itemId, categoryName)) {
            source.sendSuccess(() -> Component.literal("§aAssigned §f" + itemId + " §ato category §f" + categoryName)
                    .withStyle(net.minecraft.ChatFormatting.GREEN), true);
            ShopDataService.resendActiveSessions(server);
        } else {
            source.sendFailure(Component.literal("Failed to assign. Make sure the category '" + categoryName + "' exists (use /shopadmin category add first) and itemId is valid.")
                    .withStyle(net.minecraft.ChatFormatting.RED));
        }
        return 1;
    }

    private static int unassignItem(CommandSourceStack source, String itemId) {
        MinecraftServer server = source.getServer();
        AdminCategorySavedData data = AdminCategorySavedData.get(server);
        if (data.unassignItem(itemId)) {
            source.sendSuccess(() -> Component.literal("§eUnassigned §f" + itemId + " §efrom its category. It will appear under 'All' again.")
                    .withStyle(net.minecraft.ChatFormatting.YELLOW), true);
            ShopDataService.resendActiveSessions(server);
        } else {
            source.sendFailure(Component.literal("Item '" + itemId + "' was not assigned to any category.")
                    .withStyle(net.minecraft.ChatFormatting.RED));
        }
        return 1;
    }

    private static int listCategoryItems(CommandSourceStack source, String categoryName) {
        MinecraftServer server = source.getServer();
        AdminCategorySavedData data = AdminCategorySavedData.get(server);
        List<String> items = data.getItemsInCategory(categoryName);
        if (items.isEmpty()) {
            source.sendSuccess(() -> Component.literal("§7No items assigned to category '" + categoryName + "'. Use §f/shopadmin category assign \"" + categoryName + "\" <itemId>§7.")
                    .withStyle(net.minecraft.ChatFormatting.GRAY), false);
        } else {
            source.sendSuccess(() -> Component.literal("§6═══ Items in '" + categoryName + "' (" + items.size() + ") ═══")
                    .withStyle(net.minecraft.ChatFormatting.GOLD), false);
            for (String item : items) {
                source.sendSuccess(() -> Component.literal("  • " + item)
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
            src.sendFailure(Component.literal("Unknown player: '" + playerName + "'. "
                    + "They must have joined the server at least once, or be currently online.").withStyle(net.minecraft.ChatFormatting.RED));
            return 0;
        }

        // 2. Pull the immutable snapshot of the mint registry
        SpentMintsSavedData mintData = SpentMintsSavedData.get(server);
        Map<String, CoinMintRecord> snapshot = mintData.snapshotRegistry();

        // 3. Filter and sort by minted-at descending
        List<CoinMintRecord> playerMints = snapshot.values().stream()
                .filter(r -> targetUUID.equals(r.playerUUID()))
                .sorted(Comparator.comparingLong(CoinMintRecord::mintedAt).reversed())
                .toList();

        // 4. Aggregate stats
        long activeCount   = playerMints.stream().filter(r -> !r.consumed()).count();
        long consumedCount = playerMints.stream().filter(CoinMintRecord::consumed).count();
        long activeValue   = playerMints.stream()
                .filter(r -> !r.consumed())
                .mapToLong(r -> r.denomination() * r.count())
                .sum();

        // 5. Send output
        src.sendSuccess(() -> Component.literal(
                "═══ Coin Audit: " + playerName + " ═══").withStyle(net.minecraft.ChatFormatting.AQUA), false);
        src.sendSuccess(() -> Component.literal(String.format(
                "Mints total: %d  |  Active: %d  |  Consumed: %d",
                playerMints.size(), activeCount, consumedCount)).withStyle(net.minecraft.ChatFormatting.GRAY), false);
        src.sendSuccess(() -> Component.literal(String.format(
                "Active value: %d minor units  (UUID: %s)", activeValue, targetUUID)).withStyle(net.minecraft.ChatFormatting.GREEN), false);

        if (playerMints.isEmpty()) {
            src.sendSuccess(() -> Component.literal("No coin mints on record for this player.").withStyle(net.minecraft.ChatFormatting.GOLD), false);
            return 1;
        }

        src.sendSuccess(() -> Component.literal("─── Recent mints (newest first, max 15) ───").withStyle(net.minecraft.ChatFormatting.DARK_AQUA), false);

        int shown = Math.min(playerMints.size(), 15);
        for (int i = 0; i < shown; i++) {
            CoinMintRecord r = playerMints.get(i);
            String status  = r.consumed() ? "§cCONSUMED§r" : "§aACTIVE  §r";
            String mintTs  = TS_FMT.format(Instant.ofEpochSecond(r.mintedAt()));
            String eatTs   = r.consumed()
                    ? " → " + TS_FMT.format(Instant.ofEpochSecond(r.consumedAt()))
                    : "";
            // Show first 8 chars of mintId to keep lines tidy
            String shortId = r.mintId().length() > 8 ? r.mintId().substring(0, 8) + "…" : r.mintId();
            src.sendSuccess(() -> Component.literal(String.format(
                    "[%s] id=%s  denom=%d×%d  minted=%s%s",
                    status, shortId, r.denomination(), r.count(), mintTs, eatTs)).withStyle(net.minecraft.ChatFormatting.GRAY), false);
        }

        if (playerMints.size() > shown) {
            int extra = playerMints.size() - shown;
            src.sendSuccess(() -> Component.literal("  ... and " + extra + " older record(s) not shown.").withStyle(net.minecraft.ChatFormatting.DARK_GRAY), false);
        }

        return 1;
    }

    // -------------------------------------------------------------------------
    // /shopadmin limits maxlistings|maxblocks|info
    // -------------------------------------------------------------------------

    private static int setMaxListings(CommandSourceStack source, int amount) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be run by a player looking at a shop block.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        // Raycast to find the shop block the player is looking at
        net.minecraft.world.phys.BlockHitResult hit = (net.minecraft.world.phys.BlockHitResult)
                player.pick(6.0D, 0.0F, false);
        if (!(player.level().getBlockEntity(hit.getBlockPos()) instanceof ShopBlockEntity shop)) {
            source.sendFailure(Component.literal("Look at a shop block first.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        shop.setMaxListings(amount);
        String display = amount < 0 ? "unlimited" : String.valueOf(amount);
        source.sendSuccess(() -> Component.literal("§aMax listings for this shop block set to: §f" + display)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setMaxBlocks(CommandSourceStack source, Collection<GameProfile> targets, int amount) {
        MinecraftServer server = source.getServer();
        ShopLimitsSavedData limits = ShopLimitsSavedData.get(server);
        String display = amount < 0 ? "unlimited" : String.valueOf(amount);
        for (GameProfile profile : targets) {
            limits.setMaxShopBlocks(profile.getId(), amount);
            source.sendSuccess(() -> Component.literal("§aMax shop blocks for §f" + profile.getName()
                    + " §aset to: §f" + display).withStyle(ChatFormatting.GREEN), true);
        }
        return targets.size();
    }

    private static int limitsInfo(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be run by a player looking at a shop block.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        net.minecraft.world.phys.BlockHitResult hit = (net.minecraft.world.phys.BlockHitResult)
                player.pick(6.0D, 0.0F, false);
        if (!(player.level().getBlockEntity(hit.getBlockPos()) instanceof ShopBlockEntity shop)) {
            source.sendFailure(Component.literal("Look at a shop block first.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        int maxL = shop.getMaxListings();
        String listingDisplay = maxL < 0 ? "unlimited" : String.valueOf(maxL);
        int currentListings = shop.getListings().size();
        source.sendSuccess(() -> Component.literal("§6═══ Shop Block Limits ═══")
                .withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal("  §7Max listings: §f" + listingDisplay
                + " §7(current: §f" + currentListings + "§7)")
                .withStyle(ChatFormatting.GRAY), false);
        if (shop.getOwnerUuid() != null) {
            MinecraftServer server = source.getServer();
            ShopLimitsSavedData limits = ShopLimitsSavedData.get(server);
            int maxB = limits.getMaxShopBlocks(shop.getOwnerUuid());
            String blockDisplay = maxB < 0 ? "unlimited" : String.valueOf(maxB);
            int owned = PlayerShopRegistrySavedData.get(server).getOwnedShops(shop.getOwnerUuid()).size();
            source.sendSuccess(() -> Component.literal("  §7Owner max blocks: §f" + blockDisplay
                    + " §7(current: §f" + owned + "§7)")
                    .withStyle(ChatFormatting.GRAY), false);
        }
        return 1;
    }

    private static int limitsInfoPlayer(CommandSourceStack source, Collection<GameProfile> targets) {
        MinecraftServer server = source.getServer();
        ShopLimitsSavedData limits = ShopLimitsSavedData.get(server);
        for (GameProfile profile : targets) {
            int max = limits.getMaxShopBlocks(profile.getId());
            String display = max < 0 ? "unlimited" : String.valueOf(max);
            int owned = PlayerShopRegistrySavedData.get(server).getOwnedShops(profile.getId()).size();
            source.sendSuccess(() -> Component.literal("§f" + profile.getName() + " §7— max blocks: §f"
                    + display + " §7(current: §f" + owned + "§7)")
                    .withStyle(ChatFormatting.GRAY), false);
        }
        return targets.size();
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


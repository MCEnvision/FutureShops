package com.enviouse.futureshopsp.command;

import com.enviouse.futureshopsp.Config;
import com.enviouse.futureshopsp.Futureshops;
import com.enviouse.futureshopsp.catalog.AdminShopConfigWriter;
import com.enviouse.futureshopsp.catalog.AdminShopItemSpec;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player slash-command-driven dialogue invoked by {@code /shopadmin adminshop add}. After the
 * wizard starts, the player answers each prompt by running {@code /shopadmin respond <text>}.
 * Plain chat is never intercepted.
 *
 * <p>Steps: buy price → sell price → stock → (refresh? → interval) → (category? → name).
 * Wizards auto-cancel on logout and after 60s of inactivity (checked on the next response).
 */
@EventBusSubscriber(modid = Futureshops.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class AdminShopWizard {

    private static final ConcurrentHashMap<UUID, State> ACTIVE = new ConcurrentHashMap<>();
    private static final long TIMEOUT_MS = 60_000L;

    private AdminShopWizard() {
    }

    // ─── Lifecycle from ShopAdminCommand ─────────────────────────────────────

    /** Returns true if the player currently has a wizard open. */
    public static boolean hasActive(UUID playerId) {
        State s = ACTIVE.get(playerId);
        return s != null && !s.isExpired();
    }

    /**
     * Starts an "add" wizard for the held item. Returns {@code false} (and messages the player)
     * if the player isn't holding an item or already has an active wizard.
     */
    public static boolean start(ServerPlayer player) {
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            player.sendSystemMessage(Component.translatable("command.futureshops.admin.adminshop.add.no_held")
                    .withStyle(ChatFormatting.RED));
            return false;
        }
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(held.getItem());
        if (key == null) {
            player.sendSystemMessage(Component.translatable("command.futureshops.admin.adminshop.add.unknown_item")
                    .withStyle(ChatFormatting.RED));
            return false;
        }

        UUID id = player.getUUID();
        State existing = ACTIVE.get(id);
        if (existing != null && !existing.isExpired()) {
            player.sendSystemMessage(Component.translatable("command.futureshops.admin.adminshop.add.already_active")
                    .withStyle(ChatFormatting.RED));
            return false;
        }

        // Capture the held item's full NBT as SNBT at wizard-start. This is what
        // lets Mending books, Tacz guns, named/lored items, dyed armor, charged
        // tridents, etc. survive admin-shop round-trip. We snapshot here (not at
        // wizard finalize) so the admin can switch what they're holding without
        // affecting what gets persisted.
        String nbtSnbt = com.enviouse.futureshopsp.server.transaction.NbtMatchUtil.patchToSnbt(player.level().registryAccess(), held.getComponentsPatch());
        State state = new State(key.toString(), held.getHoverName().getString(), nbtSnbt);
        ACTIVE.put(id, state);

        player.sendSystemMessage(Component.translatable(
                "command.futureshops.admin.adminshop.add.start",
                state.displayName, state.itemId).withStyle(ChatFormatting.AQUA));
        player.sendSystemMessage(Component.translatable(
                "command.futureshops.admin.adminshop.add.usage_hint")
                .withStyle(ChatFormatting.DARK_GRAY));
        sendPrompt(player, state);
        return true;
    }

    public static void cancel(UUID playerId) {
        ACTIVE.remove(playerId);
    }

    /**
     * Handles {@code /shopadmin respond <text>} for the calling player.
     * Returns the Brigadier exit code (1 success, 0 no active / failure).
     */
    public static int respond(ServerPlayer player, String raw) {
        State state = ACTIVE.get(player.getUUID());
        if (state == null) {
            player.sendSystemMessage(Component.translatable("command.futureshops.admin.adminshop.respond.no_active")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        if (state.isExpired()) {
            ACTIVE.remove(player.getUUID());
            player.sendSystemMessage(Component.translatable("command.futureshops.admin.adminshop.add.timeout")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        String text = raw == null ? "" : raw.trim();
        if (text.equalsIgnoreCase("cancel")) {
            ACTIVE.remove(player.getUUID());
            player.sendSystemMessage(Component.translatable("command.futureshops.admin.adminshop.add.cancelled")
                    .withStyle(ChatFormatting.YELLOW));
            return 1;
        }

        try {
            handleInput(player, state, text);
            return 1;
        } catch (Exception e) {
            player.sendSystemMessage(Component.translatable(
                    "command.futureshops.admin.adminshop.add.invalid", text).withStyle(ChatFormatting.RED));
            sendPrompt(player, state);
            return 0;
        }
    }

    // ─── Lifecycle event ─────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ACTIVE.remove(event.getEntity().getUUID());
    }

    // ─── State machine ───────────────────────────────────────────────────────

    private static void handleInput(ServerPlayer player, State state, String raw) {
        switch (state.step) {
            case BUY_PRICE -> {
                state.buyPriceMinor = EconomyCommandUtil.parseAmountToMinorUnits(raw, Config.economyCurrencyDecimals);
                advance(player, state, Step.SELL_PRICE);
            }
            case SELL_PRICE -> {
                if (raw.equalsIgnoreCase("0")) {
                    state.sellPriceMinor = 0L;
                } else {
                    state.sellPriceMinor = EconomyCommandUtil.parseAmountToMinorUnits(raw, Config.economyCurrencyDecimals);
                }
                advance(player, state, Step.STOCK);
            }
            case STOCK -> {
                int stock;
                if (raw.equalsIgnoreCase("inf") || raw.equalsIgnoreCase("infinite") || raw.equals("-1")) {
                    stock = -1;
                } else {
                    stock = Integer.parseInt(raw);
                    if (stock < 0) stock = -1;
                }
                state.stock = stock;
                if (stock < 0) {
                    // Unlimited stock can't refresh — skip refresh steps.
                    state.stockRefreshSeconds = 0;
                    advance(player, state, Step.CATEGORY_CHOOSE);
                } else {
                    advance(player, state, Step.STOCK_REFRESH);
                }
            }
            case STOCK_REFRESH -> {
                YesNo yn = parseYesNo(raw);
                if (yn == YesNo.YES) {
                    advance(player, state, Step.STOCK_REFRESH_SECONDS);
                } else if (yn == YesNo.NO) {
                    state.stockRefreshSeconds = 0;
                    advance(player, state, Step.CATEGORY_CHOOSE);
                } else {
                    throw new IllegalArgumentException("expected yes/no");
                }
            }
            case STOCK_REFRESH_SECONDS -> {
                int secs = parseRefreshInterval(raw);
                if (secs <= 0) throw new IllegalArgumentException("interval must be > 0");
                state.stockRefreshSeconds = secs;
                advance(player, state, Step.CATEGORY_CHOOSE);
            }
            case CATEGORY_CHOOSE -> {
                if (raw.equalsIgnoreCase("list")) {
                    sendCategoryList(player);
                    sendPrompt(player, state);
                    state.touch();
                    return;
                }
                YesNo yn = parseYesNo(raw);
                if (yn == YesNo.NO) {
                    state.categoryId = "all";
                    finalizeWizard(player, state);
                } else if (yn == YesNo.YES) {
                    advance(player, state, Step.CATEGORY_NAME);
                } else {
                    throw new IllegalArgumentException("expected yes/no/list");
                }
            }
            case CATEGORY_NAME -> {
                if (raw.equalsIgnoreCase("list")) {
                    sendCategoryList(player);
                    sendPrompt(player, state);
                    state.touch();
                    return;
                }
                String catName = raw;
                if (raw.toLowerCase(java.util.Locale.ROOT).startsWith("new ")) {
                    catName = raw.substring(4).trim();
                    if (catName.isBlank()) throw new IllegalArgumentException("empty category");
                    AdminShopConfigWriter.addCategory(player.server, catName);
                    com.enviouse.futureshopsp.server.shop.AdminCategorySavedData.get(player.server).addCategory(catName);
                    com.enviouse.futureshopsp.server.shop.DepartmentSavedData.get(player.server).addDepartment(catName);
                }
                state.categoryId = normalizeId(catName);
                finalizeWizard(player, state);
            }
        }
    }

    private static void advance(ServerPlayer player, State state, Step next) {
        state.step = next;
        state.touch();
        sendPrompt(player, state);
    }

    private static void finalizeWizard(ServerPlayer player, State state) {
        ACTIVE.remove(player.getUUID());
        AdminShopItemSpec spec = new AdminShopItemSpec(
                state.itemId, state.displayName,
                state.buyPriceMinor, state.sellPriceMinor,
                state.stock, state.stockRefreshSeconds, state.categoryId,
                state.nbtJson);
        boolean ok = AdminShopConfigWriter.addOrUpdateItem(player.server, spec);
        if (!ok) {
            player.sendSystemMessage(Component.translatable("command.futureshops.admin.adminshop.add.write_failed")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        com.enviouse.futureshopsp.server.shop.ShopDataService.resendActiveSessions(player.server);
        String buy = EconomyCommandUtil.formatMinorUnits(state.buyPriceMinor, Config.economyCurrencyDecimals);
        String sell = EconomyCommandUtil.formatMinorUnits(state.sellPriceMinor, Config.economyCurrencyDecimals);
        String stockStr = state.stock < 0
                ? Component.translatable("command.futureshops.admin.limits.unlimited").getString()
                : Integer.toString(state.stock);
        String refreshStr = state.stockRefreshSeconds > 0
                ? state.stockRefreshSeconds + "s"
                : Component.translatable("command.futureshops.admin.adminshop.add.refresh.off").getString();
        player.sendSystemMessage(Component.translatable(
                "command.futureshops.admin.adminshop.add.done",
                state.itemId, buy, sell, stockStr, refreshStr, state.categoryId)
                .withStyle(ChatFormatting.GREEN));
    }

    private static void sendPrompt(ServerPlayer player, State state) {
        String key = switch (state.step) {
            case BUY_PRICE -> "command.futureshops.admin.adminshop.add.prompt.buy_price";
            case SELL_PRICE -> "command.futureshops.admin.adminshop.add.prompt.sell_price";
            case STOCK -> "command.futureshops.admin.adminshop.add.prompt.stock";
            case STOCK_REFRESH -> "command.futureshops.admin.adminshop.add.prompt.stock_refresh";
            case STOCK_REFRESH_SECONDS -> "command.futureshops.admin.adminshop.add.prompt.refresh_interval";
            case CATEGORY_CHOOSE -> "command.futureshops.admin.adminshop.add.prompt.category_choose";
            case CATEGORY_NAME -> "command.futureshops.admin.adminshop.add.prompt.category_name";
        };
        player.sendSystemMessage(Component.translatable(key).withStyle(ChatFormatting.GOLD));
    }

    private static void sendCategoryList(ServerPlayer player) {
        List<String> cats = AdminShopConfigWriter.listAllCategoryDisplayNames(player.server);
        if (cats.isEmpty()) {
            player.sendSystemMessage(Component.translatable("command.futureshops.admin.adminshop.add.categories_empty")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        player.sendSystemMessage(Component.translatable(
                "command.futureshops.admin.adminshop.add.categories_header", cats.size())
                .withStyle(ChatFormatting.GOLD));
        for (String c : cats) {
            player.sendSystemMessage(Component.translatable(
                    "command.futureshops.admin.adminshop.add.categories_row", c)
                    .withStyle(ChatFormatting.WHITE));
        }
    }

    // ─── Parsing helpers ─────────────────────────────────────────────────────

    private enum YesNo { YES, NO, OTHER }

    private static YesNo parseYesNo(String raw) {
        String t = raw.trim().toLowerCase(java.util.Locale.ROOT);
        if (t.equals("y") || t.equals("yes") || t.equals("true")) return YesNo.YES;
        if (t.equals("n") || t.equals("no") || t.equals("false")) return YesNo.NO;
        return YesNo.OTHER;
    }

    /**
     * Accepts a plain seconds value, or one of the suffixed shorthands:
     * {@code 30s}, {@code 5m}, {@code 1h}, {@code 1d}.
     */
    private static int parseRefreshInterval(String raw) {
        String t = raw.trim().toLowerCase(java.util.Locale.ROOT);
        if (t.isEmpty()) return 0;
        char last = t.charAt(t.length() - 1);
        long mult;
        String numPart;
        if (last == 's') { mult = 1L; numPart = t.substring(0, t.length() - 1); }
        else if (last == 'm') { mult = 60L; numPart = t.substring(0, t.length() - 1); }
        else if (last == 'h') { mult = 3600L; numPart = t.substring(0, t.length() - 1); }
        else if (last == 'd') { mult = 86400L; numPart = t.substring(0, t.length() - 1); }
        else { mult = 1L; numPart = t; }
        long val = Long.parseLong(numPart.trim()) * mult;
        if (val > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return (int) val;
    }

    private static String normalizeId(String s) {
        return s.trim().toLowerCase(java.util.Locale.ROOT).replace(' ', '_');
    }

    // ─── Wizard state ────────────────────────────────────────────────────────

    private enum Step {
        BUY_PRICE,
        SELL_PRICE,
        STOCK,
        STOCK_REFRESH,
        STOCK_REFRESH_SECONDS,
        CATEGORY_CHOOSE,
        CATEGORY_NAME
    }

    private static final class State {
        final String itemId;
        final String displayName;
        /**
         * SNBT snapshot of the held item's tag at wizard-start, or empty string when bare.
         * Carried through to {@link AdminShopItemSpec} on finalize so it lands in admin.json
         * and survives buys via the catalog path.
         */
        final String nbtJson;
        Step step = Step.BUY_PRICE;
        long buyPriceMinor;
        long sellPriceMinor;
        int stock = -1;
        int stockRefreshSeconds = 0;
        String categoryId = "all";
        long lastTouchMs = System.currentTimeMillis();

        State(String itemId, String displayName, String nbtJson) {
            this.itemId = itemId;
            this.displayName = displayName;
            this.nbtJson = nbtJson == null ? "" : nbtJson;
        }

        void touch() {
            lastTouchMs = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - lastTouchMs > TIMEOUT_MS;
        }
    }
}

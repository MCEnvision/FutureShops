package com.enviouse.futureshops.command;

import com.enviouse.futureshops.catalog.ShopCatalog;
import com.enviouse.futureshops.coin.CoinMintRecord;
import com.enviouse.futureshops.coin.SpentMintsSavedData;
import com.enviouse.futureshops.server.session.ShopSessionManager;
import com.enviouse.futureshops.server.shop.ShopDataService;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
                                        ctx, StringArgumentType.getString(ctx, "player"))))));
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
    // /shopadmin coinaudit <player>
    // -------------------------------------------------------------------------

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


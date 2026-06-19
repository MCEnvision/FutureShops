package com.enviouse.futureshopsp.command;

import com.enviouse.futureshopsp.server.economy.BalanceEntry;
import com.enviouse.futureshopsp.server.economy.BalanceManager;
import com.enviouse.futureshopsp.server.economy.EconomyProvider;
import com.enviouse.futureshopsp.server.shop.MarketplaceAnalyticsService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;

public final class BalTopCommand {
    private static final int PAGE_SIZE = 10;

    private BalTopCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("baltop")
            .executes(context -> runUi(context.getSource(), 1))
            .then(Commands.literal("ui")
                .executes(context -> runUi(context.getSource(), 1))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                    .executes(context -> runUi(context.getSource(), IntegerArgumentType.getInteger(context, "page")))))
            .then(Commands.argument("page", IntegerArgumentType.integer(1))
                .executes(context -> run(context.getSource(), IntegerArgumentType.getInteger(context, "page")))));
    }

    private static int runUi(CommandSourceStack source, int page) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(EconomyCommandUtil.error(Component.translatable("command.futureshops.player_only")));
            return 0;
        }
        MarketplaceAnalyticsService.sendLeaderboard(player, page);
        return 1;
    }

    private static int run(CommandSourceStack source, int page) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(EconomyCommandUtil.error(Component.translatable("command.futureshops.player_only")));
            return 0;
        }

        EconomyProvider provider = BalanceManager.getProvider();
        List<BalanceEntry> entries = BalanceManager.getTopBalances(page, PAGE_SIZE);
        if (entries.isEmpty()) {
            player.sendSystemMessage(EconomyCommandUtil.warning(Component.translatable("command.futureshops.baltop.empty", page)));
            return 1;
        }

        player.sendSystemMessage(EconomyCommandUtil.info(Component.translatable("command.futureshops.baltop.header", page)));
        int rank = ((page - 1) * PAGE_SIZE) + 1;
        for (BalanceEntry entry : entries) {
            String name = resolvePlayerName(player, entry.playerUUID());
            String balanceText = EconomyCommandUtil.formatMinorUnits(entry.balanceMinorUnits(), provider.getDecimalPlaces());
            player.sendSystemMessage(EconomyCommandUtil.success(Component.translatable("command.futureshops.baltop.entry", rank, name, balanceText, provider.getCurrencyName())));
            rank++;
        }
        return 1;
    }

    private static String resolvePlayerName(ServerPlayer viewer, UUID playerUUID) {
        ServerPlayer online = viewer.server.getPlayerList().getPlayer(playerUUID);
        if (online != null) {
            return online.getGameProfile().getName();
        }

        return playerUUID.toString().substring(0, 8);
    }
}


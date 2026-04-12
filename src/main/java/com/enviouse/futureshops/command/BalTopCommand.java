package com.enviouse.futureshops.command;

import com.enviouse.futureshops.server.economy.BalanceEntry;
import com.enviouse.futureshops.server.economy.BalanceManager;
import com.enviouse.futureshops.server.economy.EconomyProvider;
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
            .executes(context -> run(context.getSource(), 1))
            .then(Commands.argument("page", IntegerArgumentType.integer(1))
                .executes(context -> run(context.getSource(), IntegerArgumentType.getInteger(context, "page")))));
    }

    private static int run(CommandSourceStack source, int page) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.translatable("command.futureshops.player_only"));
            return 0;
        }

        EconomyProvider provider = BalanceManager.getProvider();
        List<BalanceEntry> entries = BalanceManager.getTopBalances(page, PAGE_SIZE);
        if (entries.isEmpty()) {
            player.sendSystemMessage(Component.translatable("command.futureshops.baltop.empty", page));
            return 1;
        }

        player.sendSystemMessage(Component.translatable("command.futureshops.baltop.header", page));
        int rank = ((page - 1) * PAGE_SIZE) + 1;
        for (BalanceEntry entry : entries) {
            String name = resolvePlayerName(player, entry.playerUUID());
            String balanceText = EconomyCommandUtil.formatMinorUnits(entry.balanceMinorUnits(), provider.getDecimalPlaces());
            player.sendSystemMessage(Component.translatable("command.futureshops.baltop.entry", rank, name, balanceText, provider.getCurrencyName()));
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


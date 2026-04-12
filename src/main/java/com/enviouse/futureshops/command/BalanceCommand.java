package com.enviouse.futureshops.command;

import com.enviouse.futureshops.server.economy.BalanceManager;
import com.enviouse.futureshops.server.economy.EconomyProvider;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

public final class BalanceCommand {
    private BalanceCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(balanceLiteral("balance"));
        dispatcher.register(balanceLiteral("bal"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> balanceLiteral(String literal) {
        return Commands.literal(literal).executes(context -> {
            if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
                context.getSource().sendFailure(Component.translatable("command.futureshops.player_only"));
                return 0;
            }

            EconomyProvider provider = BalanceManager.getProvider();
            long balanceMinorUnits = provider.getBalance(player.getUUID());
            String formatted = formatMinorUnits(balanceMinorUnits, provider.getDecimalPlaces());
            player.sendSystemMessage(Component.translatable("command.futureshops.balance.value", formatted, provider.getCurrencyName()));
            return 1;
        });
    }

    private static String formatMinorUnits(long value, int decimals) {
        if (decimals <= 0) {
            return Long.toString(value);
        }

        long absValue = Math.abs(value);
        long scale = (long) Math.pow(10.0D, decimals);
        long whole = absValue / scale;
        long fractional = absValue % scale;
        String sign = value < 0L ? "-" : "";
        return String.format(Locale.ROOT, "%s%d.%0" + decimals + "d", sign, whole, fractional);
    }
}

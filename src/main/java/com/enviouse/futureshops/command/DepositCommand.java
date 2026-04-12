package com.enviouse.futureshops.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class DepositCommand {
    private DepositCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("deposit").executes(context -> {
            if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
                context.getSource().sendFailure(Component.translatable("command.futureshops.player_only"));
                return 0;
            }

            player.sendSystemMessage(Component.translatable("command.futureshops.deposit.stub"));
            return 1;
        }));
    }
}


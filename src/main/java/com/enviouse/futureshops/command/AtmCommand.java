package com.enviouse.futureshops.command;

import com.enviouse.futureshops.server.economy.AtmService;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Convenience command for opening the physical-currency ATM anywhere. */
public final class AtmCommand {
    private AtmCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("atm").executes(context -> {
            if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
                context.getSource().sendFailure(EconomyCommandUtil.error(
                        Component.translatable("command.futureshops.player_only")));
                return 0;
            }
            AtmService.sendData(player);
            return 1;
        }));
    }
}

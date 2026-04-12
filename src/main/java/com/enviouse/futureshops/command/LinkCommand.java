package com.enviouse.futureshops.command;

import com.enviouse.futureshops.server.shop.PlayerShopBlockService;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class LinkCommand {
    private LinkCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("link")
                .executes(context -> {
                    if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
                        context.getSource().sendFailure(EconomyCommandUtil.error(Component.translatable("command.futureshops.player_only")));
                        return 0;
                    }
                    int result = PlayerShopBlockService.confirmLink(player);
                    if (result > 0) {
                        player.sendSystemMessage(EconomyCommandUtil.success(Component.translatable("command.futureshops.link.confirmed")));
                    } else {
                        player.sendSystemMessage(EconomyCommandUtil.error(Component.translatable("command.futureshops.link.failed")));
                    }
                    return result;
                }));
    }
}



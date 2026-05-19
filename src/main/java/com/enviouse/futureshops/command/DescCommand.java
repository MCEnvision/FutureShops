package com.enviouse.futureshops.command;

import com.enviouse.futureshops.server.shop.PlayerShopBlockService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class DescCommand {
    private DescCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("desc")
                .then(Commands.argument("text", StringArgumentType.greedyString())
                        .executes(context -> {
                            if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
                                context.getSource().sendFailure(
                                        EconomyCommandUtil.error(Component.translatable("command.futureshops.player_only")));
                                return 0;
                            }
                            String text = StringArgumentType.getString(context, "text");
                            int result = PlayerShopBlockService.applyDescription(player, text);
                            if (result == 2) {
                                player.sendSystemMessage(EconomyCommandUtil.success(
                                        Component.translatable("command.futureshops.desc.listing_updated")));
                            } else if (result == 1) {
                                player.sendSystemMessage(EconomyCommandUtil.success(
                                        Component.translatable("command.futureshops.desc.shop_updated")));
                            } else {
                                player.sendSystemMessage(EconomyCommandUtil.error(
                                        Component.translatable("command.futureshops.desc.none_pending")));
                            }
                            return result;
                        })));
    }
}


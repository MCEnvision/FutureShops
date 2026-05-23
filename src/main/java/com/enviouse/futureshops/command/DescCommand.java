package com.enviouse.futureshops.command;

import com.enviouse.futureshops.server.shop.PlayerShopBlockService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class DescCommand {
    private DescCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // Canonical command. Renamed from /desc -> /shopdesc because many other
        // mods register their own /desc (e.g. with an [integer levels] arg)
        // and Brigadier's merged dispatcher would intercept ours, producing
        // "Invalid value for [levels] (For input string: \"<Nothing\")" when
        // a player typed text. /shopdesc is unique to FutureShops.
        dispatcher.register(Commands.literal("shopdesc")
                .then(Commands.argument("text", StringArgumentType.greedyString())
                        .executes(DescCommand::run)));
    }

    private static int run(CommandContext<CommandSourceStack> context) {
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
    }
}

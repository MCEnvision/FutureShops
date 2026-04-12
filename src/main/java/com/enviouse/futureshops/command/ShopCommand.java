package com.enviouse.futureshops.command;

import com.enviouse.futureshops.server.shop.ShopDataService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class ShopCommand {
    private ShopCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("shop")
            .executes(context -> {
                if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
                    context.getSource().sendFailure(EconomyCommandUtil.error(Component.translatable("command.futureshops.player_only")));
                    return 0;
                }

                openShop(player, "default");
                return 1;
            })
            .then(Commands.argument("shopId", StringArgumentType.word())
                .executes(context -> {
                    if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
                        context.getSource().sendFailure(EconomyCommandUtil.error(Component.translatable("command.futureshops.player_only")));
                        return 0;
                    }

                    openShop(player, StringArgumentType.getString(context, "shopId"));
                    return 1;
                })));
    }

    private static void openShop(ServerPlayer player, String requestedShopId) {
        String resolvedShopId = ShopDataService.resolveShopId(requestedShopId);
        ShopDataService.openShop(player, resolvedShopId);
        player.sendSystemMessage(EconomyCommandUtil.success(Component.translatable("command.futureshops.shop.opened", resolvedShopId)));
    }
}

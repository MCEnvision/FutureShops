package com.enviouse.futureshops.command;

import com.enviouse.futureshops.server.shop.ShopDataService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

public final class ShopCommand {
    private static final Logger LOGGER = LogUtils.getLogger();

    private ShopCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("shop")
            .executes(context -> {
                if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
                    context.getSource().sendFailure(EconomyCommandUtil.error(Component.translatable("command.futureshops.player_only")));
                    return 0;
                }

                return openShop(player, "default");
            })
            .then(Commands.argument("shopId", StringArgumentType.word())
                .executes(context -> {
                    if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
                        context.getSource().sendFailure(EconomyCommandUtil.error(Component.translatable("command.futureshops.player_only")));
                        return 0;
                    }

                    return openShop(player,
                            StringArgumentType.getString(context, "shopId"));
                })));
    }

    private static int openShop(
            ServerPlayer player,
            String requestedShopId
    ) {
        String resolvedShopId = requestedShopId == null
                || requestedShopId.isBlank()
                ? "default" : requestedShopId;
        try {
            resolvedShopId = ShopDataService.resolveShopId(
                    requestedShopId);
            ShopDataService.openShop(player, resolvedShopId);
            player.sendSystemMessage(EconomyCommandUtil.success(
                    Component.translatable(
                            "command.futureshops.shop.opened",
                            resolvedShopId)));
            return 1;
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Unable to open FutureShops shop '{}' for player {} with id {}.",
                    resolvedShopId, player.getGameProfile().getName(),
                    player.getUUID(), exception);
            player.sendSystemMessage(EconomyCommandUtil.error(
                    Component.translatable(
                            "command.futureshops.shop.open_failed")));
            return 0;
        }
    }
}

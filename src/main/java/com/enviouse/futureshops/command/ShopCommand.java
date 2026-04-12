package com.enviouse.futureshops.command;

import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.S2CShopDataPacket;
import com.enviouse.futureshops.server.economy.BalanceManager;
import com.enviouse.futureshops.server.economy.EconomyProvider;
import com.enviouse.futureshops.server.session.ShopSessionManager;
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
                    context.getSource().sendFailure(Component.translatable("command.futureshops.player_only"));
                    return 0;
                }

                openShop(player, "default");
                return 1;
            })
            .then(Commands.argument("shopId", StringArgumentType.word())
                .executes(context -> {
                    if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
                        context.getSource().sendFailure(Component.translatable("command.futureshops.player_only"));
                        return 0;
                    }

                    openShop(player, StringArgumentType.getString(context, "shopId"));
                    return 1;
                })));
    }

    private static void openShop(ServerPlayer player, String requestedShopId) {
        String shopId = requestedShopId == null || requestedShopId.isBlank() ? "default" : requestedShopId;
        ShopSessionManager.open(player.getUUID(), shopId);

        EconomyProvider provider = BalanceManager.getProvider();
        long balance = provider.getBalance(player.getUUID());
        ShopPackets.sendToPlayer(player, new S2CShopDataPacket(shopId, balance, provider.getCurrencyName(), provider.getDecimalPlaces()));

        player.sendSystemMessage(Component.translatable("command.futureshops.shop.opened", shopId));
    }
}

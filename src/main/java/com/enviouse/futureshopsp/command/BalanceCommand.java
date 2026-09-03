package com.enviouse.futureshopsp.command;

import com.enviouse.futureshopsp.server.economy.BalanceManager;
import com.enviouse.futureshopsp.server.economy.EconomyProvider;
import com.enviouse.futureshopsp.api.economy.BalanceSnapshot;
import com.enviouse.futureshopsp.api.economy.ProviderResult;
import com.enviouse.futureshopsp.network.ShopPackets;
import com.enviouse.futureshopsp.server.shop.MarketplaceAnalyticsService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class BalanceCommand {
    private BalanceCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(balanceLiteral("balance"));
        dispatcher.register(balanceLiteral("bal"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> balanceLiteral(String literal) {
        return Commands.literal(literal)
            .executes(context -> {
                if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
                    context.getSource().sendFailure(EconomyCommandUtil.error(Component.translatable("command.futureshops.player_only")));
                    return 0;
                }

                MarketplaceAnalyticsService.sendDashboard(player);
                return 1;
            })
            .then(Commands.literal("ui")
                .executes(context -> {
                    if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
                        context.getSource().sendFailure(EconomyCommandUtil.error(Component.translatable("command.futureshops.player_only")));
                        return 0;
                    }
                    MarketplaceAnalyticsService.sendDashboard(player);
                    return 1;
                }))
            .then(Commands.argument("target", EntityArgument.player())
                .executes(context -> {
                    if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
                        context.getSource().sendFailure(EconomyCommandUtil.error(Component.translatable("command.futureshops.player_only")));
                        return 0;
                    }

                    ServerPlayer target = EntityArgument.getPlayer(context, "target");
                    sendBalance(player, target);
                    return 1;
                }));
    }

    private static void sendBalance(ServerPlayer viewer, ServerPlayer target) {
        EconomyProvider provider = BalanceManager.getProvider();
        ProviderResult<BalanceSnapshot> balanceResult = BalanceManager.queryBalance(target.getUUID());
        if (!balanceResult.confirmed()) {
            EconomyCommandUtil.sendProviderError(viewer, balanceResult);
            return;
        }
        long balanceMinorUnits = balanceResult.value().orElseThrow().balanceMinorUnits();
        String formatted = EconomyCommandUtil.formatMinorUnits(balanceMinorUnits, provider.getDecimalPlaces());

        if (viewer.getUUID().equals(target.getUUID())) {
            viewer.sendSystemMessage(EconomyCommandUtil.info(Component.translatable("command.futureshops.balance.value", formatted, provider.getCurrencyName())));
            return;
        }

        viewer.sendSystemMessage(EconomyCommandUtil.info(Component.translatable("command.futureshops.balance.other", target.getName(), formatted, provider.getCurrencyName())));
    }
}

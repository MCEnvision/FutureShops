package com.enviouse.futureshops.command;

import com.enviouse.futureshops.data.BulkSellTarget;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.S2CBulkSellQuotePacket;
import com.enviouse.futureshops.network.packets.S2CBulkSellResultPacket;
import com.enviouse.futureshops.server.economy.BalanceManager;
import com.enviouse.futureshops.server.shop.BulkSellService;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class SellAllCommand {
    private SellAllCommand() {
    }

    public static void register(
            CommandDispatcher<CommandSourceStack> dispatcher
    ) {
        dispatcher.register(Commands.literal("sellall")
                .then(target("adminshop",
                        BulkSellTarget.ADMIN_SHOP))
                .then(target("playershops",
                        BulkSellTarget.PLAYER_SHOPS)));
    }

    private static com.mojang.brigadier.builder
            .LiteralArgumentBuilder<CommandSourceStack> target(
            String name,
            BulkSellTarget target
    ) {
        return Commands.literal(name)
                .executes(context -> open(
                        context.getSource(), target, false))
                .then(Commands.literal("confirm")
                        .executes(context -> open(
                                context.getSource(), target, true)));
    }

    private static int open(
            CommandSourceStack source,
            BulkSellTarget target,
            boolean confirm
    ) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(EconomyCommandUtil.error(
                    Component.translatable(
                            "command.futureshops.player_only")));
            return 0;
        }
        String shopId = target == BulkSellTarget.ADMIN_SHOP
                ? "default" : "playershops";
        BulkSellService.QuoteResult quote =
                BulkSellService.quote(
                        player, target, shopId, true);
        if (!quote.success()) {
            source.sendFailure(EconomyCommandUtil.error(
                    failureMessage(quote.status())));
            return 0;
        }
        if (!confirm) {
            ShopPackets.sendToPlayer(player,
                    S2CBulkSellQuotePacket.from(quote));
            return 1;
        }
        BulkSellService.CommitResult result =
                BulkSellService.commitAll(player, quote);
        ShopPackets.sendToPlayer(player,
                S2CBulkSellResultPacket.from(
                        result,
                        BalanceManager.getProvider()
                                .getCurrencyName(),
                        BalanceManager.getProvider()
                                .getDecimalPlaces()));
        return result.status() == BulkSellService.Status.SUCCESS
                || result.status() == BulkSellService.Status.PARTIAL
                ? 1 : 0;
    }

    private static Component failureMessage(
            BulkSellService.Status status
    ) {
        return switch (status) {
            case REJECTED -> Component.translatable(
                    "command.futureshops.sellall.rejected");
            case RECOVERY_REQUIRED -> Component.translatable(
                    "command.futureshops.sellall.recovery_required");
            case NOTHING_ELIGIBLE -> Component.translatable(
                    "command.futureshops.sellall.nothing_eligible");
            case QUOTE_EXPIRED -> Component.translatable(
                    "command.futureshops.sellall.quote_expired");
            case INVALID_SELECTION -> Component.translatable(
                    "command.futureshops.sellall.invalid_selection");
            case INVALID_REQUEST -> Component.translatable(
                    "command.futureshops.sellall.invalid_request");
            case NOT_AVAILABLE -> Component.translatable(
                    "command.futureshops.sellall.not_available");
            case RATE_LIMITED -> Component.translatable(
                    "command.futureshops.sellall.rate_limited");
            case SUCCESS, PARTIAL, UNAVAILABLE ->
                    Component.translatable(
                            "command.futureshops.sellall.unavailable");
        };
    }
}

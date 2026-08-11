package com.enviouse.futureshops.command;

import com.enviouse.futureshops.server.market.MarketModuleService;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public final class MarketCommand {
    private MarketCommand() {
    }

    public static void register(
            CommandDispatcher<CommandSourceStack> dispatcher
    ) {
        registerAuction(dispatcher, "ah");
        registerAuction(dispatcher, "auctionhouse");
        registerBazaar(dispatcher, "bz");
        registerBazaar(dispatcher, "bazaar");
    }

    private static void registerAuction(
            CommandDispatcher<CommandSourceStack> dispatcher,
            String literal
    ) {
        dispatcher.register(Commands.literal(literal)
                .executes(context -> open(context.getSource(),
                        "auction_house", "browse"))
                .then(Commands.literal("browse")
                        .executes(context -> open(context.getSource(),
                                "auction_house", "browse")))
                .then(Commands.literal("create")
                        .executes(context -> open(context.getSource(),
                                "auction_house", "create")))
                .then(Commands.literal("mine")
                        .executes(context -> open(context.getSource(),
                                "auction_house", "mine")))
                .then(Commands.literal("bids")
                        .executes(context -> open(context.getSource(),
                                "auction_house", "bids")))
                .then(Commands.literal("watched")
                        .executes(context -> open(context.getSource(),
                                "auction_house", "watched")))
                .then(Commands.literal("claims")
                        .executes(context -> open(context.getSource(),
                                "auction_house", "claims")))
                .then(Commands.literal("history")
                        .executes(context -> open(context.getSource(),
                                "auction_house", "history"))));
    }

    private static void registerBazaar(
            CommandDispatcher<CommandSourceStack> dispatcher,
            String literal
    ) {
        dispatcher.register(Commands.literal(literal)
                .executes(context -> open(context.getSource(),
                        "bazaar", "products"))
                .then(Commands.literal("products")
                        .executes(context -> open(context.getSource(),
                                "bazaar", "products")))
                .then(Commands.literal("buy")
                        .executes(context -> open(context.getSource(),
                                "bazaar", "buy_orders")))
                .then(Commands.literal("sell")
                        .executes(context -> open(context.getSource(),
                                "bazaar", "sell_orders")))
                .then(Commands.literal("orders")
                        .executes(context -> open(context.getSource(),
                                "bazaar", "orders")))
                .then(Commands.literal("portfolio")
                        .executes(context -> open(context.getSource(),
                                "bazaar", "portfolio")))
                .then(Commands.literal("watched")
                        .executes(context -> open(context.getSource(),
                                "bazaar", "watched")))
                .then(Commands.literal("claims")
                        .executes(context -> open(context.getSource(),
                                "bazaar", "claims")))
                .then(Commands.literal("history")
                        .executes(context -> open(context.getSource(),
                                "bazaar", "history"))));
    }

    private static int open(CommandSourceStack source, String moduleId,
                            String view) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(EconomyCommandUtil.error(
                    Component.translatable(
                            "command.futureshops.player_only")));
            return 0;
        }
        MarketModuleService.open(player, moduleId, view,
                UUID.randomUUID());
        return 1;
    }
}

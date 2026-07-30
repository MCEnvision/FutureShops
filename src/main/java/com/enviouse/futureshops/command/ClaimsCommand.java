package com.enviouse.futureshops.command;

import com.enviouse.futureshops.client.market.MarketModule;
import com.enviouse.futureshops.server.market.MarketModuleService;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * The plan §4.9 claim access commands: {@code /claims [module]}, plus the {@code /escrow} and
 * {@code /claimall} aliases. Each opens the market shell's Claims view — the ONE authoritative
 * claim surface — rather than duplicating a second session-less collection path: claim
 * collection is deliberately bound to an open market session (route-nonce validated, plan §12),
 * so the commands take the player to Claim / Claim All instead of bypassing that discipline.
 * Registered unconditionally: a disabled module still opens Claims Only (claims are never
 * blocked, plan §1/§11).
 */
public final class ClaimsCommand {

    private ClaimsCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        for (String name : new String[]{"claims", "claimall", "escrow"}) {
            dispatcher.register(Commands.literal(name)
                    .executes(context -> open(context.getSource(), MarketModule.SHOP))
                    .then(Commands.literal("auction")
                            .executes(context -> open(context.getSource(),
                                    MarketModule.AUCTION_HOUSE)))
                    .then(Commands.literal("bazaar")
                            .executes(context -> open(context.getSource(),
                                    MarketModule.BAZAAR))));
        }
    }

    private static int open(CommandSourceStack source, MarketModule module) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable(
                    "command.futureshops.claims.player_only"));
            return 0;
        }
        // SHOP has no shell claims view — its claims surface is the profile/balance screen; route
        // shop-claims requests to the auction claims tab (shared claim center, plan §4.9) so the
        // command always lands ON a claims surface.
        MarketModule target = module == MarketModule.SHOP
                ? MarketModule.AUCTION_HOUSE : module;
        MarketModuleService.open(player, target.id(), "claims", UUID.randomUUID());
        return 1;
    }
}

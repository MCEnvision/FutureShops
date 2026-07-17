package com.enviouse.futureshops.command;

import com.enviouse.futureshops.event.MoneyDepositEvent;
import com.enviouse.futureshops.money.CurrencyManager;
import com.enviouse.futureshops.money.PhysicalCurrencyAdapter;
import com.enviouse.futureshops.server.economy.BalanceManager;
import com.enviouse.futureshops.server.economy.EconomyProvider;
import com.enviouse.futureshops.server.economy.TransactionResult;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class DepositCommand {
    private DepositCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("deposit")
            .executes(context -> {
                if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
                    context.getSource().sendFailure(EconomyCommandUtil.error(Component.translatable("command.futureshops.player_only")));
                    return 0;
                }
                return deposit(player, null);
            })
            .then(Commands.argument("amount", StringArgumentType.word())
                .executes(context -> {
                    if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
                        context.getSource().sendFailure(EconomyCommandUtil.error(Component.translatable("command.futureshops.player_only")));
                        return 0;
                    }
                    return deposit(player, StringArgumentType.getString(context, "amount"));
                })));
    }

    private static int deposit(ServerPlayer player, String requestedAmount) {
        EconomyProvider provider = BalanceManager.getProvider();
        PhysicalCurrencyAdapter currency = CurrencyManager.get();

        // Foreign items have no checksum/ledger, so creative mode would be an
        // unlimited money printer — refuse the deposit outright. (The built-in
        // currency needs no gate: creative-spawned bills fail the mint ledger.)
        if (!currency.isInternal() && player.getAbilities().instabuild) {
            player.sendSystemMessage(EconomyCommandUtil.warning(Component.translatable("command.futureshops.deposit.creative_blocked")));
            return 0;
        }

        // Phase 1: Destroy currency that fails validation (built-in checksums;
        // foreign currency has no checksum concept and nothing is destroyed).
        // NB: "already consumed" and "over-authorized" cases are handled atomically
        // in Phase 3 via the mint ledger; we don't pre-destroy them because a
        // stack's MintId might still have remaining balance.
        int invalidDestroyed = currency.destroyCounterfeit(player);
        if (invalidDestroyed > 0) {
            player.sendSystemMessage(EconomyCommandUtil.warning(Component.translatable("command.futureshops.deposit.invalid_destroyed", invalidDestroyed)));
        }

        // Phase 2: Total up what's depositable and resolve the requested amount.
        long totalAvailableMinor = currency.availableValueMinor(player);
        if (totalAvailableMinor <= 0L) {
            String accepted = currency.acceptedItemsSummary(provider.getDecimalPlaces());
            if (accepted != null && !accepted.isBlank()) {
                // Foreign provider: say exactly what IS accepted, so a config
                // that dropped e.g. coins from the accepted list is obvious.
                player.sendSystemMessage(EconomyCommandUtil.warning(Component.translatable(
                        "command.futureshops.deposit.no_currency_accepted", accepted)));
            } else {
                player.sendSystemMessage(EconomyCommandUtil.warning(Component.translatable("command.futureshops.deposit.no_coins")));
            }
            return 0;
        }

        long amountMinorUnits = totalAvailableMinor;
        if (requestedAmount != null) {
            try {
                long requestedMinorUnits = EconomyCommandUtil.parseAmountToMinorUnits(requestedAmount, provider.getDecimalPlaces());
                if (requestedMinorUnits > totalAvailableMinor) {
                    player.sendSystemMessage(EconomyCommandUtil.warning(Component.translatable("command.futureshops.deposit.not_enough_coins")));
                    return 0;
                }
                amountMinorUnits = requestedMinorUnits;
            } catch (IllegalArgumentException ex) {
                player.sendSystemMessage(EconomyCommandUtil.error(Component.translatable("command.futureshops.error.invalid_amount")));
                return 0;
            }
        }

        // Phase 3: Consume physical currency (built-in: mint ledger + stack
        // shrink; foreign: face-value stack shrink, never overshooting).
        PhysicalCurrencyAdapter.ConsumeSummary summary = currency.consumeUpTo(player, amountMinorUnits);
        if (summary.creditedMinor() <= 0L) {
            // The player provably holds currency (Phase 2 gate), so the requested
            // amount just isn't payable in whole units of it.
            long smallestUnit = currency.denominations().get(currency.denominations().size() - 1).valueMinor();
            player.sendSystemMessage(EconomyCommandUtil.warning(Component.translatable(
                    "command.futureshops.deposit.invalid_denomination",
                    EconomyCommandUtil.formatMinorUnits(smallestUnit, provider.getDecimalPlaces()))));
            return 0;
        }

        // Phase 4: Credit balance with actually-consumed value. Foreign currency
        // is restored on a failed credit (max balance / cancelled event); the
        // internal ledger consume is irreversible (pre-2.2 behavior, refundable
        // list is empty).
        TransactionResult result = provider.deposit(player.getUUID(), summary.creditedMinor());
        if (!result.success()) {
            for (var refund : summary.refundableStacks()) {
                player.getInventory().placeItemBackInInventory(refund);
            }
            EconomyCommandUtil.sendProviderError(player, result.errorCode());
            return 0;
        }

        // Fire MoneyDepositEvent (spec §33)
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                new MoneyDepositEvent(player.getUUID(), summary.creditedMinor(), summary.itemsConsumed()));

        String depositedText = EconomyCommandUtil.formatMinorUnits(summary.creditedMinor(), provider.getDecimalPlaces());
        String balanceText = EconomyCommandUtil.formatMinorUnits(result.resultingBalance(), provider.getDecimalPlaces());
        player.sendSystemMessage(EconomyCommandUtil.success(Component.translatable("command.futureshops.deposit.success",
                depositedText, provider.getCurrencyName(), balanceText)));
        return 1;
    }
}

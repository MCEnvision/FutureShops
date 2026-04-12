package com.enviouse.futureshops.command;

import com.enviouse.futureshops.coin.CoinValidationResult;
import com.enviouse.futureshops.coin.CoinValidationService;
import com.enviouse.futureshops.init.ModItems;
import com.enviouse.futureshops.server.economy.BalanceManager;
import com.enviouse.futureshops.server.economy.EconomyProvider;
import com.enviouse.futureshops.server.economy.TransactionResult;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class DepositCommand {
    private DepositCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("deposit")
            .executes(context -> {
                if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
                    context.getSource().sendFailure(Component.translatable("command.futureshops.player_only"));
                    return 0;
                }

                return deposit(player, null);
            })
            .then(Commands.argument("amount", StringArgumentType.word())
                .executes(context -> {
                    if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
                        context.getSource().sendFailure(Component.translatable("command.futureshops.player_only"));
                        return 0;
                    }

                    return deposit(player, StringArgumentType.getString(context, "amount"));
                })));
    }

    private static int deposit(ServerPlayer player, String requestedAmount) {
        EconomyProvider provider = BalanceManager.getProvider();
        Item coinItem = ModItems.COIN_ITEM.get();
        long denomination = ModItems.COIN_DENOMINATION_MINOR_UNITS;

        int invalidDestroyed = destroyInvalidCoins(player, coinItem);
        if (invalidDestroyed > 0) {
            player.sendSystemMessage(Component.translatable("command.futureshops.deposit.invalid_destroyed", invalidDestroyed));
        }

        long availableCoins = countCoins(player, coinItem);
        if (availableCoins <= 0L) {
            player.sendSystemMessage(Component.translatable("command.futureshops.deposit.no_coins"));
            return 0;
        }

        long coinsToDeposit = availableCoins;
        long amountMinorUnits;
        try {
            amountMinorUnits = Math.multiplyExact(availableCoins, denomination);
        } catch (ArithmeticException ex) {
            player.sendSystemMessage(Component.translatable("command.futureshops.error.server"));
            return 0;
        }

        if (requestedAmount != null) {
            try {
                long requestedMinorUnits = EconomyCommandUtil.parseAmountToMinorUnits(requestedAmount, provider.getDecimalPlaces());
                if (requestedMinorUnits % denomination != 0L) {
                    String denomText = EconomyCommandUtil.formatMinorUnits(denomination, provider.getDecimalPlaces());
                    player.sendSystemMessage(Component.translatable("command.futureshops.deposit.invalid_denomination", denomText));
                    return 0;
                }

                if (requestedMinorUnits > amountMinorUnits) {
                    player.sendSystemMessage(Component.translatable("command.futureshops.deposit.not_enough_coins"));
                    return 0;
                }

                amountMinorUnits = requestedMinorUnits;
                coinsToDeposit = requestedMinorUnits / denomination;
            } catch (IllegalArgumentException ex) {
                player.sendSystemMessage(Component.translatable("command.futureshops.error.invalid_amount"));
                return 0;
            }
        }

        TransactionResult result = provider.deposit(player.getUUID(), amountMinorUnits);
        if (!result.success()) {
            EconomyCommandUtil.sendProviderError(player, result.errorCode());
            return 0;
        }

        if (!removeCoins(player, coinItem, coinsToDeposit)) {
            provider.withdraw(player.getUUID(), amountMinorUnits);
            player.sendSystemMessage(Component.translatable("command.futureshops.error.server"));
            return 0;
        }

        String depositedText = EconomyCommandUtil.formatMinorUnits(amountMinorUnits, provider.getDecimalPlaces());
        String balanceText = EconomyCommandUtil.formatMinorUnits(result.resultingBalance(), provider.getDecimalPlaces());
        player.sendSystemMessage(Component.translatable("command.futureshops.deposit.success", depositedText, provider.getCurrencyName(), balanceText));
        return 1;
    }

    private static long countCoins(ServerPlayer player, Item coinItem) {
        long total = 0L;
        for (ItemStack stack : allCoinContainers(player)) {
            if (stack.getItem() == coinItem && CoinValidationService.validate(stack).valid()) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static boolean removeCoins(ServerPlayer player, Item coinItem, long coinsToRemove) {
        long remaining = coinsToRemove;
        for (ItemStack stack : allCoinContainers(player)) {
            if (remaining <= 0L) {
                break;
            }
            if (stack.getItem() != coinItem) {
                continue;
            }

            CoinValidationResult validation = CoinValidationService.validate(stack);
            if (!validation.valid()) {
                continue;
            }

            int taken = (int) Math.min((long) stack.getCount(), remaining);
            stack.shrink(taken);
            remaining -= taken;
        }

        return remaining == 0L;
    }

    private static int destroyInvalidCoins(ServerPlayer player, Item coinItem) {
        int destroyed = 0;
        for (ItemStack stack : allCoinContainers(player)) {
            if (stack.getItem() != coinItem) {
                continue;
            }

            if (!CoinValidationService.validate(stack).valid()) {
                destroyed += stack.getCount();
                stack.setCount(0);
            }
        }
        return destroyed;
    }

    private static List<ItemStack> allCoinContainers(ServerPlayer player) {
        return java.util.stream.Stream.concat(player.getInventory().items.stream(), player.getInventory().offhand.stream()).toList();
    }
}

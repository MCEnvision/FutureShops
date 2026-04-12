package com.enviouse.futureshops.command;

import com.enviouse.futureshops.coin.CoinMintService;
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

public final class WithdrawCommand {
    private WithdrawCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("withdraw")
            .then(Commands.argument("amount", StringArgumentType.word())
                .executes(context -> {
                    if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
                        context.getSource().sendFailure(Component.translatable("command.futureshops.player_only"));
                        return 0;
                    }

                    EconomyProvider provider = BalanceManager.getProvider();
                    long amountMinorUnits;
                    try {
                        amountMinorUnits = EconomyCommandUtil.parseAmountToMinorUnits(StringArgumentType.getString(context, "amount"), provider.getDecimalPlaces());
                    } catch (IllegalArgumentException ex) {
                        player.sendSystemMessage(Component.translatable("command.futureshops.error.invalid_amount"));
                        return 0;
                    }

                    Item coinItem = ModItems.COIN_ITEM.get();
                    long denomination = ModItems.COIN_DENOMINATION_MINOR_UNITS;
                    if (amountMinorUnits % denomination != 0L) {
                        String denomText = EconomyCommandUtil.formatMinorUnits(denomination, provider.getDecimalPlaces());
                        player.sendSystemMessage(Component.translatable("command.futureshops.withdraw.invalid_denomination", denomText));
                        return 0;
                    }

                    long coinCount = amountMinorUnits / denomination;
                    if (coinCount > Integer.MAX_VALUE || !hasSpaceForCoins(player, coinItem, coinCount)) {
                        player.sendSystemMessage(Component.translatable("command.futureshops.withdraw.no_inventory_space"));
                        return 0;
                    }

                    TransactionResult result = provider.withdraw(player.getUUID(), amountMinorUnits);
                    if (!result.success()) {
                        EconomyCommandUtil.sendProviderError(player, result.errorCode());
                        return 0;
                    }

                    if (!giveCoins(player, coinItem, coinCount)) {
                        provider.deposit(player.getUUID(), amountMinorUnits);
                        player.sendSystemMessage(Component.translatable("command.futureshops.error.server"));
                        return 0;
                    }

                    String withdrawnText = EconomyCommandUtil.formatMinorUnits(amountMinorUnits, provider.getDecimalPlaces());
                    String balanceText = EconomyCommandUtil.formatMinorUnits(result.resultingBalance(), provider.getDecimalPlaces());
                    player.sendSystemMessage(Component.translatable("command.futureshops.withdraw.success", withdrawnText, provider.getCurrencyName(), balanceText));
                    return 1;
                })));
    }

    private static boolean hasSpaceForCoins(ServerPlayer player, Item coinItem, long coinCount) {
        int maxStack = coinItem.getMaxStackSize();
        long stacksNeeded = (coinCount + maxStack - 1L) / maxStack;
        long emptySlots = allDepositTargets(player).stream().filter(ItemStack::isEmpty).count();
        return emptySlots >= stacksNeeded;
    }

    private static boolean giveCoins(ServerPlayer player, Item coinItem, long coinCount) {
        long remaining = coinCount;
        int maxStack = coinItem.getMaxStackSize();

        while (remaining > 0L) {
            int count = (int) Math.min(remaining, maxStack);
            ItemStack stack = CoinMintService.mintStack(player, count, ModItems.COIN_DENOMINATION_MINOR_UNITS);
            if (!player.getInventory().add(stack)) {
                return false;
            }
            remaining -= count;
        }

        return true;
    }

    private static List<ItemStack> allDepositTargets(ServerPlayer player) {
        return java.util.stream.Stream.concat(player.getInventory().items.stream(), player.getInventory().offhand.stream()).toList();
    }
}

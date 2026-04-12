package com.enviouse.futureshops.command;

import com.enviouse.futureshops.coin.CoinNbtKeys;
import com.enviouse.futureshops.coin.CoinValidationResult;
import com.enviouse.futureshops.coin.CoinValidationService;
import com.enviouse.futureshops.coin.SpentMintsSavedData;
import com.enviouse.futureshops.init.ModItems;
import com.enviouse.futureshops.server.economy.BalanceManager;
import com.enviouse.futureshops.server.economy.EconomyProvider;
import com.enviouse.futureshops.server.economy.TransactionResult;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

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
        Item coinItem = ModItems.COIN_ITEM.get();
        long denomination = ModItems.COIN_DENOMINATION_MINOR_UNITS;
        SpentMintsSavedData mintData = SpentMintsSavedData.get(player.getServer());

        // Phase 1: Destroy coins that fail checksum OR whose mint ID is consumed/unknown.
        int invalidDestroyed = destroyInvalidCoins(player, coinItem, mintData);
        if (invalidDestroyed > 0) {
            player.sendSystemMessage(EconomyCommandUtil.warning(Component.translatable("command.futureshops.deposit.invalid_destroyed", invalidDestroyed)));
        }

        // Phase 2: Count coins that are fully valid (checksum + unconsumed mint ID).
        long availableCoins = countValidCoins(player, coinItem, mintData);
        if (availableCoins <= 0L) {
            player.sendSystemMessage(EconomyCommandUtil.warning(Component.translatable("command.futureshops.deposit.no_coins")));
            return 0;
        }

        long coinsToDeposit = availableCoins;
        long amountMinorUnits;
        try {
            amountMinorUnits = Math.multiplyExact(availableCoins, denomination);
        } catch (ArithmeticException ex) {
            player.sendSystemMessage(EconomyCommandUtil.error(Component.translatable("command.futureshops.error.server")));
            return 0;
        }

        if (requestedAmount != null) {
            try {
                long requestedMinorUnits = EconomyCommandUtil.parseAmountToMinorUnits(requestedAmount, provider.getDecimalPlaces());
                if (requestedMinorUnits % denomination != 0L) {
                    String denomText = EconomyCommandUtil.formatMinorUnits(denomination, provider.getDecimalPlaces());
                    player.sendSystemMessage(EconomyCommandUtil.warning(Component.translatable("command.futureshops.deposit.invalid_denomination", denomText)));
                    return 0;
                }
                if (requestedMinorUnits > amountMinorUnits) {
                    player.sendSystemMessage(EconomyCommandUtil.warning(Component.translatable("command.futureshops.deposit.not_enough_coins")));
                    return 0;
                }
                amountMinorUnits = requestedMinorUnits;
                coinsToDeposit = requestedMinorUnits / denomination;
            } catch (IllegalArgumentException ex) {
                player.sendSystemMessage(EconomyCommandUtil.error(Component.translatable("command.futureshops.error.invalid_amount")));
                return 0;
            }
        }

        // Phase 3: Credit balance.
        TransactionResult result = provider.deposit(player.getUUID(), amountMinorUnits);
        if (!result.success()) {
            EconomyCommandUtil.sendProviderError(player, result.errorCode());
            return 0;
        }

        // Phase 4: Remove physical coins and collect their mint IDs for consumption.
        List<String> toConsume = new ArrayList<>();
        if (!removeCoinsAndCollectMintIds(player, coinItem, coinsToDeposit, mintData, toConsume)) {
            // Rollback the balance credit if we couldn't physically remove the coins.
            provider.withdraw(player.getUUID(), amountMinorUnits);
            player.sendSystemMessage(EconomyCommandUtil.error(Component.translatable("command.futureshops.error.server")));
            return 0;
        }

        // Phase 5: Mark mints consumed — prevents future double-deposit of these coins.
        mintData.consumeMints(toConsume);

        String depositedText = EconomyCommandUtil.formatMinorUnits(amountMinorUnits, provider.getDecimalPlaces());
        String balanceText = EconomyCommandUtil.formatMinorUnits(result.resultingBalance(), provider.getDecimalPlaces());
        player.sendSystemMessage(EconomyCommandUtil.success(Component.translatable("command.futureshops.deposit.success",
                depositedText, provider.getCurrencyName(), balanceText)));
        return 1;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Destroys coins that fail the checksum validation OR whose mint ID is
     * unknown / already consumed.  Returns the total count of destroyed items.
     */
    private static int destroyInvalidCoins(ServerPlayer player, Item coinItem, SpentMintsSavedData mintData) {
        int destroyed = 0;
        for (ItemStack stack : allCoinContainers(player)) {
            if (stack.getItem() != coinItem) continue;

            boolean invalid = !CoinValidationService.validate(stack).valid();

            if (!invalid) {
                // Secondary check: is the mint ID registered and not yet consumed?
                CompoundTag coinData = stack.getTag().getCompound(CoinNbtKeys.ROOT);
                String mintId = coinData.getString(CoinNbtKeys.MINT_ID);
                invalid = !mintData.isKnownAndUnconsumed(mintId);
            }

            if (invalid) {
                destroyed += stack.getCount();
                stack.setCount(0);
            }
        }
        return destroyed;
    }

    /** Counts coins that pass both checksum and mint-registry validation. */
    private static long countValidCoins(ServerPlayer player, Item coinItem, SpentMintsSavedData mintData) {
        long total = 0L;
        for (ItemStack stack : allCoinContainers(player)) {
            if (stack.getItem() != coinItem) continue;
            CoinValidationResult validation = CoinValidationService.validate(stack);
            if (!validation.valid()) continue;
            CompoundTag coinData = stack.getTag().getCompound(CoinNbtKeys.ROOT);
            String mintId = coinData.getString(CoinNbtKeys.MINT_ID);
            if (mintData.isKnownAndUnconsumed(mintId)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /**
     * Removes {@code coinsToRemove} valid coin items from the player's inventory
     * and collects the unique mint IDs of every touched stack.
     *
     * @param consumed output list — unique mint IDs of removed stacks
     * @return {@code true} if the exact count was removed
     */
    private static boolean removeCoinsAndCollectMintIds(
            ServerPlayer player, Item coinItem, long coinsToRemove,
            SpentMintsSavedData mintData, List<String> consumed) {
        long remaining = coinsToRemove;
        for (ItemStack stack : allCoinContainers(player)) {
            if (remaining <= 0L) break;
            if (stack.getItem() != coinItem) continue;

            CoinValidationResult validation = CoinValidationService.validate(stack);
            if (!validation.valid()) continue;

            CompoundTag coinData = stack.getTag().getCompound(CoinNbtKeys.ROOT);
            String mintId = coinData.getString(CoinNbtKeys.MINT_ID);
            if (!mintData.isKnownAndUnconsumed(mintId)) continue;

            int taken = (int) Math.min((long) stack.getCount(), remaining);
            stack.shrink(taken);
            remaining -= taken;
            if (!consumed.contains(mintId)) {
                consumed.add(mintId);
            }
        }
        return remaining == 0L;
    }

    private static List<ItemStack> allCoinContainers(ServerPlayer player) {
        return java.util.stream.Stream
                .concat(player.getInventory().items.stream(), player.getInventory().offhand.stream())
                .toList();
    }
}

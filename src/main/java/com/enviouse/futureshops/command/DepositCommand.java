package com.enviouse.futureshops.command;

import com.enviouse.futureshops.coin.CoinNbtKeys;
import com.enviouse.futureshops.coin.CoinValidationResult;
import com.enviouse.futureshops.coin.CoinValidationService;
import com.enviouse.futureshops.coin.SpentMintsSavedData;
import com.enviouse.futureshops.event.CoinDepositEvent;
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
import java.util.Comparator;
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
        SpentMintsSavedData mintData = SpentMintsSavedData.get(player.getServer());

        // Phase 1: Destroy coins that fail checksum OR whose mint ID is consumed/unknown.
        int invalidDestroyed = destroyInvalidCoins(player, coinItem, mintData);
        if (invalidDestroyed > 0) {
            player.sendSystemMessage(EconomyCommandUtil.warning(Component.translatable("command.futureshops.deposit.invalid_destroyed", invalidDestroyed)));
        }

        // Phase 2: Collect valid coin stacks with their per-stack value.
        List<CoinStackInfo> validStacks = collectValidStacks(player, coinItem, mintData);
        if (validStacks.isEmpty()) {
            player.sendSystemMessage(EconomyCommandUtil.warning(Component.translatable("command.futureshops.deposit.no_coins")));
            return 0;
        }

        long totalAvailableMinor = validStacks.stream().mapToLong(CoinStackInfo::totalValue).sum();

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

        // Phase 3: Credit balance.
        TransactionResult result = provider.deposit(player.getUUID(), amountMinorUnits);
        if (!result.success()) {
            EconomyCommandUtil.sendProviderError(player, result.errorCode());
            return 0;
        }

        // Phase 4: Remove physical coins (greedy, largest denomination first) and collect mint IDs.
        List<String> toConsume = new ArrayList<>();
        if (!removeCoinsForAmount(player, coinItem, amountMinorUnits, mintData, toConsume)) {
            provider.withdraw(player.getUUID(), amountMinorUnits);
            player.sendSystemMessage(EconomyCommandUtil.error(Component.translatable("command.futureshops.error.server")));
            return 0;
        }

        // Phase 5: Mark mints consumed.
        mintData.consumeMints(toConsume);

        // Fire CoinDepositEvent (spec §33)
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                new CoinDepositEvent(player.getUUID(), amountMinorUnits, toConsume.size()));

        String depositedText = EconomyCommandUtil.formatMinorUnits(amountMinorUnits, provider.getDecimalPlaces());
        String balanceText = EconomyCommandUtil.formatMinorUnits(result.resultingBalance(), provider.getDecimalPlaces());
        player.sendSystemMessage(EconomyCommandUtil.success(Component.translatable("command.futureshops.deposit.success",
                depositedText, provider.getCurrencyName(), balanceText)));
        return 1;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static int destroyInvalidCoins(ServerPlayer player, Item coinItem, SpentMintsSavedData mintData) {
        int destroyed = 0;
        for (ItemStack stack : allCoinContainers(player)) {
            if (stack.getItem() != coinItem) continue;

            boolean invalid = !CoinValidationService.validate(stack).valid();

            if (!invalid) {
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

    /**
     * Collects info about each valid coin stack: denomination, count, and total value.
     */
    private static List<CoinStackInfo> collectValidStacks(ServerPlayer player, Item coinItem, SpentMintsSavedData mintData) {
        List<CoinStackInfo> result = new ArrayList<>();
        for (ItemStack stack : allCoinContainers(player)) {
            if (stack.getItem() != coinItem) continue;
            CoinValidationResult validation = CoinValidationService.validate(stack);
            if (!validation.valid()) continue;
            CompoundTag coinData = stack.getTag().getCompound(CoinNbtKeys.ROOT);
            String mintId = coinData.getString(CoinNbtKeys.MINT_ID);
            if (!mintData.isKnownAndUnconsumed(mintId)) continue;
            long denomination = coinData.getLong(CoinNbtKeys.DENOMINATION);
            result.add(new CoinStackInfo(stack, mintId, denomination, stack.getCount(), denomination * stack.getCount()));
        }
        // Sort by denomination descending so we consume largest bills first
        result.sort(Comparator.comparingLong(CoinStackInfo::denomination).reversed());
        return result;
    }

    /**
     * Removes coins from the player's inventory to cover the target amount.
     * Uses a greedy approach: consumes largest-denomination stacks first.
     * Supports partial consumption of a stack when only some coins are needed.
     */
    private static boolean removeCoinsForAmount(
            ServerPlayer player, Item coinItem, long targetMinor,
            SpentMintsSavedData mintData, List<String> consumed) {
        long remaining = targetMinor;
        // Build a sorted list of valid stacks (largest denomination first)
        List<CoinStackInfo> stacks = collectValidStacks(player, coinItem, mintData);

        for (CoinStackInfo info : stacks) {
            if (remaining <= 0L) break;

            // How many coins from this stack do we need?
            long coinsNeeded = (remaining + info.denomination - 1L) / info.denomination;
            int toTake = (int) Math.min(coinsNeeded, info.count);

            long valueRemoved = info.denomination * toTake;
            // Don't overshoot — if taking toTake would exceed remaining, take fewer
            if (valueRemoved > remaining && toTake > 1) {
                toTake = (int) (remaining / info.denomination);
                valueRemoved = info.denomination * toTake;
            }
            if (toTake <= 0) continue;

            info.stack.shrink(toTake);
            remaining -= valueRemoved;
            if (!consumed.contains(info.mintId)) {
                consumed.add(info.mintId);
            }
        }
        // Allow a small tolerance: if we couldn't reach exact zero due to denomination granularity, fail
        return remaining <= 0L;
    }

    private static List<ItemStack> allCoinContainers(ServerPlayer player) {
        return java.util.stream.Stream
                .concat(player.getInventory().items.stream(), player.getInventory().offhand.stream())
                .toList();
    }

    private record CoinStackInfo(ItemStack stack, String mintId, long denomination, int count, long totalValue) {
    }
}

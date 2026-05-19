package com.enviouse.futureshops.command;

import com.enviouse.futureshops.money.MoneyNbtKeys;
import com.enviouse.futureshops.money.MoneyValidationResult;
import com.enviouse.futureshops.money.MoneyValidationService;
import com.enviouse.futureshops.money.SpentMintsSavedData;
import com.enviouse.futureshops.event.MoneyDepositEvent;
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
        Item coinItem = ModItems.MONEY_ITEM.get();
        SpentMintsSavedData mintData = SpentMintsSavedData.get(player.getServer());

        // Phase 1: Destroy coins that fail checksum (malformed / tampered NBT).
        // NB: "already consumed" and "over-authorized" cases are handled atomically
        // in Phase 4 via SpentMintsSavedData.consume; we don't pre-destroy them
        // because a stack's MintId might still have remaining balance.
        int invalidDestroyed = destroyInvalidCoins(player, coinItem);
        if (invalidDestroyed > 0) {
            player.sendSystemMessage(EconomyCommandUtil.warning(Component.translatable("command.futureshops.deposit.invalid_destroyed", invalidDestroyed)));
        }

        // Phase 2: Plan per-stack available counts using each mint's remaining balance.
        // Multiple stacks sharing the same mint ID are allocated in iteration order.
        List<PlannedStack> planned = planValidStacks(player, coinItem, mintData);
        if (planned.isEmpty()) {
            player.sendSystemMessage(EconomyCommandUtil.warning(Component.translatable("command.futureshops.deposit.no_coins")));
            return 0;
        }

        long totalAvailableMinor = planned.stream().mapToLong(p -> p.denomination * p.available).sum();

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

        // Phase 3: Consume mint ledger + shrink stacks greedily (largest denomination first).
        int acceptedTotal = consumeCoinsForAmount(mintData, planned, amountMinorUnits);

        // Phase 4: Credit balance with actually-consumed value.
        long creditedMinor = 0L;
        for (PlannedStack p : planned) {
            creditedMinor += p.denomination * p.taken;
        }
        TransactionResult result = provider.deposit(player.getUUID(), creditedMinor);
        if (!result.success()) {
            EconomyCommandUtil.sendProviderError(player, result.errorCode());
            return 0;
        }

        // Fire MoneyDepositEvent (spec §33)
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                new MoneyDepositEvent(player.getUUID(), creditedMinor, acceptedTotal));

        String depositedText = EconomyCommandUtil.formatMinorUnits(creditedMinor, provider.getDecimalPlaces());
        String balanceText = EconomyCommandUtil.formatMinorUnits(result.resultingBalance(), provider.getDecimalPlaces());
        player.sendSystemMessage(EconomyCommandUtil.success(Component.translatable("command.futureshops.deposit.success",
                depositedText, provider.getCurrencyName(), balanceText)));
        return 1;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static int destroyInvalidCoins(ServerPlayer player, Item coinItem) {
        int destroyed = 0;
        for (ItemStack stack : allCoinContainers(player)) {
            if (stack.getItem() != coinItem) continue;
            MoneyValidationResult validation = MoneyValidationService.validate(stack);
            if (!validation.valid()) {
                destroyed += stack.getCount();
                stack.setCount(0);
            }
        }
        return destroyed;
    }

    /**
     * Collects valid coin stacks and allocates each a per-stack "available" count
     * capped by the mint record's remaining balance. When multiple stacks share
     * a mint ID, earlier stacks get first dibs on the remaining balance.
     */
    private static List<PlannedStack> planValidStacks(ServerPlayer player, Item coinItem, SpentMintsSavedData mintData) {
        List<PlannedStack> result = new ArrayList<>();
        java.util.Map<String, Integer> budget = new java.util.HashMap<>();
        for (ItemStack stack : allCoinContainers(player)) {
            if (stack.getItem() != coinItem) continue;
            MoneyValidationResult validation = MoneyValidationService.validate(stack);
            if (!validation.valid()) continue;
            CompoundTag moneyData = stack.getTag().getCompound(MoneyNbtKeys.ROOT);
            String mintId = moneyData.getString(MoneyNbtKeys.MINT_ID);
            long denomination = moneyData.getLong(MoneyNbtKeys.DENOMINATION);

            int remaining = budget.computeIfAbsent(mintId, mintData::remainingCount);
            if (remaining <= 0) continue;
            int available = Math.min(stack.getCount(), remaining);
            if (available <= 0) continue;
            budget.put(mintId, remaining - available);
            result.add(new PlannedStack(stack, mintId, denomination, validation.authorizedCount(), available));
        }
        // Sort by denomination descending so we consume largest bills first.
        result.sort(Comparator.comparingLong((PlannedStack p) -> p.denomination).reversed());
        return result;
    }

    /**
     * Greedily consumes coins to cover {@code targetMinor}. Mutates {@link PlannedStack#taken}
     * and physically shrinks the underlying stacks + decrements the mint ledger.
     * Returns the total number of coins actually consumed.
     */
    private static int consumeCoinsForAmount(SpentMintsSavedData mintData,
                                             List<PlannedStack> planned, long targetMinor) {
        long remaining = targetMinor;
        int totalAccepted = 0;
        for (PlannedStack p : planned) {
            if (remaining <= 0L) break;
            if (p.available <= 0) continue;

            long coinsNeeded = (remaining + p.denomination - 1L) / p.denomination;
            int toTake = (int) Math.min(coinsNeeded, p.available);
            long valueRemoved = p.denomination * toTake;
            if (valueRemoved > remaining && toTake > 1) {
                toTake = (int) (remaining / p.denomination);
                valueRemoved = p.denomination * toTake;
            }
            if (toTake <= 0) continue;

            SpentMintsSavedData.ConsumeResult r = mintData.consume(p.mintId, toTake, p.denomination, p.authorizedCount);
            if (r.accepted() <= 0) {
                continue; // Mint race: already drained by another stack this tick.
            }
            p.stack.shrink(r.accepted());
            p.taken += r.accepted();
            remaining -= p.denomination * r.accepted();
            totalAccepted += r.accepted();
        }
        return totalAccepted;
    }

    private static List<ItemStack> allCoinContainers(ServerPlayer player) {
        return java.util.stream.Stream
                .concat(player.getInventory().items.stream(), player.getInventory().offhand.stream())
                .toList();
    }

    private static final class PlannedStack {
        final ItemStack stack;
        final String mintId;
        final long denomination;
        final int authorizedCount;
        final int available;
        int taken;

        PlannedStack(ItemStack stack, String mintId, long denomination, int authorizedCount, int available) {
            this.stack = stack;
            this.mintId = mintId;
            this.denomination = denomination;
            this.authorizedCount = authorizedCount;
            this.available = available;
        }
    }
}

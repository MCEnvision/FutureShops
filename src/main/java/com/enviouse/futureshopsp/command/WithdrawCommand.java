package com.enviouse.futureshopsp.command;

import com.enviouse.futureshopsp.money.CoinData;
import com.enviouse.futureshopsp.money.ModDataComponents;
import com.enviouse.futureshopsp.money.MoneyMintService;
import com.enviouse.futureshopsp.money.MoneyNbtKeys;
import com.enviouse.futureshopsp.money.SpentMintsSavedData;
import com.enviouse.futureshopsp.event.MoneyMintEvent;
import com.enviouse.futureshopsp.init.ModItems;
import com.enviouse.futureshopsp.server.economy.BalanceManager;
import net.minecraft.nbt.CompoundTag;
import com.enviouse.futureshopsp.server.economy.EconomyProvider;
import com.enviouse.futureshopsp.server.economy.TransactionResult;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class WithdrawCommand {

    /**
     * Bill denominations in minor units (cents), sorted largest-first.
     * Corresponds to $1000, $100, $50, $20, $10, $5, $1 bills.
     */
    private static final long[] DENOMINATIONS = {
            100_000L, // $1000
            10_000L,  // $100
            5_000L,   // $50
            2_000L,   // $20
            1_000L,   // $10
            500L,     // $5
            100L      // $1
    };

    private WithdrawCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // /withdraw <amount>            — defaults to "yes" (multiple bills)
        // /withdraw <amount> yes        — break into denominations
        // /withdraw <amount> no         — single coin of the full amount
        dispatcher.register(Commands.literal("withdraw")
            .then(Commands.argument("amount", StringArgumentType.word())
                .executes(context -> {
                    if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
                        context.getSource().sendFailure(EconomyCommandUtil.error(Component.translatable("command.futureshops.player_only")));
                        return 0;
                    }
                    return execute(player, StringArgumentType.getString(context, "amount"), true);
                })
                .then(Commands.argument("bills", StringArgumentType.word())
                    .executes(context -> {
                        if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
                            context.getSource().sendFailure(EconomyCommandUtil.error(Component.translatable("command.futureshops.player_only")));
                            return 0;
                        }
                        String billsArg = StringArgumentType.getString(context, "bills").toLowerCase();
                        boolean multipleBills = billsArg.equals("yes") || billsArg.equals("y") || billsArg.equals("true");
                        return execute(player, StringArgumentType.getString(context, "amount"), multipleBills);
                    }))));
    }

    private static int execute(ServerPlayer player, String rawAmount, boolean multipleBills) {
        if (!BalanceManager.isInternalEconomyReady()) {
            player.sendSystemMessage(EconomyCommandUtil.error(Component.translatable(
                    "command.futureshops.economy.internal_only")));
            return 0;
        }
        EconomyProvider provider = BalanceManager.getProvider();
        long amountMinorUnits;
        try {
            amountMinorUnits = EconomyCommandUtil.parseAmountToMinorUnits(rawAmount, provider.getDecimalPlaces());
        } catch (IllegalArgumentException ex) {
            player.sendSystemMessage(EconomyCommandUtil.error(Component.translatable("command.futureshops.error.invalid_amount")));
            return 0;
        }

        // Must be at least $1 (100 minor units)
        if (amountMinorUnits < 100L) {
            player.sendSystemMessage(EconomyCommandUtil.warning(Component.translatable("command.futureshops.withdraw.minimum", "1.00")));
            return 0;
        }

        // Must be a whole dollar amount (no cents for physical coins)
        if (amountMinorUnits % 100L != 0L) {
            player.sendSystemMessage(EconomyCommandUtil.warning(Component.translatable("command.futureshops.withdraw.whole_dollars_only")));
            return 0;
        }

        // Build the bill breakdown
        List<BillEntry> bills;
        if (multipleBills) {
            bills = breakIntoDenominations(amountMinorUnits);
        } else {
            bills = List.of(new BillEntry(amountMinorUnits, 1));
        }

        // Check inventory space
        int slotsNeeded = 0;
        for (BillEntry bill : bills) {
            slotsNeeded += 1; // each BillEntry occupies one inventory slot
        }
        if (!hasSpace(player, slotsNeeded)) {
            player.sendSystemMessage(EconomyCommandUtil.warning(Component.translatable("command.futureshops.withdraw.no_inventory_space")));
            return 0;
        }

        // Debit balance
        TransactionResult result = provider.withdraw(player.getUUID(), amountMinorUnits);
        if (!result.success()) {
            EconomyCommandUtil.sendProviderError(player, result.errorCode());
            return 0;
        }

        // Mint and give coins
        if (!giveAllBills(player, bills)) {
            provider.deposit(player.getUUID(), amountMinorUnits);
            player.sendSystemMessage(EconomyCommandUtil.error(Component.translatable("command.futureshops.error.server")));
            return 0;
        }

        String withdrawnText = EconomyCommandUtil.formatMinorUnits(amountMinorUnits, provider.getDecimalPlaces());
        String balanceText = EconomyCommandUtil.formatMinorUnits(result.resultingBalance(), provider.getDecimalPlaces());
        if (multipleBills) {
            StringBuilder detail = new StringBuilder();
            for (BillEntry bill : bills) {
                if (!detail.isEmpty()) detail.append(", ");
                detail.append(bill.count).append("x $").append(bill.denominationMinor / 100L);
            }
            player.sendSystemMessage(EconomyCommandUtil.success(Component.translatable(
                    "command.futureshops.withdraw.success.bills",
                    withdrawnText, provider.getCurrencyName(), balanceText, detail.toString())));
        } else {
            player.sendSystemMessage(EconomyCommandUtil.success(Component.translatable(
                    "command.futureshops.withdraw.success", withdrawnText, provider.getCurrencyName(), balanceText)));
        }
        return 1;
    }

    /**
     * Breaks the given amount (in minor units) into the fewest bills using
     * denominations: $1000, $100, $50, $20, $10, $5, $1.
     */
    private static List<BillEntry> breakIntoDenominations(long amountMinor) {
        List<BillEntry> result = new ArrayList<>();
        long remaining = amountMinor;
        for (long denom : DENOMINATIONS) {
            if (remaining <= 0L) break;
            long count = remaining / denom;
            if (count > 0L) {
                // Split into stacks of 64 max per slot
                while (count > 0L) {
                    int batch = (int) Math.min(count, 64L);
                    result.add(new BillEntry(denom, batch));
                    count -= batch;
                }
                remaining %= denom;
            }
        }
        return result;
    }

    private static boolean hasSpace(ServerPlayer player, int slotsNeeded) {
        long emptySlots = player.getInventory().items.stream().filter(ItemStack::isEmpty).count();
        emptySlots += player.getInventory().offhand.stream().filter(ItemStack::isEmpty).count();
        return emptySlots >= slotsNeeded;
    }

    private static boolean giveAllBills(ServerPlayer player, List<BillEntry> bills) {
                SpentMintsSavedData mintData = SpentMintsSavedData.get(player.getServer());

        for (BillEntry bill : bills) {
            ItemStack stack = MoneyMintService.mintStack(player, bill.count, bill.denominationMinor);

            CoinData moneyData = stack.get(ModDataComponents.COIN_DATA.get());
            // authorizedCount == batch size; the entire stack shares one mint ID so
            // it remains stackable with itself across splits.
            mintData.registerMint(
                    moneyData.mintId(),
                    player.getUUID(),
                    bill.denominationMinor,
                    bill.count,
                    moneyData.mintTimestamp(),
                    moneyData.mintServer());

            // Fire MoneyMintEvent (spec §33)
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                    new MoneyMintEvent(player.getUUID(), bill.denominationMinor, bill.count,
                            moneyData.mintId()));

            if (!player.getInventory().add(stack)) {
                return false;
            }
        }
        return true;
    }

    private record BillEntry(long denominationMinor, int count) {
    }
}

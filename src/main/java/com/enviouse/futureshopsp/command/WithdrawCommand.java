package com.enviouse.futureshopsp.command;

import com.enviouse.futureshopsp.money.CoinData;
import com.enviouse.futureshopsp.money.ModDataComponents;
import com.enviouse.futureshopsp.money.MoneyMintService;
import com.enviouse.futureshopsp.money.SpentMintsSavedData;
import com.enviouse.futureshopsp.event.MoneyMintEvent;
import com.enviouse.futureshopsp.init.ModItems;
import com.enviouse.futureshopsp.server.economy.BalanceManager;
import com.enviouse.futureshopsp.server.economy.CustodyState;
import com.enviouse.futureshopsp.server.economy.EconomyRecordChecksum;
import com.enviouse.futureshopsp.server.economy.EconomyProvider;
import com.enviouse.futureshopsp.server.transaction.ShopTransactionUtil;
import com.enviouse.futureshopsp.api.economy.MutationKind;
import com.enviouse.futureshopsp.api.economy.MutationRequest;
import com.enviouse.futureshopsp.api.economy.MutationReceipt;
import com.enviouse.futureshopsp.api.economy.ProviderResult;
import com.enviouse.futureshopsp.api.economy.RequestId;
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

        RequestId requestId = RequestId.random();
        MutationRequest request = MutationRequest.forPlayer(requestId, player.getUUID(), amountMinorUnits,
                MutationKind.WITHDRAW);
        ProviderResult<MutationReceipt> mutation = BalanceManager.getCoordinator().executeWithCustody(request,
                player.getUUID(), "physical-money-withdraw", slotsNeeded, custodyHash(bills), CustodyState.HELD);
        if (!mutation.confirmed()) {
            EconomyCommandUtil.sendProviderError(player, mutation);
            return 0;
        }

        // Mint and give coins
        if (!giveAllBills(player, bills)) {
            BalanceManager.getCoordinator().markRecoveryRequired("withdraw delivery requires recovery");
            player.sendSystemMessage(EconomyCommandUtil.error(Component.translatable(
                    "command.futureshops.economy.recovery_required")));
            return 0;
        }

        try {
            BalanceManager.getCoordinator().deliverCustody(requestId.child("custody"));
            BalanceManager.getCoordinator().claimCustody(requestId.child("custody"));
        } catch (RuntimeException exception) {
            BalanceManager.getCoordinator().markRecoveryRequired("withdraw custody finalization requires recovery");
            player.sendSystemMessage(EconomyCommandUtil.error(Component.translatable(
                    "command.futureshops.economy.recovery_required")));
            return 0;
        }

        String withdrawnText = EconomyCommandUtil.formatMinorUnits(amountMinorUnits, provider.getDecimalPlaces());
        long resultingBalance = mutation.receipt().flatMap(receipt -> receipt.resultingBalanceMinorUnits().isPresent()
                ? java.util.Optional.of(receipt.resultingBalanceMinorUnits().getAsLong())
                : java.util.Optional.empty()).orElseGet(() -> BalanceManager.getBalance(player.getUUID()));
        String balanceText = EconomyCommandUtil.formatMinorUnits(resultingBalance, provider.getDecimalPlaces());
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
        List<ItemStack> mintedStacks = new ArrayList<>(bills.size());
        for (BillEntry bill : bills) {
            mintedStacks.add(MoneyMintService.mintStack(player, bill.count, bill.denominationMinor));
        }
        if (!ShopTransactionUtil.insertIntoInventory(player.getInventory(), mintedStacks)) {
            return false;
        }
        player.getInventory().setChanged();

        for (int index = 0; index < bills.size(); index++) {
            BillEntry bill = bills.get(index);
            ItemStack stack = mintedStacks.get(index);
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
        }
        return true;
    }

    private static String custodyHash(List<BillEntry> bills) {
        StringBuilder canonical = new StringBuilder("physical-money-withdraw|");
        for (BillEntry bill : bills) {
            canonical.append(bill.denominationMinor).append('|').append(bill.count).append(';');
        }
        return EconomyRecordChecksum.sha256(canonical.toString());
    }

    private record BillEntry(long denominationMinor, int count) {
    }
}

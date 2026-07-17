package com.enviouse.futureshops.command;

import com.enviouse.futureshops.money.CurrencyManager;
import com.enviouse.futureshops.money.CurrencyWithdrawalService;
import com.enviouse.futureshops.money.PhysicalCurrencyAdapter;
import com.enviouse.futureshops.server.economy.BalanceManager;
import com.enviouse.futureshops.server.economy.EconomyProvider;
import com.enviouse.futureshops.server.economy.AtmService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import java.util.LinkedHashMap;
import java.util.Map;

public final class WithdrawCommand {

    private WithdrawCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // /withdraw <amount>            — defaults to "yes" (multiple bills)
        // /withdraw <amount> yes        — break into denominations
        // /withdraw <amount> no         — single coin of the full amount (built-in currency only)
        dispatcher.register(Commands.literal("withdraw")
            // /withdraw with no amount opens the richer denomination picker.
            .executes(context -> {
                if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
                    context.getSource().sendFailure(EconomyCommandUtil.error(Component.translatable("command.futureshops.player_only")));
                    return 0;
                }
                AtmService.sendData(player, true);
                return 1;
            })
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
        EconomyProvider provider = BalanceManager.getProvider();
        PhysicalCurrencyAdapter currency = CurrencyManager.get();
        long amountMinorUnits;
        try {
            amountMinorUnits = EconomyCommandUtil.parseAmountToMinorUnits(rawAmount, provider.getDecimalPlaces());
        } catch (IllegalArgumentException ex) {
            player.sendSystemMessage(EconomyCommandUtil.error(Component.translatable("command.futureshops.error.invalid_amount")));
            return 0;
        }

        CurrencyWithdrawalService.Result result =
                CurrencyWithdrawalService.withdrawAutomatic(player, amountMinorUnits, multipleBills);
        if (!result.success()) {
            switch (result.code()) {
                case BELOW_MINIMUM -> {
                    long smallest = currency.denominations().get(currency.denominations().size() - 1).valueMinor();
                    player.sendSystemMessage(EconomyCommandUtil.warning(Component.translatable(
                            "command.futureshops.withdraw.minimum",
                            EconomyCommandUtil.formatMinorUnits(smallest, provider.getDecimalPlaces()))));
                }
                case NOT_REPRESENTABLE -> {
                    if (currency.isInternal()) {
                        player.sendSystemMessage(EconomyCommandUtil.warning(Component.translatable(
                                "command.futureshops.withdraw.whole_dollars_only")));
                    } else {
                        long[] values = currency.denominations().stream()
                                .mapToLong(PhysicalCurrencyAdapter.Denomination::valueMinor).toArray();
                        player.sendSystemMessage(EconomyCommandUtil.warning(Component.translatable(
                                "command.futureshops.withdraw.not_representable", denominationList(provider, values))));
                    }
                }
                case NO_INVENTORY_SPACE -> player.sendSystemMessage(EconomyCommandUtil.warning(
                        Component.translatable("command.futureshops.withdraw.no_inventory_space")));
                case INSUFFICIENT_FUNDS, CANCELLED -> EconomyCommandUtil.sendProviderError(player, result.providerError());
                case INVALID_AMOUNT -> player.sendSystemMessage(EconomyCommandUtil.error(
                        Component.translatable("command.futureshops.error.invalid_amount")));
                case INVALID_PLAN, CURRENCY_CHANGED, SERVER_ERROR, SUCCESS -> player.sendSystemMessage(
                        EconomyCommandUtil.error(Component.translatable("command.futureshops.error.server")));
            }
            return 0;
        }

        String withdrawnText = EconomyCommandUtil.formatMinorUnits(amountMinorUnits, provider.getDecimalPlaces());
        String balanceText = EconomyCommandUtil.formatMinorUnits(result.resultingBalance(), provider.getDecimalPlaces());
        if (result.bills().size() > 1 || multipleBills) {
            Map<Long, Integer> grouped = new LinkedHashMap<>();
            for (CurrencyWithdrawalService.BillPortion bill : result.bills()) {
                grouped.merge(bill.valueMinor(), bill.count(), Integer::sum);
            }
            StringBuilder detail = new StringBuilder();
            for (Map.Entry<Long, Integer> bill : grouped.entrySet()) {
                if (!detail.isEmpty()) detail.append(", ");
                detail.append(bill.getValue()).append("x ")
                        .append(EconomyCommandUtil.formatMinorUnits(bill.getKey(), provider.getDecimalPlaces()));
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

    private static String denominationList(EconomyProvider provider, long[] values) {
        StringBuilder sb = new StringBuilder();
        for (long value : values) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(EconomyCommandUtil.formatMinorUnits(value, provider.getDecimalPlaces()));
        }
        return sb.toString();
    }

}

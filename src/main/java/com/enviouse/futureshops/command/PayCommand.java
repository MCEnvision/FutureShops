package com.enviouse.futureshops.command;

import com.enviouse.futureshops.server.economy.BalanceManager;
import com.enviouse.futureshops.server.economy.EconomyProvider;
import com.enviouse.futureshops.server.economy.TransactionResult;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class PayCommand {
    private PayCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("pay")
            .then(Commands.argument("target", EntityArgument.player())
                .then(Commands.argument("amount", StringArgumentType.word())
                    .executes(context -> {
                        if (!(context.getSource().getEntity() instanceof ServerPlayer payer)) {
                            context.getSource().sendFailure(Component.translatable("command.futureshops.player_only"));
                            return 0;
                        }

                        ServerPlayer target = EntityArgument.getPlayer(context, "target");
                        if (payer.getUUID().equals(target.getUUID())) {
                            payer.sendSystemMessage(Component.translatable("command.futureshops.pay.self"));
                            return 0;
                        }

                        EconomyProvider provider = BalanceManager.getProvider();
                        long amountMinorUnits;
                        try {
                            amountMinorUnits = EconomyCommandUtil.parseAmountToMinorUnits(StringArgumentType.getString(context, "amount"), provider.getDecimalPlaces());
                        } catch (IllegalArgumentException ex) {
                            payer.sendSystemMessage(Component.translatable("command.futureshops.error.invalid_amount"));
                            return 0;
                        }

                        TransactionResult result = BalanceManager.transfer(payer.getUUID(), target.getUUID(), amountMinorUnits);
                        if (!result.success()) {
                            EconomyCommandUtil.sendProviderError(payer, result.errorCode());
                            return 0;
                        }

                        String amountText = EconomyCommandUtil.formatMinorUnits(amountMinorUnits, provider.getDecimalPlaces());
                        String payerBalanceText = EconomyCommandUtil.formatMinorUnits(result.resultingBalance(), provider.getDecimalPlaces());
                        payer.sendSystemMessage(Component.translatable("command.futureshops.pay.success.sender", amountText, provider.getCurrencyName(), target.getName(), payerBalanceText));

                        String targetBalanceText = EconomyCommandUtil.formatMinorUnits(provider.getBalance(target.getUUID()), provider.getDecimalPlaces());
                        target.sendSystemMessage(Component.translatable("command.futureshops.pay.success.target", payer.getName(), amountText, provider.getCurrencyName(), targetBalanceText));
                        return 1;
                    }))));
    }
}


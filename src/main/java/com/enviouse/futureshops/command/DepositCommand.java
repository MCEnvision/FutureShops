package com.enviouse.futureshops.command;

import com.enviouse.futureshops.money.CurrencyManager;
import com.enviouse.futureshops.money.PhysicalCurrencyAdapter;
import com.enviouse.futureshops.server.economy.BalanceManager;
import com.enviouse.futureshops.server.economy.EconomyProvider;
import com.enviouse.futureshops.server.escrow.runtime.EscrowCashDepositService;
import com.enviouse.futureshops.server.escrow.runtime.EscrowCashDepositService.DepositRequest;
import com.enviouse.futureshops.server.escrow.runtime.EscrowCashDepositService.DepositResult;
import com.enviouse.futureshops.server.escrow.runtime.EscrowCashDepositService.Source;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.OptionalLong;

public final class DepositCommand {
    private DepositCommand() {
    }

    public static void register(
            CommandDispatcher<CommandSourceStack> dispatcher
    ) {
        dispatcher.register(Commands.literal("deposit")
                .executes(context -> player(context.getSource(), null))
                .then(Commands.argument("amount", StringArgumentType.word())
                        .executes(context -> player(context.getSource(),
                                StringArgumentType.getString(
                                        context, "amount")))));
    }

    private static int player(CommandSourceStack source,
                              String requestedAmount) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(EconomyCommandUtil.error(
                    Component.translatable(
                            "command.futureshops.player_only")));
            return 0;
        }
        return deposit(player, requestedAmount);
    }

    private static int deposit(ServerPlayer player,
                               String requestedAmount) {
        int decimalPlaces;
        String currencyName;
        PhysicalCurrencyAdapter currency;
        DepositRequest request;
        try (CurrencyManager.ConfigurationReadLease ignored =
                     CurrencyManager.acquireConfigurationReadLease()) {
            EconomyProvider economy = BalanceManager.getProvider();
            decimalPlaces = economy.getDecimalPlaces();
            currencyName = economy.getCurrencyName();
            currency = CurrencyManager.getOrNull();
            OptionalLong requested = OptionalLong.empty();
            if (requestedAmount != null) {
                try {
                    requested = OptionalLong.of(
                            EconomyCommandUtil.parseAmountToMinorUnits(
                                    requestedAmount,
                                    decimalPlaces));
                } catch (IllegalArgumentException exception) {
                    player.sendSystemMessage(EconomyCommandUtil.error(
                            Component.translatable(
                                    "command.futureshops.error.invalid_amount")));
                    return 0;
                }
            }
            request = EscrowCashDepositService.requestForCurrentState(
                    player, Source.INVENTORY, requested);
        }
        DepositResult result = EscrowCashDepositService.deposit(
                player, request);
        return respond(player, decimalPlaces, currencyName,
                currency, result);
    }

    private static int respond(ServerPlayer player,
                               int decimalPlaces,
                               String currencyName,
                               PhysicalCurrencyAdapter currency,
                               DepositResult result) {
        if (result.successful()) {
            String deposited = EconomyCommandUtil.formatMinorUnits(
                    result.depositedMinorUnits(),
                    decimalPlaces);
            String balance = EconomyCommandUtil.formatMinorUnits(
                    result.resultingBalanceMinorUnits(),
                    decimalPlaces);
            player.sendSystemMessage(EconomyCommandUtil.success(
                    Component.translatable(
                            "command.futureshops.deposit.success",
                            deposited, currencyName, balance)));
            if (result.overflowClaimMinorUnits() > 0L) {
                player.sendSystemMessage(EconomyCommandUtil.info(
                        Component.translatable(
                                "command.futureshops.deposit.overflow_claim",
                                EconomyCommandUtil.formatMinorUnits(
                                        result.overflowClaimMinorUnits(),
                                        decimalPlaces))));
            }
            return 1;
        }
        switch (result.status()) {
            case NO_CURRENCY -> {
                String accepted = currency == null ? null
                        : currency.acceptedItemsSummary(
                                decimalPlaces);
                player.sendSystemMessage(EconomyCommandUtil.warning(
                        accepted == null || accepted.isBlank()
                                ? Component.translatable(
                                "command.futureshops.deposit.no_coins")
                                : Component.translatable(
                                "command.futureshops.deposit.no_currency_accepted",
                                accepted)));
            }
            case INVALID_AMOUNT -> player.sendSystemMessage(
                    EconomyCommandUtil.error(Component.translatable(
                            "command.futureshops.error.invalid_amount")));
            case NOT_ENOUGH_CURRENCY -> player.sendSystemMessage(
                    EconomyCommandUtil.warning(Component.translatable(
                            "command.futureshops.deposit.not_enough_coins")));
            case INVALID_DENOMINATION -> {
                long smallest = currency == null
                        || currency.denominations().isEmpty() ? 1L
                        : currency.denominations().get(
                        currency.denominations().size() - 1).valueMinor();
                player.sendSystemMessage(EconomyCommandUtil.warning(
                        Component.translatable(
                                "command.futureshops.deposit.invalid_denomination",
                                EconomyCommandUtil.formatMinorUnits(
                                        smallest,
                                        decimalPlaces))));
            }
            case TOO_MANY_ITEMS -> player.sendSystemMessage(
                    EconomyCommandUtil.warning(Component.translatable(
                            "command.futureshops.deposit.too_many_items",
                            EscrowCashDepositService.MAX_ITEMS_CONSUMED)));
            case WRONG_PROVIDER -> player.sendSystemMessage(
                    EconomyCommandUtil.warning(Component.translatable(
                            "command.futureshops.deposit.wrong_currency",
                            currency == null ? "unknown" : currency.id())));
            case CREATIVE_BLOCKED -> player.sendSystemMessage(
                    EconomyCommandUtil.warning(Component.translatable(
                            "command.futureshops.deposit.creative_blocked")));
            case LEGACY_MIGRATION_REQUIRED -> player.sendSystemMessage(
                    EconomyCommandUtil.warning(Component.translatable(
                            "command.futureshops.deposit.legacy_migration_required")));
            case INVALID_CURRENCY -> player.sendSystemMessage(
                    EconomyCommandUtil.error(Component.translatable(
                            "command.futureshops.deposit.money_invalid")));
            case REQUEST_CONFLICT -> player.sendSystemMessage(
                    EconomyCommandUtil.error(Component.translatable(
                            "command.futureshops.deposit.request_conflict")));
            case CANCELLED -> player.sendSystemMessage(
                    EconomyCommandUtil.warning(Component.translatable(
                            "command.futureshops.deposit.cancelled")));
            case CONFIG_CHANGED -> player.sendSystemMessage(
                    EconomyCommandUtil.warning(Component.translatable(
                            "command.futureshops.deposit.config_changed")));
            case RATE_LIMITED -> player.sendSystemMessage(
                    EconomyCommandUtil.warning(Component.translatable(
                            "command.futureshops.deposit.rate_limited",
                            result.retryAfterSeconds())));
            case ESCROW_UNAVAILABLE, RECOVERY_REQUIRED ->
                    player.sendSystemMessage(EconomyCommandUtil.warning(
                            Component.translatable(
                                    "command.futureshops.deposit.recovery_required")));
            case SUCCESS -> throw new IllegalStateException(
                    "Successful cash deposit was handled earlier");
        }
        return 0;
    }
}

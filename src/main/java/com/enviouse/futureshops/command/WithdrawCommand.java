package com.enviouse.futureshops.command;

import com.enviouse.futureshops.money.AtmCurrencyCatalog;
import com.enviouse.futureshops.money.AtmCurrencyRoute;
import com.enviouse.futureshops.money.CurrencyManager;
import com.enviouse.futureshops.money.CurrencyMath;
import com.enviouse.futureshops.money.PhysicalCurrencyAdapter;
import com.enviouse.futureshops.server.economy.BalanceManager;
import com.enviouse.futureshops.server.economy.EconomyProvider;
import com.enviouse.futureshops.server.economy.AtmService;
import com.enviouse.futureshops.server.escrow.runtime.AtmWithdrawalOutcome;
import com.enviouse.futureshops.server.escrow.runtime.AtmWithdrawalStatus;
import com.enviouse.futureshops.server.shop.ShopResultCode;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class WithdrawCommand {

    private WithdrawCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("withdraw")
            .executes(context -> {
                if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
                    context.getSource().sendFailure(EconomyCommandUtil.error(Component.translatable("command.futureshops.player_only")));
                    return 0;
                }
                AtmService.requestData(player, true);
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
                        Optional<Boolean> multipleBills = parseBillMode(
                                StringArgumentType.getString(context, "bills"));
                        if (multipleBills.isEmpty()) {
                            player.sendSystemMessage(EconomyCommandUtil.warning(
                                    Component.translatable(
                                            "command.futureshops.withdraw.invalid_bill_mode")));
                            return 0;
                        }
                        return execute(player,
                                StringArgumentType.getString(context, "amount"),
                                multipleBills.orElseThrow());
                    }))));
    }

    private static int execute(ServerPlayer player, String rawAmount, boolean multipleBills) {
        EconomyProvider provider = BalanceManager.getProvider();
        Optional<PendingWithdrawalRequest> loaded;
        try {
            loaded = PendingWithdrawalRequest.load(player);
        } catch (RuntimeException exception) {
            player.sendSystemMessage(EconomyCommandUtil.error(
                    Component.translatable(
                            "command.futureshops.withdraw.pending_corrupt")));
            return 0;
        }
        if (rawAmount.equalsIgnoreCase("retry")) {
            if (loaded.isEmpty()) {
                player.sendSystemMessage(EconomyCommandUtil.warning(
                        Component.translatable(
                                "command.futureshops.withdraw.no_pending")));
                return 0;
            }
            return executePending(player, provider, loaded.orElseThrow());
        }

        long amountMinorUnits;
        try {
            amountMinorUnits = EconomyCommandUtil.parseAmountToMinorUnits(rawAmount, provider.getDecimalPlaces());
        } catch (IllegalArgumentException ex) {
            player.sendSystemMessage(EconomyCommandUtil.error(Component.translatable("command.futureshops.error.invalid_amount")));
            return 0;
        }

        if (loaded.isPresent()) {
            PendingWithdrawalRequest pending = loaded.orElseThrow();
            if (!pending.matches(amountMinorUnits, multipleBills)) {
                player.sendSystemMessage(EconomyCommandUtil.warning(
                        Component.translatable(
                                "command.futureshops.withdraw.another_pending",
                                EconomyCommandUtil.formatMinorUnits(
                                        pending.amountMinorUnits(),
                                        provider.getDecimalPlaces()),
                                pending.multipleBills() ? "yes" : "no")));
                return 0;
            }
            return executePending(player, provider, pending);
        }

        PhysicalCurrencyAdapter currency = CurrencyManager.get();
        AtmCurrencyCatalog catalog;
        try {
            catalog = AtmCurrencyCatalog.capture(currency, provider);
        } catch (RuntimeException exception) {
            player.sendSystemMessage(EconomyCommandUtil.error(
                    Component.translatable(
                            "command.futureshops.error.server")));
            return 0;
        }
        long[] values = catalog.denominations().stream()
                .mapToLong(AtmCurrencyCatalog.Denomination::valueMinorUnits)
                .toArray();
        int[] maximumStacks = catalog.denominations().stream()
                .mapToInt(AtmCurrencyCatalog.Denomination::maximumStackSize)
                .toArray();
        long smallest = values[values.length - 1];
        if (amountMinorUnits < smallest) {
            player.sendSystemMessage(EconomyCommandUtil.warning(
                    Component.translatable(
                            "command.futureshops.withdraw.minimum",
                            EconomyCommandUtil.formatMinorUnits(
                                    smallest,
                                    provider.getDecimalPlaces()))));
            return 0;
        }
        if (!multipleBills
                && catalog.route()
                == AtmCurrencyRoute.FOREIGN_UNPROTECTED) {
            player.sendSystemMessage(EconomyCommandUtil.warning(
                    Component.translatable(
                            "command.futureshops.withdraw.foreign_single_unsupported")));
            return 0;
        }
        try {
            if (BalanceManager.getBalance(player.getUUID())
                    < amountMinorUnits) {
                EconomyCommandUtil.sendProviderError(
                        player, ShopResultCode.INSUFFICIENT_FUNDS);
                return 0;
            }
        } catch (RuntimeException exception) {
            player.sendSystemMessage(EconomyCommandUtil.error(
                    Component.translatable(
                            "command.futureshops.error.server")));
            return 0;
        }
        CurrencyMath.BreakResult breakdown =
                CurrencyMath.breakIntoDenominations(
                        amountMinorUnits, values, maximumStacks,
                        AtmCurrencyCatalog.MAXIMUM_BILLS);
        if (breakdown.limitExceeded()) {
            player.sendSystemMessage(EconomyCommandUtil.warning(
                    Component.translatable(
                            "command.futureshops.withdraw.too_many_bills",
                            AtmCurrencyCatalog.MAXIMUM_BILLS)));
            return 0;
        }
        if (breakdown.remainderMinor() != 0L) {
            if (currency.isInternal()) {
                player.sendSystemMessage(EconomyCommandUtil.warning(
                        Component.translatable(
                                "command.futureshops.withdraw.whole_dollars_only")));
            } else {
                player.sendSystemMessage(EconomyCommandUtil.warning(
                        Component.translatable(
                                "command.futureshops.withdraw.not_representable",
                                denominationList(provider, values))));
            }
            return 0;
        }

        List<Integer> counts = new ArrayList<>(Collections.nCopies(
                catalog.denominations().size(), 0));
        if (!multipleBills
                && catalog.route() == AtmCurrencyRoute.PROTECTED_ESCROW) {
            counts.set(0, 1);
        } else {
            int totalBills = 0;
            for (CurrencyMath.Portion portion : breakdown.portions()) {
                counts.set(portion.denominationIndex(), Math.addExact(
                        counts.get(portion.denominationIndex()),
                        portion.count()));
                totalBills = Math.addExact(totalBills, portion.count());
            }
            if (totalBills > AtmCurrencyCatalog.MAXIMUM_BILLS) {
                player.sendSystemMessage(EconomyCommandUtil.warning(
                        Component.translatable(
                                "command.futureshops.withdraw.too_many_bills",
                                AtmCurrencyCatalog.MAXIMUM_BILLS)));
                return 0;
            }
        }
        if (!catalog.plan(counts).valid()) {
            player.sendSystemMessage(EconomyCommandUtil.warning(
                    Component.translatable(
                            "command.futureshops.withdraw.too_many_stacks",
                            AtmCurrencyCatalog.MAXIMUM_CLAIM_STACKS)));
            return 0;
        }
        PendingWithdrawalRequest pending = PendingWithdrawalRequest.create(
                amountMinorUnits, multipleBills, catalog.signature(), counts);
        try {
            PendingWithdrawalRequest.persist(player, pending);
        } catch (RuntimeException exception) {
            player.sendSystemMessage(EconomyCommandUtil.error(
                    Component.translatable(
                            "command.futureshops.withdraw.persist_failed")));
            return 0;
        }
        return executePending(player, provider, pending);
    }

    private static int executePending(
            ServerPlayer player,
            EconomyProvider provider,
            PendingWithdrawalRequest pending
    ) {
        AtmWithdrawalOutcome result = AtmService.withdrawAutomatic(
                player, pending.requestId(),
                pending.amountMinorUnits(), pending.multipleBills(),
                pending.currencySignature(),
                pending.denominationCounts());
        boolean retained = !shouldClear(result);
        if (!retained) {
            try {
                PendingWithdrawalRequest.clear(player);
            } catch (RuntimeException exception) {
                retained = true;
            }
        }
        if (!result.status().success()) {
            sendFailure(player, result);
            if (retained) {
                sendRetained(player);
            }
            return 0;
        }

        String withdrawnText = EconomyCommandUtil.formatMinorUnits(
                pending.amountMinorUnits(), provider.getDecimalPlaces());
        String balanceText = EconomyCommandUtil.formatMinorUnits(
                result.balanceMinorUnits(), provider.getDecimalPlaces());
        String detail = billDetail(pending, provider);
        String suffix = detail.isEmpty() ? "" : ".bills";
        Component message = switch (result.status()) {
            case DELIVERED -> detail.isEmpty()
                    ? Component.translatable(
                    "command.futureshops.withdraw.success",
                    withdrawnText, provider.getCurrencyName(), balanceText)
                    : Component.translatable(
                    "command.futureshops.withdraw.success" + suffix,
                    withdrawnText, provider.getCurrencyName(), balanceText,
                    detail);
            case CLAIMED -> detail.isEmpty()
                    ? Component.translatable(
                    "command.futureshops.withdraw.claimed",
                    withdrawnText, provider.getCurrencyName(), balanceText)
                    : Component.translatable(
                    "command.futureshops.withdraw.claimed" + suffix,
                    withdrawnText, provider.getCurrencyName(), balanceText,
                    detail);
            case PARTIALLY_DELIVERED -> Component.translatable(
                    "command.futureshops.withdraw.partial",
                    withdrawnText, provider.getCurrencyName(), balanceText,
                    result.deliveredBillCount(), result.claimedBillCount());
            default -> throw new IllegalStateException(
                    "Successful withdrawal status is invalid");
        };
        player.sendSystemMessage(EconomyCommandUtil.success(message));
        if (retained) {
            sendRetained(player);
        }
        return 1;
    }

    private static boolean shouldClear(AtmWithdrawalOutcome result) {
        if (result.status().success()) {
            return true;
        }
        if (result.retryable()) {
            return false;
        }
        return result.status() != AtmWithdrawalStatus.CONFLICT
                && result.status() != AtmWithdrawalStatus.MANUAL_REVIEW;
    }

    private static void sendRetained(ServerPlayer player) {
        player.sendSystemMessage(EconomyCommandUtil.warning(
                Component.translatable(
                        "command.futureshops.withdraw.request_retained")));
    }

    private static String billDetail(
            PendingWithdrawalRequest pending,
            EconomyProvider provider
    ) {
        try {
            AtmCurrencyCatalog catalog = AtmCurrencyCatalog.capture(
                    CurrencyManager.get(), provider);
            if (!catalog.signature().equals(pending.currencySignature())
                    || catalog.denominations().size()
                    != pending.denominationCounts().size()) {
                return "";
            }
            if (!pending.multipleBills()
                    && catalog.route()
                    == AtmCurrencyRoute.PROTECTED_ESCROW) {
                return "1x " + EconomyCommandUtil.formatMinorUnits(
                        pending.amountMinorUnits(),
                        provider.getDecimalPlaces());
            }
            Map<Long, Integer> grouped = new LinkedHashMap<>();
            for (int index = 0;
                 index < pending.denominationCounts().size(); index++) {
                int count = pending.denominationCounts().get(index);
                if (count > 0) {
                    grouped.merge(catalog.denominations().get(index)
                            .valueMinorUnits(), count, Integer::sum);
                }
            }
            StringBuilder detail = new StringBuilder();
            for (Map.Entry<Long, Integer> bill : grouped.entrySet()) {
                if (!detail.isEmpty()) {
                    detail.append(", ");
                }
                detail.append(bill.getValue()).append("x ")
                        .append(EconomyCommandUtil.formatMinorUnits(
                                bill.getKey(),
                                provider.getDecimalPlaces()));
            }
            return detail.toString();
        } catch (RuntimeException exception) {
            return "";
        }
    }

    private static Optional<Boolean> parseBillMode(String raw) {
        return switch (raw.toLowerCase(java.util.Locale.ROOT)) {
            case "yes", "y", "true" -> Optional.of(true);
            case "no", "n", "false" -> Optional.of(false);
            default -> Optional.empty();
        };
    }

    private static void sendFailure(
            ServerPlayer player,
            AtmWithdrawalOutcome result
    ) {
        switch (result.status()) {
            case INSUFFICIENT_FUNDS -> EconomyCommandUtil.sendProviderError(
                    player, ShopResultCode.INSUFFICIENT_FUNDS);
            case CANCELLED -> EconomyCommandUtil.sendProviderError(
                    player, ShopResultCode.CANCELLED_BY_EVENT);
            case INVALID_AMOUNT -> player.sendSystemMessage(
                    EconomyCommandUtil.error(Component.translatable(
                            "command.futureshops.error.invalid_amount")));
            case MIGRATION_PENDING, RECOVERY_PENDING ->
                    player.sendSystemMessage(EconomyCommandUtil.warning(
                            Component.translatable(
                                    "command.futureshops.withdraw.recovery_pending")));
            case ESCROW_UNAVAILABLE -> player.sendSystemMessage(
                    EconomyCommandUtil.error(Component.translatable(
                            "command.futureshops.withdraw.escrow_unavailable")));
            case RATE_LIMITED -> player.sendSystemMessage(
                    EconomyCommandUtil.warning(Component.translatable(
                            "command.futureshops.withdraw.rate_limited")));
            case MANUAL_REVIEW -> player.sendSystemMessage(
                    EconomyCommandUtil.warning(Component.translatable(
                            "command.futureshops.withdraw.manual_review",
                            result.requestId().toString())));
            case DELIVERED, CLAIMED, PARTIALLY_DELIVERED,
                 INVALID_PLAN, CURRENCY_CHANGED, CONFLICT, SERVER_ERROR ->
                    player.sendSystemMessage(EconomyCommandUtil.error(
                            Component.translatable(
                                    "command.futureshops.error.server")));
        }
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

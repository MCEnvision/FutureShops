package com.enviouse.futureshops.command;

import com.enviouse.futureshops.server.economy.BalanceManager;
import com.enviouse.futureshops.server.escrow.runtime.PlayerPaymentService;
import com.enviouse.futureshops.server.escrow.runtime.PlayerPaymentService.PaymentStatusResult;
import com.enviouse.futureshops.server.escrow.runtime.PlayerPaymentService.PlayerPaymentResult;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public final class PayCommand {
    private PayCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("pay")
            .then(Commands.literal("status")
                .then(Commands.argument("request_id", UuidArgument.uuid())
                    .executes(context -> status(
                            context.getSource(),
                            UuidArgument.getUuid(
                                    context, "request_id")))))
            .then(Commands.argument("target", EntityArgument.player())
                .then(Commands.argument("amount", StringArgumentType.word())
                    .executes(context -> executePayment(
                            context.getSource(),
                            EntityArgument.getPlayer(context, "target"),
                            StringArgumentType.getString(context, "amount"),
                            UUID.randomUUID()))
                    .then(Commands.argument(
                                    "request_id", UuidArgument.uuid())
                            .executes(context -> executePayment(
                                    context.getSource(),
                                    EntityArgument.getPlayer(
                                            context, "target"),
                                    StringArgumentType.getString(
                                            context, "amount"),
                                    UuidArgument.getUuid(
                                            context, "request_id")))))));
    }

    private static int executePayment(
            CommandSourceStack source,
            ServerPlayer target,
            String amountText,
            UUID requestId
    ) {
        if (!(source.getEntity() instanceof ServerPlayer payer)) {
            source.sendFailure(EconomyCommandUtil.error(
                    Component.translatable(
                            "command.futureshops.player_only")));
            return 0;
        }
        PaymentStatusResult known = PlayerPaymentService.status(
                payer, requestId);
        int decimals = known.payment()
                .map(PlayerPaymentResult::currencyDecimals)
                .orElseGet(() -> BalanceManager.getProvider()
                        .getDecimalPlaces());
        long amountMinorUnits;
        try {
            amountMinorUnits = EconomyCommandUtil.parseAmountToMinorUnits(
                    amountText, decimals);
        } catch (IllegalArgumentException exception) {
            payer.sendSystemMessage(EconomyCommandUtil.error(
                    Component.translatable(
                            "command.futureshops.error.invalid_amount")));
            return 0;
        }
        payer.sendSystemMessage(EconomyCommandUtil.info(
                Component.translatable(
                        "command.futureshops.pay.reference.submitting",
                        requestId.toString())));
        PlayerPaymentResult result = PlayerPaymentService.pay(
                payer, target.getUUID(), requestId, amountMinorUnits);
        return respond(payer, target, result);
    }

    private static int status(
            CommandSourceStack source,
            UUID requestId
    ) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(EconomyCommandUtil.error(
                    Component.translatable(
                            "command.futureshops.player_only")));
            return 0;
        }
        PaymentStatusResult result = PlayerPaymentService.status(
                player, requestId);
        String key = switch (result.status()) {
            case COMPLETED -> "command.futureshops.pay.status.completed";
            case PENDING -> "command.futureshops.pay.status.pending";
            case CANCELLED -> "command.futureshops.pay.status.cancelled";
            case NOT_FOUND -> "command.futureshops.pay.status.not_found";
            case REQUEST_CONFLICT ->
                    "command.futureshops.pay.status.conflict";
            case RECOVERY_REQUIRED ->
                    "command.futureshops.pay.status.recovery";
            case ESCROW_UNAVAILABLE ->
                    "command.futureshops.pay.status.unavailable";
        };
        player.sendSystemMessage(EconomyCommandUtil.info(
                Component.translatable(key, requestId.toString())));
        return result.status()
                == PlayerPaymentService.PaymentStatus.COMPLETED ? 1 : 0;
    }

    private static int respond(
            ServerPlayer payer,
            ServerPlayer target,
            PlayerPaymentResult result
    ) {
        if (!result.successful()) {
            payer.sendSystemMessage(EconomyCommandUtil.info(
                    Component.translatable(
                            "command.futureshops.pay.reference.retry",
                            result.requestId().toString())));
            switch (result.status()) {
                case INVALID_AMOUNT -> payer.sendSystemMessage(
                        EconomyCommandUtil.error(Component.translatable(
                                "command.futureshops.error.invalid_amount")));
                case SELF_PAYMENT -> payer.sendSystemMessage(
                        EconomyCommandUtil.error(Component.translatable(
                                "command.futureshops.pay.self")));
                case INSUFFICIENT_FUNDS -> payer.sendSystemMessage(
                        EconomyCommandUtil.warning(Component.translatable(
                                "command.futureshops.error.insufficient_funds")));
                case RATE_LIMITED -> payer.sendSystemMessage(
                        EconomyCommandUtil.warning(Component.translatable(
                                "command.futureshops.pay.rate_limited",
                                Math.max(1L, (result.retryAfterMillis()
                                        + 999L) / 1000L))));
                case REENTRANT_REQUEST -> payer.sendSystemMessage(
                        EconomyCommandUtil.warning(Component.translatable(
                                "command.futureshops.pay.in_progress")));
                case REQUEST_CONFLICT -> payer.sendSystemMessage(
                        EconomyCommandUtil.error(Component.translatable(
                                "command.futureshops.pay.conflict",
                                result.requestId().toString())));
                case RECOVERY_REQUIRED -> payer.sendSystemMessage(
                        EconomyCommandUtil.warning(Component.translatable(
                                "command.futureshops.pay.recovery",
                                result.requestId().toString())));
                case CONFIG_CHANGED -> payer.sendSystemMessage(
                        EconomyCommandUtil.warning(Component.translatable(
                                "command.futureshops.pay.config_changed")));
                case CANCELLED -> payer.sendSystemMessage(
                        EconomyCommandUtil.warning(Component.translatable(
                                "command.futureshops.pay.cancelled",
                                result.requestId().toString())));
                case ESCROW_UNAVAILABLE -> payer.sendSystemMessage(
                        EconomyCommandUtil.error(Component.translatable(
                                "command.futureshops.pay.unavailable")));
                case SUCCESS -> throw new IllegalStateException(
                        "Successful payment reached failure response");
            }
            return 0;
        }
        String amountText = EconomyCommandUtil.formatMinorUnits(
                result.amountMinorUnits(), result.currencyDecimals());
        String payerBalanceText = EconomyCommandUtil.formatMinorUnits(
                result.payerBalanceMinorUnits(), result.currencyDecimals());
        payer.sendSystemMessage(EconomyCommandUtil.success(
                Component.translatable(
                        "command.futureshops.pay.success.sender",
                        amountText, result.currencyName(), target.getName(),
                        payerBalanceText)));
        if (result.replayed()) {
            payer.sendSystemMessage(EconomyCommandUtil.info(
                    Component.translatable(
                            "command.futureshops.pay.reference.replayed")));
        }
        String targetBalanceText = EconomyCommandUtil.formatMinorUnits(
                result.recipientBalanceMinorUnits(),
                result.currencyDecimals());
        target.sendSystemMessage(EconomyCommandUtil.success(
                Component.translatable(
                        "command.futureshops.pay.success.target",
                        payer.getName(), amountText, result.currencyName(),
                        targetBalanceText)));
        if (result.overflowClaimMinorUnits() > 0L) {
            String claimText = EconomyCommandUtil.formatMinorUnits(
                    result.overflowClaimMinorUnits(),
                    result.currencyDecimals());
            payer.sendSystemMessage(EconomyCommandUtil.info(
                    Component.translatable(
                            "command.futureshops.pay.overflow.sender",
                            claimText, result.currencyName())));
            target.sendSystemMessage(EconomyCommandUtil.info(
                    Component.translatable(
                            "command.futureshops.pay.overflow.target",
                            claimText, result.currencyName())));
        }
        return 1;
    }
}

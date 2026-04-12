package com.enviouse.futureshops.command;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.ChatFormatting;

import java.math.BigDecimal;
import java.util.Locale;

public final class EconomyCommandUtil {
    private EconomyCommandUtil() {
    }

    public static long parseAmountToMinorUnits(String raw, int decimals) {
        try {
            BigDecimal parsed = new BigDecimal(raw.trim());
            if (parsed.signum() <= 0) {
                throw new IllegalArgumentException("NON_POSITIVE");
            }

            BigDecimal scaled = parsed.movePointRight(decimals);
            if (scaled.stripTrailingZeros().scale() > 0) {
                throw new IllegalArgumentException("TOO_MANY_DECIMALS");
            }

            return scaled.longValueExact();
        } catch (NumberFormatException | ArithmeticException ex) {
            throw new IllegalArgumentException("INVALID_NUMBER", ex);
        }
    }

    public static String formatMinorUnits(long value, int decimals) {
        if (decimals <= 0) {
            return Long.toString(value);
        }

        long absValue = Math.abs(value);
        long scale = (long) Math.pow(10.0D, decimals);
        long whole = absValue / scale;
        long fractional = absValue % scale;
        String sign = value < 0L ? "-" : "";
        return String.format(Locale.ROOT, "%s%d.%0" + decimals + "d", sign, whole, fractional);
    }

    public static void sendProviderError(ServerPlayer player, String errorCode) {
        String key = switch (errorCode) {
            case "INVALID_AMOUNT" -> "command.futureshops.error.invalid_amount";
            case "INSUFFICIENT_FUNDS" -> "command.futureshops.error.insufficient_funds";
            case "MAX_BALANCE_EXCEEDED" -> "command.futureshops.error.max_balance_exceeded";
            case "INVALID_TARGET" -> "command.futureshops.pay.self";
            default -> "command.futureshops.error.server";
        };

        player.sendSystemMessage(error(Component.translatable(key)));
    }

    public static MutableComponent info(Component component) {
        return component.copy().withStyle(ChatFormatting.GRAY);
    }

    public static MutableComponent success(Component component) {
        return component.copy().withStyle(ChatFormatting.GREEN);
    }

    public static MutableComponent warning(Component component) {
        return component.copy().withStyle(ChatFormatting.GOLD);
    }

    public static MutableComponent error(Component component) {
        return component.copy().withStyle(ChatFormatting.RED);
    }
}

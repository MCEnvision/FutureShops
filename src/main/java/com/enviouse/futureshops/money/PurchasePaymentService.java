package com.enviouse.futureshops.money;

import com.enviouse.futureshops.server.economy.BalanceManager;
import com.enviouse.futureshops.server.economy.EconomyProvider;
import com.enviouse.futureshops.server.economy.TransactionResult;
import com.enviouse.futureshops.server.shop.ShopResultCode;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class PurchasePaymentService {
    private PurchasePaymentService() {
    }

    public record Receipt(PaymentSource source, long amountMinor,
                          PhysicalCurrencyAdapter adapter,
                          PhysicalCurrencyAdapter.ExactPayment physicalPayment) {
    }

    public record Result(boolean success, ShopResultCode code, long resultingBalance, Receipt receipt) {
        private static Result failed(ShopResultCode code, long balance) {
            return new Result(false, code, balance, null);
        }
    }

    public static Result charge(ServerPlayer player, long amountMinor, PaymentSource source) {
        EconomyProvider provider = BalanceManager.getProvider();
        long balance = provider.getBalance(player.getUUID());
        if (amountMinor < 0L || source == null) {
            return Result.failed(ShopResultCode.INVALID_REQUEST, balance);
        }
        if (amountMinor == 0L) {
            return new Result(true, ShopResultCode.OK, balance,
                    new Receipt(source, 0L, null, null));
        }
        if (source == PaymentSource.WALLET) {
            TransactionResult withdrawal = provider.withdraw(player.getUUID(), amountMinor, "BUY");
            if (!withdrawal.success()) {
                return Result.failed(withdrawal.errorCode(), withdrawal.resultingBalance());
            }
            return new Result(true, ShopResultCode.OK, withdrawal.resultingBalance(),
                    new Receipt(source, amountMinor, null, null));
        }

        PhysicalCurrencyAdapter adapter = CurrencyManager.get();
        int destroyed = adapter.destroyCounterfeit(player);
        if (destroyed > 0) {
            player.sendSystemMessage(Component.translatable(
                    "command.futureshops.deposit.invalid_destroyed", destroyed));
        }
        PhysicalCurrencyAdapter.ExactPayment payment = adapter.consumeExact(player, amountMinor);
        if (!payment.success() || payment.amountMinor() != amountMinor) {
            return Result.failed(ShopResultCode.INSUFFICIENT_PHYSICAL_FUNDS, balance);
        }
        return new Result(true, ShopResultCode.OK, balance,
                new Receipt(source, amountMinor, adapter, payment));
    }

    public static void refund(ServerPlayer player, Receipt receipt) {
        if (receipt == null || receipt.amountMinor() <= 0L) {
            return;
        }
        if (receipt.source() == PaymentSource.WALLET) {
            BalanceManager.getProvider().deposit(player.getUUID(), receipt.amountMinor(), "BUY");
            return;
        }
        if (receipt.adapter() != null && receipt.physicalPayment() != null) {
            receipt.adapter().refundExact(player, receipt.physicalPayment());
        }
    }
}

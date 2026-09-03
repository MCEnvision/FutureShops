package com.enviouse.futureshopsp.server.economy;

import com.enviouse.futureshopsp.Config;
import com.enviouse.futureshopsp.api.economy.EconomyAmounts;
import com.enviouse.futureshopsp.event.BalanceChangeEvent;
import com.enviouse.futureshopsp.server.shop.ShopResultCode;
import com.enviouse.futureshopsp.server.util.PageBounds;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.common.NeoForge;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class InternalEconomyProvider implements EconomyProvider {
    private final MinecraftServer server;

    public InternalEconomyProvider(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public long getBalance(UUID playerUUID) {
        return getData().getBalanceOrDefault(playerUUID, Config.economyStartingBalanceMinorUnits);
    }

    @Override
    public TransactionResult withdraw(UUID playerUUID, long amountMinorUnits) {
        return withdraw(playerUUID, amountMinorUnits, "WITHDRAW");
    }

    @Override
    public TransactionResult withdraw(UUID playerUUID, long amountMinorUnits, String reason) {
        if (amountMinorUnits <= 0L) {
            return TransactionResult.error(ShopResultCode.INVALID_AMOUNT, getBalance(playerUUID));
        }

        long currentBalance = getBalance(playerUUID);
        // Only admin-issued withdrawals may push a balance below zero (and only when the
        // server has opted into negative balances). Every other reason — BUY, SELL, BARTER,
        // TRANSFER, WITHDRAW, etc. — must leave the balance >= 0, so a player whose admin
        // has put them in debt cannot purchase more and deepen the hole.
        boolean canGoNegative = "ADMIN".equals(reason) && Config.economyAllowNegative;
        if (!canGoNegative && currentBalance < amountMinorUnits) {
            return TransactionResult.error(ShopResultCode.INSUFFICIENT_FUNDS, currentBalance);
        }

        // Fire cancellable BalanceChangeEvent.Pre (spec §33)
        BalanceChangeEvent.Pre preEvent = new BalanceChangeEvent.Pre(playerUUID, -amountMinorUnits, reason, currentBalance);
        NeoForge.EVENT_BUS.post(preEvent);
        if (preEvent.isCanceled()) {
            return TransactionResult.error(ShopResultCode.CANCELLED_BY_EVENT, currentBalance);
        }

        long newBalance;
        try {
            newBalance = EconomyAmounts.subtractExact(currentBalance, amountMinorUnits);
        } catch (ArithmeticException exception) {
            return TransactionResult.error(ShopResultCode.SERVER_ERROR, currentBalance);
        }
        getData().setBalance(playerUUID, newBalance);

        return TransactionResult.ok(newBalance);
    }

    @Override
    public TransactionResult deposit(UUID playerUUID, long amountMinorUnits) {
        return deposit(playerUUID, amountMinorUnits, "DEPOSIT");
    }

    @Override
    public TransactionResult deposit(UUID playerUUID, long amountMinorUnits, String reason) {
        if (amountMinorUnits <= 0L) {
            return TransactionResult.error(ShopResultCode.INVALID_AMOUNT, getBalance(playerUUID));
        }

        long currentBalance = getBalance(playerUUID);
        long newBalance;
        try {
            newBalance = EconomyAmounts.addExact(currentBalance, amountMinorUnits);
        } catch (ArithmeticException exception) {
            return TransactionResult.error(ShopResultCode.MAX_BALANCE_EXCEEDED, currentBalance);
        }
        if (newBalance > Config.economyMaxBalanceMinorUnits) {
            return TransactionResult.error(ShopResultCode.MAX_BALANCE_EXCEEDED, currentBalance);
        }

        // Fire cancellable BalanceChangeEvent.Pre (spec §33)
        BalanceChangeEvent.Pre preEvent = new BalanceChangeEvent.Pre(playerUUID, amountMinorUnits, reason, currentBalance);
        NeoForge.EVENT_BUS.post(preEvent);
        if (preEvent.isCanceled()) {
            return TransactionResult.error(ShopResultCode.CANCELLED_BY_EVENT, currentBalance);
        }

        getData().setBalance(playerUUID, newBalance);

        return TransactionResult.ok(newBalance);
    }

    @Override
    public TransactionResult transfer(UUID fromPlayerUUID, UUID toPlayerUUID, long amountMinorUnits) {
        if (fromPlayerUUID.equals(toPlayerUUID)) {
            return TransactionResult.error(ShopResultCode.INVALID_TARGET, getBalance(fromPlayerUUID));
        }
        return EconomyProvider.super.transfer(fromPlayerUUID, toPlayerUUID, amountMinorUnits);
    }

    @Override
    public List<BalanceEntry> getTopBalances(int page, int pageSize) {
        if (!PageBounds.isValid(page, pageSize)) {
            return List.of();
        }
        int safePage = page;
        int safePageSize = pageSize;
        long skip = PageBounds.offset(safePage, safePageSize);

        Map<UUID, Long> balances = getData().snapshotBalances();
        if (skip >= balances.size()) {
            return List.of();
        }
        return balances.entrySet().stream()
            .sorted(Map.Entry.<UUID, Long>comparingByValue(Comparator.reverseOrder()))
            .skip(skip)
            .limit(safePageSize)
            .map(entry -> new BalanceEntry(entry.getKey(), entry.getValue()))
            .toList();
    }

    @Override
    public String getCurrencyName() {
        return Config.economyCurrencyName;
    }

    @Override
    public int getDecimalPlaces() {
        return Config.economyCurrencyDecimals;
    }

    private InternalBalanceSavedData getData() {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(new SavedData.Factory<>(InternalBalanceSavedData::new, InternalBalanceSavedData::load, null), InternalBalanceSavedData.DATA_NAME);
    }

    boolean persistenceIntegrityValid() {
        return getData().integrityValid();
    }
}

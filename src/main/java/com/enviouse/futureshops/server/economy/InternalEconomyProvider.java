package com.enviouse.futureshops.server.economy;

import com.enviouse.futureshops.Config;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

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
        if (amountMinorUnits <= 0L) {
            return TransactionResult.error("INVALID_AMOUNT", getBalance(playerUUID));
        }

        long currentBalance = getBalance(playerUUID);
        if (!Config.economyAllowNegative && currentBalance < amountMinorUnits) {
            return TransactionResult.error("INSUFFICIENT_FUNDS", currentBalance);
        }

        long newBalance = currentBalance - amountMinorUnits;
        getData().setBalance(playerUUID, newBalance);
        return TransactionResult.ok(newBalance);
    }

    @Override
    public TransactionResult deposit(UUID playerUUID, long amountMinorUnits) {
        if (amountMinorUnits <= 0L) {
            return TransactionResult.error("INVALID_AMOUNT", getBalance(playerUUID));
        }

        long currentBalance = getBalance(playerUUID);
        long newBalance = currentBalance + amountMinorUnits;
        if (newBalance > Config.economyMaxBalanceMinorUnits) {
            return TransactionResult.error("MAX_BALANCE_EXCEEDED", currentBalance);
        }

        getData().setBalance(playerUUID, newBalance);
        return TransactionResult.ok(newBalance);
    }

    @Override
    public TransactionResult transfer(UUID fromPlayerUUID, UUID toPlayerUUID, long amountMinorUnits) {
        if (fromPlayerUUID.equals(toPlayerUUID)) {
            return TransactionResult.error("INVALID_TARGET", getBalance(fromPlayerUUID));
        }
        return EconomyProvider.super.transfer(fromPlayerUUID, toPlayerUUID, amountMinorUnits);
    }

    @Override
    public List<BalanceEntry> getTopBalances(int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        int skip = (safePage - 1) * safePageSize;

        Map<UUID, Long> balances = getData().snapshotBalances();
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
        return overworld.getDataStorage().computeIfAbsent(InternalBalanceSavedData::load, InternalBalanceSavedData::new, InternalBalanceSavedData.DATA_NAME);
    }
}

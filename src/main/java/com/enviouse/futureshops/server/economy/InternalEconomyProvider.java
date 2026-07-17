package com.enviouse.futureshops.server.economy;

import com.enviouse.futureshops.Config;
import com.enviouse.futureshops.event.BalanceChangeEvent;
import com.enviouse.futureshops.server.economy.migration.LegacyBalanceMigrationManager;
import com.enviouse.futureshops.server.economy.migration.WalletInitializationIds;
import com.enviouse.futureshops.server.escrow.runtime.EscrowRuntimeException;
import com.enviouse.futureshops.server.escrow.runtime.EscrowWalletService;
import com.enviouse.futureshops.server.escrow.runtime.WalletMutationResult;
import com.enviouse.futureshops.server.escrow.runtime.WalletMutationStatus;
import com.enviouse.futureshops.server.shop.ShopResultCode;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import org.slf4j.Logger;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public class InternalEconomyProvider implements EconomyProvider {
    private static final Logger LOGGER = LogUtils.getLogger();
    public InternalEconomyProvider(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
    }

    @Override
    public long getBalance(UUID playerUUID) {
        Objects.requireNonNull(playerUUID, "playerUUID");
        LegacyBalanceMigrationManager.requireComplete();
        return initializedBalance(EscrowWalletService.live(), playerUUID);
    }

    @Override
    public TransactionResult withdraw(UUID playerUUID, long amountMinorUnits) {
        return withdraw(UUID.randomUUID(), playerUUID, amountMinorUnits, "WITHDRAW");
    }

    @Override
    public TransactionResult withdraw(UUID playerUUID, long amountMinorUnits,
                                      String reason) {
        return withdraw(UUID.randomUUID(), playerUUID, amountMinorUnits, reason);
    }

    public TransactionResult withdraw(UUID requestId, UUID playerUUID,
                                      long amountMinorUnits, String reason) {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(playerUUID, "playerUUID");
        return guarded(List.of(playerUUID), playerUUID, () -> {
            EscrowWalletService wallet = EscrowWalletService.live();
            LegacyBalanceMigrationManager.requireComplete();
            boolean allowNegative = "ADMIN".equals(reason)
                    && Config.economyAllowNegative;
            if (wallet.wasRequestApplied(requestId)) {
                return map(wallet.debit(
                        requestId, playerUUID, amountMinorUnits,
                        allowNegative, reason));
            }
            long before = initializedBalance(wallet, playerUUID);
            if (amountMinorUnits <= 0L) {
                return TransactionResult.error(ShopResultCode.INVALID_AMOUNT, before);
            }
            long delta = Math.negateExact(amountMinorUnits);
            BalanceChangeEvent.Pre pre = new BalanceChangeEvent.Pre(
                    playerUUID, delta, reason, before);
            if (postPre(pre)) {
                return TransactionResult.error(
                        ShopResultCode.CANCELLED_BY_EVENT, before);
            }
            WalletMutationResult result = wallet.debit(
                    requestId, playerUUID, amountMinorUnits, allowNegative, reason);
            TransactionResult mapped = map(result);
            if (result.status() == WalletMutationStatus.APPLIED) {
                postAfter(new BalanceChangeEvent.Post(
                        playerUUID, delta, reason, result.primaryBalance()));
            }
            return mapped;
        });
    }

    @Override
    public TransactionResult deposit(UUID playerUUID, long amountMinorUnits) {
        return deposit(UUID.randomUUID(), playerUUID, amountMinorUnits, "DEPOSIT");
    }

    @Override
    public TransactionResult deposit(UUID playerUUID, long amountMinorUnits,
                                     String reason) {
        return deposit(UUID.randomUUID(), playerUUID, amountMinorUnits, reason);
    }

    public TransactionResult deposit(UUID requestId, UUID playerUUID,
                                     long amountMinorUnits, String reason) {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(playerUUID, "playerUUID");
        return guarded(List.of(playerUUID), playerUUID, () -> {
            EscrowWalletService wallet = EscrowWalletService.live();
            LegacyBalanceMigrationManager.requireComplete();
            if (wallet.wasRequestApplied(requestId)) {
                return map(wallet.credit(
                        requestId, playerUUID, amountMinorUnits,
                        Config.economyMaxBalanceMinorUnits, reason));
            }
            long before = initializedBalance(wallet, playerUUID);
            if (amountMinorUnits <= 0L) {
                return TransactionResult.error(ShopResultCode.INVALID_AMOUNT, before);
            }
            try {
                long after = Math.addExact(before, amountMinorUnits);
                if (after > Config.economyMaxBalanceMinorUnits) {
                    return TransactionResult.error(
                            ShopResultCode.MAX_BALANCE_EXCEEDED, before);
                }
            } catch (ArithmeticException exception) {
                return TransactionResult.error(ShopResultCode.SERVER_ERROR, before);
            }
            BalanceChangeEvent.Pre pre = new BalanceChangeEvent.Pre(
                    playerUUID, amountMinorUnits, reason, before);
            if (postPre(pre)) {
                return TransactionResult.error(
                        ShopResultCode.CANCELLED_BY_EVENT, before);
            }
            WalletMutationResult result = wallet.credit(
                    requestId, playerUUID, amountMinorUnits,
                    Config.economyMaxBalanceMinorUnits, reason);
            TransactionResult mapped = map(result);
            if (result.status() == WalletMutationStatus.APPLIED) {
                postAfter(new BalanceChangeEvent.Post(
                        playerUUID, amountMinorUnits, reason,
                        result.primaryBalance()));
            }
            return mapped;
        });
    }

    @Override
    public TransactionResult transfer(UUID fromPlayerUUID, UUID toPlayerUUID,
                                      long amountMinorUnits) {
        return transfer(UUID.randomUUID(), fromPlayerUUID, toPlayerUUID,
                amountMinorUnits, "TRANSFER");
    }

    public TransactionResult transfer(UUID requestId, UUID fromPlayerUUID,
                                      UUID toPlayerUUID, long amountMinorUnits,
                                      String reason) {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(fromPlayerUUID, "fromPlayerUUID");
        Objects.requireNonNull(toPlayerUUID, "toPlayerUUID");
        return guarded(List.of(fromPlayerUUID, toPlayerUUID), fromPlayerUUID, () -> {
            EscrowWalletService wallet = EscrowWalletService.live();
            LegacyBalanceMigrationManager.requireComplete();
            if (wallet.wasRequestApplied(requestId)) {
                return map(wallet.transfer(
                        requestId, fromPlayerUUID, toPlayerUUID,
                        amountMinorUnits, Config.economyMaxBalanceMinorUnits,
                        reason));
            }
            long fromBefore = initializedBalance(wallet, fromPlayerUUID);
            long toBefore = initializedBalance(wallet, toPlayerUUID);
            if (fromPlayerUUID.equals(toPlayerUUID)) {
                return TransactionResult.error(
                        ShopResultCode.INVALID_TARGET, fromBefore);
            }
            if (amountMinorUnits <= 0L) {
                return TransactionResult.error(
                        ShopResultCode.INVALID_AMOUNT, fromBefore);
            }
            if (fromBefore < amountMinorUnits) {
                return TransactionResult.error(
                        ShopResultCode.INSUFFICIENT_FUNDS, fromBefore);
            }
            try {
                long recipientAfter = Math.addExact(toBefore, amountMinorUnits);
                if (recipientAfter > Config.economyMaxBalanceMinorUnits) {
                    return TransactionResult.error(
                            ShopResultCode.MAX_BALANCE_EXCEEDED, fromBefore);
                }
            } catch (ArithmeticException exception) {
                return TransactionResult.error(
                        ShopResultCode.SERVER_ERROR, fromBefore);
            }
            long senderDelta = Math.negateExact(amountMinorUnits);
            BalanceChangeEvent.Pre senderPre = new BalanceChangeEvent.Pre(
                    fromPlayerUUID, senderDelta, reason, fromBefore);
            BalanceChangeEvent.Pre recipientPre = new BalanceChangeEvent.Pre(
                    toPlayerUUID, amountMinorUnits, reason, toBefore);
            boolean cancelled = postPre(senderPre);
            cancelled = postPre(recipientPre) || cancelled;
            if (cancelled) {
                return TransactionResult.error(
                        ShopResultCode.CANCELLED_BY_EVENT, fromBefore);
            }
            WalletMutationResult result = wallet.transfer(
                    requestId, fromPlayerUUID, toPlayerUUID,
                    amountMinorUnits, Config.economyMaxBalanceMinorUnits, reason);
            TransactionResult mapped = map(result);
            if (result.status() == WalletMutationStatus.APPLIED) {
                postAfter(new BalanceChangeEvent.Post(
                        fromPlayerUUID, senderDelta, reason,
                        result.primaryBalance()));
                postAfter(new BalanceChangeEvent.Post(
                        toPlayerUUID, amountMinorUnits, reason,
                        result.secondaryBalance().orElseThrow()));
            }
            return mapped;
        });
    }

    public TransactionResult setBalance(UUID requestId, UUID playerUUID,
                                        long targetBalance,
                                        boolean allowNegative,
                                        String reason) {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(playerUUID, "playerUUID");
        return guarded(List.of(playerUUID), playerUUID, () -> {
            EscrowWalletService wallet = EscrowWalletService.live();
            LegacyBalanceMigrationManager.requireComplete();
            if (wallet.wasRequestApplied(requestId)) {
                return map(wallet.setBalance(
                        requestId, playerUUID, targetBalance,
                        allowNegative, reason));
            }
            long before = initializedBalance(wallet, playerUUID);
            long delta;
            try {
                delta = Math.subtractExact(targetBalance, before);
            } catch (ArithmeticException exception) {
                return TransactionResult.error(
                        ShopResultCode.SERVER_ERROR, before);
            }
            WalletMutationResult result = wallet.setBalance(
                    requestId, playerUUID, targetBalance,
                    allowNegative, reason);
            TransactionResult mapped = map(result);
            if (result.status() == WalletMutationStatus.APPLIED) {
                postAfter(new BalanceChangeEvent.Post(
                        playerUUID, delta, reason, result.primaryBalance()));
            }
            return mapped;
        });
    }

    @Override
    public List<BalanceEntry> getTopBalances(int page, int pageSize) {
        LegacyBalanceMigrationManager.requireComplete();
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        long skip = (long) (safePage - 1) * safePageSize;
        Map<UUID, Long> balances = EscrowWalletService.live().snapshotBalances();
        return balances.entrySet().stream()
                .sorted(Map.Entry.<UUID, Long>comparingByValue(
                                Comparator.reverseOrder())
                        .thenComparing(entry -> entry.getKey().toString()))
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

    private static long initializedBalance(EscrowWalletService wallet,
                                           UUID playerUUID) {
        LegacyBalanceMigrationManager.requireComplete();
        if (wallet.isInitialized(playerUUID)) {
            return wallet.balance(playerUUID);
        }
        WalletMutationResult result = wallet.initialize(
                WalletInitializationIds.startingGrant(playerUUID),
                playerUUID,
                Config.economyStartingBalanceMinorUnits,
                false,
                "STARTING_GRANT");
        if (result.status() == WalletMutationStatus.APPLIED
                || result.status() == WalletMutationStatus.REPLAYED
                || result.status() == WalletMutationStatus.ALREADY_INITIALIZED) {
            return result.primaryBalance();
        }
        throw new EscrowRuntimeException(
                "Wallet starting balance initialization failed with status "
                        + result.status());
    }

    private static TransactionResult map(WalletMutationResult result) {
        if (result.success()) {
            return TransactionResult.ok(result.primaryBalance());
        }
        ShopResultCode code = switch (result.status()) {
            case INVALID_AMOUNT, NEGATIVE_NOT_ALLOWED ->
                    ShopResultCode.INVALID_AMOUNT;
            case INVALID_TARGET -> ShopResultCode.INVALID_TARGET;
            case INSUFFICIENT_FUNDS -> ShopResultCode.INSUFFICIENT_FUNDS;
            case MAX_BALANCE_EXCEEDED ->
                    ShopResultCode.MAX_BALANCE_EXCEEDED;
            case APPLIED, REPLAYED -> ShopResultCode.OK;
            case ALREADY_INITIALIZED, ARITHMETIC_OVERFLOW, CONFLICT ->
                    ShopResultCode.SERVER_ERROR;
        };
        return TransactionResult.error(code, result.primaryBalance());
    }

    private static boolean postPre(BalanceChangeEvent.Pre event) {
        try {
            return MinecraftForge.EVENT_BUS.post(event);
        } catch (RuntimeException exception) {
            LOGGER.error("FutureShops balance pre event failed.", exception);
            return true;
        }
    }

    private static void postAfter(BalanceChangeEvent.Post event) {
        try {
            MinecraftForge.EVENT_BUS.post(event);
        } catch (RuntimeException exception) {
            LOGGER.error("FutureShops balance post event failed.", exception);
        }
    }

    private static TransactionResult guarded(List<UUID> playerIds,
                                             UUID primaryPlayer,
                                             Supplier<TransactionResult> operation) {
        java.util.Optional<WalletMutationGuard.Lease> optionalLease =
                WalletMutationGuard.tryAcquire(playerIds);
        if (optionalLease.isEmpty()) {
            return TransactionResult.error(
                    ShopResultCode.SERVER_ERROR,
                    currentBalanceWithoutInitialization(primaryPlayer));
        }
        try (WalletMutationGuard.Lease ignored =
                     optionalLease.orElseThrow()) {
            return operation.get();
        } catch (RuntimeException exception) {
            LOGGER.error("FutureShops wallet operation failed.", exception);
            return TransactionResult.error(
                    ShopResultCode.SERVER_ERROR,
                    currentBalanceWithoutInitialization(primaryPlayer));
        }
    }

    private static long currentBalanceWithoutInitialization(UUID playerUUID) {
        try {
            LegacyBalanceMigrationManager.requireComplete();
            EscrowWalletService wallet = EscrowWalletService.live();
            return wallet.isInitialized(playerUUID) ? wallet.balance(playerUUID) : 0L;
        } catch (RuntimeException exception) {
            return 0L;
        }
    }
}

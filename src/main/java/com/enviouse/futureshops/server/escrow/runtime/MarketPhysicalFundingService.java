package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.money.AtmCurrencyCatalog;
import com.enviouse.futureshops.money.CurrencyManager;
import com.enviouse.futureshops.server.economy.BalanceManager;
import com.enviouse.futureshops.server.escrow.model.CashDepositMode;
import net.minecraft.server.level.ServerPlayer;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.regex.Pattern;

public final class MarketPhysicalFundingService {
    private static final Pattern DOMAIN = Pattern.compile("[a-z0-9_.]{1,32}");

    private MarketPhysicalFundingService() {
    }

    public static FundingResult fund(ServerPlayer player, UUID actionRequestId,
                                     String domain, long amountMinor) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(actionRequestId, "actionRequestId");
        String normalizedDomain = requireDomain(domain);
        if (amountMinor <= 0L) {
            return FundingResult.failed(Status.INVALID_AMOUNT,
                    fundingRequestId(normalizedDomain, actionRequestId));
        }
        UUID depositRequestId = fundingRequestId(normalizedDomain, actionRequestId);
        String signature;
        try (CurrencyManager.ConfigurationReadLease ignored =
                     CurrencyManager.acquireConfigurationReadLease()) {
            signature = AtmCurrencyCatalog.capture(CurrencyManager.get(),
                    BalanceManager.getProvider()).signature();
        } catch (RuntimeException exception) {
            return FundingResult.failed(Status.UNAVAILABLE, depositRequestId);
        }
        EscrowCashDepositService.DepositRequest request =
                new EscrowCashDepositService.DepositRequest(depositRequestId,
                        signature, EscrowCashDepositService.Source.INVENTORY,
                        OptionalLong.of(amountMinor), CashDepositMode.PUBLIC_WALLET,
                        Optional.empty());
        EscrowCashDepositService.DepositResult deposit =
                EscrowCashDepositService.deposit(player, request);
        return fromDeposit(signature, amountMinor, deposit);
    }

    static UUID fundingRequestId(String domain, UUID actionRequestId) {
        String material = "futureshops market physical funding v1\u0000"
                + requireDomain(domain) + "\u0000" + actionRequestId;
        return UUID.nameUUIDFromBytes(material.getBytes(StandardCharsets.UTF_8));
    }

    static FundingResult fromDeposit(String currencySignature, long requestedMinor,
                                     EscrowCashDepositService.DepositResult deposit) {
        Objects.requireNonNull(currencySignature, "currencySignature");
        Objects.requireNonNull(deposit, "deposit");
        if (!deposit.successful()) {
            return FundingResult.failed(map(deposit.status()), deposit.requestId());
        }
        if (deposit.depositedMinorUnits() != requestedMinor
                || deposit.walletCreditMinorUnits() < requestedMinor) {
            return new FundingResult(Status.WALLET_CAPACITY, deposit.requestId(),
                    deposit.transactionId(), currencySignature,
                    deposit.depositedMinorUnits(), deposit.walletCreditMinorUnits(),
                    deposit.overflowClaimMinorUnits(),
                    deposit.resultingBalanceMinorUnits(), deposit.replayed());
        }
        if (deposit.resultingBalanceMinorUnits() < requestedMinor) {
            return new FundingResult(Status.WALLET_DEBT, deposit.requestId(),
                    deposit.transactionId(), currencySignature,
                    deposit.depositedMinorUnits(),
                    deposit.walletCreditMinorUnits(),
                    deposit.overflowClaimMinorUnits(),
                    deposit.resultingBalanceMinorUnits(),
                    deposit.replayed());
        }
        return new FundingResult(Status.FUNDED, deposit.requestId(),
                deposit.transactionId(), currencySignature,
                deposit.depositedMinorUnits(), deposit.walletCreditMinorUnits(),
                deposit.overflowClaimMinorUnits(), deposit.resultingBalanceMinorUnits(),
                deposit.replayed());
    }

    private static Status map(EscrowCashDepositService.Status status) {
        return switch (status) {
            case NO_CURRENCY, NOT_ENOUGH_CURRENCY -> Status.INSUFFICIENT_CASH;
            case INVALID_AMOUNT -> Status.INVALID_AMOUNT;
            case INVALID_DENOMINATION, TOO_MANY_ITEMS, WRONG_PROVIDER,
                    CREATIVE_BLOCKED, LEGACY_MIGRATION_REQUIRED,
                    INVALID_CURRENCY -> Status.INVALID_CASH;
            case REQUEST_CONFLICT -> Status.REQUEST_CONFLICT;
            case CANCELLED, RECOVERY_REQUIRED -> Status.RECOVERY_REQUIRED;
            case CONFIG_CHANGED -> Status.CONFIG_CHANGED;
            case RATE_LIMITED -> Status.RATE_LIMITED;
            case ESCROW_UNAVAILABLE -> Status.UNAVAILABLE;
            case SUCCESS -> Status.FUNDED;
        };
    }

    private static String requireDomain(String value) {
        String result = Objects.requireNonNull(value, "domain").strip();
        if (!DOMAIN.matcher(result).matches()) {
            throw new IllegalArgumentException("Market funding domain is invalid");
        }
        return result;
    }

    public enum Status {
        FUNDED,
        INSUFFICIENT_CASH,
        INVALID_AMOUNT,
        INVALID_CASH,
        WALLET_CAPACITY,
        WALLET_DEBT,
        REQUEST_CONFLICT,
        CONFIG_CHANGED,
        RATE_LIMITED,
        RECOVERY_REQUIRED,
        UNAVAILABLE
    }

    public record FundingResult(Status status, UUID depositRequestId,
                                Optional<UUID> depositTransactionId,
                                String currencySignature, long depositedMinor,
                                long walletCreditMinor, long overflowClaimMinor,
                                long resultingWalletMinor, boolean replayed) {
        public FundingResult {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(depositRequestId, "depositRequestId");
            depositTransactionId = Objects.requireNonNull(depositTransactionId,
                    "depositTransactionId");
            currencySignature = Objects.requireNonNull(currencySignature,
                    "currencySignature");
            if (status == Status.FUNDED && depositTransactionId.isEmpty()) {
                throw new IllegalArgumentException("Funded result needs a transaction");
            }
        }

        static FundingResult failed(Status status, UUID requestId) {
            if (status == Status.FUNDED) {
                throw new IllegalArgumentException("Failure status is invalid");
            }
            return new FundingResult(status, requestId, Optional.empty(), "",
                    0L, 0L, 0L, 0L, false);
        }

        public boolean funded() {
            return status == Status.FUNDED;
        }
    }
}

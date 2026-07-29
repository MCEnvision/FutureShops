package com.enviouse.futureshops.server.transaction;

import com.enviouse.futureshops.money.AtmCurrencyCatalog;
import com.enviouse.futureshops.money.CurrencyManager;
import com.enviouse.futureshops.money.PhysicalCurrencyAdapter;
import com.enviouse.futureshops.server.economy.BalanceManager;
import com.enviouse.futureshops.server.escrow.runtime.EscrowCashDepositService;
import com.enviouse.futureshops.server.escrow.runtime.ServerShopPurchaseCommit;
import com.enviouse.futureshops.server.escrow.runtime.ServerShopFundingRelease;
import com.enviouse.futureshops.server.escrow.runtime.ServerShopFundingReleaseService;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.model.CashDepositMode;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

final class ServerShopPhysicalFundingBridge {
    private ServerShopPhysicalFundingBridge() {
    }

    static Result fund(
            ServerPlayer player,
            UUID purchaseRequestId,
            long amountMinorUnits
    ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(purchaseRequestId, "purchaseRequestId");
        if (amountMinorUnits <= 0L) {
            return Result.failure(Status.INVALID);
        }
        try {
            String currencySignature;
            try (CurrencyManager.ConfigurationReadLease ignored =
                         CurrencyManager.acquireConfigurationReadLease()) {
                PhysicalCurrencyAdapter adapter = CurrencyManager.get();
                currencySignature = AtmCurrencyCatalog.capture(
                        adapter, BalanceManager.getProvider()).signature();
            }
            EscrowCashDepositService.DepositRequest request =
                    new EscrowCashDepositService.DepositRequest(
                            fundingRequestId(purchaseRequestId),
                            currencySignature,
                            EscrowCashDepositService.Source.INVENTORY,
                            OptionalLong.of(amountMinorUnits),
                            CashDepositMode.INTERNAL_ESCROW,
                            Optional.of(purchaseRequestId));
            EscrowCashDepositService.DepositResult result =
                    EscrowCashDepositService.depositForEscrow(player, request);
            if (result.successful()) {
                if (result.depositedMinorUnits() != amountMinorUnits
                        || result.walletCreditMinorUnits() != 0L
                        || result.overflowClaimMinorUnits()
                        != amountMinorUnits) {
                    return Result.failure(Status.RECOVERY_REQUIRED);
                }
                UUID transactionId = result.transactionId().orElseThrow();
                EscrowClaim claim = EscrowCashDepositService
                        .requireInternalEscrowFundingClaimEvidence(
                                player, result);
                if (claim.status()
                        == com.enviouse.futureshops.server.escrow.claim
                        .ClaimStatus.COMPLETED) {
                    return ServerShopFundingReleaseService.resolve(
                            player.getUUID(),
                            new ServerShopPurchaseCommit.PhysicalFunding(
                                    purchaseRequestId, transactionId,
                                    claim.claimId(), amountMinorUnits))
                            .filter(value -> value.status()
                                    == ServerShopFundingReleaseService.Status
                                    .RELEASED)
                            .map(value -> Result.failure(Status.RELEASED))
                            .orElseGet(() -> Result.failure(
                                    Status.RECOVERY_REQUIRED));
                }
                return Result.funded(result.replayed(),
                        new ServerShopPurchaseCommit.PhysicalFunding(
                                purchaseRequestId, transactionId,
                                claim.claimId(),
                                amountMinorUnits));
            }
            return Result.failure(switch (result.status()) {
                case NO_CURRENCY, NOT_ENOUGH_CURRENCY,
                        INVALID_DENOMINATION -> Status.INSUFFICIENT;
                case RATE_LIMITED -> Status.BUSY;
                case REQUEST_CONFLICT, CONFIG_CHANGED,
                        RECOVERY_REQUIRED -> Status.RECOVERY_REQUIRED;
                default -> Status.UNAVAILABLE;
            });
        } catch (RuntimeException exception) {
            return Result.failure(Status.UNAVAILABLE);
        }
    }

    static UUID fundingRequestId(UUID purchaseRequestId) {
        return ServerShopFundingRelease.fundingRequestId(
                purchaseRequestId);
    }

    enum Status {
        FUNDED,
        INSUFFICIENT,
        BUSY,
        INVALID,
        UNAVAILABLE,
        RECOVERY_REQUIRED,
        RELEASED
    }

    record Result(
            Status status,
            boolean replayed,
            Optional<ServerShopPurchaseCommit.PhysicalFunding> funding
    ) {
        Result {
            Objects.requireNonNull(status, "status");
            funding = Objects.requireNonNull(funding, "funding");
            if (replayed && status != Status.FUNDED
                    || (status == Status.FUNDED) != funding.isPresent()) {
                throw new IllegalArgumentException(
                        "Physical funding replay state is invalid");
            }
        }

        static Result funded(
                boolean replayed,
                ServerShopPurchaseCommit.PhysicalFunding funding
        ) {
            return new Result(Status.FUNDED, replayed,
                    Optional.of(Objects.requireNonNull(funding, "funding")));
        }

        static Result failure(Status status) {
            return new Result(status, false, Optional.empty());
        }

        boolean successful() {
            return status == Status.FUNDED;
        }
    }
}

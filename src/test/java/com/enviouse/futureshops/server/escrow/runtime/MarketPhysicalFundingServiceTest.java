package com.enviouse.futureshops.server.escrow.runtime;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketPhysicalFundingServiceTest {
    private static final UUID ACTION = UUID.fromString(
            "12345678-1234-5678-1234-567812345678");
    private static final UUID TRANSACTION = UUID.fromString(
            "22345678-1234-5678-1234-567812345678");
    private static final String SIGNATURE = "0123456789abcdef".repeat(4);

    @Test
    void fundingIdentityIsStableAndDomainSeparated() {
        UUID first = MarketPhysicalFundingService.fundingRequestId(
                "auction.bid", ACTION);
        assertEquals(first, MarketPhysicalFundingService.fundingRequestId(
                "auction.bid", ACTION));
        assertNotEquals(first, MarketPhysicalFundingService.fundingRequestId(
                "bazaar", ACTION));
    }

    @Test
    void exactSuccessfulDepositBecomesFundingEvidence() {
        EscrowCashDepositService.DepositResult deposit = result(
                EscrowCashDepositService.Status.SUCCESS, 500L, 500L, 0L);
        MarketPhysicalFundingService.FundingResult funding =
                MarketPhysicalFundingService.fromDeposit(
                        SIGNATURE, 500L, deposit);
        assertTrue(funding.funded());
        assertEquals(TRANSACTION,
                funding.depositTransactionId().orElseThrow());
        assertEquals(500L, funding.walletCreditMinor());
    }

    @Test
    void walletOverflowFailsSafelyWithDepositEvidence() {
        EscrowCashDepositService.DepositResult deposit = result(
                EscrowCashDepositService.Status.SUCCESS, 500L, 300L, 200L);
        MarketPhysicalFundingService.FundingResult funding =
                MarketPhysicalFundingService.fromDeposit(
                        SIGNATURE, 500L, deposit);
        assertFalse(funding.funded());
        assertEquals(MarketPhysicalFundingService.Status.WALLET_CAPACITY,
                funding.status());
        assertEquals(TRANSACTION,
                funding.depositTransactionId().orElseThrow());
        assertEquals(200L, funding.overflowClaimMinor());
    }

    @Test
    void cashFirstRepaysDebtAndDoesNotPretendToFundTheMarket() {
        EscrowCashDepositService.DepositResult deposit = result(
                EscrowCashDepositService.Status.SUCCESS, 500L, 500L,
                0L, 300L);
        MarketPhysicalFundingService.FundingResult funding =
                MarketPhysicalFundingService.fromDeposit(
                        SIGNATURE, 500L, deposit);
        assertFalse(funding.funded());
        assertEquals(MarketPhysicalFundingService.Status.WALLET_DEBT,
                funding.status());
        assertEquals(TRANSACTION,
                funding.depositTransactionId().orElseThrow());
    }

    @Test
    void cashFailuresMapWithoutInventingValue() {
        EscrowCashDepositService.DepositResult deposit = result(
                EscrowCashDepositService.Status.NOT_ENOUGH_CURRENCY,
                0L, 0L, 0L);
        MarketPhysicalFundingService.FundingResult funding =
                MarketPhysicalFundingService.fromDeposit(
                        SIGNATURE, 500L, deposit);
        assertEquals(MarketPhysicalFundingService.Status.INSUFFICIENT_CASH,
                funding.status());
        assertTrue(funding.depositTransactionId().isEmpty());
        assertEquals(0L, funding.depositedMinor());
    }

    private static EscrowCashDepositService.DepositResult result(
            EscrowCashDepositService.Status status, long deposited,
            long walletCredit, long overflow) {
        return result(status, deposited, walletCredit, overflow,
                walletCredit);
    }

    private static EscrowCashDepositService.DepositResult result(
            EscrowCashDepositService.Status status, long deposited,
            long walletCredit, long overflow, long resultingBalance) {
        boolean success = status == EscrowCashDepositService.Status.SUCCESS;
        return new EscrowCashDepositService.DepositResult(status,
                ACTION, success ? Optional.of(TRANSACTION) : Optional.empty(),
                deposited, success ? 1 : 0, walletCredit, overflow,
                success ? resultingBalance : 0L, false, false,
                Optional.empty(), 0L);
    }
}

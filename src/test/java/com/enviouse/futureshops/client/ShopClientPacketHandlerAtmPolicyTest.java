package com.enviouse.futureshops.client;

import com.enviouse.futureshops.network.packets.C2SAtmWithdrawPacket;
import com.enviouse.futureshops.network.packets.C2SAtmCollectCashPacket;
import com.enviouse.futureshops.network.packets.C2SAtmDepositPacket;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopClientPacketHandlerAtmPolicyTest {
    private static final UUID REQUEST_ID = UUID.fromString(
            "40000000-0000-0000-0000-000000000001");
    private static final String SIGNATURE = "a".repeat(64);

    @Test
    void canonicalPacketKeepsTheTrackedIdentityAndPayload() {
        AtmWithdrawalTracker tracker = new AtmWithdrawalTracker(
                () -> REQUEST_ID, () -> 5L, 10L, 4);
        AtmWithdrawalTracker.PendingRequest request = tracker.begin(
                SIGNATURE, List.of(1, 0, 3), 325L);

        C2SAtmWithdrawPacket packet =
                ShopClientPacketHandler.atmPacket(request);

        assertEquals(REQUEST_ID, packet.requestId());
        assertEquals(SIGNATURE, packet.currencySignature());
        assertEquals(List.of(1, 0, 3), packet.denominationCounts());
    }

    @Test
    void onlyAcceptedKnownResultsMayChangeTheGlobalBalance() {
        assertTrue(ShopClientPacketHandler.shouldApplyAtmBalance(
                AtmWithdrawalTracker.ResultDecision.ACCEPT_RETRYABLE,
                true));
        assertTrue(ShopClientPacketHandler.shouldApplyAtmBalance(
                AtmWithdrawalTracker.ResultDecision.ACCEPT_TERMINAL,
                true));
        assertFalse(ShopClientPacketHandler.shouldApplyAtmBalance(
                AtmWithdrawalTracker.ResultDecision.ACCEPT_TERMINAL,
                false));
        assertFalse(ShopClientPacketHandler.shouldApplyAtmBalance(
                AtmWithdrawalTracker.ResultDecision.DUPLICATE,
                true));
        assertFalse(ShopClientPacketHandler.shouldApplyAtmBalance(
                AtmWithdrawalTracker.ResultDecision.MISMATCHED,
                true));
        assertFalse(ShopClientPacketHandler.shouldApplyAtmBalance(
                AtmWithdrawalTracker.ResultDecision.UNTRACKED,
                true));
    }

    @Test
    void cashCollectionPacketKeepsTrackedRequestAndExactClaims() {
        UUID claimOne = UUID.fromString(
                "40000000-0000-0000-0000-000000000011");
        UUID claimTwo = UUID.fromString(
                "40000000-0000-0000-0000-000000000012");
        AtmCashClaimCollectionTracker tracker =
                new AtmCashClaimCollectionTracker(
                        () -> 5L, 10L, 4);
        AtmCashClaimCollectionTracker.PendingRequest request =
                tracker.begin(REQUEST_ID, List.of(claimOne, claimTwo));

        C2SAtmCollectCashPacket packet =
                ShopClientPacketHandler.atmCashCollectionPacket(request);

        assertEquals(C2SAtmCollectCashPacket.deriveRequestId(
                REQUEST_ID, List.of(claimOne, claimTwo)),
                packet.requestId());
        assertEquals(List.of(claimOne, claimTwo), packet.claimIds());
    }

    @Test
    void depositPacketKeepsTrackedRequestSourceAndExactAmount() {
        AtmDepositTracker tracker = new AtmDepositTracker(
                () -> REQUEST_ID, () -> 5L, 10L, 4);
        AtmDepositTracker.PendingRequest request = tracker.begin(
                SIGNATURE,
                C2SAtmDepositPacket.Source.OFF_HAND,
                OptionalLong.of(725L));

        C2SAtmDepositPacket packet =
                ShopClientPacketHandler.atmDepositPacket(request);

        assertEquals(REQUEST_ID, packet.requestId());
        assertEquals(SIGNATURE, packet.currencySignature());
        assertEquals(C2SAtmDepositPacket.Source.OFF_HAND,
                packet.source());
        assertEquals(OptionalLong.of(725L),
                packet.requestedMinorUnits());
    }
}

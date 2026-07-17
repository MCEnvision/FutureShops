package com.enviouse.futureshops.client.screen;

import com.enviouse.futureshops.data.AtmDenominationData;
import com.enviouse.futureshops.network.packets.S2CAtmCollectCashResultPacket;
import com.enviouse.futureshops.network.packets.S2CAtmDataPacket;
import com.enviouse.futureshops.network.packets.C2SAtmDepositPacket;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AtmScreenMessagePolicyTest {
    @Test
    void knownAvailabilityCodesReceiveSpecificExplanations() {
        assertEquals(
                "gui.futureshops.atm.availability.migration_failed",
                AtmScreen.availabilityKey("MIGRATION_FAILED"));
        assertEquals(
                "gui.futureshops.atm.availability.recovery_pending",
                AtmScreen.availabilityKey("RECOVERY_PENDING"));
        assertEquals(
                "gui.futureshops.atm.availability.escrow_maintenance",
                AtmScreen.availabilityKey("ESCROW_MAINTENANCE"));
    }

    @Test
    void unknownAvailabilityCodesUseTheSafeFallback() {
        assertEquals(
                "gui.futureshops.atm.availability.unavailable",
                AtmScreen.availabilityKey("FUTURE_STATE"));
    }

    @Test
    void deliveryAndClaimStatusesRemainDistinct() {
        assertEquals("gui.futureshops.atm.result.delivered",
                AtmScreen.resultKey("DELIVERED"));
        assertEquals("gui.futureshops.atm.result.claimed",
                AtmScreen.resultKey("CLAIMED"));
        assertEquals("gui.futureshops.atm.result.recovery_pending",
                AtmScreen.resultKey("RECOVERY_PENDING"));
        assertEquals("gui.futureshops.atm.result.manual_review",
                AtmScreen.resultKey("MANUAL_REVIEW"));
        assertEquals("gui.futureshops.atm.result.rate_limited",
                AtmScreen.resultKey("RATE_LIMITED"));
    }

    @Test
    void unknownResultStatusUsesTheSafeFallback() {
        assertEquals("gui.futureshops.atm.result.server_error",
                AtmScreen.resultKey("FUTURE_STATE"));
    }

    @Test
    void cashCollectionStatusesUseDedicatedMessages() {
        assertEquals("gui.futureshops.atm.collect_result.delivered",
                AtmScreen.cashCollectionResultKey("DELIVERED"));
        assertEquals("gui.futureshops.atm.collect_result.manual_review",
                AtmScreen.cashCollectionResultKey("MANUAL_REVIEW"));
        assertEquals("gui.futureshops.atm.collect_result.rate_limited",
                AtmScreen.cashCollectionResultKey("RATE_LIMITED"));
        assertEquals("gui.futureshops.atm.collect_result.unavailable",
                AtmScreen.cashCollectionResultKey("FUTURE_STATE"));
    }

    @Test
    void cashCollectionRendersEveryBoundedQuarantineHandle() {
        UUID first = UUID.fromString(
                "51000000-0000-0000-0000-000000000001");
        UUID second = UUID.fromString(
                "51000000-0000-0000-0000-000000000002");
        S2CAtmCollectCashResultPacket result =
                new S2CAtmCollectCashResultPacket(
                        UUID.fromString(
                                "51000000-0000-0000-0000-000000000010"),
                        "MANUAL_REVIEW", false, false,
                        0, 3, List.of(first, second));

        assertEquals(first + ", " + second,
                AtmScreen.cashClaimRecoveryHandles(result));
    }

    @Test
    void atmRetryDelayRoundsUpForTheUi() {
        assertEquals(0L, AtmScreen.retrySeconds(0L));
        assertEquals(1L, AtmScreen.retrySeconds(1L));
        assertEquals(1L, AtmScreen.retrySeconds(1_000L));
        assertEquals(2L, AtmScreen.retrySeconds(1_001L));
    }

    @Test
    void terminalCollectionRemovesExactSubmittedClaimsWithoutRefresh() {
        UUID first = UUID.fromString(
                "51000000-0000-0000-0000-000000000021");
        UUID second = UUID.fromString(
                "51000000-0000-0000-0000-000000000022");
        UUID untouched = UUID.fromString(
                "51000000-0000-0000-0000-000000000023");
        S2CAtmDataPacket current = new S2CAtmDataPacket(
                100L, true, "Credits", 2, "futureshops",
                S2CAtmDataPacket.ROUTE_PROTECTED, true,
                "a".repeat(64),
                List.of(new AtmDenominationData(
                        "futureshops:money", 100L, 64)),
                true, S2CAtmDataPacket.AVAILABLE, true, 3,
                List.of(
                        new S2CAtmDataPacket.CashClaimSummary(
                                first, "PROTECTED_CASH", 1),
                        new S2CAtmDataPacket.CashClaimSummary(
                                second, "PROTECTED_CASH", 1),
                        new S2CAtmDataPacket.CashClaimSummary(
                                untouched, "PROTECTED_CASH", 1)));

        S2CAtmDataPacket reconciled =
                AtmScreen.reconcileTerminalCashClaims(
                        current, List.of(first, second), 1);

        assertEquals(1, reconciled.pendingCashClaimCount());
        assertEquals(List.of(untouched),
                reconciled.collectibleCashClaims().stream()
                        .map(S2CAtmDataPacket.CashClaimSummary::claimId)
                        .toList());
    }

    @Test
    void depositStatusesAndSourcesUseExactMessages() {
        assertEquals("gui.futureshops.atm.deposit_result.success",
                AtmScreen.depositResultKey("SUCCESS"));
        assertEquals(
                "gui.futureshops.atm.deposit_result.legacy_migration_required",
                AtmScreen.depositResultKey(
                        "LEGACY_MIGRATION_REQUIRED"));
        assertEquals("gui.futureshops.atm.deposit_result.rate_limited",
                AtmScreen.depositResultKey("RATE_LIMITED"));
        assertEquals("gui.futureshops.atm.deposit_result.too_many_items",
                AtmScreen.depositResultKey("TOO_MANY_ITEMS"));
        assertEquals(
                "gui.futureshops.atm.deposit_result.request_conflict",
                AtmScreen.depositResultKey("REQUEST_CONFLICT"));
        assertEquals("gui.futureshops.atm.deposit_result.cancelled",
                AtmScreen.depositResultKey("CANCELLED"));
        assertEquals("gui.futureshops.atm.deposit_result.server_error",
                AtmScreen.depositResultKey("FUTURE_STATUS"));
        assertEquals("gui.futureshops.atm.deposit_source.main_hand",
                AtmScreen.depositSourceKey(
                        C2SAtmDepositPacket.Source.MAIN_HAND));
        assertEquals(
                "gui.futureshops.atm.deposit_source.off_hand_exact",
                AtmScreen.depositSourceExactKey(
                        C2SAtmDepositPacket.Source.OFF_HAND));
    }

    @Test
    void catalogRefreshDefersForPendingWithdrawalsOrDeposits()
            throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/enviouse/futureshops/client/screen/AtmScreen.java"));

        assertEquals(true, source.contains(
                "ShopClientPacketHandler.pendingAtmDeposit()"));
        assertEquals(true, source.contains(
                "withdrawalCatalogChanged || depositCatalogChanged"));
        assertEquals(true, source.contains(
                "data.currencySignature(), depositSource, amount"));
    }
}

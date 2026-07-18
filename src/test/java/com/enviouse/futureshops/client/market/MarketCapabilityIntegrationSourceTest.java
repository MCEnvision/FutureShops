package com.enviouse.futureshops.client.market;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketCapabilityIntegrationSourceTest {
    @Test
    void capabilityPacketsAreAppendedInStableOrder() throws Exception {
        String source = read(
                "src/main/java/com/enviouse/futureshops/network/ShopPackets.java");
        int settlement = source.indexOf(
                "CHANNEL.messageBuilder(C2SPlayerShopSettlementClaimPacket.class");
        int request = source.indexOf(
                "CHANNEL.messageBuilder(C2SMarketCapabilitiesPacket.class");
        int response = source.indexOf(
                "CHANNEL.messageBuilder(S2CMarketCapabilitiesPacket.class");

        assertTrue(settlement >= 0);
        assertTrue(request > settlement);
        assertTrue(response > request);
        assertTrue(source.contains(
                "public static final String PROTOCOL_VERSION = \"48\""));
    }

    @Test
    void clientCorrelatesAppliesAndClearsCapabilityState()
            throws Exception {
        String handler = read(
                "src/main/java/com/enviouse/futureshops/client/ShopClientPacketHandler.java");
        String packet = read(
                "src/main/java/com/enviouse/futureshops/network/packets/S2CMarketCapabilitiesPacket.java");

        assertTrue(handler.contains(
                "MarketCapabilityClientState.beginRequest()"));
        assertTrue(handler.contains(
                "MarketCapabilityClientState.accept("));
        int acceptance = handler.indexOf(
                "MarketCapabilityClientState.accept(");
        int wallet = handler.indexOf(
                "ShopClientState.applyMarketWalletSnapshot(");
        assertTrue(wallet > acceptance);
        assertTrue(handler.substring(acceptance, wallet).contains(
                ".ACCEPT"));
        assertTrue(handler.contains(
                "market.applyCapabilities(packet.snapshot())"));
        assertTrue(handler.contains("MarketCapabilityClientState.clear()"));
        assertTrue(handler.contains(
                "ShopClientState.clearMarketWalletSnapshot()"));
        assertTrue(packet.contains(
                "handleMarketCapabilities(packet)"));
        assertTrue(packet.contains(
                "snapshot.walletBalanceMinorUnits()"));
        assertTrue(packet.contains("snapshot.currencyName()"));
    }

    @Test
    void sharedScreenRequestsRefreshesAndDisplaysClaimCapabilities()
            throws Exception {
        String screen = read(
                "src/main/java/com/enviouse/futureshops/client/screen/MarketModuleScreen.java");

        assertTrue(screen.contains("requestCapabilities();"));
        assertTrue(screen.contains("refreshMarketState()"));
        assertTrue(screen.contains("GLFW.GLFW_KEY_F5"));
        assertTrue(screen.contains("applyCapabilities("));
        assertTrue(screen.contains("openClaimCount(MarketModule target)"));
        assertTrue(screen.contains(
                "gui.futureshops.market.claim_count"));
        assertTrue(screen.contains("capability.canOpenView(view)"));
    }

    @Test
    void serverShutdownClearsCapabilityRevisionState() throws Exception {
        String source = read(
                "src/main/java/com/enviouse/futureshops/Futureshops.java");

        assertTrue(source.contains(
                "MarketCapabilityProjectionService.clearRevisionState()"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }
}

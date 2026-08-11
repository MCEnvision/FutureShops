package com.enviouse.futureshops.server.market.profile;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketProfileMutationIntegrationSourceTest {
    @Test
    void packetsAreAppendedAfterProtocolFortyFourTail()
            throws Exception {
        String source = read(
                "src/main/java/com/enviouse/futureshops/network/ShopPackets.java");
        int capability = source.indexOf(
                "CHANNEL.messageBuilder(S2CMarketCapabilitiesPacket.class");
        int request = source.indexOf(
                "CHANNEL.messageBuilder(C2SMarketProfileMutationPacket.class");
        int response = source.indexOf(
                "CHANNEL.messageBuilder(S2CMarketProfileMutationPacket.class");

        assertTrue(capability >= 0);
        assertTrue(request > capability);
        assertTrue(response > request);
        assertTrue(source.contains(
                "public static final String PROTOCOL_VERSION = \"57\""));
    }

    @Test
    void serverUsesSharedSessionsAccessPolicyAndPersistentProfiles()
            throws Exception {
        String packet = read(
                "src/main/java/com/enviouse/futureshops/network/packets/C2SMarketProfileMutationPacket.java");
        String service = read(
                "src/main/java/com/enviouse/futureshops/server/market/MarketProfileMutationService.java");
        String processor = read(
                "src/main/java/com/enviouse/futureshops/server/market/profile/MarketProfileMutationProcessor.java");
        String profiles = read(
                "src/main/java/com/enviouse/futureshops/server/market/profile/MarketProfileSavedData.java");

        assertTrue(packet.contains(
                "MarketProfileMutationService.mutate(player, packet)"));
        assertTrue(service.contains("MarketModuleService.sessions()"));
        assertTrue(service.contains("MarketProfileSavedData.get(server)"));
        assertTrue(service.contains("AuctionHouseSavedData.get(server)"));
        assertTrue(service.contains("BazaarSavedData.get(server)"));
        assertTrue(processor.contains(
                "MarketModuleAccessPolicy.pageAccess"));
        assertTrue(processor.contains("allowsBrowse()"));
        assertTrue(processor.contains("allowsClaims()"));
        assertTrue(processor.contains("retiredMutationRequest("));
        assertTrue(profiles.contains("mutationReplayFilterVersion"));
        assertTrue(profiles.contains("MAX_MUTATION_RECEIPTS = 512"));
    }

    @Test
    void clientTracksCorrelationWithoutScreenMutationWiring()
            throws Exception {
        String packet = read(
                "src/main/java/com/enviouse/futureshops/network/packets/S2CMarketProfileMutationPacket.java");
        String handler = read(
                "src/main/java/com/enviouse/futureshops/client/ShopClientPacketHandler.java");

        assertTrue(packet.contains(
                "handleMarketProfileMutation(packet)"));
        assertTrue(handler.contains(
                "MarketProfileMutationClientState.begin(packet.command())"));
        assertTrue(handler.contains(
                "MarketProfileMutationClientState.accept("));
        assertTrue(handler.contains(
                "MarketProfileMutationClientState.clear()"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }
}

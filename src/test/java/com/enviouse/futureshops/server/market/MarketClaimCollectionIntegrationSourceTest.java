package com.enviouse.futureshops.server.market;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketClaimCollectionIntegrationSourceTest {
    @Test
    void packetsAreTheStrictProtocolFortyFourTail() throws Exception {
        String source = read(
                "src/main/java/com/enviouse/futureshops/network/ShopPackets.java");
        int profileResponse = source.indexOf(
                "CHANNEL.messageBuilder(S2CMarketProfileMutationPacket.class");
        int request = source.indexOf(
                "CHANNEL.messageBuilder(C2SMarketClaimCollectionPacket.class");
        int response = source.indexOf(
                "CHANNEL.messageBuilder(S2CMarketClaimCollectionPacket.class");

        assertTrue(profileResponse >= 0);
        assertTrue(request > profileResponse);
        assertTrue(response > request);
        assertEquals(61, count(source.substring(0, request),
                "CHANNEL.messageBuilder"));
        assertEquals(62, count(source.substring(0, response),
                "CHANNEL.messageBuilder"));
        assertTrue(source.contains(
                "public static final String PROTOCOL_VERSION = \"52\""));

        String c2s = read(
                "src/main/java/com/enviouse/futureshops/network/packets/C2SMarketClaimCollectionPacket.java");
        String s2c = read(
                "src/main/java/com/enviouse/futureshops/network/packets/S2CMarketClaimCollectionPacket.java");
        assertTrue(c2s.contains("requireFullyRead(buffer)"));
        assertTrue(s2c.contains("requireFullyRead(buffer)"));
    }

    @Test
    void productionDispatchUsesEveryProtectedCollector()
            throws Exception {
        String service = read(
                "src/main/java/com/enviouse/futureshops/server/market/MarketClaimCollectionService.java");
        String processor = read(
                "src/main/java/com/enviouse/futureshops/server/market/MarketClaimCollectionProcessor.java");
        String planner = read(
                "src/main/java/com/enviouse/futureshops/server/escrow/runtime/ExactItemClaimDeliveryPlanner.java");

        assertTrue(service.contains("EscrowMoneyClaimService.collect("));
        assertTrue(service.contains("runtime.collectExactItemClaim("));
        assertTrue(service.contains("AtmCashClaimCenter.collect("));
        assertTrue(service.contains(
                "C2SAtmCollectCashPacket.deriveRequestId("));
        assertTrue(processor.contains("claim.kind().publiclyVisible()"));
        assertTrue(processor.contains(
                "case INTERNAL_ESCROW_MONEY"));
        assertTrue(processor.contains(
                "case SHOP -> !bazaar && !auction"));
        assertTrue(planner.contains("kind == ClaimKind.REFUND"));
    }

    private static int count(String value, String needle) {
        int result = 0;
        int offset = 0;
        while ((offset = value.indexOf(needle, offset)) >= 0) {
            result++;
            offset += needle.length();
        }
        return result;
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }
}

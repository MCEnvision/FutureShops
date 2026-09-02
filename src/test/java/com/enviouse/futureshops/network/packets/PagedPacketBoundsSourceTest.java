package com.enviouse.futureshops.network.packets;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PagedPacketBoundsSourceTest {
    @Test
    void pagedMutationHandlersRejectUnboundedPageRequests() throws Exception {
        Path root = Path.of("src/main/java/com/enviouse/futureshops/network/packets");
        for (String name : new String[]{
                "C2SOpenBalTopUiPacket.java",
                "C2SFetchHistoryPacket.java",
                "C2SFetchSettlementHistoryPacket.java"}) {
            String source = Files.readString(root.resolve(name));
            assertTrue(source.contains("PageBounds"), name);
            if (!name.equals("C2SOpenBalTopUiPacket.java")) {
                assertTrue(source.contains("PageBounds.isValid(packet.page(), packet.pageSize())"), name);
            }
        }
    }
}

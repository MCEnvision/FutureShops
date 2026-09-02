package com.enviouse.futureshopsp.network.packets;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PagedPacketBoundsSourceTest {
    @Test
    void pagedHandlersRejectUnboundedPageRequests() throws Exception {
        Path root = projectDirectory().resolve(Path.of(
                "src", "main", "java", "com", "enviouse", "futureshopsp", "network", "packets"));
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

    private static Path projectDirectory() {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null) {
            if (Files.isDirectory(candidate.resolve(Path.of("src", "main", "java")))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("FutureShops source directory is unavailable");
    }
}

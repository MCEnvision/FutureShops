package com.enviouse.futureshops.server.market;

import net.minecraftforge.server.permission.events.PermissionGatherEvent;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketPermissionsTest {
    @Test
    void allDocumentedNodesRegisterExactlyOnce() {
        PermissionGatherEvent.Nodes event =
                new PermissionGatherEvent.Nodes();
        MarketPermissions.register(event);
        Set<String> names = event.getNodes().stream()
                .map(node -> node.getNodeName())
                .collect(Collectors.toSet());

        assertEquals(13, names.size());
        assertTrue(names.contains("futureshops.auction.create"));
        assertTrue(names.contains("futureshops.bazaar.instant"));
        assertTrue(names.contains("futureshops.escrow.admin"));
    }

    @Test
    void claimDefaultsNeverConfiscateOwnedValue() {
        UUID player = UUID.randomUUID();
        assertTrue(MarketPermissions.AUCTION_CLAIM
                .getDefaultResolver().resolve(null, player));
        assertTrue(MarketPermissions.BAZAAR_CLAIM
                .getDefaultResolver().resolve(null, player));
        assertTrue(MarketPermissions.ESCROW_CLAIM
                .getDefaultResolver().resolve(null, player));
        assertFalse(MarketPermissions.AUCTION_USE
                .getDefaultResolver().resolve(null, player));
    }
}

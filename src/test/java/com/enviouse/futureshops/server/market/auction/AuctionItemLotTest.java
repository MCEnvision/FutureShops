package com.enviouse.futureshops.server.market.auction;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuctionItemLotTest {
    @Test
    void acceptsCanonicalResourceAndBoundedSearchMetadata() {
        AuctionItemLot lot = lot("example_mod:materials/refined.diamond", "ores/rare", "Refined Diamond");

        assertEquals("example_mod:materials/refined.diamond", lot.registryId());
        assertEquals("ores/rare", lot.categoryId());
    }

    @Test
    void rejectsNoncanonicalOrMalformedResourceIdentifiers() {
        assertThrows(IllegalArgumentException.class,
            () -> lot("Minecraft:diamond", "ores", "diamond"));
        assertThrows(IllegalArgumentException.class,
            () -> lot("minecraft:bad path", "ores", "diamond"));
        assertThrows(IllegalArgumentException.class,
            () -> lot("minecraft:diamond:extra", "ores", "diamond"));
        assertThrows(IllegalArgumentException.class,
            () -> lot(" minecraft:diamond", "ores", "diamond"));
    }

    @Test
    void rejectsControlCharactersAndMalformedUtf16() {
        assertThrows(IllegalArgumentException.class,
            () -> lot("minecraft:diamond", "ores", "diamond\nblock"));
        assertThrows(IllegalArgumentException.class,
            () -> lot("minecraft:diamond", "ores", "diamond\uD800"));
        assertThrows(IllegalArgumentException.class,
            () -> lot("minecraft:diamond", "ORES", "diamond"));
    }

    private static AuctionItemLot lot(String registryId, String category, String search) {
        return new AuctionItemLot(
            new UUID(1L, 1L), registryId, "a".repeat(64),
            1, 32, category, search);
    }
}

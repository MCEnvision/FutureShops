package com.enviouse.futureshops;

import com.enviouse.futureshops.network.ShopPackets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the network protocol version. The v2.0 admin-NBT-listing work added a leading {@code listingId}
 * field to {@link com.enviouse.futureshops.data.CatalogItem} and the buy/sell/admin-cart wire lines,
 * which is a breaking wire change — so the version was bumped 23 → 24 and v23 clients are refused at
 * handshake. A silent revert to 23 (or any other value) would let mismatched clients desync on the new
 * field; this test makes that a red build.
 */
public class ProtocolVersionConstantTest {

    @Test
    void protocolVersionIs24() {
        assertEquals("24", ShopPackets.PROTOCOL_VERSION,
                "PROTOCOL_VERSION must be \"24\" for the per-listing-id wire format. "
                        + "If you intentionally changed the wire format, bump it and update this test.");
    }
}

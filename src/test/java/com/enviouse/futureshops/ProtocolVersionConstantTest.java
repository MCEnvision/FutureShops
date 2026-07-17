package com.enviouse.futureshops;

import com.enviouse.futureshops.network.ShopPackets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the network protocol version. Protocol 30 appends the ATM open/data/withdraw/result packets
 * so clients can submit exact denomination plans to the server-authoritative minting path.
 * Protocol 29 changes the protocol-28 barter-batch action from
 * an off-hand-paid atomic add to safe targets followed by searchable ingredient editors. The wire
 * shape is unchanged, but mixed v28/v29 peers would interpret the same packet differently.
 * Protocol 27 added: (a) the floating-icon config packet
 * ({@link com.enviouse.futureshops.network.packets.C2SPlayerShopIconPacket}, registered at the end
 * of the id space) letting owners set the block-top icon mode (CYCLE / OWNER_HEAD / CUSTOM_ITEM),
 * with trailing {@code FloatingIconMode}/{@code FloatingIconItem} strings on the block update tag;
 * and (b) trailing {@code hidden}/{@code showcase} booleans on
 * {@link com.enviouse.futureshops.data.PlayerShopListingData} for the per-listing visibility flags,
 * and (c) trailing {@code floatingIconMode}/{@code floatingIconItem} strings + a
 * {@link com.enviouse.futureshops.data.PlayerShopStorageEntry} list + a {@code savedConfigNames}
 * string list on {@link com.enviouse.futureshops.network.packets.S2CPlayerShopDataPacket} (owner
 * Storefront/Storage/Payouts tabs), plus three appended C2S packets
 * ({@link com.enviouse.futureshops.network.packets.C2SPlayerShopIconPacket},
 * {@link com.enviouse.futureshops.network.packets.C2SPlayerShopUnlinkStoragePacket},
 * {@link com.enviouse.futureshops.network.packets.C2SPlayerShopSavedConfigPacket}).
 * Both are breaking wire changes, so the version was bumped 26 → 27 and v26 clients are refused
 * at handshake. (Protocol 26 added the in-GUI admin shop editor: a trailing {@code canEdit} boolean
 * on {@link com.enviouse.futureshops.network.packets.S2CShopDataPacket} plus three new packets;
 * v25 appended trailing {@code nbtJson} fields to barter ingredients, verify-cart lines,
 * settlement/history rows, owned-shop summaries and the bal-top popular item, plus a trailing
 * {@code targetListingId} on {@link com.enviouse.futureshops.data.CatalogBarterRecipe}; v24 added
 * the leading {@code listingId} to {@link com.enviouse.futureshops.data.CatalogItem} and the
 * buy/sell/admin-cart lines.) A silent revert would let mismatched clients disagree on packet
 * fields or semantics; this test makes that a red build.
 */
public class ProtocolVersionConstantTest {

    @Test
    void protocolVersionIs31() {
        assertEquals("31", ShopPackets.PROTOCOL_VERSION,
                "PROTOCOL_VERSION must be 31 because purchase packets now include payment source");
    }
}

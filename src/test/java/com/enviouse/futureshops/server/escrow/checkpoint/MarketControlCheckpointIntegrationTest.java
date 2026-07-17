package com.enviouse.futureshops.server.escrow.checkpoint;

import com.enviouse.futureshops.MinecraftTestBootstrap;
import com.enviouse.futureshops.server.escrow.admin.EscrowAdministrativeAuditSavedData;
import com.enviouse.futureshops.server.escrow.claim.ClaimSavedData;
import com.enviouse.futureshops.server.escrow.custody.CustodySavedData;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryJournalSavedData;
import com.enviouse.futureshops.server.escrow.ledger.LedgerSavedData;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintSavedData;
import com.enviouse.futureshops.server.escrow.runtime.EscrowRuntimeSavedData;
import com.enviouse.futureshops.server.escrow.runtime.PlayerShopEscrowSavedData;
import com.enviouse.futureshops.server.escrow.runtime.ServerShopIntentSavedData;
import com.enviouse.futureshops.server.escrow.stock.StockSavedData;
import com.enviouse.futureshops.server.escrow.store.EscrowTransactionSavedData;
import com.enviouse.futureshops.server.market.auction.AuctionHouseSavedData;
import com.enviouse.futureshops.server.market.bazaar.BazaarSavedData;
import com.enviouse.futureshops.server.market.control.MarketControlActor;
import com.enviouse.futureshops.server.market.control.MarketControlModule;
import com.enviouse.futureshops.server.market.control.MarketControlSavedData;
import com.enviouse.futureshops.server.market.control.MarketControlTransitionCommand;
import com.enviouse.futureshops.server.market.control.MarketModuleStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketControlCheckpointIntegrationTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void checkpointRestoresNonemptyMarketControlState() {
        Fixture source = fixture("control.source", 3L);
        apply(source.control(), command("source.freeze",
                MarketControlModule.AUCTION_HOUSE, 0L,
                MarketModuleStatus.FROZEN, 100L));
        apply(source.control(), command("source.drain",
                MarketControlModule.BAZAAR, 0L,
                MarketModuleStatus.DRAINING, 120L));
        Fixture target = fixture("control.target", 2L);
        apply(target.control(), command("target.freeze",
                MarketControlModule.SHOP, 0L,
                MarketModuleStatus.FROZEN, 90L));

        Map<EscrowCheckpointStore, byte[]> snapshots =
                source.bundle().captureSnapshots();
        target.bundle().prepareSnapshots(snapshots, source.lineage(),
                source.sequence()).apply();

        assertEquals(source.control().snapshot(),
                target.control().snapshot());
        assertEquals(source.control().auditProjection(),
                target.control().auditProjection());
        assertTrue(target.control().receipt(
                id("source.freeze")) != null);
        assertEquals(MarketModuleStatus.DRAINING,
                target.control().snapshot().module(
                        MarketControlModule.BAZAAR).status());
    }

    @Test
    void corruptMarketControlComponentCausesNoLiveMutation() {
        Fixture source = fixture("corrupt.source", 3L);
        apply(source.control(), command("corrupt.source.freeze",
                MarketControlModule.AUCTION_HOUSE, 0L,
                MarketModuleStatus.FROZEN, 100L));
        Fixture target = fixture("corrupt.target", 2L);
        apply(target.control(), command("corrupt.target.drain",
                MarketControlModule.SHOP, 0L,
                MarketModuleStatus.DRAINING, 90L));
        var baseline = target.control().snapshot();
        EnumMap<EscrowCheckpointStore, byte[]> corrupted =
                copy(source.bundle().captureSnapshots());
        byte[] component = corrupted.get(
                EscrowCheckpointStore.MARKET_CONTROL);
        component[component.length - 1] ^= 1;

        assertThrows(EscrowCheckpointSnapshotException.class,
                () -> target.bundle().prepareSnapshots(corrupted,
                        source.lineage(), source.sequence()));
        assertEquals(baseline, target.control().snapshot());
    }

    private static void apply(
            MarketControlSavedData control,
            MarketControlTransitionCommand command
    ) {
        control.applyCommitted(control.planStandalone(command)
                .mutation().orElseThrow());
    }

    private static MarketControlTransitionCommand command(
            String key,
            MarketControlModule module,
            long revision,
            MarketModuleStatus status,
            long requestedAt
    ) {
        return new MarketControlTransitionCommand(id(key), module,
                revision, status,
                new MarketControlActor(id("operator"), "Operator"),
                "Checkpoint test", requestedAt, requestedAt + 10L,
                Optional.empty(), Optional.empty());
    }

    private static Fixture fixture(String key, long sequence) {
        UUID lineage = id(key + ".lineage");
        EscrowRuntimeSavedData runtime = new EscrowRuntimeSavedData();
        runtime.establishLineage(lineage, 1L);
        for (long next = 2L; next <= sequence; next++) {
            runtime.advance(lineage, next);
        }
        MarketControlSavedData control = new MarketControlSavedData();
        EscrowSavedDataCheckpointBundle bundle =
                new EscrowSavedDataCheckpointBundle(
                        new EscrowTransactionSavedData(),
                        new LedgerSavedData(), new ClaimSavedData(),
                        new EscrowAdministrativeAuditSavedData(),
                        new CustodySavedData(),
                        new ProtectedMintSavedData(),
                        new StockSavedData(),
                        new ItemInventoryJournalSavedData(),
                        new AuctionHouseSavedData(),
                        new BazaarSavedData(),
                        new ServerShopIntentSavedData(),
                        new PlayerShopEscrowSavedData(), control,
                        runtime, () -> true);
        return new Fixture(lineage, sequence, control, bundle);
    }

    private static EnumMap<EscrowCheckpointStore, byte[]> copy(
            Map<EscrowCheckpointStore, byte[]> source
    ) {
        EnumMap<EscrowCheckpointStore, byte[]> copied =
                new EnumMap<>(EscrowCheckpointStore.class);
        source.forEach((store, bytes) -> copied.put(store,
                bytes.clone()));
        return copied;
    }

    private static UUID id(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(
                StandardCharsets.UTF_8));
    }

    private record Fixture(
            UUID lineage,
            long sequence,
            MarketControlSavedData control,
            EscrowSavedDataCheckpointBundle bundle
    ) {
    }
}

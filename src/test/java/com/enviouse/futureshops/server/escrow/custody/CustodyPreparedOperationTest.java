package com.enviouse.futureshops.server.escrow.custody;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustodyPreparedOperationTest {
    @Test
    void preparedIntentHasStableIdentityAndCodecWithPreciseTime() {
        CustodyLot lot = CustodyTestFixtures.itemLot("prepared reserve", 1);
        Instant precise = Instant.parse("2026-07-16T12:00:00.987654321Z");
        CustodyPreparedOperation first = CustodyPreparedOperation.prepare(CustodyOperation.RESERVE,
                lot.reserveRequestKey(), lot, lot.holdEvidence().source().adapterId(),
                lot.sourceCapability(), "simulation token", lot.holdEvidence(), precise);
        CustodyPreparedOperation second = CustodyPreparedOperation.prepare(CustodyOperation.RESERVE,
                lot.reserveRequestKey(), lot, lot.holdEvidence().source().adapterId(),
                lot.sourceCapability(), "simulation token", lot.holdEvidence(), precise);

        byte[] encoded = CustodyPreparedOperationCodec.encode(first);
        CustodyPreparedOperation decoded = CustodyPreparedOperationCodec.decode(encoded);

        assertEquals(first.intentId(), second.intentId());
        assertEquals(first, decoded);
        assertArrayEquals(encoded, CustodyPreparedOperationCodec.encode(decoded));
    }

    @Test
    void unresolvedIntentSurvivesRestartAndMutationResolvesIt() {
        CustodyLot lot = CustodyTestFixtures.itemLot("prepared restart", 4);
        CustodyPreparedOperation intent = CustodyPreparedOperation.prepare(CustodyOperation.RESERVE,
                lot.reserveRequestKey(), lot, lot.holdEvidence().source().adapterId(),
                lot.sourceCapability(), "capacity accepted", lot.holdEvidence(),
                CustodyTestFixtures.NOW);
        CustodySavedData data = new CustodySavedData();
        data.prepareCommitted(intent);

        CustodySavedData loaded = CustodySavedData.load(data.save(new CompoundTag()));
        assertEquals(1, loaded.unresolvedPreparedOperations(10).size());
        assertTrue(loaded.hasMaterializedState());

        loaded.applyCommitted(CustodyMutation.reserve(lot));
        assertTrue(loaded.unresolvedPreparedOperations(10).isEmpty());
        assertTrue(loaded.applyCommitted(CustodyMutation.reserve(lot)).replayed());

        CustodySavedData resolvedReload = CustodySavedData.load(loaded.save(new CompoundTag()));
        assertTrue(resolvedReload.unresolvedPreparedOperations(10).isEmpty());
        assertTrue(resolvedReload.conservation().conserved());
    }

    @Test
    void duplicatePreparedRequestWithChangedSimulationFailsClosed() {
        CustodyLot lot = CustodyTestFixtures.itemLot("prepared duplicate", 1);
        CustodyPreparedOperation first = CustodyPreparedOperation.prepare(CustodyOperation.RESERVE,
                lot.reserveRequestKey(), lot, lot.holdEvidence().source().adapterId(),
                lot.sourceCapability(), "first simulation", lot.holdEvidence(),
                CustodyTestFixtures.NOW);
        CustodyPreparedOperation changed = CustodyPreparedOperation.prepare(CustodyOperation.RESERVE,
                lot.reserveRequestKey(), lot, lot.holdEvidence().source().adapterId(),
                lot.sourceCapability(), "changed simulation", lot.holdEvidence(),
                CustodyTestFixtures.NOW);
        CustodySavedData data = new CustodySavedData();
        data.prepareCommitted(first);

        assertThrows(CustodyConflictException.class, () -> data.prepareCommitted(changed));
    }

    @Test
    void invalidMutationCannotPartiallyChangeCustodyState() {
        CustodyLot lot = CustodyTestFixtures.itemLot("atomic reserve", 2);
        CustodySavedData data = new CustodySavedData();
        data.applyCommitted(CustodyMutation.reserve(lot));
        CustodyTransferEvidence evidence = CustodyTestFixtures.terminalEvidence("atomic release");
        Instant receiptTime = CustodyTestFixtures.NOW.plusSeconds(1);
        CustodyOperationReceipt receipt = CustodyOperationReceipt.terminal(
                lot, CustodyOperation.RELEASE, "atomic release", evidence, receiptTime);
        CustodyLot mismatched = lot.transition(CustodyLotState.RELEASED,
                receiptTime.plusSeconds(1));
        CustodyMutation invalid = new CustodyMutation(mismatched, receipt);

        assertThrows(CustodyConflictException.class, () -> data.applyCommitted(invalid));
        assertEquals(CustodyLotState.HELD, data.getLot(lot.lotId()).state());

        CustodyMutation valid = CustodyMutation.terminal(lot, CustodyOperation.RELEASE,
                "atomic release", evidence, receiptTime);
        assertEquals(CustodyLotState.RELEASED,
                data.applyCommitted(valid).lot().state());
    }

    @Test
    void preflightLeavesPreparedAndCustodyStoresUnchanged() {
        CustodyLot lot = CustodyTestFixtures.itemLot("preflight custody", 2);
        CustodyPreparedOperation intent = CustodyPreparedOperation.prepare(
                CustodyOperation.RESERVE, lot.reserveRequestKey(), lot,
                lot.holdEvidence().source().adapterId(), lot.sourceCapability(),
                "preflight token", lot.holdEvidence(), CustodyTestFixtures.NOW);
        CustodySavedData data = new CustodySavedData();

        data.preflightPrepareCommitted(intent);
        assertTrue(data.unresolvedPreparedOperations(10).isEmpty());
        data.prepareCommitted(intent);
        data.preflightCommitted(CustodyMutation.reserve(lot));

        assertEquals(null, data.getLot(lot.lotId()));
        assertEquals(1, data.unresolvedPreparedOperations(10).size());
    }
}

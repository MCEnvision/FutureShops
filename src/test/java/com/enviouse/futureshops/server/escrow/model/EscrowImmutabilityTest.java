package com.enviouse.futureshops.server.escrow.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EscrowImmutabilityTest {
    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void participantDefensivelyCopiesRoles() {
        EnumSet<EscrowParticipantRole> roles = EnumSet.of(EscrowParticipantRole.INITIATOR);
        EscrowParticipant participant = new EscrowParticipant(EscrowParty.system("system"), roles);

        roles.add(EscrowParticipantRole.PAYER);

        assertEquals(Set.of(EscrowParticipantRole.INITIATOR), participant.roles());
        assertThrows(
                UnsupportedOperationException.class,
                () -> participant.roles().add(EscrowParticipantRole.PAYER)
        );
    }

    @Test
    void assetDefensivelyCopiesPayloadAndAttributes() {
        byte[] payload = new byte[]{1, 2, 3};
        Map<String, String> attributes = new HashMap<>();
        attributes.put("item_id", "minecraft:diamond");
        EscrowAssetLot asset = itemLot(payload, attributes);

        payload[0] = 9;
        attributes.put("item_id", "minecraft:dirt");
        byte[] returned = asset.serializedPayload();
        returned[1] = 9;

        assertArrayEquals(new byte[]{1, 2, 3}, asset.serializedPayload());
        assertEquals("minecraft:diamond", asset.attributes().get("item_id"));
        assertThrows(
                UnsupportedOperationException.class,
                () -> asset.attributes().put("new", "value")
        );
    }

    @Test
    void assetEqualityUsesPayloadContents() {
        UUID lotId = UUID.randomUUID();
        EscrowParty source = EscrowParty.system("source");
        EscrowParty destination = EscrowParty.system("destination");
        EscrowAssetLot first = itemLot(lotId, source, destination, new byte[]{1, 2, 3});
        EscrowAssetLot second = itemLot(lotId, source, destination, new byte[]{1, 2, 3});

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    void transactionDefensivelyCopiesParticipantsAndAssets() {
        EscrowParty source = EscrowParty.player(UUID.randomUUID());
        EscrowParty destination = EscrowParty.system("destination");
        Set<EscrowParticipant> participants = new HashSet<>();
        participants.add(new EscrowParticipant(
                source,
                EnumSet.of(EscrowParticipantRole.INITIATOR, EscrowParticipantRole.PAYER)
        ));
        participants.add(new EscrowParticipant(
                destination,
                EnumSet.of(EscrowParticipantRole.BENEFICIARY)
        ));
        List<EscrowAssetLot> assets = new ArrayList<>();
        assets.add(itemLot(UUID.randomUUID(), source, destination, new byte[]{1}));

        EscrowTransaction transaction = EscrowTransaction.create(
                EscrowTransactionId.random(),
                Optional.empty(),
                new EscrowRequestKey("immutable-request"),
                EscrowOperation.PLAYER_PAYMENT,
                participants,
                assets,
                START,
                1,
                Optional.empty()
        );
        participants.clear();
        assets.clear();

        assertEquals(2, transaction.participants().size());
        assertEquals(1, transaction.assetLots().size());
        assertThrows(UnsupportedOperationException.class, () -> transaction.participants().clear());
        assertThrows(UnsupportedOperationException.class, () -> transaction.assetLots().clear());
    }

    @Test
    void errorDefensivelyCopiesDetails() {
        Map<String, String> details = new HashMap<>();
        details.put("adapter", "inventory");
        EscrowError error = new EscrowError("FAILED", "Failed", true, START, details);

        details.put("adapter", "changed");

        assertEquals("inventory", error.details().get("adapter"));
        assertThrows(UnsupportedOperationException.class, () -> error.details().clear());
    }

    private static EscrowAssetLot itemLot(byte[] payload, Map<String, String> attributes) {
        return itemLot(
                UUID.randomUUID(),
                EscrowParty.system("source"),
                EscrowParty.system("destination"),
                payload,
                attributes
        );
    }

    private static EscrowAssetLot itemLot(
            UUID lotId,
            EscrowParty source,
            EscrowParty destination,
            byte[] payload
    ) {
        return itemLot(lotId, source, destination, payload, Map.of("item_id", "minecraft:diamond"));
    }

    private static EscrowAssetLot itemLot(
            UUID lotId,
            EscrowParty source,
            EscrowParty destination,
            byte[] payload,
            Map<String, String> attributes
    ) {
        return new EscrowAssetLot(
                lotId,
                EscrowAssetLotType.ITEM_STACK,
                EscrowProtectionLevel.RECONCILED,
                source,
                destination,
                1,
                Optional.empty(),
                payload,
                attributes
        );
    }
}

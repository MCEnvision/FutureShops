package com.enviouse.futureshops.server.escrow.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EscrowModelInvariantTest {
    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private static final EscrowParty PLAYER = EscrowParty.player(
            UUID.fromString("00000000-0000-0000-0000-000000000001")
    );
    private static final EscrowParty SYSTEM = EscrowParty.system("treasury");
    private static final EscrowTransactionId ID = new EscrowTransactionId(
            UUID.fromString("20000000-0000-0000-0000-000000000001")
    );

    @Test
    void transactionRejectsInvalidIdentityAndRevision() {
        EscrowTransaction valid = newTransaction();

        assertThrows(IllegalArgumentException.class, () -> copy(
                valid,
                Optional.of(valid.transactionId()),
                valid.participants(),
                valid.assetLots(),
                valid.operation(),
                valid.state(),
                valid.timestamps(),
                valid.lastError(),
                valid.retryMetadata(),
                valid.shopReference(),
                valid.revision(),
                valid.configRevision()
        ));
        assertThrows(IllegalArgumentException.class, () -> copy(
                valid,
                valid.parentTransactionId(),
                valid.participants(),
                valid.assetLots(),
                valid.operation(),
                valid.state(),
                valid.timestamps(),
                valid.lastError(),
                valid.retryMetadata(),
                valid.shopReference(),
                -1,
                valid.configRevision()
        ));
        assertThrows(IllegalArgumentException.class, () -> new EscrowRequestKey(" "));
        assertThrows(IllegalArgumentException.class, () -> new MoneyAmount("credits", -1));
    }

    @Test
    void transactionRequiresOneParticipantRecordPerPartyAndAnInitiator() {
        EscrowTransaction valid = newTransaction();
        EscrowParticipant first = new EscrowParticipant(
                PLAYER,
                EnumSet.of(EscrowParticipantRole.INITIATOR)
        );
        EscrowParticipant second = new EscrowParticipant(
                PLAYER,
                EnumSet.of(EscrowParticipantRole.PAYER)
        );

        assertThrows(IllegalArgumentException.class, () -> copy(
                valid,
                valid.parentTransactionId(),
                Set.of(first, second, beneficiary()),
                valid.assetLots(),
                valid.operation(),
                valid.state(),
                valid.timestamps(),
                valid.lastError(),
                valid.retryMetadata(),
                valid.shopReference(),
                valid.revision(),
                valid.configRevision()
        ));
        assertThrows(IllegalArgumentException.class, () -> copy(
                valid,
                valid.parentTransactionId(),
                Set.of(
                        new EscrowParticipant(PLAYER, EnumSet.of(EscrowParticipantRole.PAYER)),
                        beneficiary()
                ),
                valid.assetLots(),
                valid.operation(),
                valid.state(),
                valid.timestamps(),
                valid.lastError(),
                valid.retryMetadata(),
                valid.shopReference(),
                valid.revision(),
                valid.configRevision()
        ));
    }

    @Test
    void transactionRequiresUniqueLotsAndParticipantEndpoints() {
        EscrowTransaction valid = newTransaction();

        assertThrows(IllegalArgumentException.class, () -> copy(
                valid,
                valid.parentTransactionId(),
                valid.participants(),
                List.of(valid.assetLots().get(0), valid.assetLots().get(0)),
                valid.operation(),
                valid.state(),
                valid.timestamps(),
                valid.lastError(),
                valid.retryMetadata(),
                valid.shopReference(),
                valid.revision(),
                valid.configRevision()
        ));

        EscrowAssetLot unknownDestination = walletLot(
                UUID.randomUUID(),
                PLAYER,
                EscrowParty.system("unknown")
        );
        assertThrows(IllegalArgumentException.class, () -> copy(
                valid,
                valid.parentTransactionId(),
                valid.participants(),
                List.of(unknownDestination),
                valid.operation(),
                valid.state(),
                valid.timestamps(),
                valid.lastError(),
                valid.retryMetadata(),
                valid.shopReference(),
                valid.revision(),
                valid.configRevision()
        ));
    }

    @Test
    void shopOperationsRequireDimensionAwareLocation() {
        EscrowTransaction valid = newTransaction();

        assertThrows(IllegalArgumentException.class, () -> copy(
                valid,
                valid.parentTransactionId(),
                valid.participants(),
                valid.assetLots(),
                EscrowOperation.PLAYER_SHOP_BUY,
                valid.state(),
                valid.timestamps(),
                valid.lastError(),
                valid.retryMetadata(),
                Optional.empty(),
                valid.revision(),
                valid.configRevision()
        ));

        DimensionAwareShopReference overworld = new DimensionAwareShopReference(
                "shop-one",
                "minecraft:overworld",
                10,
                64,
                20
        );
        DimensionAwareShopReference nether = new DimensionAwareShopReference(
                "shop-one",
                "minecraft:the_nether",
                10,
                64,
                20
        );
        assertNotEquals(overworld, nether);
    }

    @Test
    void stateMetadataMustMatchState() {
        EscrowTransaction valid = newTransaction();
        EscrowTimestamps decidedMissing = new EscrowTimestamps(
                START,
                START.plusSeconds(1),
                Optional.empty(),
                Optional.empty()
        );

        assertThrows(IllegalArgumentException.class, () -> copy(
                valid,
                valid.parentTransactionId(),
                valid.participants(),
                valid.assetLots(),
                valid.operation(),
                EscrowState.COMMITTED,
                decidedMissing,
                valid.lastError(),
                valid.retryMetadata(),
                valid.shopReference(),
                valid.revision(),
                valid.configRevision()
        ));

        EscrowTimestamps terminalMissing = new EscrowTimestamps(
                START,
                START.plusSeconds(2),
                Optional.of(START.plusSeconds(1)),
                Optional.empty()
        );
        assertThrows(IllegalArgumentException.class, () -> copy(
                valid,
                valid.parentTransactionId(),
                valid.participants(),
                valid.assetLots(),
                valid.operation(),
                EscrowState.COMPLETED,
                terminalMissing,
                valid.lastError(),
                valid.retryMetadata(),
                valid.shopReference(),
                valid.revision(),
                valid.configRevision()
        ));

        EscrowRetryMetadata scheduled = EscrowRetryMetadata.none().schedule(
                EscrowState.HOLDING,
                3,
                START.plusSeconds(5)
        );
        assertThrows(IllegalArgumentException.class, () -> copy(
                valid,
                valid.parentTransactionId(),
                valid.participants(),
                valid.assetLots(),
                valid.operation(),
                EscrowState.HOLDING,
                valid.timestamps(),
                valid.lastError(),
                scheduled,
                valid.shopReference(),
                valid.revision(),
                valid.configRevision()
        ));
    }

    @Test
    void moneyMathIsCurrencySafeAndOverflowChecked() {
        MoneyAmount ten = new MoneyAmount("futureshops:credits", 10);
        MoneyAmount five = new MoneyAmount("futureshops:credits", 5);

        assertEquals(new MoneyAmount("futureshops:credits", 15), ten.add(five));
        assertEquals(new MoneyAmount("futureshops:credits", 5), ten.subtract(five));
        assertEquals(new MoneyAmount("futureshops:credits", 50), ten.multiply(5));
        assertThrows(ArithmeticException.class, () -> five.subtract(ten));
        assertThrows(
                IllegalArgumentException.class,
                () -> ten.add(new MoneyAmount("other:credits", 5))
        );
        assertThrows(
                ArithmeticException.class,
                () -> new MoneyAmount("futureshops:credits", Long.MAX_VALUE).add(five)
        );
    }

    @Test
    void assetShapeAndProtectionRulesAreStrict() {
        assertThrows(IllegalArgumentException.class, () -> new EscrowAssetLot(
                UUID.randomUUID(),
                EscrowAssetLotType.WALLET_MONEY,
                EscrowProtectionLevel.PROTECTED,
                PLAYER,
                SYSTEM,
                1,
                Optional.empty(),
                new byte[0],
                Map.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> new EscrowAssetLot(
                UUID.randomUUID(),
                EscrowAssetLotType.FOREIGN_PHYSICAL_CURRENCY,
                EscrowProtectionLevel.PROTECTED,
                PLAYER,
                SYSTEM,
                1,
                Optional.of(new MoneyAmount("foreign:coin", 1)),
                new byte[]{1},
                Map.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> new EscrowAssetLot(
                UUID.randomUUID(),
                EscrowAssetLotType.ITEM_STACK,
                EscrowProtectionLevel.RECONCILED,
                PLAYER,
                SYSTEM,
                1,
                Optional.empty(),
                new byte[0],
                Map.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> new EscrowAssetLot(
                UUID.randomUUID(),
                EscrowAssetLotType.STOCK_RESERVATION,
                EscrowProtectionLevel.PROTECTED,
                PLAYER,
                SYSTEM,
                1,
                Optional.empty(),
                new byte[0],
                Map.of()
        ));

        EscrowAssetLot reservation = new EscrowAssetLot(
                UUID.randomUUID(),
                EscrowAssetLotType.STOCK_RESERVATION,
                EscrowProtectionLevel.PROTECTED,
                PLAYER,
                SYSTEM,
                1,
                Optional.empty(),
                new byte[0],
                Map.of("resource_id", "catalog:item")
        );
        assertTrue(reservation.type().isReservation());
    }

    @Test
    void errorAndRetryMetadataRejectMalformedValues() {
        assertThrows(IllegalArgumentException.class, () -> new EscrowError(
                "lowercase",
                "message",
                true,
                START,
                Map.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> new EscrowRetryMetadata(
                1,
                3,
                Optional.of(START),
                Optional.empty()
        ));
        assertThrows(IllegalArgumentException.class, () -> EscrowRetryMetadata.none().schedule(
                EscrowState.COMPLETED,
                3,
                START
        ));
    }

    private static EscrowTransaction newTransaction() {
        return EscrowTransaction.create(
                ID,
                Optional.empty(),
                new EscrowRequestKey("request-one"),
                EscrowOperation.PLAYER_PAYMENT,
                Set.of(initiator(), beneficiary()),
                List.of(walletLot(UUID.fromString("10000000-0000-0000-0000-000000000001"), PLAYER, SYSTEM)),
                START,
                7,
                Optional.empty()
        );
    }

    private static EscrowParticipant initiator() {
        return new EscrowParticipant(
                PLAYER,
                EnumSet.of(EscrowParticipantRole.INITIATOR, EscrowParticipantRole.PAYER)
        );
    }

    private static EscrowParticipant beneficiary() {
        return new EscrowParticipant(
                SYSTEM,
                EnumSet.of(EscrowParticipantRole.BENEFICIARY)
        );
    }

    private static EscrowAssetLot walletLot(UUID lotId, EscrowParty source, EscrowParty destination) {
        return new EscrowAssetLot(
                lotId,
                EscrowAssetLotType.WALLET_MONEY,
                EscrowProtectionLevel.PROTECTED,
                source,
                destination,
                1,
                Optional.of(new MoneyAmount("futureshops:credits", 100)),
                new byte[0],
                Map.of()
        );
    }

    private static EscrowTransaction copy(
            EscrowTransaction value,
            Optional<EscrowTransactionId> parent,
            Set<EscrowParticipant> participants,
            List<EscrowAssetLot> assets,
            EscrowOperation operation,
            EscrowState state,
            EscrowTimestamps timestamps,
            Optional<EscrowError> lastError,
            EscrowRetryMetadata retry,
            Optional<DimensionAwareShopReference> shopReference,
            long revision,
            long configRevision
    ) {
        return new EscrowTransaction(
                value.transactionId(),
                parent,
                value.requestKey(),
                operation,
                state,
                participants,
                assets,
                timestamps,
                revision,
                configRevision,
                lastError,
                retry,
                shopReference
        );
    }
}

package com.enviouse.futureshops.server.escrow.runtime;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ForeignCashClaimPayloadCodecTest {
    @Test
    void roundTripRetainsFullStackBytesAndStableFingerprint() {
        byte[] itemStackNbt = ForeignAtmWithdrawalTestFixtures.nbt(
                "banknote", 32, 0);
        ForeignCashClaimPayload payload = ForeignCashClaimPayload.capture(
                ForeignAtmWithdrawalTestFixtures.PROVIDER,
                ForeignAtmWithdrawalTestFixtures.SIGNATURE,
                "examplecurrency:banknote",
                100L, 32, 0, 0, 1, itemStackNbt);

        byte[] encoded = ForeignCashClaimPayloadCodec.encode(payload);
        ForeignCashClaimPayload decoded =
                ForeignCashClaimPayloadCodec.decode(encoded);

        assertEquals(payload, decoded);
        assertEquals(payload.fingerprint(), decoded.fingerprint());
        assertArrayEquals(itemStackNbt, decoded.serializedItemStackNbt());
        assertArrayEquals(encoded,
                ForeignCashClaimPayloadCodec.encode(decoded));
        itemStackNbt[0] ^= 1;
        assertNotEquals(itemStackNbt[0],
                decoded.serializedItemStackNbt()[0]);
    }

    @Test
    void changedMetadataOrStackBytesCannotReuseFingerprint() {
        ForeignCashClaimPayload payload = ForeignCashClaimPayload.capture(
                ForeignAtmWithdrawalTestFixtures.PROVIDER,
                ForeignAtmWithdrawalTestFixtures.SIGNATURE,
                "examplecurrency:banknote",
                100L, 32, 0, 0, 1,
                ForeignAtmWithdrawalTestFixtures.nbt("banknote", 32, 0));

        assertThrows(IllegalArgumentException.class,
                () -> new ForeignCashClaimPayload(
                        payload.providerId(), payload.configSignature(),
                        payload.registryItemId(),
                        payload.denominationMinorUnits() + 1L,
                        payload.stackCount(), payload.denominationIndex(),
                        payload.portionIndex(), payload.portionCount(),
                        payload.serializedItemStackNbt(), payload.fingerprint()));

        byte[] changedNbt = payload.serializedItemStackNbt();
        changedNbt[0] ^= 1;
        assertThrows(IllegalArgumentException.class,
                () -> new ForeignCashClaimPayload(
                        payload.providerId(), payload.configSignature(),
                        payload.registryItemId(),
                        payload.denominationMinorUnits(),
                        payload.stackCount(), payload.denominationIndex(),
                        payload.portionIndex(), payload.portionCount(),
                        changedNbt, payload.fingerprint()));
    }

    @Test
    void codecRejectsTamperingSchemasTruncationTrailingDataAndBounds() {
        ForeignCashClaimPayload payload = ForeignCashClaimPayload.capture(
                ForeignAtmWithdrawalTestFixtures.PROVIDER,
                ForeignAtmWithdrawalTestFixtures.SIGNATURE,
                "examplecurrency:coin",
                25L, 4, 1, 0, 1,
                ForeignAtmWithdrawalTestFixtures.nbt("coin", 4, 0));
        byte[] encoded = ForeignCashClaimPayloadCodec.encode(payload);

        byte[] badMagic = encoded.clone();
        badMagic[0] ^= 1;
        assertThrows(IllegalArgumentException.class,
                () -> ForeignCashClaimPayloadCodec.decode(badMagic));

        byte[] newer = encoded.clone();
        newer[7] = 2;
        assertThrows(IllegalStateException.class,
                () -> ForeignCashClaimPayloadCodec.decode(newer));

        byte[] changedFingerprint = encoded.clone();
        changedFingerprint[changedFingerprint.length - 1] ^= 1;
        assertThrows(IllegalArgumentException.class,
                () -> ForeignCashClaimPayloadCodec.decode(changedFingerprint));

        assertThrows(IllegalArgumentException.class,
                () -> ForeignCashClaimPayloadCodec.decode(
                        Arrays.copyOf(encoded, encoded.length - 1)));
        assertThrows(IllegalArgumentException.class,
                () -> ForeignCashClaimPayloadCodec.decode(
                        Arrays.copyOf(encoded, encoded.length + 1)));
        assertThrows(IllegalArgumentException.class,
                () -> ForeignCashClaimPayloadCodec.decode(new byte[
                        ForeignCashClaimPayloadCodec.MAX_ENCODED_BYTES + 1]));
    }

    @Test
    void constructorRejectsProtectedProviderInvalidConfigAndFieldBounds() {
        byte[] nbt = ForeignAtmWithdrawalTestFixtures.nbt("coin", 1, 0);
        assertThrows(IllegalArgumentException.class,
                () -> ForeignCashClaimPayload.capture(
                        "futureshops",
                        ForeignAtmWithdrawalTestFixtures.SIGNATURE,
                        "futureshops:money",
                        1L, 1, 0, 0, 1, nbt));
        assertThrows(IllegalArgumentException.class,
                () -> ForeignCashClaimPayload.capture(
                        ForeignAtmWithdrawalTestFixtures.PROVIDER,
                        "not a signature",
                        "examplecurrency:coin",
                        1L, 1, 0, 0, 1, nbt));
        assertThrows(IllegalArgumentException.class,
                () -> ForeignCashClaimPayload.capture(
                        ForeignAtmWithdrawalTestFixtures.PROVIDER,
                        ForeignAtmWithdrawalTestFixtures.SIGNATURE,
                        "Invalid Item",
                        1L, 1, 0, 0, 1, nbt));
        assertThrows(IllegalArgumentException.class,
                () -> ForeignCashClaimPayload.capture(
                        ForeignAtmWithdrawalTestFixtures.PROVIDER,
                        ForeignAtmWithdrawalTestFixtures.SIGNATURE,
                        "examplecurrency:coin",
                        1L,
                        ForeignCashClaimPayload.MAX_STACK_COUNT + 1,
                        0, 0, 1, nbt));
        assertThrows(IllegalArgumentException.class,
                () -> ForeignCashClaimPayload.capture(
                        ForeignAtmWithdrawalTestFixtures.PROVIDER,
                        ForeignAtmWithdrawalTestFixtures.SIGNATURE,
                        "examplecurrency:coin",
                        1L, 1,
                        ForeignCashClaimPayload.MAX_DENOMINATIONS,
                        0, 1, nbt));
        assertThrows(IllegalArgumentException.class,
                () -> ForeignCashClaimPayload.capture(
                        ForeignAtmWithdrawalTestFixtures.PROVIDER,
                        ForeignAtmWithdrawalTestFixtures.SIGNATURE,
                        "examplecurrency:coin",
                        1L, 1, 0, 1, 1, nbt));
        assertThrows(IllegalArgumentException.class,
                () -> ForeignCashClaimPayload.capture(
                        ForeignAtmWithdrawalTestFixtures.PROVIDER,
                        ForeignAtmWithdrawalTestFixtures.SIGNATURE,
                        "examplecurrency:coin",
                        1L, 1, 0, 0, 1,
                        new byte[
                                ForeignCashClaimPayload.MAX_ITEM_STACK_NBT_BYTES
                                        + 1]));
    }
}

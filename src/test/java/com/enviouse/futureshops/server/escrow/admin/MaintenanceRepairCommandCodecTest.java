package com.enviouse.futureshops.server.escrow.admin;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MaintenanceRepairCommandCodecTest {
    private static final Instant CREATED =
            Instant.parse("2026-07-17T12:34:56.123456789Z");

    @Test
    void everyPayloadAndBothExpectedStateFormsRoundTripWithStableBytes() {
        UUID transactionId = UUID.randomUUID();
        List<Scenario> scenarios = List.of(
                new Scenario(MaintenanceRepairTarget.runtime(),
                        MaintenanceExpectedState.fingerprint(bytes(1)),
                        new MaintenanceRepairPayload.EnterMaintenance("incident.42")),
                new Scenario(MaintenanceRepairTarget.transaction(transactionId),
                        MaintenanceExpectedState.revision(3L),
                        new MaintenanceRepairPayload.RetryReset()),
                new Scenario(MaintenanceRepairTarget.transaction(transactionId),
                        MaintenanceExpectedState.revision(4L),
                        new MaintenanceRepairPayload.ForceRefund()),
                new Scenario(MaintenanceRepairTarget.transaction(transactionId),
                        MaintenanceExpectedState.revision(5L),
                        new MaintenanceRepairPayload.ForceSettlement()),
                new Scenario(MaintenanceRepairTarget.claim(UUID.randomUUID()),
                        MaintenanceExpectedState.fingerprint(bytes(2)),
                        new MaintenanceRepairPayload.ClaimQuarantine()),
                new Scenario(MaintenanceRepairTarget.claim(UUID.randomUUID()),
                        MaintenanceExpectedState.fingerprint(bytes(3)),
                        new MaintenanceRepairPayload.ClaimRepair(
                                MaintenanceClaimRepairDisposition.COMPLETE, 0L)),
                new Scenario(MaintenanceRepairTarget.custodyLot(UUID.randomUUID()),
                        MaintenanceExpectedState.revision(0L),
                        new MaintenanceRepairPayload.CustodyReconcile(
                                MaintenanceStateFingerprint.of(bytes(4)),
                                MaintenanceCustodyDisposition.MARK_RELEASED)),
                new Scenario(MaintenanceRepairTarget.custodyBatch(UUID.randomUUID()),
                        MaintenanceExpectedState.revision(1L),
                        new MaintenanceRepairPayload.CustodyQuarantine()),
                new Scenario(MaintenanceRepairTarget.runtime(),
                        MaintenanceExpectedState.fingerprint(bytes(5)),
                        new MaintenanceRepairPayload.VerifyAndResume(99L,
                                MaintenanceStateFingerprint.of(bytes(6)))));

        for (Scenario scenario : scenarios) {
            MaintenanceRepairCommand command = command(scenario.target(),
                    scenario.expectedState(), scenario.payload());
            byte[] encoded = MaintenanceRepairCommandCodec.encode(command);
            MaintenanceRepairCommand decoded = MaintenanceRepairCommandCodec.decode(encoded);
            assertEquals(command, decoded);
            assertArrayEquals(encoded, MaintenanceRepairCommandCodec.encode(decoded));
        }
    }

    @Test
    void framingSchemaEnumsBooleansUtf8AndTimestampsFailClosed() {
        byte[] encoded = MaintenanceRepairCommandCodec.encode(command(
                MaintenanceRepairTarget.transaction(UUID.randomUUID()),
                MaintenanceExpectedState.revision(3L),
                new MaintenanceRepairPayload.RetryReset()));

        byte[] badMagic = encoded.clone();
        badMagic[0] = 0;
        assertMalformed(badMagic);

        byte[] newer = encoded.clone();
        putInt(newer, 4, MaintenanceRepairCommandCodec.CURRENT_SCHEMA + 1);
        assertThrows(IllegalStateException.class,
                () -> MaintenanceRepairCommandCodec.decode(newer));

        byte[] old = encoded.clone();
        putInt(old, 4, MaintenanceRepairCommandCodec.CURRENT_SCHEMA - 1);
        assertMalformed(old);

        byte[] badConfirmation = encoded.clone();
        badConfirmation[34] = 2;
        assertMalformed(badConfirmation);

        byte[] badNanos = encoded.clone();
        Arrays.fill(badNanos, 43, 47, (byte) 0xff);
        assertMalformed(badNanos);

        byte[] badTarget = encoded.clone();
        badTarget[47] = 99;
        assertMalformed(badTarget);

        byte[] badExpectedState = encoded.clone();
        badExpectedState[64] = 99;
        assertMalformed(badExpectedState);

        byte[] badAction = encoded.clone();
        badAction[73] = 99;
        assertMalformed(badAction);

        byte[] malformedUtf8 = encoded.clone();
        malformedUtf8[28] = (byte) 0x80;
        assertMalformed(malformedUtf8);

        byte[] oversizedActor = encoded.clone();
        putInt(oversizedActor, 24, Integer.MAX_VALUE);
        assertMalformed(oversizedActor);

        byte[] invalidAuditBoolean = encoded.clone();
        invalidAuditBoolean[130] = 2;
        assertMalformed(invalidAuditBoolean);

        byte[] mismatchedAuditAction = encoded.clone();
        mismatchedAuditAction[95] = 4;
        assertMalformed(mismatchedAuditAction);
    }

    @Test
    void actionSpecificEnumsAndFingerprintsFailClosed() {
        byte[] claim = MaintenanceRepairCommandCodec.encode(command(
                MaintenanceRepairTarget.claim(UUID.randomUUID()),
                MaintenanceExpectedState.revision(1L),
                new MaintenanceRepairPayload.ClaimRepair(
                        MaintenanceClaimRepairDisposition.REOPEN_PENDING, 2L)));
        claim[74] = 99;
        assertMalformed(claim);

        byte[] custody = MaintenanceRepairCommandCodec.encode(command(
                MaintenanceRepairTarget.custodyLot(UUID.randomUUID()),
                MaintenanceExpectedState.revision(1L),
                new MaintenanceRepairPayload.CustodyReconcile(
                        MaintenanceStateFingerprint.of(bytes(7)),
                        MaintenanceCustodyDisposition.CONFIRM_HELD)));
        custody[106] = 99;
        assertMalformed(custody);

        byte[] fingerprint = MaintenanceRepairCommandCodec.encode(command(
                MaintenanceRepairTarget.claim(UUID.randomUUID()),
                MaintenanceExpectedState.fingerprint(bytes(8)),
                new MaintenanceRepairPayload.ClaimQuarantine()));
        Arrays.fill(fingerprint, 65, 97, (byte) 0);
        assertMalformed(fingerprint);
    }

    @Test
    void truncationTrailingDataAndGlobalSizeLimitFailClosed() {
        byte[] encoded = MaintenanceRepairCommandCodec.encode(command(
                MaintenanceRepairTarget.transaction(UUID.randomUUID()),
                MaintenanceExpectedState.revision(3L),
                new MaintenanceRepairPayload.RetryReset()));
        for (int length : List.of(1, 24, 64, 74, encoded.length - 1)) {
            assertMalformed(Arrays.copyOf(encoded, length));
        }
        assertMalformed(Arrays.copyOf(encoded, encoded.length + 1));
        assertThrows(IllegalArgumentException.class,
                () -> MaintenanceRepairCommandCodec.decode(null));
        assertThrows(IllegalArgumentException.class,
                () -> MaintenanceRepairCommandCodec.decode(new byte[0]));
        assertThrows(IllegalArgumentException.class,
                () -> MaintenanceRepairCommandCodec.decode(
                        new byte[MaintenanceRepairCommandCodec.MAX_ENCODED_BYTES + 1]));
    }

    private static MaintenanceRepairCommand command(MaintenanceRepairTarget target,
                                                    MaintenanceExpectedState expectedState,
                                                    MaintenanceRepairPayload payload) {
        return MaintenanceRepairCommand.create(UUID.randomUUID(), "a", "b", true,
                CREATED, target, expectedState, payload, true, "c");
    }

    private static void assertMalformed(byte[] encoded) {
        assertThrows(IllegalArgumentException.class,
                () -> MaintenanceRepairCommandCodec.decode(encoded));
    }

    private static void putInt(byte[] value, int offset, int replacement) {
        ByteBuffer.wrap(value, offset, Integer.BYTES).putInt(replacement);
    }

    private static byte[] bytes(int seed) {
        byte[] value = new byte[MaintenanceStateFingerprint.BYTE_LENGTH];
        for (int index = 0; index < value.length; index++) {
            value[index] = (byte) (seed + index);
        }
        return value;
    }

    private record Scenario(MaintenanceRepairTarget target,
                            MaintenanceExpectedState expectedState,
                            MaintenanceRepairPayload payload) {
    }
}

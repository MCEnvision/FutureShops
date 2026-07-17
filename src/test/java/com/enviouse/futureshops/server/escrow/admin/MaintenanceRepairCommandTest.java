package com.enviouse.futureshops.server.escrow.admin;

import com.enviouse.futureshops.server.escrow.model.EscrowTransactionId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaintenanceRepairCommandTest {
    private static final Instant CREATED =
            Instant.parse("2026-07-17T12:34:56.123456789Z");

    @Test
    void everyAllowlistedActionHasAnExactTargetAndPayloadShape() {
        UUID transactionId = UUID.randomUUID();
        List<Scenario> scenarios = List.of(
                new Scenario(MaintenanceRepairTarget.runtime(),
                        new MaintenanceRepairPayload.EnterMaintenance("incident.42")),
                new Scenario(MaintenanceRepairTarget.transaction(transactionId),
                        new MaintenanceRepairPayload.RetryReset()),
                new Scenario(MaintenanceRepairTarget.transaction(transactionId),
                        new MaintenanceRepairPayload.ForceRefund()),
                new Scenario(MaintenanceRepairTarget.transaction(transactionId),
                        new MaintenanceRepairPayload.ForceSettlement()),
                new Scenario(MaintenanceRepairTarget.claim(UUID.randomUUID()),
                        new MaintenanceRepairPayload.ClaimQuarantine()),
                new Scenario(MaintenanceRepairTarget.claim(UUID.randomUUID()),
                        new MaintenanceRepairPayload.ClaimRepair(
                                MaintenanceClaimRepairDisposition.REOPEN_PARTIAL, 3L)),
                new Scenario(MaintenanceRepairTarget.custodyLot(UUID.randomUUID()),
                        new MaintenanceRepairPayload.CustodyReconcile(
                                fingerprint(1), MaintenanceCustodyDisposition.CONFIRM_HELD)),
                new Scenario(MaintenanceRepairTarget.custodyLot(UUID.randomUUID()),
                        new MaintenanceRepairPayload.CustodyQuarantine()),
                new Scenario(MaintenanceRepairTarget.custodyBatch(UUID.randomUUID()),
                        new MaintenanceRepairPayload.CustodyQuarantine()),
                new Scenario(MaintenanceRepairTarget.runtime(),
                        new MaintenanceRepairPayload.VerifyAndResume(91L, fingerprint(2))));

        for (Scenario scenario : scenarios) {
            MaintenanceRepairCommand command = command(scenario.target(),
                    scenario.payload(), true, true);
            assertEquals(scenario.payload().action(), command.auditRecord().action());
            assertEquals(scenario.target().targetId(), command.target().targetId());
            assertTrue(command.appliesAction());
            Optional<EscrowTransactionId> transaction =
                    scenario.target().type() == MaintenanceRepairTargetType.TRANSACTION
                            ? Optional.of(new EscrowTransactionId(
                            scenario.target().targetId())) : Optional.empty();
            assertEquals(transaction, command.auditRecord().transactionId());
        }
    }

    @Test
    void unconfirmedAttemptsCanOnlyProduceFailureAudits() {
        MaintenanceRepairTarget target = MaintenanceRepairTarget.transaction(UUID.randomUUID());
        MaintenanceRepairPayload payload = new MaintenanceRepairPayload.RetryReset();
        MaintenanceRepairCommand rejected = command(target, payload, false, false);
        assertFalse(rejected.appliesAction());
        assertFalse(rejected.auditRecord().successful());

        assertThrows(IllegalArgumentException.class,
                () -> command(target, payload, false, true));
    }

    @Test
    void embeddedAuditKeepsExistingSavedDataCompatibility() {
        MaintenanceRepairCommand command = command(
                MaintenanceRepairTarget.claim(UUID.randomUUID()),
                new MaintenanceRepairPayload.ClaimRepair(
                        MaintenanceClaimRepairDisposition.REOPEN_PENDING, 2L),
                true, true);
        EscrowAdministrativeAuditSavedData data = new EscrowAdministrativeAuditSavedData();
        data.append(command.auditRecord());
        EscrowAdministrativeAuditSavedData loaded = EscrowAdministrativeAuditSavedData.load(
                data.save(new net.minecraft.nbt.CompoundTag()));
        assertEquals(command.auditRecord(), loaded.getRecord(command.commandId()));
    }

    @Test
    void actionTargetAndAuditMismatchesFailClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> command(MaintenanceRepairTarget.claim(UUID.randomUUID()),
                        new MaintenanceRepairPayload.ForceRefund(), true, true));
        assertThrows(IllegalArgumentException.class,
                () -> command(MaintenanceRepairTarget.custodyBatch(UUID.randomUUID()),
                        new MaintenanceRepairPayload.CustodyReconcile(
                                fingerprint(3), MaintenanceCustodyDisposition.QUARANTINE),
                        true, true));

        MaintenanceRepairCommand command = command(
                MaintenanceRepairTarget.transaction(UUID.randomUUID()),
                new MaintenanceRepairPayload.RetryReset(), true, true);
        EscrowAdministrativeRecord mismatched = new EscrowAdministrativeRecord(
                command.commandId(), "different actor", command.payload().action(),
                command.auditRecord().transactionId(), command.reason(), command.createdAt(),
                true, command.auditRecord().outcome());
        assertThrows(IllegalArgumentException.class,
                () -> new MaintenanceRepairCommand(command.commandId(), command.actor(),
                        command.reason(), command.confirmed(), command.createdAt(),
                        command.target(), command.expectedState(), command.payload(), mismatched));
    }

    @Test
    void optimisticStateAndActionValuesAreStrictlyBounded() {
        assertThrows(IllegalArgumentException.class,
                () -> MaintenanceExpectedState.revision(-1L));
        assertThrows(IllegalArgumentException.class,
                () -> MaintenanceExpectedState.fingerprint(new byte[32]));
        assertThrows(IllegalArgumentException.class,
                () -> MaintenanceExpectedState.fingerprint(new byte[31]));
        assertThrows(IllegalArgumentException.class,
                () -> new MaintenanceRepairTarget(MaintenanceRepairTargetType.RUNTIME,
                        UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class,
                () -> MaintenanceRepairTarget.claim(new UUID(0L, 0L)));
        assertThrows(IllegalArgumentException.class,
                () -> new MaintenanceRepairPayload.EnterMaintenance("x".repeat(129)));
        assertThrows(IllegalArgumentException.class,
                () -> new MaintenanceRepairPayload.ClaimRepair(
                        MaintenanceClaimRepairDisposition.COMPLETE, 1L));
        assertThrows(IllegalArgumentException.class,
                () -> new MaintenanceRepairPayload.ClaimRepair(
                        MaintenanceClaimRepairDisposition.REOPEN_PENDING, 0L));
        assertThrows(IllegalArgumentException.class,
                () -> new MaintenanceRepairPayload.VerifyAndResume(-1L, fingerprint(4)));
        assertThrows(IllegalArgumentException.class,
                () -> command(MaintenanceRepairTarget.runtime(),
                        new MaintenanceRepairPayload.EnterMaintenance("incident"),
                        "x".repeat(161), "reason", true, true));
        assertThrows(IllegalArgumentException.class,
                () -> command(MaintenanceRepairTarget.runtime(),
                        new MaintenanceRepairPayload.EnterMaintenance("incident"),
                        "actor", "x".repeat(1025), true, true));
        assertThrows(IllegalArgumentException.class,
                () -> command(MaintenanceRepairTarget.runtime(),
                        new MaintenanceRepairPayload.EnterMaintenance("bad.\ud800"),
                        true, true));
        assertThrows(IllegalArgumentException.class,
                () -> command(MaintenanceRepairTarget.runtime(),
                        new MaintenanceRepairPayload.EnterMaintenance("bad\nincident"),
                        true, true));
    }

    @Test
    void fingerprintValuesAreImmutable() {
        byte[] source = bytes(7);
        MaintenanceStateFingerprint fingerprint = MaintenanceStateFingerprint.of(source);
        source[0] = 99;
        byte[] exposed = fingerprint.bytes();
        exposed[1] = 99;
        assertEquals(bytes(7)[0], fingerprint.bytes()[0]);
        assertEquals(bytes(7)[1], fingerprint.bytes()[1]);
        assertEquals(MaintenanceStateFingerprint.of(bytes(7)), fingerprint);
    }

    private static MaintenanceRepairCommand command(MaintenanceRepairTarget target,
                                                    MaintenanceRepairPayload payload,
                                                    boolean confirmed,
                                                    boolean successful) {
        return command(target, payload, "console", "Verified repair",
                confirmed, successful);
    }

    private static MaintenanceRepairCommand command(MaintenanceRepairTarget target,
                                                    MaintenanceRepairPayload payload,
                                                    String actor,
                                                    String reason,
                                                    boolean confirmed,
                                                    boolean successful) {
        return MaintenanceRepairCommand.create(UUID.randomUUID(), actor, reason,
                confirmed, CREATED, target, MaintenanceExpectedState.revision(4L),
                payload, successful, successful ? "Applied" : "Confirmation required");
    }

    private static MaintenanceStateFingerprint fingerprint(int seed) {
        return MaintenanceStateFingerprint.of(bytes(seed));
    }

    private static byte[] bytes(int seed) {
        byte[] value = new byte[MaintenanceStateFingerprint.BYTE_LENGTH];
        for (int index = 0; index < value.length; index++) {
            value[index] = (byte) (seed + index);
        }
        return value;
    }

    private record Scenario(MaintenanceRepairTarget target,
                            MaintenanceRepairPayload payload) {
    }
}

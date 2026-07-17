package com.enviouse.futureshops.server.escrow.admin;

import com.enviouse.futureshops.server.escrow.model.EscrowTransactionId;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EscrowAdministrativeAuditSavedDataTest {
    @Test
    void recordsAreImmutableIdempotentAndPersistent() {
        EscrowAdministrativeAuditSavedData data = new EscrowAdministrativeAuditSavedData();
        EscrowAdministrativeRecord record = record(UUID.randomUUID(), "Retry after storage repair");

        assertTrue(data.append(record).applied());
        assertTrue(data.append(record).replayed());
        EscrowAdministrativeAuditSavedData loaded = EscrowAdministrativeAuditSavedData.load(
                data.save(new CompoundTag()));

        assertEquals(record, loaded.getRecord(record.requestId()));
        assertEquals(record, loaded.latest(10).get(0));
    }

    @Test
    void reusedRequestAndNewerSchemaFailClosed() {
        UUID requestId = UUID.randomUUID();
        EscrowAdministrativeAuditSavedData data = new EscrowAdministrativeAuditSavedData();
        data.append(record(requestId, "First reason"));

        assertThrows(AdminAuditConflictException.class,
                () -> data.append(record(requestId, "Different reason")));

        CompoundTag newer = new CompoundTag();
        newer.putInt("schemaVersion", Integer.MAX_VALUE);
        assertThrows(IllegalStateException.class,
                () -> EscrowAdministrativeAuditSavedData.load(newer));
    }

    private static EscrowAdministrativeRecord record(UUID requestId, String reason) {
        return new EscrowAdministrativeRecord(
                requestId,
                "console",
                EscrowAdministrativeAction.RETRY_TRANSACTION,
                Optional.of(EscrowTransactionId.random()),
                reason,
                Instant.parse("2026-07-16T12:00:00.123456789Z"),
                true,
                "Scheduled");
    }
}

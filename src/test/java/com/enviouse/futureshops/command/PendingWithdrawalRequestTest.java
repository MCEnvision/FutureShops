package com.enviouse.futureshops.command;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PendingWithdrawalRequestTest {
    private static final String SIGNATURE = "a".repeat(64);

    @Test
    void roundTripPreservesTheStableRetryIdentityAndSelection() {
        PendingWithdrawalRequest request = new PendingWithdrawalRequest(
                UUID.fromString("7d387731-49cf-4e2c-a421-b0678457ed08"),
                12_300L, true, SIGNATURE,
                List.of(1, 2, 3), 1_700_000_000L);

        PendingWithdrawalRequest decoded =
                PendingWithdrawalRequest.fromTag(request.toTag());

        assertEquals(request, decoded);
        assertTrue(decoded.matches(12_300L, true));
        assertFalse(decoded.matches(12_300L, false));
        assertFalse(decoded.matches(12_301L, true));
    }

    @Test
    void constructorRejectsInvalidOrOversizedSelections() {
        UUID requestId = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,
                () -> new PendingWithdrawalRequest(
                        requestId, 100L, true, SIGNATURE,
                        List.of(0, 0), 1L));
        assertThrows(IllegalArgumentException.class,
                () -> new PendingWithdrawalRequest(
                        requestId, 100L, true, SIGNATURE,
                        List.of(4096, 1), 1L));
        assertThrows(IllegalArgumentException.class,
                () -> new PendingWithdrawalRequest(
                        requestId, 100L, true, "A".repeat(64),
                        List.of(1), 1L));
    }

    @Test
    void decoderRejectsWrongVersionsAndNbtTypes() {
        PendingWithdrawalRequest request = new PendingWithdrawalRequest(
                UUID.randomUUID(), 100L, false, SIGNATURE,
                List.of(1), 1L);
        CompoundTag wrongVersion = request.toTag();
        wrongVersion.putInt("version", 2);
        assertThrows(IllegalArgumentException.class,
                () -> PendingWithdrawalRequest.fromTag(wrongVersion));

        CompoundTag wrongCounts = request.toTag();
        wrongCounts.putString("counts", "1");
        assertThrows(IllegalArgumentException.class,
                () -> PendingWithdrawalRequest.fromTag(wrongCounts));
    }
}

package com.enviouse.futureshops.server.economy;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WalletMutationGuardTest {
    @Test
    void overlappingWalletSetsCannotReenterOnTheSameThread() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();

        try (WalletMutationGuard.Lease ignored =
                     WalletMutationGuard.tryAcquire(
                             List.of(first, second)).orElseThrow()) {
            assertTrue(WalletMutationGuard.tryAcquire(
                    List.of(first)).isEmpty());
            assertTrue(WalletMutationGuard.tryAcquire(
                    List.of(second, third)).isEmpty());
            try (WalletMutationGuard.Lease independent =
                         WalletMutationGuard.tryAcquire(
                                 List.of(third)).orElseThrow()) {
                assertTrue(WalletMutationGuard.tryAcquire(
                        List.of(third)).isEmpty());
            }
        }

        assertTrue(WalletMutationGuard.tryAcquire(
                List.of(first, second)).map(lease -> {
                    lease.close();
                    return true;
                }).orElse(false));
    }
}

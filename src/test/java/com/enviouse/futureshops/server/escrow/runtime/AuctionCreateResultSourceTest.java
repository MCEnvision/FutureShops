package com.enviouse.futureshops.server.escrow.runtime;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AuctionCreateResultSourceTest {
    @Test
    void durableCreateCannotReportRecoveryRequired() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/enviouse/futureshops/server/escrow/runtime/AuctionActionService.java"));
        int createCatch = source.indexOf(
                "Optional<AuctionEscrowCommit> durable =");
        int appliedResponse = source.indexOf(
                "respondFromCommit(player, requestId, \"CREATE\"",
                createCatch);
        int recoveryResponse = source.indexOf(
                "respond(player, requestId, \"CREATE\", \"RECOVERY_REQUIRED\"",
                createCatch);

        assertTrue(createCatch >= 0);
        assertTrue(appliedResponse > createCatch);
        assertTrue(recoveryResponse > appliedResponse);
    }
}

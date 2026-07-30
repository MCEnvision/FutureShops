package com.enviouse.futureshops.server.shop;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerShopOfferHistorySourceTest {
    @Test
    void normalizedHistoryUsesRequestAndOptionIdentity()
            throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/enviouse/futureshops/server/shop/"
                        + "PlayerShopEscrowTransactionService.java"));
        int finish = source.indexOf(
                "private static void finishOffer(");
        int delegate = source.indexOf(
                "recordOfferHistory(actor, quote.intent(), result)",
                finish);
        int record = source.indexOf(
                "TransactionHistoryService.recordServerOfferComponents(",
                delegate);
        int option = source.indexOf(
                "selection.optionId()", record);
        int pending = source.indexOf(
                "COMMITTED_WITH_PENDING_DELIVERY", delegate);

        assertTrue(finish >= 0);
        assertTrue(delegate > finish);
        assertTrue(pending > delegate);
        assertTrue(record > pending);
        assertTrue(option > record);
    }
}

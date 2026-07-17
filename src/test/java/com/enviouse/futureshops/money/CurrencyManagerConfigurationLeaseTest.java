package com.enviouse.futureshops.money;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurrencyManagerConfigurationLeaseTest {
    @Test
    void configPublishesCurrencyAndMintInputsInsideWriteScope()
            throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/enviouse/futureshops/Config.java"));
        int scopeStart = source.indexOf(
                ".withConfigurationWriteLock(() -> {");
        int scopeEnd = source.indexOf(
                "\n                });", scopeStart);

        assertTrue(scopeStart >= 0);
        assertTrue(scopeEnd > scopeStart);
        for (String assignment : List.of(
                "economyCurrencyName =",
                "economyCurrencyDecimals =",
                "economyStartingBalanceMinorUnits =",
                "economyMaxBalanceMinorUnits =",
                "economyAllowNegative =",
                "currencyProvider =",
                "currencyItems =",
                "currencyAcceptOnlyItems =",
                "moneyChecksumSalt =",
                "moneyMintServerId =",
                "moneyMaxAgeDays =",
                "CurrencyManager.initialize()")) {
            int position = source.indexOf(assignment, scopeStart);
            assertTrue(position > scopeStart && position < scopeEnd,
                    assignment);
        }
    }

    @Test
    void writeSideWaitsForConfigurationReadLease() throws Exception {
        CountDownLatch writerStarted = new CountDownLatch(1);
        CountDownLatch writeEntered = new CountDownLatch(1);
        CurrencyManager.ConfigurationReadLease lease =
                CurrencyManager.acquireConfigurationReadLease();
        Thread writer = new Thread(() -> {
            writerStarted.countDown();
            CurrencyManager.withConfigurationWriteLock(
                    writeEntered::countDown);
        });
        boolean enteredWhileHeld;
        try {
            writer.start();
            assertTrue(writerStarted.await(5L, TimeUnit.SECONDS));
            enteredWhileHeld = writeEntered.await(
                    100L, TimeUnit.MILLISECONDS);
        } finally {
            lease.close();
        }

        assertFalse(enteredWhileHeld);
        assertTrue(writeEntered.await(5L, TimeUnit.SECONDS));
        writer.join(TimeUnit.SECONDS.toMillis(5L));
        assertFalse(writer.isAlive());
    }
}

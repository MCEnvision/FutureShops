package com.enviouse.futureshops.server.escrow.runtime;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerPaymentArchitectureTest {
    @Test
    void commandUsesEscrowWithStatusAndSameReferenceRetry()
            throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/enviouse/futureshops/command/PayCommand.java"));

        assertTrue(source.contains("PlayerPaymentService.pay("));
        assertTrue(source.contains("PlayerPaymentService.status("));
        assertTrue(source.contains("UuidArgument.getUuid("));
        assertTrue(source.contains("UUID.randomUUID()"));
        int executeStart = source.indexOf(
                "private static int executePayment(");
        int executeEnd = source.indexOf(
                "private static int status(", executeStart);
        String execute = source.substring(executeStart, executeEnd);
        assertTrue(execute.indexOf(
                "command.futureshops.pay.reference.submitting")
                < execute.indexOf("PlayerPaymentService.pay("));
        assertTrue(source.contains(
                "\"request_id\", UuidArgument.uuid()"));
        assertFalse(source.contains("BalanceManager.transfer("));
        assertFalse(source.contains("provider.transfer("));
    }

    @Test
    void completedReplayPrecedesEveryMutableOrLivePolicyCheck()
            throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/enviouse/futureshops/server/escrow/runtime/PlayerPaymentService.java"));
        int payStart = source.indexOf(
                "public static synchronized PlayerPaymentResult pay(");
        int payEnd = source.indexOf(
                "public static synchronized PaymentStatusResult status(");
        String pay = source.substring(payStart, payEnd);
        int replay = pay.indexOf("resolveReplay(");

        assertTrue(replay >= 0);
        assertTrue(replay < pay.indexOf("runtime.isReady()"));
        assertTrue(replay < pay.indexOf(
                "ServerRequestSecurityManager.tryAcquire("));
        assertTrue(replay < pay.indexOf(
                "WalletMutationGuard.tryAcquire("));
        assertTrue(replay < pay.indexOf(
                "BalanceManager.getProvider()"));
        assertTrue(replay < pay.indexOf("basicFailure(request)"));
        assertTrue(replay < pay.indexOf("postPreEvents(preview)"));
        assertTrue(occurrences(pay,
                "CurrencyManager.acquireConfigurationReadLease()") >= 2);
    }

    @Test
    void walletMutationsShareOneReentrancyGuardAndHistoryOutbox()
            throws Exception {
        String provider = Files.readString(Path.of(
                "src/main/java/com/enviouse/futureshops/server/economy/InternalEconomyProvider.java"));
        String payment = Files.readString(Path.of(
                "src/main/java/com/enviouse/futureshops/server/escrow/runtime/PlayerPaymentService.java"));
        String runtime = Files.readString(Path.of(
                "src/main/java/com/enviouse/futureshops/server/escrow/runtime/EscrowRuntimeService.java"));

        assertTrue(provider.contains("WalletMutationGuard.tryAcquire("));
        assertFalse(provider.contains("ACTIVE_ACCOUNTS"));
        assertTrue(payment.contains("WalletMutationGuard.tryAcquire("));
        assertTrue(payment.contains("if (!result.replayed())"));
        assertTrue(runtime.contains(
                "paymentHistoryProjector.reconcileBatch("));
        assertTrue(runtime.indexOf("recoveryScheduler.processBatch(")
                < runtime.indexOf(
                "paymentHistoryProjector.reconcileBatch("));
    }

    private static int occurrences(String source, String value) {
        int count = 0;
        int from = 0;
        while ((from = source.indexOf(value, from)) >= 0) {
            count++;
            from += value.length();
        }
        return count;
    }
}

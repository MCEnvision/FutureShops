package com.enviouse.futureshops.server.security;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AtmRequestSecurityContractTest {
    @Test
    void everyAtmRequestIsGatedBeforeEscrowOrClaimWork() throws Exception {
        String service = Files.readString(Path.of(
                "src/main/java/com/enviouse/futureshops/server/economy/AtmService.java"));
        String depositService = Files.readString(Path.of(
                "src/main/java/com/enviouse/futureshops/server/escrow/runtime/EscrowCashDepositService.java"));

        assertBefore(service,
                "ServerRequestAction.ATM_DATA",
                "EscrowAtmWithdrawalService.access(player)");
        assertTrue(service.contains(
                "ServerRequestAction.ATM_WITHDRAWAL"));
        assertBefore(service,
                "ServerRequestAction.ATM_CASH_COLLECTION",
                "AtmCashClaimCenter.collect(");
        assertBefore(depositService,
                "ServerRequestAction.ATM_DEPOSIT",
                "withConfigurationReadLease(");
        assertTrue(service.contains("sendDataRejection(player, gate)"));
        assertTrue(service.contains("withdrawThroughGate("));
        assertTrue(service.contains("sendCashCollectionRejection("));
        assertTrue(!service.contains("sendDepositGateRejection("));
    }

    @Test
    void modLifecycleInitializesClearsAndPrunesLogoutBuckets()
            throws Exception {
        String lifecycle = Files.readString(Path.of(
                "src/main/java/com/enviouse/futureshops/Futureshops.java"));

        assertTrue(lifecycle.contains(
                "ServerRequestSecurityManager.initialize(event.getServer())"));
        assertTrue(lifecycle.contains(
                "ServerRequestSecurityManager.shutdown(event.getServer())"));
        assertTrue(lifecycle.contains(
                "ServerRequestSecurityManager.removePlayer(player)"));
    }

    @Test
    void commandAndGuiWithdrawalsShareOneGateAndRefreshBypassesDataBucket()
            throws Exception {
        String service = Files.readString(Path.of(
                "src/main/java/com/enviouse/futureshops/server/economy/AtmService.java"));
        String command = Files.readString(Path.of(
                "src/main/java/com/enviouse/futureshops/command/WithdrawCommand.java"));

        assertTrue(service.contains(
                "private static AtmWithdrawalOutcome withdrawThroughGate("));
        assertTrue(service.contains(
                "public static AtmWithdrawalOutcome withdrawAutomatic("));
        assertTrue(command.contains("AtmService.withdrawAutomatic("));
        assertTrue(!command.contains(
                "EscrowAtmWithdrawalService.withdrawAutomatic("));
        assertTrue(service.contains(
                "private static void sendAuthoritativeData("));
        assertTrue(service.contains(
                "public static void requestData("));
        assertTrue(!service.contains(
                "public static void sendAuthoritativeData("));
        assertTrue(service.contains("result.retryAfterMillis()"));
        assertTrue(service.contains(
                "status == AtmWithdrawalStatus.RATE_LIMITED"));
        assertTrue(service.contains("retryAfterMillis(gate)"));
    }

    @Test
    void atmDepositUsesOneLimiterAndTheFixedEscrowApi()
            throws Exception {
        String service = Files.readString(Path.of(
                "src/main/java/com/enviouse/futureshops/server/economy/AtmService.java"));
        String packet = Files.readString(Path.of(
                "src/main/java/com/enviouse/futureshops/network/packets/C2SAtmDepositPacket.java"));
        String depositService = Files.readString(Path.of(
                "src/main/java/com/enviouse/futureshops/server/escrow/runtime/EscrowCashDepositService.java"));

        assertTrue(!service.contains(
                "ServerRequestAction.ATM_DEPOSIT"));
        assertTrue(depositService.contains(
                "ServerRequestAction.ATM_DEPOSIT"));
        assertTrue(service.contains(
                "EscrowCashDepositService.deposit(player, request)"));
        assertTrue(service.contains(
                "new EscrowCashDepositService.DepositRequest("));
        assertTrue(service.contains(
                "result.replayed()"));
        assertTrue(service.contains(
                "packet.status().equals(\"CANCELLED\")"));
        assertTrue(packet.contains(
                "AtmService.deposit(player, packet.requestId()"));
        assertTrue(packet.contains(
                "packet.currencySignature(), packet.source()"));
        assertTrue(service.contains(
                "currencySignature,"));
        assertTrue(!packet.contains("EscrowCashDepositService.deposit("));
        assertTrue(depositService.indexOf(
                "ServerRequestAction.ATM_DEPOSIT")
                == depositService.lastIndexOf(
                "ServerRequestAction.ATM_DEPOSIT"));
    }

    private static void assertBefore(
            String source,
            String gate,
            String protectedWork
    ) {
        int gateIndex = source.indexOf(gate);
        int workIndex = source.indexOf(protectedWork);
        assertTrue(gateIndex >= 0);
        assertTrue(workIndex >= 0);
        assertTrue(gateIndex < workIndex);
    }
}

package com.enviouse.futureshops.server.security;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerRequestSecurityConfigContractTest {
    @Test
    void escrowTomlDefinesEveryBoundAndAtmActionLimit() throws Exception {
        String config = Files.readString(Path.of(
                "src/main/java/com/enviouse/futureshops/config/EscrowConfig.java"));

        for (String key : List.of(
                "request_security.tracked_key_cap",
                "request_security.idle_retention_seconds",
                "request_security.atm_data.capacity",
                "request_security.atm_data.refill_tokens",
                "request_security.atm_data.refill_period_millis",
                "request_security.atm_withdrawal.capacity",
                "request_security.atm_withdrawal.refill_tokens",
                "request_security.atm_withdrawal.refill_period_millis",
                "request_security.atm_cash_collection.capacity",
                "request_security.atm_cash_collection.refill_tokens",
                "request_security.atm_cash_collection.refill_period_millis",
                "request_security.atm_deposit.capacity",
                "request_security.atm_deposit.refill_tokens",
                "request_security.atm_deposit.refill_period_millis",
                "request_security.pay.capacity",
                "request_security.pay.refill_tokens",
                "request_security.pay.refill_period_millis")) {
            assertTrue(config.contains(key), key);
        }
        assertTrue(config.contains(
                "ServerRequestSecuritySettings.defaults()"));
        assertTrue(config.contains(
                "new ServerRequestSecuritySettings.ActionLimit("));
    }

    @Test
    void lifecycleManagerConsumesTheValidatedEscrowSettings()
            throws Exception {
        String manager = Files.readString(Path.of(
                "src/main/java/com/enviouse/futureshops/server/security/ServerRequestSecurityManager.java"));

        assertTrue(manager.contains(
                "EscrowConfig.settings().requestSecurity()"));
        assertTrue(manager.contains(
                "ServerRequestSecurityPolicy.createLimiter("));
    }
}

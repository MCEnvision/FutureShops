package com.enviouse.futureshopsp.server.economy;

import com.enviouse.futureshopsp.Config;
import net.minecraft.server.MinecraftServer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(EphemeralTestServerProvider.class)
class BalanceManagerSelectionTest {
    @AfterEach
    void cleanup() {
        BalanceManager.clear();
        Config.economyProviderId = null;
    }

    @Test
    void unknownSelectionNeverFallsBackToInternal(MinecraftServer server) {
        Config.economyProviderId = "missing_provider";

        BalanceManager.initialize(server);

        assertFalse(BalanceManager.getProvider() instanceof InternalEconomyProvider);
        assertThrows(EconomyUnavailableException.class,
                () -> BalanceManager.getBalance(UUID.fromString("00000000-0000-0000-0000-000000000003")));
    }
}

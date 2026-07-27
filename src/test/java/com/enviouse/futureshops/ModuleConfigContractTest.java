package com.enviouse.futureshops;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModuleConfigContractTest {
    private static final String CONFIG_ROOT = "src/main/java/com/enviouse/futureshops/config/";

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    @Test
    void mainConfigOwnsModuleEnablementAndNavigation() throws Exception {
        String source = read("src/main/java/com/enviouse/futureshops/Config.java");
        assertTrue(source.contains("modules.bazaar_enabled"));
        assertTrue(source.contains("modules.auction_house_enabled"));
        assertTrue(source.contains("modules.show_module_navigation"));
        assertTrue(source.contains("modules.default_module"));
        assertTrue(source.contains(
                "define(\"modules.bazaar_enabled\", false)"));
        assertTrue(source.contains(
                "define(\"modules.auction_house_enabled\", false)"));
        assertTrue(source.contains(
                "new ModuleSettings(false, false, true, \"shop\")"));
        assertTrue(source.contains("public record ModuleSettings"));
        assertTrue(source.contains("private static volatile ModuleSettings moduleSettings"));
        assertTrue(source.contains("event.getConfig().getSpec() != SPEC"));
        assertFalse(source.contains("modules.escrow_enabled"));
    }

    @Test
    void everyDedicatedSpecIsRegisteredWithAnExplicitFile() throws Exception {
        String source = read("src/main/java/com/enviouse/futureshops/Futureshops.java");
        assertTrue(source.contains("Config.SPEC, \"futureshops-common.toml\""));
        assertTrue(source.contains("EscrowConfig.SPEC, EscrowConfig.FILE_NAME"));
        assertTrue(source.contains("AuctionHouseConfig.SPEC, AuctionHouseConfig.FILE_NAME"));
        assertTrue(source.contains("BazaarConfig.SPEC, BazaarConfig.FILE_NAME"));
        assertTrue(source.contains("ClientConfig.SPEC, \"futureshops-client.toml\""));
        assertTrue(source.contains(
                "event.enqueueWork(ShopDefinitionLoader::prepareStorage)"));
    }

    @Test
    void dedicatedSpecsPublishImmutableValidatedSnapshots() throws Exception {
        for (String file : List.of("EscrowConfig.java", "AuctionHouseConfig.java", "BazaarConfig.java")) {
            String source = read(CONFIG_ROOT + file);
            assertTrue(source.contains("public static final ForgeConfigSpec SPEC"), file);
            assertTrue(source.contains("private static volatile Settings settings"), file);
            assertTrue(source.contains("public record Settings"), file);
            assertTrue(source.contains("event.getConfig().getSpec() != SPEC"), file);
            assertTrue(source.contains("ConfigValidation.require"), file);
        }
    }

    @Test
    void escrowHasNoDisableSwitch() throws Exception {
        String source = read(CONFIG_ROOT + "EscrowConfig.java");
        assertFalse(source.contains("escrow.enabled"));
        assertFalse(source.contains("ESCROW_ENABLED"));
        assertFalse(source.contains("define(\"enabled\""));
        assertTrue(source.contains("persistence.checkpoint_maximum_journal_bytes"));
        assertTrue(source.contains("persistence.checkpoint_maximum_journal_records"));
    }

    @Test
    void physicalCurrencySettingsRepeatTheForeignProtectionWarning() throws Exception {
        String main = read("src/main/java/com/enviouse/futureshops/Config.java");
        String exactWarning = "WARNING. Changing the currency provider from futureshops disables all FutureShops physical currency duplication protection. Currency items from other mods are spawned and accepted without mint ids, checksums, or spent mint tracking.";
        assertTrue(main.contains(exactWarning));
        assertTrue(read(CONFIG_ROOT + "EscrowConfig.java").contains("Config.FOREIGN_CURRENCY_WARNING"));
        assertTrue(read(CONFIG_ROOT + "AuctionHouseConfig.java").contains("Config.FOREIGN_CURRENCY_WARNING"));
        assertTrue(read(CONFIG_ROOT + "BazaarConfig.java").contains("Config.FOREIGN_CURRENCY_WARNING"));
    }
}

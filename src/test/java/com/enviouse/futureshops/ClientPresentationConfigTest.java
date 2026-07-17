package com.enviouse.futureshops;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientPresentationConfigTest {
    @Test
    void defaultsExposeEveryPresentationGroup() {
        ClientConfig.Settings settings = ClientConfig.Settings.defaults();

        assertFalse(settings.presentation().use12HourTime());
        assertTrue(settings.search().predictive());
        assertTrue(settings.presentation().rememberPaymentSource());
        assertTrue(settings.sound().enabled());
        assertTrue(settings.confirmation().requiresPurchaseConfirmation(1L));
    }

    @Test
    void confirmationPolicyHandlesLargeOnlyAndNever() {
        ClientConfig.Confirmation largeOnly = new ClientConfig.Confirmation("large_only", 500L);
        ClientConfig.Confirmation never = new ClientConfig.Confirmation("never", 500L);

        assertFalse(largeOnly.requiresPurchaseConfirmation(499L));
        assertTrue(largeOnly.requiresPurchaseConfirmation(500L));
        assertFalse(never.requiresPurchaseConfirmation(Long.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> never.requiresPurchaseConfirmation(-1L));
    }

    @Test
    void invalidPresentationValuesFailClosed() {
        assertThrows(IllegalArgumentException.class, () -> new ClientConfig.Presentation(
            false, "dense", "medium", true, true, true, true));
        assertThrows(IllegalArgumentException.class, () -> new ClientConfig.Theme(
            "server", true, "not a color"));
        assertThrows(IllegalArgumentException.class, () -> new ClientConfig.Accessibility(
            false, "unknown", false));
        assertThrows(IllegalArgumentException.class, () -> new ClientConfig.Motion(301));
        assertThrows(IllegalArgumentException.class, () -> new ClientConfig.Sound(true, 101));
    }

    @Test
    void forgeConfigSubscriberIsPublicAndReloadable() throws Exception {
        String source = Files.readString(Path.of(
            "src/main/java/com/enviouse/futureshops/ClientConfig.java"));

        assertTrue(source.contains("public static void onConfigEvent(ModConfigEvent event)"));
        assertTrue(source.contains("event.getConfig().getSpec() != SPEC"));
        assertTrue(source.contains("settings = readSettings()"));
    }
}

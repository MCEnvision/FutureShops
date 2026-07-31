package com.enviouse.futureshops;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DependencyMaintenanceBaselineTest {
    @Test
    void dependabotCoversActionsAndGradleWithoutHidingMajorUpdates()
            throws Exception {
        String config = Files.readString(
                Path.of(".github/dependabot.yml"));

        assertTrue(config.contains(
                "package-ecosystem: \"github-actions\""));
        assertTrue(config.contains(
                "package-ecosystem: \"gradle\""));
        assertFalse(config.contains(
                "version-update:semver-major"));
        assertFalse(config.contains("net.neoforged:neoforge"));
    }
}

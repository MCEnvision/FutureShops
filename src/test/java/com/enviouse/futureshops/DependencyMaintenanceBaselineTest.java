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

    @Test
    void packagedJarRejectsLauncherSuppliedLibraries() throws Exception {
        String build = Files.readString(Path.of("build.gradle"));

        assertTrue(build.contains("verifyPackagedDependencyBoundary"));
        assertTrue(build.contains("META-INF/jarjar/metadata.json"));
        assertTrue(build.contains("io/netty/"));
        assertTrue(build.contains("org/apache/commons/"));
        assertTrue(build.contains("com/google/common/"));
        assertTrue(build.contains("org/apache/logging/log4j/"));
        assertTrue(build.contains("org/codehaus/plexus/"));
    }

    @Test
    void qualityPushTargetsTheVersionedForgeBaseAndWrapperIsExecutable()
            throws Exception {
        String workflow = Files.readString(Path.of(
                ".github/workflows/quality.yml"));
        assertTrue(workflow.contains("1.20.1/3.0.0-beta.2"));
        assertTrue(Files.isExecutable(Path.of("gradlew")));
    }
}

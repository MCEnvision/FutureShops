package com.enviouse.futureshops.server.debug;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DebugCommandSourceTest {
    @Test
    void commandIsRegisteredUnderTheExistingFutureShopsRoot() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (!Files.isDirectory(root.resolve("src/main/java"))) {
            root = root.getParent();
        }
        String command = Files.readString(root.resolve(
                "src/main/java/com/enviouse/futureshops/command/DebugCommand.java"));
        String registration = Files.readString(root.resolve(
                "src/main/java/com/enviouse/futureshops/command/ModCommandEvents.java"));
        assertTrue(command.contains("literal(\"futureshops\")"));
        assertTrue(command.contains("literal(\"debug\")"));
        assertTrue(command.contains("literal(\"request\")"));
        assertTrue(command.contains("literal(\"actor\")"));
        assertTrue(command.contains("literal(\"status\")"));
        assertTrue(command.contains("literal(\"off\")"));
        assertTrue(registration.contains("DebugCommand.register"));
    }
}

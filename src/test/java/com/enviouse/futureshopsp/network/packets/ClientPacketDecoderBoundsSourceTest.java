package com.enviouse.futureshopsp.network.packets;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

class ClientPacketDecoderBoundsSourceTest {
    @Test
    void everyServerboundTextFieldHasAnExplicitBound() throws Exception {
        Path root = projectDirectory().resolve(Path.of(
                "src", "main", "java", "com", "enviouse", "futureshopsp", "network", "packets"));
        try (var files = Files.list(root)) {
            for (Path file : files.filter(path -> path.getFileName().toString().startsWith("C2S"))
                    .filter(path -> path.getFileName().toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                assertFalse(source.contains("readUtf()"), file.getFileName().toString());
            }
        }
    }

    private static Path projectDirectory() {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null) {
            if (Files.isDirectory(candidate.resolve(Path.of("src", "main", "java")))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("FutureShops source directory is unavailable");
    }
}

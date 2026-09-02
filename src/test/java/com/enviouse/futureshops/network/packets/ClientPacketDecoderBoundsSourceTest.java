package com.enviouse.futureshops.network.packets;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;

class ClientPacketDecoderBoundsSourceTest {
    @Test
    void everyClientToServerTextDecoderUsesAnExplicitBound() throws IOException {
        Path root = Path.of("src/main/java/com/enviouse/futureshops/network/packets");
        try (Stream<Path> files = Files.list(root)) {
            files.filter(path -> path.getFileName().toString().startsWith("C2S"))
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .forEach(path -> {
                        try {
                            assertFalse(Files.readString(path).contains("readUtf()"),
                                    path.getFileName() + " has an unbounded text decoder");
                        } catch (IOException exception) {
                            throw new java.io.UncheckedIOException(exception);
                        }
                    });
        }
    }
}

package com.enviouse.futureshopsp.catalog;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminShopConfigWriterSafetySourceTest {
    @Test
    void writerUsesBoundedUtf8AndNoFollowPathChecks() throws Exception {
        String source = Files.readString(projectDirectory().resolve(Path.of(
                "src", "main", "java", "com", "enviouse", "futureshopsp",
                "catalog", "AdminShopConfigWriter.java")));
        assertTrue(source.contains("MAX_CONFIG_BYTES"));
        assertTrue(source.contains("safeParentDirectory"));
        assertTrue(source.contains("LinkOption.NOFOLLOW_LINKS"));
        assertTrue(source.contains("CodingErrorAction.REPORT"));
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

package com.enviouse.futureshopsp.api;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopModAPISafetySourceTest {
    @Test
    void physicalCoinAggregationUsesCheckedArithmetic() throws Exception {
        String source = Files.readString(projectDirectory().resolve(Path.of(
                "src", "main", "java", "com", "enviouse", "futureshopsp", "api", "ShopModAPI.java")));

        assertTrue(source.contains("Math.addExact(total"));
        assertTrue(source.contains("Math.multiplyExact(result.denominationMinorUnits(), (long) stack.getCount())"));
        assertTrue(!source.contains("total += result.denominationMinorUnits() * stack.getCount()"));
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

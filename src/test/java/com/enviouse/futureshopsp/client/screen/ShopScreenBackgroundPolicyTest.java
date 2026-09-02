package com.enviouse.futureshopsp.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopScreenBackgroundPolicyTest {
    @Test
    void sharedScreenBaseOwnsTheVanillaBackgroundPolicy() throws Exception {
        assertEquals(AbstractShopScreen.class,
                AbstractShopScreen.class.getDeclaredMethod(
                        "renderBackground", GuiGraphics.class,
                        int.class, int.class, float.class)
                        .getDeclaringClass());
    }

    @Test
    void everyConcreteShopScreenUsesTheSharedBase() throws IOException {
        Path screenDirectory = projectDirectory().resolve(Path.of(
                "src", "main", "java", "com", "enviouse",
                "futureshopsp", "client", "screen"));
        List<Path> screens;
        try (var paths = Files.list(screenDirectory)) {
            screens = paths
                    .filter(path -> path.getFileName().toString()
                            .endsWith("Screen.java"))
                    .filter(path -> !path.getFileName().toString()
                            .equals("AbstractShopScreen.java"))
                    .filter(ShopScreenBackgroundPolicyTest::isShopScreen)
                    .sorted()
                    .toList();
        }

        assertFalse(screens.isEmpty());
        for (Path screen : screens) {
            String source = Files.readString(screen);
            assertTrue(source.contains("extends AbstractShopScreen"),
                    () -> screen + " bypasses the shared background policy");
            assertFalse(source.contains("extends Screen"),
                    () -> screen + " directly inherits the vanilla blur pass");
        }
    }

    private static boolean isShopScreen(Path path) {
        try {
            return Files.readString(path).contains(
                    "implements ShopScreenMarker");
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Path projectDirectory() {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null) {
            if (Files.isDirectory(candidate.resolve(
                    Path.of("src", "main", "java")))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException(
                "FutureShops source directory is unavailable");
    }
}

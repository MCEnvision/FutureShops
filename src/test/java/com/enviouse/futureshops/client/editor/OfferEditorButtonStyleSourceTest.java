package com.enviouse.futureshops.client.editor;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfferEditorButtonStyleSourceTest {
    private static final Path CLIENT_SOURCE = Path.of(
            "src/main/java/com/enviouse/futureshops/client");
    private static final Pattern VANILLA_BUILDER = Pattern.compile(
            "(?<!FutureShops)Button\\.builder\\(");
    private static final Pattern VANILLA_CONSTRUCTOR = Pattern.compile(
            "new\\s+Button\\s*\\(");

    @Test
    void clientScreensDoNotConstructVanillaButtons() throws IOException {
        try (Stream<Path> files = Files.walk(CLIENT_SOURCE)) {
            List<Path> javaFiles = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList();
            for (Path file : javaFiles) {
                String source = Files.readString(file);
                assertFalse(VANILLA_BUILDER.matcher(source).find(),
                        () -> "vanilla button builder in " + file);
                assertFalse(VANILLA_CONSTRUCTOR.matcher(source).find(),
                        () -> "vanilla button constructor in " + file);
            }
        }
    }

    @Test
    void editorButtonsUseTheFutureShopsRenderer() throws IOException {
        String button = readScreen("FutureShopsButton.java");
        assertTrue(button.contains("extends Button"));
        assertTrue(button.contains("ShopUiUtil.button(graphics"));
        assertTrue(button.contains("ButtonStyle.SECONDARY"));
        assertTrue(button.contains("active && isFocused()"));

        for (String screen : List.of(
                "AdminOfferEditorScreen.java",
                "OfferEditorItemPickerScreen.java",
                "OfferEditorCategoryPickerScreen.java",
                "OfferEditorBundleComparisonPickerScreen.java")) {
            assertTrue(readScreen(screen).contains(
                    "FutureShopsButton.styled("), screen);
        }
    }

    @Test
    void templateChooserReservesTheSummaryPanel() throws IOException {
        String source = readScreen("AdminOfferEditorScreen.java");
        assertTrue(source.contains(
                "contentWidth = width - margin * 2 - summaryWidth"));
        assertTrue(source.contains(
                "- (summaryWidth == 0 ? 0 : 8);"));
        assertTrue(source.contains(
                "(contentWidth - gap * (columns - 1)) / columns"));
    }

    @Test
    void generalIconPreviewHasAFramedSlotOutsidePickerButtons()
            throws IOException {
        String source = readScreen("AdminOfferEditorScreen.java");
        assertTrue(source.contains(
                "fieldX + fieldWidth + previewSize + 6"));
        assertTrue(source.contains(
                "previewY + previewSize + 4 <= footerTop()"));
        assertTrue(source.contains(
                "int previewX = fieldX + fieldWidth + 6;"));
        assertTrue(source.contains(
                "ShopUiUtil.renderCard(graphics, previewX, previewY,"));
        assertFalse(source.contains(
                "sectionY(196), mouseX, mouseY);"));
    }

    @Test
    void narrowEditorClipsScrollableContentAboveFooter()
            throws IOException {
        String source = readScreen("AdminOfferEditorScreen.java");
        assertTrue(source.contains(
                "abstractWidget.visible = contentWidgetVisible("));
        assertTrue(source.contains(
                "graphics.enableScissor(contentLeft, contentTop,"));
        assertTrue(source.contains(
                "if (!field.visible) {"));
    }

    private static String readScreen(String file) throws IOException {
        return Files.readString(Path.of(
                "src/main/java/com/enviouse/futureshops/"
                        + "client/screen/" + file));
    }
}

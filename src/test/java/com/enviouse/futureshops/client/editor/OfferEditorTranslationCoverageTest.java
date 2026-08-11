package com.enviouse.futureshops.client.editor;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OfferEditorTranslationCoverageTest {
    private static final Pattern ISSUE_CODE =
            Pattern.compile("\"(offer\\.[a-z0-9_.]+)\"");
    @Test
    void everyRegisteredControlAndFieldHasLocalizedHelp()
            throws IOException {
        JsonObject language = JsonParser.parseString(Files.readString(
                Path.of("src/main/resources/assets/futureshops/lang/"
                        + "en_us.json"))).getAsJsonObject();

        for (String key
                : OfferEditorControlRegistry.requiredTranslationKeys()) {
            assertTrue(language.has(key),
                    () -> "Missing offer editor translation " + key);
            assertTrue(!language.get(key).getAsString().isBlank(),
                    () -> "Blank offer editor translation " + key);
        }
    }

    @Test
    void everyStructuredEditorIssueHasLocalizedText()
            throws IOException {
        JsonObject language = JsonParser.parseString(Files.readString(
                Path.of("src/main/resources/assets/futureshops/lang/"
                        + "en_us.json"))).getAsJsonObject();
        List<Path> sources = List.of(
                Path.of("src/main/java/com/enviouse/futureshops/catalog/"
                        + "offer/ServerShopOfferValidator.java"),
                Path.of("src/main/java/com/enviouse/futureshops/catalog/"
                        + "offer/ServerShopOfferCatalogValidator.java"),
                Path.of("src/main/java/com/enviouse/futureshops/catalog/"
                        + "AdminShopOfferConfigWriter.java"),
                Path.of("src/main/java/com/enviouse/futureshops/server/shop/"
                        + "PlayerShopOfferEditorService.java"),
                Path.of("src/main/java/com/enviouse/futureshops/network/"
                        + "packets/S2CAdminOfferSaveResultPacket.java"));
        for (Path source : sources) {
            Matcher matcher = ISSUE_CODE.matcher(
                    Files.readString(source));
            while (matcher.find()) {
                String key = "gui.futureshops.offer_editor.validation."
                        + matcher.group(1);
                assertTrue(language.has(key),
                        () -> "Missing offer editor issue text " + key);
            }
        }
        assertTrue(language.has(
                "gui.futureshops.offer_editor.validation."
                        + "offer.field.invalid_number"));
    }
}

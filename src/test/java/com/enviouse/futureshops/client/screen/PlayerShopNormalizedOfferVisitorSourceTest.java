package com.enviouse.futureshops.client.screen;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerShopNormalizedOfferVisitorSourceTest {
    private static final Path SCREEN = Path.of(
            "src/main/java/com/enviouse/futureshops/client/screen/"
                    + "PlayerShopBlockScreen.java");
    private static final Path CHOOSER = Path.of(
            "src/main/java/com/enviouse/futureshops/client/screen/"
                    + "PlayerShopOfferOptionScreen.java");

    @Test
    void normalizedVisitorActionsUseTheTypedOfferPacket() throws Exception {
        String screen = readSource(SCREEN);
        assertTrue(screen.contains(
                "PlayerShopClientState.selectedNormalizedOffer()"));
        assertTrue(screen.contains(
                "new C2SPlayerShopOfferPacket("));
        assertTrue(screen.contains(
                "normalized.clientListingIndex()"));
        assertTrue(screen.contains("offer.revision()"));
        assertTrue(screen.contains(
                "OfferAction.ACQUIRE_FROM_SHOP"));
        assertTrue(screen.contains("OfferAction.SELL_TO_SHOP"));
    }

    @Test
    void chooserMakesAlternativesAndCompoundInputsVisible()
            throws Exception {
        String chooser = readSource(CHOOSER);
        assertTrue(chooser.contains(
                "\"gui.futureshops.offer.or\""));
        assertTrue(chooser.contains(
                "ServerShopOfferPresentation.acquireSummary("));
        assertTrue(chooser.contains(
                "ServerShopOfferPresentation.sellSummary("));
        assertTrue(chooser.contains(
                "hasComponents(\n"
                        + "                option.itemCosts(), quantity)"));
        assertTrue(chooser.contains(
                "hasComponents(\n"
                        + "                option.itemInputs(), quantity)"));
        assertTrue(chooser.contains(
                "option.free()"));
    }

    @Test
    void freeAndBarterSkipThePaymentChooser() throws Exception {
        String screen = readSource(SCREEN);
        assertTrue(screen.contains(
                "if (option.moneyCostPresent())"));
        assertTrue(screen.contains(
                "quantity, Optional.empty())"));
        assertTrue(screen.contains(
                "Optional.of(paymentSource)"));
    }

    private static String readSource(Path path) throws Exception {
        return Files.readString(path)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }
}

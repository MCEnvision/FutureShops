package com.enviouse.futureshops.client.editor;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminOfferEditorPhaseFourSourceTest {
    @Test
    void editorWiresPersistentSearchPickersToEveryItemRole()
            throws IOException {
        String source = read("AdminOfferEditorScreen.java");
        String comparisonPicker = read(
                "OfferEditorBundleComparisonPickerScreen.java");

        assertTrue(source.contains(
                "new OfferEditorCategoryPickerScreen"));
        assertTrue(source.contains(
                "new OfferEditorBundleComparisonPickerScreen"));
        assertTrue(source.contains(
                "OfferEditorItemPickerScreen.Source.INVENTORY"));
        assertTrue(source.contains(
                "OfferEditorItemPickerScreen.Source.REGISTRY"));
        assertTrue(source.contains("acceptIcon"));
        assertTrue(source.contains("acceptOutput"));
        assertTrue(source.contains("acceptAcquireCost"));
        assertTrue(source.contains("acceptSellInput"));
        assertTrue(source.contains("flushFields();"));
        assertTrue(source.contains("draft.acceptFieldValue"));
        assertTrue(source.contains("draft.clearFieldValues"));
        assertTrue(source.contains("firstInvalidFieldPath"));
        assertTrue(source.contains("blockStructuralEdit"));
        assertTrue(comparisonPicker.contains(
                "ShopClientState.getCatalogOffers()"));
        assertTrue(comparisonPicker.contains(
                "standalone.exactNbt()"));
        assertTrue(comparisonPicker.contains(
                "option.moneyCostPresent()"));
        assertTrue(comparisonPicker.contains(
                "option.hasItemCosts()"));
    }

    @Test
    void addingAListingOpensTheContextualQuickAddGrid()
            throws IOException {
        String shop = read("ShopMainScreen.java");
        String picker = read("AdminItemPickerScreen.java");

        assertTrue(shop.contains(
                "AdminItemPickerScreen.forQuickAdd"));
        assertTrue(shop.contains("quickAddMode()"));
        assertTrue(picker.contains(
                "AdminOfferEditorScreen.createQuickAdd"));
        assertTrue(picker.contains("sendQuickAdd"));
        assertTrue(picker.contains("openSimpleEditor"));
    }

    @Test
    void everyComponentRoleSupportsDirectEditRemoveAndReorder()
            throws IOException {
        String source = read("AdminOfferEditorScreen.java");

        assertTrue(source.contains("updateOutputItem"));
        assertTrue(source.contains("updateOutputCount"));
        assertTrue(source.contains("selectedOutputCount"));
        assertTrue(source.contains("moveOutput"));
        assertTrue(source.contains("confirmRemoveOutput"));
        assertTrue(source.contains("updateAcquireCostItem"));
        assertTrue(source.contains("updateAcquireCostCount"));
        assertTrue(source.contains("selectedAcquireCostCount"));
        assertTrue(source.contains("moveAcquireCost"));
        assertTrue(source.contains("confirmRemoveAcquireCost"));
        assertTrue(source.contains("updateSellInputItem"));
        assertTrue(source.contains("updateSellInputCount"));
        assertTrue(source.contains("selectedSellInputCount"));
        assertTrue(source.contains("moveSellInput"));
        assertTrue(source.contains("confirmRemoveSellInput"));
        assertTrue(source.contains("buildCountStepper"));
        assertTrue(source.contains("Math.addExact"));
        assertTrue(source.contains("mutateAndRebuild"));
    }

    @Test
    void previewSharesReadOnlyProjectionWithVisitorScreens()
            throws IOException {
        String editor = read("AdminOfferEditorScreen.java");
        String detail = read("ItemDetailScreen.java");
        String chooser = read("ServerShopOfferOptionScreen.java");

        assertTrue(editor.contains(
                "ServerShopOfferPresentation.project"));
        assertTrue(editor.contains("PreviewMode.BROWSE_CARD"));
        assertTrue(editor.contains("PreviewMode.DETAIL"));
        assertTrue(editor.contains(
                "PreviewMode.OPTION_CHOOSER"));
        assertTrue(detail.contains(
                "ServerShopOfferPresentation.acquireCostSummary"));
        assertTrue(detail.contains(
                "ServerShopOfferPresentation.sellPayoutSummary"));
        assertTrue(chooser.contains(
                "ServerShopOfferPresentation"));
        assertTrue(chooser.contains(".acquireSummary"));
        assertTrue(chooser.contains(".sellSummary"));
    }

    @Test
    void validationAndKeyboardNavigationAreExplicit()
            throws IOException {
        String source = read("AdminOfferEditorScreen.java");

        assertTrue(source.contains("sectionButtonLabel"));
        assertTrue(source.contains("buildValidationNavigation"));
        assertTrue(source.contains("focusIssue"));
        assertTrue(source.contains("ShopColors.ERROR"));
        assertTrue(source.contains("GLFW.GLFW_KEY_F"));
        assertTrue(source.contains("GLFW.GLFW_KEY_S"));
        assertTrue(source.contains("GLFW.GLFW_KEY_ENTER"));
        assertTrue(source.contains("GLFW.GLFW_KEY_ESCAPE"));
        assertTrue(source.contains("Screen.hasAltDown"));
    }

    @Test
    void staleConflictHasExplicitReviewAndReloadActions()
            throws IOException {
        String source = read("AdminOfferEditorScreen.java");

        assertTrue(source.contains("reviewStaleChanges"));
        assertTrue(source.contains("review_changes"));
        assertTrue(source.contains("renderStaleReview"));
        assertTrue(source.contains("OfferEditorStaleReview.compare"));
        assertTrue(source.contains("back_to_local_draft"));
        assertTrue(source.contains("confirmReloadServer"));
        assertTrue(source.contains(
                "draft.baseline().revision()"));
    }

    @Test
    void selectedSectionResetUsesNamedConfirmation()
            throws IOException {
        String source = read("AdminOfferEditorScreen.java");

        assertTrue(source.contains("confirmResetSection"));
        assertTrue(source.contains("reset_section_title"));
        assertTrue(source.contains("draft.resetSection(section)"));
        assertTrue(source.contains("help(\"reset_section\")"));
    }

    @Test
    void commonOffersOpenInTheGuidedFourStepEditor()
            throws IOException {
        String source = read("AdminOfferEditorScreen.java");

        assertTrue(source.contains("if (!advancedMode)"));
        assertTrue(source.contains("initSimpleEditor"));
        assertTrue(source.contains("BASICS(\"basics\")"));
        assertTrue(source.contains("ITEMS(\"items\")"));
        assertTrue(source.contains("TRADE(\"trade\")"));
        assertTrue(source.contains("REVIEW(\"review\")"));
        assertTrue(source.contains(
                "OfferEditorTemplates.Template.FREE"));
        assertTrue(source.contains(
                "OfferEditorTemplates.Template.SELL"));
        assertTrue(source.contains(
                "OfferEditorTemplates.Template.BUNDLE"));
        assertTrue(source.contains("OfferEditorSimpleMode.apply"));
        assertTrue(source.contains("openSimpleSearch"));
        assertTrue(source.contains("maximumSimpleScroll"));
        assertTrue(source.contains("simpleViewportTop"));
    }

    @Test
    void editorFieldsReserveLabelSpace()
            throws IOException {
        String source = read("AdminOfferEditorScreen.java");

        assertTrue(source.contains("fieldWidth, 18,"));
        assertTrue(source.contains(
                "\"description\", listing.description(), y + 42,"));
        assertTrue(source.contains("simpleFieldLabels.getOrDefault"));
    }

    @Test
    void newOfferIsAlwaysAvailableFromTheEditToolbar()
            throws IOException {
        String source = read("ShopMainScreen.java");

        assertTrue(source.contains(
                "gui.futureshops.admin_edit.new_offer"));
        assertTrue(source.contains(
                "AdminOfferEditorScreen.create(this)"));
    }

    private static String read(String file) throws IOException {
        return Files.readString(Path.of(
                "src/main/java/com/enviouse/futureshops/"
                        + "client/screen/" + file));
    }
}

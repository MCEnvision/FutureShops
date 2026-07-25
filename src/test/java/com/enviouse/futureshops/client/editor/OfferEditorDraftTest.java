package com.enviouse.futureshops.client.editor;

import com.enviouse.futureshops.catalog.offer.AcquireOfferOption;
import com.enviouse.futureshops.catalog.offer.OfferItemComponent;
import com.enviouse.futureshops.catalog.offer.OfferLimitPolicy;
import com.enviouse.futureshops.catalog.offer.OfferSchedule;
import com.enviouse.futureshops.catalog.offer.OfferStockPolicy;
import com.enviouse.futureshops.catalog.offer.OfferValidationIssue;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferListing;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferRevision;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfferEditorDraftTest {
    @Test
    void unchangedFieldFlushDoesNotCreateUnsavedChanges() {
        ServerShopOfferListing baseline = listing();
        OfferEditorDraft draft = new OfferEditorDraft(baseline);

        draft.update("displayName", current -> copyName(
                current, current.displayName()));

        assertFalse(draft.dirty());
        assertTrue(draft.dirtyPaths().isEmpty());
    }

    @Test
    void invalidNumberTextSurvivesRebuildAndBlocksSave() {
        OfferEditorDraft draft = new OfferEditorDraft(listing());

        draft.recordFieldValue(
                "limits.maximumPerRequest", "", true);

        assertEquals("", draft.fieldValue(
                "limits.maximumPerRequest", "2304"));
        assertFalse(draft.valid());
        assertTrue(draft.issues().stream().anyMatch(issue ->
                issue.path().equals("limits.maximumPerRequest")
                        && issue.code().equals(
                        "offer.field.invalid_number")));
    }

    @Test
    void rejectedSaveKeepsDraftAndFocusesExactSection() {
        OfferEditorDraft draft = new OfferEditorDraft(listing());
        draft.update("acquireOptions.0.label", current ->
                replaceOptionLabel(current, "Changed"));

        draft.reject(List.of(new OfferValidationIssue(
                OfferValidationIssue.Severity.ERROR,
                "acquireOptions.0.moneyCost",
                "offer.money.not_positive")));

        assertEquals("Changed",
                draft.candidate().acquireOptions().get(0).label());
        assertEquals(OfferEditorDraft.Section.GET_OPTIONS,
                draft.section());
        assertEquals("acquireOptions.0.moneyCost",
                draft.focusedPath());
        assertFalse(draft.serverIssues().isEmpty());
    }

    @Test
    void acknowledgementBecomesNewRevertBaseline() {
        OfferEditorDraft draft = new OfferEditorDraft(listing());
        ServerShopOfferListing acknowledged =
                copyName(listing(), "Acknowledged");
        draft.acknowledge(acknowledged);
        draft.update("displayName", current ->
                copyName(current, "Unsaved"));

        draft.revert();

        assertEquals("Acknowledged",
                draft.candidate().displayName());
        assertFalse(draft.dirty());
        assertTrue(draft.serverIssues().isEmpty());
    }

    @Test
    void resetSectionPreservesChangesInOtherSections() {
        ServerShopOfferListing baseline = listing();
        OfferEditorDraft draft = new OfferEditorDraft(baseline);
        draft.update("displayName", current ->
                copyName(current, "Unsaved name"));
        draft.update("outputs.0.count", current ->
                replaceOutputCount(current, 3));

        assertTrue(draft.sectionDirty(
                OfferEditorDraft.Section.GENERAL));
        assertTrue(draft.sectionDirty(
                OfferEditorDraft.Section.OUTPUTS));

        draft.resetSection(OfferEditorDraft.Section.GENERAL);

        assertEquals(baseline.displayName(),
                draft.candidate().displayName());
        assertEquals(3, draft.candidate().outputs().get(0).count());
        assertFalse(draft.sectionDirty(
                OfferEditorDraft.Section.GENERAL));
        assertTrue(draft.sectionDirty(
                OfferEditorDraft.Section.OUTPUTS));
        assertTrue(draft.dirty());
    }

    @Test
    void pickerValueReplacesStaleRawTextWithoutClearingOtherFields() {
        OfferEditorDraft draft = new OfferEditorDraft(listing());
        draft.recordFieldValue("outputs.0.itemId",
                "bad:item", false);
        draft.recordFieldValue("description",
                "preserved", false);

        draft.acceptFieldValue("outputs.0.itemId",
                "minecraft:diamond");

        assertEquals("minecraft:diamond", draft.fieldValue(
                "outputs.0.itemId", ""));
        assertEquals("preserved", draft.fieldValue(
                "description", ""));
    }

    @Test
    void componentReorderCanClearOnlyItsIndexedRawValues() {
        OfferEditorDraft draft = new OfferEditorDraft(listing());
        draft.recordFieldValue("outputs.0.itemId",
                "minecraft:diamond", false);
        draft.recordFieldValue("outputs.0.count", "", true);
        draft.recordFieldValue("description",
                "preserved", false);

        draft.clearFieldValues("outputs");

        assertEquals("minecraft:stone", draft.fieldValue(
                "outputs.0.itemId", "minecraft:stone"));
        assertEquals("1", draft.fieldValue(
                "outputs.0.count", "1"));
        assertEquals("preserved", draft.fieldValue(
                "description", ""));
        assertTrue(draft.valid());
    }

    @Test
    void structuralEditsCanDetectInvalidRawValuesBeforeClearing() {
        OfferEditorDraft draft = new OfferEditorDraft(listing());
        draft.recordFieldValue("outputs.0.count", "", true);
        draft.recordFieldValue(
                "acquireOptions.0.moneyCost", "100", true);

        assertEquals("outputs.0.count",
                draft.firstInvalidFieldPath("outputs")
                        .orElseThrow());
        assertTrue(draft.firstInvalidFieldPath(
                "acquireOptions").isEmpty());
        assertEquals("",
                draft.fieldValue("outputs.0.count", "1"));
    }

    private static ServerShopOfferListing listing() {
        ServerShopOfferListing listing = new ServerShopOfferListing(
                "test_offer", 0L, "Test", "", "all",
                "minecraft:stone", "", true, 0L, "",
                List.of(new OfferItemComponent(
                        "output", "minecraft:stone", 1, "")),
                List.of(AcquireOfferOption.money("money", 100L)),
                List.of(), OfferStockPolicy.unlimited(),
                OfferLimitPolicy.defaults(), OfferSchedule.always(),
                List.of());
        return listing.withRevision(
                ServerShopOfferRevision.compute(listing));
    }

    private static ServerShopOfferListing copyName(
            ServerShopOfferListing source,
            String name
    ) {
        return new ServerShopOfferListing(
                source.listingId(), source.revision(), name,
                source.description(), source.categoryId(),
                source.iconItemId(), source.iconNbt(), source.active(),
                source.expiresAtEpoch(), source.permissionNode(),
                source.outputs(), source.acquireOptions(),
                source.sellOptions(), source.stockPolicy(),
                source.limits(), source.schedule(),
                source.bundleComparisons());
    }

    private static ServerShopOfferListing replaceOptionLabel(
            ServerShopOfferListing source,
            String label
    ) {
        AcquireOfferOption old = source.acquireOptions().get(0);
        AcquireOfferOption changed = new AcquireOfferOption(
                old.optionId(), label, old.free(),
                old.moneyCostPresent(), old.moneyCostMinorUnits(),
                old.itemCosts(), old.outputMultiplier(), old.limits(),
                old.schedule(), old.permissionNode());
        return new ServerShopOfferListing(
                source.listingId(), source.revision(),
                source.displayName(), source.description(),
                source.categoryId(), source.iconItemId(),
                source.iconNbt(), source.active(),
                source.expiresAtEpoch(), source.permissionNode(),
                source.outputs(), List.of(changed),
                source.sellOptions(), source.stockPolicy(),
                source.limits(), source.schedule(),
                source.bundleComparisons());
    }

    private static ServerShopOfferListing replaceOutputCount(
            ServerShopOfferListing source,
            int count
    ) {
        OfferItemComponent output = source.outputs().get(0);
        OfferItemComponent changed = new OfferItemComponent(
                output.componentId(), output.itemId(),
                count, output.exactNbt());
        return new ServerShopOfferListing(
                source.listingId(), source.revision(),
                source.displayName(), source.description(),
                source.categoryId(), source.iconItemId(),
                source.iconNbt(), source.active(),
                source.expiresAtEpoch(), source.permissionNode(),
                List.of(changed), source.acquireOptions(),
                source.sellOptions(), source.stockPolicy(),
                source.limits(), source.schedule(),
                source.bundleComparisons());
    }
}

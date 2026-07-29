package com.enviouse.futureshops.catalog;

import com.enviouse.futureshops.catalog.offer.AcquireOfferOption;
import com.enviouse.futureshops.catalog.offer.OfferItemComponent;
import com.enviouse.futureshops.catalog.offer.OfferLimitPolicy;
import com.enviouse.futureshops.catalog.offer.OfferSchedule;
import com.enviouse.futureshops.catalog.offer.OfferStockPolicy;
import com.enviouse.futureshops.catalog.offer.OfferValidationIssue;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferListing;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferRevision;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdminShopOfferDurableReceiptTest {
    @Test
    void createUsesCandidateIdentityForDurableRecovery() {
        ServerShopOfferListing listing = listing("new_offer");
        assertEquals("new_offer",
                AdminShopOfferConfigWriter.mutationTargetId(
                        AdminShopOfferConfigWriter.Operation.CREATE,
                        "", listing));
        assertEquals("existing_offer",
                AdminShopOfferConfigWriter.mutationTargetId(
                        AdminShopOfferConfigWriter.Operation.UPDATE,
                        "existing_offer", listing));
    }

    @Test
    void catalogValidationIssuesAreScopedToTheEditedListing() {
        List<OfferValidationIssue> scoped =
                AdminShopOfferConfigWriter.editorIssues(List.of(
                        new OfferValidationIssue(
                                OfferValidationIssue.Severity.ERROR,
                                "listings.2.outputs.0.componentId",
                                "offer.identifier.invalid"),
                        new OfferValidationIssue(
                                OfferValidationIssue.Severity.ERROR,
                                "listings.0.listingId",
                                "offer.identifier.invalid")), 2);

        assertEquals("outputs.0.componentId",
                scoped.get(0).path());
        assertEquals("catalog", scoped.get(1).path());
        assertEquals("offer.catalog.existing_invalid",
                scoped.get(1).code());
    }

    @Test
    void durableReceiptRoundTripsTheAcknowledgedSnapshot() {
        JsonObject root = new JsonObject();
        ServerShopOfferListing listing = listing();
        AdminShopOfferConfigWriter.DurableReceipt receipt =
                new AdminShopOfferConfigWriter.DurableReceipt(
                        UUID.randomUUID(), UUID.randomUUID(),
                        "a".repeat(64), listing.revision(),
                        Optional.of(listing));

        AdminShopOfferConfigWriter.appendDurableReceipt(root, receipt);

        assertEquals(receipt,
                AdminShopOfferConfigWriter.findDurableReceipt(
                        root, receipt.requestId()).orElseThrow());
        assertEquals(receipt.result().snapshot(),
                AdminShopOfferConfigWriter.findDurableReceipt(
                        root, receipt.requestId()).orElseThrow()
                        .result().snapshot());
    }

    @Test
    void requestReuseWithDifferentEvidenceFailsClosed() {
        JsonObject root = new JsonObject();
        UUID requestId = UUID.randomUUID();
        AdminShopOfferConfigWriter.appendDurableReceipt(
                root, new AdminShopOfferConfigWriter.DurableReceipt(
                        requestId, UUID.randomUUID(), "b".repeat(64),
                        0L, Optional.empty()));

        assertThrows(IllegalStateException.class,
                () -> AdminShopOfferConfigWriter.appendDurableReceipt(
                        root,
                        new AdminShopOfferConfigWriter.DurableReceipt(
                                requestId, UUID.randomUUID(),
                                "c".repeat(64), 0L,
                                Optional.empty())));
    }

    @Test
    void malformedOrDuplicatedRowsFailClosed() {
        UUID requestId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        JsonObject row = new JsonObject();
        row.addProperty("requestId", requestId.toString());
        row.addProperty("playerId", playerId.toString());
        row.addProperty("requestFingerprint", "d".repeat(64));
        row.addProperty("revision", 0L);
        JsonArray rows = new JsonArray();
        rows.add(row);
        rows.add(row.deepCopy());
        JsonObject duplicated = new JsonObject();
        duplicated.add("offerMutationReceipts", rows);

        assertThrows(IllegalStateException.class,
                () -> AdminShopOfferConfigWriter.findDurableReceipt(
                        duplicated, requestId));

        JsonObject malformed = new JsonObject();
        JsonArray malformedRows = new JsonArray();
        malformedRows.add("invalid");
        malformed.add("offerMutationReceipts", malformedRows);
        assertThrows(IllegalArgumentException.class,
                () -> AdminShopOfferConfigWriter.findDurableReceipt(
                        malformed, requestId));
    }

    @Test
    void durableReceiptHistoryIsBounded() {
        JsonObject root = new JsonObject();
        UUID first = null;
        UUID last = null;
        for (int index = 0; index < 140; index++) {
            UUID requestId = UUID.randomUUID();
            if (index == 0) {
                first = requestId;
            }
            last = requestId;
            AdminShopOfferConfigWriter.appendDurableReceipt(
                    root,
                    new AdminShopOfferConfigWriter.DurableReceipt(
                            requestId, UUID.randomUUID(),
                            "%064x".formatted(index), 0L,
                            Optional.empty()));
        }

        assertEquals(Optional.empty(),
                AdminShopOfferConfigWriter.findDurableReceipt(root, first));
        assertEquals(last,
                AdminShopOfferConfigWriter.findDurableReceipt(root, last)
                        .orElseThrow().requestId());
        assertEquals(128,
                root.getAsJsonArray("offerMutationReceipts").size());
    }

    @Test
    void pendingReceiptCanOnlyBecomeTheSameMutationOutcome() {
        JsonObject root = new JsonObject();
        UUID requestId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        ServerShopOfferListing listing = listing();
        AdminShopOfferConfigWriter.DurableReceipt pending =
                new AdminShopOfferConfigWriter.DurableReceipt(
                        requestId, playerId, "e".repeat(64),
                        listing.revision(), Optional.of(listing),
                        "admin", listing.listingId(),
                        AdminShopOfferConfigWriter.Operation.UPDATE,
                        AdminShopOfferConfigWriter.Status.UNAVAILABLE);
        AdminShopOfferConfigWriter.appendDurableReceipt(root, pending);
        AdminShopOfferConfigWriter.DurableReceipt completed =
                new AdminShopOfferConfigWriter.DurableReceipt(
                        requestId, playerId, "e".repeat(64),
                        listing.revision(), Optional.of(listing),
                        "admin", listing.listingId(),
                        AdminShopOfferConfigWriter.Operation.UPDATE,
                        AdminShopOfferConfigWriter.Status.SUCCESS);

        AdminShopOfferConfigWriter.replaceDurableReceipt(
                root, completed);

        assertEquals(AdminShopOfferConfigWriter.Status.SUCCESS,
                AdminShopOfferConfigWriter.findDurableReceipt(
                        root, requestId).orElseThrow().status());
        assertThrows(IllegalStateException.class,
                () -> AdminShopOfferConfigWriter
                        .replaceDurableReceipt(
                                root,
                                new AdminShopOfferConfigWriter
                                        .DurableReceipt(
                                        requestId, playerId,
                                        "f".repeat(64),
                                        listing.revision(),
                                        Optional.of(listing),
                                        "admin",
                                        listing.listingId(),
                                        AdminShopOfferConfigWriter
                                                .Operation.UPDATE,
                                        AdminShopOfferConfigWriter
                                                .Status.SUCCESS)));
    }

    private static ServerShopOfferListing listing() {
        return listing("diamond");
    }

    private static ServerShopOfferListing listing(String listingId) {
        ServerShopOfferListing unversioned =
                new ServerShopOfferListing(
                listingId, 0L, "Diamond", "", "all",
                "minecraft:diamond", "", true, 0L, "",
                List.of(new OfferItemComponent(
                        "output", "minecraft:diamond", 1, "")),
                List.of(AcquireOfferOption.money("money", 100L)),
                List.of(), OfferStockPolicy.unlimited(),
                OfferLimitPolicy.defaults(), OfferSchedule.always(),
                List.of());
        return unversioned.withRevision(
                ServerShopOfferRevision.compute(unversioned));
    }
}

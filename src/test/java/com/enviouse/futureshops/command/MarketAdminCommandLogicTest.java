package com.enviouse.futureshops.command;

import com.enviouse.futureshops.server.market.auction.AuctionListingState;
import com.enviouse.futureshops.server.market.bazaar.BazaarOrderState;
import com.enviouse.futureshops.server.market.bazaar.BazaarProductStatus;
import com.enviouse.futureshops.server.market.control.MarketControlActor;
import com.enviouse.futureshops.server.market.control.MarketControlModule;
import com.enviouse.futureshops.server.market.control.MarketModuleControl;
import com.enviouse.futureshops.server.market.control.MarketModuleStatus;
import com.enviouse.futureshops.server.escrow.runtime.EscrowRuntimeState;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure decision logic of {@code /marketadmin} (MarketAdminCommand.Logic): the two-step confirm
 * window, reason bounding, idempotent request-id derivations, module/verb parsing, and the
 * status-formatting decisions — plus lang-key pinning for every dynamically composed key suffix
 * the command emits (the repo-wide lang coverage test can only see literal keys).
 */
class MarketAdminCommandLogicTest {

    private static final Path EN_US =
            Path.of("src/main/resources/assets/futureshops/lang/en_us.json");

    // ── confirm window ──────────────────────────────────────────────────────

    @Test
    void confirmArmsWhenNothingIsPending() {
        assertEquals(MarketAdminCommand.Logic.ConfirmDecision.ARM,
                MarketAdminCommand.Logic.confirmDecision(null, "fp", 1_000L));
    }

    @Test
    void confirmExecutesOnExactRerunInsideTheWindow() {
        var pending = new MarketAdminCommand.Logic.PendingConfirm("fp", 10_000L);
        assertEquals(MarketAdminCommand.Logic.ConfirmDecision.EXECUTE,
                MarketAdminCommand.Logic.confirmDecision(pending, "fp", 10_001L));
        // Boundary: exactly the window end still executes.
        assertEquals(MarketAdminCommand.Logic.ConfirmDecision.EXECUTE,
                MarketAdminCommand.Logic.confirmDecision(pending, "fp",
                        10_000L + MarketAdminCommand.Logic.CONFIRM_WINDOW_MILLIS));
        // Immediately re-running (same millisecond) also executes.
        assertEquals(MarketAdminCommand.Logic.ConfirmDecision.EXECUTE,
                MarketAdminCommand.Logic.confirmDecision(pending, "fp", 10_000L));
    }

    @Test
    void confirmReArmsWhenExpiredMismatchedOrClockRanBackwards() {
        var pending = new MarketAdminCommand.Logic.PendingConfirm("fp", 10_000L);
        assertEquals(MarketAdminCommand.Logic.ConfirmDecision.ARM,
                MarketAdminCommand.Logic.confirmDecision(pending, "fp",
                        10_001L + MarketAdminCommand.Logic.CONFIRM_WINDOW_MILLIS));
        assertEquals(MarketAdminCommand.Logic.ConfirmDecision.ARM,
                MarketAdminCommand.Logic.confirmDecision(pending, "other", 10_001L));
        assertEquals(MarketAdminCommand.Logic.ConfirmDecision.ARM,
                MarketAdminCommand.Logic.confirmDecision(pending, "fp", 9_999L));
    }

    @Test
    void cancelFingerprintChangesWithListingRevisionAndReason() {
        UUID listingId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        String base = MarketAdminCommand.Logic.cancelFingerprint(listingId, 3L, "why");
        assertEquals(base,
                MarketAdminCommand.Logic.cancelFingerprint(listingId, 3L, "why"));
        assertNotEquals(base,
                MarketAdminCommand.Logic.cancelFingerprint(listingId, 4L, "why"));
        assertNotEquals(base,
                MarketAdminCommand.Logic.cancelFingerprint(listingId, 3L, "other"));
        assertNotEquals(base, MarketAdminCommand.Logic.cancelFingerprint(
                UUID.randomUUID(), 3L, "why"));
    }

    // ── reason bounding ─────────────────────────────────────────────────────

    @Test
    void reasonBoundingMirrorsTheMarketControlModelRules() {
        assertEquals(MarketModuleControl.MAX_REASON_BYTES,
                MarketAdminCommand.Logic.MAX_REASON_BYTES);
        assertEquals(Optional.of("empty"),
                MarketAdminCommand.Logic.reasonProblem(
                        MarketAdminCommand.Logic.normalizeReason("   ")));
        assertEquals(Optional.of("empty"),
                MarketAdminCommand.Logic.reasonProblem(
                        MarketAdminCommand.Logic.normalizeReason(null)));
        assertEquals(Optional.empty(),
                MarketAdminCommand.Logic.reasonProblem("stopping a dupe wave"));
        assertEquals(Optional.of("control_character"),
                MarketAdminCommand.Logic.reasonProblem("line\nbreak"));
    }

    @Test
    void reasonBoundingCountsUtf8BytesNotCharacters() {
        String maxAscii = "a".repeat(MarketAdminCommand.Logic.MAX_REASON_BYTES);
        assertEquals(Optional.empty(),
                MarketAdminCommand.Logic.reasonProblem(maxAscii));
        assertEquals(Optional.of("too_long"),
                MarketAdminCommand.Logic.reasonProblem(maxAscii + "a"));

        // 'é' is 2 UTF-8 bytes: 256 of them fit exactly, 257 do not.
        String maxTwoByte = "é".repeat(MarketAdminCommand.Logic.MAX_REASON_BYTES / 2);
        assertEquals(MarketAdminCommand.Logic.MAX_REASON_BYTES,
                maxTwoByte.getBytes(StandardCharsets.UTF_8).length);
        assertEquals(Optional.empty(),
                MarketAdminCommand.Logic.reasonProblem(maxTwoByte));
        assertEquals(Optional.of("too_long"),
                MarketAdminCommand.Logic.reasonProblem(maxTwoByte + "é"));
    }

    @Test
    void normalizeReasonStripsSurroundingWhitespace() {
        assertEquals("keep interior  spacing",
                MarketAdminCommand.Logic.normalizeReason("  keep interior  spacing \n"));
    }

    // ── module and verb parsing ─────────────────────────────────────────────

    @Test
    void parseModuleAcceptsEveryModuleIdCaseInsensitively() {
        for (MarketControlModule module : MarketControlModule.values()) {
            assertEquals(Optional.of(module),
                    MarketAdminCommand.Logic.parseModule(module.id()));
            assertEquals(Optional.of(module),
                    MarketAdminCommand.Logic.parseModule(
                            module.id().toUpperCase(Locale.ROOT)));
        }
        assertEquals(Optional.empty(), MarketAdminCommand.Logic.parseModule("stock"));
        assertEquals(Optional.empty(), MarketAdminCommand.Logic.parseModule(null));
    }

    @Test
    void verbMappingCoversTheStandaloneStatusesOnly() {
        assertEquals(MarketModuleStatus.FROZEN,
                MarketAdminCommand.Logic.targetStatus("freeze"));
        assertEquals(MarketModuleStatus.ENABLED,
                MarketAdminCommand.Logic.targetStatus("resume"));
        assertEquals(MarketModuleStatus.ENABLED,
                MarketAdminCommand.Logic.targetStatus("enable"));
        // No DISABLED status exists in the model; disable maps to DRAINING (plan §11 drain).
        assertEquals(MarketModuleStatus.DRAINING,
                MarketAdminCommand.Logic.targetStatus("disable"));
        assertThrows(IllegalArgumentException.class,
                () -> MarketAdminCommand.Logic.targetStatus("cancel_and_refund"));
        // CANCEL_AND_REFUND must never be reachable from a command verb.
        for (String verb : new String[] {"freeze", "resume", "enable", "disable"}) {
            assertNotEquals(MarketModuleStatus.CANCEL_AND_REFUND,
                    MarketAdminCommand.Logic.targetStatus(verb));
        }
    }

    // ── idempotent request ids ──────────────────────────────────────────────

    @Test
    void adminCancelRequestIdUsesThePlannedDerivationAndIsRevisionSensitive() {
        UUID listingId = UUID.fromString("7d387731-49cf-4e2c-a421-b0678457ed08");
        UUID derived = MarketAdminCommand.Logic.adminCancelRequestId(listingId, 5L);
        // Pinned derivation: nameUUID over listingId + revision + "admincancel".
        assertEquals(UUID.nameUUIDFromBytes(
                        (listingId + ":5:admincancel").getBytes(StandardCharsets.UTF_8)),
                derived);
        assertEquals(derived,
                MarketAdminCommand.Logic.adminCancelRequestId(listingId, 5L));
        assertNotEquals(derived,
                MarketAdminCommand.Logic.adminCancelRequestId(listingId, 6L));
        assertNotEquals(derived,
                MarketAdminCommand.Logic.adminCancelRequestId(UUID.randomUUID(), 5L));
    }

    @Test
    void cancelTerminalTransactionIdMatchesThePlayerPathConvention() {
        UUID requestId = UUID.fromString("7d387731-49cf-4e2c-a421-b0678457ed08");
        assertEquals(UUID.nameUUIDFromBytes(
                        ("auction.cancel." + requestId).getBytes(StandardCharsets.UTF_8)),
                MarketAdminCommand.Logic.cancelTerminalTransactionId(requestId));
    }

    @Test
    void controlRequestIdIsDeterministicPerModuleTargetAndRevision() {
        UUID first = MarketAdminCommand.Logic.controlRequestId(
                MarketControlModule.BAZAAR, MarketModuleStatus.FROZEN, 2L);
        assertEquals(first, MarketAdminCommand.Logic.controlRequestId(
                MarketControlModule.BAZAAR, MarketModuleStatus.FROZEN, 2L));
        assertNotEquals(first, MarketAdminCommand.Logic.controlRequestId(
                MarketControlModule.BAZAAR, MarketModuleStatus.FROZEN, 3L));
        assertNotEquals(first, MarketAdminCommand.Logic.controlRequestId(
                MarketControlModule.BAZAAR, MarketModuleStatus.ENABLED, 2L));
        assertNotEquals(first, MarketAdminCommand.Logic.controlRequestId(
                MarketControlModule.SHOP, MarketModuleStatus.FROZEN, 2L));
    }

    // ── status formatting decisions ─────────────────────────────────────────

    @Test
    void auditCountIsBoundedToOneThroughFifty() {
        assertEquals(1, MarketAdminCommand.Logic.boundAuditCount(Integer.MIN_VALUE));
        assertEquals(1, MarketAdminCommand.Logic.boundAuditCount(0));
        assertEquals(1, MarketAdminCommand.Logic.boundAuditCount(1));
        assertEquals(37, MarketAdminCommand.Logic.boundAuditCount(37));
        assertEquals(50, MarketAdminCommand.Logic.boundAuditCount(50));
        assertEquals(50, MarketAdminCommand.Logic.boundAuditCount(Integer.MAX_VALUE));
        assertEquals(50, MarketAdminCommand.Logic.MAX_AUDIT_RECORDS);
    }

    @Test
    void openListingAndOrderClassificationTracksTheTerminalFlags() {
        for (AuctionListingState state : AuctionListingState.values()) {
            assertEquals(!state.terminal(),
                    MarketAdminCommand.Logic.openAuctionListing(state),
                    "auction state " + state);
        }
        for (BazaarOrderState state : BazaarOrderState.values()) {
            assertEquals(!state.terminal(),
                    MarketAdminCommand.Logic.openBazaarOrder(state),
                    "bazaar order state " + state);
        }
    }

    @Test
    void ageSecondsFloorsAndNeverGoesNegative() {
        Instant prepared = Instant.ofEpochMilli(10_000L);
        assertEquals(0L, MarketAdminCommand.Logic.ageSeconds(prepared, 10_000L));
        assertEquals(0L, MarketAdminCommand.Logic.ageSeconds(prepared, 10_999L));
        assertEquals(1L, MarketAdminCommand.Logic.ageSeconds(prepared, 11_000L));
        assertEquals(59L, MarketAdminCommand.Logic.ageSeconds(prepared, 69_999L));
        // A clock that ran backwards must not render a negative age.
        assertEquals(0L, MarketAdminCommand.Logic.ageSeconds(prepared, 9_000L));
    }

    @Test
    void statusKeySuffixIsTheLowercaseEnumName() {
        assertEquals("cancel_and_refund", MarketAdminCommand.Logic.statusKeySuffix(
                MarketModuleStatus.CANCEL_AND_REFUND));
        assertEquals("ready", MarketAdminCommand.Logic.statusKeySuffix(
                EscrowRuntimeState.READY));
    }

    // ── actor bounding ──────────────────────────────────────────────────────

    @Test
    void actorLabelIsBoundedCleanAndNeverEmpty() {
        assertEquals("EnVy", MarketAdminCommand.Logic.actorLabel("  EnVy \n"));
        assertEquals("admin", MarketAdminCommand.Logic.actorLabel(""));
        assertEquals("admin", MarketAdminCommand.Logic.actorLabel(null));
        assertEquals("admin", MarketAdminCommand.Logic.actorLabel("\u0001\u0002"));
        String bounded = MarketAdminCommand.Logic.actorLabel(
                "x".repeat(MarketControlActor.MAX_LABEL_BYTES * 3));
        assertEquals(MarketControlActor.MAX_LABEL_BYTES,
                bounded.getBytes(StandardCharsets.UTF_8).length);
        // Multibyte truncation must land on a character boundary within the byte budget.
        String multibyte = MarketAdminCommand.Logic.actorLabel("é".repeat(200));
        assertTrue(multibyte.getBytes(StandardCharsets.UTF_8).length
                <= MarketControlActor.MAX_LABEL_BYTES);
        // Every produced label must satisfy the model's own validation.
        MarketAdminCommand.Logic.controlActor(UUID.randomUUID(), "é".repeat(200));
        MarketAdminCommand.Logic.controlActor(UUID.randomUUID(), null);
    }

    // ── scheduler trigger pin ───────────────────────────────────────────────

    /**
     * {@code /marketadmin sweep} now calls the schedulers' public {@code trigger(server)}
     * methods directly — pin that both exist so a signature change fails loudly here instead of
     * silently breaking the command.
     */
    @Test
    void bothSchedulersExposeAPublicTrigger() throws Exception {
        assertNotNull(com.enviouse.futureshops.server.escrow.runtime
                .AuctionExpirationScheduler.class
                .getMethod("trigger", net.minecraft.server.MinecraftServer.class));
        assertNotNull(com.enviouse.futureshops.server.escrow.runtime
                .BazaarExpirationScheduler.class
                .getMethod("trigger", net.minecraft.server.MinecraftServer.class));
    }

    // ── lang-key pinning for dynamically composed keys ──────────────────────

    /**
     * The command composes several keys from enum names at runtime
     * ({@code command.futureshops.marketadmin.<namespace>.<lowercase enum>}), which the
     * repo-wide literal-key coverage test cannot see. Pin every composable key here.
     */
    @Test
    void everyDynamicallyComposedMarketAdminLangKeyExists() throws Exception {
        String lang = Files.readString(EN_US);
        String prefix = "command.futureshops.marketadmin.";

        for (MarketControlModule module : MarketControlModule.values()) {
            assertKey(lang, prefix + "module." + module.id());
        }
        for (MarketModuleStatus status : MarketModuleStatus.values()) {
            assertKey(lang, prefix + "module_state."
                    + status.name().toLowerCase(Locale.ROOT));
        }
        for (EscrowRuntimeState state : EscrowRuntimeState.values()) {
            assertKey(lang, prefix + "runtime_state."
                    + state.name().toLowerCase(Locale.ROOT));
        }
        for (BazaarProductStatus status : BazaarProductStatus.values()) {
            assertKey(lang, prefix + "bazaar.product_state."
                    + status.name().toLowerCase(Locale.ROOT));
        }
        for (String problem : new String[] {"empty", "too_long", "control_character"}) {
            assertKey(lang, prefix + "control.reason." + problem);
        }
        for (String kind : new String[] {"auction", "bazaar"}) {
            assertKey(lang, prefix + "recovery.kind." + kind);
        }
        for (String fixed : new String[] {
                "runtime_unavailable",
                "status.header", "status.module_line", "status.runtime_line",
                "status.counts_line", "status.recovery_line", "status.maintenance_line",
                "status.counts_unavailable",
                "control.unknown_module", "control.noop", "control.applied",
                "control.replayed", "control.rejected",
                "audit.header", "audit.empty", "audit.line_ok", "audit.line_failed",
                "recovery.header", "recovery.empty", "recovery.line",
                "sweep.done",
                "cancel.not_found", "cancel.terminal", "cancel.armed",
                "cancel.armed_bid_warning", "cancel.armed_hint", "cancel.replayed",
                "cancel.no_custody", "cancel.rejected", "cancel.done",
                "cancel.audit_failed",
                "bazaar.not_found", "bazaar.invalid_product", "bazaar.noop",
                "bazaar.updated",
                "error.internal"}) {
            assertKey(lang, prefix + fixed);
        }
    }

    private static void assertKey(String lang, String key) {
        assertTrue(lang.contains("\"" + key + "\""),
                "en_us.json is missing lang key " + key);
    }

    @Test
    void confirmWindowIsThirtySeconds() {
        assertEquals(30_000L, MarketAdminCommand.Logic.CONFIRM_WINDOW_MILLIS);
        assertFalse(MarketAdminCommand.Logic.CONFIRM_WINDOW_MILLIS <= 0L);
    }
}

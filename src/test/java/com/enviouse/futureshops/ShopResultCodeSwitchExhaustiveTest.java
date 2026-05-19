package com.enviouse.futureshops;

import com.enviouse.futureshops.server.shop.ShopResultCode;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins every dispatch switch keyed on a {@link ShopResultCode} (or its String name) to
 * <strong>exhaustive, default-free</strong> enumerations so a newly-added enum constant
 * cannot silently collapse into a generic "something went wrong" branch without a
 * deliberate case-label choice.
 *
 * <p>Audited sites:
 * <ul>
 *   <li>{@code ShopClientPacketHandler.errorKey(ShopResultCode)} — buy/sell/barter chat
 *       feedback key lookup (enum switch).</li>
 *   <li>{@code EconomyCommandUtil.sendProviderError(ServerPlayer, ShopResultCode)} —
 *       /pay, /bal, balance-command error output (enum switch).</li>
 *   <li>{@code PlayerShopBlockScreen.buyerFriendlyMessage(String)} — String-valued
 *       allowlist switch deciding which result codes are surfaced to the buying
 *       player (validated against a pinned allowlist of buyer-visible codes).</li>
 * </ul>
 *
 * <p>For enum switches the test confirms:
 * <ol>
 *   <li>The switch body contains <strong>no</strong> {@code default ->} branch.</li>
 *   <li>Every {@link ShopResultCode} constant appears as a case label.</li>
 * </ol>
 *
 * <p>For the String switch the test confirms:
 * <ol>
 *   <li>The set of quoted case literals matches the hardcoded allowlist exactly
 *       (catches both drift directions).</li>
 *   <li>The {@code default ->} branch resolves to {@code false} (never {@code true}),
 *       preserving the "hide unknown codes from the buyer" invariant.</li>
 *   <li>Every allowlisted code is a real {@link ShopResultCode} constant.</li>
 *   <li>Every allowlisted code has a matching
 *       {@code gui.futureshops.player_shop.buyer.<code>} key in {@code en_us.json}.</li>
 * </ol>
 *
 * <p>A final guardrail test walks {@code src/main/java} looking for any other
 * {@code switch} expression whose scrutinee type is {@code ShopResultCode} and fails
 * if the audit roster above does not already cover it — the refactor-rot catch.
 */
public class ShopResultCodeSwitchExhaustiveTest {

    private static final Path CLIENT_HANDLER =
            Path.of("src/main/java/com/enviouse/futureshops/client/ShopClientPacketHandler.java");
    private static final Path ECON_UTIL =
            Path.of("src/main/java/com/enviouse/futureshops/command/EconomyCommandUtil.java");
    private static final Path PLAYER_SHOP_SCREEN =
            Path.of("src/main/java/com/enviouse/futureshops/client/screen/PlayerShopBlockScreen.java");
    private static final Path LANG_FILE =
            Path.of("src/main/resources/assets/futureshops/lang/en_us.json");
    private static final Path MAIN_JAVA_ROOT =
            Path.of("src/main/java");

    /**
     * Pinned allowlist of {@link ShopResultCode} names that {@code buyerFriendlyMessage}
     * is allowed to surface to the buying player. Any drift (addition or removal) must
     * be a deliberate edit to both this list AND the switch body — the test enforces
     * symmetry so the two cannot diverge silently.
     */
    private static final Set<String> BUYER_VISIBLE_CODES = Set.of(
            "BOUGHT",
            "INSUFFICIENT_FUNDS",
            "OUT_OF_STOCK",
            "MISSING_BARTER_ITEMS",
            "STORAGE_FULL",
            "INVALID_ITEM",
            "NO_LINK",
            "ROLLBACK",
            "UNCONFIGURED",
            "RS_NOT_CONTROLLER",
            "INVALID_REQUEST"
    );

    /**
     * Roster of audited switch sites (file + method-hint). The
     * {@link #everyShopResultCodeSwitchSiteIsAudited} guardrail compares this against
     * switch expressions it discovers in the source tree and fails if a new site
     * appears without being added here.
     */
    private static final Set<String> AUDITED_SWITCH_SITES = Set.of(
            CLIENT_HANDLER + "::errorKey",
            ECON_UTIL + "::sendProviderError",
            PLAYER_SHOP_SCREEN + "::buyerFriendlyMessage"
    );

    /**
     * Captures a whole {@code switch (…) { … }} body. The regex is lenient but the
     * audited methods are short enough to match the first {@code switch} block
     * after the method signature.
     */
    private static final Pattern SWITCH_BODY = Pattern.compile(
            "switch\\s*\\(\\s*\\w+\\s*\\)\\s*\\{([\\s\\S]*?)\\n\\s*\\}\\s*;",
            Pattern.DOTALL);

    /**
     * Captures every {@code case A, B, C ->} label group (comma-separated names allowed).
     * Enum-style labels only (unquoted identifiers).
     */
    private static final Pattern CASE_LABELS = Pattern.compile(
            "case\\s+([A-Z_][A-Z0-9_, \\n\\r\\t]*?)\\s*->");

    /**
     * Captures every {@code case "A", "B", "C" ->} label group used in String switches.
     */
    private static final Pattern STRING_CASE_LABELS = Pattern.compile(
            "case\\s+(\"[A-Z_]+\"(?:\\s*,\\s*\"[A-Z_]+\")*)\\s*->");

    @Test
    void errorKeySwitchIsExhaustiveOverShopResultCode() throws Exception {
        assertExhaustive(CLIENT_HANDLER, "errorKey");
    }

    @Test
    void sendProviderErrorSwitchIsExhaustiveOverShopResultCode() throws Exception {
        assertExhaustive(ECON_UTIL, "sendProviderError");
    }

    /**
     * Confirms the String-valued switch in
     * {@code PlayerShopBlockScreen.buyerFriendlyMessage(String)} matches the pinned
     * allowlist exactly, that its {@code default} branch cannot leak unknown codes to
     * the buyer, and that every listed code is a real {@link ShopResultCode} constant.
     */
    @Test
    void buyerFriendlyMessageSwitchMatchesAllowlist() throws Exception {
        String src = Files.readString(PLAYER_SHOP_SCREEN);
        String body = extractSwitchBody(src, "buyerFriendlyMessage");
        assertTrue(body != null && !body.isBlank(),
                "Could not locate switch body in " + PLAYER_SHOP_SCREEN
                        + " (method hint: buyerFriendlyMessage)");

        // Guard 1: default branch must resolve to `false` — unknown codes must NEVER
        // surface to the buyer. `default -> true` would leak raw enum names via the
        // translatable lookup fallback.
        assertTrue(Pattern.compile("default\\s*->\\s*false").matcher(body).find(),
                PLAYER_SHOP_SCREEN + " :: `buyerFriendlyMessage` must keep "
                        + "`default -> false` so unknown result codes stay hidden from "
                        + "the buyer. Do not switch the default to `true` or remove it.");
        assertFalse(Pattern.compile("default\\s*->\\s*true").matcher(body).find(),
                PLAYER_SHOP_SCREEN + " :: `buyerFriendlyMessage` default branch must "
                        + "not evaluate to `true` — that would leak untranslated raw "
                        + "enum names to buyers.");

        // Guard 2: parse String case labels and verify symmetric equality with the
        // pinned allowlist.
        Set<String> labelled = new HashSet<>();
        Matcher m = STRING_CASE_LABELS.matcher(body);
        while (m.find()) {
            for (String raw : m.group(1).split(",")) {
                String trimmed = raw.trim();
                if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2) {
                    labelled.add(trimmed.substring(1, trimmed.length() - 1));
                }
            }
        }

        Set<String> expected = new TreeSet<>(BUYER_VISIBLE_CODES);
        Set<String> actual = new TreeSet<>(labelled);
        assertEquals(expected, actual,
                PLAYER_SHOP_SCREEN + " :: `buyerFriendlyMessage` case labels drifted "
                        + "from the pinned buyer-visible allowlist. If this change is "
                        + "deliberate, update BUYER_VISIBLE_CODES in this test AND the "
                        + "switch body together, and add/remove the matching "
                        + "`gui.futureshops.player_shop.buyer.<code>` lang key.");

        // Guard 3: every label must be a real ShopResultCode constant.
        Set<String> shopResultCodeNames = Arrays.stream(ShopResultCode.values())
                .map(Enum::name).collect(Collectors.toCollection(TreeSet::new));
        List<String> stale = new ArrayList<>();
        for (String name : labelled) {
            if (!shopResultCodeNames.contains(name)) stale.add(name);
        }
        assertEquals(List.of(), stale,
                PLAYER_SHOP_SCREEN + " :: `buyerFriendlyMessage` references case "
                        + "labels that are not ShopResultCode constants: " + stale);
    }

    /**
     * Every buyer-visible code must resolve to a non-blank
     * {@code gui.futureshops.player_shop.buyer.<code>} key in {@code en_us.json}, or
     * the runtime lookup at the end of {@code buyerFriendlyMessage} would render the
     * raw translation key to the player.
     */
    @Test
    void buyerFriendlyMessagesHaveLangKeys() throws Exception {
        String lang = Files.readString(LANG_FILE);
        List<String> missing = new ArrayList<>();
        for (String code : BUYER_VISIBLE_CODES) {
            String key = "\"gui.futureshops.player_shop.buyer." + code.toLowerCase(Locale.ROOT) + "\"";
            int idx = lang.indexOf(key);
            if (idx < 0) {
                missing.add(code);
                continue;
            }
            // Ensure the value is non-blank: "key": "something non-empty"
            int colon = lang.indexOf(':', idx + key.length());
            int quoteOpen = lang.indexOf('"', colon);
            int quoteClose = lang.indexOf('"', quoteOpen + 1);
            if (quoteOpen < 0 || quoteClose < 0 || quoteClose - quoteOpen <= 1) {
                missing.add(code + " (blank value)");
            }
        }
        assertEquals(List.of(), missing,
                "Buyer-visible codes without a non-blank "
                        + "`gui.futureshops.player_shop.buyer.<code>` lang key in "
                        + LANG_FILE + ": " + missing
                        + ". Every entry in BUYER_VISIBLE_CODES must have a translation.");
    }

    /**
     * Refactor-rot catch: scans {@code src/main/java} for any {@code switch} expression
     * whose scrutinee type resolves to {@link ShopResultCode} (either directly, or via
     * a parameter/variable of that type), and fails if the discovered site is not in
     * {@link #AUDITED_SWITCH_SITES}. Forces every new dispatch site to be registered
     * with an exhaustiveness test before it can land.
     */
    @Test
    void everyShopResultCodeSwitchSiteIsAudited() throws Exception {
        List<String> discovered = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(MAIN_JAVA_ROOT)) {
            List<Path> javaFiles = paths
                    .filter(p -> p.toString().endsWith(".java"))
                    .collect(Collectors.toList());
            for (Path file : javaFiles) {
                String src = Files.readString(file);
                // Only inspect files that mention ShopResultCode at all — cheap filter.
                if (!src.contains("ShopResultCode")) continue;

                Matcher sw = Pattern.compile("switch\\s*\\(\\s*(\\w+)\\s*\\)").matcher(src);
                while (sw.find()) {
                    String scrutinee = sw.group(1);
                    int switchIdx = sw.start();
                    if (!referencesShopResultCode(src, scrutinee, switchIdx)) continue;

                    String methodHint = enclosingMethodName(src, switchIdx);
                    String siteId = file + "::" + methodHint;
                    if (!AUDITED_SWITCH_SITES.contains(siteId)) {
                        discovered.add(siteId);
                    }
                }
            }
        }
        assertEquals(List.of(), discovered,
                "Found switch expression(s) over ShopResultCode that are not in "
                        + "AUDITED_SWITCH_SITES: " + discovered
                        + ". Add a dedicated exhaustiveness test and register the site "
                        + "in AUDITED_SWITCH_SITES so future refactors cannot silently "
                        + "introduce a default branch.");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static void assertExhaustive(Path file, String methodHint) throws Exception {
        String src = Files.readString(file);
        String body = extractSwitchBody(src, methodHint);
        assertTrue(body != null && !body.isBlank(),
                "Could not locate switch body in " + file + " (method hint: " + methodHint + ")");

        // Guard 1: no `default ->` branch — the whole point is to force explicit case mapping.
        assertFalse(Pattern.compile("\\bdefault\\s*->").matcher(body).find(),
                file + " :: `" + methodHint + "` contains a `default ->` branch. "
                        + "Replace it with an explicit case-label group listing every "
                        + "ShopResultCode constant that should fall through to the generic "
                        + "server-error key — otherwise new enum constants silently collapse "
                        + "into SERVER_ERROR output with no maintainer review.");

        // Guard 2: every enum constant must appear as a case label somewhere in the body.
        Set<String> labelled = new HashSet<>();
        Matcher m = CASE_LABELS.matcher(body);
        while (m.find()) {
            for (String raw : m.group(1).split(",")) {
                String name = raw.trim();
                if (!name.isEmpty()) labelled.add(name);
            }
        }

        Set<String> expected = new TreeSet<>();
        for (ShopResultCode c : ShopResultCode.values()) expected.add(c.name());

        List<String> missing = new ArrayList<>();
        for (String name : expected) {
            if (!labelled.contains(name)) missing.add(name);
        }
        assertTrue(missing.isEmpty(),
                file + " :: `" + methodHint + "` switch is not exhaustive over "
                        + "ShopResultCode — missing case labels: " + missing + ". "
                        + "Either add a specific mapping or append the constant to the "
                        + "'intentionally generic' fall-through group.");

        // Guard 3: surface bogus labels (refactor-rot catch — e.g. deleted enum still referenced).
        List<String> stale = new ArrayList<>();
        for (String name : labelled) {
            if (!expected.contains(name)) stale.add(name);
        }
        assertEquals(List.of(), stale,
                file + " :: `" + methodHint + "` references case labels that are not "
                        + "ShopResultCode constants: " + stale);

        // Sanity-pin the expected constant count so enum growth forces a conscious
        // re-read of this test (prevents people from silently adding constants to one
        // switch and forgetting the other).
        assertEquals(expected.size(), labelled.size(),
                "Case-label count (" + labelled.size() + ") and ShopResultCode.values() "
                        + "count (" + expected.size() + ") diverged — check for duplicate "
                        + "labels or missing constants in " + file);
    }

    /**
     * Finds the first {@code switch (…) { … };} block that appears after the given
     * method hint (method name) in the source. Audited methods are short and contain
     * exactly one switch expression, so the pattern-after-hint heuristic is robust
     * enough without building a Java parser.
     */
    private static String extractSwitchBody(String src, String methodHint) {
        int hintIdx = src.indexOf(methodHint);
        if (hintIdx < 0) return null;
        Matcher m = SWITCH_BODY.matcher(src);
        while (m.find()) {
            if (m.start() > hintIdx) return m.group(1);
        }
        return null;
    }

    /**
     * Heuristic: within 400 characters before the switch, look for either
     * {@code ShopResultCode <scrutinee>} (local/param declaration) or a parameter of
     * the enclosing method named {@code scrutinee} whose type is {@code ShopResultCode}.
     * This is intentionally conservative — false negatives just mean the guardrail
     * misses a site, which is covered by the direct tests above. False positives would
     * be noisier, so we lean toward precision.
     */
    private static boolean referencesShopResultCode(String src, String scrutinee, int switchIdx) {
        int windowStart = Math.max(0, switchIdx - 2000);
        String window = src.substring(windowStart, switchIdx);
        // Direct typed declaration / parameter.
        Pattern direct = Pattern.compile(
                "ShopResultCode\\s+" + Pattern.quote(scrutinee) + "\\b");
        if (direct.matcher(window).find()) return true;
        // Assignment from a ShopResultCode-returning API (best-effort).
        Pattern via = Pattern.compile(
                "\\b" + Pattern.quote(scrutinee) + "\\s*=\\s*[^;]*ShopResultCode");
        return via.matcher(window).find();
    }

    /**
     * Walks backward from the switch index to find the enclosing method name. Looks
     * for the last Java method signature before the switch. Returns {@code "<unknown>"}
     * if it cannot confidently identify one.
     */
    private static String enclosingMethodName(String src, int switchIdx) {
        // Match `<modifiers> <returnType> methodName(...)` — capture group 1 is the name.
        Pattern sig = Pattern.compile(
                "\\b(?:public|private|protected|static|final|synchronized|\\s)+" +
                "[A-Za-z_][\\w<>\\[\\],\\s\\?]*?\\s+" +
                "([a-zA-Z_][\\w]*)\\s*\\([^)]*\\)\\s*(?:throws\\s+[\\w\\s,.]+)?\\s*\\{");
        Matcher m = sig.matcher(src);
        String last = "<unknown>";
        while (m.find()) {
            if (m.start() > switchIdx) break;
            last = m.group(1);
        }
        return last;
    }
}

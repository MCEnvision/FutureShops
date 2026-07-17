package com.enviouse.futureshops;

import org.junit.jupiter.api.Test;

import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Invariant test for the "BOTH-mode silently trades with money" regression fixed in LGB#4 / LGB#BOTH-guard.
 *
 * Every call to {@code new C2SPlayerShopBuyPacket(...)} must supply a non-empty payment-method
 * argument. Historically a 3-arg legacy constructor defaulted the method to {@code ""}, which
 * caused the server's BOTH-mode branch to fall back to a balance check — so clicking the Barter
 * button would silently spend coins whenever the player could afford the listing.
 *
 * This test scans production source files for buy-packet construction sites and fails if any
 * uses the deprecated 3-arg form or passes a string-literal {@code ""} for paymentMethod.
 */
public class BuyPacketCallSiteTest {

    /** Production files that are legally allowed to construct the buy packet. */
    private static final List<Path> CALL_SITES = List.of(
            Path.of("src/main/java/com/enviouse/futureshops/client/screen/PlayerShopCartScreen.java"),
            Path.of("src/main/java/com/enviouse/futureshops/client/screen/PlayerShopBlockScreen.java"),
            Path.of("src/main/java/com/enviouse/futureshops/client/screen/PlayerShopBarterScreen.java"),
            Path.of("src/main/java/com/enviouse/futureshops/client/screen/PlayerStorefrontScreen.java"));

    // Locates `new C2SPlayerShopBuyPacket(` prefixes; the matching close paren is found
    // manually by balancing parens so that inner calls like `entry.shopPos()` are not
    // mis-parsed as the end of the ctor.
    private static final Pattern CTOR_START = Pattern.compile("new\\s+C2SPlayerShopBuyPacket\\s*\\(");

    private static String extractArgs(String src, int openParenPos) {
        int depth = 1;
        int i = openParenPos + 1;
        while (i < src.length() && depth > 0) {
            char c = src.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            if (depth == 0) return src.substring(openParenPos + 1, i);
            i++;
        }
        return "";
    }

    @Test
    void everyBuyPacketCallSitePassesNonEmptyPaymentMethod() throws Exception {
        List<String> failures = new ArrayList<>();
        int totalCallSites = 0;

        for (Path file : CALL_SITES) {
            assertTrue(Files.exists(file), "Expected call-site source file to exist: " + file);
            String src = Files.readString(file);
            Matcher m = CTOR_START.matcher(src);
            while (m.find()) {
                totalCallSites++;
                int openParen = m.end() - 1;
                String argsRaw = extractArgs(src, openParen);
                // Strip comments + collapse whitespace so we can split cleanly.
                String args = argsRaw.replaceAll("//[^\\n]*", "")
                        .replaceAll("/\\*.*?\\*/", "")
                        .replaceAll("\\s+", " ")
                        .trim();

                // Split on commas that are NOT inside nested parens.
                List<String> parts = splitTopLevel(args);
                if (parts.size() < 4) {
                    failures.add(file + " :: legacy 3-arg C2SPlayerShopBuyPacket ctor used — "
                            + "BOTH-mode listings will be rejected by the server guard: " + argsRaw);
                    continue;
                }
                String paymentArg = parts.get(3).trim();
                if (paymentArg.equals("\"\"")) {
                    failures.add(file + " :: empty-string paymentMethod passed to C2SPlayerShopBuyPacket: " + argsRaw);
                }
                // `entry.chosenPayment()`, `"MONEY"`, `"BARTER"`, `"MONEY_AND_BARTER"`, `paymentMethod`
                // are all fine — chosenPayment() defaults per-mode in PlayerShopCartState.
            }
        }

        assertTrue(totalCallSites > 0, "Test regex found no buy-packet call sites — update the test.");
        assertTrue(failures.isEmpty(),
                "Forbidden C2SPlayerShopBuyPacket call sites:\n  " + String.join("\n  ", failures));
    }

    private static List<String> splitTopLevel(String s) {
        List<String> out = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == ',' && depth == 0) {
                out.add(s.substring(start, i));
                start = i + 1;
            }
        }
        if (start < s.length()) out.add(s.substring(start));
        return out;
    }

    @Test
    void cartScreenForwardsChosenPayment() throws Exception {
        // Guard against a regression where the cart checkout stops forwarding the per-entry
        // chosenPayment tag — which is what lets MONEY_AND_BARTER and BOTH entries reach the
        // server with the right tag.
        Path cart = Path.of("src/main/java/com/enviouse/futureshops/client/screen/PlayerShopCartScreen.java");
        String src = Files.readString(cart);
        if (!src.contains("entry.chosenPayment()")) {
            fail("PlayerShopCartScreen no longer forwards entry.chosenPayment() — BOTH/compound trades will regress.");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Trade-mode label invariants (task: prettyMode → ShopUiUtil.tradeModeLabel)
    // ═══════════════════════════════════════════════════════════════════════

    /** Root of the client tree — all UI code lives here. */
    private static final Path CLIENT_ROOT =
            Path.of("src/main/java/com/enviouse/futureshops/client");

    /** The single file that's legally allowed to hard-code trade-mode label strings. */
    private static final String LABEL_HELPER_FILE = "ShopUiUtil.java";

    /**
     * Forbidden hard-coded trade-mode label literals in client code.
     * These must flow through {@code ShopUiUtil.tradeModeLabel(String)} so
     * translations (via {@code gui.futureshops.trade_mode.*} lang keys) can
     * override them without code edits.
     */
    private static final List<Pattern> FORBIDDEN_LABEL_LITERALS = List.of(
            // Old pre-relabel strings — historical regressions
            Pattern.compile("\"§aMoney§7/§9Barter\""),
            Pattern.compile("\"§6M\\+B\""),
            // New relabel strings — must come from lang, not be hard-coded at call sites
            Pattern.compile("\"§aMoney §7or §9Barter\""),
            Pattern.compile("\"§aMoney §f\\+ §9Barter\""),
            Pattern.compile("\"Money or Barter\""),
            Pattern.compile("\"Money \\+ Barter\""),
            Pattern.compile("\"Money/Barter\""));

    /**
     * Forbid referencing the server-side {@code ShopBlockEntity.TradeMode} enum from any
     * client source. Trade modes cross the wire as strings; treating the enum as a client
     * concept invites drift between server and client.
     */
    private static final Pattern FORBIDDEN_ENUM_REFS =
            Pattern.compile("ShopBlockEntity\\.TradeMode\\.(BOTH|MONEY_AND_BARTER|MONEY|BARTER)");

    @Test
    void clientNeverHardCodesTradeModeLabels() throws Exception {
        List<String> failures = new ArrayList<>();
        List<Path> sources = collectJava(CLIENT_ROOT);
        assertTrue(!sources.isEmpty(), "No client sources found under " + CLIENT_ROOT);

        for (Path file : sources) {
            String fileName = file.getFileName().toString();
            if (LABEL_HELPER_FILE.equals(fileName)) {
                // The helper is the single site allowed to define the literals. Skip.
                continue;
            }
            // The relabel helper comment is allowed to quote the old literals in-prose.
            // Test it by stripping line/block comments before scanning.
            String src = stripComments(Files.readString(file));
            for (Pattern forbidden : FORBIDDEN_LABEL_LITERALS) {
                Matcher fm = forbidden.matcher(src);
                if (fm.find()) {
                    failures.add(file + " :: hard-codes trade-mode label " + fm.group()
                            + " — route through ShopUiUtil.tradeModeLabel(mode) instead.");
                }
            }
            Matcher em = FORBIDDEN_ENUM_REFS.matcher(src);
            if (em.find()) {
                failures.add(file + " :: references server enum " + em.group()
                        + " — client should compare trade-mode strings, not the enum.");
            }
        }

        assertTrue(failures.isEmpty(),
                "Forbidden trade-mode usages in client code:\n  " + String.join("\n  ", failures));
    }

    @Test
    void prettyModeDelegatesToTradeModeLabelHelper() throws Exception {
        // Defensive lock — if someone reinlines prettyMode with its own switch, the lang
        // keys silently stop affecting the listing rail / detail panel labels.
        Path blockScreen = Path.of("src/main/java/com/enviouse/futureshops/client/screen/PlayerShopBlockScreen.java");
        String src = Files.readString(blockScreen);
        if (!src.contains("ShopUiUtil.tradeModeLabel(")) {
            fail("PlayerShopBlockScreen no longer delegates trade-mode rendering to "
                    + "ShopUiUtil.tradeModeLabel — translations via gui.futureshops.trade_mode.* "
                    + "keys will no longer apply.");
        }
    }

    private static List<Path> collectJava(Path root) throws Exception {
        List<Path> out = new ArrayList<>();
        if (!Files.exists(root)) return out;
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.toString().endsWith(".java")) out.add(file);
                return FileVisitResult.CONTINUE;
            }
        });
        return out;
    }

    private static String stripComments(String src) {
        // Very small comment stripper sufficient for this invariant scan:
        //   - // line comments to end of line
        //   - /* block comments */  (non-greedy, DOTALL)
        String noBlocks = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL).matcher(src).replaceAll("");
        return noBlocks.replaceAll("//[^\\n]*", "");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Result-code lang-key invariants (task: buyer fallback strings → lang keys)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * English fallback strings that previously lived inside
     * {@code PlayerShopBlockScreen.buyerFriendlyMessage} as a hard-coded switch.
     * They MUST now come from {@code gui.futureshops.player_shop.buyer.<code>} lang
     * keys so translations work. Any client source that hard-codes these literals
     * is a regression — the scan excludes nothing (even the block screen itself
     * must look them up via {@code Component.translatable(...)}).
     */
    private static final List<String> FORBIDDEN_RESULT_FALLBACKS = List.of(
            "§a✓ Purchase complete!",
            "§cInsufficient balance.",
            "§cThis item is out of stock.",
            "§cYou don't have enough barter items.",
            "§cThe shop's storage is full.",
            "§cInvalid item.",
            "§cShop storage not linked.",
            "§cTransaction failed. No items were taken.",
            "§cThis listing is not yet configured by the shop owner.",
            "§cBlock not supported — link directly to the System Controller.",
            "§cPick Money or Barter before confirming.");

    @Test
    void clientNeverHardCodesBuyerResultFallbackStrings() throws Exception {
        List<String> failures = new ArrayList<>();
        List<Path> sources = collectJava(CLIENT_ROOT);
        assertTrue(!sources.isEmpty(), "No client sources found under " + CLIENT_ROOT);

        for (Path file : sources) {
            String src = stripComments(Files.readString(file));
            for (String fallback : FORBIDDEN_RESULT_FALLBACKS) {
                // Match as a double-quoted Java string literal so the test doesn't trip
                // on (hypothetical) lang JSON values that happen to share phrasing.
                String quoted = "\"" + fallback + "\"";
                if (src.contains(quoted)) {
                    failures.add(file + " :: hard-codes buyer-result fallback "
                            + quoted + " — look up via Component.translatable("
                            + "\"gui.futureshops.player_shop.buyer.<code>\") instead.");
                }
            }
        }

        assertTrue(failures.isEmpty(),
                "Forbidden hard-coded buyer-result fallbacks in client code:\n  "
                        + String.join("\n  ", failures));
    }

    @Test
    void buyerFriendlyMessageUsesComponentTranslatable() throws Exception {
        // Defensive lock — if someone reinlines buyerFriendlyMessage with its own
        // English switch, translations silently stop working for buy/sell/barter chat.
        Path blockScreen = Path.of("src/main/java/com/enviouse/futureshops/client/screen/PlayerShopBlockScreen.java");
        String src = Files.readString(blockScreen);
        int idx = src.indexOf("buyerFriendlyMessage(String code)");
        assertTrue(idx >= 0,
                "PlayerShopBlockScreen.buyerFriendlyMessage(String) is gone — update the test "
                        + "if the method was renamed, or route result-code chat through another helper.");
        // Scan the next ~1500 chars (method body) for the lookup pattern.
        String body = src.substring(idx, Math.min(src.length(), idx + 2000));
        if (!body.contains("Component.translatable(\"gui.futureshops.player_shop.buyer.")) {
            fail("buyerFriendlyMessage no longer resolves via Component.translatable("
                    + "\"gui.futureshops.player_shop.buyer.<code>\") — buyer-visible result "
                    + "messages will regress to hard-coded English.");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Lang-key coverage + value-leak scan
    // ═══════════════════════════════════════════════════════════════════════

    private static final Path EN_US = Path.of("src/main/resources/assets/futureshops/lang/en_us.json");

    /**
     * Extracts all {@code "gui.futureshops.*"} keys + their raw string values from en_us.json.
     * Minimal regex-based parser — en_us.json is a flat object, so no nesting concerns.
     */
    private static java.util.LinkedHashMap<String, String> loadLangPairs() throws Exception {
        String json = Files.readString(EN_US);
        // Match "<key>": "<value>" — value supports escaped quotes (\").
        Pattern kv = Pattern.compile(
                "\"(gui\\.futureshops\\.[^\"]+)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"",
                Pattern.DOTALL);
        Matcher m = kv.matcher(json);
        java.util.LinkedHashMap<String, String> out = new java.util.LinkedHashMap<>();
        while (m.find()) {
            // Unescape common JSON escapes for value matching against Java string literals.
            String value = m.group(2)
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
                    .replace("\\n", "\n")
                    .replace("\\t", "\t");
            out.put(m.group(1), value);
        }
        return out;
    }

    /**
     * Every {@code Component.translatable("gui.futureshops.…")} literal referenced anywhere in
     * client code must resolve to an existing key in {@code en_us.json}. Catches typos and
     * stale keys that would silently render the raw key string in-game.
     *
     * Dynamic prefix references (code concatenates an uppercase enum tail onto the key, e.g.
     * {@code "gui.futureshops.player_shop.buyer." + upper}) are handled by scanning all keys
     * sharing that prefix — the test fails if NO key matches the prefix, proving the family
     * is wired up.
     */
    @Test
    void everyClientLangKeyReferenceResolves() throws Exception {
        java.util.Map<String, String> lang = loadLangPairs();
        assertTrue(!lang.isEmpty(), "Could not parse any keys out of " + EN_US);

        // Pattern: Component.translatable("gui.futureshops.<anything>") or "gui.futureshops.<…>." + var
        Pattern ref = Pattern.compile(
                "Component\\.translatable\\(\\s*\"(gui\\.futureshops\\.[^\"]+)\"");

        List<String> failures = new ArrayList<>();
        for (Path file : collectJava(CLIENT_ROOT)) {
            String src = stripComments(Files.readString(file));
            Matcher m = ref.matcher(src);
            while (m.find()) {
                String key = m.group(1);
                if (key.endsWith(".")) {
                    // Dynamic key (prefix + runtime suffix) — require at least one match.
                    String prefix = key;
                    boolean anyMatch = lang.keySet().stream().anyMatch(k -> k.startsWith(prefix));
                    if (!anyMatch) {
                        failures.add(file + " :: dynamic lang-key prefix \"" + prefix
                                + "\" matches no entries in en_us.json");
                    }
                } else if (!lang.containsKey(key)) {
                    failures.add(file + " :: lang key \"" + key
                            + "\" is referenced but not defined in en_us.json");
                }
            }
        }

        assertTrue(failures.isEmpty(),
                "Dangling / typo'd lang-key references in client code:\n  "
                        + String.join("\n  ", failures));
    }

    /**
     * For every {@code gui.futureshops.player_shop.result.*} and
     * {@code gui.futureshops.player_shop.buyer.*} key, scan client code for any Java string
     * literal that hard-codes the English value (optionally prefixed with a §-color code).
     * Any hit is a regression — the sentence must come from the lang file via
     * {@code Component.translatable(key)}.
     */
    @Test
    void clientNeverHardCodesAnyKnownResultCodeLangValue() throws Exception {
        java.util.Map<String, String> lang = loadLangPairs();
        assertTrue(!lang.isEmpty(), "Could not parse any keys out of " + EN_US);

        // Only scan values under the two result-code namespaces — other lang families
        // (e.g. button labels) are decorative and not caught by this guard.
        java.util.Map<String, String> resultKeys = new java.util.LinkedHashMap<>();
        for (var e : lang.entrySet()) {
            String k = e.getKey();
            if (k.startsWith("gui.futureshops.player_shop.result.")
                    || k.startsWith("gui.futureshops.player_shop.buyer.")) {
                resultKeys.put(k, e.getValue());
            }
        }
        assertTrue(!resultKeys.isEmpty(),
                "No gui.futureshops.player_shop.result.* or .buyer.* keys found — "
                        + "lang file must be missing or malformed.");

        // Color-code prefixes that commonly decorate status strings in this codebase.
        String[] colorPrefixes = {"", "§a", "§c", "§e", "§6", "§9", "§b", "§d", "§f", "§7"};

        List<String> failures = new ArrayList<>();
        for (Path file : collectJava(CLIENT_ROOT)) {
            String src = stripComments(Files.readString(file));
            for (var e : resultKeys.entrySet()) {
                // Skip super-short values (1–3 chars, like an abbreviation or glyph) — they
                // are too likely to false-positive on unrelated literals.
                String value = e.getValue();
                // Strip leading §-codes so the color-prefix grid can re-apply them cleanly.
                String bare = value.replaceFirst("^(§.)+", "").trim();
                if (bare.length() < 5) continue;

                for (String prefix : colorPrefixes) {
                    String candidate = "\"" + prefix + bare + "\"";
                    int idx = src.indexOf(candidate);
                    if (idx >= 0) {
                        failures.add(file + " :: hard-codes lang-value "
                                + candidate + " for key \"" + e.getKey()
                                + "\" — look up via Component.translatable(\"" + e.getKey()
                                + "\") instead.");
                    }
                }
            }
        }

        assertTrue(failures.isEmpty(),
                "Forbidden hard-coded result-code lang values in client code:\n  "
                        + String.join("\n  ", failures));
    }

    /**
     * For every value in {@link com.enviouse.futureshops.server.shop.ShopResultCode}, assert there
     * is a matching {@code gui.futureshops.player_shop.result.<lowercase>} lang key. Enumerating
     * the enum directly — rather than regex-scraping free-form string literals — is both
     * exhaustive (no code can silently be added server-side without a translation) and
     * refactor-proof now that {@code sendResult}, {@code BarterResult.error}, and the transaction
     * response packets all flow through the enum.
     */
    @Test
    void everyServerResultCodeHasLangKey() throws Exception {
        java.util.Map<String, String> lang = loadLangPairs();

        List<String> missing = new ArrayList<>();
        for (com.enviouse.futureshops.server.shop.ShopResultCode code
                : com.enviouse.futureshops.server.shop.ShopResultCode.values()) {
            String key = code.langKey();
            if (!lang.containsKey(key)) {
                missing.add(code.name() + " (expected key: " + key + ")");
            }
        }

        assertTrue(missing.isEmpty(),
                "ShopResultCode values that have no en_us.json translation:\n  "
                        + String.join("\n  ", missing));
    }
}





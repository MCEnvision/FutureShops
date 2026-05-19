package com.enviouse.futureshops;

import org.junit.jupiter.api.Test;

import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Invariant guard for the command → lang-key refactor.
 *
 * <p>Every player-facing sentence emitted from {@code src/main/java/com/enviouse/futureshops/command/}
 * must flow through {@link net.minecraft.network.chat.Component#translatable(String, Object...)}
 * (or a {@code Component.literal("")} container used purely to chain translatable parts together),
 * never {@code Component.literal("Some English text.")}.
 *
 * <p>Exceptions: command files may still use {@code Commands.literal("subcommand")} for the Brigadier
 * node names (those are protocol identifiers, not display text); and literal {@code "\n"} / empty
 * strings are allowed for structural glue in multi-part banners. The scan explicitly targets
 * {@code net.minecraft.network.chat.Component.literal("...")} call sites whose argument contains
 * a letter — a good heuristic for "English sentence" vs. structural glue.
 *
 * <p>Two files are currently on the grandfather list:
 * <ul>
 *   <li>{@code ShopAdminCommand.java} — admin-only, 50+ messages; follow-up refactor.</li>
 * </ul>
 * Remove entries from {@link #GRANDFATHERED} as they get migrated.
 */
public class CommandLangKeyCoverageTest {

    private static final Path COMMAND_ROOT =
            Path.of("src/main/java/com/enviouse/futureshops/command");

    private static final Path SCREEN_ROOT =
            Path.of("src/main/java/com/enviouse/futureshops/client/screen");

    private static final Path EN_US =
            Path.of("src/main/resources/assets/futureshops/lang/en_us.json");

    /** Command files still allowed to hard-code English while their refactor is pending. */
    private static final List<String> GRANDFATHERED = List.of();

    /**
     * Screen files still allowed to hard-code English while their refactor is pending.
     * Any <strong>new</strong> screen file added under {@link #SCREEN_ROOT} that is not on
     * this list must route player-visible text through {@code Component.translatable(...)}.
     * Remove entries from this list as each screen is migrated — the test will then
     * permanently guard that file against regressions.
     */
    private static final List<String> SCREEN_GRANDFATHERED = List.of();

    /**
     * Matches {@code Component.literal("…")} — we then inspect the arg to decide if it's a
     * sentence or a structural literal.
     */
    private static final Pattern LITERAL_CTOR =
            Pattern.compile("Component\\.literal\\(\\s*\"((?:[^\"\\\\]|\\\\.)*)\"\\s*\\)");

    /**
     * A literal is a "sentence" if (after decoding Java escapes and stripping Minecraft
     * <code>§</code>-style color/format codes) it contains at least one alphabetic char.
     *
     * <p>Stripping <code>§x</code> pairs is important for screen code: literals like
     * {@code "§c✕"} or {@code "§7← Back"} are glyph/decoration payloads whose only
     * letters come from the formatting marker — we don't want the test to demand that
     * a bare glyph become a translatable key. After stripping, {@code "§c✕"} → {@code "✕"}
     * (no letters → not a sentence), while {@code "§a$ Buy"} → {@code "$ Buy"} still
     * contains real alphabetic text and gets flagged.
     */
    private static boolean isSentence(String raw) {
        // Decode the common Java escapes so "\n" / "\t" / "\\" / "\"" don't falsely trip
        // the letter check (the literal two-char sequence {'\\','n'} must NOT count as
        // containing the letter 'n').
        StringBuilder decoded = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\\' && i + 1 < raw.length()) {
                char next = raw.charAt(i + 1);
                switch (next) {
                    case 'n': decoded.append('\n'); i++; continue;
                    case 't': decoded.append('\t'); i++; continue;
                    case 'r': decoded.append('\r'); i++; continue;
                    case '"': decoded.append('"'); i++; continue;
                    case '\\': decoded.append('\\'); i++; continue;
                    default: // leave as-is; `u`-escapes handled below by appending the raw chars
                }
            }
            decoded.append(c);
        }
        // Strip Minecraft §-style color/format codes (§a, §c, §r, §l, etc.) — they're
        // presentation markup, not English text.
        String stripped = decoded.toString().replaceAll("\u00a7.", "");
        for (int i = 0; i < stripped.length(); i++) {
            if (Character.isLetter(stripped.charAt(i))) return true;
        }
        return false;
    }

    @Test
    void noCommandFileHardCodesEnglishSentences() throws Exception {
        List<String> failures = new ArrayList<>();
        List<Path> sources = collectJava(COMMAND_ROOT);
        assertTrue(!sources.isEmpty(), "No command sources found under " + COMMAND_ROOT);

        for (Path file : sources) {
            String name = file.getFileName().toString();
            if (GRANDFATHERED.contains(name)) continue;

            String src = stripComments(Files.readString(file));
            Matcher m = LITERAL_CTOR.matcher(src);
            while (m.find()) {
                String arg = m.group(1);
                if (isSentence(arg)) {
                    failures.add(file + " :: Component.literal(\"" + arg + "\") — "
                            + "use Component.translatable(\"command.futureshops.<key>\") instead.");
                }
            }
        }

        assertTrue(failures.isEmpty(),
                "Command files still hard-coding English sentences:\n  "
                        + String.join("\n  ", failures));
    }

    @Test
    void everyCommandLangKeyReferenceResolves() throws Exception {
        Map<String, String> lang = loadLangPairs(EN_US);
        assertTrue(!lang.isEmpty(), "Could not parse any keys out of " + EN_US);

        Pattern ref = Pattern.compile(
                "Component\\.translatable\\(\\s*\"(command\\.futureshops\\.[^\"]+)\"");

        List<String> failures = new ArrayList<>();
        for (Path file : collectJava(COMMAND_ROOT)) {
            String src = stripComments(Files.readString(file));
            Matcher m = ref.matcher(src);
            while (m.find()) {
                String key = m.group(1);
                if (!lang.containsKey(key)) {
                    failures.add(file + " :: lang key \"" + key
                            + "\" is referenced but not defined in en_us.json");
                }
            }
        }

        assertTrue(failures.isEmpty(),
                "Dangling / typo'd command lang-key references:\n  "
                        + String.join("\n  ", failures));
    }

    /**
     * Ratchet-forward invariant for the client-screen layer.
     *
     * <p>Every screen file <strong>not</strong> on {@link #SCREEN_GRANDFATHERED} must route
     * player-visible text through {@code Component.translatable(...)} — the same rule the
     * command scanner enforces. The grandfather list is seeded with every screen that had
     * hard-coded English at the time the test was added; as each screen is migrated the
     * file is removed from the list and becomes permanently guarded.
     *
     * <p>This test prevents regressions in two ways:
     * <ol>
     *   <li><em>Already-clean screens stay clean</em> — any new {@code Component.literal("English")}
     *       in {@code ConfirmationModal}, {@code ShopScreenMarker}, {@code ShopUiUtil}, or any
     *       other un-grandfathered screen fails the build.</li>
     *   <li><em>New screens start translation-ready</em> — any screen file added under
     *       {@link #SCREEN_ROOT} without a {@link #SCREEN_GRANDFATHERED} entry must be
     *       translation-clean from day one.</li>
     * </ol>
     */
    @Test
    void noNonGrandfatheredScreenHardCodesEnglishSentences() throws Exception {
        List<String> failures = new ArrayList<>();
        List<Path> sources = collectJava(SCREEN_ROOT);
        assertTrue(!sources.isEmpty(), "No screen sources found under " + SCREEN_ROOT);

        for (Path file : sources) {
            String name = file.getFileName().toString();
            if (SCREEN_GRANDFATHERED.contains(name)) continue;

            String src = stripComments(Files.readString(file));
            Matcher m = LITERAL_CTOR.matcher(src);
            while (m.find()) {
                String arg = m.group(1);
                if (isSentence(arg)) {
                    failures.add(file + " :: Component.literal(\"" + arg + "\") — "
                            + "use Component.translatable(\"gui.futureshops.<key>\") instead.");
                }
            }
        }

        assertTrue(failures.isEmpty(),
                "Client screens (non-grandfathered) hard-coding English sentences:\n  "
                        + String.join("\n  ", failures));
    }

    /**
     * Reverse direction of {@code everyServerResultCodeHasLangKey} in
     * {@code BuyPacketCallSiteTest}: every lang key under the
     * {@code gui.futureshops.player_shop.result.*} namespace must correspond to a defined
     * {@link com.enviouse.futureshops.server.shop.ShopResultCode} constant. Catches the
     * "enum constant deleted but nobody cleaned up its lang line" case, so the two tests
     * together bidirectionally pin the enum ↔ lang mapping.
     */
    @Test
    void everyResultLangKeyMapsToExistingShopResultCode() throws Exception {
        Map<String, String> lang = loadLangPairs(EN_US);
        assertTrue(!lang.isEmpty(), "Could not parse any keys out of " + EN_US);

        String prefix = "gui.futureshops.player_shop.result.";
        java.util.Set<String> validSuffixes = new java.util.HashSet<>();
        for (com.enviouse.futureshops.server.shop.ShopResultCode code
                : com.enviouse.futureshops.server.shop.ShopResultCode.values()) {
            validSuffixes.add(code.name().toLowerCase(Locale.ROOT));
        }

        List<String> orphans = new ArrayList<>();
        for (String key : lang.keySet()) {
            if (!key.startsWith(prefix)) continue;
            String suffix = key.substring(prefix.length());
            if (!validSuffixes.contains(suffix)) {
                orphans.add(key + " (no matching ShopResultCode." + suffix.toUpperCase(Locale.ROOT) + " constant)");
            }
        }

        assertTrue(orphans.isEmpty(),
                "Orphan result-code lang keys (no matching enum constant):\n  "
                        + String.join("\n  ", orphans));
    }

    // ── helpers ──────────────────────────────────────────────────────────

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
        String noBlocks = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL).matcher(src).replaceAll("");
        return noBlocks.replaceAll("//[^\\n]*", "");
    }

    private static Map<String, String> loadLangPairs(Path langFile) throws Exception {
        String json = Files.readString(langFile);
        Pattern kv = Pattern.compile(
                "\"((?:gui|command)\\.futureshops\\.[^\"]+)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"",
                Pattern.DOTALL);
        Matcher m = kv.matcher(json);
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        while (m.find()) out.put(m.group(1), m.group(2));
        // Prevent "unused import" warnings in tooling that tracks Locale.
        if (out.isEmpty()) Locale.getDefault();
        return out;
    }
}



package com.enviouse.futureshops;

import org.junit.jupiter.api.Test;

import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports lang-file entries that appear to never be referenced from any source tree.
 *
 * <p><strong>Warning-level only</strong>: this test never fails — orphan keys are often
 * intentional (platform-specific resources, planned-but-unimplemented features,
 * translator conveniences) and blocking the build on them would be too noisy. The
 * report is printed to {@code System.out} so it shows up in CI logs and IDE test
 * output without breaking the red/green signal.
 *
 * <p>Run with {@code ./gradlew.bat test --info} (or just watch the test task output)
 * to see the unused-keys summary.
 */
public class LangDeadKeyReportTest {

    private static final Path EN_US = Path.of("src/main/resources/assets/futureshops/lang/en_us.json");

    /** Roots scanned for key references. */
    private static final List<Path> SOURCE_ROOTS = List.of(
            Path.of("src/main/java"),
            Path.of("src/main/resources"),
            Path.of("src/test/java"),
            Path.of("src/generated/resources"));

    @Test
    void reportUnreferencedLangKeys() throws Exception {
        if (!Files.exists(EN_US)) {
            System.out.println("[dead-key report] en_us.json missing — skipping.");
            return;
        }

        Map<String, String> keys = loadKeys(EN_US);
        Set<String> unreferenced = new LinkedHashSet<>(keys.keySet());

        // Build a single concatenated corpus of all source files so key lookups are
        // O(files + keys) rather than O(files × keys). We just need substring matches.
        StringBuilder corpus = new StringBuilder(1 << 20);
        int filesScanned = 0;
        for (Path root : SOURCE_ROOTS) {
            if (!Files.exists(root)) continue;
            List<Path> files = collectSearchable(root);
            for (Path f : files) {
                // Exclude the lang file itself — otherwise every key looks "referenced".
                if (f.toAbsolutePath().equals(EN_US.toAbsolutePath())) continue;
                corpus.append(Files.readString(f)).append('\n');
                filesScanned++;
            }
        }

        String fullText = corpus.toString();
        for (String key : keys.keySet()) {
            if (fullText.contains(key)) {
                unreferenced.remove(key);
            }
        }

        // Consider any dynamic prefix a match for every key under it.
        // e.g. "gui.futureshops.player_shop.buyer." being referenced makes every
        // buyer.* key count as referenced.
        Pattern dynamic = Pattern.compile("\"(gui\\.futureshops\\.[A-Za-z0-9_.]+\\.)\"");
        Matcher dm = dynamic.matcher(fullText);
        Set<String> dynamicPrefixes = new LinkedHashSet<>();
        while (dm.find()) dynamicPrefixes.add(dm.group(1));
        List<String> still = new ArrayList<>();
        for (String key : unreferenced) {
            boolean coveredByPrefix = dynamicPrefixes.stream().anyMatch(key::startsWith);
            if (!coveredByPrefix) still.add(key);
        }

        System.out.println();
        System.out.println("══════════════════════════════════════════════════════════════");
        System.out.println(" [dead-key report] en_us.json = " + keys.size() + " keys");
        System.out.println(" Sources scanned: " + filesScanned + " files across " + SOURCE_ROOTS.size() + " roots");
        System.out.println(" Dynamic prefixes detected: " + dynamicPrefixes.size());
        if (still.isEmpty()) {
            System.out.println(" ✓ Every lang key is referenced. Clean!");
        } else {
            System.out.println(" ⚠ Potentially unused (" + still.size() + " keys):");
            for (String k : still) {
                System.out.println("     - " + k);
            }
            System.out.println(" NOTE: false positives happen when keys are loaded dynamically,");
            System.out.println("       referenced only in generated/datagen output, or reserved");
            System.out.println("       for future features. Review before deleting.");
        }
        System.out.println("══════════════════════════════════════════════════════════════");
        System.out.println();
    }

    private static Map<String, String> loadKeys(Path langFile) throws Exception {
        String json = Files.readString(langFile);
        Pattern kv = Pattern.compile(
                "\"(gui\\.futureshops\\.[^\"]+)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"",
                Pattern.DOTALL);
        Matcher m = kv.matcher(json);
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        while (m.find()) out.put(m.group(1), m.group(2));
        return out;
    }

    private static List<Path> collectSearchable(Path root) throws Exception {
        List<Path> out = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String name = file.toString().toLowerCase();
                if (name.endsWith(".java") || name.endsWith(".json") || name.endsWith(".toml")
                        || name.endsWith(".mcmeta") || name.endsWith(".txt")) {
                    out.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return out;
    }
}


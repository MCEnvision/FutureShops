package com.enviouse.futureshops;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.enviouse.futureshops.config.AuctionHouseConfig;
import com.enviouse.futureshops.config.BazaarConfig;
import com.enviouse.futureshops.config.EscrowConfig;
import net.minecraftforge.common.ForgeConfigSpec;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedTomlWarningTest {
    @TempDir
    Path directory;

    @ParameterizedTest(name = "{0}")
    @MethodSource("serverSpecs")
    void exactForeignCurrencyWarningIsWrittenToToml(
            String fileName,
            String expectedSetting,
            ForgeConfigSpec spec
    ) throws Exception {
        Path path = directory.resolve(fileName);
        try (CommentedFileConfig generated = CommentedFileConfig
                .builder(path, TomlFormat.instance())
                .sync()
                .build()) {
            spec.correct(generated);
            assertTrue(spec.isCorrect(generated), fileName);
            generated.save();
        }

        try (CommentedFileConfig reloaded = CommentedFileConfig
                .builder(path, TomlFormat.instance())
                .sync()
                .build()) {
            reloaded.load();
            assertTrue(spec.isCorrect(reloaded), fileName);
        }

        String toml = Files.readString(path);
        long occurrences = Pattern.compile(Pattern.quote(Config.FOREIGN_CURRENCY_WARNING))
                .matcher(toml)
                .results()
                .count();
        assertEquals(1L, occurrences, fileName);
        assertTrue(toml.lines().anyMatch(line -> line.stripLeading().startsWith("#")
                && line.contains(Config.FOREIGN_CURRENCY_WARNING)), fileName);
        assertTrue(toml.contains(expectedSetting), fileName);
    }

    private static Stream<Arguments> serverSpecs() {
        return Stream.of(
                Arguments.of(Config.FILE_NAME,
                        "provider = \"futureshops\"", Config.SPEC),
                Arguments.of(EscrowConfig.FILE_NAME,
                        "physical_refund_policy = \"wallet_claim\"", EscrowConfig.SPEC),
                Arguments.of(AuctionHouseConfig.FILE_NAME,
                        "allow_physical = true", AuctionHouseConfig.SPEC),
                Arguments.of(BazaarConfig.FILE_NAME,
                        "allow_physical = true", BazaarConfig.SPEC)
        );
    }
}

package com.enviouse.futureshops.api.economy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionalProviderFixtureTest {
    @TempDir
    Path temp;

    @Test
    void separatelyCompiledRegistrantCommitsAndLooksUpAfterFreshProcess() throws Exception {
        Path classes = temp.resolve("classes");
        Files.createDirectories(classes);
        Path harness = temp.resolve("Harness.java");
        Files.writeString(harness, """
                import fixture.TransactionalProviderRegistrant;
                import com.enviouse.futureshops.api.economy.*;
                import java.nio.file.Path;
                import java.util.UUID;
                public final class Harness {
                    public static void main(String[] args) throws Exception {
                        Path db = Path.of(args[1]);
                        UUID id = UUID.fromString(args[2]);
                        var provider = new TransactionalProviderRegistrant.TransactionalProvider(db);
                        var request = MutationRequest.forPlayer(new RequestId(id), id, 125L,
                                MutationKind.WITHDRAW);
                        if ("apply".equals(args[0])) {
                            System.out.println(provider.withdraw(request).status());
                        } else {
                            System.out.println(provider.lookup(request.requestId()).status());
                        }
                    }
                }
                """);
        String classpath = System.getProperty("java.class.path");
        Path fixture = Path.of("src/test/fixtures/transactional-provider/"
                + "TransactionalProviderRegistrant.java").toAbsolutePath();
        run("javac", "-cp", classpath, "-d", classes.toString(), fixture.toString(),
                harness.toString());
        UUID requestId = UUID.randomUUID();
        Path database = temp.resolve("provider.db");
        String applied = run("java", "-cp", classes + java.io.File.pathSeparator + classpath,
                "Harness", "apply", database.toString(), requestId.toString());
        String lookedUp = run("java", "-cp", classes + java.io.File.pathSeparator + classpath,
                "Harness", "lookup", database.toString(), requestId.toString());
        assertEquals("CONFIRMED", applied.trim());
        assertEquals("CONFIRMED", lookedUp.trim());
        assertTrue(Files.size(database) > 0);
    }

    private static String run(String executable, String... arguments) throws Exception {
        Process process = new ProcessBuilder(executable)
                .command(java.util.stream.Stream.concat(java.util.stream.Stream.of(executable),
                        java.util.Arrays.stream(arguments)).toList())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), output);
        return output;
    }
}

package com.enviouse.futureshops.server.escrow.runtime;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerShopOfferReplayRateGateTest {
    @Test
    void singleOfferRateGatePrecedesCommitReplayLookup()
            throws IOException {
        assertRateGatePrecedesReplay(
                "ServerShopOfferService.java",
                "ServerShopOfferCommitSavedData.get(");
    }

    @Test
    void cartOfferRateGatePrecedesCommitReplayLookup()
            throws IOException {
        assertRateGatePrecedesReplay(
                "ServerShopOfferCartService.java",
                "ServerShopOfferCartCommitSavedData.get(");
    }

    @Test
    void singleCapacityGatePrecedesQuoteAndEscrowMutation()
            throws IOException {
        assertCapacityGatePrecedesMutation(
                "ServerShopOfferService.java",
                "ensureSingleCapacity(");
    }

    @Test
    void cartCapacityGatePrecedesQuoteAndEscrowMutation()
            throws IOException {
        assertCapacityGatePrecedesMutation(
                "ServerShopOfferCartService.java",
                "ensureCartCapacity(");
    }

    @Test
    void lowerEscrowIdentityCheckPrecedesSingleQuote()
            throws IOException {
        assertIdentityCheckPrecedesQuote(
                "ServerShopOfferService.java",
                "ServerShopOfferCartCommitSavedData.get(");
    }

    @Test
    void lowerEscrowIdentityCheckPrecedesCartQuote()
            throws IOException {
        assertIdentityCheckPrecedesQuote(
                "ServerShopOfferCartService.java",
                "ServerShopOfferCommitSavedData.get(");
    }

    @Test
    void singleAcceptedQuoteAndEventFailuresAreDurable()
            throws IOException {
        assertAcceptedFailuresAreDurable(
                "ServerShopOfferService.java");
    }

    @Test
    void cartAcceptedQuoteAndEventFailuresAreDurable()
            throws IOException {
        assertAcceptedFailuresAreDurable(
                "ServerShopOfferCartService.java");
    }

    private static void assertRateGatePrecedesReplay(
            String fileName,
            String replayMarker
    ) throws IOException {
        Path sourcePath = Path.of(
                "src/main/java/com/enviouse/futureshops/server/"
                        + "escrow/runtime/" + fileName);
        String source = Files.readString(sourcePath);
        int gate = source.indexOf(
                "ServerRequestSecurityManager.tryAcquire(");
        int replay = source.indexOf(replayMarker);

        assertTrue(gate >= 0, "Expected a server request rate gate");
        assertTrue(replay >= 0, "Expected a durable replay lookup");
        assertTrue(gate < replay,
                "Rate gate must run before durable replay decoding");
    }

    private static void assertCapacityGatePrecedesMutation(
            String fileName,
            String capacityMarker
    ) throws IOException {
        Path sourcePath = Path.of(
                "src/main/java/com/enviouse/futureshops/server/"
                        + "escrow/runtime/" + fileName);
        String source = Files.readString(sourcePath);
        int capacity = source.lastIndexOf(capacityMarker,
                source.indexOf("Quote quote = quote("));
        int quote = source.indexOf("Quote quote = quote(");
        int escrow = source.indexOf(
                "PlayerShopLiveEscrowService.execute(");

        assertTrue(capacity >= 0,
                "Expected a replay retention capacity gate");
        assertTrue(quote >= 0, "Expected a server quote");
        assertTrue(escrow >= 0, "Expected an escrow mutation");
        assertTrue(capacity < quote,
                "Capacity must be reserved before quote preparation");
        assertTrue(capacity < escrow,
                "Capacity must be reserved before escrow mutation");
    }

    private static void assertIdentityCheckPrecedesQuote(
            String fileName,
            String otherKindMarker
    ) throws IOException {
        Path sourcePath = Path.of(
                "src/main/java/com/enviouse/futureshops/server/"
                        + "escrow/runtime/" + fileName);
        String source = Files.readString(sourcePath);
        int otherKind = source.indexOf(otherKindMarker);
        int lowerEscrow = source.indexOf(
                "runtime.playerShopEscrowEntry(");
        int quote = source.indexOf("Quote quote = quote(");

        assertTrue(otherKind >= 0,
                "Expected a cross kind request identity check");
        assertTrue(lowerEscrow >= 0,
                "Expected a lower escrow request identity check");
        assertTrue(otherKind < quote,
                "Cross kind identity must be checked before quoting");
        assertTrue(lowerEscrow < quote,
                "Lower escrow identity must be checked before quoting");
    }

    private static void assertAcceptedFailuresAreDurable(
            String fileName
    ) throws IOException {
        Path sourcePath = Path.of(
                "src/main/java/com/enviouse/futureshops/server/"
                        + "escrow/runtime/" + fileName);
        String source = Files.readString(sourcePath);
        int execute = source.indexOf("public static Result execute(");
        int gate = source.indexOf(
                "ServerRequestSecurityManager.tryAcquire(", execute);
        int quote = source.indexOf("Quote quote = quote(", gate);
        int prepared = source.indexOf(
                "preparedEntries.prepare(", quote);
        String preAccepted = source.substring(execute, quote);
        String accepted = source.substring(quote, prepared);

        assertTrue(gate < quote,
                "Rate gate must precede accepted request failures");
        assertFalse(preAccepted.contains(
                "failAcceptedRequest("),
                "Preacceptance failures must remain retryable");
        assertTrue(accepted.contains(
                "failAcceptedRequest("));
        assertTrue(accepted.contains(
                "CANCELLED_BY_EVENT"));
        assertTrue(accepted.contains(
                "INVALID_REQUEST"));
    }
}

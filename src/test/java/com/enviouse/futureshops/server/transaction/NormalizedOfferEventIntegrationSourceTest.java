package com.enviouse.futureshops.server.transaction;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NormalizedOfferEventIntegrationSourceTest {
    @Test
    void serverSingleAuthorizesBeforePreparationAndPostsAfterCommit()
            throws Exception {
        String source = source(
                "server/escrow/runtime/ServerShopOfferService.java");
        int quote = source.indexOf("Quote quote = quote(");
        int pre = source.indexOf("firePreEvent(", quote);
        int prepare = source.indexOf("prepareIntent(", quote);
        int commit = source.indexOf(
                "ServerShopOfferCommitSavedData.get(", prepare);
        int post = source.indexOf("firePostEvent(", commit);

        assertTrue(quote >= 0);
        assertTrue(pre > quote);
        assertTrue(prepare > pre);
        assertTrue(commit > prepare);
        assertTrue(post > commit);
        int sell = source.indexOf(
                "ServerShopOfferIntentFactory.sell(", prepare);
        int authorizedPayout = source.indexOf(
                "quote.moneyTotalMinorUnits()", sell);
        assertTrue(sell > prepare);
        assertTrue(authorizedPayout > sell);
    }

    @Test
    void serverCartAuthorizesEveryLineBeforeIntent()
            throws Exception {
        String source = source(
                "server/escrow/runtime/ServerShopOfferCartService.java");
        int quote = source.indexOf("Quote quote = quote(");
        int pre = source.indexOf(
                "NormalizedOfferTransactionEvents.fireAcquirePre(",
                quote);
        int intent = source.indexOf(
                "ServerShopOfferIntentFactory.acquireCart(", quote);
        int commit = source.indexOf(
                "ServerShopOfferCartCommitSavedData.get(", intent);
        int post = source.indexOf("firePostEvents(", commit);

        assertTrue(quote >= 0);
        assertTrue(pre > quote);
        assertTrue(intent > pre);
        assertTrue(commit > intent);
        assertTrue(post > commit);
    }

    @Test
    void playerOfferReplayRecordsHistoryWithoutRepeatingPostEvents()
            throws Exception {
        String source = source(
                "server/shop/PlayerShopEscrowTransactionService.java");
        int replay = source.indexOf(
                "private static void resumeExistingOffer(");
        int history = source.indexOf(
                "recordOfferHistory(actor, intent, result)", replay);
        int replayEnd = source.indexOf(
                "static boolean replayRequestMatches(", replay);
        int post = source.indexOf("fireOfferPost(", replay);

        assertTrue(replay >= 0);
        assertTrue(history > replay);
        assertTrue(replayEnd > history);
        assertTrue(post < 0 || post > replayEnd);
    }

    @Test
    void historyStoresEveryExactBundleComponentAndComparison()
            throws Exception {
        String source = source(
                "server/transaction/TransactionHistoryService.java");

        assertTrue(source.contains(
                "for (int index = 0; index < exactComponents.size();"));
        assertTrue(source.contains("component.exactNbt()"));
        assertTrue(source.contains(
                "snapshot.comparisonRevisions()"));
        assertTrue(source.contains(
                "component.role().name().toLowerCase("));
    }

    private static String source(String relative) throws Exception {
        return Files.readString(Path.of(
                "src/main/java/com/enviouse/futureshops/",
                relative));
    }
}

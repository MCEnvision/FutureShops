package com.enviouse.futureshops.server.market;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketPageServiceClaimPagingSourceTest {
    @Test
    void serviceUsesRepositoryPagesOnlyForClaimViews()
            throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/enviouse/futureshops/server/market/MarketPageService.java"));
        int accessGate = source.indexOf(
                "MarketModuleAccessPolicy.pageAccess");
        int claimBranch = source.indexOf(
                "if (\"claims\".equals(query.view()))");
        int nonClaimBranch = source.indexOf("} else {", claimBranch);
        int actionPolicy = source.indexOf(
                "MarketModuleAccessPolicy\n                .applyPageActions",
                nonClaimBranch);
        String claims = source.substring(claimBranch, nonClaimBranch);
        String nonClaims = source.substring(nonClaimBranch,
                actionPolicy);

        assertTrue(accessGate >= 0 && accessGate < claimBranch);
        assertTrue(actionPolicy > nonClaimBranch);
        assertTrue(claims.contains(".openPageFor("));
        assertTrue(claims.contains(
                "query.pageIndex(), query.pageSize()"));
        assertTrue(claims.contains("MarketPageProjector.bazaar("));
        assertTrue(claims.contains("MarketPageProjector.auction("));
        assertFalse(claims.contains("pendingFor("));
        assertTrue(nonClaims.contains("List.of()"));
        assertFalse(nonClaims.contains("openPageFor("));
    }
}

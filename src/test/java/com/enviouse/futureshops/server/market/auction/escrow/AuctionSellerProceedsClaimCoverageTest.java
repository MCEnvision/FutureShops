package com.enviouse.futureshops.server.market.auction.escrow;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuctionSellerProceedsClaimCoverageTest {
    private static final Path PACKAGE = Path.of(
            "src/main/java/com/enviouse/futureshops/server/market/auction/escrow");

    @Test
    void settlementPlannerOnlyPaysSellerThroughFullNetClaim()
            throws IOException {
        String source = read("AuctionEscrowLifecyclePlanner.java");
        String settlement = section(source,
                "private static Settlement settlement(",
                "private static void addRefund(");

        assertTrue(settlement.contains("sellerProceedsClaimId("));
        assertTrue(settlement.contains(".seller.proceeds"));
        assertTrue(settlement.contains("sellerNet, sellerNet"));
        assertTrue(settlement.contains("ClaimStatus.PENDING"));
        assertTrue(settlement.contains(
                "AuctionEscrowLedgerAccounts.claim("));
        assertFalse(settlement.contains("allocatePayout("));
        assertFalse(settlement.contains(
                "AuctionEscrowLedgerAccounts.debt("));
        assertFalse(settlement.contains(
                "AuctionEscrowLedgerAccounts.wallet(\n                    listing.sellerId())"));
        assertFalse(source.contains("List.of(buyerWallet, sellerWallet)"));
        assertFalse(source.contains("List.of(sellerWallet)"));
        assertFalse(source.contains("List.of(seller)"));
    }

    @Test
    void conservationRequiresTheSameDeterministicFullNetClaim()
            throws IOException {
        String source = read("AuctionEscrowConservationValidator.java");
        String settlement = section(source,
                "private static void validateSettlement(",
                "private static void validateRefund(");

        assertTrue(settlement.contains("sellerProceedsClaimId("));
        assertTrue(settlement.contains(".seller.proceeds"));
        assertTrue(settlement.contains("sellerNet"));
        assertTrue(settlement.contains("int requiredWallets = buyNow ? 1 : 0"));
        assertFalse(settlement.contains("allocatePayout("));
        assertFalse(settlement.contains(
                "AuctionEscrowLedgerAccounts.debt("));
        assertFalse(settlement.contains(
                "AuctionEscrowLedgerAccounts.wallet(\n                    listing.sellerId())"));
    }

    @Test
    void sellerClaimIdentityHasOneStableDerivation() throws IOException {
        String source = read("AuctionEscrowIds.java");

        assertTrue(source.contains("sellerProceedsClaimId("));
        assertTrue(source.contains("seller proceeds claim"));
        assertTrue(source.contains(
                "return sellerProceedsClaimId(requestId, ownerId)"));
    }

    private static String read(String name) throws IOException {
        return Files.readString(PACKAGE.resolve(name));
    }

    private static String section(
            String source,
            String start,
            String end
    ) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from);
        if (from < 0 || to < 0) {
            throw new IllegalArgumentException(
                    "Auction source section is missing");
        }
        return source.substring(from, to);
    }
}

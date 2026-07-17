package com.enviouse.futureshops.server.escrow.claim;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record OpenClaimPage(
        UUID ownerId,
        String sourcePrefix,
        int pageIndex,
        int pageSize,
        int totalResults,
        int pageCount,
        List<EscrowClaim> claims
) {
    public static final int MAXIMUM_PAGE_SIZE = 256;
    static final Comparator<EscrowClaim> ORDER = Comparator
            .comparing(EscrowClaim::createdAt)
            .thenComparing(value -> value.claimId().toString());

    public OpenClaimPage {
        ownerId = Objects.requireNonNull(ownerId, "ownerId");
        sourcePrefix = requireSourcePrefix(sourcePrefix);
        claims = List.copyOf(Objects.requireNonNull(claims, "claims"));
        if (pageIndex < 0 || pageSize <= 0
                || pageSize > MAXIMUM_PAGE_SIZE
                || totalResults < 0
                || pageCount != pageCount(totalResults, pageSize)) {
            throw new IllegalArgumentException(
                    "Open claim page values are invalid");
        }
        long start = (long) pageIndex * pageSize;
        int expectedSize = start >= totalResults ? 0
                : (int) Math.min(pageSize, totalResults - start);
        if (claims.size() != expectedSize) {
            throw new IllegalArgumentException(
                    "Open claim page size is invalid");
        }
        EscrowClaim previous = null;
        for (EscrowClaim claim : claims) {
            if (!claim.ownerId().equals(ownerId)
                    || !claim.sourceKey().startsWith(sourcePrefix)
                    || !claim.kind().publiclyVisible()
                    || !open(claim.status())
                    || (previous != null
                    && ORDER.compare(previous, claim) > 0)) {
                throw new IllegalArgumentException(
                        "Open claim page entry is invalid");
            }
            previous = claim;
        }
    }

    static int pageCount(int totalResults, int pageSize) {
        if (totalResults == 0) {
            return 0;
        }
        return Math.addExact((totalResults - 1) / pageSize, 1);
    }

    static String requireSourcePrefix(String value) {
        String prefix = Objects.requireNonNull(value, "sourcePrefix");
        if (prefix.length() > 192 || !prefix.equals(prefix.strip())) {
            throw new IllegalArgumentException(
                    "Open claim source prefix is invalid");
        }
        return prefix;
    }

    static boolean open(ClaimStatus status) {
        return status == ClaimStatus.PENDING
                || status == ClaimStatus.PARTIALLY_DELIVERED
                || status == ClaimStatus.QUARANTINED;
    }
}

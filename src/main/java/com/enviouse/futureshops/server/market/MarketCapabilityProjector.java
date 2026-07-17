package com.enviouse.futureshops.server.market;

import com.enviouse.futureshops.client.market.MarketCapabilitiesSnapshot;
import com.enviouse.futureshops.client.market.MarketModule;
import com.enviouse.futureshops.client.market.MarketModuleAvailability;
import com.enviouse.futureshops.client.market.MarketModuleCapability;
import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class MarketCapabilityProjector {
    private MarketCapabilityProjector() {
    }

    public static MarketCapabilitiesSnapshot project(
            UUID requestId,
            long revision,
            boolean showNavigation,
            MarketModule defaultModule,
            boolean escrowReady,
            boolean bazaarEnabled,
            boolean auctionHouseEnabled,
            Branding shopBranding,
            Branding bazaarBranding,
            Branding auctionHouseBranding,
            List<EscrowClaim> ownedClaims
    ) {
        List<EscrowClaim> claims = List.copyOf(Objects.requireNonNull(
                ownedClaims, "ownedClaims"));
        return project(requestId, revision, showNavigation,
                defaultModule, escrowReady, bazaarEnabled,
                auctionHouseEnabled, shopBranding, bazaarBranding,
                auctionHouseBranding, new ClaimCounts(
                        countOpenClaims(claims),
                        countClaims(claims, "bazaar."),
                        countClaims(claims, "auction.")));
    }

    public static MarketCapabilitiesSnapshot project(
            UUID requestId,
            long revision,
            boolean showNavigation,
            MarketModule defaultModule,
            boolean escrowReady,
            boolean bazaarEnabled,
            boolean auctionHouseEnabled,
            Branding shopBranding,
            Branding bazaarBranding,
            Branding auctionHouseBranding,
            ClaimCounts claimCounts
    ) {
        Objects.requireNonNull(defaultModule, "defaultModule");
        ClaimCounts claims = Objects.requireNonNull(
                claimCounts, "claimCounts");
        long bazaarClaims = claims.bazaarOpenClaims();
        long auctionClaims = claims.auctionOpenClaims();
        long shopClaims = Math.subtractExact(Math.subtractExact(
                claims.totalOpenClaims(), bazaarClaims),
                auctionClaims);
        MarketModuleAvailability shopAvailability = availability(
                true, true, shopClaims);
        MarketModuleAvailability bazaarAvailability = availability(
                bazaarEnabled, escrowReady, bazaarClaims);
        MarketModuleAvailability auctionAvailability = availability(
                auctionHouseEnabled, escrowReady, auctionClaims);
        return project(requestId, revision, showNavigation,
                defaultModule, shopAvailability, bazaarAvailability,
                auctionAvailability, shopBranding, bazaarBranding,
                auctionHouseBranding, claims, 0L, false, "Coins", 2);
    }

    public static MarketCapabilitiesSnapshot project(
            UUID requestId,
            long revision,
            boolean showNavigation,
            MarketModule defaultModule,
            MarketModuleAvailability shopAvailability,
            MarketModuleAvailability bazaarAvailability,
            MarketModuleAvailability auctionHouseAvailability,
            Branding shopBranding,
            Branding bazaarBranding,
            Branding auctionHouseBranding,
            ClaimCounts claimCounts
    ) {
        return project(requestId, revision, showNavigation,
                defaultModule, shopAvailability, bazaarAvailability,
                auctionHouseAvailability, shopBranding,
                bazaarBranding, auctionHouseBranding, claimCounts,
                0L, false, "Coins", 2);
    }

    public static MarketCapabilitiesSnapshot project(
            UUID requestId,
            long revision,
            boolean showNavigation,
            MarketModule defaultModule,
            MarketModuleAvailability shopAvailability,
            MarketModuleAvailability bazaarAvailability,
            MarketModuleAvailability auctionHouseAvailability,
            Branding shopBranding,
            Branding bazaarBranding,
            Branding auctionHouseBranding,
            ClaimCounts claimCounts,
            long walletBalanceMinorUnits,
            boolean walletBalanceKnown,
            String currencyName,
            int currencyDecimals
    ) {
        Objects.requireNonNull(defaultModule, "defaultModule");
        ClaimCounts claims = Objects.requireNonNull(
                claimCounts, "claimCounts");
        long bazaarClaims = claims.bazaarOpenClaims();
        long auctionClaims = claims.auctionOpenClaims();
        long shopClaims = Math.subtractExact(Math.subtractExact(
                claims.totalOpenClaims(), bazaarClaims),
                auctionClaims);
        return new MarketCapabilitiesSnapshot(requestId, revision,
                showNavigation, defaultModule, walletBalanceMinorUnits,
                walletBalanceKnown, currencyName, currencyDecimals,
                List.of(
                capability(MarketModule.SHOP, shopAvailability,
                        shopClaims, revision, shopBranding),
                capability(MarketModule.BAZAAR, bazaarAvailability,
                        bazaarClaims, revision,
                        bazaarBranding),
                capability(MarketModule.AUCTION_HOUSE,
                        auctionHouseAvailability, auctionClaims,
                        revision, auctionHouseBranding)));
    }

    private static MarketModuleCapability capability(
            MarketModule module,
            MarketModuleAvailability requestedAvailability,
            long claims,
            long revision,
            Branding branding
    ) {
        Objects.requireNonNull(branding, "branding");
        MarketModuleAvailability availability = normalizeAvailability(
                requestedAvailability, claims);
        return new MarketModuleCapability(module, availability,
                branding.displayName(), branding.accentHex(), claims,
                revision);
    }

    private static MarketModuleAvailability availability(
            boolean enabled,
            boolean escrowReady,
            long claims
    ) {
        return enabled && escrowReady
                ? MarketModuleAvailability.ENABLED
                : claims > 0L
                ? MarketModuleAvailability.CLAIMS_ONLY
                : MarketModuleAvailability.DISABLED;
    }

    private static MarketModuleAvailability normalizeAvailability(
            MarketModuleAvailability requested,
            long claims
    ) {
        MarketModuleAvailability availability = Objects.requireNonNull(
                requested, "requested");
        if ((availability == MarketModuleAvailability.DISABLED
                || availability == MarketModuleAvailability.HIDDEN)
                && claims > 0L) {
            return MarketModuleAvailability.CLAIMS_ONLY;
        }
        if (availability == MarketModuleAvailability.CLAIMS_ONLY
                && claims == 0L) {
            return MarketModuleAvailability.DISABLED;
        }
        return availability;
    }

    private static long countClaims(
            List<EscrowClaim> claims,
            String... prefixes
    ) {
        long count = 0L;
        for (EscrowClaim claim : claims) {
            if (!open(claim.status())) {
                continue;
            }
            for (String prefix : prefixes) {
                if (claim.sourceKey().startsWith(prefix)) {
                    count = Math.addExact(count, 1L);
                    break;
                }
            }
        }
        return count;
    }

    private static long countOpenClaims(List<EscrowClaim> claims) {
        long count = 0L;
        for (EscrowClaim claim : claims) {
            if (open(claim.status())) {
                count = Math.addExact(count, 1L);
            }
        }
        return count;
    }

    private static boolean open(ClaimStatus status) {
        return status == ClaimStatus.PENDING
                || status == ClaimStatus.PARTIALLY_DELIVERED
                || status == ClaimStatus.QUARANTINED;
    }

    public record Branding(String displayName, String accentHex) {
        public Branding {
            displayName = Objects.requireNonNull(displayName,
                    "displayName").strip();
            accentHex = Objects.requireNonNull(accentHex,
                    "accentHex").strip();
        }
    }

    public record ClaimCounts(
            long totalOpenClaims,
            long bazaarOpenClaims,
            long auctionOpenClaims
    ) {
        public ClaimCounts {
            if (totalOpenClaims < 0L || bazaarOpenClaims < 0L
                    || auctionOpenClaims < 0L) {
                throw new IllegalArgumentException(
                        "Market claim counts must not be negative");
            }
            long categorized = Math.addExact(
                    bazaarOpenClaims, auctionOpenClaims);
            if (categorized > totalOpenClaims) {
                throw new IllegalArgumentException(
                        "Market claim counts exceed the open total");
            }
        }
    }
}

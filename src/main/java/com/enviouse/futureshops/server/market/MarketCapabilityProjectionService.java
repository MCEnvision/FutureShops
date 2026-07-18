package com.enviouse.futureshops.server.market;

import com.enviouse.futureshops.Config;
import com.enviouse.futureshops.client.market.MarketCapabilitiesSnapshot;
import com.enviouse.futureshops.client.market.MarketModule;
import com.enviouse.futureshops.client.market.MarketModuleAvailability;
import com.enviouse.futureshops.client.market.MarketModuleCapability;
import com.enviouse.futureshops.config.AuctionHouseConfig;
import com.enviouse.futureshops.config.BazaarConfig;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.S2CMarketCapabilitiesPacket;
import com.enviouse.futureshops.server.escrow.claim.ClaimSavedData;
import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.claim.OpenClaimSourceCounts;
import com.enviouse.futureshops.server.escrow.runtime.EscrowRuntimeManager;
import com.enviouse.futureshops.server.escrow.runtime.EscrowRuntimeService;
import com.enviouse.futureshops.server.escrow.runtime.EscrowWalletService;
import com.enviouse.futureshops.server.market.control.MarketControlSavedData;
import com.enviouse.futureshops.server.market.control.MarketControlState;
import com.enviouse.futureshops.server.market.control.MarketControlModule;
import com.enviouse.futureshops.server.market.control.MarketModuleControl;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class MarketCapabilityProjectionService {
    public static final String BAZAAR_CLAIM_PREFIX = "bazaar.";
    public static final String AUCTION_CLAIM_PREFIX = "auction.";

    private static final MarketCapabilityRevisionTracker REVISIONS =
            new MarketCapabilityRevisionTracker(
                    MarketCapabilityRevisionTracker.MAXIMUM_SUBJECTS);

    private MarketCapabilityProjectionService() {
    }

    public static void respond(ServerPlayer player, UUID requestId) {
        ShopPackets.sendToPlayer(Objects.requireNonNull(player, "player"),
                new S2CMarketCapabilitiesPacket(project(player,
                        requestId)));
    }

    public static MarketCapabilitiesSnapshot project(
            ServerPlayer player,
            UUID requestId
    ) {
        Objects.requireNonNull(player, "player");
        MinecraftServer server = Objects.requireNonNull(
                player.getServer(), "server");
        UUID ownerId = player.getUUID();
        OpenClaimSourceCounts claimCounts = ClaimSavedData.get(server)
                .openSourceCountsFor(ownerId, List.of(
                        BAZAAR_CLAIM_PREFIX,
                        AUCTION_CLAIM_PREFIX));
        EscrowRuntimeService runtime =
                EscrowRuntimeManager.getOrNull();
        long walletBalance = 0L;
        boolean walletBalanceKnown = false;
        if (runtime != null && runtime.isReady()) {
            walletBalance = EscrowWalletService.live().balance(ownerId);
            walletBalanceKnown = true;
        }
        Optional<MarketControlState> marketControl =
                marketControl(server);
        BazaarConfig.Branding bazaar =
                BazaarConfig.settings().branding();
        AuctionHouseConfig.Branding auction =
                AuctionHouseConfig.settings().branding();
        Projection projection = new Projection(requestId, ownerId,
                Config.showModuleNavigation(),
                MarketModule.fromId(Config.defaultModule()),
                runtime != null && runtime.isReady(),
                Config.bazaarEnabled(), Config.auctionHouseEnabled(),
                walletBalance, walletBalanceKnown,
                configuredCurrencyName(),
                Config.economyCurrencyDecimals,
                auctionDurationPresetSeconds(),
                new MarketCapabilityProjector.Branding(
                        MarketModule.SHOP.defaultDisplayName(),
                        MarketModule.SHOP.defaultAccent()),
                new MarketCapabilityProjector.Branding(
                        bazaar.displayName(), bazaar.accentColor()),
                new MarketCapabilityProjector.Branding(
                        auction.displayName(), auction.accentColor()),
                marketControl);
        return project(projection, claimCounts, REVISIONS);
    }

    public static MarketCapabilitiesSnapshot project(
            Projection projection,
            List<EscrowClaim> claims,
            MarketCapabilityRevisionTracker revisions
    ) {
        Objects.requireNonNull(projection, "projection");
        Objects.requireNonNull(revisions, "revisions");
        List<EscrowClaim> ownedClaims = List.copyOf(
                Objects.requireNonNull(claims, "claims")).stream()
                .filter(claim -> claim.ownerId().equals(
                        projection.ownerId()))
                .filter(claim -> claim.kind().publiclyVisible())
                .toList();
        return project(projection,
                new MarketCapabilityProjector.ClaimCounts(
                countOpen(ownedClaims),
                countPrefix(ownedClaims, BAZAAR_CLAIM_PREFIX),
                countPrefix(ownedClaims, AUCTION_CLAIM_PREFIX)),
                revisions);
    }

    public static MarketCapabilitiesSnapshot project(
            Projection projection,
            OpenClaimSourceCounts claimCounts,
            MarketCapabilityRevisionTracker revisions
    ) {
        Objects.requireNonNull(claimCounts, "claimCounts");
        return project(projection,
                new MarketCapabilityProjector.ClaimCounts(
                claimCounts.totalOpenClaims(),
                claimCounts.matching(BAZAAR_CLAIM_PREFIX),
                claimCounts.matching(AUCTION_CLAIM_PREFIX)),
                revisions);
    }

    public static MarketCapabilitiesSnapshot project(
            Projection projection,
            MarketCapabilityProjector.ClaimCounts claimCounts,
            MarketCapabilityRevisionTracker revisions
    ) {
        Objects.requireNonNull(projection, "projection");
        Objects.requireNonNull(claimCounts, "claimCounts");
        Objects.requireNonNull(revisions, "revisions");
        MarketModuleAvailability shopAvailability =
                MarketModuleAccessPolicy.capability(
                        MarketModule.SHOP, true,
                        projection.escrowReady(),
                        control(projection, MarketModule.SHOP),
                        Math.subtractExact(Math.subtractExact(
                                claimCounts.totalOpenClaims(),
                                claimCounts.bazaarOpenClaims()),
                                claimCounts.auctionOpenClaims()));
        MarketModuleAvailability bazaarAvailability =
                MarketModuleAccessPolicy.capability(
                        MarketModule.BAZAAR,
                        projection.bazaarEnabled(),
                        projection.escrowReady(),
                        control(projection, MarketModule.BAZAAR),
                        claimCounts.bazaarOpenClaims());
        MarketModuleAvailability auctionAvailability =
                MarketModuleAccessPolicy.capability(
                        MarketModule.AUCTION_HOUSE,
                        projection.auctionHouseEnabled(),
                        projection.escrowReady(),
                        control(projection,
                                MarketModule.AUCTION_HOUSE),
                        claimCounts.auctionOpenClaims());
        MarketCapabilitiesSnapshot unversioned = withDurations(
                MarketCapabilityProjector.project(
                        projection.requestId(), 0L,
                        projection.showNavigation(),
                        projection.defaultModule(),
                        shopAvailability, bazaarAvailability,
                        auctionAvailability,
                        projection.shopBranding(),
                        projection.bazaarBranding(),
                        projection.auctionHouseBranding(),
                        claimCounts, projection.walletBalanceMinorUnits(),
                        projection.walletBalanceKnown(),
                        projection.currencyName(),
                        projection.currencyDecimals()),
                projection.auctionDurationPresetSeconds());
        long revision = revisions.revision(projection.ownerId(),
                stateFingerprint(unversioned));
        return withDurations(MarketCapabilityProjector.project(
                projection.requestId(), revision,
                projection.showNavigation(),
                projection.defaultModule(),
                shopAvailability, bazaarAvailability,
                auctionAvailability,
                projection.shopBranding(),
                projection.bazaarBranding(),
                        projection.auctionHouseBranding(),
                        claimCounts, projection.walletBalanceMinorUnits(),
                        projection.walletBalanceKnown(),
                        projection.currencyName(),
                        projection.currencyDecimals()),
                projection.auctionDurationPresetSeconds());
    }

    public static void clearRevisionState() {
        REVISIONS.clear();
    }

    private static Optional<MarketControlState> marketControl(
            MinecraftServer server
    ) {
        try {
            return Optional.of(MarketControlSavedData.get(server)
                    .snapshot());
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private static Optional<MarketModuleControl> control(
            Projection projection,
            MarketModule module
    ) {
        return projection.marketControl().map(state -> state.module(
                switch (module) {
                    case SHOP -> MarketControlModule.SHOP;
                    case BAZAAR -> MarketControlModule.BAZAAR;
                    case AUCTION_HOUSE ->
                            MarketControlModule.AUCTION_HOUSE;
                }));
    }

    private static String stateFingerprint(
            MarketCapabilitiesSnapshot snapshot
    ) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeBoolean(snapshot.showNavigation());
                writeText(output, snapshot.defaultModule().id());
                output.writeLong(snapshot.walletBalanceMinorUnits());
                output.writeBoolean(snapshot.walletBalanceKnown());
                writeText(output, snapshot.currencyName());
                output.writeInt(snapshot.currencyDecimals());
                output.writeInt(snapshot.auctionDurationPresetSeconds().size());
                for (long seconds : snapshot.auctionDurationPresetSeconds()) {
                    output.writeLong(seconds);
                }
                output.writeInt(snapshot.modules().size());
                for (MarketModuleCapability capability :
                        snapshot.modules()) {
                    writeText(output, capability.module().id());
                    writeText(output,
                            capability.availability().name());
                    writeText(output, capability.displayName());
                    writeText(output, capability.accentHex());
                    output.writeLong(capability.openClaims());
                }
            }
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(bytes.toByteArray()));
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "Market capability fingerprint failed", exception);
        }
    }

    private static void writeText(
            DataOutputStream output,
            String value
    ) throws IOException {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(encoded.length);
        output.write(encoded);
    }

    private static String configuredCurrencyName() {
        String value = Config.economyCurrencyName;
        return value == null || value.isBlank() ? "Coins" : value;
    }

    private static List<Long> auctionDurationPresetSeconds() {
        return AuctionHouseConfig.settings().listings()
                .durationPresetsMinutes().stream()
                .map(value -> Math.multiplyExact(value.longValue(), 60L))
                .limit(8).toList();
    }

    private static MarketCapabilitiesSnapshot withDurations(
            MarketCapabilitiesSnapshot snapshot, List<Long> durations) {
        return new MarketCapabilitiesSnapshot(snapshot.requestId(),
                snapshot.revision(), snapshot.showNavigation(),
                snapshot.defaultModule(), snapshot.walletBalanceMinorUnits(),
                snapshot.walletBalanceKnown(), snapshot.currencyName(),
                snapshot.currencyDecimals(), durations, snapshot.modules());
    }

    private static long countOpen(List<EscrowClaim> claims) {
        long count = 0L;
        for (EscrowClaim claim : claims) {
            if (claim.kind().publiclyVisible()
                    && claim.status() != ClaimStatus.COMPLETED) {
                count = Math.addExact(count, 1L);
            }
        }
        return count;
    }

    private static long countPrefix(
            List<EscrowClaim> claims,
            String prefix
    ) {
        long count = 0L;
        for (EscrowClaim claim : claims) {
            if (claim.kind().publiclyVisible()
                    && claim.status() != ClaimStatus.COMPLETED
                    && claim.sourceKey().startsWith(prefix)) {
                count = Math.addExact(count, 1L);
            }
        }
        return count;
    }

    public record Projection(
            UUID requestId,
            UUID ownerId,
            boolean showNavigation,
            MarketModule defaultModule,
            boolean escrowReady,
            boolean bazaarEnabled,
            boolean auctionHouseEnabled,
            long walletBalanceMinorUnits,
            boolean walletBalanceKnown,
            String currencyName,
            int currencyDecimals,
            List<Long> auctionDurationPresetSeconds,
            MarketCapabilityProjector.Branding shopBranding,
            MarketCapabilityProjector.Branding bazaarBranding,
            MarketCapabilityProjector.Branding auctionHouseBranding,
            Optional<MarketControlState> marketControl
    ) {
        private static final UUID ZERO = new UUID(0L, 0L);

        public Projection {
            requestId = Objects.requireNonNull(requestId, "requestId");
            ownerId = Objects.requireNonNull(ownerId, "ownerId");
            Objects.requireNonNull(defaultModule, "defaultModule");
            currencyName = Objects.requireNonNull(
                    currencyName, "currencyName");
            auctionDurationPresetSeconds = List.copyOf(
                    Objects.requireNonNull(auctionDurationPresetSeconds,
                            "auctionDurationPresetSeconds"));
            Objects.requireNonNull(shopBranding, "shopBranding");
            Objects.requireNonNull(bazaarBranding, "bazaarBranding");
            Objects.requireNonNull(auctionHouseBranding,
                    "auctionHouseBranding");
            marketControl = Objects.requireNonNull(
                    marketControl, "marketControl");
            if (ZERO.equals(requestId) || ZERO.equals(ownerId)) {
                throw new IllegalArgumentException(
                        "Market capability projection identity is invalid");
            }
            if (!walletBalanceKnown && walletBalanceMinorUnits != 0L
                    || currencyName.isEmpty()
                    || currencyName.length()
                    > MarketCapabilitiesSnapshot
                    .MAXIMUM_CURRENCY_NAME_LENGTH
                    || !currencyName.equals(currencyName.strip())
                    || currencyDecimals < 0 || currencyDecimals > 6) {
                throw new IllegalArgumentException(
                        "Market wallet projection is invalid");
            }
        }

        public Projection(
                UUID requestId, UUID ownerId, boolean showNavigation,
                MarketModule defaultModule, boolean escrowReady,
                boolean bazaarEnabled, boolean auctionHouseEnabled,
                long walletBalanceMinorUnits, boolean walletBalanceKnown,
                String currencyName, int currencyDecimals,
                MarketCapabilityProjector.Branding shopBranding,
                MarketCapabilityProjector.Branding bazaarBranding,
                MarketCapabilityProjector.Branding auctionHouseBranding,
                Optional<MarketControlState> marketControl) {
            this(requestId, ownerId, showNavigation, defaultModule,
                    escrowReady, bazaarEnabled, auctionHouseEnabled,
                    walletBalanceMinorUnits, walletBalanceKnown,
                    currencyName, currencyDecimals,
                    List.of(3_600L, 21_600L, 86_400L, 259_200L,
                            604_800L), shopBranding, bazaarBranding,
                    auctionHouseBranding, marketControl);
        }

        public Projection(
                UUID requestId,
                UUID ownerId,
                boolean showNavigation,
                MarketModule defaultModule,
                boolean escrowReady,
                boolean bazaarEnabled,
                boolean auctionHouseEnabled,
                MarketCapabilityProjector.Branding shopBranding,
                MarketCapabilityProjector.Branding bazaarBranding,
                MarketCapabilityProjector.Branding auctionHouseBranding
        ) {
            this(requestId, ownerId, showNavigation, defaultModule,
                    escrowReady, bazaarEnabled, auctionHouseEnabled,
                    0L, false, "Coins", 2,
                    shopBranding, bazaarBranding,
                    auctionHouseBranding,
                    Optional.of(MarketControlState.initial(0L)));
        }

        public Projection(
                UUID requestId,
                UUID ownerId,
                boolean showNavigation,
                MarketModule defaultModule,
                boolean escrowReady,
                boolean bazaarEnabled,
                boolean auctionHouseEnabled,
                MarketCapabilityProjector.Branding shopBranding,
                MarketCapabilityProjector.Branding bazaarBranding,
                MarketCapabilityProjector.Branding auctionHouseBranding,
                Optional<MarketControlState> marketControl
        ) {
            this(requestId, ownerId, showNavigation, defaultModule,
                    escrowReady, bazaarEnabled, auctionHouseEnabled,
                    0L, false, "Coins", 2, shopBranding,
                    bazaarBranding, auctionHouseBranding,
                    marketControl);
        }
    }
}

package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.MinecraftTestBootstrap;
import com.enviouse.futureshops.config.AuctionHouseConfig;
import com.enviouse.futureshops.server.market.auction.AuctionBidStanding;
import com.enviouse.futureshops.server.market.auction.AuctionItemLot;
import com.enviouse.futureshops.server.market.auction.AuctionListing;
import com.enviouse.futureshops.server.market.auction.AuctionListingState;
import com.enviouse.futureshops.server.market.auction.AuctionListingType;
import com.enviouse.futureshops.server.market.auction.AuctionRuleSnapshot;
import com.enviouse.futureshops.server.market.auction.AuctionTimeBasis;
import com.enviouse.futureshops.server.market.auction.escrow.AuctionEscrowTestFixtures;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the pure seams of {@link AuctionActionService}: config-to-rule-snapshot
 * mapping, bid hold deltas, listing restriction screening, and deterministic identifier
 * derivation. No Forge bootstrap — registries come from {@link MinecraftTestBootstrap}.
 */
class AuctionActionServiceTest {

    private static final String FINGERPRINT = "0123456789abcdef".repeat(4);

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.initialize();
    }

    // ═══════════════════════════ rules() ═══════════════════════════

    @Test
    void rulesMapsEveryDefaultFieldIncludingSecondsToMillis() {
        AuctionRuleSnapshot snapshot =
                AuctionActionService.rules(AuctionHouseConfig.Settings.defaults());

        assertEquals(100L, snapshot.listingFeeMinor());
        assertEquals(250, snapshot.saleTaxBasisPoints());
        assertEquals(100L, snapshot.minimumIncrementMinor());
        assertEquals(0, snapshot.minimumIncrementBasisPoints());
        assertTrue(snapshot.antiSnipeEnabled());
        assertEquals(60_000L, snapshot.antiSnipeTriggerMillis());
        assertEquals(60_000L, snapshot.antiSnipeExtensionMillis());
        assertEquals(600_000L, snapshot.maximumAntiSnipeCumulativeMillis());
        assertEquals(10, snapshot.maximumAntiSnipeExtensionCount());
        assertTrue(snapshot.allowSellerCancelBeforeBid());
        assertEquals(AuctionTimeBasis.REAL_TIME, snapshot.timeBasis());
        assertTrue(snapshot.pauseWhileFrozen());
        assertEquals(0L, snapshot.configRevision());
    }

    /**
     * Defaults share values across fields (fee == increment, trigger == extension), so a
     * transposed mapping could pass the defaults test. Distinct values per field catch that,
     * and the {@code online_time} branch of the time-basis parse is exercised.
     */
    @Test
    void rulesMapsDistinctCustomValuesAndOnlineTimeBasis() {
        AuctionHouseConfig.Settings defaults = AuctionHouseConfig.Settings.defaults();
        AuctionHouseConfig.Settings custom = new AuctionHouseConfig.Settings(
                defaults.branding(),
                new AuctionHouseConfig.Lifecycle("freeze", "online_time", false),
                new AuctionHouseConfig.ListingRules(true, true, true, 14, 64, 262144,
                        100000000000L, 5, 10080, List.of(60, 360, 1440, 4320, 10080), false),
                new AuctionHouseConfig.BidRules(50, 55L, 12, 8, false),
                new AuctionHouseConfig.AntiSnipeRules(true, 30, 45, 90, 3),
                new AuctionHouseConfig.FeeRules(7L, 321, "void"),
                defaults.payment(),
                defaults.restrictions(),
                defaults.notifications(),
                defaults.browse());

        AuctionRuleSnapshot snapshot = AuctionActionService.rules(custom);

        assertEquals(7L, snapshot.listingFeeMinor());
        assertEquals(321, snapshot.saleTaxBasisPoints());
        assertEquals(55L, snapshot.minimumIncrementMinor());
        assertEquals(12, snapshot.minimumIncrementBasisPoints());
        assertTrue(snapshot.antiSnipeEnabled());
        assertEquals(30_000L, snapshot.antiSnipeTriggerMillis());
        assertEquals(45_000L, snapshot.antiSnipeExtensionMillis());
        assertEquals(90_000L, snapshot.maximumAntiSnipeCumulativeMillis());
        assertEquals(3, snapshot.maximumAntiSnipeExtensionCount());
        assertFalse(snapshot.allowSellerCancelBeforeBid());
        assertEquals(AuctionTimeBasis.ONLINE_TIME, snapshot.timeBasis());
        assertFalse(snapshot.pauseWhileFrozen());
    }

    // ═══════════════════════════ heldDelta() ═══════════════════════════

    @Test
    void heldDeltaHoldsFullAmountWhenNoBidStands() {
        AuctionListing listing = timedListing(seller(), Optional.empty());
        assertEquals(800L,
                AuctionActionService.heldDelta(listing, bidder(), 800L));
    }

    @Test
    void heldDeltaHoldsFullAmountForNewBidderOverAnotherStanding() {
        AuctionListing listing = timedListing(seller(),
                Optional.of(standing(otherBidder(), 500L)));
        assertEquals(800L,
                AuctionActionService.heldDelta(listing, bidder(), 800L));
    }

    @Test
    void heldDeltaHoldsExactDeltaWhenRaisingOwnTopBid() {
        AuctionListing listing = timedListing(seller(),
                Optional.of(standing(bidder(), 500L)));
        assertEquals(300L,
                AuctionActionService.heldDelta(listing, bidder(), 800L));
        assertEquals(0L,
                AuctionActionService.heldDelta(listing, bidder(), 500L));
    }

    @Test
    void heldDeltaOverflowOnOwnRaiseThrowsInsteadOfWrapping() {
        AuctionListing listing = timedListing(seller(),
                Optional.of(standing(bidder(), 500L)));
        assertThrows(ArithmeticException.class,
                () -> AuctionActionService.heldDelta(listing, bidder(), Long.MIN_VALUE));
    }

    // ═══════════════════════════ restrictionDenial() ═══════════════════════════

    @Test
    void restrictionDenialAcceptsCleanVanillaStack() {
        assertNull(AuctionActionService.restrictionDenial(
                restrictions(List.of(), List.of(), "deny", "deny"),
                new ItemStack(Items.EMERALD)));
    }

    @Test
    void restrictionDenialRejectsDeniedRegistryId() {
        assertEquals("denied_id", AuctionActionService.restrictionDenial(
                restrictions(List.of("minecraft:emerald"), List.of(), "deny", "deny"),
                new ItemStack(Items.EMERALD)));
    }

    /**
     * Tag data is never bound in unit tests, so only the negative side of the denied-tag
     * screen is verifiable here: a configured denied tag must not reject a stack that
     * carries no tags. The positive denied-tag path needs datapack tag loading
     * (integration-level).
     */
    @Test
    void restrictionDenialIgnoresDeniedTagsWhenStackHasNoTags() {
        assertNull(AuctionActionService.restrictionDenial(
                restrictions(List.of(), List.of("minecraft:gems"), "deny", "deny"),
                new ItemStack(Items.EMERALD)));
    }

    // Capability providers only attach under the Forge runtime, so no plain-JUnit ItemStack can
    // serialize a real root-level ForgeCaps entry — the serialized-NBT seam is probed directly
    // with the synthetic root tag the live path would produce.

    @Test
    void restrictionDenialRejectsCapabilityBackedItem() {
        CompoundTag serialized = new ItemStack(Items.EMERALD).serializeNBT();
        serialized.put("ForgeCaps", new CompoundTag());

        assertEquals("capability", AuctionActionService.serializedDenial(
                restrictions(List.of(), List.of(), "deny", "deny"), serialized));
    }

    @Test
    void restrictionDenialAllowsCapabilityItemWhenPolicyAllows() {
        CompoundTag serialized = new ItemStack(Items.EMERALD).serializeNBT();
        serialized.put("ForgeCaps", new CompoundTag());

        assertNull(AuctionActionService.serializedDenial(
                restrictions(List.of(), List.of(), "deny", "allow"), serialized));
    }

    @Test
    void restrictionDenialRejectsShulkerStyleContainerNbt() {
        ItemStack stack = new ItemStack(Items.SHULKER_BOX);
        CompoundTag blockEntityTag = new CompoundTag();
        ListTag items = new ListTag();
        CompoundTag stored = new CompoundTag();
        stored.putString("id", "minecraft:emerald");
        stored.putByte("Count", (byte) 1);
        stored.putByte("Slot", (byte) 0);
        items.add(stored);
        blockEntityTag.put("Items", items);
        stack.getOrCreateTag().put("BlockEntityTag", blockEntityTag);

        assertEquals("container", AuctionActionService.restrictionDenial(
                restrictions(List.of(), List.of(), "deny", "deny"), stack));
    }

    @Test
    void restrictionDenialRejectsDirectItemsListNbt() {
        ItemStack stack = new ItemStack(Items.EMERALD);
        ListTag items = new ListTag();
        CompoundTag stored = new CompoundTag();
        stored.putString("id", "minecraft:diamond");
        stored.putByte("Count", (byte) 1);
        items.add(stored);
        stack.getOrCreateTag().put("Items", items);

        assertEquals("container", AuctionActionService.restrictionDenial(
                restrictions(List.of(), List.of(), "deny", "deny"), stack));
    }

    @Test
    void restrictionDenialAllowsContainerNbtWhenPolicyAllowsAll() {
        ItemStack stack = new ItemStack(Items.EMERALD);
        ListTag items = new ListTag();
        CompoundTag stored = new CompoundTag();
        stored.putString("id", "minecraft:diamond");
        stored.putByte("Count", (byte) 1);
        items.add(stored);
        stack.getOrCreateTag().put("Items", items);

        assertNull(AuctionActionService.restrictionDenial(
                restrictions(List.of(), List.of(), "allow_all", "deny"), stack));
    }

    @Test
    void restrictionDenialReportsDeniedIdBeforeCapability() {
        ItemStack stack = new ItemStack(MinecraftTestBootstrap.capabilityItem());
        assertEquals("denied_id", AuctionActionService.restrictionDenial(
                restrictions(List.of("futureshops:test_capability_item"), List.of(),
                        "deny", "deny"),
                stack));
    }

    // ═══════════════════════════ derived() ═══════════════════════════

    @Test
    void derivedIsDeterministicAndMatchesNameUuidContract() {
        UUID requestId = AuctionEscrowTestFixtures.id(11);
        UUID first = AuctionActionService.derived("auction.listing.", requestId);
        UUID second = AuctionActionService.derived("auction.listing.", requestId);

        assertEquals(first, second);
        assertEquals(UUID.nameUUIDFromBytes(
                        ("auction.listing." + requestId).getBytes(StandardCharsets.UTF_8)),
                first);
    }

    @Test
    void derivedIsDistinctAcrossPrefixesAndRequestIds() {
        UUID requestId = AuctionEscrowTestFixtures.id(11);
        List<String> prefixes = List.of("auction.listing.", "auction.activation.",
                "auction.entry.", "auction.lot.", "auction.bid.", "auction.hold.",
                "auction.holdtxn.", "auction.settle.", "auction.cancel.");
        Set<UUID> derived = new HashSet<>();
        for (String prefix : prefixes) {
            derived.add(AuctionActionService.derived(prefix, requestId));
        }
        assertEquals(prefixes.size(), derived.size());

        assertNotEquals(
                AuctionActionService.derived("auction.listing.",
                        AuctionEscrowTestFixtures.id(11)),
                AuctionActionService.derived("auction.listing.",
                        AuctionEscrowTestFixtures.id(12)));
    }

    // ═══════════════════════════ helpers ═══════════════════════════

    private static UUID seller() {
        return AuctionEscrowTestFixtures.id(101);
    }

    private static UUID bidder() {
        return AuctionEscrowTestFixtures.id(102);
    }

    private static UUID otherBidder() {
        return AuctionEscrowTestFixtures.id(103);
    }

    private static AuctionHouseConfig.RestrictionRules restrictions(
            List<String> deniedItemIds,
            List<String> deniedItemTags,
            String containerPolicy,
            String capabilityPolicy
    ) {
        return new AuctionHouseConfig.RestrictionRules(
                deniedItemIds, deniedItemTags, containerPolicy, capabilityPolicy);
    }

    private static AuctionListing timedListing(
            UUID sellerId,
            Optional<AuctionBidStanding> highestBid
    ) {
        return new AuctionListing(
                AuctionEscrowTestFixtures.id(21), sellerId,
                AuctionEscrowTestFixtures.id(22),
                new AuctionItemLot(AuctionEscrowTestFixtures.id(23), "minecraft:emerald",
                        FINGERPRINT, 1, 16, "minecraft", "emerald minecraft"),
                AuctionListingType.TIMED_AUCTION, AuctionListingState.ACTIVE,
                3L, 100L, 0L, AuctionEscrowTestFixtures.rules(),
                1_000L, 101_000L, 100_000L, 2_000L, 0L, 0, 0L,
                highestBid.isPresent() ? 1L : 0L, highestBid,
                Optional.empty(), Optional.empty());
    }

    private static AuctionBidStanding standing(UUID bidderId, long amountMinor) {
        return new AuctionBidStanding(AuctionEscrowTestFixtures.id(31), bidderId,
                AuctionEscrowTestFixtures.id(32), AuctionEscrowTestFixtures.id(33),
                amountMinor, 1L, 1_500L);
    }
}

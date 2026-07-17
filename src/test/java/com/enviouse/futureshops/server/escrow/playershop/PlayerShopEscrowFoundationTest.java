package com.enviouse.futureshops.server.escrow.playershop;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

class PlayerShopEscrowFoundationTest {
    private static final UUID OWNER = id("owner");
    private static final UUID ACTOR = id("actor");
    private static final Instant NOW = Instant.parse("2026-07-17T12:00:00Z");

    @Test
    void everyValuePathRoundTripsAndConserves() {
        List<Fixture> fixtures = List.of(
                purchase(id("money purchase"),
                        PlayerShopTradeMethod.MONEY,
                        PlayerShopPaymentSource.WALLET, false),
                purchase(id("barter purchase"),
                        PlayerShopTradeMethod.BARTER,
                        PlayerShopPaymentSource.NONE, false),
                purchase(id("compound purchase"),
                        PlayerShopTradeMethod.MONEY_AND_BARTER,
                        PlayerShopPaymentSource.INVENTORY_CASH, false),
                purchase(id("admin purchase"),
                        PlayerShopTradeMethod.MONEY_AND_BARTER,
                        PlayerShopPaymentSource.WALLET, true),
                buyback(id("buyback"), false),
                buyback(id("admin buyback"), true),
                settlement(id("settlement")));

        for (Fixture fixture : fixtures) {
            check(PlayerShopConservationValidator.validate(
                    fixture.intent()).conserved(), "fixture must conserve");
            checkEquals(fixture.intent(), PlayerShopIntentCodec.decode(
                    PlayerShopIntentCodec.encode(fixture.intent())),
                    "intent round trip");
            checkEquals(fixture.commit(), PlayerShopAtomicCommitCodec.decode(
                    PlayerShopAtomicCommitCodec.encode(fixture.commit())),
                    "commit round trip");
        }
    }

    @Test
    void replayRegistryRejectsConflictingReuse() {
        UUID requestId = id("replay request");
        Fixture first = purchase(requestId, PlayerShopTradeMethod.MONEY,
                PlayerShopPaymentSource.WALLET, false);
        Fixture conflict = purchase(requestId, PlayerShopTradeMethod.BARTER,
                PlayerShopPaymentSource.NONE, false);
        PlayerShopReplayRegistry registry = new PlayerShopReplayRegistry();

        checkEquals(PlayerShopReplayRegistry.ApplyStatus.ADDED,
                registry.applyIntent(first.intent()).status(), "first intent");
        checkEquals(PlayerShopReplayRegistry.ApplyStatus.IDEMPOTENT_REPLAY,
                registry.applyIntent(PlayerShopIntentCodec.decode(
                        PlayerShopIntentCodec.encode(first.intent()))).status(),
                "intent replay");
        checkEquals(PlayerShopReplayRegistry.ApplyStatus.CONFLICT,
                registry.applyIntent(conflict.intent()).status(),
                "intent conflict");
        checkEquals(PlayerShopReplayRegistry.ApplyStatus.UPDATED,
                registry.applyCommit(first.commit()).status(), "first commit");
        checkEquals(PlayerShopReplayRegistry.ApplyStatus.IDEMPOTENT_REPLAY,
                registry.applyCommit(first.commit()).status(), "commit replay");
        checkEquals(PlayerShopReplayRegistry.ApplyStatus.CONFLICT,
                registry.applyCommit(conflict.commit()).status(),
                "commit conflict");
    }

    @Test
    void storageRecoveryKeepsExactClaimsWithoutDrops() {
        Fixture fixture = buyback(id("recovery buyback"), false);
        PlayerShopStorageCustodyReceipt prepared =
                fixture.commit().storageReceipts().get(0);
        PlayerShopStorageCustodyReceipt uncertain = prepared.recoveryRequired(
                4, "before", "partial", bytes("partial receipt"),
                NOW.plusSeconds(2), "Adapter stopped during insertion");
        PlayerShopStorageCustodyReceipt preserved = uncertain.resolve(
                PlayerShopStorageCustodyReceipt.RecoveryState.CLAIM_PRESERVED,
                "verified partial", bytes("preserved receipt"),
                NOW.plusSeconds(3), "Uninserted items remain in the owner claim");

        checkEquals(PlayerShopStorageCustodyReceipt.RecoveryState.CLAIM_PRESERVED,
                preserved.state(), "claim preserved");
        checkEquals(4, preserved.appliedQuantity(), "partial amount retained");
        checkEquals(preserved,
                PlayerShopStorageCustodyReceiptCodec.decode(
                        PlayerShopStorageCustodyReceiptCodec.encode(preserved)),
                "recovery round trip");
        check(fixture.intent().claims().stream().anyMatch(value ->
                        value.kind() == PlayerShopClaimPlan.Kind.EXACT_ITEM
                                && value.beneficiaryId().equals(OWNER)),
                "owner claim exists before insertion");
    }

    @Test
    void codecsRejectTamperingTruncationAndOversize() {
        Fixture fixture = purchase(id("codec purchase"),
                PlayerShopTradeMethod.MONEY_AND_BARTER,
                PlayerShopPaymentSource.WALLET, false);
        byte[] intent = PlayerShopIntentCodec.encode(fixture.intent());
        byte[] commit = PlayerShopAtomicCommitCodec.encode(fixture.commit());
        byte[] tampered = intent.clone();
        tampered[tampered.length - 1] ^= 0x01;

        expectThrows(() -> PlayerShopIntentCodec.decode(tampered));
        expectThrows(() -> PlayerShopIntentCodec.decode(
                Arrays.copyOf(intent, intent.length - 1)));
        expectThrows(() -> PlayerShopAtomicCommitCodec.decode(
                Arrays.copyOf(commit, commit.length - 1)));
        expectThrows(() -> PlayerShopIntentCodec.decode(new byte[
                PlayerShopIntentCodec.MAX_ENCODED_BYTES + 1]));
    }

    @Test
    void listingRevisionChangesForConfigAndExactItemChanges() {
        PlayerShopListingSnapshot original = listing(
                PlayerShopListingSnapshot.ConfiguredTradeMode.MONEY,
                PlayerShopListingSnapshot.Direction.SELL, false,
                bytes("diamond template"));
        PlayerShopListingSnapshot changedTemplate = listing(
                PlayerShopListingSnapshot.ConfiguredTradeMode.MONEY,
                PlayerShopListingSnapshot.Direction.SELL, false,
                bytes("tagged diamond template"));
        PlayerShopListingSnapshot changedPromotion =
                PlayerShopListingSnapshot.capture(
                        original.listingId(), original.listingIndex(),
                        original.direction(), original.configuredTradeMode(),
                        original.baseQuantity(), original.moneyPriceMinorUnits(),
                        original.barterTemplate(),
                        original.barterUnitsPerPurchase(),
                        original.buybackPriceMinorUnits(), original.buybackCap(),
                        original.buybackBought(), original.outputs(),
                        new PlayerShopListingSnapshot.PromotionSnapshot(
                                "PERCENTAGE", 25.0D, 0, 0, 0L, 0L,
                                false, true),
                        original.hidden(), original.showcase(),
                        original.adminShop());

        check(!original.revisionFingerprint().equals(
                changedTemplate.revisionFingerprint()),
                "exact template revision");
        check(!original.revisionFingerprint().equals(
                changedPromotion.revisionFingerprint()),
                "promotion revision");
    }

    @Test
    void directOutputDeliveryFailsConservation() {
        Fixture fixture = purchase(id("direct delivery"),
                PlayerShopTradeMethod.MONEY,
                PlayerShopPaymentSource.WALLET, true);
        PlayerShopEscrowIntent source = fixture.intent();
        PlayerShopItemTransfer quoted = source.itemTransfers().get(0);
        PlayerShopItemTransfer unsafe = new PlayerShopItemTransfer(
                quoted.transferId(), quoted.source(),
                PlayerShopAssetEndpoint.participant(
                        PlayerShopAssetEndpoint.Kind.ACTOR_INVENTORY,
                        ACTOR, "direct delivery"), quoted.lot());
        PlayerShopEscrowIntent intent = PlayerShopEscrowIntent.prepared(
                source.requestId(), source.actorId(), source.ownerId(),
                source.shopIdentity(), source.operation(), source.tradeMethod(),
                source.paymentSource(), source.requestedUnits(),
                source.quoteCreatedAt(), source.listing(),
                source.moneyTransfers(), List.of(unsafe), List.of(), List.of());

        check(!PlayerShopConservationValidator.validate(intent).conserved(),
                "direct delivery must fail");
    }

    @Test
    void itemEvidenceIsImmutableAndNonzero() {
        byte[] template = bytes("template");
        byte[] stack = bytes("stack");
        PlayerShopItemLot lot = PlayerShopItemLot.captureRaw(
                id("item source"), "output.0", 0, 1,
                "minecraft:diamond", 8, PlayerShopItemMatchMode.EXACT,
                template, stack);
        template[0] ^= 1;
        stack[0] ^= 1;
        byte[] returned = lot.serializedExactStack();
        returned[0] ^= 1;

        checkEquals("minecraft:diamond", lot.itemId(), "item identity");
        check(!Arrays.equals(stack, lot.serializedExactStack()),
                "caller stack mutation isolated");
        expectThrows(() -> PlayerShopItemLot.captureRaw(
                new UUID(0L, 0L), "output.0", 0, 1,
                "minecraft:diamond", 1, PlayerShopItemMatchMode.EXACT,
                bytes("one"), bytes("one")));
    }

    static Fixture purchase(
            UUID requestId,
            PlayerShopTradeMethod method,
            PlayerShopPaymentSource source,
            boolean admin
    ) {
        PlayerShopListingSnapshot.ConfiguredTradeMode configured = switch (method) {
            case MONEY -> PlayerShopListingSnapshot.ConfiguredTradeMode.MONEY;
            case BARTER -> PlayerShopListingSnapshot.ConfiguredTradeMode.BARTER;
            case MONEY_AND_BARTER ->
                    PlayerShopListingSnapshot.ConfiguredTradeMode.MONEY_AND_BARTER;
            default -> throw new IllegalArgumentException("purchase method");
        };
        PlayerShopListingSnapshot listing = listing(configured,
                PlayerShopListingSnapshot.Direction.SELL, admin,
                bytes("diamond template"));
        PlayerShopIdentity identity = identity(OWNER);
        List<PlayerShopMoneyTransfer> money = new ArrayList<>();
        List<PlayerShopItemTransfer> items = new ArrayList<>();
        List<PlayerShopClaimPlan> claims = new ArrayList<>();
        List<PlayerShopStorageMutationPlan> storage = new ArrayList<>();
        List<PlayerShopMoneyMutationReceipt> moneyReceipts = new ArrayList<>();
        List<PlayerShopItemMutationReceipt> itemReceipts = new ArrayList<>();
        List<PlayerShopStorageCustodyReceipt> storageReceipts = new ArrayList<>();

        PlayerShopItemLot outputLot = lot(requestId, "buyer output", 8,
                "minecraft:diamond", bytes("diamond template"));
        PlayerShopClaimPlan outputClaim = PlayerShopClaimPlan.item(requestId,
                "buyer output", ACTOR, outputLot, "Player shop purchase output");
        PlayerShopAssetEndpoint outputSource = admin
                ? PlayerShopAssetEndpoint.system(
                PlayerShopAssetEndpoint.Kind.ADMIN_MINT, "admin shop output")
                : PlayerShopAssetEndpoint.participant(
                PlayerShopAssetEndpoint.Kind.LINKED_STOCK, OWNER,
                "linked stock");
        PlayerShopItemTransfer outputTransfer = new PlayerShopItemTransfer(
                id(requestId + " output transfer"), outputSource,
                claimEndpoint(outputClaim), outputLot);
        items.add(outputTransfer);
        claims.add(outputClaim);
        itemReceipts.add(PlayerShopItemMutationReceipt.funded(requestId,
                outputTransfer, admin
                        ? PlayerShopItemMutationReceipt.FundingKind.ADMIN_MINT
                        : PlayerShopItemMutationReceipt.FundingKind.STORAGE_EXTRACTION,
                bytes("output custody")));
        if (!admin) {
            PlayerShopStorageMutationPlan extraction =
                    PlayerShopStorageMutationPlan.extraction(requestId,
                            storage.size(), storageEndpoint(0),
                            outputTransfer.transferId(), outputLot,
                            "stock before");
            storage.add(extraction);
            storageReceipts.add(PlayerShopStorageCustodyReceipt.prepared(
                    requestId, extraction, NOW).applied("stock before",
                    "stock after", bytes("extract receipt"),
                    NOW.plusSeconds(1)));
        }

        if (method == PlayerShopTradeMethod.MONEY
                || method == PlayerShopTradeMethod.MONEY_AND_BARTER) {
            long amount = 250L;
            PlayerShopAssetEndpoint moneySource = source
                    == PlayerShopPaymentSource.WALLET
                    ? PlayerShopAssetEndpoint.participant(
                    PlayerShopAssetEndpoint.Kind.ACTOR_WALLET, ACTOR,
                    "wallet")
                    : PlayerShopAssetEndpoint.participant(
                    PlayerShopAssetEndpoint.Kind.ACTOR_CASH, ACTOR,
                    "inventory cash");
            PlayerShopClaimPlan sellerClaim = admin ? null
                    : PlayerShopClaimPlan.money(requestId, "seller proceeds",
                    OWNER, amount, "Player shop seller proceeds");
            PlayerShopAssetEndpoint destination = admin
                    ? PlayerShopAssetEndpoint.system(
                    PlayerShopAssetEndpoint.Kind.ADMIN_SINK,
                    "admin shop money sink")
                    : claimEndpoint(sellerClaim);
            long sourceBefore = source == PlayerShopPaymentSource.WALLET
                    ? 2_000L : PlayerShopMoneyTransfer.BALANCE_NOT_APPLICABLE;
            long destinationBefore = admin
                    ? PlayerShopMoneyTransfer.BALANCE_NOT_APPLICABLE : 0L;
            PlayerShopMoneyTransfer transfer = new PlayerShopMoneyTransfer(
                    id(requestId + " money transfer"), moneySource,
                    destination, amount, source, sourceBefore,
                    destinationBefore);
            money.add(transfer);
            if (sellerClaim != null) claims.add(sellerClaim);
            moneyReceipts.add(PlayerShopMoneyMutationReceipt.applied(
                    requestId, transfer,
                    sourceBefore == PlayerShopMoneyTransfer.BALANCE_NOT_APPLICABLE
                            ? sourceBefore : sourceBefore - amount,
                    destinationBefore
                            == PlayerShopMoneyTransfer.BALANCE_NOT_APPLICABLE
                            ? destinationBefore : destinationBefore + amount,
                    bytes("money provider receipt")));
        }

        if (method == PlayerShopTradeMethod.BARTER
                || method == PlayerShopTradeMethod.MONEY_AND_BARTER) {
            PlayerShopItemLot barterLot = lot(requestId, "barter proceeds", 6,
                    "minecraft:emerald", bytes("emerald template"));
            PlayerShopClaimPlan ownerClaim = admin ? null
                    : PlayerShopClaimPlan.item(requestId, "barter proceeds",
                    OWNER, barterLot, "Player shop barter proceeds");
            PlayerShopAssetEndpoint destination = admin
                    ? PlayerShopAssetEndpoint.system(
                    PlayerShopAssetEndpoint.Kind.ADMIN_SINK,
                    "admin shop barter sink")
                    : claimEndpoint(ownerClaim);
            PlayerShopItemTransfer transfer = new PlayerShopItemTransfer(
                    id(requestId + " barter transfer"),
                    PlayerShopAssetEndpoint.participant(
                            PlayerShopAssetEndpoint.Kind.ACTOR_INVENTORY,
                            ACTOR, "buyer inventory"),
                    destination, barterLot);
            items.add(transfer);
            if (ownerClaim != null) claims.add(ownerClaim);
            itemReceipts.add(PlayerShopItemMutationReceipt.funded(requestId,
                    transfer,
                    PlayerShopItemMutationReceipt.FundingKind.INVENTORY_REMOVAL,
                    bytes("barter custody")));
            if (!admin) {
                PlayerShopStorageMutationPlan insertion =
                        PlayerShopStorageMutationPlan.insertion(requestId,
                                storage.size(), storageEndpoint(1),
                                transfer.transferId(), ownerClaim.claimId(),
                                barterLot, "barter before");
                storage.add(insertion);
                storageReceipts.add(PlayerShopStorageCustodyReceipt.prepared(
                        requestId, insertion, NOW));
            }
        }

        PlayerShopEscrowIntent intent = PlayerShopEscrowIntent.prepared(
                requestId, ACTOR, OWNER, identity,
                admin ? PlayerShopOperation.ADMIN_PURCHASE_SINK
                        : PlayerShopOperation.PURCHASE,
                method, source, 2, NOW, listing, money, items, claims, storage);
        PlayerShopAtomicCommit commit = PlayerShopAtomicCommit.create(intent,
                NOW.plusSeconds(1), moneyReceipts, itemReceipts,
                storageReceipts);
        return new Fixture(intent, commit);
    }

    static Fixture buyback(UUID requestId, boolean admin) {
        PlayerShopListingSnapshot listing = listing(
                PlayerShopListingSnapshot.ConfiguredTradeMode.MONEY,
                PlayerShopListingSnapshot.Direction.BUY, admin,
                bytes("diamond template"));
        PlayerShopItemLot itemLot = lot(requestId, "buyback items", 8,
                "minecraft:diamond", bytes("diamond template"));
        PlayerShopClaimPlan ownerItemClaim = admin ? null
                : PlayerShopClaimPlan.item(requestId, "buyback items", OWNER,
                itemLot, "Player shop buyback items");
        PlayerShopClaimPlan sellerMoneyClaim = PlayerShopClaimPlan.money(
                requestId, "buyback money", ACTOR, 50L,
                "Player shop buyback proceeds");
        PlayerShopItemTransfer itemTransfer = new PlayerShopItemTransfer(
                id(requestId + " buyback item transfer"),
                PlayerShopAssetEndpoint.participant(
                        PlayerShopAssetEndpoint.Kind.ACTOR_INVENTORY, ACTOR,
                        "seller inventory"),
                admin ? PlayerShopAssetEndpoint.system(
                        PlayerShopAssetEndpoint.Kind.ADMIN_SINK,
                        "admin buyback item sink")
                        : claimEndpoint(ownerItemClaim), itemLot);
        PlayerShopMoneyTransfer moneyTransfer = new PlayerShopMoneyTransfer(
                id(requestId + " buyback money transfer"),
                admin ? PlayerShopAssetEndpoint.system(
                        PlayerShopAssetEndpoint.Kind.ADMIN_MINT,
                        "admin buyback mint")
                        : PlayerShopAssetEndpoint.participant(
                        PlayerShopAssetEndpoint.Kind.OWNER_WALLET, OWNER,
                        "owner wallet"),
                claimEndpoint(sellerMoneyClaim), 50L,
                PlayerShopPaymentSource.NONE,
                admin ? PlayerShopMoneyTransfer.BALANCE_NOT_APPLICABLE : 500L,
                0L);
        List<PlayerShopStorageMutationPlan> storage = new ArrayList<>();
        List<PlayerShopStorageCustodyReceipt> storageReceipts =
                new ArrayList<>();
        if (!admin) {
            PlayerShopStorageMutationPlan insertion =
                    PlayerShopStorageMutationPlan.insertion(requestId, 0,
                            storageEndpoint(0), itemTransfer.transferId(),
                            ownerItemClaim.claimId(), itemLot,
                            "stock before buyback");
            storage.add(insertion);
            storageReceipts.add(PlayerShopStorageCustodyReceipt.prepared(
                    requestId, insertion, NOW));
        }
        List<PlayerShopClaimPlan> claims = admin
                ? List.of(sellerMoneyClaim)
                : List.of(ownerItemClaim, sellerMoneyClaim);
        PlayerShopEscrowIntent intent = PlayerShopEscrowIntent.prepared(
                requestId, ACTOR, OWNER, identity(OWNER),
                admin ? PlayerShopOperation.ADMIN_BUYBACK
                        : PlayerShopOperation.BUYBACK,
                PlayerShopTradeMethod.BUYBACK, PlayerShopPaymentSource.NONE,
                2, NOW, listing, List.of(moneyTransfer),
                List.of(itemTransfer), claims, storage);
        PlayerShopMoneyMutationReceipt moneyReceipt =
                PlayerShopMoneyMutationReceipt.applied(requestId,
                        moneyTransfer,
                        admin ? PlayerShopMoneyTransfer.BALANCE_NOT_APPLICABLE
                                : 450L,
                        50L, bytes("buyback money receipt"));
        PlayerShopItemMutationReceipt itemReceipt =
                PlayerShopItemMutationReceipt.funded(requestId, itemTransfer,
                        PlayerShopItemMutationReceipt.FundingKind.INVENTORY_REMOVAL,
                        bytes("buyback item receipt"));
        return new Fixture(intent, PlayerShopAtomicCommit.create(intent,
                NOW.plusSeconds(1), List.of(moneyReceipt),
                List.of(itemReceipt), storageReceipts));
    }

    static Fixture settlement(UUID requestId) {
        PlayerShopClaimPlan claim = PlayerShopClaimPlan.money(requestId,
                "settlement claim", OWNER, 300L,
                "Player shop settlement claim");
        PlayerShopMoneyTransfer transfer = new PlayerShopMoneyTransfer(
                id(requestId + " settlement transfer"),
                PlayerShopAssetEndpoint.participant(
                        PlayerShopAssetEndpoint.Kind.SETTLEMENT_BALANCE,
                        OWNER, "pending settlement"),
                claimEndpoint(claim), 300L, PlayerShopPaymentSource.NONE,
                300L, 0L);
        PlayerShopEscrowIntent intent = PlayerShopEscrowIntent.prepared(
                requestId, OWNER, OWNER, identity(OWNER),
                PlayerShopOperation.SETTLEMENT_CLAIM,
                PlayerShopTradeMethod.SETTLEMENT,
                PlayerShopPaymentSource.NONE, 1, NOW, null,
                List.of(transfer), List.of(), List.of(claim), List.of());
        PlayerShopMoneyMutationReceipt receipt =
                PlayerShopMoneyMutationReceipt.applied(requestId, transfer,
                        0L, 300L, bytes("settlement receipt"));
        return new Fixture(intent, PlayerShopAtomicCommit.create(intent,
                NOW.plusSeconds(1), List.of(receipt), List.of(), List.of()));
    }

    private static PlayerShopListingSnapshot listing(
            PlayerShopListingSnapshot.ConfiguredTradeMode mode,
            PlayerShopListingSnapshot.Direction direction,
            boolean admin,
            byte[] outputTemplate
    ) {
        PlayerShopListingSnapshot.ItemTemplate barter = mode
                == PlayerShopListingSnapshot.ConfiguredTradeMode.MONEY
                ? null : new PlayerShopListingSnapshot.ItemTemplate(
                "minecraft:emerald", 3, PlayerShopItemMatchMode.EXACT,
                bytes("emerald template"));
        return PlayerShopListingSnapshot.capture("listing.0", 0, direction,
                mode, 4, 125L, barter, barter == null ? 0 : 3,
                25L, 100, 0,
                List.of(new PlayerShopListingSnapshot.ItemTemplate(
                        "minecraft:diamond", 4,
                        PlayerShopItemMatchMode.EXACT, outputTemplate)),
                new PlayerShopListingSnapshot.PromotionSnapshot("", 0.0D,
                        0, 0, 0L, 0L, false, false),
                false, false, admin);
    }

    private static PlayerShopIdentity identity(UUID owner) {
        return new PlayerShopIdentity(id("registry shop"), 3L,
                "player_shop.registry", "minecraft:overworld",
                10, 64, -4, owner);
    }

    private static PlayerShopStorageEndpoint storageEndpoint(int ordinal) {
        return new PlayerShopStorageEndpoint("minecraft:overworld",
                11 + ordinal, 64, -4, ordinal, 7L,
                "forge:item_handler");
    }

    private static PlayerShopItemLot lot(
            UUID requestId,
            String key,
            int quantity,
            String itemId,
            byte[] template
    ) {
        return PlayerShopItemLot.captureRaw(requestId, key, 0, 1, itemId,
                quantity, PlayerShopItemMatchMode.EXACT, template,
                bytes(key + ".stack." + quantity));
    }

    private static PlayerShopAssetEndpoint claimEndpoint(
            PlayerShopClaimPlan claim
    ) {
        return PlayerShopAssetEndpoint.participant(
                claim.kind() == PlayerShopClaimPlan.Kind.MONEY
                        ? PlayerShopAssetEndpoint.Kind.MONEY_CLAIM
                        : PlayerShopAssetEndpoint.Kind.ITEM_CLAIM,
                claim.beneficiaryId(), claim.claimId().toString());
    }

    private static UUID id(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static void check(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }

    private static void checkEquals(Object expected, Object actual,
                                    String label) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(label + ". Expected " + expected
                    + " but got " + actual);
        }
    }

    private static void expectThrows(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException expected) {
            return;
        }
        throw new AssertionError("Expected an exception");
    }

    record Fixture(
            PlayerShopEscrowIntent intent,
            PlayerShopAtomicCommit commit
    ) {
    }
}

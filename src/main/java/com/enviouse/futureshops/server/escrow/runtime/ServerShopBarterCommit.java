package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.money.ItemStackSnapshotCodec;
import com.enviouse.futureshops.server.escrow.claim.ClaimKind;
import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.item.ExactItemClaimPayload;
import com.enviouse.futureshops.server.escrow.item.ExactItemClaimPayloadCodec;
import com.enviouse.futureshops.server.escrow.item.ItemInputMatcher;
import com.enviouse.futureshops.server.escrow.item.ItemMatchMode;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryAllocation;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryBatchEntry;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationReceipt;
import com.enviouse.futureshops.server.escrow.model.DimensionAwareShopReference;
import com.enviouse.futureshops.server.escrow.model.EscrowAssetLot;
import com.enviouse.futureshops.server.escrow.model.EscrowAssetLotType;
import com.enviouse.futureshops.server.escrow.model.EscrowOperation;
import com.enviouse.futureshops.server.escrow.model.EscrowParticipant;
import com.enviouse.futureshops.server.escrow.model.EscrowParticipantRole;
import com.enviouse.futureshops.server.escrow.model.EscrowParty;
import com.enviouse.futureshops.server.escrow.model.EscrowProtectionLevel;
import com.enviouse.futureshops.server.escrow.model.EscrowRequestKey;
import com.enviouse.futureshops.server.escrow.model.EscrowState;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;
import com.enviouse.futureshops.server.escrow.model.EscrowTransactionId;
import com.enviouse.futureshops.server.escrow.stock.StockKey;
import com.enviouse.futureshops.server.escrow.stock.StockMutationCommand;
import com.enviouse.futureshops.server.escrow.stock.StockMutationType;
import com.enviouse.futureshops.server.escrow.stock.StockReservationDirection;
import com.enviouse.futureshops.server.escrow.stock.StockReservationId;
import com.enviouse.futureshops.server.escrow.stock.StockReservationRequest;
import com.enviouse.futureshops.server.escrow.stock.StockReservationResolution;
import net.minecraft.world.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public record ServerShopBarterCommit(
        UUID requestId,
        UUID playerId,
        String shopId,
        String recipeId,
        int multiplier,
        long quoteRevision,
        long recipeRevision,
        Instant quoteCreatedAt,
        List<Ingredient> ingredients,
        List<OutputLine> outputs,
        ItemInventoryMutationReceipt ingredientCustodyReceipt,
        EscrowTransaction completedTransaction,
        StockMutationCommand.ReserveBatch stockReservation,
        StockMutationCommand.ResolveBatch stockCommit,
        List<EscrowClaim> outputClaims
) {
    public static final String CLAIM_LABEL = "Server shop barter output";
    public static final int MAX_IDENTIFIER_LENGTH = 160;
    public static final int MAX_INGREDIENTS = 64;
    public static final int MAX_OUTPUT_LINES = 256;
    public static final int MAX_TOTAL_OUTPUT_PORTIONS = 3840;
    public static final long MAX_REVISION = 1_000_000_000_000L;

    private static final UUID ZERO_UUID = new UUID(0L, 0L);

    public ServerShopBarterCommit {
        requestId = requireUuid(requestId, "requestId");
        playerId = requireUuid(playerId, "playerId");
        shopId = requireIdentifier(shopId, "shopId");
        recipeId = requireIdentifier(recipeId, "recipeId");
        quoteCreatedAt = Objects.requireNonNull(
                quoteCreatedAt, "quoteCreatedAt");
        ingredients = copyIngredients(ingredients);
        outputs = copyOutputs(outputs);
        ingredientCustodyReceipt = Objects.requireNonNull(
                ingredientCustodyReceipt, "ingredientCustodyReceipt");
        completedTransaction = Objects.requireNonNull(
                completedTransaction, "completedTransaction");
        stockReservation = Objects.requireNonNull(
                stockReservation, "stockReservation");
        stockCommit = Objects.requireNonNull(stockCommit, "stockCommit");
        outputClaims = List.copyOf(Objects.requireNonNull(
                outputClaims, "outputClaims"));
        ServerShopBarterConservationValidator.validate(
                new CommitView(requestId, playerId, shopId, recipeId,
                        multiplier, quoteRevision, recipeRevision,
                        quoteCreatedAt, ingredients, outputs,
                        ingredientCustodyReceipt, completedTransaction,
                        stockReservation, stockCommit, outputClaims));
    }

    public static ServerShopBarterCommit create(
            UUID requestId,
            UUID playerId,
            String shopId,
            String recipeId,
            int multiplier,
            long quoteRevision,
            long recipeRevision,
            Instant quoteCreatedAt,
            List<Ingredient> ingredients,
            List<OutputLine> outputs,
            ItemInventoryMutationReceipt ingredientCustodyReceipt,
            DimensionAwareShopReference shopReference
    ) {
        CanonicalInput input = new CanonicalInput(requestId, playerId,
                shopId, recipeId, multiplier, quoteRevision,
                recipeRevision, quoteCreatedAt, ingredients, outputs,
                ingredientCustodyReceipt, shopReference);
        CanonicalComponents components = canonical(input);
        return new ServerShopBarterCommit(input.requestId(),
                input.playerId(), input.shopId(), input.recipeId(),
                input.multiplier(), input.quoteRevision(),
                input.recipeRevision(), input.quoteCreatedAt(),
                input.ingredients(), input.outputs(),
                input.ingredientCustodyReceipt(),
                components.transaction(), components.reserve(),
                components.commit(), components.claims());
    }

    public int totalIngredientQuantity() {
        int total = 0;
        for (Ingredient ingredient : ingredients) {
            total = Math.addExact(total,
                    ingredient.totalQuantity(multiplier));
        }
        return total;
    }

    public int totalOutputQuantity() {
        int total = 0;
        for (OutputLine output : outputs) {
            total = Math.addExact(total,
                    output.totalQuantity(multiplier));
        }
        return total;
    }

    public String wireFingerprint() {
        return wireFingerprint(requestId, playerId, shopId, recipeId,
                multiplier);
    }

    public String quoteFingerprint() {
        return quoteFingerprint(canonicalInput());
    }

    public static String wireFingerprint(
            UUID requestId,
            UUID playerId,
            String shopId,
            String recipeId,
            int multiplier
    ) {
        String material = "futureshops server shop barter wire v1\u0000"
                + requireUuid(requestId, "requestId") + "\u0000"
                + requireUuid(playerId, "playerId") + "\u0000"
                + requireIdentifier(shopId, "shopId") + "\u0000"
                + requireIdentifier(recipeId, "recipeId") + "\u0000"
                + requireMultiplier(multiplier);
        return sha256(material.getBytes(StandardCharsets.UTF_8));
    }

    public static UUID ingredientEntryId(
            UUID requestId,
            Ingredient ingredient
    ) {
        Objects.requireNonNull(ingredient, "ingredient");
        return deterministicUuid("ingredient entry", requestId,
                ingredient.ingredientIndex() + "\u0000"
                        + ingredient.ingredientId() + "\u0000"
                        + ingredient.itemId() + "\u0000"
                        + ingredient.matchMode().name() + "\u0000"
                        + ingredientIdentityTemplate(ingredient));
    }

    public static UUID ingredientCustodyRequestId(UUID requestId) {
        return deterministicUuid("ingredient custody", requestId, "");
    }

    public static UUID ingredientLotId(
            UUID requestId,
            int allocationIndex
    ) {
        if (allocationIndex < 0
                || allocationIndex
                >= ItemInventoryMutationReceipt.MAX_ALLOCATIONS) {
            throw new IllegalArgumentException(
                    "Server shop barter allocation index is invalid");
        }
        return deterministicUuid("ingredient lot", requestId,
                Integer.toString(allocationIndex));
    }

    public static UUID stockReserveRequestId(UUID requestId) {
        return deterministicUuid("stock reserve", requestId, "");
    }

    public static UUID stockCommitRequestId(UUID requestId) {
        return deterministicUuid("stock commit", requestId, "");
    }

    public static UUID stockReleaseRequestId(UUID requestId) {
        return deterministicUuid("stock release", requestId, "");
    }

    public static String outputSourceKey(
            UUID requestId,
            int outputIndex
    ) {
        requireUuid(requestId, "requestId");
        if (outputIndex < 0 || outputIndex >= MAX_OUTPUT_LINES) {
            throw new IllegalArgumentException(
                    "Server shop barter output index is invalid");
        }
        return "server.shop.barter.output." + requestId + "."
                + outputIndex;
    }

    public static String claimSourceKey(ExactItemClaimPayload payload) {
        Objects.requireNonNull(payload, "payload");
        return payload.sourceKey() + "." + payload.portionIndex();
    }

    public static List<ItemInventoryBatchEntry> custodyEntries(
            UUID requestId,
            int multiplier,
            List<Ingredient> ingredients
    ) {
        UUID safeRequest = requireUuid(requestId, "requestId");
        int safeMultiplier = requireMultiplier(multiplier);
        List<Ingredient> canonical = copyIngredients(ingredients);
        return canonical.stream().map(ingredient ->
                ItemInventoryBatchEntry.extract(
                        ingredientEntryId(safeRequest, ingredient),
                        ingredientMatcher(ingredient),
                        ingredient.totalQuantity(safeMultiplier)))
                .toList();
    }

    CanonicalInput canonicalInput() {
        return new CanonicalInput(requestId, playerId, shopId, recipeId,
                multiplier, quoteRevision, recipeRevision, quoteCreatedAt,
                ingredients, outputs, ingredientCustodyReceipt,
                completedTransaction.shopReference().orElseThrow());
    }

    static CanonicalComponents canonical(CanonicalInput input) {
        List<EscrowClaim> claims = claims(input);
        StockMutationCommand.ReserveBatch reserve = reserve(input);
        StockMutationCommand.ResolveBatch commit = commit(input);
        EscrowTransaction transaction = transaction(input);
        return new CanonicalComponents(transaction, reserve, commit,
                claims);
    }

    static long configurationRevision(
            long quoteRevision,
            long recipeRevision
    ) {
        requireRevision(quoteRevision, "quote revision");
        requireRevision(recipeRevision, "recipe revision");
        byte[] digest = HexFormat.of().parseHex(sha256((
                "futureshops barter configuration v1\u0000"
                        + quoteRevision + "\u0000" + recipeRevision)
                .getBytes(StandardCharsets.UTF_8)));
        long value = 0L;
        for (int index = 0; index < Long.BYTES; index++) {
            value = value << 8 | digest[index] & 255L;
        }
        return value & Long.MAX_VALUE;
    }

    static String quoteFingerprint(CanonicalInput input) {
        StringBuilder material = new StringBuilder(
                "futureshops server shop barter quote v1\u0000")
                .append(input.requestId()).append('\u0000')
                .append(input.playerId()).append('\u0000')
                .append(input.shopId()).append('\u0000')
                .append(input.recipeId()).append('\u0000')
                .append(input.multiplier()).append('\u0000')
                .append(input.quoteRevision()).append('\u0000')
                .append(input.recipeRevision()).append('\u0000')
                .append(input.quoteCreatedAt()).append('\u0000')
                .append(input.shopReference().dimensionId())
                .append('\u0000').append(input.shopReference().blockX())
                .append('\u0000').append(input.shopReference().blockY())
                .append('\u0000').append(input.shopReference().blockZ());
        for (Ingredient ingredient : input.ingredients()) {
            material.append('\u0000').append("ingredient")
                    .append('\u0000').append(
                            ingredient.ingredientIndex())
                    .append('\u0000').append(ingredient.ingredientId())
                    .append('\u0000').append(ingredient.itemId())
                    .append('\u0000').append(
                            ingredient.quantityPerTrade())
                    .append('\u0000').append(
                            ingredient.matchMode().name())
                    .append('\u0000').append(sha256(
                            ingredient.exactItemTemplate()));
        }
        for (OutputLine output : input.outputs()) {
            material.append('\u0000').append("output")
                    .append('\u0000').append(output.outputIndex())
                    .append('\u0000').append(output.listingId())
                    .append('\u0000').append(output.itemId())
                    .append('\u0000').append(output.quantityPerTrade())
                    .append('\u0000').append(
                            output.expectedStockRevision());
            for (ExactItemClaimPayload payload : output.portions()) {
                material.append('\u0000').append(payload.fingerprint());
            }
        }
        return sha256(material.toString().getBytes(StandardCharsets.UTF_8));
    }

    static ItemStack requireExactTemplate(
            byte[] encoded,
            String expectedItemId
    ) {
        byte[] copy = Objects.requireNonNull(
                encoded, "exactItemTemplate").clone();
        ItemStack stack = ItemStackSnapshotCodec.decode(copy);
        if (stack.getCount() != 1
                || !ItemStackSnapshotCodec.snapshotMatchesIdentity(copy,
                stack)) {
            throw new IllegalArgumentException(
                    "Server shop barter item template is not canonical");
        }
        ItemInputMatcher matcher = ItemInputMatcher.exact(stack);
        if (!matcher.registryItemId().equals(expectedItemId)) {
            throw new IllegalArgumentException(
                    "Server shop barter item template conflicts");
        }
        return stack;
    }

    static boolean exactPortionMatches(
            byte[] template,
            ItemInventoryAllocation allocation
    ) {
        try {
            ItemStack stack = ItemStackSnapshotCodec.decode(
                    allocation.actualStackSnapshot());
            if (stack.getCount() != allocation.count()) {
                return false;
            }
            stack.setCount(1);
            return ItemStackSnapshotCodec.snapshotMatchesIdentity(
                    template, stack);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    static boolean portionMatches(
            Ingredient ingredient,
            ItemInventoryAllocation allocation
    ) {
        Objects.requireNonNull(ingredient, "ingredient");
        Objects.requireNonNull(allocation, "allocation");
        if (ingredient.matchMode() == ItemMatchMode.EXACT) {
            return exactPortionMatches(
                    ingredient.exactItemTemplate(), allocation);
        }
        try {
            ItemStack stack = ItemStackSnapshotCodec.decode(
                    allocation.actualStackSnapshot());
            return stack.getCount() == allocation.count()
                    && ItemInputMatcher.itemOnly(
                    ingredient.itemId()).matches(stack);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static ItemInputMatcher ingredientMatcher(
            Ingredient ingredient
    ) {
        ItemStack template = requireExactTemplate(
                ingredient.exactItemTemplate(), ingredient.itemId());
        return ingredient.matchMode() == ItemMatchMode.EXACT
                ? ItemInputMatcher.exact(template)
                : ItemInputMatcher.itemOnly(ingredient.itemId());
    }

    private static String ingredientIdentityTemplate(
            Ingredient ingredient
    ) {
        return ingredient.matchMode() == ItemMatchMode.EXACT
                ? sha256(ingredient.exactItemTemplate()) : "item only";
    }

    private static ItemMatchMode defaultMatchMode(
            byte[] exactItemTemplate,
            String itemId
    ) {
        ItemStack template = requireExactTemplate(
                exactItemTemplate, itemId);
        return template.hasTag() && !template.getTag().isEmpty()
                ? ItemMatchMode.EXACT : ItemMatchMode.ITEM_ONLY;
    }

    private static EscrowTransaction transaction(CanonicalInput input) {
        EscrowParty player = EscrowParty.player(input.playerId());
        EscrowParty shop = EscrowParty.shop(input.shopId());
        String quoteFingerprint = quoteFingerprint(input);
        String wireFingerprint = wireFingerprint(input.requestId(),
                input.playerId(), input.shopId(), input.recipeId(),
                input.multiplier());
        String custodyDigest = HexFormat.of().formatHex(
                input.ingredientCustodyReceipt().digest());
        Map<UUID, Ingredient> ingredientsByEntry = new HashMap<>();
        for (Ingredient ingredient : input.ingredients()) {
            ingredientsByEntry.put(ingredientEntryId(
                    input.requestId(), ingredient), ingredient);
        }
        List<EscrowAssetLot> assets = new ArrayList<>();
        List<ItemInventoryAllocation> allocations =
                input.ingredientCustodyReceipt().actualPortions();
        for (int index = 0; index < allocations.size(); index++) {
            ItemInventoryAllocation allocation = allocations.get(index);
            Ingredient ingredient = ingredientsByEntry.get(
                    allocation.entryId());
            if (ingredient == null) {
                throw new IllegalArgumentException(
                        "Server shop barter custody entry is unknown");
            }
            assets.add(new EscrowAssetLot(
                    ingredientLotId(input.requestId(), index),
                    EscrowAssetLotType.ITEM_STACK,
                    EscrowProtectionLevel.PROTECTED,
                    player, shop, allocation.count(), Optional.empty(),
                    allocation.actualStackSnapshot(), commonAttributes(
                    input, quoteFingerprint, wireFingerprint,
                    custodyDigest, Map.ofEntries(
                    Map.entry("allocation_index", Integer.toString(index)),
                    Map.entry("asset_role", "ingredient"),
                    Map.entry("entry_id", allocation.entryId().toString()),
                    Map.entry("ingredient_id",
                            ingredient.ingredientId()),
                    Map.entry("ingredient_index", Integer.toString(
                            ingredient.ingredientIndex())),
                    Map.entry("inventory_slot", Integer.toString(
                            allocation.slot().serializedSlot())),
                    Map.entry("item_id", ingredient.itemId()),
                    Map.entry("match_mode",
                            ingredient.matchMode().name()),
                    Map.entry("quantity_per_trade", Integer.toString(
                            ingredient.quantityPerTrade())),
                    Map.entry("template_fingerprint", sha256(
                            ingredient.exactItemTemplate()))))));
        }
        for (OutputLine output : input.outputs()) {
            for (ExactItemClaimPayload payload : output.portions()) {
                assets.add(new EscrowAssetLot(payload.lotId(),
                        EscrowAssetLotType.ITEM_STACK,
                        EscrowProtectionLevel.PROTECTED,
                        shop, player, payload.stackCount(),
                        Optional.empty(),
                        ExactItemClaimPayloadCodec.encode(payload),
                        commonAttributes(input, quoteFingerprint,
                                wireFingerprint, custodyDigest,
                                Map.ofEntries(
                                Map.entry("asset_role", "output"),
                                Map.entry("item_id", output.itemId()),
                                Map.entry("listing_id",
                                        output.listingId()),
                                Map.entry("output_index", Integer.toString(
                                        output.outputIndex())),
                                Map.entry("portion_count", Integer.toString(
                                        payload.portionCount())),
                                Map.entry("portion_index", Integer.toString(
                                        payload.portionIndex())),
                                Map.entry("quantity_per_trade",
                                        Integer.toString(
                                                output.quantityPerTrade())),
                                Map.entry("stock_revision", Long.toString(
                                        output.expectedStockRevision()))))));
            }
        }
        Set<EscrowParticipant> participants = Set.of(
                new EscrowParticipant(player, Set.of(
                        EscrowParticipantRole.INITIATOR,
                        EscrowParticipantRole.PAYER,
                        EscrowParticipantRole.BUYER,
                        EscrowParticipantRole.RECIPIENT)),
                new EscrowParticipant(shop, Set.of(
                        EscrowParticipantRole.BENEFICIARY,
                        EscrowParticipantRole.SELLER,
                        EscrowParticipantRole.CUSTODIAN)));
        String requestKey = "server.shop.barter." + input.requestId()
                + "." + sha256((quoteFingerprint + "\u0000"
                + custodyDigest).getBytes(StandardCharsets.UTF_8));
        Instant now = input.ingredientCustodyReceipt().appliedAt();
        EscrowTransaction created = EscrowTransaction.create(
                new EscrowTransactionId(input.requestId()),
                Optional.empty(), new EscrowRequestKey(requestKey),
                EscrowOperation.SERVER_SHOP_BARTER, participants, assets,
                now, configurationRevision(input.quoteRevision(),
                        input.recipeRevision()),
                Optional.of(input.shopReference()));
        return created.transitionTo(EscrowState.VALIDATED, now)
                .transitionTo(EscrowState.HOLDING, now)
                .transitionTo(EscrowState.HELD, now)
                .transitionTo(EscrowState.COMMIT_DECIDED, now)
                .transitionTo(EscrowState.COMMITTED, now)
                .transitionTo(EscrowState.CLAIMS_CREATED, now)
                .transitionTo(EscrowState.COMPLETED, now);
    }

    private static Map<String, String> commonAttributes(
            CanonicalInput input,
            String quoteFingerprint,
            String wireFingerprint,
            String custodyDigest,
            Map<String, String> specific
    ) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("ingredient_custody_digest", custodyDigest);
        values.put("multiplier", Integer.toString(input.multiplier()));
        values.put("quote_created_at", input.quoteCreatedAt().toString());
        values.put("quote_fingerprint", quoteFingerprint);
        values.put("quote_revision", Long.toString(input.quoteRevision()));
        values.put("recipe_id", input.recipeId());
        values.put("recipe_revision", Long.toString(
                input.recipeRevision()));
        values.put("shop_id", input.shopId());
        values.put("wire_fingerprint", wireFingerprint);
        for (Map.Entry<String, String> entry : specific.entrySet()) {
            if (values.put(entry.getKey(), entry.getValue()) != null) {
                throw new IllegalArgumentException(
                        "Server shop barter asset attribute is duplicated");
            }
        }
        return Map.copyOf(values);
    }

    private static List<EscrowClaim> claims(CanonicalInput input) {
        List<EscrowClaim> claims = new ArrayList<>();
        Instant now = input.ingredientCustodyReceipt().appliedAt();
        for (OutputLine output : input.outputs()) {
            for (ExactItemClaimPayload payload : output.portions()) {
                claims.add(new EscrowClaim(payload.lotId(),
                        input.requestId(), input.playerId(),
                        claimSourceKey(payload), ClaimKind.ITEM,
                        payload.stackCount(), payload.stackCount(),
                        ExactItemClaimPayloadCodec.encode(payload),
                        ClaimStatus.PENDING, CLAIM_LABEL, now, now));
            }
        }
        return List.copyOf(claims);
    }

    private static StockMutationCommand.ReserveBatch reserve(
            CanonicalInput input
    ) {
        return stockReservation(input.requestId(), input.shopId(),
                input.multiplier(), input.outputs(),
                input.quoteCreatedAt());
    }

    public static StockMutationCommand.ReserveBatch stockReservation(
            UUID requestId,
            String shopId,
            int multiplier,
            List<OutputLine> outputs,
            Instant appliedAt
    ) {
        UUID safeRequest = requireUuid(requestId, "requestId");
        String safeShop = requireIdentifier(shopId, "shopId");
        int safeMultiplier = requireMultiplier(multiplier);
        List<OutputLine> canonical = copyOutputs(outputs);
        List<StockReservationRequest> reservations = canonical.stream()
                .map(output -> new StockReservationRequest(
                        new StockKey(safeShop, output.listingId()),
                        StockReservationDirection.OUTBOUND,
                        output.totalQuantity(safeMultiplier),
                        output.expectedStockRevision()))
                .toList();
        return new StockMutationCommand.ReserveBatch(
                stockReserveRequestId(safeRequest),
                safeRequest, reservations, Objects.requireNonNull(
                appliedAt, "appliedAt"));
    }

    private static StockMutationCommand.ResolveBatch commit(
            CanonicalInput input
    ) {
        List<StockReservationResolution> resolutions =
                input.outputs().stream().map(output ->
                        new StockReservationResolution(
                                StockReservationId.forTransaction(
                                        input.requestId(),
                                        new StockKey(input.shopId(),
                                                output.listingId()),
                                        StockReservationDirection.OUTBOUND),
                                0L)).toList();
        return new StockMutationCommand.ResolveBatch(
                stockCommitRequestId(input.requestId()),
                StockMutationType.COMMIT_BATCH, input.requestId(),
                resolutions,
                input.ingredientCustodyReceipt().appliedAt());
    }

    public static StockMutationCommand.ResolveBatch stockRelease(
            UUID requestId,
            String shopId,
            List<OutputLine> outputs,
            Instant appliedAt
    ) {
        UUID safeRequest = requireUuid(requestId, "requestId");
        String safeShop = requireIdentifier(shopId, "shopId");
        List<OutputLine> canonical = copyOutputs(outputs);
        List<StockReservationResolution> resolutions = canonical.stream()
                .map(output -> new StockReservationResolution(
                        StockReservationId.forTransaction(safeRequest,
                                new StockKey(safeShop,
                                        output.listingId()),
                                StockReservationDirection.OUTBOUND),
                        0L)).toList();
        return new StockMutationCommand.ResolveBatch(
                stockReleaseRequestId(safeRequest),
                StockMutationType.RELEASE_BATCH, safeRequest,
                resolutions, Objects.requireNonNull(appliedAt,
                "appliedAt"));
    }

    static List<Ingredient> copyIngredients(List<Ingredient> values) {
        List<Ingredient> copied = List.copyOf(Objects.requireNonNull(
                values, "ingredients"));
        if (copied.isEmpty() || copied.size() > MAX_INGREDIENTS) {
            throw new IllegalArgumentException(
                    "Server shop barter ingredient count is invalid");
        }
        Set<String> ids = new HashSet<>();
        Set<String> identities = new HashSet<>();
        for (int index = 0; index < copied.size(); index++) {
            Ingredient ingredient = Objects.requireNonNull(
                    copied.get(index), "ingredient");
            String identity = ingredient.itemId() + "\u0000"
                    + ingredient.matchMode().name() + "\u0000"
                    + ingredientIdentityTemplate(ingredient);
            if (ingredient.ingredientIndex() != index
                    || !ids.add(ingredient.ingredientId())
                    || !identities.add(identity)) {
                throw new IllegalArgumentException(
                        "Server shop barter ingredient identities are invalid");
            }
        }
        return copied;
    }

    static List<OutputLine> copyOutputs(List<OutputLine> values) {
        List<OutputLine> copied = List.copyOf(Objects.requireNonNull(
                values, "outputs"));
        if (copied.isEmpty() || copied.size() > MAX_OUTPUT_LINES) {
            throw new IllegalArgumentException(
                    "Server shop barter output count is invalid");
        }
        Set<String> listings = new HashSet<>();
        Set<UUID> lots = new HashSet<>();
        int totalPortions = 0;
        for (int index = 0; index < copied.size(); index++) {
            OutputLine output = Objects.requireNonNull(
                    copied.get(index), "output");
            if (output.outputIndex() != index
                    || !listings.add(output.listingId())) {
                throw new IllegalArgumentException(
                        "Server shop barter output identities are invalid");
            }
            totalPortions = Math.addExact(totalPortions,
                    output.portions().size());
            for (ExactItemClaimPayload portion : output.portions()) {
                if (!lots.add(portion.lotId())) {
                    throw new IllegalArgumentException(
                            "Server shop barter output lot is duplicated");
                }
            }
        }
        if (totalPortions > MAX_TOTAL_OUTPUT_PORTIONS) {
            throw new IllegalArgumentException(
                    "Server shop barter output portions exceed their limit");
        }
        return copied;
    }

    static int requireMultiplier(int multiplier) {
        if (multiplier <= 0) {
            throw new IllegalArgumentException(
                    "Server shop barter multiplier is invalid");
        }
        return multiplier;
    }

    static long requireRevision(long revision, String label) {
        if (revision < 0L || revision > MAX_REVISION) {
            throw new IllegalArgumentException(
                    "Server shop barter " + label + " is invalid");
        }
        return revision;
    }

    static UUID requireUuid(UUID value, String label) {
        UUID safe = Objects.requireNonNull(value, label);
        if (ZERO_UUID.equals(safe)) {
            throw new IllegalArgumentException(
                    "Server shop barter " + label + " is invalid");
        }
        return safe;
    }

    static String requireIdentifier(String value, String label) {
        String safe = Objects.requireNonNull(value, label).strip();
        if (safe.isEmpty() || safe.length() > MAX_IDENTIFIER_LENGTH) {
            throw new IllegalArgumentException(
                    "Server shop barter " + label + " is invalid");
        }
        return safe;
    }

    static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(
                    "SHA-256").digest(Objects.requireNonNull(
                    value, "value")));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable", exception);
        }
    }

    private static UUID deterministicUuid(
            String purpose,
            UUID requestId,
            String suffix
    ) {
        String material = "futureshops server shop barter v1\u0000"
                + Objects.requireNonNull(purpose, "purpose") + "\u0000"
                + requireUuid(requestId, "requestId") + "\u0000"
                + Objects.requireNonNull(suffix, "suffix");
        return UUID.nameUUIDFromBytes(
                material.getBytes(StandardCharsets.UTF_8));
    }

    public record Ingredient(
            int ingredientIndex,
            String ingredientId,
            String itemId,
            int quantityPerTrade,
            ItemMatchMode matchMode,
            byte[] exactItemTemplate
    ) {
        public Ingredient(
                int ingredientIndex,
                String ingredientId,
                String itemId,
                int quantityPerTrade,
                byte[] exactItemTemplate
        ) {
            this(ingredientIndex, ingredientId, itemId,
                    quantityPerTrade, defaultMatchMode(
                            exactItemTemplate, itemId),
                    exactItemTemplate);
        }

        public Ingredient {
            if (ingredientIndex < 0 || ingredientIndex >= MAX_INGREDIENTS
                    || quantityPerTrade <= 0) {
                throw new IllegalArgumentException(
                        "Server shop barter ingredient is invalid");
            }
            ingredientId = requireIdentifier(
                    ingredientId, "ingredientId");
            itemId = requireIdentifier(itemId, "itemId");
            matchMode = Objects.requireNonNull(
                    matchMode, "matchMode");
            exactItemTemplate = Objects.requireNonNull(
                    exactItemTemplate, "exactItemTemplate").clone();
            requireExactTemplate(exactItemTemplate, itemId);
        }

        @Override
        public byte[] exactItemTemplate() {
            return exactItemTemplate.clone();
        }

        public int totalQuantity(int multiplier) {
            return Math.multiplyExact(quantityPerTrade,
                    requireMultiplier(multiplier));
        }

        @Override
        public boolean equals(Object object) {
            return object instanceof Ingredient other
                    && ingredientIndex == other.ingredientIndex
                    && ingredientId.equals(other.ingredientId)
                    && itemId.equals(other.itemId)
                    && quantityPerTrade == other.quantityPerTrade
                    && matchMode == other.matchMode
                    && Arrays.equals(exactItemTemplate,
                    other.exactItemTemplate);
        }

        @Override
        public int hashCode() {
            return 31 * Objects.hash(ingredientIndex, ingredientId,
                    itemId, quantityPerTrade, matchMode)
                    + Arrays.hashCode(exactItemTemplate);
        }
    }

    public record OutputLine(
            int outputIndex,
            String listingId,
            String itemId,
            int quantityPerTrade,
            long expectedStockRevision,
            List<ExactItemClaimPayload> portions
    ) {
        public OutputLine {
            if (outputIndex < 0 || outputIndex >= MAX_OUTPUT_LINES
                    || quantityPerTrade <= 0) {
                throw new IllegalArgumentException(
                        "Server shop barter output is invalid");
            }
            listingId = requireIdentifier(listingId, "listingId");
            itemId = requireIdentifier(itemId, "itemId");
            requireRevision(expectedStockRevision,
                    "expected stock revision");
            portions = List.copyOf(Objects.requireNonNull(
                    portions, "portions"));
            if (portions.isEmpty()
                    || portions.size()
                    > ExactItemClaimPayload.MAX_PORTIONS) {
                throw new IllegalArgumentException(
                        "Server shop barter output portions are invalid");
            }
            int delivered = 0;
            for (int index = 0; index < portions.size(); index++) {
                ExactItemClaimPayload payload = Objects.requireNonNull(
                        portions.get(index), "portion");
                if (payload.portionIndex() != index
                        || payload.portionCount() != portions.size()
                        || !payload.registryItemId().equals(itemId)) {
                    throw new IllegalArgumentException(
                            "Server shop barter output portion is invalid");
                }
                delivered = Math.addExact(delivered,
                        payload.stackCount());
            }
            if (delivered <= 0) {
                throw new IllegalArgumentException(
                        "Server shop barter output quantity is invalid");
            }
        }

        public int totalQuantity(int multiplier) {
            return Math.multiplyExact(quantityPerTrade,
                    requireMultiplier(multiplier));
        }
    }

    record CanonicalInput(
            UUID requestId,
            UUID playerId,
            String shopId,
            String recipeId,
            int multiplier,
            long quoteRevision,
            long recipeRevision,
            Instant quoteCreatedAt,
            List<Ingredient> ingredients,
            List<OutputLine> outputs,
            ItemInventoryMutationReceipt ingredientCustodyReceipt,
            DimensionAwareShopReference shopReference
    ) {
        CanonicalInput {
            requestId = requireUuid(requestId, "requestId");
            playerId = requireUuid(playerId, "playerId");
            shopId = requireIdentifier(shopId, "shopId");
            recipeId = requireIdentifier(recipeId, "recipeId");
            multiplier = requireMultiplier(multiplier);
            requireRevision(quoteRevision, "quote revision");
            requireRevision(recipeRevision, "recipe revision");
            quoteCreatedAt = Objects.requireNonNull(
                    quoteCreatedAt, "quoteCreatedAt");
            ingredients = copyIngredients(ingredients);
            outputs = copyOutputs(outputs);
            for (Ingredient ingredient : ingredients) {
                ingredient.totalQuantity(multiplier);
            }
            for (OutputLine output : outputs) {
                int total = output.totalQuantity(multiplier);
                int delivered = 0;
                for (ExactItemClaimPayload portion : output.portions()) {
                    if (!portion.sourceTransactionId().equals(requestId)
                            || !portion.sourceKey().equals(
                            outputSourceKey(requestId,
                                    output.outputIndex()))) {
                        throw new IllegalArgumentException(
                                "Server shop barter output identity conflicts");
                    }
                    delivered = Math.addExact(delivered,
                            portion.stackCount());
                }
                if (delivered != total) {
                    throw new IllegalArgumentException(
                            "Server shop barter output quantity conflicts");
                }
            }
            ingredientCustodyReceipt = Objects.requireNonNull(
                    ingredientCustodyReceipt,
                    "ingredientCustodyReceipt");
            shopReference = Objects.requireNonNull(
                    shopReference, "shopReference");
            if (!shopReference.shopId().equals(shopId)
                    || quoteCreatedAt.isAfter(
                    ingredientCustodyReceipt.appliedAt())) {
                throw new IllegalArgumentException(
                        "Server shop barter quote context is invalid");
            }
        }
    }

    record CanonicalComponents(
            EscrowTransaction transaction,
            StockMutationCommand.ReserveBatch reserve,
            StockMutationCommand.ResolveBatch commit,
            List<EscrowClaim> claims
    ) {
    }

    record CommitView(
            UUID requestId,
            UUID playerId,
            String shopId,
            String recipeId,
            int multiplier,
            long quoteRevision,
            long recipeRevision,
            Instant quoteCreatedAt,
            List<Ingredient> ingredients,
            List<OutputLine> outputs,
            ItemInventoryMutationReceipt ingredientCustodyReceipt,
            EscrowTransaction completedTransaction,
            StockMutationCommand.ReserveBatch stockReservation,
            StockMutationCommand.ResolveBatch stockCommit,
            List<EscrowClaim> outputClaims
    ) {
        CanonicalInput canonicalInput() {
            return new CanonicalInput(requestId, playerId, shopId,
                    recipeId, multiplier, quoteRevision, recipeRevision,
                    quoteCreatedAt, ingredients, outputs,
                    ingredientCustodyReceipt,
                    completedTransaction.shopReference().orElseThrow());
        }
    }
}

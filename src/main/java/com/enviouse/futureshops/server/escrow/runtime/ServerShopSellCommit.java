package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.money.ItemStackSnapshotCodec;
import com.enviouse.futureshops.server.escrow.claim.ClaimKind;
import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.item.ItemInputMatcher;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryAllocation;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryBatchEntry;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryBatchPlanner;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationDirection;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationReceipt;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountId;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountType;
import com.enviouse.futureshops.server.escrow.ledger.LedgerLeg;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;
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
import com.enviouse.futureshops.server.escrow.model.MoneyAmount;
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
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public record ServerShopSellCommit(
        UUID requestId,
        UUID playerId,
        String shopId,
        String listingId,
        String itemId,
        int quantity,
        long unitPriceMinorUnits,
        long quoteRevision,
        long expectedStockRevision,
        Instant quoteCreatedAt,
        long walletBeforeMinorUnits,
        long debtBeforeMinorUnits,
        long reservedBeforeMinorUnits,
        long walletBalanceLimitMinorUnits,
        long configurationGeneration,
        String currencyName,
        int currencyDecimals,
        byte[] exactItemTemplate,
        ItemInventoryMutationReceipt itemCustodyReceipt,
        EscrowTransaction completedTransaction,
        LedgerTransaction ledgerTransaction,
        StockMutationCommand.ReserveBatch stockReservation,
        StockMutationCommand.ResolveBatch stockCommit,
        Optional<EscrowClaim> overflowClaim
) {
    public static final String CURRENCY_ID = "futureshops:wallet";
    public static final String LEDGER_REASON = "Server shop sell";
    public static final String CLAIM_LABEL = "Server shop sell overflow";
    public static final int MAX_IDENTIFIER_LENGTH = 160;
    public static final int MAX_CURRENCY_NAME_LENGTH = 128;
    public static final long MAX_REVISION = 1_000_000_000_000L;

    private static final UUID ZERO_UUID = new UUID(0L, 0L);

    public ServerShopSellCommit {
        requestId = requireUuid(requestId, "requestId");
        playerId = requireUuid(playerId, "playerId");
        shopId = requireIdentifier(shopId, "shopId");
        listingId = requireIdentifier(listingId, "listingId");
        itemId = requireIdentifier(itemId, "itemId");
        quoteCreatedAt = Objects.requireNonNull(
                quoteCreatedAt, "quoteCreatedAt");
        currencyName = normalizeCurrencyName(currencyName);
        exactItemTemplate = Objects.requireNonNull(
                exactItemTemplate, "exactItemTemplate").clone();
        itemCustodyReceipt = Objects.requireNonNull(
                itemCustodyReceipt, "itemCustodyReceipt");
        completedTransaction = Objects.requireNonNull(
                completedTransaction, "completedTransaction");
        ledgerTransaction = Objects.requireNonNull(
                ledgerTransaction, "ledgerTransaction");
        stockReservation = Objects.requireNonNull(
                stockReservation, "stockReservation");
        stockCommit = Objects.requireNonNull(stockCommit, "stockCommit");
        overflowClaim = Objects.requireNonNull(
                overflowClaim, "overflowClaim");
        ServerShopSellConservationValidator.validate(
                new ServerShopSellCommitView(requestId, playerId, shopId,
                        listingId, itemId, quantity, unitPriceMinorUnits,
                        quoteRevision, expectedStockRevision,
                        quoteCreatedAt, walletBeforeMinorUnits,
                        debtBeforeMinorUnits, reservedBeforeMinorUnits,
                        walletBalanceLimitMinorUnits,
                        configurationGeneration, currencyName,
                        currencyDecimals, exactItemTemplate,
                        itemCustodyReceipt, completedTransaction,
                        ledgerTransaction, stockReservation, stockCommit,
                        overflowClaim));
    }

    public static ServerShopSellCommit create(
            UUID requestId,
            UUID playerId,
            String shopId,
            String listingId,
            String itemId,
            int quantity,
            long unitPriceMinorUnits,
            long quoteRevision,
            long expectedStockRevision,
            Instant quoteCreatedAt,
            long walletBeforeMinorUnits,
            long debtBeforeMinorUnits,
            long reservedBeforeMinorUnits,
            long walletBalanceLimitMinorUnits,
            long configurationGeneration,
            String currencyName,
            int currencyDecimals,
            byte[] exactItemTemplate,
            ItemInventoryMutationReceipt itemCustodyReceipt,
            DimensionAwareShopReference shopReference
    ) {
        CanonicalInput input = new CanonicalInput(requestId, playerId,
                shopId, listingId, itemId, quantity, unitPriceMinorUnits,
                quoteRevision, expectedStockRevision, quoteCreatedAt,
                walletBeforeMinorUnits, debtBeforeMinorUnits,
                reservedBeforeMinorUnits, walletBalanceLimitMinorUnits,
                configurationGeneration, currencyName, currencyDecimals,
                exactItemTemplate, itemCustodyReceipt, shopReference);
        CanonicalComponents components = canonical(input);
        return new ServerShopSellCommit(input.requestId(), input.playerId(),
                input.shopId(), input.listingId(), input.itemId(),
                input.quantity(), input.unitPriceMinorUnits(),
                input.quoteRevision(), input.expectedStockRevision(),
                input.quoteCreatedAt(), input.walletBeforeMinorUnits(),
                input.debtBeforeMinorUnits(),
                input.reservedBeforeMinorUnits(),
                input.walletBalanceLimitMinorUnits(),
                input.configurationGeneration(), input.currencyName(),
                input.currencyDecimals(), input.exactItemTemplate(),
                input.itemCustodyReceipt(), components.transaction(),
                components.ledger(), components.reserve(),
                components.commit(), components.claim());
    }

    @Override
    public byte[] exactItemTemplate() {
        return exactItemTemplate.clone();
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof ServerShopSellCommit other)) {
            return false;
        }
        return requestId.equals(other.requestId)
                && playerId.equals(other.playerId)
                && shopId.equals(other.shopId)
                && listingId.equals(other.listingId)
                && itemId.equals(other.itemId)
                && quantity == other.quantity
                && unitPriceMinorUnits == other.unitPriceMinorUnits
                && quoteRevision == other.quoteRevision
                && expectedStockRevision == other.expectedStockRevision
                && quoteCreatedAt.equals(other.quoteCreatedAt)
                && walletBeforeMinorUnits == other.walletBeforeMinorUnits
                && debtBeforeMinorUnits == other.debtBeforeMinorUnits
                && reservedBeforeMinorUnits == other.reservedBeforeMinorUnits
                && walletBalanceLimitMinorUnits
                == other.walletBalanceLimitMinorUnits
                && configurationGeneration == other.configurationGeneration
                && currencyName.equals(other.currencyName)
                && currencyDecimals == other.currencyDecimals
                && Arrays.equals(exactItemTemplate,
                other.exactItemTemplate)
                && itemCustodyReceipt.equals(other.itemCustodyReceipt)
                && completedTransaction.equals(other.completedTransaction)
                && ledgerTransaction.equals(other.ledgerTransaction)
                && stockReservation.equals(other.stockReservation)
                && stockCommit.equals(other.stockCommit)
                && overflowClaim.equals(other.overflowClaim);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(requestId, playerId, shopId, listingId,
                itemId, quantity, unitPriceMinorUnits, quoteRevision,
                expectedStockRevision, quoteCreatedAt,
                walletBeforeMinorUnits, debtBeforeMinorUnits,
                reservedBeforeMinorUnits, walletBalanceLimitMinorUnits,
                configurationGeneration, currencyName, currencyDecimals,
                itemCustodyReceipt, completedTransaction,
                ledgerTransaction, stockReservation, stockCommit,
                overflowClaim);
        return 31 * result + Arrays.hashCode(exactItemTemplate);
    }

    public long payoutMinorUnits() {
        return Math.multiplyExact(unitPriceMinorUnits, quantity);
    }

    public long acceptedPayoutMinorUnits() {
        return acceptedMinorUnits(payoutMinorUnits(),
                walletBeforeMinorUnits, debtBeforeMinorUnits,
                reservedBeforeMinorUnits, walletBalanceLimitMinorUnits);
    }

    public long debtCreditMinorUnits() {
        return debtCreditMinorUnits(
                acceptedPayoutMinorUnits(), debtBeforeMinorUnits);
    }

    public long walletCreditMinorUnits() {
        return Math.subtractExact(
                acceptedPayoutMinorUnits(), debtCreditMinorUnits());
    }

    public long overflowClaimMinorUnits() {
        return Math.subtractExact(
                payoutMinorUnits(), acceptedPayoutMinorUnits());
    }

    public long resultingBalanceMinorUnits() {
        return Math.addExact(Math.addExact(walletBeforeMinorUnits,
                debtBeforeMinorUnits), acceptedPayoutMinorUnits());
    }

    public String wireFingerprint() {
        return wireFingerprint(requestId, playerId, shopId, listingId,
                quantity);
    }

    public String quoteFingerprint() {
        return quoteFingerprint(canonicalInput());
    }

    public static String wireFingerprint(
            UUID requestId,
            UUID playerId,
            String shopId,
            String listingId,
            int quantity
    ) {
        String material = "futureshops server shop sell wire v1\u0000"
                + requireUuid(requestId, "requestId") + "\u0000"
                + requireUuid(playerId, "playerId") + "\u0000"
                + requireIdentifier(shopId, "shopId") + "\u0000"
                + requireIdentifier(listingId, "listingId") + "\u0000"
                + requireQuantity(quantity);
        return sha256(material.getBytes(StandardCharsets.UTF_8));
    }

    public static UUID itemEntryId(UUID requestId) {
        return deterministicUuid("item entry", requestId, "");
    }

    public static UUID itemCustodyRequestId(UUID requestId) {
        return deterministicUuid("item custody", requestId, "");
    }

    public static UUID stockReserveRequestId(UUID requestId) {
        return deterministicUuid("stock reserve", requestId, "");
    }

    public static UUID stockCommitRequestId(UUID requestId) {
        return deterministicUuid("stock commit", requestId, "");
    }

    public static UUID overflowClaimId(UUID requestId) {
        return deterministicUuid("overflow claim", requestId, "");
    }

    public static UUID itemLotId(UUID requestId, int allocationIndex) {
        if (allocationIndex < 0
                || allocationIndex
                >= ItemInventoryMutationReceipt.MAX_ALLOCATIONS) {
            throw new IllegalArgumentException(
                    "Server shop sell allocation index is invalid");
        }
        return deterministicUuid("item lot", requestId,
                Integer.toString(allocationIndex));
    }

    public static UUID moneyLotId(UUID requestId) {
        return deterministicUuid("money lot", requestId, "");
    }

    public static String overflowClaimSourceKey(UUID requestId) {
        return "server.shop.sell." + requireUuid(requestId, "requestId")
                + ".overflow";
    }

    public static String ledgerIdempotencyKey(UUID requestId) {
        return "server.shop.sell." + requireUuid(requestId, "requestId")
                + ".ledger";
    }

    public static LedgerAccountId walletAccount(UUID playerId) {
        return new LedgerAccountId(LedgerAccountType.PLAYER_WALLET,
                requireUuid(playerId, "playerId").toString());
    }

    public static LedgerAccountId debtAccount(UUID playerId) {
        return new LedgerAccountId(LedgerAccountType.PLAYER_DEBT,
                requireUuid(playerId, "playerId").toString());
    }

    public static LedgerAccountId reservedAccount(UUID playerId) {
        return new LedgerAccountId(LedgerAccountType.PLAYER_RESERVED,
                requireUuid(playerId, "playerId").toString());
    }

    public static LedgerAccountId sourceAccount(String shopId) {
        String normalized = requireIdentifier(shopId, "shopId");
        String owner = normalized.length() <= 128
                ? normalized : "shop." + sha256(
                normalized.getBytes(StandardCharsets.UTF_8));
        return new LedgerAccountId(LedgerAccountType.SERVER_SHOP_SOURCE,
                owner);
    }

    public static List<ItemInventoryBatchEntry> custodyEntries(
            UUID requestId,
            int quantity,
            byte[] exactItemTemplate
    ) {
        ItemStack template = requireExactTemplate(
                exactItemTemplate, null);
        return List.of(ItemInventoryBatchEntry.extract(
                itemEntryId(requestId), ItemInputMatcher.exact(template),
                requireQuantity(quantity)));
    }

    CanonicalInput canonicalInput() {
        return new CanonicalInput(requestId, playerId, shopId, listingId,
                itemId, quantity, unitPriceMinorUnits, quoteRevision,
                expectedStockRevision, quoteCreatedAt,
                walletBeforeMinorUnits, debtBeforeMinorUnits,
                reservedBeforeMinorUnits, walletBalanceLimitMinorUnits,
                configurationGeneration, currencyName, currencyDecimals,
                exactItemTemplate, itemCustodyReceipt,
                completedTransaction.shopReference().orElseThrow());
    }

    static CanonicalComponents canonical(CanonicalInput input) {
        long payout = payout(input.unitPriceMinorUnits(), input.quantity());
        long accepted = acceptedMinorUnits(payout,
                input.walletBeforeMinorUnits(),
                input.debtBeforeMinorUnits(),
                input.reservedBeforeMinorUnits(),
                input.walletBalanceLimitMinorUnits());
        long debtCredit = debtCreditMinorUnits(
                accepted, input.debtBeforeMinorUnits());
        long walletCredit = Math.subtractExact(accepted, debtCredit);
        long overflow = Math.subtractExact(payout, accepted);
        Optional<EscrowClaim> claim = overflowClaim(
                input, overflow);
        LedgerTransaction ledger = ledger(input, payout, debtCredit,
                walletCredit, overflow);
        EscrowTransaction transaction = transaction(input, payout,
                accepted, debtCredit, walletCredit, overflow);
        StockMutationCommand.ReserveBatch reserve = stockReserve(input);
        StockMutationCommand.ResolveBatch commit = stockCommit(input);
        return new CanonicalComponents(transaction, ledger, reserve,
                commit, claim);
    }

    static long acceptedMinorUnits(
            long amount,
            long wallet,
            long debt,
            long reserved,
            long limit
    ) {
        requirePayout(amount);
        requireWalletSnapshot(wallet, debt, reserved, limit);
        long balance = Math.addExact(wallet, debt);
        long exposure = Math.addExact(balance, reserved);
        if (exposure >= limit) {
            return 0L;
        }
        long capacity;
        try {
            capacity = Math.subtractExact(limit, exposure);
        } catch (ArithmeticException exception) {
            capacity = Long.MAX_VALUE;
        }
        return Math.min(amount, capacity);
    }

    static long debtCreditMinorUnits(long accepted, long debt) {
        if (accepted <= 0L || debt >= 0L) {
            return 0L;
        }
        if (debt == Long.MIN_VALUE) {
            return accepted;
        }
        return Math.min(accepted, Math.negateExact(debt));
    }

    static void requireWalletSnapshot(
            long wallet,
            long debt,
            long reserved,
            long limit
    ) {
        if (wallet < 0L || debt > 0L || wallet > 0L && debt < 0L
                || reserved < 0L || limit < 0L) {
            throw new IllegalArgumentException(
                    "Server shop sell wallet snapshot is invalid");
        }
    }

    static ItemStack requireExactTemplate(
            byte[] encoded,
            String expectedItemId
    ) {
        byte[] copy = Objects.requireNonNull(
                encoded, "exactItemTemplate").clone();
        ItemStack stack = ItemStackSnapshotCodec.decode(copy);
        if (stack.getCount() != 1
                || !Arrays.equals(copy, ItemStackSnapshotCodec.encode(
                stack))) {
            throw new IllegalArgumentException(
                    "Server shop sell item template is not canonical");
        }
        ItemInputMatcher matcher = ItemInputMatcher.exact(stack);
        if (expectedItemId != null
                && !matcher.registryItemId().equals(expectedItemId)) {
            throw new IllegalArgumentException(
                    "Server shop sell item template conflicts");
        }
        return stack;
    }

    static boolean exactPortionMatches(
            byte[] expectedTemplate,
            ItemInventoryAllocation allocation
    ) {
        try {
            ItemStack actual = ItemStackSnapshotCodec.decode(
                    allocation.actualStackSnapshot());
            if (actual.getCount() != allocation.count()) {
                return false;
            }
            actual.setCount(1);
            return Arrays.equals(expectedTemplate,
                    ItemStackSnapshotCodec.encode(actual));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    static String quoteFingerprint(CanonicalInput input) {
        String material = "futureshops server shop sell quote v1\u0000"
                + input.requestId() + "\u0000" + input.playerId()
                + "\u0000" + input.shopId() + "\u0000"
                + input.listingId() + "\u0000" + input.itemId()
                + "\u0000" + input.quantity() + "\u0000"
                + input.unitPriceMinorUnits() + "\u0000"
                + input.quoteRevision() + "\u0000"
                + input.expectedStockRevision() + "\u0000"
                + input.quoteCreatedAt() + "\u0000"
                + input.walletBeforeMinorUnits() + "\u0000"
                + input.debtBeforeMinorUnits() + "\u0000"
                + input.reservedBeforeMinorUnits() + "\u0000"
                + input.walletBalanceLimitMinorUnits() + "\u0000"
                + input.configurationGeneration() + "\u0000"
                + input.currencyName() + "\u0000"
                + input.currencyDecimals() + "\u0000"
                + sha256(input.exactItemTemplate()) + "\u0000"
                + input.shopReference().dimensionId() + "\u0000"
                + input.shopReference().blockX() + "\u0000"
                + input.shopReference().blockY() + "\u0000"
                + input.shopReference().blockZ();
        return sha256(material.getBytes(StandardCharsets.UTF_8));
    }

    private static EscrowTransaction transaction(
            CanonicalInput input,
            long payout,
            long accepted,
            long debtCredit,
            long walletCredit,
            long overflow
    ) {
        EscrowParty player = EscrowParty.player(input.playerId());
        EscrowParty shop = EscrowParty.shop(input.shopId());
        List<EscrowAssetLot> assets = new ArrayList<>();
        List<ItemInventoryAllocation> portions =
                input.itemCustodyReceipt().actualPortions();
        for (int index = 0; index < portions.size(); index++) {
            ItemInventoryAllocation portion = portions.get(index);
            assets.add(new EscrowAssetLot(
                    itemLotId(input.requestId(), index),
                    EscrowAssetLotType.ITEM_STACK,
                    EscrowProtectionLevel.PROTECTED,
                    player, shop, portion.count(), Optional.empty(),
                    portion.actualStackSnapshot(), Map.ofEntries(
                    Map.entry("allocation_index", Integer.toString(index)),
                    Map.entry("entry_id", portion.entryId().toString()),
                    Map.entry("inventory_slot", Integer.toString(
                            portion.slot().serializedSlot())),
                    Map.entry("item_id", input.itemId()),
                    Map.entry("listing_id", input.listingId()),
                    Map.entry("quote_revision", Long.toString(
                            input.quoteRevision())),
                    Map.entry("snapshot_fingerprint", sha256(
                            portion.actualStackSnapshot())),
                    Map.entry("stock_revision", Long.toString(
                            input.expectedStockRevision())))));
        }
        String quoteFingerprint = quoteFingerprint(input);
        assets.add(new EscrowAssetLot(moneyLotId(input.requestId()),
                EscrowAssetLotType.WALLET_MONEY,
                EscrowProtectionLevel.PROTECTED, shop, player, 1L,
                Optional.of(new MoneyAmount(CURRENCY_ID, payout)),
                new byte[0], Map.ofEntries(
                Map.entry("accepted_payout", Long.toString(accepted)),
                Map.entry("configuration_generation", Long.toString(
                        input.configurationGeneration())),
                Map.entry("currency_decimals", Integer.toString(
                        input.currencyDecimals())),
                Map.entry("currency_name", input.currencyName()),
                Map.entry("debt_before", Long.toString(
                        input.debtBeforeMinorUnits())),
                Map.entry("debt_credit", Long.toString(debtCredit)),
                Map.entry("exact_item_fingerprint", sha256(
                        input.exactItemTemplate())),
                Map.entry("item_custody_digest", HexFormat.of().formatHex(
                        input.itemCustodyReceipt().digest())),
                Map.entry("item_id", input.itemId()),
                Map.entry("listing_id", input.listingId()),
                Map.entry("overflow_claim", Long.toString(overflow)),
                Map.entry("quote_created_at",
                        input.quoteCreatedAt().toString()),
                Map.entry("quote_fingerprint", quoteFingerprint),
                Map.entry("quote_revision", Long.toString(
                        input.quoteRevision())),
                Map.entry("reserved_before", Long.toString(
                        input.reservedBeforeMinorUnits())),
                Map.entry("shop_id", input.shopId()),
                Map.entry("stock_revision", Long.toString(
                        input.expectedStockRevision())),
                Map.entry("unit_price", Long.toString(
                        input.unitPriceMinorUnits())),
                Map.entry("wallet_before", Long.toString(
                        input.walletBeforeMinorUnits())),
                Map.entry("wallet_credit", Long.toString(walletCredit)),
                Map.entry("wallet_limit", Long.toString(
                        input.walletBalanceLimitMinorUnits())),
                Map.entry("wire_fingerprint", wireFingerprint(
                        input.requestId(), input.playerId(), input.shopId(),
                        input.listingId(), input.quantity())))));
        Set<EscrowParticipant> participants = Set.of(
                new EscrowParticipant(player, Set.of(
                        EscrowParticipantRole.INITIATOR,
                        EscrowParticipantRole.SELLER,
                        EscrowParticipantRole.BENEFICIARY,
                        EscrowParticipantRole.RECIPIENT)),
                new EscrowParticipant(shop, Set.of(
                        EscrowParticipantRole.BUYER,
                        EscrowParticipantRole.PAYER,
                        EscrowParticipantRole.CUSTODIAN)));
        String requestKey = "server.shop.sell." + input.requestId() + "."
                + sha256((quoteFingerprint + "\u0000"
                + HexFormat.of().formatHex(
                input.itemCustodyReceipt().digest())).getBytes(
                StandardCharsets.UTF_8));
        Instant now = input.itemCustodyReceipt().appliedAt();
        EscrowTransaction created = EscrowTransaction.create(
                new EscrowTransactionId(input.requestId()),
                Optional.empty(), new EscrowRequestKey(requestKey),
                EscrowOperation.SERVER_SHOP_SELL, participants, assets,
                now, input.quoteRevision(),
                Optional.of(input.shopReference()));
        return created.transitionTo(EscrowState.VALIDATED, now)
                .transitionTo(EscrowState.HOLDING, now)
                .transitionTo(EscrowState.HELD, now)
                .transitionTo(EscrowState.COMMIT_DECIDED, now)
                .transitionTo(EscrowState.COMMITTED, now)
                .transitionTo(EscrowState.CLAIMS_CREATED, now)
                .transitionTo(EscrowState.COMPLETED, now);
    }

    private static LedgerTransaction ledger(
            CanonicalInput input,
            long payout,
            long debtCredit,
            long walletCredit,
            long overflow
    ) {
        List<LedgerLeg> legs = new ArrayList<>();
        legs.add(new LedgerLeg(sourceAccount(input.shopId()),
                Math.negateExact(payout)));
        if (debtCredit > 0L) {
            legs.add(new LedgerLeg(debtAccount(input.playerId()),
                    debtCredit));
        }
        if (walletCredit > 0L) {
            legs.add(new LedgerLeg(walletAccount(input.playerId()),
                    walletCredit));
        }
        if (overflow > 0L) {
            legs.add(new LedgerLeg(new LedgerAccountId(
                    LedgerAccountType.PLAYER_CLAIM,
                    overflowClaimId(input.requestId()).toString()),
                    overflow));
        }
        return new LedgerTransaction(input.requestId(),
                ledgerIdempotencyKey(input.requestId()), LEDGER_REASON,
                legs);
    }

    private static StockMutationCommand.ReserveBatch stockReserve(
            CanonicalInput input
    ) {
        StockReservationRequest reservation =
                new StockReservationRequest(
                        new StockKey(input.shopId(), input.listingId()),
                        StockReservationDirection.INBOUND,
                        input.quantity(), input.expectedStockRevision());
        return new StockMutationCommand.ReserveBatch(
                stockReserveRequestId(input.requestId()),
                input.requestId(), List.of(reservation),
                input.itemCustodyReceipt().appliedAt());
    }

    private static StockMutationCommand.ResolveBatch stockCommit(
            CanonicalInput input
    ) {
        StockReservationId reservationId =
                StockReservationId.forTransaction(input.requestId(),
                        new StockKey(input.shopId(), input.listingId()),
                        StockReservationDirection.INBOUND);
        return new StockMutationCommand.ResolveBatch(
                stockCommitRequestId(input.requestId()),
                StockMutationType.COMMIT_BATCH, input.requestId(),
                List.of(new StockReservationResolution(
                        reservationId, 0L)),
                input.itemCustodyReceipt().appliedAt());
    }

    private static Optional<EscrowClaim> overflowClaim(
            CanonicalInput input,
            long overflow
    ) {
        if (overflow == 0L) {
            return Optional.empty();
        }
        Instant now = input.itemCustodyReceipt().appliedAt();
        return Optional.of(new EscrowClaim(
                overflowClaimId(input.requestId()), input.requestId(),
                input.playerId(),
                overflowClaimSourceKey(input.requestId()), ClaimKind.MONEY,
                overflow, overflow, new byte[0], ClaimStatus.PENDING,
                CLAIM_LABEL, now, now));
    }

    private static long payout(long unitPrice, int quantity) {
        if (unitPrice <= 0L) {
            throw new IllegalArgumentException(
                    "Server shop sell unit price is invalid");
        }
        return requirePayout(Math.multiplyExact(
                unitPrice, requireQuantity(quantity)));
    }

    private static long requirePayout(long payout) {
        if (payout <= 0L) {
            throw new IllegalArgumentException(
                    "Server shop sell payout is invalid");
        }
        return payout;
    }

    private static int requireQuantity(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "Server shop sell quantity is invalid");
        }
        return quantity;
    }

    static long requireRevision(long revision, String label) {
        if (revision < 0L || revision > MAX_REVISION) {
            throw new IllegalArgumentException(
                    "Server shop sell " + label + " is invalid");
        }
        return revision;
    }

    static String normalizeCurrencyName(String value) {
        String normalized = Objects.requireNonNull(
                value, "currencyName").strip();
        if (normalized.isEmpty()
                || normalized.length() > MAX_CURRENCY_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    "Server shop sell currency name is invalid");
        }
        return normalized;
    }

    static String requireIdentifier(String value, String label) {
        String normalized = Objects.requireNonNull(value, label).strip();
        if (normalized.isEmpty()
                || normalized.length() > MAX_IDENTIFIER_LENGTH) {
            throw new IllegalArgumentException(
                    "Server shop sell " + label + " is invalid");
        }
        return normalized;
    }

    static UUID requireUuid(UUID value, String label) {
        UUID result = Objects.requireNonNull(value, label);
        if (ZERO_UUID.equals(result)) {
            throw new IllegalArgumentException(
                    "Server shop sell " + label + " is invalid");
        }
        return result;
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
        String material = "futureshops server shop sell v1\u0000"
                + Objects.requireNonNull(purpose, "purpose") + "\u0000"
                + requireUuid(requestId, "requestId") + "\u0000"
                + Objects.requireNonNull(suffix, "suffix");
        return UUID.nameUUIDFromBytes(
                material.getBytes(StandardCharsets.UTF_8));
    }

    record CanonicalInput(
            UUID requestId,
            UUID playerId,
            String shopId,
            String listingId,
            String itemId,
            int quantity,
            long unitPriceMinorUnits,
            long quoteRevision,
            long expectedStockRevision,
            Instant quoteCreatedAt,
            long walletBeforeMinorUnits,
            long debtBeforeMinorUnits,
            long reservedBeforeMinorUnits,
            long walletBalanceLimitMinorUnits,
            long configurationGeneration,
            String currencyName,
            int currencyDecimals,
            byte[] exactItemTemplate,
            ItemInventoryMutationReceipt itemCustodyReceipt,
            DimensionAwareShopReference shopReference
    ) {
        CanonicalInput {
            requestId = requireUuid(requestId, "requestId");
            playerId = requireUuid(playerId, "playerId");
            shopId = requireIdentifier(shopId, "shopId");
            listingId = requireIdentifier(listingId, "listingId");
            itemId = requireIdentifier(itemId, "itemId");
            requireQuantity(quantity);
            payout(unitPriceMinorUnits, quantity);
            requireRevision(quoteRevision, "quote revision");
            requireRevision(expectedStockRevision,
                    "expected stock revision");
            quoteCreatedAt = Objects.requireNonNull(
                    quoteCreatedAt, "quoteCreatedAt");
            requireWalletSnapshot(walletBeforeMinorUnits,
                    debtBeforeMinorUnits, reservedBeforeMinorUnits,
                    walletBalanceLimitMinorUnits);
            if (configurationGeneration < 0L
                    || currencyDecimals < 0 || currencyDecimals > 6) {
                throw new IllegalArgumentException(
                        "Server shop sell currency policy is invalid");
            }
            currencyName = normalizeCurrencyName(currencyName);
            exactItemTemplate = Objects.requireNonNull(
                    exactItemTemplate, "exactItemTemplate").clone();
            requireExactTemplate(exactItemTemplate, itemId);
            itemCustodyReceipt = Objects.requireNonNull(
                    itemCustodyReceipt, "itemCustodyReceipt");
            shopReference = Objects.requireNonNull(
                    shopReference, "shopReference");
            if (!shopReference.shopId().equals(shopId)
                    || quoteCreatedAt.isAfter(
                    itemCustodyReceipt.appliedAt())) {
                throw new IllegalArgumentException(
                        "Server shop sell quote context is invalid");
            }
        }

        @Override
        public byte[] exactItemTemplate() {
            return exactItemTemplate.clone();
        }
    }

    record CanonicalComponents(
            EscrowTransaction transaction,
            LedgerTransaction ledger,
            StockMutationCommand.ReserveBatch reserve,
            StockMutationCommand.ResolveBatch commit,
            Optional<EscrowClaim> claim
    ) {
    }

    record ServerShopSellCommitView(
            UUID requestId,
            UUID playerId,
            String shopId,
            String listingId,
            String itemId,
            int quantity,
            long unitPriceMinorUnits,
            long quoteRevision,
            long expectedStockRevision,
            Instant quoteCreatedAt,
            long walletBeforeMinorUnits,
            long debtBeforeMinorUnits,
            long reservedBeforeMinorUnits,
            long walletBalanceLimitMinorUnits,
            long configurationGeneration,
            String currencyName,
            int currencyDecimals,
            byte[] exactItemTemplate,
            ItemInventoryMutationReceipt itemCustodyReceipt,
            EscrowTransaction completedTransaction,
            LedgerTransaction ledgerTransaction,
            StockMutationCommand.ReserveBatch stockReservation,
            StockMutationCommand.ResolveBatch stockCommit,
            Optional<EscrowClaim> overflowClaim
    ) {
        ServerShopSellCommitView {
            exactItemTemplate = exactItemTemplate.clone();
        }

        @Override
        public byte[] exactItemTemplate() {
            return exactItemTemplate.clone();
        }

        CanonicalInput canonicalInput() {
            return new CanonicalInput(requestId, playerId, shopId,
                    listingId, itemId, quantity, unitPriceMinorUnits,
                    quoteRevision, expectedStockRevision, quoteCreatedAt,
                    walletBeforeMinorUnits, debtBeforeMinorUnits,
                    reservedBeforeMinorUnits, walletBalanceLimitMinorUnits,
                    configurationGeneration, currencyName,
                    currencyDecimals, exactItemTemplate,
                    itemCustodyReceipt,
                    completedTransaction.shopReference().orElseThrow());
        }
    }
}

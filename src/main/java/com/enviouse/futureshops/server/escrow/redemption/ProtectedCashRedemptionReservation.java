package com.enviouse.futureshops.server.escrow.redemption;

import com.enviouse.futureshops.money.InternalBillInventoryPlanner;
import com.enviouse.futureshops.server.escrow.custody.CustodyAdapterCapability;
import com.enviouse.futureshops.server.escrow.custody.CustodyAssetType;
import com.enviouse.futureshops.server.escrow.custody.CustodyItemSnapshot;
import com.enviouse.futureshops.server.escrow.custody.CustodyLot;
import com.enviouse.futureshops.server.escrow.custody.CustodyLotState;
import com.enviouse.futureshops.server.escrow.custody.CustodyMutation;
import com.enviouse.futureshops.server.escrow.custody.CustodyMutationCodec;
import com.enviouse.futureshops.server.escrow.custody.CustodyOperation;
import com.enviouse.futureshops.server.escrow.custody.CustodyProtectionTier;
import com.enviouse.futureshops.server.escrow.custody.ProtectedCurrencyProvenance;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountId;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountType;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintJournalEvent;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintOperation;
import com.enviouse.futureshops.server.escrow.model.EscrowAssetLot;
import com.enviouse.futureshops.server.escrow.model.EscrowAssetLotType;
import com.enviouse.futureshops.server.escrow.model.EscrowParticipantRole;
import com.enviouse.futureshops.server.escrow.model.EscrowParty;
import com.enviouse.futureshops.server.escrow.model.EscrowProtectionLevel;
import com.enviouse.futureshops.server.escrow.model.EscrowOperation;
import com.enviouse.futureshops.server.escrow.model.EscrowState;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;
import com.enviouse.futureshops.server.escrow.model.CashDepositMode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record ProtectedCashRedemptionReservation(
        UUID reservationId,
        UUID playerId,
        LedgerAccountId destinationAccount,
        long walletBalanceLimitMinorUnits,
        CashDepositMode depositMode,
        byte[] inventoryBeforeHash,
        InternalBillInventoryPlanner.ExactPlan plan,
        EscrowTransaction heldTransaction,
        List<CustodyMutation> custodyReservations,
        List<ProtectedMintJournalEvent> mintReservations
) {
    public static final int MAX_PORTIONS =
            ProtectedCashRedemptionSupport.MAX_PORTIONS;
    public static final int MAX_MINT_BATCHES =
            ProtectedCashRedemptionSupport.MAX_BATCHES;
    public static final int MAX_TOTAL_SNAPSHOT_BYTES =
            ProtectedCashRedemptionSupport.MAX_TOTAL_SNAPSHOT_BYTES;
    public static final String CURRENCY_ID =
            ProtectedCashRedemptionSupport.CURRENCY_ID;

    public ProtectedCashRedemptionReservation(
            UUID reservationId,
            UUID playerId,
            LedgerAccountId destinationAccount,
            long walletBalanceLimitMinorUnits,
            byte[] inventoryBeforeHash,
            InternalBillInventoryPlanner.ExactPlan plan,
            EscrowTransaction heldTransaction,
            List<CustodyMutation> custodyReservations,
            List<ProtectedMintJournalEvent> mintReservations
    ) {
        this(reservationId, playerId, destinationAccount,
                walletBalanceLimitMinorUnits,
                CashDepositMode.PUBLIC_WALLET, inventoryBeforeHash, plan,
                heldTransaction, custodyReservations, mintReservations);
    }

    public ProtectedCashRedemptionReservation {
        Objects.requireNonNull(reservationId, "reservationId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(destinationAccount, "destinationAccount");
        Objects.requireNonNull(depositMode, "depositMode");
        ProtectedCashRedemptionSupport.requireHash(inventoryBeforeHash,
                "Protected cash reservation inventory hash");
        inventoryBeforeHash = inventoryBeforeHash.clone();
        if (walletBalanceLimitMinorUnits < 0L
                || (destinationAccount.type()
                != LedgerAccountType.PLAYER_WALLET
                && walletBalanceLimitMinorUnits != 0L)) {
            throw new IllegalArgumentException(
                    "Protected cash wallet balance limit is invalid");
        }
        plan = ProtectedCashRedemptionSupport.canonicalPlan(plan);
        Objects.requireNonNull(heldTransaction, "heldTransaction");
        Objects.requireNonNull(custodyReservations, "custodyReservations");
        Objects.requireNonNull(mintReservations, "mintReservations");
        requireHeldTransaction(heldTransaction, playerId, destinationAccount);
        UUID expectedReservationId = reservationId(
                playerId, destinationAccount, walletBalanceLimitMinorUnits,
                depositMode, inventoryBeforeHash, heldTransaction, plan);
        boolean legacyPublicIdentity = depositMode
                == CashDepositMode.PUBLIC_WALLET
                && reservationId.equals(
                ProtectedCashRedemptionSupport.legacyReservationId(
                        playerId, destinationAccount,
                        walletBalanceLimitMinorUnits, inventoryBeforeHash,
                        heldTransaction, plan));
        if (!reservationId.equals(expectedReservationId)
                && !legacyPublicIdentity) {
            throw new IllegalArgumentException(
                    "Protected cash reservation identity is invalid");
        }
        ProtectedCashRedemptionSupport.PlanFacts facts =
                ProtectedCashRedemptionSupport.analyze(plan);
        custodyReservations = canonicalCustody(custodyReservations,
                playerId, destinationAccount, heldTransaction, plan, facts);
        mintReservations = canonicalMints(mintReservations,
                destinationAccount, heldTransaction, facts);
        requireEscrowAssets(heldTransaction, playerId, reservationId,
                destinationAccount, walletBalanceLimitMinorUnits, depositMode,
                inventoryBeforeHash, plan, custodyReservations,
                legacyPublicIdentity);
    }

    public UUID transactionId() {
        return heldTransaction.transactionId().value();
    }

    public long amountMinorUnits() {
        return plan.requestedMinorUnits();
    }

    public String fingerprint() {
        return ProtectedCashRedemptionReservationCodec.fingerprint(this);
    }

    public static UUID reservationId(
            UUID playerId,
            LedgerAccountId destinationAccount,
            long walletBalanceLimitMinorUnits,
            CashDepositMode depositMode,
            byte[] inventoryBeforeHash,
            EscrowTransaction heldTransaction,
            InternalBillInventoryPlanner.ExactPlan plan
    ) {
        return ProtectedCashRedemptionSupport.reservationId(
                playerId, destinationAccount, walletBalanceLimitMinorUnits,
                depositMode, inventoryBeforeHash, heldTransaction, plan);
    }

    public static UUID reservationId(
            UUID playerId,
            LedgerAccountId destinationAccount,
            long walletBalanceLimitMinorUnits,
            byte[] inventoryBeforeHash,
            EscrowTransaction heldTransaction,
            InternalBillInventoryPlanner.ExactPlan plan
    ) {
        return reservationId(playerId, destinationAccount,
                walletBalanceLimitMinorUnits,
                CashDepositMode.PUBLIC_WALLET, inventoryBeforeHash,
                heldTransaction, plan);
    }

    public static UUID custodyLotId(
            UUID transactionId,
            InternalBillInventoryPlanner.Portion portion
    ) {
        return ProtectedCashRedemptionSupport.lotId(transactionId, portion);
    }

    public static String custodyReserveRequestKey(
            UUID transactionId,
            LedgerAccountId destinationAccount,
            UUID lotId
    ) {
        return ProtectedCashRedemptionSupport.reserveLotRequestKey(
                transactionId, destinationAccount, lotId);
    }

    public static String mintReserveRequestKey(
            UUID transactionId,
            LedgerAccountId destinationAccount,
            UUID batchId
    ) {
        return ProtectedCashRedemptionSupport.reserveMintRequestKey(
                transactionId, destinationAccount, batchId);
    }

    public static Map<String, String> assetAttributes(
            InternalBillInventoryPlanner.Portion portion,
            LedgerAccountId destinationAccount,
            long walletBalanceLimitMinorUnits,
            CashDepositMode depositMode,
            byte[] inventoryBeforeHash
    ) {
        Objects.requireNonNull(portion, "portion");
        Objects.requireNonNull(destinationAccount, "destinationAccount");
        Objects.requireNonNull(depositMode, "depositMode");
        ProtectedCashRedemptionSupport.requireHash(inventoryBeforeHash,
                "Protected cash reservation inventory hash");
        return Map.of(
                "authority", "protected",
                "contract", ProtectedCashRedemptionSupport.fingerprint(
                        (destinationAccount.type().name() + "\u0000"
                                + destinationAccount.ownerKey() + "\u0000"
                                + walletBalanceLimitMinorUnits + "\u0000"
                                + ProtectedCashRedemptionSupport.hex(
                                inventoryBeforeHash)).getBytes(
                                java.nio.charset.StandardCharsets.UTF_8)),
                "mint_id", portion.mintId(),
                "selected_count", Integer.toString(portion.selectedCount()),
                "slot", portion.slot().container().name() + "."
                        + portion.slot().index(),
                "deposit_mode", depositMode.name());
    }

    static Map<String, String> legacyAssetAttributes(
            InternalBillInventoryPlanner.Portion portion,
            LedgerAccountId destinationAccount,
            long walletBalanceLimitMinorUnits,
            byte[] inventoryBeforeHash
    ) {
        Objects.requireNonNull(portion, "portion");
        Objects.requireNonNull(destinationAccount, "destinationAccount");
        ProtectedCashRedemptionSupport.requireHash(inventoryBeforeHash,
                "Protected cash reservation inventory hash");
        return Map.of(
                "authority", "protected",
                "contract", ProtectedCashRedemptionSupport.fingerprint(
                        (destinationAccount.type().name() + "\u0000"
                                + destinationAccount.ownerKey() + "\u0000"
                                + walletBalanceLimitMinorUnits + "\u0000"
                                + ProtectedCashRedemptionSupport.hex(
                                inventoryBeforeHash)).getBytes(
                                java.nio.charset.StandardCharsets.UTF_8)),
                "mint_id", portion.mintId(),
                "selected_count", Integer.toString(portion.selectedCount()),
                "slot", portion.slot().container().name() + "."
                        + portion.slot().index());
    }

    public static Map<String, String> assetAttributes(
            InternalBillInventoryPlanner.Portion portion,
            LedgerAccountId destinationAccount,
            long walletBalanceLimitMinorUnits,
            byte[] inventoryBeforeHash
    ) {
        return assetAttributes(portion, destinationAccount,
                walletBalanceLimitMinorUnits,
                CashDepositMode.PUBLIC_WALLET, inventoryBeforeHash);
    }

    private static void requireHeldTransaction(
            EscrowTransaction transaction,
            UUID playerId,
            LedgerAccountId destination
    ) {
        if (transaction.state() != EscrowState.HELD) {
            throw new IllegalArgumentException(
                    "Protected cash reservation requires a held transaction");
        }
        EscrowParty player = EscrowParty.player(playerId);
        var playerParticipant = transaction.participants().stream()
                .filter(value -> value.party().equals(player))
                .findFirst().orElseThrow(() -> new IllegalArgumentException(
                        "Protected cash payer identity is invalid"));
        if (!playerParticipant.hasRole(EscrowParticipantRole.PAYER)) {
            throw new IllegalArgumentException(
                    "Protected cash payer identity is invalid");
        }
        boolean protectedCustodian = transaction.participants().stream()
                .anyMatch(value -> value.party().equals(
                        EscrowParty.system("protected_currency"))
                        && value.hasRole(EscrowParticipantRole.CUSTODIAN));
        if (!protectedCustodian) {
            throw new IllegalArgumentException(
                    "Protected cash custodian identity is invalid");
        }
        boolean validDestination = switch (destination.type()) {
            case PLAYER_WALLET -> transaction.operation()
                    == EscrowOperation.CURRENCY_DEPOSIT
                    && destination.ownerKey().equals(playerId.toString())
                    && playerParticipant.hasRole(
                    EscrowParticipantRole.BENEFICIARY);
            case SERVER_SHOP_SINK -> isServerShopPayment(
                    transaction.operation())
                    && destination.ownerKey().equals("system")
                    && transaction.participants().stream().anyMatch(value ->
                    value.party().equals(EscrowParty.system("server_shop"))
                            && value.hasRole(
                            EscrowParticipantRole.BENEFICIARY));
            case TRANSACTION_ESCROW -> isEscrowPayment(
                    transaction.operation())
                    && destination.ownerKey().equals(
                    transaction.transactionId().value().toString())
                    && transaction.participants().stream().anyMatch(value ->
                    !value.party().equals(player)
                            && !value.party().equals(EscrowParty.system(
                            "protected_currency"))
                            && value.hasRole(
                            EscrowParticipantRole.BENEFICIARY));
            default -> false;
        };
        if (!validDestination) {
            throw new IllegalArgumentException(
                    "Protected cash destination policy is invalid");
        }
    }

    private static boolean isServerShopPayment(
            EscrowOperation operation
    ) {
        return operation == EscrowOperation.SERVER_SHOP_BUY
                || operation == EscrowOperation.SERVER_SHOP_CART;
    }

    private static boolean isEscrowPayment(
            EscrowOperation operation
    ) {
        return switch (operation) {
            case SERVER_SHOP_BUY, SERVER_SHOP_BARTER, SERVER_SHOP_CART,
                    PLAYER_SHOP_BUY, PLAYER_SHOP_BARTER,
                    PLAYER_SHOP_COMPOUND, PLAYER_PAYMENT, AUCTION_BID,
                    AUCTION_BUY_NOW, BAZAAR_BUY_ORDER, BAZAAR_FILL -> true;
            default -> false;
        };
    }

    private static List<CustodyMutation> canonicalCustody(
            List<CustodyMutation> supplied,
            UUID playerId,
            LedgerAccountId destinationAccount,
            EscrowTransaction transaction,
            InternalBillInventoryPlanner.ExactPlan plan,
            ProtectedCashRedemptionSupport.PlanFacts facts
    ) {
        if (supplied.size() != plan.portions().size()
                || supplied.isEmpty()
                || supplied.size() > MAX_PORTIONS) {
            throw new IllegalArgumentException(
                    "Protected cash custody reservation count is invalid");
        }
        Map<UUID, CustodyMutation> byLot = new HashMap<>();
        for (CustodyMutation mutation : supplied) {
            Objects.requireNonNull(mutation, "custodyReservation");
            if (byLot.put(mutation.resultingLot().lotId(), mutation) != null) {
                throw new IllegalArgumentException(
                        "Protected cash custody lot is duplicated");
            }
        }
        List<CustodyMutation> ordered = new ArrayList<>(supplied.size());
        for (InternalBillInventoryPlanner.Portion portion : plan.portions()) {
            UUID expectedLotId = custodyLotId(
                    transaction.transactionId().value(), portion);
            CustodyMutation mutation = byLot.remove(expectedLotId);
            if (mutation == null) {
                throw new IllegalArgumentException(
                        "Protected cash custody plan is incomplete");
            }
            requireCustodyMutation(mutation, playerId, destinationAccount,
                    transaction,
                    portion, facts.snapshots().get(portion.slot()));
            ordered.add(mutation);
        }
        if (!byLot.isEmpty()) {
            throw new IllegalArgumentException(
                    "Protected cash custody plan has an unexpected lot");
        }
        return List.copyOf(ordered);
    }

    private static void requireCustodyMutation(
            CustodyMutation mutation,
            UUID playerId,
            LedgerAccountId destinationAccount,
            EscrowTransaction transaction,
            InternalBillInventoryPlanner.Portion portion,
            ProtectedCashRedemptionSupport.BillSnapshot bill
    ) {
        UUID transactionId = transaction.transactionId().value();
        CustodyLot lot = mutation.resultingLot();
        String requestKey = custodyReserveRequestKey(
                transactionId, destinationAccount, lot.lotId());
        long units = portion.valueMinorUnits();
        if (!lot.lotId().equals(custodyLotId(transactionId, portion))
                || !lot.transactionId().equals(transactionId)
                || !lot.reserveRequestKey().equals(requestKey)
                || lot.assetType()
                != CustodyAssetType.PROTECTED_PHYSICAL_CURRENCY
                || lot.protectionTier() != CustodyProtectionTier.PROTECTED
                || lot.sourceCapability()
                != CustodyAdapterCapability.RECONCILABLE
                || lot.state() != CustodyLotState.HELD
                || lot.units() != units
                || !lot.currencyProvider().equals(
                CustodyLot.BUILT_IN_CURRENCY_PROVIDER)
                || lot.revision() != 0L
                || mutation.receipt().operation() != CustodyOperation.RESERVE
                || !mutation.receipt().requestKey().equals(requestKey)
                || !mutation.receipt().transactionId().equals(transactionId)
                || !mutation.receipt().lotId().equals(lot.lotId())) {
            throw new IllegalArgumentException(
                    "Protected cash custody reservation identity is invalid");
        }
        requireTime(lot.createdAt(), transaction.timestamps().createdAt(),
                transaction.timestamps().updatedAt(),
                "Protected cash custody reservation time");
        if (!lot.createdAt().equals(lot.updatedAt())
                || !mutation.receipt().createdAt().equals(lot.createdAt())) {
            throw new IllegalArgumentException(
                    "Protected cash custody reservation timeline is invalid");
        }
        if (!lot.holdEvidence().source().ownerKey().equals(playerId.toString())
                || lot.holdEvidence().source().capability()
                != CustodyAdapterCapability.RECONCILABLE
                || !lot.holdEvidence().destination().ownerKey().equals(
                transactionId.toString())
                || lot.holdEvidence().destination().capability()
                != CustodyAdapterCapability.TRANSACTIONAL_PROTECTED) {
            throw new IllegalArgumentException(
                    "Protected cash custody endpoints are invalid");
        }
        if (lot.itemSnapshots().size() != 1
                || lot.protectedProvenance().size() != 1) {
            throw new IllegalArgumentException(
                    "Protected cash custody reservation shape is invalid");
        }
        CustodyItemSnapshot snapshot = lot.itemSnapshots().get(0);
        if (!snapshot.registryId().equals(bill.registryId())
                || snapshot.count() != portion.selectedCount()
                || !Arrays.equals(snapshot.serializedNbt(),
                portion.exactStackSnapshot())) {
            throw new IllegalArgumentException(
                    "Protected cash custody snapshot does not match its plan");
        }
        ProtectedCurrencyProvenance provenance =
                lot.protectedProvenance().get(0);
        if (!provenance.mintId().equals(bill.mintId())
                || provenance.denominationMinorUnits()
                != portion.denominationMinorUnits()
                || provenance.authorizedCount() != portion.authorizedCount()
                || provenance.billCount() != portion.selectedCount()
                || !provenance.serverIdentityEvidence().equals(
                bill.serverIdentityEvidence())
                || !provenance.checksumEvidence().equals(
                bill.checksumEvidence())) {
            throw new IllegalArgumentException(
                    "Protected cash custody provenance does not match its plan");
        }
    }

    private static List<ProtectedMintJournalEvent> canonicalMints(
            List<ProtectedMintJournalEvent> supplied,
            LedgerAccountId destinationAccount,
            EscrowTransaction transaction,
            ProtectedCashRedemptionSupport.PlanFacts facts
    ) {
        if (supplied.size() != facts.batches().size()
                || supplied.isEmpty()
                || supplied.size() > MAX_MINT_BATCHES) {
            throw new IllegalArgumentException(
                    "Protected cash mint reservation count is invalid");
        }
        Map<UUID, ProtectedMintJournalEvent> byBatch = new HashMap<>();
        Set<String> requestKeys = new HashSet<>();
        for (ProtectedMintJournalEvent event : supplied) {
            Objects.requireNonNull(event, "mintReservation");
            UUID batchId = event.targetBatchId().orElseThrow(() ->
                    new IllegalArgumentException(
                            "Protected cash mint reservation lacks a batch"));
            if (byBatch.put(batchId, event) != null
                    || !requestKeys.add(event.requestKey())) {
                throw new IllegalArgumentException(
                        "Protected cash mint reservation is duplicated");
            }
        }
        List<UUID> batchIds = new ArrayList<>(facts.batches().keySet());
        batchIds.sort(Comparator.comparing(UUID::toString));
        List<ProtectedMintJournalEvent> ordered = new ArrayList<>(batchIds.size());
        for (UUID batchId : batchIds) {
            ProtectedMintJournalEvent event = byBatch.remove(batchId);
            ProtectedCashRedemptionSupport.BatchFacts batch =
                    facts.batches().get(batchId);
            if (event == null
                    || event.operation() != ProtectedMintOperation.RESERVE
                    || !event.transactionId().equals(
                    transaction.transactionId().value())
                    || !event.requestKey().equals(mintReserveRequestKey(
                    transaction.transactionId().value(), destinationAccount,
                    batchId))
                    || event.quantity() != batch.selectedCount()
                    || event.batch().isPresent()) {
                throw new IllegalArgumentException(
                        "Protected cash mint reservation is invalid");
            }
            requireTime(event.occurredAt(), transaction.timestamps().createdAt(),
                    transaction.timestamps().updatedAt(),
                    "Protected cash mint reservation time");
            ordered.add(event);
        }
        if (!byBatch.isEmpty()) {
            throw new IllegalArgumentException(
                    "Protected cash mint reservation has an unexpected batch");
        }
        return List.copyOf(ordered);
    }

    private static void requireEscrowAssets(
            EscrowTransaction transaction,
            UUID playerId,
            UUID reservationId,
            LedgerAccountId destinationAccount,
            long walletBalanceLimitMinorUnits,
            CashDepositMode depositMode,
            byte[] inventoryBeforeHash,
            InternalBillInventoryPlanner.ExactPlan plan,
            List<CustodyMutation> custody,
            boolean legacyPublicIdentity
    ) {
        Map<UUID, EscrowAssetLot> cashLots = new LinkedHashMap<>();
        for (EscrowAssetLot lot : transaction.assetLots()) {
            if (lot.type() == EscrowAssetLotType.PROTECTED_PHYSICAL_CURRENCY) {
                if (cashLots.put(lot.lotId(), lot) != null) {
                    throw new IllegalArgumentException(
                            "Protected cash escrow asset is duplicated");
                }
            }
        }
        if (cashLots.size() != custody.size()) {
            throw new IllegalArgumentException(
                    "Protected cash escrow assets are incomplete");
        }
        EscrowParty player = EscrowParty.player(playerId);
        for (int index = 0; index < plan.portions().size(); index++) {
            InternalBillInventoryPlanner.Portion portion =
                    plan.portions().get(index);
            CustodyMutation mutation = custody.get(index);
            EscrowAssetLot asset = cashLots.remove(
                    mutation.resultingLot().lotId());
            if (asset == null
                    || asset.protectionLevel()
                    != EscrowProtectionLevel.PROTECTED
                    || !asset.source().equals(player)
                    || asset.destination().equals(player)
                    || asset.quantity() != portion.selectedCount()
                    || asset.money().isEmpty()
                    || !asset.money().orElseThrow().currencyId().equals(CURRENCY_ID)
                    || asset.money().orElseThrow().minorUnits()
                    != portion.valueMinorUnits()
                    || !Arrays.equals(asset.serializedPayload(),
                    CustodyMutationCodec.encode(mutation))
                    || !asset.attributes().equals(assetAttributes(portion,
                    destinationAccount, walletBalanceLimitMinorUnits,
                    depositMode, inventoryBeforeHash))
                    && (!legacyPublicIdentity
                    || !asset.attributes().equals(legacyAssetAttributes(
                    portion, destinationAccount,
                    walletBalanceLimitMinorUnits,
                    inventoryBeforeHash)))) {
                throw new IllegalArgumentException(
                        "Protected cash escrow asset does not match its custody lot");
            }
        }
        if (!cashLots.isEmpty()) {
            throw new IllegalArgumentException(
                    "Protected cash escrow has an unexpected cash asset");
        }
    }

    @Override
    public byte[] inventoryBeforeHash() {
        return inventoryBeforeHash.clone();
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof ProtectedCashRedemptionReservation other)) {
            return false;
        }
        return walletBalanceLimitMinorUnits
                == other.walletBalanceLimitMinorUnits
                && reservationId.equals(other.reservationId)
                && playerId.equals(other.playerId)
                && destinationAccount.equals(other.destinationAccount)
                && Arrays.equals(inventoryBeforeHash,
                other.inventoryBeforeHash)
                && plan.equals(other.plan)
                && heldTransaction.equals(other.heldTransaction)
                && custodyReservations.equals(other.custodyReservations)
                && mintReservations.equals(other.mintReservations);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(reservationId, playerId,
                destinationAccount, walletBalanceLimitMinorUnits, plan,
                heldTransaction, custodyReservations, mintReservations);
        return 31 * result + Arrays.hashCode(inventoryBeforeHash);
    }

    private static void requireTime(Instant value,
                                    Instant minimum,
                                    Instant maximum,
                                    String label) {
        if (value.isBefore(minimum) || value.isAfter(maximum)) {
            throw new IllegalArgumentException(label + " is invalid");
        }
    }
}

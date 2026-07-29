package com.enviouse.futureshops.server.escrow.redemption;

import com.enviouse.futureshops.money.InternalBillInventoryPlanner;
import com.enviouse.futureshops.money.ItemStackSnapshotCodec;
import com.enviouse.futureshops.server.escrow.claim.ClaimKind;
import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.custody.CustodyAdapterCapability;
import com.enviouse.futureshops.server.escrow.custody.CustodyLot;
import com.enviouse.futureshops.server.escrow.custody.CustodyLotState;
import com.enviouse.futureshops.server.escrow.custody.CustodyMutation;
import com.enviouse.futureshops.server.escrow.custody.CustodyOperation;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountId;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountType;
import com.enviouse.futureshops.server.escrow.ledger.LedgerLeg;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintJournalEvent;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintOperation;
import com.enviouse.futureshops.server.escrow.model.EscrowState;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;
import com.enviouse.futureshops.server.escrow.model.CashDepositMode;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public record ProtectedCashRedemptionSettlement(
        ProtectedCashRedemptionReservation reservation,
        EscrowTransaction completedTransaction,
        InventoryMutationReceipt inventoryMutation,
        List<CustodyMutation> custodyConsumptions,
        List<ProtectedMintJournalEvent> mintCommits,
        LedgerAccountId destinationAccount,
        long walletBalanceBeforeMinorUnits,
        long walletReservedBeforeMinorUnits,
        Optional<EscrowClaim> overflowClaim,
        LedgerTransaction ledgerTransaction
) {
    public static final String LEDGER_REASON =
            ProtectedCashRedemptionSupport.LEDGER_REASON;
    public static final String CURRENCY_SINK_OWNER = "protected_currency";
    public static final String OVERFLOW_CLAIM_LABEL =
            "Protected cash overflow";

    public ProtectedCashRedemptionSettlement {
        Objects.requireNonNull(reservation, "reservation");
        Objects.requireNonNull(completedTransaction, "completedTransaction");
        Objects.requireNonNull(inventoryMutation, "inventoryMutation");
        Objects.requireNonNull(custodyConsumptions, "custodyConsumptions");
        Objects.requireNonNull(mintCommits, "mintCommits");
        Objects.requireNonNull(destinationAccount, "destinationAccount");
        overflowClaim = Objects.requireNonNull(overflowClaim,
                "overflowClaim");
        Objects.requireNonNull(ledgerTransaction, "ledgerTransaction");
        if (walletReservedBeforeMinorUnits < 0L) {
            throw new IllegalArgumentException(
                    "Protected cash wallet balance snapshot is invalid");
        }
        requireCompletedTransaction(reservation, completedTransaction);
        requireInventoryMutation(reservation, completedTransaction,
                inventoryMutation);
        custodyConsumptions = canonicalCustody(reservation,
                completedTransaction, inventoryMutation, custodyConsumptions);
        mintCommits = canonicalMints(reservation, completedTransaction,
                inventoryMutation, mintCommits);
        requireLedger(reservation, completedTransaction, inventoryMutation,
                destinationAccount, walletBalanceBeforeMinorUnits,
                walletReservedBeforeMinorUnits, overflowClaim,
                ledgerTransaction);
    }

    public UUID transactionId() {
        return reservation.transactionId();
    }

    public long amountMinorUnits() {
        return reservation.amountMinorUnits();
    }

    public String fingerprint() {
        return ProtectedCashRedemptionSettlementCodec.fingerprint(this);
    }

    public static String inventoryMutationRequestKey(
            UUID transactionId,
            LedgerAccountId destinationAccount
    ) {
        return ProtectedCashRedemptionSupport.inventoryRequestKey(
                transactionId, destinationAccount);
    }

    public static String custodyConsumeRequestKey(UUID transactionId,
                                                  LedgerAccountId destinationAccount,
                                                  UUID lotId) {
        return ProtectedCashRedemptionSupport.consumeLotRequestKey(
                transactionId, destinationAccount, lotId);
    }

    public static String mintCommitRequestKey(UUID transactionId,
                                              LedgerAccountId destinationAccount,
                                              UUID batchId) {
        return ProtectedCashRedemptionSupport.commitMintRequestKey(
                transactionId, destinationAccount, batchId);
    }

    public static String ledgerIdempotencyKey(
            UUID transactionId,
            LedgerAccountId destinationAccount
    ) {
        return ProtectedCashRedemptionSupport.ledgerIdempotencyKey(
                transactionId, destinationAccount);
    }

    public static UUID overflowClaimId(
            ProtectedCashRedemptionReservation reservation
    ) {
        Objects.requireNonNull(reservation, "reservation");
        String material = "futureshops protected cash overflow claim v1 "
                + reservation.transactionId() + " "
                + reservation.reservationId();
        return UUID.nameUUIDFromBytes(material.getBytes(
                java.nio.charset.StandardCharsets.UTF_8));
    }

    public static String overflowClaimSourceKey(
            ProtectedCashRedemptionReservation reservation
    ) {
        return "protected.cash." + Objects.requireNonNull(
                reservation, "reservation").transactionId()
                + ".overflow.claim";
    }

    public long walletCreditMinorUnits() {
        return expectedWalletCredit(reservation,
                walletBalanceBeforeMinorUnits,
                walletReservedBeforeMinorUnits);
    }

    public long overflowClaimMinorUnits() {
        return Math.subtractExact(amountMinorUnits(),
                walletCreditMinorUnits());
    }

    private static void requireCompletedTransaction(
            ProtectedCashRedemptionReservation reservation,
            EscrowTransaction completed
    ) {
        EscrowTransaction held = reservation.heldTransaction();
        if (completed.state() != EscrowState.COMPLETED
                || !completed.transactionId().equals(held.transactionId())
                || !completed.parentTransactionId().equals(
                held.parentTransactionId())
                || !completed.requestKey().equals(held.requestKey())
                || completed.operation() != held.operation()
                || !completed.participants().equals(held.participants())
                || !completed.assetLots().equals(held.assetLots())
                || completed.configRevision() != held.configRevision()
                || !completed.shopReference().equals(held.shopReference())
                || completed.revision() != Math.addExact(
                held.revision(), 4L)
                || !completed.timestamps().createdAt().equals(
                held.timestamps().createdAt())
                || completed.timestamps().updatedAt().isBefore(
                held.timestamps().updatedAt())) {
            throw new IllegalArgumentException(
                    "Protected cash completed transaction identity is invalid");
        }
    }

    private static void requireInventoryMutation(
            ProtectedCashRedemptionReservation reservation,
            EscrowTransaction completed,
            InventoryMutationReceipt receipt
    ) {
        if (!receipt.playerId().equals(reservation.playerId())
                || !receipt.transactionId().equals(reservation.transactionId())
                || !receipt.reservationId().equals(reservation.reservationId())
                || !ProtectedCashRedemptionSupport.equal(
                receipt.beforeInventoryHash(),
                reservation.inventoryBeforeHash())
                || !receipt.requestKey().equals(inventoryMutationRequestKey(
                reservation.transactionId(),
                reservation.destinationAccount()))
                || receipt.mutations().size()
                != reservation.plan().portions().size()) {
            throw new IllegalArgumentException(
                    "Protected cash inventory receipt identity is invalid");
        }
        requireTime(receipt.occurredAt(),
                reservation.heldTransaction().timestamps().updatedAt(),
                completed.timestamps().updatedAt(),
                "Protected cash inventory mutation time");
        for (int index = 0;
             index < reservation.plan().portions().size(); index++) {
            InternalBillInventoryPlanner.Portion portion =
                    reservation.plan().portions().get(index);
            SlotMutation mutation = receipt.mutations().get(index);
            if (!mutation.slot().equals(portion.slot())
                    || mutation.removedCount() != portion.selectedCount()
                    || !Arrays.equals(mutation.beforeSnapshot(),
                    portion.exactStackSnapshot())
                    || !Arrays.equals(mutation.afterSnapshot(),
                    ProtectedCashRedemptionSupport.expectedAfterSnapshot(
                            portion))) {
                throw new IllegalArgumentException(
                        "Protected cash inventory receipt does not match its plan");
            }
        }
    }

    private static List<CustodyMutation> canonicalCustody(
            ProtectedCashRedemptionReservation reservation,
            EscrowTransaction completed,
            InventoryMutationReceipt inventory,
            List<CustodyMutation> supplied
    ) {
        if (supplied.size() != reservation.custodyReservations().size()
                || supplied.isEmpty()
                || supplied.size()
                > ProtectedCashRedemptionReservation.MAX_PORTIONS) {
            throw new IllegalArgumentException(
                    "Protected cash custody consumption count is invalid");
        }
        HashMap<UUID, CustodyMutation> byLot = new HashMap<>();
        for (CustodyMutation mutation : supplied) {
            Objects.requireNonNull(mutation, "custodyConsumption");
            if (byLot.put(mutation.resultingLot().lotId(), mutation) != null) {
                throw new IllegalArgumentException(
                        "Protected cash custody consumption is duplicated");
            }
        }
        List<CustodyMutation> ordered = new ArrayList<>(supplied.size());
        for (CustodyMutation reserve : reservation.custodyReservations()) {
            CustodyMutation consume = byLot.remove(
                    reserve.resultingLot().lotId());
            if (consume == null) {
                throw new IllegalArgumentException(
                        "Protected cash custody consumption is incomplete");
            }
            requireConsumption(reservation, completed, inventory,
                    reserve.resultingLot(), consume);
            ordered.add(consume);
        }
        if (!byLot.isEmpty()) {
            throw new IllegalArgumentException(
                    "Protected cash custody consumption has an unexpected lot");
        }
        return List.copyOf(ordered);
    }

    private static void requireConsumption(
            ProtectedCashRedemptionReservation reservation,
            EscrowTransaction completed,
            InventoryMutationReceipt inventory,
            CustodyLot held,
            CustodyMutation consume
    ) {
        String requestKey = custodyConsumeRequestKey(
                reservation.transactionId(), reservation.destinationAccount(),
                held.lotId());
        Instant consumedAt = consume.receipt().createdAt();
        CustodyLot expectedLot = held.transition(
                CustodyLotState.CONSUMED, consumedAt);
        if (!consume.resultingLot().equals(expectedLot)
                || consume.receipt().operation() != CustodyOperation.CONSUME
                || !consume.receipt().requestKey().equals(requestKey)
                || !consume.receipt().transactionId().equals(
                reservation.transactionId())
                || !consume.receipt().lotId().equals(held.lotId())
                || !consume.receipt().evidence().source().equals(
                held.holdEvidence().destination())
                || consume.receipt().evidence().destination().capability()
                != CustodyAdapterCapability.TRANSACTIONAL_PROTECTED
                || !consume.receipt().evidence().destination().ownerKey().equals(
                CURRENCY_SINK_OWNER)
                || !consume.receipt().evidence().destination().mutationToken()
                .equals(ProtectedCashRedemptionSupport.hex(
                        inventory.mutationTokenDigest()))) {
            throw new IllegalArgumentException(
                    "Protected cash custody consumption is invalid");
        }
        requireTime(consumedAt, inventory.occurredAt(),
                completed.timestamps().updatedAt(),
                "Protected cash custody consumption time");
    }

    private static List<ProtectedMintJournalEvent> canonicalMints(
            ProtectedCashRedemptionReservation reservation,
            EscrowTransaction completed,
            InventoryMutationReceipt inventory,
            List<ProtectedMintJournalEvent> supplied
    ) {
        if (supplied.size() != reservation.mintReservations().size()
                || supplied.isEmpty()
                || supplied.size()
                > ProtectedCashRedemptionReservation.MAX_MINT_BATCHES) {
            throw new IllegalArgumentException(
                    "Protected cash mint commit count is invalid");
        }
        HashMap<UUID, ProtectedMintJournalEvent> byBatch = new HashMap<>();
        Set<String> requestKeys = new HashSet<>();
        for (ProtectedMintJournalEvent event : supplied) {
            Objects.requireNonNull(event, "mintCommit");
            UUID batchId = event.targetBatchId().orElseThrow(() ->
                    new IllegalArgumentException(
                            "Protected cash mint commit lacks a batch"));
            if (byBatch.put(batchId, event) != null
                    || !requestKeys.add(event.requestKey())) {
                throw new IllegalArgumentException(
                        "Protected cash mint commit is duplicated");
            }
        }
        List<ProtectedMintJournalEvent> ordered = new ArrayList<>(
                reservation.mintReservations().size());
        for (ProtectedMintJournalEvent reserved :
                reservation.mintReservations()) {
            UUID batchId = reserved.targetBatchId().orElseThrow();
            ProtectedMintJournalEvent commit = byBatch.remove(batchId);
            if (commit == null
                    || commit.operation() != ProtectedMintOperation.COMMIT
                    || !commit.transactionId().equals(reservation.transactionId())
                    || !commit.requestKey().equals(mintCommitRequestKey(
                    reservation.transactionId(),
                    reservation.destinationAccount(), batchId))
                    || commit.quantity() != reserved.quantity()
                    || commit.batch().isPresent()) {
                throw new IllegalArgumentException(
                        "Protected cash mint commit does not match its reservation");
            }
            requireTime(commit.occurredAt(), inventory.occurredAt(),
                    completed.timestamps().updatedAt(),
                    "Protected cash mint commit time");
            ordered.add(commit);
        }
        if (!byBatch.isEmpty()) {
            throw new IllegalArgumentException(
                    "Protected cash mint commit has an unexpected batch");
        }
        return List.copyOf(ordered);
    }

    private static void requireLedger(
            ProtectedCashRedemptionReservation reservation,
            EscrowTransaction completed,
            InventoryMutationReceipt inventory,
            LedgerAccountId destination,
            long walletBalanceBefore,
            long walletReservedBefore,
            Optional<EscrowClaim> overflowClaim,
            LedgerTransaction ledger
    ) {
        if (!destination.equals(reservation.destinationAccount())) {
            throw new IllegalArgumentException(
                    "Protected cash settlement destination changed");
        }
        long amount = reservation.amountMinorUnits();
        boolean walletDestination = destination.type()
                == LedgerAccountType.PLAYER_WALLET;
        if (!walletDestination
                && (walletBalanceBefore != 0L
                || walletReservedBefore != 0L
                || overflowClaim.isPresent())) {
            throw new IllegalArgumentException(
                    "Protected cash wallet overflow data is unexpected");
        }
        long walletCredit = walletDestination
                ? expectedWalletCredit(reservation, walletBalanceBefore,
                walletReservedBefore) : 0L;
        long claimCredit = walletDestination
                ? Math.subtractExact(amount, walletCredit) : 0L;
        int expectedLegs = walletDestination
                ? 1 + (walletCredit > 0L ? 1 : 0)
                + (claimCredit > 0L ? 1 : 0) : 2;
        if (!ledger.transactionId().equals(reservation.transactionId())
                || !ledger.idempotencyKey().equals(ledgerIdempotencyKey(
                reservation.transactionId(),
                reservation.destinationAccount()))
                || !ledger.reason().equals(LEDGER_REASON)
                || ledger.legs().size() != expectedLegs) {
            throw new IllegalArgumentException(
                    "Protected cash ledger identity is invalid");
        }
        LedgerLeg outstanding = ledger.legs().get(0);
        if (outstanding.account().type()
                != LedgerAccountType.PROTECTED_CURRENCY_OUTSTANDING
                || !outstanding.account().ownerKey().equals("system")
                || outstanding.deltaMinor() != Math.negateExact(amount)) {
            throw new IllegalArgumentException(
                    "Protected cash ledger legs are invalid");
        }
        if (!walletDestination) {
            LedgerLeg credit = ledger.legs().get(1);
            if (!credit.account().equals(destination)
                    || credit.deltaMinor() != amount) {
                throw new IllegalArgumentException(
                        "Protected cash ledger legs are invalid");
            }
            return;
        }
        int legIndex = 1;
        if (walletCredit > 0L) {
            LedgerLeg wallet = ledger.legs().get(legIndex++);
            if (!wallet.account().equals(destination)
                    || wallet.deltaMinor() != walletCredit) {
                throw new IllegalArgumentException(
                        "Protected cash wallet credit is invalid");
            }
        }
        if (claimCredit == 0L) {
            if (overflowClaim.isPresent()) {
                throw new IllegalArgumentException(
                        "Protected cash overflow claim is unexpected");
            }
            return;
        }
        EscrowClaim claim = overflowClaim.orElseThrow(() ->
                new IllegalArgumentException(
                        "Protected cash overflow claim is missing"));
        LedgerLeg claimLeg = ledger.legs().get(legIndex);
        UUID claimId = overflowClaimId(reservation);
        if (!claim.claimId().equals(claimId)
                || !claim.transactionId().equals(reservation.transactionId())
                || !claim.ownerId().equals(reservation.playerId())
                || !claim.sourceKey().equals(
                overflowClaimSourceKey(reservation))
                || claim.kind() != expectedOverflowClaimKind(reservation)
                || claim.originalUnits() != claimCredit
                || claim.remainingUnits() != claimCredit
                || claim.payload().length != 0
                || claim.status() != ClaimStatus.PENDING
                || !claim.label().equals(OVERFLOW_CLAIM_LABEL)
                || !claim.createdAt().equals(claim.updatedAt())
                || claim.createdAt().isBefore(inventory.occurredAt())
                || claim.createdAt().isAfter(
                completed.timestamps().updatedAt())
                || claimLeg.account().type()
                != LedgerAccountType.PLAYER_CLAIM
                || !claimLeg.account().ownerKey().equals(claimId.toString())
                || claimLeg.deltaMinor() != claimCredit) {
            throw new IllegalArgumentException(
                    "Protected cash overflow claim is invalid");
        }
    }

    private static long expectedWalletCredit(
            ProtectedCashRedemptionReservation reservation,
            long walletBalanceBefore,
            long walletReservedBefore
    ) {
        if (reservation.depositMode() == CashDepositMode.INTERNAL_ESCROW) {
            return 0L;
        }
        if (reservation.destinationAccount().type()
                != LedgerAccountType.PLAYER_WALLET) {
            return 0L;
        }
        BigInteger capacity = BigInteger.valueOf(
                        reservation.walletBalanceLimitMinorUnits())
                .subtract(BigInteger.valueOf(walletBalanceBefore))
                .subtract(BigInteger.valueOf(walletReservedBefore));
        if (capacity.signum() <= 0) {
            return 0L;
        }
        BigInteger amount = BigInteger.valueOf(
                reservation.amountMinorUnits());
        return capacity.compareTo(amount) >= 0
                ? reservation.amountMinorUnits() : capacity.longValueExact();
    }

    private static ClaimKind expectedOverflowClaimKind(
            ProtectedCashRedemptionReservation reservation
    ) {
        return reservation.depositMode() == CashDepositMode.INTERNAL_ESCROW
                ? ClaimKind.INTERNAL_ESCROW_MONEY : ClaimKind.MONEY;
    }

    private static void requireTime(Instant value,
                                    Instant minimum,
                                    Instant maximum,
                                    String label) {
        if (value.isBefore(minimum) || value.isAfter(maximum)) {
            throw new IllegalArgumentException(label + " is invalid");
        }
    }

    public record SlotMutation(
            InternalBillInventoryPlanner.SlotIdentity slot,
            int removedCount,
            byte[] beforeSnapshot,
            byte[] afterSnapshot
    ) {
        public SlotMutation {
            Objects.requireNonNull(slot, "slot");
            Objects.requireNonNull(beforeSnapshot, "beforeSnapshot");
            Objects.requireNonNull(afterSnapshot, "afterSnapshot");
            beforeSnapshot = beforeSnapshot.clone();
            afterSnapshot = afterSnapshot.clone();
            if (removedCount <= 0
                    || beforeSnapshot.length == 0
                    || beforeSnapshot.length
                    > ItemStackSnapshotCodec.MAXIMUM_BYTES
                    || afterSnapshot.length
                    > ItemStackSnapshotCodec.MAXIMUM_BYTES) {
                throw new IllegalArgumentException(
                        "Protected cash inventory slot mutation is invalid");
            }
        }

        @Override
        public byte[] beforeSnapshot() {
            return beforeSnapshot.clone();
        }

        @Override
        public byte[] afterSnapshot() {
            return afterSnapshot.clone();
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof SlotMutation other)) {
                return false;
            }
            return removedCount == other.removedCount
                    && slot.equals(other.slot)
                    && Arrays.equals(beforeSnapshot, other.beforeSnapshot)
                    && Arrays.equals(afterSnapshot, other.afterSnapshot);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(slot, removedCount);
            result = 31 * result + Arrays.hashCode(beforeSnapshot);
            return 31 * result + Arrays.hashCode(afterSnapshot);
        }
    }

    public record InventoryMutationReceipt(
            UUID receiptId,
            UUID playerId,
            UUID transactionId,
            UUID reservationId,
            String requestKey,
            List<SlotMutation> mutations,
            byte[] beforeInventoryHash,
            byte[] afterInventoryHash,
            byte[] mutationTokenDigest,
            Instant occurredAt
    ) {
        public static final int MAX_REQUEST_KEY_LENGTH = 256;

        public InventoryMutationReceipt {
            Objects.requireNonNull(receiptId, "receiptId");
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(transactionId, "transactionId");
            Objects.requireNonNull(reservationId, "reservationId");
            requestKey = Objects.requireNonNull(requestKey, "requestKey").trim();
            mutations = canonicalMutations(mutations);
            Objects.requireNonNull(beforeInventoryHash, "beforeInventoryHash");
            Objects.requireNonNull(afterInventoryHash, "afterInventoryHash");
            Objects.requireNonNull(mutationTokenDigest, "mutationTokenDigest");
            Objects.requireNonNull(occurredAt, "occurredAt");
            beforeInventoryHash = beforeInventoryHash.clone();
            afterInventoryHash = afterInventoryHash.clone();
            mutationTokenDigest = mutationTokenDigest.clone();
            if (requestKey.isEmpty()
                    || requestKey.length() > MAX_REQUEST_KEY_LENGTH) {
                throw new IllegalArgumentException(
                        "Protected cash inventory request key is invalid");
            }
            ProtectedCashRedemptionSupport.requireHash(
                    beforeInventoryHash,
                    "Protected cash inventory before hash");
            ProtectedCashRedemptionSupport.requireHash(
                    afterInventoryHash,
                    "Protected cash inventory after hash");
            ProtectedCashRedemptionSupport.requireHash(
                    mutationTokenDigest,
                    "Protected cash inventory mutation token digest");
            if (Arrays.equals(beforeInventoryHash, afterInventoryHash)
                    || !receiptId.equals(
                    ProtectedCashRedemptionSupport.inventoryReceiptId(
                            requestKey))) {
                throw new IllegalArgumentException(
                        "Protected cash inventory receipt identity is invalid");
            }
            byte[] expectedDigest = tokenDigest(receiptId, playerId,
                    transactionId, reservationId, requestKey, mutations,
                    beforeInventoryHash, afterInventoryHash, occurredAt);
            if (!ProtectedCashRedemptionSupport.equal(
                    mutationTokenDigest, expectedDigest)) {
                throw new IllegalArgumentException(
                        "Protected cash inventory mutation token is invalid");
            }
        }

        public static InventoryMutationReceipt create(
                UUID playerId,
                UUID transactionId,
                UUID reservationId,
                String requestKey,
                List<SlotMutation> mutations,
                byte[] beforeInventoryHash,
                byte[] afterInventoryHash,
                Instant occurredAt
        ) {
            List<SlotMutation> canonical = canonicalMutations(mutations);
            UUID receiptId = ProtectedCashRedemptionSupport.inventoryReceiptId(
                    requestKey);
            byte[] digest = tokenDigest(receiptId, playerId, transactionId,
                    reservationId, requestKey, canonical, beforeInventoryHash,
                    afterInventoryHash, occurredAt);
            return new InventoryMutationReceipt(receiptId, playerId,
                    transactionId, reservationId, requestKey, canonical,
                    beforeInventoryHash, afterInventoryHash, digest, occurredAt);
        }

        @Override
        public byte[] beforeInventoryHash() {
            return beforeInventoryHash.clone();
        }

        @Override
        public byte[] afterInventoryHash() {
            return afterInventoryHash.clone();
        }

        @Override
        public byte[] mutationTokenDigest() {
            return mutationTokenDigest.clone();
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof InventoryMutationReceipt other)) {
                return false;
            }
            return receiptId.equals(other.receiptId)
                    && playerId.equals(other.playerId)
                    && transactionId.equals(other.transactionId)
                    && reservationId.equals(other.reservationId)
                    && requestKey.equals(other.requestKey)
                    && mutations.equals(other.mutations)
                    && Arrays.equals(beforeInventoryHash,
                    other.beforeInventoryHash)
                    && Arrays.equals(afterInventoryHash,
                    other.afterInventoryHash)
                    && Arrays.equals(mutationTokenDigest,
                    other.mutationTokenDigest)
                    && occurredAt.equals(other.occurredAt);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(receiptId, playerId, transactionId,
                    reservationId, requestKey, mutations, occurredAt);
            result = 31 * result + Arrays.hashCode(beforeInventoryHash);
            result = 31 * result + Arrays.hashCode(afterInventoryHash);
            return 31 * result + Arrays.hashCode(mutationTokenDigest);
        }

        private static List<SlotMutation> canonicalMutations(
                List<SlotMutation> supplied
        ) {
            Objects.requireNonNull(supplied, "mutations");
            if (supplied.isEmpty()
                    || supplied.size()
                    > ProtectedCashRedemptionReservation.MAX_PORTIONS) {
                throw new IllegalArgumentException(
                        "Protected cash inventory mutation count is invalid");
            }
            List<SlotMutation> ordered = new ArrayList<>(supplied);
            ordered.forEach(value -> Objects.requireNonNull(
                    value, "slotMutation"));
            ordered.sort(Comparator.comparing(SlotMutation::slot));
            Set<InternalBillInventoryPlanner.SlotIdentity> slots =
                    new HashSet<>();
            long snapshotBytes = 0L;
            for (SlotMutation mutation : ordered) {
                if (!slots.add(mutation.slot())) {
                    throw new IllegalArgumentException(
                            "Protected cash inventory mutation repeats a slot");
                }
                snapshotBytes = Math.addExact(snapshotBytes,
                        mutation.beforeSnapshot().length);
                snapshotBytes = Math.addExact(snapshotBytes,
                        mutation.afterSnapshot().length);
                if (snapshotBytes > Math.multiplyExact(
                        (long) ProtectedCashRedemptionSupport.MAX_TOTAL_SNAPSHOT_BYTES,
                        2L)) {
                    throw new IllegalArgumentException(
                            "Protected cash inventory snapshots exceed their limit");
                }
            }
            return List.copyOf(ordered);
        }

        private static byte[] tokenDigest(
                UUID receiptId,
                UUID playerId,
                UUID transactionId,
                UUID reservationId,
                String requestKey,
                List<SlotMutation> mutations,
                byte[] beforeInventoryHash,
                byte[] afterInventoryHash,
                Instant occurredAt
        ) {
            Objects.requireNonNull(receiptId, "receiptId");
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(transactionId, "transactionId");
            Objects.requireNonNull(reservationId, "reservationId");
            Objects.requireNonNull(requestKey, "requestKey");
            Objects.requireNonNull(beforeInventoryHash, "beforeInventoryHash");
            Objects.requireNonNull(afterInventoryHash, "afterInventoryHash");
            Objects.requireNonNull(occurredAt, "occurredAt");
            List<SlotMutation> canonical = canonicalMutations(mutations);
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                DataOutputStream output = new DataOutputStream(bytes);
                ProtectedCashRedemptionSupport.writeUuid(output, receiptId);
                ProtectedCashRedemptionSupport.writeUuid(output, playerId);
                ProtectedCashRedemptionSupport.writeUuid(output, transactionId);
                ProtectedCashRedemptionSupport.writeUuid(output, reservationId);
                ProtectedCashRedemptionSupport.writeString(output, requestKey,
                        MAX_REQUEST_KEY_LENGTH * 4);
                output.writeInt(canonical.size());
                for (SlotMutation mutation : canonical) {
                    output.writeInt(mutation.slot().container().ordinal());
                    output.writeInt(mutation.slot().index());
                    output.writeInt(mutation.removedCount());
                    ProtectedCashRedemptionSupport.writeBytes(output,
                            mutation.beforeSnapshot(),
                            ItemStackSnapshotCodec.MAXIMUM_BYTES,
                            "Protected cash inventory before snapshot");
                    ProtectedCashRedemptionSupport.writeBytes(output,
                            mutation.afterSnapshot(),
                            ItemStackSnapshotCodec.MAXIMUM_BYTES,
                            "Protected cash inventory after snapshot");
                }
                output.write(beforeInventoryHash);
                output.write(afterInventoryHash);
                ProtectedCashRedemptionSupport.writeInstant(output, occurredAt);
                output.flush();
                return ProtectedCashRedemptionSupport.sha256(
                        bytes.toByteArray());
            } catch (IOException exception) {
                throw new IllegalStateException(
                        "Unable to digest protected cash inventory mutation",
                        exception);
            }
        }
    }
}

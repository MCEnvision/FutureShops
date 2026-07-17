package com.enviouse.futureshops.server.escrow.redemption;

import com.enviouse.futureshops.money.InternalBillInventoryPlanner;
import com.enviouse.futureshops.money.ItemStackSnapshotCodec;
import com.enviouse.futureshops.server.escrow.custody.CustodyEndpointEvidence;
import com.enviouse.futureshops.server.escrow.custody.CustodyLot;
import com.enviouse.futureshops.server.escrow.custody.CustodyLotState;
import com.enviouse.futureshops.server.escrow.custody.CustodyMutation;
import com.enviouse.futureshops.server.escrow.custody.CustodyOperation;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountId;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintJournalEvent;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintOperation;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintState;
import com.enviouse.futureshops.server.escrow.model.EscrowState;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record ProtectedCashRedemptionCancellation(
        ProtectedCashRedemptionReservation reservation,
        EscrowTransaction refundedTransaction,
        InventoryNoMutationProof inventoryProof,
        List<CustodyMutation> custodyReleases,
        List<ProtectedMintJournalEvent> mintReleases
) {
    public ProtectedCashRedemptionCancellation {
        Objects.requireNonNull(reservation, "reservation");
        Objects.requireNonNull(refundedTransaction, "refundedTransaction");
        Objects.requireNonNull(inventoryProof, "inventoryProof");
        Objects.requireNonNull(custodyReleases, "custodyReleases");
        Objects.requireNonNull(mintReleases, "mintReleases");
        requireRefundedTransaction(reservation, refundedTransaction);
        requireInventoryProof(reservation, refundedTransaction,
                inventoryProof);
        custodyReleases = canonicalCustody(reservation,
                refundedTransaction, inventoryProof, custodyReleases);
        mintReleases = canonicalMints(reservation, refundedTransaction,
                inventoryProof, mintReleases);
    }

    public UUID transactionId() {
        return reservation.transactionId();
    }

    public long amountMinorUnits() {
        return reservation.amountMinorUnits();
    }

    public String fingerprint() {
        return ProtectedCashRedemptionCancellationCodec.fingerprint(this);
    }

    public static String inventoryProofRequestKey(
            UUID transactionId,
            LedgerAccountId destinationAccount
    ) {
        return ProtectedCashRedemptionSupport.noMutationProofRequestKey(
                transactionId, destinationAccount);
    }

    public static String custodyReleaseRequestKey(
            UUID transactionId,
            LedgerAccountId destinationAccount,
            UUID lotId
    ) {
        return ProtectedCashRedemptionSupport.releaseLotRequestKey(
                transactionId, destinationAccount, lotId);
    }

    public static String mintReleaseRequestKey(
            UUID transactionId,
            LedgerAccountId destinationAccount,
            UUID batchId
    ) {
        return ProtectedCashRedemptionSupport.releaseMintRequestKey(
                transactionId, destinationAccount, batchId);
    }

    private static void requireRefundedTransaction(
            ProtectedCashRedemptionReservation reservation,
            EscrowTransaction refunded
    ) {
        EscrowTransaction held = reservation.heldTransaction();
        if (refunded.state() != EscrowState.REFUNDED
                || !refunded.transactionId().equals(held.transactionId())
                || !refunded.parentTransactionId().equals(
                held.parentTransactionId())
                || !refunded.requestKey().equals(held.requestKey())
                || refunded.operation() != held.operation()
                || !refunded.participants().equals(held.participants())
                || !refunded.assetLots().equals(held.assetLots())
                || refunded.configRevision() != held.configRevision()
                || !refunded.shopReference().equals(held.shopReference())
                || refunded.revision() != Math.addExact(held.revision(), 3L)
                || !refunded.timestamps().createdAt().equals(
                held.timestamps().createdAt())
                || refunded.timestamps().commitDecidedAt().isPresent()
                || refunded.timestamps().terminalAt().isEmpty()
                || refunded.timestamps().updatedAt().isBefore(
                held.timestamps().updatedAt())) {
            throw new IllegalArgumentException(
                    "Protected cash refunded transaction identity is invalid");
        }
    }

    private static void requireInventoryProof(
            ProtectedCashRedemptionReservation reservation,
            EscrowTransaction refunded,
            InventoryNoMutationProof proof
    ) {
        if (!proof.playerId().equals(reservation.playerId())
                || !proof.transactionId().equals(reservation.transactionId())
                || !proof.reservationId().equals(reservation.reservationId())
                || !proof.requestKey().equals(inventoryProofRequestKey(
                reservation.transactionId(),
                reservation.destinationAccount()))
                || !ProtectedCashRedemptionSupport.equal(
                proof.inventoryHash(), reservation.inventoryBeforeHash())
                || proof.observations().size()
                != reservation.plan().portions().size()) {
            throw new IllegalArgumentException(
                    "Protected cash no mutation proof identity is invalid");
        }
        requireTime(proof.inspectedAt(),
                reservation.heldTransaction().timestamps().updatedAt(),
                refunded.timestamps().updatedAt(),
                "Protected cash no mutation proof time");
        for (int index = 0;
             index < reservation.plan().portions().size(); index++) {
            InternalBillInventoryPlanner.Portion portion =
                    reservation.plan().portions().get(index);
            SlotObservation observation = proof.observations().get(index);
            if (!observation.slot().equals(portion.slot())
                    || !Arrays.equals(observation.exactSnapshot(),
                    portion.exactStackSnapshot())) {
                throw new IllegalArgumentException(
                        "Protected cash no mutation proof does not match plan");
            }
        }
    }

    private static List<CustodyMutation> canonicalCustody(
            ProtectedCashRedemptionReservation reservation,
            EscrowTransaction refunded,
            InventoryNoMutationProof proof,
            List<CustodyMutation> supplied
    ) {
        if (supplied.size() != reservation.custodyReservations().size()
                || supplied.isEmpty()
                || supplied.size()
                > ProtectedCashRedemptionReservation.MAX_PORTIONS) {
            throw new IllegalArgumentException(
                    "Protected cash custody release count is invalid");
        }
        HashMap<UUID, CustodyMutation> byLot = new HashMap<>();
        for (CustodyMutation mutation : supplied) {
            Objects.requireNonNull(mutation, "custodyRelease");
            if (byLot.put(mutation.resultingLot().lotId(), mutation) != null) {
                throw new IllegalArgumentException(
                        "Protected cash custody release is duplicated");
            }
        }
        List<CustodyMutation> ordered = new ArrayList<>(supplied.size());
        for (int index = 0;
             index < reservation.custodyReservations().size(); index++) {
            CustodyLot held = reservation.custodyReservations().get(index)
                    .resultingLot();
            CustodyMutation release = byLot.remove(held.lotId());
            if (release == null) {
                throw new IllegalArgumentException(
                        "Protected cash custody release is incomplete");
            }
            requireCustodyRelease(reservation, refunded, proof, held,
                    reservation.plan().portions().get(index), release);
            ordered.add(release);
        }
        if (!byLot.isEmpty()) {
            throw new IllegalArgumentException(
                    "Protected cash custody release has an unexpected lot");
        }
        return List.copyOf(ordered);
    }

    private static void requireCustodyRelease(
            ProtectedCashRedemptionReservation reservation,
            EscrowTransaction refunded,
            InventoryNoMutationProof proof,
            CustodyLot held,
            InternalBillInventoryPlanner.Portion portion,
            CustodyMutation release
    ) {
        String requestKey = custodyReleaseRequestKey(
                reservation.transactionId(), reservation.destinationAccount(),
                held.lotId());
        Instant releasedAt = release.receipt().createdAt();
        CustodyLot expected = held.transition(CustodyLotState.RELEASED,
                releasedAt);
        CustodyEndpointEvidence originalSource = held.holdEvidence().source();
        CustodyEndpointEvidence destination = release.receipt().evidence()
                .destination();
        byte[] snapshotHash = ProtectedCashRedemptionSupport.sha256(
                portion.exactStackSnapshot());
        if (!release.resultingLot().equals(expected)
                || release.receipt().operation() != CustodyOperation.RELEASE
                || !release.receipt().requestKey().equals(requestKey)
                || !release.receipt().transactionId().equals(
                reservation.transactionId())
                || !release.receipt().lotId().equals(held.lotId())
                || !release.receipt().evidence().source().equals(
                held.holdEvidence().destination())
                || !destination.adapterId().equals(
                originalSource.adapterId())
                || destination.capability() != originalSource.capability()
                || !destination.ownerKey().equals(originalSource.ownerKey())
                || !destination.locationKey().equals(
                originalSource.locationKey())
                || !ProtectedCashRedemptionSupport.equal(
                destination.beforeStateHash(), snapshotHash)
                || !ProtectedCashRedemptionSupport.equal(
                destination.afterStateHash(), snapshotHash)
                || !destination.mutationToken().equals(
                ProtectedCashRedemptionSupport.hex(proof.proofDigest()))) {
            throw new IllegalArgumentException(
                    "Protected cash custody release is invalid");
        }
        requireTime(releasedAt, proof.inspectedAt(),
                refunded.timestamps().updatedAt(),
                "Protected cash custody release time");
    }

    private static List<ProtectedMintJournalEvent> canonicalMints(
            ProtectedCashRedemptionReservation reservation,
            EscrowTransaction refunded,
            InventoryNoMutationProof proof,
            List<ProtectedMintJournalEvent> supplied
    ) {
        if (supplied.size() != reservation.mintReservations().size()
                || supplied.isEmpty()
                || supplied.size()
                > ProtectedCashRedemptionReservation.MAX_MINT_BATCHES) {
            throw new IllegalArgumentException(
                    "Protected cash mint release count is invalid");
        }
        HashMap<UUID, ProtectedMintJournalEvent> byBatch = new HashMap<>();
        Set<String> requestKeys = new HashSet<>();
        for (ProtectedMintJournalEvent event : supplied) {
            Objects.requireNonNull(event, "mintRelease");
            UUID batchId = event.targetBatchId().orElseThrow(() ->
                    new IllegalArgumentException(
                            "Protected cash mint release lacks a batch"));
            if (byBatch.put(batchId, event) != null
                    || !requestKeys.add(event.requestKey())) {
                throw new IllegalArgumentException(
                        "Protected cash mint release is duplicated");
            }
        }
        List<ProtectedMintJournalEvent> ordered = new ArrayList<>(
                reservation.mintReservations().size());
        for (ProtectedMintJournalEvent reserved :
                reservation.mintReservations()) {
            UUID batchId = reserved.targetBatchId().orElseThrow();
            ProtectedMintJournalEvent release = byBatch.remove(batchId);
            if (release == null
                    || release.operation() != ProtectedMintOperation.RELEASE
                    || !release.transactionId().equals(
                    reservation.transactionId())
                    || !release.requestKey().equals(mintReleaseRequestKey(
                    reservation.transactionId(),
                    reservation.destinationAccount(), batchId))
                    || release.quantity() != reserved.quantity()
                    || release.sourceState().orElse(null)
                    != ProtectedMintState.RESERVED
                    || release.batch().isPresent()) {
                throw new IllegalArgumentException(
                        "Protected cash mint release is invalid");
            }
            requireTime(release.occurredAt(), proof.inspectedAt(),
                    refunded.timestamps().updatedAt(),
                    "Protected cash mint release time");
            ordered.add(release);
        }
        if (!byBatch.isEmpty()) {
            throw new IllegalArgumentException(
                    "Protected cash mint release has an unexpected batch");
        }
        return List.copyOf(ordered);
    }

    private static void requireTime(Instant value,
                                    Instant minimum,
                                    Instant maximum,
                                    String label) {
        if (value.isBefore(minimum) || value.isAfter(maximum)) {
            throw new IllegalArgumentException(label + " is invalid");
        }
    }

    public record SlotObservation(
            InternalBillInventoryPlanner.SlotIdentity slot,
            byte[] exactSnapshot
    ) {
        public SlotObservation {
            Objects.requireNonNull(slot, "slot");
            Objects.requireNonNull(exactSnapshot, "exactSnapshot");
            exactSnapshot = exactSnapshot.clone();
            if (exactSnapshot.length == 0
                    || exactSnapshot.length
                    > ItemStackSnapshotCodec.MAXIMUM_BYTES) {
                throw new IllegalArgumentException(
                        "Protected cash no mutation snapshot is invalid");
            }
        }

        @Override
        public byte[] exactSnapshot() {
            return exactSnapshot.clone();
        }

        @Override
        public boolean equals(Object object) {
            return object instanceof SlotObservation other
                    && slot.equals(other.slot)
                    && Arrays.equals(exactSnapshot, other.exactSnapshot);
        }

        @Override
        public int hashCode() {
            return 31 * slot.hashCode() + Arrays.hashCode(exactSnapshot);
        }
    }

    public record InventoryNoMutationProof(
            UUID proofId,
            UUID playerId,
            UUID transactionId,
            UUID reservationId,
            String requestKey,
            List<SlotObservation> observations,
            byte[] inventoryHash,
            byte[] proofDigest,
            Instant inspectedAt
    ) {
        public static final int MAX_REQUEST_KEY_LENGTH = 256;

        public InventoryNoMutationProof {
            Objects.requireNonNull(proofId, "proofId");
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(transactionId, "transactionId");
            Objects.requireNonNull(reservationId, "reservationId");
            requestKey = Objects.requireNonNull(requestKey,
                    "requestKey").trim();
            observations = canonicalObservations(observations);
            ProtectedCashRedemptionSupport.requireHash(inventoryHash,
                    "Protected cash no mutation inventory hash");
            ProtectedCashRedemptionSupport.requireHash(proofDigest,
                    "Protected cash no mutation proof digest");
            inventoryHash = inventoryHash.clone();
            proofDigest = proofDigest.clone();
            Objects.requireNonNull(inspectedAt, "inspectedAt");
            if (requestKey.isEmpty()
                    || requestKey.length() > MAX_REQUEST_KEY_LENGTH
                    || !proofId.equals(
                    ProtectedCashRedemptionSupport.inventoryReceiptId(
                            requestKey))) {
                throw new IllegalArgumentException(
                        "Protected cash no mutation proof identity is invalid");
            }
            byte[] expectedDigest = digest(proofId, playerId, transactionId,
                    reservationId, requestKey, observations, inventoryHash,
                    inspectedAt);
            if (!ProtectedCashRedemptionSupport.equal(
                    proofDigest, expectedDigest)) {
                throw new IllegalArgumentException(
                        "Protected cash no mutation proof digest is invalid");
            }
        }

        public static InventoryNoMutationProof create(
                UUID playerId,
                UUID transactionId,
                UUID reservationId,
                String requestKey,
                List<SlotObservation> observations,
                byte[] inventoryHash,
                Instant inspectedAt
        ) {
            List<SlotObservation> canonical = canonicalObservations(
                    observations);
            UUID proofId = ProtectedCashRedemptionSupport.inventoryReceiptId(
                    requestKey);
            byte[] digest = digest(proofId, playerId, transactionId,
                    reservationId, requestKey, canonical, inventoryHash,
                    inspectedAt);
            return new InventoryNoMutationProof(proofId, playerId,
                    transactionId, reservationId, requestKey, canonical,
                    inventoryHash, digest, inspectedAt);
        }

        @Override
        public byte[] inventoryHash() {
            return inventoryHash.clone();
        }

        @Override
        public byte[] proofDigest() {
            return proofDigest.clone();
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof InventoryNoMutationProof other)) {
                return false;
            }
            return proofId.equals(other.proofId)
                    && playerId.equals(other.playerId)
                    && transactionId.equals(other.transactionId)
                    && reservationId.equals(other.reservationId)
                    && requestKey.equals(other.requestKey)
                    && observations.equals(other.observations)
                    && Arrays.equals(inventoryHash, other.inventoryHash)
                    && Arrays.equals(proofDigest, other.proofDigest)
                    && inspectedAt.equals(other.inspectedAt);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(proofId, playerId, transactionId,
                    reservationId, requestKey, observations, inspectedAt);
            result = 31 * result + Arrays.hashCode(inventoryHash);
            return 31 * result + Arrays.hashCode(proofDigest);
        }

        private static List<SlotObservation> canonicalObservations(
                List<SlotObservation> supplied
        ) {
            Objects.requireNonNull(supplied, "observations");
            if (supplied.isEmpty()
                    || supplied.size()
                    > ProtectedCashRedemptionReservation.MAX_PORTIONS) {
                throw new IllegalArgumentException(
                        "Protected cash no mutation observation count is invalid");
            }
            List<SlotObservation> ordered = new ArrayList<>(supplied);
            ordered.forEach(value -> Objects.requireNonNull(value,
                    "slotObservation"));
            ordered.sort(Comparator.comparing(SlotObservation::slot));
            Set<InternalBillInventoryPlanner.SlotIdentity> slots =
                    new HashSet<>();
            long bytes = 0L;
            for (SlotObservation observation : ordered) {
                if (!slots.add(observation.slot())) {
                    throw new IllegalArgumentException(
                            "Protected cash no mutation slot is duplicated");
                }
                bytes = Math.addExact(bytes,
                        observation.exactSnapshot().length);
                if (bytes
                        > ProtectedCashRedemptionReservation
                        .MAX_TOTAL_SNAPSHOT_BYTES) {
                    throw new IllegalArgumentException(
                            "Protected cash no mutation snapshots are too large");
                }
            }
            return List.copyOf(ordered);
        }

        private static byte[] digest(
                UUID proofId,
                UUID playerId,
                UUID transactionId,
                UUID reservationId,
                String requestKey,
                List<SlotObservation> observations,
                byte[] inventoryHash,
                Instant inspectedAt
        ) {
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                DataOutputStream output = new DataOutputStream(bytes);
                ProtectedCashRedemptionSupport.writeUuid(output, proofId);
                ProtectedCashRedemptionSupport.writeUuid(output, playerId);
                ProtectedCashRedemptionSupport.writeUuid(output,
                        transactionId);
                ProtectedCashRedemptionSupport.writeUuid(output,
                        reservationId);
                ProtectedCashRedemptionSupport.writeString(output,
                        requestKey, MAX_REQUEST_KEY_LENGTH * 4);
                output.writeInt(observations.size());
                for (SlotObservation observation : observations) {
                    output.writeInt(
                            observation.slot().container().ordinal());
                    output.writeInt(observation.slot().index());
                    ProtectedCashRedemptionSupport.writeBytes(output,
                            observation.exactSnapshot(),
                            ItemStackSnapshotCodec.MAXIMUM_BYTES,
                            "Protected cash no mutation snapshot");
                }
                ProtectedCashRedemptionSupport.writeBytes(output,
                        inventoryHash,
                        ProtectedCashRedemptionSupport.HASH_BYTES,
                        "Protected cash no mutation inventory hash");
                ProtectedCashRedemptionSupport.writeInstant(output,
                        inspectedAt);
                output.flush();
                return ProtectedCashRedemptionSupport.sha256(
                        bytes.toByteArray());
            } catch (IOException exception) {
                throw new IllegalStateException(
                        "Unable to hash protected cash no mutation proof",
                        exception);
            }
        }
    }
}

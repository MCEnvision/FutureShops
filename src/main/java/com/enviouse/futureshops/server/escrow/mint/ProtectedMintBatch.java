package com.enviouse.futureshops.server.escrow.mint;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record ProtectedMintBatch(UUID batchId, UUID transactionId,
                                 String authorizeRequestKey,
                                 long denominationMinorUnits,
                                 int authorizedCount,
                                 int authorizedQuantity,
                                 int availableQuantity,
                                 Map<UUID, Integer> reservedQuantities,
                                 Map<UUID, Integer> spentQuantities,
                                 int refundedQuantity,
                                 int quarantinedQuantity,
                                 Optional<UUID> replacementForBatchId,
                                 String serverIdentityEvidence,
                                 String checksumEvidence,
                                 Instant authorizedAt,
                                 Instant updatedAt,
                                 long revision) {
    public static final int MAX_AUTHORIZED_COUNT = 4096;
    public static final int MAX_RESERVATION_ENTRIES = 4096;

    public ProtectedMintBatch {
        Objects.requireNonNull(batchId, "batchId");
        Objects.requireNonNull(transactionId, "transactionId");
        authorizeRequestKey = ProtectedMintText.requestKey(authorizeRequestKey);
        reservedQuantities = quantityMap(reservedQuantities, "reservedQuantities");
        spentQuantities = quantityMap(spentQuantities, "spentQuantities");
        replacementForBatchId = Objects.requireNonNull(replacementForBatchId,
                "replacementForBatchId");
        serverIdentityEvidence = ProtectedMintText.serverEvidence(serverIdentityEvidence);
        checksumEvidence = ProtectedMintText.checksumEvidence(checksumEvidence);
        Objects.requireNonNull(authorizedAt, "authorizedAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (denominationMinorUnits <= 0L || authorizedCount <= 0
                || authorizedCount > MAX_AUTHORIZED_COUNT || authorizedQuantity < 0
                || availableQuantity < 0 || refundedQuantity < 0
                || quarantinedQuantity < 0 || revision < 0L) {
            throw new IllegalArgumentException("Protected mint batch quantities are invalid");
        }
        if (updatedAt.isBefore(authorizedAt)) {
            throw new IllegalArgumentException("Protected mint update precedes authorization");
        }
        long accounted = authorizedQuantity;
        accounted = Math.addExact(accounted, availableQuantity);
        accounted = Math.addExact(accounted, sum(reservedQuantities));
        accounted = Math.addExact(accounted, sum(spentQuantities));
        accounted = Math.addExact(accounted, refundedQuantity);
        accounted = Math.addExact(accounted, quarantinedQuantity);
        if (accounted != authorizedCount) {
            throw new IllegalArgumentException("Protected mint batch does not conserve");
        }
        if (revision == 0L && (authorizedQuantity != authorizedCount
                || availableQuantity != 0 || !reservedQuantities.isEmpty()
                || !spentQuantities.isEmpty() || refundedQuantity != 0
                || quarantinedQuantity != 0 || !updatedAt.equals(authorizedAt))) {
            throw new IllegalArgumentException("Initial protected mint batch is invalid");
        }
        replacementForBatchId.ifPresent(value -> {
            if (value.equals(batchId)) {
                throw new IllegalArgumentException("Protected mint batch cannot replace itself");
            }
        });
        Math.multiplyExact(denominationMinorUnits, (long) authorizedCount);
    }

    public static ProtectedMintBatch plan(UUID batchId, UUID transactionId,
                                          String requestKey,
                                          long denominationMinorUnits,
                                          int authorizedCount,
                                          String serverIdentityEvidence,
                                          Instant authorizedAt,
                                          ProtectedMintEvidenceFactory evidenceFactory) {
        return initial(batchId, transactionId, requestKey, denominationMinorUnits,
                authorizedCount, Optional.empty(), serverIdentityEvidence,
                authorizedAt, evidenceFactory);
    }

    public static ProtectedMintBatch plan(UUID transactionId, String requestKey,
                                          long denominationMinorUnits,
                                          int authorizedCount,
                                          String serverIdentityEvidence,
                                          Instant authorizedAt,
                                          ProtectedMintEvidenceFactory evidenceFactory) {
        return plan(ProtectedMintIds.batchId(transactionId, requestKey), transactionId,
                requestKey, denominationMinorUnits, authorizedCount,
                serverIdentityEvidence, authorizedAt, evidenceFactory);
    }

    public static ProtectedMintBatch issue(UUID batchId, UUID transactionId,
                                           String requestKey,
                                           long denominationMinorUnits,
                                           int authorizedCount,
                                           String serverIdentityEvidence,
                                           Instant issuedAt,
                                           ProtectedMintEvidenceFactory evidenceFactory) {
        return plan(batchId, transactionId, requestKey, denominationMinorUnits,
                authorizedCount, serverIdentityEvidence, issuedAt, evidenceFactory)
                .materialize(authorizedCount, issuedAt);
    }

    public static ProtectedMintBatch issue(UUID transactionId, String requestKey,
                                           long denominationMinorUnits,
                                           int authorizedCount,
                                           String serverIdentityEvidence,
                                           Instant issuedAt,
                                           ProtectedMintEvidenceFactory evidenceFactory) {
        return issue(ProtectedMintIds.batchId(transactionId, requestKey), transactionId,
                requestKey, denominationMinorUnits, authorizedCount,
                serverIdentityEvidence, issuedAt, evidenceFactory);
    }

    public static ProtectedMintBatch replacement(UUID replacementBatchId,
                                                 UUID refundTransactionId,
                                                 String requestKey,
                                                 ProtectedMintBatch sourceBatch,
                                                 int quantity,
                                                 String serverIdentityEvidence,
                                                 Instant authorizedAt,
                                                 ProtectedMintEvidenceFactory evidenceFactory) {
        Objects.requireNonNull(sourceBatch, "sourceBatch");
        return initial(replacementBatchId, refundTransactionId, requestKey,
                sourceBatch.denominationMinorUnits(), quantity,
                Optional.of(sourceBatch.batchId()), serverIdentityEvidence,
                authorizedAt, evidenceFactory);
    }

    public ProtectedMintBatch materialize(int quantity, Instant now) {
        requireQuantity(authorizedQuantity, quantity, "authorized");
        return changed(authorizedQuantity - quantity, availableQuantity + quantity,
                reservedQuantities, spentQuantities, refundedQuantity,
                quarantinedQuantity, now);
    }

    public ProtectedMintBatch reserve(UUID reservationTransactionId, int quantity,
                                      Instant now) {
        Objects.requireNonNull(reservationTransactionId, "reservationTransactionId");
        requireQuantity(availableQuantity, quantity, "available");
        Map<UUID, Integer> reserved = add(reservedQuantities, reservationTransactionId,
                quantity);
        return changed(authorizedQuantity, availableQuantity - quantity, reserved,
                spentQuantities, refundedQuantity, quarantinedQuantity, now);
    }

    public ProtectedMintBatch commit(UUID reservationTransactionId, int quantity,
                                     Instant now) {
        Map<UUID, Integer> reserved = subtract(reservedQuantities,
                reservationTransactionId, quantity, "reserved");
        Map<UUID, Integer> spent = add(spentQuantities, reservationTransactionId, quantity);
        return changed(authorizedQuantity, availableQuantity, reserved, spent,
                refundedQuantity, quarantinedQuantity, now);
    }

    public ProtectedMintBatch refund(UUID reservationTransactionId,
                                     ProtectedMintState sourceState,
                                     int quantity, Instant now) {
        Objects.requireNonNull(sourceState, "sourceState");
        Map<UUID, Integer> reserved = reservedQuantities;
        Map<UUID, Integer> spent = spentQuantities;
        if (sourceState == ProtectedMintState.RESERVED) {
            reserved = subtract(reserved, reservationTransactionId, quantity, "reserved");
        } else if (sourceState == ProtectedMintState.SPENT) {
            spent = subtract(spent, reservationTransactionId, quantity, "spent");
        } else {
            throw new IllegalStateException("Protected mint refund source is invalid");
        }
        return changed(authorizedQuantity, availableQuantity, reserved, spent,
                Math.addExact(refundedQuantity, quantity), quarantinedQuantity, now);
    }

    public ProtectedMintBatch quarantine(Optional<UUID> reservationTransactionId,
                                         ProtectedMintState sourceState,
                                         int quantity, Instant now) {
        Objects.requireNonNull(reservationTransactionId, "reservationTransactionId");
        Objects.requireNonNull(sourceState, "sourceState");
        int authorized = authorizedQuantity;
        int available = availableQuantity;
        Map<UUID, Integer> reserved = reservedQuantities;
        if (sourceState == ProtectedMintState.AUTHORIZED) {
            requireNoReservation(reservationTransactionId);
            requireQuantity(authorized, quantity, "authorized");
            authorized -= quantity;
        } else if (sourceState == ProtectedMintState.AVAILABLE) {
            requireNoReservation(reservationTransactionId);
            requireQuantity(available, quantity, "available");
            available -= quantity;
        } else if (sourceState == ProtectedMintState.RESERVED) {
            reserved = subtract(reserved, reservationTransactionId.orElseThrow(), quantity,
                    "reserved");
        } else {
            throw new IllegalStateException("Protected mint quarantine source is invalid");
        }
        return changed(authorized, available, reserved, spentQuantities,
                refundedQuantity, Math.addExact(quarantinedQuantity, quantity), now);
    }

    public int reservedQuantity() {
        return sum(reservedQuantities);
    }

    public int reservedFor(UUID transactionId) {
        return reservedQuantities.getOrDefault(transactionId, 0);
    }

    public int spentQuantity() {
        return sum(spentQuantities);
    }

    public int spentFor(UUID transactionId) {
        return spentQuantities.getOrDefault(transactionId, 0);
    }

    public int quantity(ProtectedMintState state) {
        return switch (Objects.requireNonNull(state, "state")) {
            case AUTHORIZED -> authorizedQuantity;
            case AVAILABLE -> availableQuantity;
            case RESERVED -> reservedQuantity();
            case SPENT -> spentQuantity();
            case REFUNDED -> refundedQuantity;
            case QUARANTINED -> quarantinedQuantity;
        };
    }

    public boolean isFullyMaterialized() {
        return authorizedQuantity == 0;
    }

    private static ProtectedMintBatch initial(UUID batchId, UUID transactionId,
                                              String requestKey,
                                              long denominationMinorUnits,
                                              int authorizedCount,
                                              Optional<UUID> replacementForBatchId,
                                              String serverIdentityEvidence,
                                              Instant authorizedAt,
                                              ProtectedMintEvidenceFactory evidenceFactory) {
        Objects.requireNonNull(evidenceFactory, "evidenceFactory");
        String serverEvidence = ProtectedMintText.serverEvidence(serverIdentityEvidence);
        String checksum = evidenceFactory.checksumEvidence(batchId, transactionId,
                denominationMinorUnits, authorizedCount, serverEvidence, authorizedAt);
        return new ProtectedMintBatch(batchId, transactionId, requestKey,
                denominationMinorUnits, authorizedCount, authorizedCount, 0,
                Map.of(), Map.of(), 0, 0, replacementForBatchId,
                serverEvidence, checksum, authorizedAt, authorizedAt, 0L);
    }

    private ProtectedMintBatch changed(int authorized, int available,
                                       Map<UUID, Integer> reserved,
                                       Map<UUID, Integer> spent,
                                       int refunded, int quarantined,
                                       Instant now) {
        Objects.requireNonNull(now, "now");
        if (now.isBefore(updatedAt)) {
            throw new IllegalArgumentException("Protected mint transition time moved backward");
        }
        return new ProtectedMintBatch(batchId, transactionId, authorizeRequestKey,
                denominationMinorUnits, authorizedCount, authorized, available,
                reserved, spent, refunded, quarantined, replacementForBatchId,
                serverIdentityEvidence, checksumEvidence, authorizedAt, now,
                Math.addExact(revision, 1L));
    }

    private static Map<UUID, Integer> quantityMap(Map<UUID, Integer> values, String label) {
        values = Map.copyOf(Objects.requireNonNull(values, label));
        if (values.size() > MAX_RESERVATION_ENTRIES) {
            throw new IllegalArgumentException("Protected mint transaction quantity map is too large");
        }
        for (Map.Entry<UUID, Integer> entry : values.entrySet()) {
            Objects.requireNonNull(entry.getKey(), label + " key");
            if (entry.getValue() == null || entry.getValue() <= 0) {
                throw new IllegalArgumentException("Protected mint transaction quantity is invalid");
            }
        }
        return values;
    }

    private static Map<UUID, Integer> add(Map<UUID, Integer> values, UUID transactionId,
                                          int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Protected mint quantity must be positive");
        }
        Map<UUID, Integer> changed = new HashMap<>(values);
        changed.merge(Objects.requireNonNull(transactionId, "transactionId"), quantity,
                Math::addExact);
        return Map.copyOf(changed);
    }

    private static Map<UUID, Integer> subtract(Map<UUID, Integer> values,
                                               UUID transactionId, int quantity,
                                               String label) {
        Objects.requireNonNull(transactionId, "transactionId");
        int current = values.getOrDefault(transactionId, 0);
        requireQuantity(current, quantity, label);
        Map<UUID, Integer> changed = new HashMap<>(values);
        int remaining = current - quantity;
        if (remaining == 0) {
            changed.remove(transactionId);
        } else {
            changed.put(transactionId, remaining);
        }
        return Map.copyOf(changed);
    }

    private static int sum(Map<UUID, Integer> values) {
        int total = 0;
        for (int quantity : values.values()) {
            total = Math.addExact(total, quantity);
        }
        return total;
    }

    private static void requireQuantity(int available, int requested, String label) {
        if (requested <= 0 || requested > available) {
            throw new IllegalStateException("Protected mint " + label + " quantity is insufficient");
        }
    }

    private static void requireNoReservation(Optional<UUID> reservationTransactionId) {
        if (reservationTransactionId.isPresent()) {
            throw new IllegalArgumentException("Protected mint source must not have a reservation");
        }
    }
}

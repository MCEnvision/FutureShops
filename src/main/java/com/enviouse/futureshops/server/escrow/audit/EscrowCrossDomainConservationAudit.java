package com.enviouse.futureshops.server.escrow.audit;

import com.enviouse.futureshops.server.escrow.claim.ClaimLiabilityCategory;
import com.enviouse.futureshops.server.escrow.claim.ClaimLiabilityEntry;
import com.enviouse.futureshops.server.escrow.claim.ClaimLiabilitySnapshot;
import com.enviouse.futureshops.server.escrow.claim.ClaimSavedData;
import com.enviouse.futureshops.server.escrow.custody.CustodyAssetType;
import com.enviouse.futureshops.server.escrow.custody.CustodyHeldLiability;
import com.enviouse.futureshops.server.escrow.custody.CustodyLiabilitySnapshot;
import com.enviouse.futureshops.server.escrow.custody.CustodySavedData;
import com.enviouse.futureshops.server.escrow.custody.ProtectedCurrencyProvenance;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountId;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountType;
import com.enviouse.futureshops.server.escrow.ledger.LedgerSavedData;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintBatchLiability;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintLiabilitySnapshot;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintSavedData;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

public final class EscrowCrossDomainConservationAudit {
    public static final String PLAYER_RESERVED = "PLAYER_RESERVED";
    public static final String PLAYER_CLAIM = "PLAYER_CLAIM";
    public static final String PROTECTED_CURRENCY_OUTSTANDING =
            "PROTECTED_CURRENCY_OUTSTANDING";

    private EscrowCrossDomainConservationAudit() {
    }

    public static EscrowConservationReport verify(LedgerSavedData ledger,
                                                  ClaimSavedData claims,
                                                  CustodySavedData custody,
                                                  ProtectedMintSavedData protectedMints) {
        Objects.requireNonNull(ledger, "ledger");
        Objects.requireNonNull(claims, "claims");
        Objects.requireNonNull(custody, "custody");
        Objects.requireNonNull(protectedMints, "protectedMints");
        return verify(ledger.snapshotBalances(), claims.liabilitySnapshot(),
                custody.liabilitySnapshot(), protectedMints.liabilitySnapshot());
    }

    public static EscrowConservationReport verify(
            Map<LedgerAccountId, Long> ledgerBalances,
            ClaimLiabilitySnapshot claims,
            CustodyLiabilitySnapshot custody,
            ProtectedMintLiabilitySnapshot protectedMints
    ) {
        Objects.requireNonNull(ledgerBalances, "ledgerBalances");
        Objects.requireNonNull(claims, "claims");
        Objects.requireNonNull(custody, "custody");
        Objects.requireNonNull(protectedMints, "protectedMints");
        List<EscrowConservationViolation> violations = new ArrayList<>();

        CheckedAccumulator ledgerNet = new CheckedAccumulator(
                "Ledger net minor units", violations);
        Map<LedgerAccountType, CheckedAccumulator> ledgerByType = new EnumMap<>(
                LedgerAccountType.class);
        for (Map.Entry<LedgerAccountId, Long> entry : sortedLedger(ledgerBalances)) {
            Objects.requireNonNull(entry.getKey(), "ledger account");
            long balance = Objects.requireNonNull(entry.getValue(), "ledger balance");
            ledgerNet.add(balance);
            ledgerByType.computeIfAbsent(entry.getKey().type(), type ->
                    new CheckedAccumulator("Ledger " + type + " minor units", violations))
                    .add(balance);
        }
        OptionalLong ledgerNetValue = ledgerNet.value();
        if (ledgerNetValue.isPresent() && ledgerNetValue.getAsLong() != 0L) {
            violations.add(violation(EscrowConservationViolationCode.LEDGER_NOT_ZERO,
                    "Ledger", 0L, ledgerNetValue.getAsLong(),
                    "Escrow ledger balances do not sum to zero"));
        }

        CheckedAccumulator monetaryClaims = new CheckedAccumulator(
                "Unfinished monetary claim minor units", violations);
        Map<ClaimLiabilityCategory, CheckedAccumulator> unverifiedClaims =
                new EnumMap<>(ClaimLiabilityCategory.class);
        for (ClaimLiabilityEntry claim : sortedClaims(claims.unfinishedClaims())) {
            if (claim.category().monetary()) {
                monetaryClaims.add(claim.remainingUnits());
            } else {
                unverifiedClaims.computeIfAbsent(claim.category(), category ->
                        new CheckedAccumulator("Unverified claim " + category, violations))
                        .add(claim.remainingUnits());
            }
        }

        CheckedAccumulator walletCustody = new CheckedAccumulator(
                "Held wallet custody minor units", violations);
        CheckedAccumulator foreignCustody = new CheckedAccumulator(
                "Unverified foreign custody minor units", violations);
        CheckedAccumulator itemCustody = new CheckedAccumulator(
                "Unverified item custody units", violations);
        Map<ProtectedReservationKey, CheckedAccumulator> custodyReservations =
                new HashMap<>();
        Map<ProtectedReservationKey, Boolean> reservationEvidence = new HashMap<>();

        Map<UUID, ProtectedMintBatchLiability> mintByBatch = new HashMap<>();
        Map<ProtectedReservationKey, CheckedAccumulator> mintReservations = new HashMap<>();
        CheckedAccumulator mintOutstanding = new CheckedAccumulator(
                "Protected mint outstanding minor units", violations);
        for (ProtectedMintBatchLiability batch : sortedBatches(protectedMints.batches())) {
            ProtectedMintBatchLiability old = mintByBatch.putIfAbsent(batch.batchId(), batch);
            if (old != null) {
                violations.add(violation(
                        EscrowConservationViolationCode.PROTECTED_MINT_BATCH_DUPLICATED,
                        batch.batchId().toString(),
                        "Protected mint liability snapshot repeats a batch"));
            }
            CheckedAccumulator outstandingQuantity = new CheckedAccumulator(
                    "Protected mint batch outstanding quantity " + batch.batchId(),
                    violations);
            outstandingQuantity.add(batch.authorizedQuantity());
            outstandingQuantity.add(batch.availableQuantity());
            for (Map.Entry<UUID, Integer> reservation : sortedReservations(
                    batch.reservedQuantities())) {
                outstandingQuantity.add(reservation.getValue());
                ProtectedReservationKey key = new ProtectedReservationKey(
                        batch.batchId(), reservation.getKey());
                mintReservations.computeIfAbsent(key, ignored -> new CheckedAccumulator(
                        "Protected mint reservation quantity " + key, violations))
                        .add(reservation.getValue());
            }
            outstandingQuantity.value().ifPresent(value ->
                    mintOutstanding.addProduct(batch.denominationMinorUnits(), value));
        }

        for (CustodyHeldLiability held : sortedCustody(custody.heldLiabilities())) {
            if (held.assetType() == CustodyAssetType.WALLET_RESERVE) {
                walletCustody.add(held.units());
            } else if (held.assetType() == CustodyAssetType.ITEM_STACK) {
                itemCustody.add(held.units());
            } else if (held.assetType() == CustodyAssetType.FOREIGN_PHYSICAL_CURRENCY) {
                foreignCustody.add(held.units());
            } else {
                auditProtectedCustody(held, mintByBatch, custodyReservations,
                        reservationEvidence, violations);
            }
        }

        List<EscrowLiabilityComparison> liabilityComparisons = new ArrayList<>();
        addLiabilityComparison(PLAYER_RESERVED,
                ledgerValue(ledgerByType, LedgerAccountType.PLAYER_RESERVED),
                walletCustody.value(), liabilityComparisons, violations);
        addLiabilityComparison(PLAYER_CLAIM,
                ledgerValue(ledgerByType, LedgerAccountType.PLAYER_CLAIM),
                monetaryClaims.value(), liabilityComparisons, violations);
        addLiabilityComparison(PROTECTED_CURRENCY_OUTSTANDING,
                ledgerValue(ledgerByType,
                        LedgerAccountType.PROTECTED_CURRENCY_OUTSTANDING),
                mintOutstanding.value(), liabilityComparisons, violations);

        Map<ProtectedReservationKey, ProtectedReservationComparison> comparisons =
                compareReservations(mintReservations, custodyReservations,
                        reservationEvidence, violations);

        if (!custody.locallyConserved()) {
            for (String detail : custody.localViolations().stream().sorted().toList()) {
                violations.add(violation(
                        EscrowConservationViolationCode.CUSTODY_LOCAL_CORRUPTION,
                        "Custody", detail));
            }
        }
        if (!protectedMints.locallyConserved()) {
            for (String detail : protectedMints.localViolations().stream().sorted().toList()) {
                violations.add(violation(
                        EscrowConservationViolationCode.PROTECTED_MINT_LOCAL_CORRUPTION,
                        "Protected mint", detail));
            }
        }

        List<EscrowUnverifiedCategory> unverified = unverifiedCategories(
                itemCustody, foreignCustody, unverifiedClaims);
        String fingerprint = fingerprint(ledgerBalances, claims, custody, protectedMints);
        return new EscrowConservationReport(violations.isEmpty(), ledgerNetValue,
                liabilityComparisons, comparisons, custody.locallyConserved(),
                protectedMints.locallyConserved(), unverified, violations, fingerprint);
    }

    private static void auditProtectedCustody(
            CustodyHeldLiability held,
            Map<UUID, ProtectedMintBatchLiability> mintByBatch,
            Map<ProtectedReservationKey, CheckedAccumulator> custodyReservations,
            Map<ProtectedReservationKey, Boolean> reservationEvidence,
            List<EscrowConservationViolation> violations
    ) {
        CheckedAccumulator lotValue = new CheckedAccumulator(
                "Protected custody lot value " + held.lotId(), violations);
        Set<UUID> seenBatches = new HashSet<>();
        for (ProtectedCurrencyProvenance provenance : held.protectedProvenance()) {
            ProtectedReservationKey key = new ProtectedReservationKey(
                    provenance.mintId(), held.transactionId());
            custodyReservations.computeIfAbsent(key, ignored -> new CheckedAccumulator(
                    "Protected custody reservation quantity " + key, violations))
                    .add(provenance.billCount());
            lotValue.addProduct(provenance.denominationMinorUnits(), provenance.billCount());
            boolean valid = true;
            if (!seenBatches.add(provenance.mintId())) {
                violations.add(violation(
                        EscrowConservationViolationCode.PROTECTED_CUSTODY_BATCH_DUPLICATED,
                        held.lotId().toString(),
                        "Protected custody lot repeats a mint batch"));
                valid = false;
            }
            ProtectedMintBatchLiability batch = mintByBatch.get(provenance.mintId());
            if (batch == null) {
                violations.add(violation(
                        EscrowConservationViolationCode.PROTECTED_CUSTODY_BATCH_MISSING,
                        key.toString(),
                        "Protected custody references an unknown mint batch"));
                valid = false;
            } else {
                valid &= validateProtectedEvidence(held, provenance, batch, violations);
            }
            reservationEvidence.merge(key, valid, (first, second) -> first && second);
        }
        OptionalLong observedValue = lotValue.value();
        if (observedValue.isPresent() && observedValue.getAsLong() != held.units()) {
            violations.add(violation(
                    EscrowConservationViolationCode.PROTECTED_CUSTODY_VALUE_MISMATCH,
                    held.lotId().toString(), held.units(), observedValue.getAsLong(),
                    "Protected custody provenance does not equal its held value"));
        }
    }

    private static boolean validateProtectedEvidence(
            CustodyHeldLiability held,
            ProtectedCurrencyProvenance provenance,
            ProtectedMintBatchLiability batch,
            List<EscrowConservationViolation> violations
    ) {
        boolean valid = true;
        String subject = held.lotId() + "." + provenance.mintId();
        if (provenance.denominationMinorUnits() != batch.denominationMinorUnits()) {
            violations.add(violation(
                    EscrowConservationViolationCode.PROTECTED_CUSTODY_DENOMINATION_MISMATCH,
                    subject, batch.denominationMinorUnits(),
                    provenance.denominationMinorUnits(),
                    "Protected custody denomination does not match its mint batch"));
            valid = false;
        }
        if (provenance.authorizedCount() != batch.authorizedCount()) {
            violations.add(violation(
                    EscrowConservationViolationCode.PROTECTED_CUSTODY_AUTHORIZATION_MISMATCH,
                    subject, batch.authorizedCount(), provenance.authorizedCount(),
                    "Protected custody authorization does not match its mint batch"));
            valid = false;
        }
        if (!evidenceEqual(batch.serverIdentityEvidence(),
                provenance.serverIdentityEvidence())) {
            violations.add(violation(
                    EscrowConservationViolationCode.PROTECTED_CUSTODY_SERVER_EVIDENCE_MISMATCH,
                    subject,
                    "Protected custody server evidence does not match its mint batch"));
            valid = false;
        }
        if (!evidenceEqual(batch.checksumEvidence(), provenance.checksumEvidence())) {
            violations.add(violation(
                    EscrowConservationViolationCode.PROTECTED_CUSTODY_CHECKSUM_MISMATCH,
                    subject,
                    "Protected custody checksum does not match its mint batch"));
            valid = false;
        }
        return valid;
    }

    private static Map<ProtectedReservationKey, ProtectedReservationComparison>
    compareReservations(
            Map<ProtectedReservationKey, CheckedAccumulator> mintReservations,
            Map<ProtectedReservationKey, CheckedAccumulator> custodyReservations,
            Map<ProtectedReservationKey, Boolean> evidence,
            List<EscrowConservationViolation> violations
    ) {
        Set<ProtectedReservationKey> keys = new LinkedHashSet<>();
        keys.addAll(mintReservations.keySet());
        keys.addAll(custodyReservations.keySet());
        List<ProtectedReservationKey> sorted = keys.stream()
                .sorted(reservationKeyComparator()).toList();
        Map<ProtectedReservationKey, ProtectedReservationComparison> result =
                new LinkedHashMap<>();
        for (ProtectedReservationKey key : sorted) {
            OptionalLong mint = accumulatorValue(mintReservations.get(key));
            OptionalLong held = accumulatorValue(custodyReservations.get(key));
            boolean evidenceValid = evidence.getOrDefault(key,
                    !custodyReservations.containsKey(key));
            boolean quantityMatches = mint.isPresent() && held.isPresent()
                    && mint.getAsLong() == held.getAsLong();
            ProtectedReservationComparison comparison = new ProtectedReservationComparison(
                    key, mint, held, evidenceValid, quantityMatches);
            result.put(key, comparison);
            if (!quantityMatches) {
                violations.add(new EscrowConservationViolation(
                        EscrowConservationViolationCode.PROTECTED_RESERVATION_QUANTITY_MISMATCH,
                        key.toString(), mint, held,
                        "Protected mint reservation does not equal held custody quantity"));
            }
        }
        return result;
    }

    private static OptionalLong accumulatorValue(CheckedAccumulator accumulator) {
        return accumulator == null ? OptionalLong.of(0L) : accumulator.value();
    }

    private static void addLiabilityComparison(
            String liability,
            OptionalLong ledger,
            OptionalLong authoritative,
            List<EscrowLiabilityComparison> comparisons,
            List<EscrowConservationViolation> violations
    ) {
        boolean matches = ledger.isPresent() && authoritative.isPresent()
                && ledger.getAsLong() == authoritative.getAsLong();
        comparisons.add(new EscrowLiabilityComparison(
                liability, ledger, authoritative, matches));
        if (!matches) {
            violations.add(new EscrowConservationViolation(
                    EscrowConservationViolationCode.LIABILITY_MISMATCH,
                    liability, authoritative, ledger,
                    "Ledger liability does not equal authoritative materialized liability"));
        }
    }

    private static OptionalLong ledgerValue(
            Map<LedgerAccountType, CheckedAccumulator> ledgerByType,
            LedgerAccountType type
    ) {
        CheckedAccumulator accumulator = ledgerByType.get(type);
        return accumulator == null ? OptionalLong.of(0L) : accumulator.value();
    }

    private static List<EscrowUnverifiedCategory> unverifiedCategories(
            CheckedAccumulator itemCustody,
            CheckedAccumulator foreignCustody,
            Map<ClaimLiabilityCategory, CheckedAccumulator> claims
    ) {
        List<EscrowUnverifiedCategory> result = new ArrayList<>();
        result.add(new EscrowUnverifiedCategory(
                "CUSTODY_ITEMS", itemCustody.value(), "item units",
                "Item custody has no authoritative monetary ledger mapping"));
        result.add(new EscrowUnverifiedCategory(
                "CUSTODY_FOREIGN_CURRENCY", foreignCustody.value(), "minor units",
                "Foreign currency custody is controlled by another mod"));
        addUnverifiedClaim(result, claims, ClaimLiabilityCategory.ITEM,
                "CLAIM_ITEMS", "item units");
        addUnverifiedClaim(result, claims, ClaimLiabilityCategory.PROTECTED_CASH,
                "CLAIM_PROTECTED_CASH", "minor units");
        addUnverifiedClaim(result, claims, ClaimLiabilityCategory.FOREIGN_CASH,
                "CLAIM_FOREIGN_CASH", "minor units");
        addUnverifiedClaim(result, claims, ClaimLiabilityCategory.BARTER_ITEM,
                "CLAIM_BARTER_ITEMS", "item units");
        addUnverifiedClaim(result, claims, ClaimLiabilityCategory.ITEM_REFUND,
                "CLAIM_ITEM_REFUNDS", "claim units");
        return result;
    }

    private static void addUnverifiedClaim(
            List<EscrowUnverifiedCategory> result,
            Map<ClaimLiabilityCategory, CheckedAccumulator> claims,
            ClaimLiabilityCategory category,
            String name,
            String unit
    ) {
        result.add(new EscrowUnverifiedCategory(name,
                accumulatorValue(claims.get(category)), unit,
                "Claim payload units have no authoritative monetary ledger mapping"));
    }

    private static List<Map.Entry<LedgerAccountId, Long>> sortedLedger(
            Map<LedgerAccountId, Long> balances
    ) {
        return balances.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator
                        .comparing((LedgerAccountId value) -> value.type().ordinal())
                        .thenComparing(LedgerAccountId::ownerKey)))
                .toList();
    }

    private static List<ClaimLiabilityEntry> sortedClaims(
            List<ClaimLiabilityEntry> claims
    ) {
        return claims.stream().sorted(Comparator
                .comparing((ClaimLiabilityEntry value) -> value.claimId().toString())
                .thenComparing(value -> value.transactionId().toString()))
                .toList();
    }

    private static List<CustodyHeldLiability> sortedCustody(
            List<CustodyHeldLiability> liabilities
    ) {
        return liabilities.stream().sorted(Comparator
                .comparing((CustodyHeldLiability value) -> value.lotId().toString())
                .thenComparing(value -> value.transactionId().toString()))
                .toList();
    }

    private static List<ProtectedMintBatchLiability> sortedBatches(
            List<ProtectedMintBatchLiability> batches
    ) {
        return batches.stream().sorted(Comparator
                .comparing((ProtectedMintBatchLiability value) ->
                        value.batchId().toString())
                .thenComparingLong(ProtectedMintBatchLiability::denominationMinorUnits))
                .toList();
    }

    private static List<Map.Entry<UUID, Integer>> sortedReservations(
            Map<UUID, Integer> reservations
    ) {
        return reservations.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(
                        Comparator.comparing(UUID::toString)))
                .toList();
    }

    private static Comparator<ProtectedReservationKey> reservationKeyComparator() {
        return Comparator.comparing(
                        (ProtectedReservationKey key) -> key.mintBatchId().toString())
                .thenComparing(key -> key.transactionId().toString());
    }

    private static boolean evidenceEqual(String expected, String observed) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                observed.getBytes(StandardCharsets.UTF_8));
    }

    private static EscrowConservationViolation violation(
            EscrowConservationViolationCode code,
            String subject,
            String detail
    ) {
        return new EscrowConservationViolation(code, subject,
                OptionalLong.empty(), OptionalLong.empty(), detail);
    }

    private static EscrowConservationViolation violation(
            EscrowConservationViolationCode code,
            String subject,
            long expected,
            long actual,
            String detail
    ) {
        return new EscrowConservationViolation(code, subject,
                OptionalLong.of(expected), OptionalLong.of(actual), detail);
    }

    private static String fingerprint(
            Map<LedgerAccountId, Long> ledgerBalances,
            ClaimLiabilitySnapshot claims,
            CustodyLiabilitySnapshot custody,
            ProtectedMintLiabilitySnapshot protectedMints
    ) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (DataOutputStream output = new DataOutputStream(new DigestOutputStream(
                    OutputStream.nullOutputStream(), digest))) {
                output.writeInt(0x46534341);
                output.writeInt(1);
                writeLedgerFingerprint(output, ledgerBalances);
                writeClaimFingerprint(output, claims);
                writeCustodyFingerprint(output, custody);
                writeMintFingerprint(output, protectedMints);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException | IOException exception) {
            throw new IllegalStateException(
                    "Unable to fingerprint escrow conservation input", exception);
        }
    }

    private static void writeLedgerFingerprint(DataOutputStream output,
                                               Map<LedgerAccountId, Long> balances)
            throws IOException {
        List<Map.Entry<LedgerAccountId, Long>> entries = sortedLedger(balances);
        output.writeInt(entries.size());
        for (Map.Entry<LedgerAccountId, Long> entry : entries) {
            output.writeInt(entry.getKey().type().ordinal());
            writeString(output, entry.getKey().ownerKey());
            output.writeLong(entry.getValue());
        }
    }

    private static void writeClaimFingerprint(DataOutputStream output,
                                              ClaimLiabilitySnapshot snapshot)
            throws IOException {
        List<ClaimLiabilityEntry> claims = sortedClaims(snapshot.unfinishedClaims());
        output.writeInt(claims.size());
        for (ClaimLiabilityEntry claim : claims) {
            writeUuid(output, claim.claimId());
            writeUuid(output, claim.transactionId());
            output.writeInt(claim.category().ordinal());
            output.writeInt(claim.status().ordinal());
            output.writeLong(claim.remainingUnits());
        }
    }

    private static void writeCustodyFingerprint(DataOutputStream output,
                                                CustodyLiabilitySnapshot snapshot)
            throws IOException {
        List<CustodyHeldLiability> held = sortedCustody(snapshot.heldLiabilities());
        output.writeInt(held.size());
        for (CustodyHeldLiability liability : held) {
            writeUuid(output, liability.lotId());
            writeUuid(output, liability.transactionId());
            output.writeInt(liability.assetType().ordinal());
            output.writeLong(liability.units());
            writeString(output, liability.currencyProvider());
            List<ProtectedCurrencyProvenance> provenance =
                    liability.protectedProvenance().stream()
                            .sorted(Comparator.comparing(value -> value.mintId().toString()))
                            .toList();
            output.writeInt(provenance.size());
            for (ProtectedCurrencyProvenance value : provenance) {
                writeUuid(output, value.mintId());
                output.writeLong(value.denominationMinorUnits());
                output.writeInt(value.authorizedCount());
                output.writeInt(value.billCount());
                writeString(output, value.serverIdentityEvidence());
                writeString(output, value.checksumEvidence());
            }
        }
        writeLocalFingerprint(output, snapshot.locallyConserved(),
                snapshot.localViolations());
    }

    private static void writeMintFingerprint(DataOutputStream output,
                                             ProtectedMintLiabilitySnapshot snapshot)
            throws IOException {
        List<ProtectedMintBatchLiability> batches = sortedBatches(snapshot.batches());
        output.writeInt(batches.size());
        for (ProtectedMintBatchLiability batch : batches) {
            writeUuid(output, batch.batchId());
            output.writeLong(batch.denominationMinorUnits());
            output.writeInt(batch.authorizedCount());
            output.writeInt(batch.authorizedQuantity());
            output.writeInt(batch.availableQuantity());
            List<Map.Entry<UUID, Integer>> reservations = sortedReservations(
                    batch.reservedQuantities());
            output.writeInt(reservations.size());
            for (Map.Entry<UUID, Integer> reservation : reservations) {
                writeUuid(output, reservation.getKey());
                output.writeInt(reservation.getValue());
            }
            writeString(output, batch.serverIdentityEvidence());
            writeString(output, batch.checksumEvidence());
        }
        writeLocalFingerprint(output, snapshot.locallyConserved(),
                snapshot.localViolations());
    }

    private static void writeLocalFingerprint(DataOutputStream output,
                                              boolean conserved,
                                              List<String> violations) throws IOException {
        output.writeBoolean(conserved);
        List<String> sorted = violations.stream().sorted().toList();
        output.writeInt(sorted.size());
        for (String violation : sorted) {
            writeString(output, violation);
        }
    }

    private static void writeUuid(DataOutputStream output, UUID value) throws IOException {
        output.writeLong(value.getMostSignificantBits());
        output.writeLong(value.getLeastSignificantBits());
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static final class CheckedAccumulator {
        private final String subject;
        private final List<EscrowConservationViolation> violations;
        private long amount;
        private boolean overflow;

        private CheckedAccumulator(String subject,
                                   List<EscrowConservationViolation> violations) {
            this.subject = Objects.requireNonNull(subject, "subject");
            this.violations = Objects.requireNonNull(violations, "violations");
        }

        private void add(long value) {
            if (overflow) {
                return;
            }
            try {
                amount = Math.addExact(amount, value);
            } catch (ArithmeticException exception) {
                markOverflow();
            }
        }

        private void addProduct(long first, long second) {
            if (overflow) {
                return;
            }
            try {
                add(Math.multiplyExact(first, second));
            } catch (ArithmeticException exception) {
                markOverflow();
            }
        }

        private OptionalLong value() {
            return overflow ? OptionalLong.empty() : OptionalLong.of(amount);
        }

        private void markOverflow() {
            if (!overflow) {
                overflow = true;
                violations.add(violation(
                        EscrowConservationViolationCode.ARITHMETIC_OVERFLOW,
                        subject, "Escrow conservation arithmetic overflowed"));
            }
        }
    }
}

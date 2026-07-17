package com.enviouse.futureshops.server.escrow.custody;

import java.io.DataOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record CustodyLot(
        UUID lotId,
        UUID transactionId,
        String reserveRequestKey,
        CustodyAssetType assetType,
        CustodyProtectionTier protectionTier,
        CustodyAdapterCapability sourceCapability,
        CustodyLotState state,
        long units,
        String currencyProvider,
        List<CustodyItemSnapshot> itemSnapshots,
        List<ProtectedCurrencyProvenance> protectedProvenance,
        byte[] assetFingerprint,
        CustodyTransferEvidence holdEvidence,
        Instant createdAt,
        Instant updatedAt,
        long revision
) {
    public static final String BUILT_IN_CURRENCY_PROVIDER = "futureshops";
    public static final int MAX_REQUEST_KEY_LENGTH = 256;
    public static final int MAX_PROVIDER_LENGTH = 256;
    public static final int MAX_SNAPSHOTS = 4096;
    public static final int MAX_TOTAL_NBT_BYTES = 4_194_304;

    public CustodyLot {
        Objects.requireNonNull(lotId, "lotId");
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(reserveRequestKey, "reserveRequestKey");
        Objects.requireNonNull(assetType, "assetType");
        Objects.requireNonNull(protectionTier, "protectionTier");
        Objects.requireNonNull(sourceCapability, "sourceCapability");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(currencyProvider, "currencyProvider");
        Objects.requireNonNull(itemSnapshots, "itemSnapshots");
        Objects.requireNonNull(protectedProvenance, "protectedProvenance");
        Objects.requireNonNull(assetFingerprint, "assetFingerprint");
        Objects.requireNonNull(holdEvidence, "holdEvidence");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        reserveRequestKey = requireRequestKey(reserveRequestKey);
        currencyProvider = currencyProvider.strip();
        itemSnapshots = List.copyOf(itemSnapshots);
        protectedProvenance = List.copyOf(protectedProvenance);
        assetFingerprint = assetFingerprint.clone();
        CustodyHashes.requireHash(assetFingerprint, "Custody asset fingerprint");
        if (units <= 0L) {
            throw new IllegalArgumentException("Custody units must be positive");
        }
        if (currencyProvider.length() > MAX_PROVIDER_LENGTH) {
            throw new IllegalArgumentException("Custody currency provider is too long");
        }
        if (revision < 0L || updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("Invalid custody lot timeline");
        }
        if (state == CustodyLotState.HELD && revision != 0L) {
            throw new IllegalArgumentException("Held custody lots must have revision zero");
        }
        if (state.isTerminal() && revision < 1L) {
            throw new IllegalArgumentException("Terminal custody lots require a revision");
        }
        validateSnapshots(itemSnapshots);
        validateShape(assetType, protectionTier, sourceCapability, units, currencyProvider,
                itemSnapshots, protectedProvenance);
        if (holdEvidence.source().capability() != sourceCapability) {
            throw new IllegalArgumentException("Custody source capability does not match its evidence");
        }
        byte[] expected = fingerprint(assetType, protectionTier, units, currencyProvider,
                itemSnapshots, protectedProvenance);
        if (!CustodyHashes.equal(expected, assetFingerprint)) {
            throw new IllegalArgumentException("Custody lot fingerprint does not match its assets");
        }
    }

    public static CustodyLot held(UUID lotId,
                                  UUID transactionId,
                                  String reserveRequestKey,
                                  CustodyAssetType assetType,
                                  CustodyProtectionTier protectionTier,
                                  long units,
                                  String currencyProvider,
                                  List<CustodyItemSnapshot> itemSnapshots,
                                  List<ProtectedCurrencyProvenance> protectedProvenance,
                                  CustodyTransferEvidence holdEvidence,
                                  Instant now) {
        Objects.requireNonNull(holdEvidence, "holdEvidence");
        return new CustodyLot(lotId, transactionId, reserveRequestKey, assetType, protectionTier,
                holdEvidence.source().capability(), CustodyLotState.HELD, units, currencyProvider,
                itemSnapshots, protectedProvenance,
                fingerprint(assetType, protectionTier, units, currencyProvider.strip(),
                        itemSnapshots, protectedProvenance),
                holdEvidence, now, now, 0L);
    }

    @Override
    public byte[] assetFingerprint() {
        return assetFingerprint.clone();
    }

    public CustodyLot transition(CustodyLotState nextState, Instant now) {
        Objects.requireNonNull(nextState, "nextState");
        Objects.requireNonNull(now, "now");
        if (state != CustodyLotState.HELD || nextState == CustodyLotState.HELD) {
            throw new CustodyConflictException("Invalid custody lot state transition");
        }
        if (now.isBefore(updatedAt)) {
            throw new CustodyConflictException("Custody transition time cannot move backward");
        }
        return new CustodyLot(lotId, transactionId, reserveRequestKey, assetType, protectionTier,
                sourceCapability, nextState, units, currencyProvider, itemSnapshots,
                protectedProvenance, assetFingerprint, holdEvidence, createdAt, now,
                Math.addExact(revision, 1L));
    }

    public boolean hasSameAssets(List<CustodyItemSnapshot> observedSnapshots) {
        Objects.requireNonNull(observedSnapshots, "observedSnapshots");
        byte[] observed = fingerprint(assetType, protectionTier, units, currencyProvider,
                observedSnapshots, protectedProvenance);
        return CustodyHashes.equal(assetFingerprint, observed);
    }

    static String requireRequestKey(String requestKey) {
        String normalized = Objects.requireNonNull(requestKey, "requestKey").strip();
        if (normalized.isEmpty() || normalized.length() > MAX_REQUEST_KEY_LENGTH) {
            throw new IllegalArgumentException("Invalid custody request key");
        }
        CustodyHashes.strictUtf8(normalized);
        return normalized;
    }

    static byte[] fingerprint(CustodyAssetType type,
                              CustodyProtectionTier tier,
                              long units,
                              String provider,
                              List<CustodyItemSnapshot> snapshots,
                              List<ProtectedCurrencyProvenance> provenance) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(tier, "tier");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(snapshots, "snapshots");
        Objects.requireNonNull(provenance, "provenance");
        return CustodyHashes.encodeAndHash(output -> {
            CustodyHashes.writeString(output, type.name());
            CustodyHashes.writeString(output, tier.name());
            output.writeLong(units);
            CustodyHashes.writeString(output, provider);
            output.writeInt(snapshots.size());
            for (CustodyItemSnapshot snapshot : snapshots) {
                CustodyHashes.writeBytes(output, snapshot.contentHash());
            }
            output.writeInt(provenance.size());
            for (ProtectedCurrencyProvenance value : provenance) {
                writeProvenance(output, value);
            }
        });
    }

    private static void writeProvenance(DataOutputStream output,
                                        ProtectedCurrencyProvenance value) throws IOException {
        output.writeLong(value.mintId().getMostSignificantBits());
        output.writeLong(value.mintId().getLeastSignificantBits());
        output.writeLong(value.denominationMinorUnits());
        output.writeInt(value.authorizedCount());
        output.writeInt(value.billCount());
        CustodyHashes.writeString(output, value.serverIdentityEvidence());
        CustodyHashes.writeString(output, value.checksumEvidence());
    }

    private static void validateSnapshots(List<CustodyItemSnapshot> snapshots) {
        if (snapshots.size() > MAX_SNAPSHOTS) {
            throw new IllegalArgumentException("Custody item snapshot count exceeds bounds");
        }
        long bytes = 0L;
        for (CustodyItemSnapshot snapshot : snapshots) {
            Objects.requireNonNull(snapshot, "snapshot");
            bytes = Math.addExact(bytes, snapshot.serializedNbt().length);
            if (bytes > MAX_TOTAL_NBT_BYTES) {
                throw new IllegalArgumentException("Custody item NBT total exceeds bounds");
            }
        }
    }

    private static void validateShape(CustodyAssetType type,
                                      CustodyProtectionTier tier,
                                      CustodyAdapterCapability capability,
                                      long units,
                                      String provider,
                                      List<CustodyItemSnapshot> snapshots,
                                      List<ProtectedCurrencyProvenance> provenance) {
        if (type.requiresItemSnapshots() != !snapshots.isEmpty()) {
            throw new IllegalArgumentException("Custody item snapshot shape does not match its asset type");
        }
        if (type == CustodyAssetType.WALLET_RESERVE) {
            if (tier != CustodyProtectionTier.PROTECTED
                    || capability != CustodyAdapterCapability.TRANSACTIONAL_PROTECTED
                    || !BUILT_IN_CURRENCY_PROVIDER.equals(provider)
                    || !provenance.isEmpty()) {
                throw new IllegalArgumentException("Wallet custody requires protected FutureShops storage");
            }
            return;
        }
        long snapshotUnits = snapshots.stream().mapToLong(CustodyItemSnapshot::count)
                .reduce(0L, Math::addExact);
        if (type == CustodyAssetType.ITEM_STACK) {
            if (units != snapshotUnits || !provider.isEmpty() || !provenance.isEmpty()
                    || tier == CustodyProtectionTier.UNPROTECTED_FOREIGN
                    || capability == CustodyAdapterCapability.UNPROTECTED_EXTERNAL) {
                throw new IllegalArgumentException("Item custody has inconsistent protection or quantity");
            }
            return;
        }
        if (type == CustodyAssetType.PROTECTED_PHYSICAL_CURRENCY) {
            if (tier != CustodyProtectionTier.PROTECTED
                    || capability == CustodyAdapterCapability.UNPROTECTED_EXTERNAL
                    || !BUILT_IN_CURRENCY_PROVIDER.equals(provider)
                    || provenance.isEmpty()) {
                throw new IllegalArgumentException("Protected currency custody requires FutureShops provenance");
            }
            Set<UUID> mintIds = new HashSet<>();
            long value = 0L;
            long billCount = 0L;
            for (ProtectedCurrencyProvenance entry : provenance) {
                if (!mintIds.add(entry.mintId())) {
                    throw new IllegalArgumentException("Protected currency mint IDs must be unique");
                }
                value = Math.addExact(value, entry.totalMinorUnits());
                billCount = Math.addExact(billCount, entry.billCount());
            }
            if (value != units || billCount != snapshotUnits) {
                throw new IllegalArgumentException("Protected currency provenance does not balance its lot");
            }
            return;
        }
        if (tier != CustodyProtectionTier.UNPROTECTED_FOREIGN
                || capability != CustodyAdapterCapability.UNPROTECTED_EXTERNAL
                || provider.isEmpty()
                || BUILT_IN_CURRENCY_PROVIDER.equals(provider)
                || !provenance.isEmpty()) {
            throw new IllegalArgumentException("Foreign currency must use unprotected foreign custody");
        }
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof CustodyLot other)) {
            return false;
        }
        return units == other.units
                && revision == other.revision
                && lotId.equals(other.lotId)
                && transactionId.equals(other.transactionId)
                && reserveRequestKey.equals(other.reserveRequestKey)
                && assetType == other.assetType
                && protectionTier == other.protectionTier
                && sourceCapability == other.sourceCapability
                && state == other.state
                && currencyProvider.equals(other.currencyProvider)
                && itemSnapshots.equals(other.itemSnapshots)
                && protectedProvenance.equals(other.protectedProvenance)
                && Arrays.equals(assetFingerprint, other.assetFingerprint)
                && holdEvidence.equals(other.holdEvidence)
                && createdAt.equals(other.createdAt)
                && updatedAt.equals(other.updatedAt);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hash(lotId, transactionId, reserveRequestKey, assetType,
                protectionTier, sourceCapability, state, units, currencyProvider,
                itemSnapshots, protectedProvenance, holdEvidence, createdAt, updatedAt, revision)
                + Arrays.hashCode(assetFingerprint);
    }
}

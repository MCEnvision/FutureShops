package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.init.ModItems;
import com.enviouse.futureshops.money.ItemStackSnapshotCodec;
import com.enviouse.futureshops.money.ProtectedMoneyMintBridge;
import com.enviouse.futureshops.server.escrow.claim.ClaimKind;
import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.custody.CashClaimCustodySupport;
import com.enviouse.futureshops.server.escrow.custody.CustodyAdapterCapability;
import com.enviouse.futureshops.server.escrow.custody.CustodyAssetType;
import com.enviouse.futureshops.server.escrow.custody.CustodyBatchPlan;
import com.enviouse.futureshops.server.escrow.custody.CustodyEndpointEvidence;
import com.enviouse.futureshops.server.escrow.custody.CustodyItemSnapshot;
import com.enviouse.futureshops.server.escrow.custody.CustodyLot;
import com.enviouse.futureshops.server.escrow.custody.CustodyOperation;
import com.enviouse.futureshops.server.escrow.custody.CustodyProtectionTier;
import com.enviouse.futureshops.server.escrow.custody.CustodyTransferEvidence;
import com.enviouse.futureshops.server.escrow.custody.ProtectedCurrencyProvenance;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintBatch;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintSavedData;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintValidationResult;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class CashClaimDeliveryPlanner {
    private static final String CLAIM_ESCROW_ADAPTER =
            "futureshops.cash_claim";

    private CashClaimDeliveryPlanner() {
    }

    public static CashClaimDeliveryPlan plan(
            EscrowClaim claim,
            ProtectedMintSavedData protectedMints,
            UUID attemptId
    ) {
        Objects.requireNonNull(attemptId, "attemptId");
        if (claim.status() != ClaimStatus.PENDING
                || claim.remainingUnits() != claim.originalUnits()) {
            throw new IllegalArgumentException(
                    "Cash claim must be fully pending before delivery");
        }
        Materialization materialization = materialize(
                claim, protectedMints);
        CustodyLot lot = lot(claim, materialization);
        String requestKey = deliveryRequestKey(claim.claimId(), attemptId);
        CustodyBatchPlan custodyPlan = new CustodyBatchPlan(
                CustodyOperation.RELEASE, requestKey,
                CashClaimCustodySupport.PLAYER_INVENTORY_ADAPTER_ID,
                CustodyAdapterCapability.RECONCILABLE,
                materialization.protectionTier(), List.of(lot),
                lot.units());
        return new CashClaimDeliveryPlan(claim,
                materialization.stack(), custodyPlan);
    }

    static CustodyLot expectedLot(
            EscrowClaim claim,
            ProtectedMintSavedData protectedMints
    ) {
        return lot(claim, materialize(claim, protectedMints));
    }

    static Materialization materialize(
            EscrowClaim claim,
            ProtectedMintSavedData protectedMints
    ) {
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(protectedMints, "protectedMints");
        if (claim.kind() == ClaimKind.PROTECTED_CASH) {
            return protectedCash(claim, protectedMints);
        }
        if (claim.kind() == ClaimKind.FOREIGN_CASH) {
            return foreignCash(claim);
        }
        throw new IllegalArgumentException(
                "Claim is not deliverable physical cash");
    }

    static UUID lotId(UUID claimId) {
        return UUID.nameUUIDFromBytes(("futureshops cash claim lot "
                + Objects.requireNonNull(claimId, "claimId"))
                .getBytes(StandardCharsets.UTF_8));
    }

    static String reserveRequestKey(UUID claimId) {
        return "cash.claim.reserve." + claimId;
    }

    static String deliveryRequestKey(UUID claimId, UUID attemptId) {
        return "cash.claim.delivery." + claimId + "." + attemptId;
    }

    private static Materialization protectedCash(
            EscrowClaim claim,
            ProtectedMintSavedData protectedMints
    ) {
        ProtectedCashClaimPayload payload =
                ProtectedCashClaimPayloadCodec.decode(claim.payload());
        ProtectedMintBatch batch = protectedMints.getBatch(payload.batchId());
        if (batch == null
                || !batch.transactionId().equals(claim.transactionId())
                || batch.denominationMinorUnits()
                != payload.denominationMinorUnits()
                || batch.authorizedCount() != payload.authorizedCount()
                || !batch.serverIdentityEvidence().equals(
                payload.serverIdentityEvidence())
                || !batch.checksumEvidence().equals(
                payload.checksumEvidence())) {
            throw new IllegalArgumentException(
                    "Protected cash claim does not match its mint batch");
        }
        ProtectedMintValidationResult validation = protectedMints.validate(
                batch.batchId(), payload.denominationMinorUnits(),
                payload.authorizedCount(), payload.serverIdentityEvidence(),
                payload.checksumEvidence(), payload.billCount(),
                Optional.empty());
        if (!validation.valid()
                || validation.validatedQuantity() != payload.billCount()) {
            throw new IllegalArgumentException(
                    "Protected cash claim mint is unavailable");
        }
        long units = Math.multiplyExact(payload.denominationMinorUnits(),
                (long) payload.billCount());
        requireClaimUnits(claim, units);
        ItemStack stack = ProtectedMoneyMintBridge.mintMaterializedStack(
                batch, payload.billCount());
        byte[] snapshot = ItemStackSnapshotCodec.encode(stack);
        ResourceLocation registryId = ForgeRegistries.ITEMS.getKey(
                stack.getItem());
        if (registryId == null
                || stack.getItem() != ModItems.MONEY_ITEM.get()) {
            throw new IllegalArgumentException(
                    "Protected cash item is not registered");
        }
        ProtectedCurrencyProvenance provenance =
                new ProtectedCurrencyProvenance(batch.batchId(),
                        batch.denominationMinorUnits(),
                        batch.authorizedCount(), payload.billCount(),
                        batch.serverIdentityEvidence(),
                        batch.checksumEvidence());
        return new Materialization(stack,
                CustodyItemSnapshot.capture(registryId.toString(),
                        payload.billCount(), snapshot),
                CustodyAssetType.PROTECTED_PHYSICAL_CURRENCY,
                CustodyProtectionTier.PROTECTED,
                CustodyAdapterCapability.TRANSACTIONAL_PROTECTED,
                CustodyLot.BUILT_IN_CURRENCY_PROVIDER,
                List.of(provenance), units,
                "futureshops.protected_mint", batch.batchId().toString(),
                "mint.available");
    }

    private static Materialization foreignCash(EscrowClaim claim) {
        ForeignCashClaimPayload payload =
                ForeignCashClaimPayloadCodec.decode(claim.payload());
        ItemStack stack = ItemStackSnapshotCodec.decode(
                payload.serializedItemStackNbt());
        ResourceLocation registryId = ForgeRegistries.ITEMS.getKey(
                stack.getItem());
        if (registryId == null
                || stack.getItem() == ModItems.MONEY_ITEM.get()
                || ForeignCashClaimPayload.PROTECTED_ITEM_ID.equals(
                registryId.toString())
                || !registryId.toString().equals(payload.registryItemId())
                || stack.getCount() != payload.stackCount()
                || payload.stackCount() > stack.getMaxStackSize()
                || !Arrays.equals(ItemStackSnapshotCodec.encode(stack),
                payload.serializedItemStackNbt())) {
            throw new IllegalArgumentException(
                    "Foreign cash claim stack does not match its payload");
        }
        long units = Math.multiplyExact(payload.denominationMinorUnits(),
                (long) payload.stackCount());
        requireClaimUnits(claim, units);
        return new Materialization(stack,
                CustodyItemSnapshot.capture(payload.registryItemId(),
                        payload.stackCount(),
                        payload.serializedItemStackNbt()),
                CustodyAssetType.FOREIGN_PHYSICAL_CURRENCY,
                CustodyProtectionTier.UNPROTECTED_FOREIGN,
                CustodyAdapterCapability.UNPROTECTED_EXTERNAL,
                payload.providerId(), List.of(), units,
                "foreign." + payload.providerId(), payload.providerId(),
                payload.registryItemId());
    }

    private static CustodyLot lot(
            EscrowClaim claim,
            Materialization materialization
    ) {
        byte[] empty = sha256(new byte[0]);
        byte[] assets = materialization.snapshot().contentHash();
        String token = "claim." + claim.claimId();
        CustodyEndpointEvidence source = new CustodyEndpointEvidence(
                materialization.sourceAdapter(),
                materialization.sourceCapability(),
                materialization.sourceOwner(),
                materialization.sourceLocation(), assets, empty, token);
        CustodyEndpointEvidence destination =
                new CustodyEndpointEvidence(CLAIM_ESCROW_ADAPTER,
                        CustodyAdapterCapability.RECONCILABLE,
                        claim.ownerId().toString(),
                        claim.claimId().toString(), empty, assets, token);
        return CustodyLot.held(lotId(claim.claimId()),
                claim.transactionId(), reserveRequestKey(claim.claimId()),
                materialization.assetType(),
                materialization.protectionTier(), materialization.units(),
                materialization.provider(),
                List.of(materialization.snapshot()),
                materialization.provenance(),
                new CustodyTransferEvidence(source, destination),
                claim.createdAt());
    }

    private static void requireClaimUnits(EscrowClaim claim, long units) {
        if (claim.originalUnits() != units) {
            throw new IllegalArgumentException(
                    "Cash claim value does not match its payload");
        }
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    static final class Materialization {
        private final ItemStack stack;
        private final CustodyItemSnapshot snapshot;
        private final CustodyAssetType assetType;
        private final CustodyProtectionTier protectionTier;
        private final CustodyAdapterCapability sourceCapability;
        private final String provider;
        private final List<ProtectedCurrencyProvenance> provenance;
        private final long units;
        private final String sourceAdapter;
        private final String sourceOwner;
        private final String sourceLocation;

        private Materialization(
                ItemStack stack,
                CustodyItemSnapshot snapshot,
                CustodyAssetType assetType,
                CustodyProtectionTier protectionTier,
                CustodyAdapterCapability sourceCapability,
                String provider,
                List<ProtectedCurrencyProvenance> provenance,
                long units,
                String sourceAdapter,
                String sourceOwner,
                String sourceLocation
        ) {
            this.stack = stack.copy();
            this.snapshot = snapshot;
            this.assetType = assetType;
            this.protectionTier = protectionTier;
            this.sourceCapability = sourceCapability;
            this.provider = provider;
            this.provenance = List.copyOf(provenance);
            this.units = units;
            this.sourceAdapter = sourceAdapter;
            this.sourceOwner = sourceOwner;
            this.sourceLocation = sourceLocation;
        }

        ItemStack stack() {
            return stack.copy();
        }

        CustodyItemSnapshot snapshot() {
            return snapshot;
        }

        CustodyAssetType assetType() {
            return assetType;
        }

        CustodyProtectionTier protectionTier() {
            return protectionTier;
        }

        CustodyAdapterCapability sourceCapability() {
            return sourceCapability;
        }

        String provider() {
            return provider;
        }

        List<ProtectedCurrencyProvenance> provenance() {
            return provenance;
        }

        long units() {
            return units;
        }

        String sourceAdapter() {
            return sourceAdapter;
        }

        String sourceOwner() {
            return sourceOwner;
        }

        String sourceLocation() {
            return sourceLocation;
        }
    }
}

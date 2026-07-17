package com.enviouse.futureshops.money;

import com.enviouse.futureshops.Config;
import com.enviouse.futureshops.init.ModItems;
import com.enviouse.futureshops.server.escrow.custody.ProtectedCurrencyProvenance;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintBatch;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintEvidenceFactory;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintSavedData;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintValidationResult;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ProtectedMoneyMintBridge {
    private ProtectedMoneyMintBridge() {
    }

    public static ProtectedMintBatch plan(UUID transactionId, String requestKey,
                                          long denominationMinorUnits, int authorizedCount,
                                          Instant authorizedAt) {
        return ProtectedMintBatch.plan(transactionId, requestKey, denominationMinorUnits,
                authorizedCount, Config.moneyMintServerId, authorizedAt, evidenceFactory());
    }

    public static ProtectedMintEvidenceFactory evidenceFactory() {
        return (batchId, transactionId, denominationMinorUnits, authorizedCount,
                serverIdentityEvidence, authorizedAt) -> MoneyChecksumService.createChecksum(
                denominationMinorUnits, batchId.toString(), authorizedAt.getEpochSecond(),
                transactionId.toString(), serverIdentityEvidence, authorizedCount);
    }

    public static ItemStack mintMaterializedStack(ProtectedMintBatch batch, int count) {
        Objects.requireNonNull(batch, "batch");
        ItemStack stack = new ItemStack(ModItems.MONEY_ITEM.get(), 1);
        if (count <= 0 || count > batch.availableQuantity()
                || count > stack.getMaxStackSize()) {
            throw new IllegalArgumentException("Protected money mint quantity is unavailable");
        }
        String expectedChecksum = evidenceFactory().checksumEvidence(
                batch.batchId(), batch.transactionId(), batch.denominationMinorUnits(),
                batch.authorizedCount(), batch.serverIdentityEvidence(), batch.authorizedAt());
        if (!constantTimeEquals(expectedChecksum, batch.checksumEvidence())) {
            throw new IllegalArgumentException("Protected money mint checksum does not match its plan");
        }
        stack.setCount(count);
        CompoundTag moneyData = new CompoundTag();
        moneyData.putLong(MoneyNbtKeys.DENOMINATION, batch.denominationMinorUnits());
        moneyData.putString(MoneyNbtKeys.MINT_ID, batch.batchId().toString());
        moneyData.putLong(MoneyNbtKeys.MINT_TIMESTAMP, batch.authorizedAt().getEpochSecond());
        moneyData.putString(MoneyNbtKeys.MINT_PLAYER, batch.transactionId().toString());
        moneyData.putString(MoneyNbtKeys.MINT_SERVER, batch.serverIdentityEvidence());
        moneyData.putInt(MoneyNbtKeys.AUTHORIZED_COUNT, batch.authorizedCount());
        moneyData.putString(MoneyNbtKeys.CHECKSUM, batch.checksumEvidence());
        stack.getOrCreateTag().put(MoneyNbtKeys.ROOT, moneyData);
        return stack;
    }

    public static ProtectedCurrencyProvenance provenance(ItemStack stack) {
        CompoundTag data = requireMoneyData(stack);
        UUID batchId;
        try {
            batchId = UUID.fromString(data.getString(MoneyNbtKeys.MINT_ID));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Protected money mint ID is invalid", exception);
        }
        return new ProtectedCurrencyProvenance(batchId,
                data.getLong(MoneyNbtKeys.DENOMINATION),
                data.getInt(MoneyNbtKeys.AUTHORIZED_COUNT), stack.getCount(),
                data.getString(MoneyNbtKeys.MINT_SERVER),
                data.getString(MoneyNbtKeys.CHECKSUM));
    }

    public static ProtectedMintValidationResult validate(
            ItemStack stack,
            ProtectedMintSavedData protectedMints,
            Optional<UUID> expectedReservationTransactionId
    ) {
        Objects.requireNonNull(protectedMints, "protectedMints");
        Objects.requireNonNull(expectedReservationTransactionId,
                "expectedReservationTransactionId");
        ProtectedCurrencyProvenance provenance = provenance(stack);
        return protectedMints.validate(provenance.mintId(),
                provenance.denominationMinorUnits(), provenance.authorizedCount(),
                provenance.serverIdentityEvidence(), provenance.checksumEvidence(),
                provenance.billCount(), expectedReservationTransactionId);
    }

    private static CompoundTag requireMoneyData(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        if (stack.isEmpty() || stack.getItem() != ModItems.MONEY_ITEM.get()) {
            throw new IllegalArgumentException("Protected money stack is invalid");
        }
        CompoundTag root = stack.getTag();
        if (root == null || !root.contains(MoneyNbtKeys.ROOT, Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("Protected money data is missing");
        }
        CompoundTag data = root.getCompound(MoneyNbtKeys.ROOT);
        if (!data.contains(MoneyNbtKeys.DENOMINATION, Tag.TAG_LONG)
                || !data.contains(MoneyNbtKeys.MINT_ID, Tag.TAG_STRING)
                || !data.contains(MoneyNbtKeys.MINT_TIMESTAMP, Tag.TAG_LONG)
                || !data.contains(MoneyNbtKeys.MINT_PLAYER, Tag.TAG_STRING)
                || !data.contains(MoneyNbtKeys.MINT_SERVER, Tag.TAG_STRING)
                || !data.contains(MoneyNbtKeys.AUTHORIZED_COUNT, Tag.TAG_INT)
                || !data.contains(MoneyNbtKeys.CHECKSUM, Tag.TAG_STRING)) {
            throw new IllegalArgumentException("Protected money data is incomplete");
        }
        long denomination = data.getLong(MoneyNbtKeys.DENOMINATION);
        int authorizedCount = data.getInt(MoneyNbtKeys.AUTHORIZED_COUNT);
        if (denomination <= 0L || authorizedCount <= 0
                || stack.getCount() > authorizedCount) {
            throw new IllegalArgumentException("Protected money quantity is invalid");
        }
        String expected = MoneyChecksumService.createChecksum(denomination,
                data.getString(MoneyNbtKeys.MINT_ID),
                data.getLong(MoneyNbtKeys.MINT_TIMESTAMP),
                data.getString(MoneyNbtKeys.MINT_PLAYER),
                data.getString(MoneyNbtKeys.MINT_SERVER), authorizedCount);
        if (!constantTimeEquals(expected, data.getString(MoneyNbtKeys.CHECKSUM))) {
            throw new IllegalArgumentException("Protected money checksum is invalid");
        }
        return data;
    }

    private static boolean constantTimeEquals(String first, String second) {
        byte[] left = Objects.requireNonNull(first, "first")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] right = Objects.requireNonNull(second, "second")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return java.security.MessageDigest.isEqual(left, right);
    }
}

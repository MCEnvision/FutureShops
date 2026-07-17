package com.enviouse.futureshops.money;

import com.enviouse.futureshops.Config;
import com.enviouse.futureshops.init.ModItems;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintBatch;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintSavedData;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintValidationResult;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class InternalBillAuthorityRouter {
    private final ProtectedMintSavedData protectedMints;
    private final Map<String, MoneyMintRecord> legacyMints;
    private final Item moneyItem;
    private final Instant validationTime;

    public InternalBillAuthorityRouter(ProtectedMintSavedData protectedMints,
                                       SpentMintsSavedData legacyMints) {
        this(protectedMints, legacyMints, ModItems.MONEY_ITEM.get(), Instant.now());
    }

    InternalBillAuthorityRouter(ProtectedMintSavedData protectedMints,
                                SpentMintsSavedData legacyMints,
                                Item moneyItem,
                                Instant validationTime) {
        this.protectedMints = Objects.requireNonNull(protectedMints, "protectedMints");
        this.legacyMints = Objects.requireNonNull(legacyMints, "legacyMints")
                .snapshotRegistry();
        this.moneyItem = Objects.requireNonNull(moneyItem, "moneyItem");
        this.validationTime = Objects.requireNonNull(validationTime, "validationTime");
    }

    public Resolution resolve(ItemStack stack) {
        ParsedBill parsed = parse(stack);
        if (parsed.failure() != null) {
            return Resolution.rejected(parsed.failure());
        }

        ProtectedMintBatch protectedBatch = protectedBatch(parsed.mintId());
        MoneyMintRecord legacy = legacyMints.get(parsed.mintId());
        if (protectedBatch != null && legacy != null) {
            return Resolution.identified(Authority.NONE, Status.CROSS_STORE_COLLISION,
                    parsed, 0);
        }
        if (protectedBatch != null) {
            return resolveProtected(parsed, protectedBatch);
        }
        Authority identified = legacy != null ? Authority.LEGACY : Authority.NONE;
        if (!validLegacyChecksum(parsed)) {
            return Resolution.identified(identified, Status.CHECKSUM_MISMATCH,
                    parsed, 0);
        }
        if (legacy != null) {
            return resolveLegacy(parsed, legacy);
        }
        return Resolution.identified(Authority.NONE, Status.UNKNOWN_MINT, parsed, 0);
    }

    private Resolution resolveProtected(ParsedBill parsed, ProtectedMintBatch batch) {
        if (!parsed.mintId().equals(batch.batchId().toString())
                || parsed.denominationMinorUnits() != batch.denominationMinorUnits()
                || parsed.authorizedCount() != batch.authorizedCount()
                || parsed.mintedAt() != batch.authorizedAt().getEpochSecond()
                || !parsed.mintPlayer().equals(batch.transactionId().toString())
                || !evidenceEqual(parsed.serverIdentity(),
                        batch.serverIdentityEvidence())
                || !evidenceEqual(parsed.checksum(), batch.checksumEvidence())) {
            return Resolution.identified(Authority.PROTECTED,
                    Status.EVIDENCE_MISMATCH, parsed, 0);
        }
        ProtectedMintValidationResult validation = protectedMints.validate(
                batch.batchId(), parsed.denominationMinorUnits(), parsed.authorizedCount(),
                parsed.serverIdentity(), parsed.checksum(), 1, Optional.empty());
        if (validation.valid()) {
            return Resolution.identified(Authority.PROTECTED, Status.VALID, parsed,
                    batch.availableQuantity());
        }
        Status status = switch (validation.code()) {
            case DENOMINATION_MISMATCH, SERVER_IDENTITY_MISMATCH, CHECKSUM_MISMATCH,
                    UNKNOWN_MINT -> Status.EVIDENCE_MISMATCH;
            case NOT_AVAILABLE, ALREADY_SPENT, REFUNDED, QUARANTINED ->
                    Status.UNAVAILABLE;
            case VALID -> throw new IllegalStateException(
                    "Protected bill validation result is inconsistent");
        };
        return Resolution.identified(Authority.PROTECTED, status, parsed, 0);
    }

    private Resolution resolveLegacy(ParsedBill parsed, MoneyMintRecord legacy) {
        if (expired(parsed.mintedAt())) {
            return Resolution.identified(Authority.LEGACY, Status.EXPIRED, parsed, 0);
        }
        if (legacy.denomination() != parsed.denominationMinorUnits()
                || legacy.authorizedCount() != parsed.authorizedCount()) {
            return Resolution.identified(Authority.LEGACY, Status.EVIDENCE_MISMATCH,
                    parsed, 0);
        }
        int available = Math.max(0, legacy.remainingCount());
        return Resolution.identified(Authority.LEGACY,
                available == 0 ? Status.UNAVAILABLE : Status.VALID,
                parsed, available);
    }

    private ProtectedMintBatch protectedBatch(String mintId) {
        try {
            return protectedMints.getBatch(UUID.fromString(mintId));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private boolean expired(long mintedAt) {
        long now = validationTime.getEpochSecond();
        long maximumAge;
        try {
            maximumAge = Math.multiplyExact((long) Config.moneyMaxAgeDays, 86_400L);
            return maximumAge < 0L || mintedAt > now
                    || Math.subtractExact(now, mintedAt) > maximumAge;
        } catch (ArithmeticException exception) {
            return true;
        }
    }

    private static boolean validLegacyChecksum(ParsedBill parsed) {
        String expected = MoneyChecksumService.createChecksum(
                parsed.denominationMinorUnits(), parsed.mintId(), parsed.mintedAt(),
                parsed.mintPlayer(), parsed.serverIdentity(), parsed.authorizedCount());
        return evidenceEqual(expected, parsed.checksum());
    }

    private static boolean evidenceEqual(String expected, String supplied) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8));
    }

    private ParsedBill parse(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getItem() != moneyItem) {
            return ParsedBill.failed(Status.NOT_MONEY);
        }
        CompoundTag root = stack.getTag();
        if (root == null || !root.contains(MoneyNbtKeys.ROOT, Tag.TAG_COMPOUND)) {
            return ParsedBill.failed(Status.MALFORMED);
        }
        CompoundTag data = root.getCompound(MoneyNbtKeys.ROOT);
        if (!data.contains(MoneyNbtKeys.DENOMINATION, Tag.TAG_LONG)
                || !data.contains(MoneyNbtKeys.MINT_ID, Tag.TAG_STRING)
                || !data.contains(MoneyNbtKeys.MINT_TIMESTAMP, Tag.TAG_LONG)
                || !data.contains(MoneyNbtKeys.MINT_PLAYER, Tag.TAG_STRING)
                || !data.contains(MoneyNbtKeys.MINT_SERVER, Tag.TAG_STRING)
                || !data.contains(MoneyNbtKeys.AUTHORIZED_COUNT, Tag.TAG_INT)
                || !data.contains(MoneyNbtKeys.CHECKSUM, Tag.TAG_STRING)) {
            return ParsedBill.failed(Status.MALFORMED);
        }
        long denomination = data.getLong(MoneyNbtKeys.DENOMINATION);
        int authorizedCount = data.getInt(MoneyNbtKeys.AUTHORIZED_COUNT);
        String mintId = data.getString(MoneyNbtKeys.MINT_ID);
        String checksum = data.getString(MoneyNbtKeys.CHECKSUM);
        if (denomination <= 0L || authorizedCount <= 0
                || stack.getCount() > authorizedCount || mintId.isEmpty()
                || checksum.isEmpty()) {
            return ParsedBill.failed(Status.MALFORMED);
        }
        return new ParsedBill(null, mintId, denomination, authorizedCount,
                data.getLong(MoneyNbtKeys.MINT_TIMESTAMP),
                data.getString(MoneyNbtKeys.MINT_PLAYER),
                data.getString(MoneyNbtKeys.MINT_SERVER), checksum);
    }

    public enum Authority {
        NONE,
        PROTECTED,
        LEGACY
    }

    public enum Status {
        VALID,
        NOT_MONEY,
        MALFORMED,
        CHECKSUM_MISMATCH,
        EXPIRED,
        UNKNOWN_MINT,
        CROSS_STORE_COLLISION,
        EVIDENCE_MISMATCH,
        UNAVAILABLE
    }

    public record Resolution(Authority authority,
                             Status status,
                             String mintId,
                             long denominationMinorUnits,
                             int authorizedCount,
                             int authorityAvailableCount) {
        public Resolution {
            Objects.requireNonNull(authority, "authority");
            Objects.requireNonNull(status, "status");
            mintId = Objects.requireNonNull(mintId, "mintId");
            if (denominationMinorUnits < 0L || authorizedCount < 0
                    || authorityAvailableCount < 0) {
                throw new IllegalArgumentException("Bill authority result is invalid");
            }
            if (status == Status.VALID
                    && (authority == Authority.NONE || authorityAvailableCount <= 0
                    || denominationMinorUnits <= 0L || authorizedCount <= 0)) {
                throw new IllegalArgumentException("Valid bill authority result is incomplete");
            }
        }

        public boolean spendable() {
            return status == Status.VALID;
        }

        private static Resolution rejected(Status status) {
            return new Resolution(Authority.NONE, status, "", 0L, 0, 0);
        }

        private static Resolution identified(Authority authority, Status status,
                                             ParsedBill parsed, int available) {
            return new Resolution(authority, status, parsed.mintId(),
                    parsed.denominationMinorUnits(), parsed.authorizedCount(), available);
        }
    }

    private record ParsedBill(Status failure,
                              String mintId,
                              long denominationMinorUnits,
                              int authorizedCount,
                              long mintedAt,
                              String mintPlayer,
                              String serverIdentity,
                              String checksum) {
        private static ParsedBill failed(Status failure) {
            return new ParsedBill(Objects.requireNonNull(failure, "failure"),
                    "", 0L, 0, 0L, "", "", "");
        }
    }
}

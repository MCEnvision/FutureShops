package com.enviouse.futureshops.command;

import com.enviouse.futureshops.mixin.PlayerListInvoker;
import com.enviouse.futureshops.money.CurrencyWithdrawalService;
import com.enviouse.futureshops.server.escrow.inventory.PlayerDataDurabilityBarrier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

record PendingWithdrawalRequest(
        UUID requestId,
        long amountMinorUnits,
        boolean multipleBills,
        String currencySignature,
        List<Integer> denominationCounts,
        long createdAtEpochSecond
) {
    static final String PLAYER_DATA_KEY =
            "futureshops_pending_withdrawal_request";
    private static final int VERSION = 1;
    private static final UUID ZERO_UUID = new UUID(0L, 0L);
    private static final Pattern SIGNATURE = Pattern.compile("[0-9a-f]{64}");
    private static final PlayerDataDurabilityBarrier DURABILITY_BARRIER =
            new PlayerDataDurabilityBarrier();

    PendingWithdrawalRequest {
        Objects.requireNonNull(requestId, "requestId");
        currencySignature = Objects.requireNonNull(
                currencySignature, "currencySignature");
        denominationCounts = List.copyOf(Objects.requireNonNull(
                denominationCounts, "denominationCounts"));
        if (requestId.equals(ZERO_UUID)
                || amountMinorUnits <= 0L
                || !SIGNATURE.matcher(currencySignature).matches()
                || denominationCounts.isEmpty()
                || denominationCounts.size()
                > CurrencyWithdrawalService.MAX_DENOMINATIONS
                || createdAtEpochSecond <= 0L) {
            throw new IllegalArgumentException(
                    "Pending withdrawal request is invalid");
        }
        int total = 0;
        for (Integer count : denominationCounts) {
            if (count == null || count < 0
                    || count > CurrencyWithdrawalService.MAX_SELECTED_ITEMS) {
                throw new IllegalArgumentException(
                        "Pending withdrawal denomination is invalid");
            }
            total = Math.addExact(total, count);
            if (total > CurrencyWithdrawalService.MAX_SELECTED_ITEMS) {
                throw new IllegalArgumentException(
                        "Pending withdrawal bill total is invalid");
            }
        }
        if (total == 0) {
            throw new IllegalArgumentException(
                    "Pending withdrawal selection is empty");
        }
    }

    static PendingWithdrawalRequest create(
            long amountMinorUnits,
            boolean multipleBills,
            String currencySignature,
            List<Integer> denominationCounts
    ) {
        return new PendingWithdrawalRequest(
                UUID.randomUUID(), amountMinorUnits, multipleBills,
                currencySignature, denominationCounts,
                Instant.now().getEpochSecond());
    }

    boolean matches(long amount, boolean multiple) {
        return amountMinorUnits == amount && multipleBills == multiple;
    }

    static Optional<PendingWithdrawalRequest> load(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        CompoundTag persistent = player.getPersistentData();
        if (!persistent.contains(PLAYER_DATA_KEY)) {
            return Optional.empty();
        }
        if (!persistent.contains(PLAYER_DATA_KEY, Tag.TAG_COMPOUND)) {
            throw new IllegalStateException(
                    "Pending withdrawal player data has the wrong type");
        }
        return Optional.of(fromTag(
                persistent.getCompound(PLAYER_DATA_KEY)));
    }

    static void persist(ServerPlayer player, PendingWithdrawalRequest request) {
        mutateAndSave(player, request.toTag());
    }

    static void clear(ServerPlayer player) {
        mutateAndSave(player, null);
    }

    CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("version", VERSION);
        tag.putUUID("requestId", requestId);
        tag.putLong("amount", amountMinorUnits);
        tag.putBoolean("multiple", multipleBills);
        tag.putString("signature", currencySignature);
        int[] counts = new int[denominationCounts.size()];
        for (int index = 0; index < counts.length; index++) {
            counts[index] = denominationCounts.get(index);
        }
        tag.putIntArray("counts", counts);
        tag.putLong("createdAt", createdAtEpochSecond);
        return tag;
    }

    static PendingWithdrawalRequest fromTag(CompoundTag tag) {
        Objects.requireNonNull(tag, "tag");
        if (!tag.contains("version", Tag.TAG_INT)
                || tag.getInt("version") != VERSION
                || !tag.hasUUID("requestId")
                || !tag.contains("amount", Tag.TAG_LONG)
                || !tag.contains("multiple", Tag.TAG_BYTE)
                || !tag.contains("signature", Tag.TAG_STRING)
                || !tag.contains("counts", Tag.TAG_INT_ARRAY)
                || !tag.contains("createdAt", Tag.TAG_LONG)) {
            throw new IllegalArgumentException(
                    "Pending withdrawal player data is invalid");
        }
        int[] rawCounts = tag.getIntArray("counts");
        List<Integer> counts = new ArrayList<>(rawCounts.length);
        for (int count : rawCounts) {
            counts.add(count);
        }
        return new PendingWithdrawalRequest(
                tag.getUUID("requestId"), tag.getLong("amount"),
                tag.getBoolean("multiple"), tag.getString("signature"),
                counts, tag.getLong("createdAt"));
    }

    private static void mutateAndSave(
            ServerPlayer player,
            CompoundTag replacement
    ) {
        Objects.requireNonNull(player, "player");
        CompoundTag persistent = player.getPersistentData();
        if (replacement == null) {
            persistent.remove(PLAYER_DATA_KEY);
        } else {
            persistent.put(PLAYER_DATA_KEY, replacement.copy());
        }
        try {
            if (player.getServer() == null
                    || player.getServer().getPlayerList()
                    .getPlayer(player.getUUID()) != player) {
                throw new IllegalStateException(
                        "Withdrawal player is not active");
            }
            ((PlayerListInvoker) player.getServer().getPlayerList())
                    .futureshops$save(player);
            DURABILITY_BARRIER.forcePlayerData(
                    player.getServer(), player.getUUID());
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Withdrawal request durability is unknown", exception);
        }
    }
}

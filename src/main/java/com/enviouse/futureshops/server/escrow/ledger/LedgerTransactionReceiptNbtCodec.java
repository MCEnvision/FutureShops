package com.enviouse.futureshops.server.escrow.ledger;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.List;

final class LedgerTransactionReceiptNbtCodec {
    private LedgerTransactionReceiptNbtCodec() {
    }

    static CompoundTag write(LedgerTransactionReceipt receipt) {
        CompoundTag tag = new CompoundTag();
        LedgerTransaction transaction = receipt.transaction();
        tag.putLong("sequence", receipt.applicationSequence());
        tag.putUUID("transaction", transaction.transactionId());
        tag.putString("idempotency", transaction.idempotencyKey());
        tag.putString("reason", transaction.reason());
        tag.putString("fingerprint", receipt.fingerprint());
        ListTag legs = new ListTag();
        for (LedgerLeg leg : LedgerTransaction.canonicalLegs(transaction.legs())) {
            CompoundTag value = new CompoundTag();
            value.putString("type", leg.account().type().name());
            value.putString("owner", leg.account().ownerKey());
            value.putLong("delta", leg.deltaMinor());
            legs.add(value);
        }
        tag.put("legs", legs);
        return tag;
    }

    static LedgerTransactionReceipt read(CompoundTag tag) {
        if (!tag.contains("sequence", Tag.TAG_LONG)
                || !tag.hasUUID("transaction")
                || !tag.contains("idempotency", Tag.TAG_STRING)
                || !tag.contains("reason", Tag.TAG_STRING)
                || !tag.contains("fingerprint", Tag.TAG_STRING)) {
            throw new IllegalStateException("Escrow ledger receipt is incomplete");
        }
        Tag rawLegs = tag.get("legs");
        if (!(rawLegs instanceof ListTag legs)
                || (!legs.isEmpty() && legs.getElementType() != Tag.TAG_COMPOUND)) {
            throw new IllegalStateException("Escrow ledger receipt legs are invalid");
        }
        if (legs.size() < 2 || legs.size() > 128) {
            throw new IllegalStateException("Escrow ledger receipt leg count is invalid");
        }
        List<LedgerLeg> decoded = legs.stream().map(raw -> readLeg((CompoundTag) raw)).toList();
        try {
            LedgerTransaction transaction = new LedgerTransaction(
                    tag.getUUID("transaction"), tag.getString("idempotency"),
                    tag.getString("reason"), decoded);
            return new LedgerTransactionReceipt(
                    tag.getLong("sequence"), transaction, tag.getString("fingerprint"));
        } catch (IllegalArgumentException | ArithmeticException exception) {
            throw new IllegalStateException("Escrow ledger receipt is invalid", exception);
        }
    }

    private static LedgerLeg readLeg(CompoundTag tag) {
        if (!tag.contains("type", Tag.TAG_STRING)
                || !tag.contains("owner", Tag.TAG_STRING)
                || !tag.contains("delta", Tag.TAG_LONG)) {
            throw new IllegalStateException("Escrow ledger receipt leg is incomplete");
        }
        try {
            LedgerAccountType type = LedgerAccountType.valueOf(tag.getString("type"));
            return new LedgerLeg(new LedgerAccountId(type, tag.getString("owner")),
                    tag.getLong("delta"));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Escrow ledger receipt leg is invalid", exception);
        }
    }
}

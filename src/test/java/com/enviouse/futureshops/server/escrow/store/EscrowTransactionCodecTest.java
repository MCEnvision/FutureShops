package com.enviouse.futureshops.server.escrow.store;

import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;
import com.enviouse.futureshops.server.escrow.journal.WriteAheadJournal;
import com.enviouse.futureshops.server.escrow.model.EscrowOperation;
import com.enviouse.futureshops.server.escrow.model.EscrowState;
import com.enviouse.futureshops.server.escrow.runtime.EscrowJournalEventCodec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EscrowTransactionCodecTest {
    @Test
    void nbtRoundTripPreservesEveryTransactionField() {
        EscrowTransaction transaction = EscrowTransactionFixtures.recoveryRequired("codec nbt full");

        EscrowTransaction decoded = EscrowTransactionNbtCodec.decode(
                EscrowTransactionNbtCodec.encode(transaction));

        assertEquals(transaction, decoded);
        assertEquals("minecraft:the_nether", decoded.shopReference().orElseThrow().dimensionId());
        assertEquals(EscrowTransactionFixtures.PARENT_ID,
                decoded.parentTransactionId().orElseThrow().value());
        assertEquals("DELIVERY_RETRY", decoded.lastError().orElseThrow().code());
        assertEquals(1, decoded.retryMetadata().attemptCount());
        assertEquals(EscrowTransactionFixtures.CREATED_AT, decoded.timestamps().createdAt());
        assertArrayEquals(new byte[]{0, 1, 2, 3, -1}, decoded.assetLots().get(1).serializedPayload());
    }

    @Test
    void binaryRoundTripPreservesErrorDetailsAndRecoveryMetadata() {
        EscrowTransaction transaction = EscrowTransactionFixtures.recoveryRequired("codec binary full");

        byte[] encoded = EscrowTransactionByteCodec.encode(transaction);
        EscrowTransaction decoded = EscrowTransactionByteCodec.decode(encoded);

        assertEquals(transaction, decoded);
        assertEquals(transaction.lastError().orElseThrow().details(),
                decoded.lastError().orElseThrow().details());
        assertEquals(transaction.retryMetadata(), decoded.retryMetadata());
        org.junit.jupiter.api.Assertions.assertTrue(encoded.length <= WriteAheadJournal.MAX_PAYLOAD_BYTES);
        org.junit.jupiter.api.Assertions.assertTrue(encoded.length <= EscrowJournalEventCodec.MAX_BODY_BYTES);
    }

    @Test
    void codecsPreserveCommitAndTerminalTimestamps() {
        EscrowTransaction completed = EscrowTransactionFixtures.created("codec completed")
                .transitionTo(EscrowState.VALIDATED, EscrowTransactionFixtures.CREATED_AT.plusSeconds(1))
                .transitionTo(EscrowState.HOLDING, EscrowTransactionFixtures.CREATED_AT.plusSeconds(2))
                .transitionTo(EscrowState.HELD, EscrowTransactionFixtures.CREATED_AT.plusSeconds(3))
                .transitionTo(EscrowState.COMMIT_DECIDED, EscrowTransactionFixtures.CREATED_AT.plusSeconds(4))
                .transitionTo(EscrowState.COMMITTED, EscrowTransactionFixtures.CREATED_AT.plusSeconds(5))
                .transitionTo(EscrowState.CLAIMS_CREATED, EscrowTransactionFixtures.CREATED_AT.plusSeconds(6))
                .transitionTo(EscrowState.COMPLETED, EscrowTransactionFixtures.CREATED_AT.plusSeconds(7));

        EscrowTransaction fromNbt = EscrowTransactionNbtCodec.decode(
                EscrowTransactionNbtCodec.encode(completed));
        EscrowTransaction fromBytes = EscrowTransactionByteCodec.decode(
                EscrowTransactionByteCodec.encode(completed));

        assertEquals(completed, fromNbt);
        assertEquals(completed, fromBytes);
        assertEquals(EscrowTransactionFixtures.CREATED_AT.plusSeconds(4),
                fromBytes.timestamps().commitDecidedAt().orElseThrow());
        assertEquals(EscrowTransactionFixtures.CREATED_AT.plusSeconds(7),
                fromBytes.timestamps().terminalAt().orElseThrow());
    }

    @Test
    void codecsPreserveAbsentParentErrorAndShopReference() {
        EscrowTransaction source = EscrowTransactionFixtures.created("codec absent optionals");
        EscrowTransaction transaction = new EscrowTransaction(
                source.transactionId(),
                Optional.empty(),
                source.requestKey(),
                EscrowOperation.ATM_WITHDRAWAL,
                source.state(),
                source.participants(),
                source.assetLots(),
                source.timestamps(),
                source.revision(),
                source.configRevision(),
                Optional.empty(),
                source.retryMetadata(),
                Optional.empty());

        assertEquals(transaction, EscrowTransactionNbtCodec.decode(
                EscrowTransactionNbtCodec.encode(transaction)));
        assertEquals(transaction, EscrowTransactionByteCodec.decode(
                EscrowTransactionByteCodec.encode(transaction)));
    }

    @Test
    void codecsRejectNewerSchemas() {
        EscrowTransaction transaction = EscrowTransactionFixtures.created("codec schema");
        CompoundTag tag = EscrowTransactionNbtCodec.encode(transaction);
        tag.putInt("schema", Integer.MAX_VALUE);

        byte[] encoded = EscrowTransactionByteCodec.encode(transaction);
        ByteBuffer.wrap(encoded).putInt(4, Integer.MAX_VALUE);

        assertThrows(IllegalStateException.class, () -> EscrowTransactionNbtCodec.decode(tag));
        assertThrows(IllegalStateException.class, () -> EscrowTransactionByteCodec.decode(encoded));
    }

    @Test
    void nbtCodecRejectsUnknownEnumsAndWrongListTypes() {
        CompoundTag unknownEnum = EscrowTransactionNbtCodec.encode(
                EscrowTransactionFixtures.created("codec enum"));
        unknownEnum.putString("operation", "UNKNOWN_OPERATION");

        CompoundTag wrongList = EscrowTransactionNbtCodec.encode(
                EscrowTransactionFixtures.created("codec list"));
        ListTag strings = new ListTag();
        strings.add(net.minecraft.nbt.StringTag.valueOf("not a participant"));
        wrongList.put("participants", strings);

        assertThrows(IllegalStateException.class, () -> EscrowTransactionNbtCodec.decode(unknownEnum));
        assertThrows(IllegalStateException.class, () -> EscrowTransactionNbtCodec.decode(wrongList));
    }

    @Test
    void nbtCodecRejectsOversizedAssetPayload() {
        CompoundTag tag = EscrowTransactionNbtCodec.encode(
                EscrowTransactionFixtures.created("codec payload"));
        CompoundTag asset = (CompoundTag) ((ListTag) tag.get("asset_lots")).get(1);
        asset.putByteArray("payload", new byte[EscrowCodecLimits.MAX_PAYLOAD_BYTES + 1]);

        assertThrows(IllegalStateException.class, () -> EscrowTransactionNbtCodec.decode(tag));
    }

    @Test
    void binaryCodecRejectsTruncationAndTrailingData() {
        byte[] encoded = EscrowTransactionByteCodec.encode(
                EscrowTransactionFixtures.recoveryRequired("codec malformed"));
        byte[] truncated = Arrays.copyOf(encoded, encoded.length - 1);
        byte[] trailing = Arrays.copyOf(encoded, encoded.length + 1);

        assertThrows(IllegalStateException.class, () -> EscrowTransactionByteCodec.decode(truncated));
        assertThrows(IllegalStateException.class, () -> EscrowTransactionByteCodec.decode(trailing));
    }
}

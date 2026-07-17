package com.enviouse.futureshops.server.escrow.store;

import com.enviouse.futureshops.server.escrow.model.DimensionAwareShopReference;
import com.enviouse.futureshops.server.escrow.model.EscrowAssetLot;
import com.enviouse.futureshops.server.escrow.model.EscrowAssetLotType;
import com.enviouse.futureshops.server.escrow.model.EscrowError;
import com.enviouse.futureshops.server.escrow.model.EscrowOperation;
import com.enviouse.futureshops.server.escrow.model.EscrowParticipant;
import com.enviouse.futureshops.server.escrow.model.EscrowParticipantRole;
import com.enviouse.futureshops.server.escrow.model.EscrowParty;
import com.enviouse.futureshops.server.escrow.model.EscrowProtectionLevel;
import com.enviouse.futureshops.server.escrow.model.EscrowRequestKey;
import com.enviouse.futureshops.server.escrow.model.EscrowState;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;
import com.enviouse.futureshops.server.escrow.model.EscrowTransactionId;
import com.enviouse.futureshops.server.escrow.model.MoneyAmount;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

final class EscrowTransactionFixtures {
    static final Instant CREATED_AT = Instant.parse("2026-07-16T12:00:00.123456789Z");
    static final UUID BUYER_ID = UUID.fromString("76c1e728-8475-4125-a75e-adaf78dba8af");
    static final UUID TRANSACTION_ID = UUID.fromString("d304d36e-4ee7-4ea2-b1d1-17dfb067136a");
    static final UUID PARENT_ID = UUID.fromString("301b5727-f0fc-40b8-b0d1-5993ea4832f2");

    private EscrowTransactionFixtures() {
    }

    static EscrowTransaction created(String requestKey) {
        return created(TRANSACTION_ID, requestKey);
    }

    static EscrowTransaction created(UUID transactionId, String requestKey) {
        EscrowParty buyer = EscrowParty.player(BUYER_ID);
        EscrowParty shop = EscrowParty.shop("spawn_market");
        Set<EscrowParticipant> participants = Set.of(
                new EscrowParticipant(buyer, Set.of(
                        EscrowParticipantRole.INITIATOR,
                        EscrowParticipantRole.PAYER,
                        EscrowParticipantRole.BUYER)),
                new EscrowParticipant(shop, Set.of(
                        EscrowParticipantRole.BENEFICIARY,
                        EscrowParticipantRole.SELLER)));
        List<EscrowAssetLot> lots = List.of(
                new EscrowAssetLot(
                        UUID.fromString("e9bcc1b8-d9a7-4aee-a059-21cbc08c6072"),
                        EscrowAssetLotType.WALLET_MONEY,
                        EscrowProtectionLevel.PROTECTED,
                        buyer,
                        shop,
                        1L,
                        Optional.of(new MoneyAmount("futureshops:credits", 1250L)),
                        new byte[0],
                        Map.of("quote", "summer sale")),
                new EscrowAssetLot(
                        UUID.fromString("1af9df65-a240-4cbf-a035-60a6fdb29786"),
                        EscrowAssetLotType.ITEM_STACK,
                        EscrowProtectionLevel.RECONCILED,
                        shop,
                        buyer,
                        3L,
                        Optional.empty(),
                        new byte[]{0, 1, 2, 3, -1},
                        Map.of("item_id", "minecraft:diamond", "variant", "pristine")));
        return EscrowTransaction.create(
                new EscrowTransactionId(transactionId),
                Optional.of(new EscrowTransactionId(PARENT_ID)),
                new EscrowRequestKey(requestKey),
                EscrowOperation.SERVER_SHOP_BUY,
                participants,
                lots,
                CREATED_AT,
                27L,
                Optional.of(new DimensionAwareShopReference(
                        "spawn_market", "minecraft:the_nether", -123, 72, 456)));
    }

    static EscrowTransaction recoveryRequired(String requestKey) {
        EscrowTransaction transaction = created(requestKey)
                .transitionTo(EscrowState.VALIDATED, CREATED_AT.plusSeconds(1))
                .transitionTo(EscrowState.HOLDING, CREATED_AT.plusSeconds(2))
                .transitionTo(EscrowState.HELD, CREATED_AT.plusSeconds(3));
        EscrowError error = new EscrowError(
                "DELIVERY_RETRY",
                "The destination was temporarily unavailable",
                true,
                CREATED_AT.plusSeconds(4),
                Map.of("adapter", "vanilla_inventory", "reason", "full"));
        return transaction.requireRecovery(
                error,
                5,
                CREATED_AT.plusSeconds(30).plusNanos(777),
                CREATED_AT.plusSeconds(4));
    }
}

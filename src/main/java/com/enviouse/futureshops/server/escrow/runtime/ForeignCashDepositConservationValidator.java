package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.Config;
import com.enviouse.futureshops.server.escrow.custody.CustodyMutationCodec;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountType;
import com.enviouse.futureshops.server.escrow.model.EscrowAssetLotType;
import com.enviouse.futureshops.server.escrow.model.EscrowProtectionLevel;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class ForeignCashDepositConservationValidator {
    private ForeignCashDepositConservationValidator() {
    }

    static void validateReservation(
            ForeignCashDepositReservation reservation
    ) {
        Map<UUID, ForeignCashDepositPlan.Portion> portions = new HashMap<>();
        for (ForeignCashDepositPlan.Portion portion :
                reservation.plan().portions()) {
            portions.put(ForeignCashDepositReservation.custodyLotId(
                    reservation.transactionId(), portion), portion);
        }
        long total = 0L;
        for (var asset : reservation.heldTransaction().assetLots()) {
            ForeignCashDepositPlan.Portion portion = portions.remove(
                    asset.lotId());
            if (portion == null
                    || asset.type()
                    != EscrowAssetLotType.FOREIGN_PHYSICAL_CURRENCY
                    || asset.protectionLevel()
                    != EscrowProtectionLevel.EXTERNAL
                    || asset.quantity() != portion.selectedCount()
                    || asset.money().orElseThrow().minorUnits()
                    != portion.valueMinorUnits()
                    || !asset.money().orElseThrow().currencyId().equals(
                    ForeignCashDepositReservation.CURRENCY_ID)
                    || !asset.attributes().get("provider").equals(
                    reservation.plan().providerId())
                    || !asset.attributes().get("provider_signature").equals(
                    reservation.plan().providerSignature())
                    || !asset.attributes().get(
                    "dupe_protection_warning").equals(
                    Config.FOREIGN_CURRENCY_WARNING)
                    || !CustodyMutationCodec.decode(
                    asset.serializedPayload()).equals(
                    reservation.custodyReservations().stream()
                            .filter(value -> value.resultingLot().lotId()
                                    .equals(asset.lotId()))
                            .findFirst().orElseThrow())) {
                throw new IllegalArgumentException(
                        "Foreign cash deposit escrow asset is invalid");
            }
            total = Math.addExact(total,
                    asset.money().orElseThrow().minorUnits());
        }
        if (!portions.isEmpty()
                || total != reservation.amountMinorUnits()) {
            throw new IllegalArgumentException(
                    "Foreign cash deposit reservation does not conserve");
        }
    }

    static void validateSettlement(
            ForeignCashDepositSettlement settlement
    ) {
        validateReservation(settlement.reservation());
        long source = settlement.ledgerTransaction().legs().stream()
                .filter(leg -> leg.account().equals(
                        ForeignCashDepositFactory.foreignSourceAccount(
                                settlement.reservation())))
                .mapToLong(leg -> leg.deltaMinor()).sum();
        long foreignSources = settlement.ledgerTransaction().legs().stream()
                .filter(leg -> leg.account().type()
                        == LedgerAccountType.FOREIGN_CURRENCY_SOURCE)
                .count();
        if (foreignSources != 1L
                || source != Math.negateExact(
                settlement.amountMinorUnits())) {
            throw new IllegalArgumentException(
                    "Foreign cash deposit settlement does not conserve");
        }
    }

    static void validateCancellation(
            ForeignCashDepositCancellation cancellation
    ) {
        validateReservation(cancellation.reservation());
    }
}

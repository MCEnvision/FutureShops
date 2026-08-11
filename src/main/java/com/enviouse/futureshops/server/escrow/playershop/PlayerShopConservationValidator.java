package com.enviouse.futureshops.server.escrow.playershop;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class PlayerShopConservationValidator {
    private PlayerShopConservationValidator() {
    }

    public static Report validate(PlayerShopEscrowIntent intent) {
        Objects.requireNonNull(intent, "intent");
        List<String> violations = new ArrayList<>();
        switch (intent.operation()) {
            case PURCHASE -> validatePurchase(intent, false, violations);
            case ADMIN_PURCHASE_SINK -> validatePurchase(intent, true,
                    violations);
            case BUYBACK -> validateBuyback(intent, false, violations);
            case ADMIN_BUYBACK -> validateBuyback(intent, true, violations);
            case SETTLEMENT_CLAIM -> validateSettlement(intent, violations);
            case SERVER_SHOP_OFFER_ACQUIRE ->
                    validateServerOfferAcquire(intent, violations);
            case SERVER_SHOP_OFFER_SELL ->
                    validateServerOfferSell(intent, violations);
            case PLAYER_SHOP_OFFER_ACQUIRE ->
                    validatePlayerOfferAcquire(intent, violations);
            case PLAYER_SHOP_OFFER_SELL ->
                    validatePlayerOfferSell(intent, violations);
        }
        validateClaims(intent, violations);
        validateStorageCoverage(intent, violations);
        return new Report(violations.isEmpty(), violations);
    }

    public static void requireConserved(PlayerShopEscrowIntent intent) {
        Report report = validate(intent);
        if (!report.conserved()) {
            throw new IllegalArgumentException("Player shop conservation failed. "
                    + String.join(". ", report.violations()));
        }
    }

    private static void validatePurchase(
            PlayerShopEscrowIntent intent,
            boolean admin,
            List<String> violations
    ) {
        PlayerShopListingSnapshot listing = intent.listing();
        if (listing == null || listing.adminShop() != admin) {
            violations.add("Purchase shop type does not match the listing");
            return;
        }
        if (listing.direction() == PlayerShopListingSnapshot.Direction.BUY) {
            violations.add("Purchase listing does not allow sales");
        }
        if (!methodAllowed(listing.configuredTradeMode(),
                intent.tradeMethod())) {
            violations.add("Purchase method does not match the listing");
        }
        boolean needsMoney = intent.tradeMethod() == PlayerShopTradeMethod.MONEY
                || intent.tradeMethod()
                        == PlayerShopTradeMethod.MONEY_AND_BARTER;
        boolean needsBarter = intent.tradeMethod()
                == PlayerShopTradeMethod.BARTER
                || intent.tradeMethod()
                        == PlayerShopTradeMethod.MONEY_AND_BARTER;
        for (PlayerShopMoneyTransfer transfer : intent.moneyTransfers()) {
            if (!transfer.source().participantId().equals(intent.actorId())
                    || transfer.paymentSource() != intent.paymentSource()
                    || transfer.destination().kind() != (admin
                    ? PlayerShopAssetEndpoint.Kind.ADMIN_SINK
                    : PlayerShopAssetEndpoint.Kind.MONEY_CLAIM)
                    || !admin && (!transfer.destination().participantId()
                    .equals(intent.ownerId()))) {
                violations.add("Purchase money is not routed to the seller claim or sink");
            }
        }
        if (!needsMoney && !intent.moneyTransfers().isEmpty()) {
            violations.add("Barter purchase contains money");
        }
        if (needsMoney && intent.paymentSource()
                == PlayerShopPaymentSource.NONE) {
            violations.add("Money purchase has no payment source");
        }
        List<PlayerShopItemTransfer> outputs = new ArrayList<>();
        List<PlayerShopItemTransfer> barter = new ArrayList<>();
        for (PlayerShopItemTransfer transfer : intent.itemTransfers()) {
            if (transfer.destination().kind()
                    == PlayerShopAssetEndpoint.Kind.ITEM_CLAIM
                    && transfer.destination().participantId()
                    .equals(intent.actorId())) {
                outputs.add(transfer);
            } else if (transfer.source().kind()
                    == PlayerShopAssetEndpoint.Kind.ACTOR_INVENTORY
                    && transfer.source().participantId()
                    .equals(intent.actorId())) {
                barter.add(transfer);
            } else {
                violations.add("Purchase item transfer has an invalid route");
            }
        }
        PlayerShopAssetEndpoint.Kind expectedOutputSource = admin
                ? PlayerShopAssetEndpoint.Kind.ADMIN_MINT
                : PlayerShopAssetEndpoint.Kind.LINKED_STOCK;
        if (outputs.stream().anyMatch(value -> value.source().kind()
                != expectedOutputSource)) {
            violations.add("Purchase output has an invalid source");
        }
        validateOutputQuantities(intent, outputs, violations);
        if (!needsBarter && !barter.isEmpty()) {
            violations.add("Money purchase contains barter items");
        }
        if (needsBarter) {
            PlayerShopListingSnapshot.ItemTemplate template =
                    listing.barterTemplate();
            for (PlayerShopItemTransfer transfer : barter) {
                if (template == null || !matchesTemplate(transfer.lot(),
                        template)
                        || transfer.destination().kind() != (admin
                        ? PlayerShopAssetEndpoint.Kind.ADMIN_SINK
                        : PlayerShopAssetEndpoint.Kind.ITEM_CLAIM)
                        || !admin && !transfer.destination().participantId()
                        .equals(intent.ownerId())) {
                    violations.add("Barter proceeds are not routed to the owner claim or sink");
                }
            }
        }
    }

    private static void validateBuyback(
            PlayerShopEscrowIntent intent,
            boolean admin,
            List<String> violations
    ) {
        PlayerShopListingSnapshot listing = intent.listing();
        if (listing == null || listing.adminShop() != admin) {
            violations.add("Buyback shop type does not match the listing");
            return;
        }
        if (listing.direction() == PlayerShopListingSnapshot.Direction.SELL) {
            violations.add("Buyback listing does not allow purchases");
        }
        if (intent.requestedUnits() > listing.remainingBuybackUnits()) {
            violations.add("Buyback exceeds the listing cap");
        }
        if (intent.moneyTransfers().size() != 1) {
            violations.add("Buyback money leg count is invalid");
        } else {
            PlayerShopMoneyTransfer money = intent.moneyTransfers().get(0);
            long expected = Math.multiplyExact(listing.buybackPriceMinorUnits(),
                    intent.requestedUnits());
            if (money.amountMinorUnits() != expected
                    || money.destination().kind()
                    != PlayerShopAssetEndpoint.Kind.MONEY_CLAIM
                    || !money.destination().participantId()
                    .equals(intent.actorId())
                    || money.source().kind() != (admin
                    ? PlayerShopAssetEndpoint.Kind.ADMIN_MINT
                    : PlayerShopAssetEndpoint.Kind.OWNER_WALLET)
                    || !admin && !money.source().participantId()
                    .equals(intent.ownerId())) {
                violations.add("Buyback money is not routed to the seller claim");
            }
        }
        if (intent.itemTransfers().isEmpty()) {
            violations.add("Buyback item input is missing");
        }
        int expectedQuantity = Math.multiplyExact(listing.baseQuantity(),
                intent.requestedUnits());
        int actualQuantity = 0;
        PlayerShopListingSnapshot.ItemTemplate template =
                listing.outputs().get(0);
        for (PlayerShopItemTransfer transfer : intent.itemTransfers()) {
            actualQuantity = Math.addExact(actualQuantity,
                    transfer.lot().quantity());
            if (transfer.source().kind()
                    != PlayerShopAssetEndpoint.Kind.ACTOR_INVENTORY
                    || !transfer.source().participantId()
                    .equals(intent.actorId())
                    || !matchesTemplate(transfer.lot(), template)
                    || transfer.destination().kind() != (admin
                    ? PlayerShopAssetEndpoint.Kind.ADMIN_SINK
                    : PlayerShopAssetEndpoint.Kind.ITEM_CLAIM)
                    || !admin && !transfer.destination().participantId()
                    .equals(intent.ownerId())) {
                violations.add("Buyback item is not routed to the owner claim or sink");
            }
        }
        if (actualQuantity != expectedQuantity) {
            violations.add("Buyback item quantity is not conserved");
        }
    }

    private static void validateSettlement(
            PlayerShopEscrowIntent intent,
            List<String> violations
    ) {
        if (!intent.actorId().equals(intent.ownerId())
                || intent.moneyTransfers().size() != 1
                || !intent.itemTransfers().isEmpty()
                || !intent.storageMutations().isEmpty()) {
            violations.add("Settlement claim shape is invalid");
            return;
        }
        PlayerShopMoneyTransfer money = intent.moneyTransfers().get(0);
        if (money.source().kind()
                != PlayerShopAssetEndpoint.Kind.SETTLEMENT_BALANCE
                || !money.source().participantId().equals(intent.ownerId())
                || money.destination().kind()
                != PlayerShopAssetEndpoint.Kind.MONEY_CLAIM
                || !money.destination().participantId().equals(intent.actorId())
                || money.paymentSource() != PlayerShopPaymentSource.NONE) {
            violations.add("Settlement money is not routed to the owner claim");
        }
    }

    private static void validateServerOfferAcquire(
            PlayerShopEscrowIntent intent,
            List<String> violations
    ) {
        PlayerShopListingSnapshot listing = intent.listing();
        if (listing == null || !listing.adminShop()
                || listing.direction()
                == PlayerShopListingSnapshot.Direction.BUY) {
            violations.add("Server shop acquire listing is invalid");
            return;
        }
        boolean needsMoney = intent.tradeMethod()
                == PlayerShopTradeMethod.MONEY
                || intent.tradeMethod()
                == PlayerShopTradeMethod.MONEY_AND_BARTER;
        boolean needsItems = intent.tradeMethod()
                == PlayerShopTradeMethod.BARTER
                || intent.tradeMethod()
                == PlayerShopTradeMethod.MONEY_AND_BARTER;
        if (needsMoney != !intent.moneyTransfers().isEmpty()
                || needsMoney && intent.moneyTransfers().size() != 1) {
            violations.add("Server shop acquire money leg is invalid");
        }
        for (PlayerShopMoneyTransfer transfer : intent.moneyTransfers()) {
            if (!transfer.source().participantId().equals(intent.actorId())
                    || transfer.paymentSource() != intent.paymentSource()
                    || transfer.destination().kind()
                    != PlayerShopAssetEndpoint.Kind.ADMIN_SINK) {
                violations.add(
                        "Server shop acquire money route is invalid");
            }
        }
        List<PlayerShopItemTransfer> outputs = new ArrayList<>();
        List<PlayerShopItemTransfer> inputs = new ArrayList<>();
        for (PlayerShopItemTransfer transfer : intent.itemTransfers()) {
            if (transfer.source().kind()
                    == PlayerShopAssetEndpoint.Kind.ADMIN_MINT
                    && transfer.destination().kind()
                    == PlayerShopAssetEndpoint.Kind.ITEM_CLAIM
                    && transfer.destination().participantId()
                    .equals(intent.actorId())) {
                outputs.add(transfer);
            } else if (transfer.source().kind()
                    == PlayerShopAssetEndpoint.Kind.ACTOR_INVENTORY
                    && transfer.source().participantId()
                    .equals(intent.actorId())
                    && transfer.destination().kind()
                    == PlayerShopAssetEndpoint.Kind.ADMIN_SINK) {
                inputs.add(transfer);
            } else {
                violations.add(
                        "Server shop acquire item route is invalid");
            }
        }
        if (needsItems != !inputs.isEmpty()) {
            violations.add("Server shop acquire item costs are invalid");
        }
        if (intent.tradeMethod() == PlayerShopTradeMethod.FREE
                && (!intent.moneyTransfers().isEmpty()
                || !inputs.isEmpty())) {
            violations.add("Server shop free offer contains a cost");
        }
        validateOutputQuantities(intent, outputs, violations);
        intent.offerSelection().ifPresent(selection ->
                validateOfferComponents(intent, selection,
                        outputs, inputs, violations));
    }

    private static void validateServerOfferSell(
            PlayerShopEscrowIntent intent,
            List<String> violations
    ) {
        PlayerShopListingSnapshot listing = intent.listing();
        if (listing == null || !listing.adminShop()
                || listing.direction()
                == PlayerShopListingSnapshot.Direction.SELL
                || intent.moneyTransfers().size() != 1) {
            violations.add("Server shop sell offer shape is invalid");
            return;
        }
        PlayerShopMoneyTransfer money = intent.moneyTransfers().get(0);
        if (money.amountMinorUnits() <= 0L
                || money.source().kind()
                != PlayerShopAssetEndpoint.Kind.ADMIN_MINT
                || money.destination().kind()
                != PlayerShopAssetEndpoint.Kind.MONEY_CLAIM
                || !money.destination().participantId()
                .equals(intent.actorId())
                || money.paymentSource() != PlayerShopPaymentSource.NONE) {
            violations.add("Server shop sell payout is invalid");
        }
        List<PlayerShopItemTransfer> inputs = intent.itemTransfers().stream()
                .filter(value -> value.source().kind()
                        == PlayerShopAssetEndpoint.Kind.ACTOR_INVENTORY
                        && value.source().participantId()
                        .equals(intent.actorId())
                        && value.destination().kind()
                        == PlayerShopAssetEndpoint.Kind.ADMIN_SINK)
                .toList();
        if (inputs.isEmpty()
                || inputs.size() != intent.itemTransfers().size()) {
            violations.add("Server shop sell inputs are invalid");
            return;
        }
        validateInputQuantities(intent, inputs, violations);
        intent.offerSelection().ifPresent(selection ->
                validateOfferSellComponents(intent, selection,
                        inputs, violations));
    }

    private static void validatePlayerOfferAcquire(
            PlayerShopEscrowIntent intent,
            List<String> violations
    ) {
        PlayerShopListingSnapshot listing = intent.listing();
        if (listing == null || listing.direction()
                == PlayerShopListingSnapshot.Direction.BUY
                || intent.offerSelection().isEmpty()) {
            violations.add("Player shop acquire offer shape is invalid");
            return;
        }
        boolean admin = listing.adminShop();
        boolean needsMoney = intent.tradeMethod()
                == PlayerShopTradeMethod.MONEY
                || intent.tradeMethod()
                == PlayerShopTradeMethod.MONEY_AND_BARTER;
        boolean needsItems = intent.tradeMethod()
                == PlayerShopTradeMethod.BARTER
                || intent.tradeMethod()
                == PlayerShopTradeMethod.MONEY_AND_BARTER;
        if (needsMoney != !intent.moneyTransfers().isEmpty()
                || needsMoney && intent.moneyTransfers().size() != 1) {
            violations.add("Player shop acquire money leg is invalid");
        }
        for (PlayerShopMoneyTransfer transfer
                : intent.moneyTransfers()) {
            if (!transfer.source().participantId().equals(
                    intent.actorId())
                    || transfer.paymentSource()
                    != intent.paymentSource()
                    || transfer.destination().kind() != (admin
                    ? PlayerShopAssetEndpoint.Kind.ADMIN_SINK
                    : PlayerShopAssetEndpoint.Kind.MONEY_CLAIM)
                    || !admin && !transfer.destination().participantId()
                    .equals(intent.ownerId())) {
                violations.add(
                        "Player shop acquire money route is invalid");
            }
        }
        List<PlayerShopItemTransfer> outputs = new ArrayList<>();
        List<PlayerShopItemTransfer> inputs = new ArrayList<>();
        for (PlayerShopItemTransfer transfer
                : intent.itemTransfers()) {
            if (transfer.source().kind() == (admin
                    ? PlayerShopAssetEndpoint.Kind.ADMIN_MINT
                    : PlayerShopAssetEndpoint.Kind.LINKED_STOCK)
                    && transfer.destination().kind()
                    == PlayerShopAssetEndpoint.Kind.ITEM_CLAIM
                    && transfer.destination().participantId()
                    .equals(intent.actorId())) {
                outputs.add(transfer);
            } else if (transfer.source().kind()
                    == PlayerShopAssetEndpoint.Kind.ACTOR_INVENTORY
                    && transfer.source().participantId()
                    .equals(intent.actorId())
                    && transfer.destination().kind() == (admin
                    ? PlayerShopAssetEndpoint.Kind.ADMIN_SINK
                    : PlayerShopAssetEndpoint.Kind.ITEM_CLAIM)
                    && (admin || transfer.destination().participantId()
                    .equals(intent.ownerId()))) {
                inputs.add(transfer);
            } else {
                violations.add(
                        "Player shop acquire item route is invalid");
            }
        }
        if (needsItems != !inputs.isEmpty()) {
            violations.add(
                    "Player shop acquire item costs are invalid");
        }
        if (intent.tradeMethod() == PlayerShopTradeMethod.FREE
                && (!intent.moneyTransfers().isEmpty()
                || !inputs.isEmpty())) {
            violations.add(
                    "Player shop free offer contains a cost");
        }
        validateOutputQuantities(intent, outputs, violations);
        validateOfferComponents(intent,
                intent.offerSelection().orElseThrow(),
                outputs, inputs, violations);
    }

    private static void validatePlayerOfferSell(
            PlayerShopEscrowIntent intent,
            List<String> violations
    ) {
        PlayerShopListingSnapshot listing = intent.listing();
        if (listing == null || listing.direction()
                == PlayerShopListingSnapshot.Direction.SELL
                || intent.offerSelection().isEmpty()
                || intent.moneyTransfers().size() != 1) {
            violations.add("Player shop sell offer shape is invalid");
            return;
        }
        boolean admin = listing.adminShop();
        PlayerShopMoneyTransfer money =
                intent.moneyTransfers().get(0);
        if (money.amountMinorUnits() <= 0L
                || money.source().kind() != (admin
                ? PlayerShopAssetEndpoint.Kind.ADMIN_MINT
                : PlayerShopAssetEndpoint.Kind.OWNER_WALLET)
                || !admin && !money.source().participantId()
                .equals(intent.ownerId())
                || money.destination().kind()
                != PlayerShopAssetEndpoint.Kind.MONEY_CLAIM
                || !money.destination().participantId()
                .equals(intent.actorId())
                || money.paymentSource()
                != PlayerShopPaymentSource.NONE) {
            violations.add("Player shop sell payout is invalid");
        }
        List<PlayerShopItemTransfer> inputs =
                intent.itemTransfers().stream()
                .filter(value -> value.source().kind()
                        == PlayerShopAssetEndpoint.Kind.ACTOR_INVENTORY
                        && value.source().participantId()
                        .equals(intent.actorId())
                        && value.destination().kind() == (admin
                        ? PlayerShopAssetEndpoint.Kind.ADMIN_SINK
                        : PlayerShopAssetEndpoint.Kind.ITEM_CLAIM)
                        && (admin || value.destination().participantId()
                        .equals(intent.ownerId())))
                .toList();
        if (inputs.isEmpty()
                || inputs.size() != intent.itemTransfers().size()) {
            violations.add("Player shop sell inputs are invalid");
            return;
        }
        validateInputQuantities(intent, inputs, violations);
        validateOfferSellComponents(intent,
                intent.offerSelection().orElseThrow(),
                inputs, violations);
    }

    private static void validateOfferComponents(
            PlayerShopEscrowIntent intent,
            PlayerShopOfferSelection selection,
            List<PlayerShopItemTransfer> outputs,
            List<PlayerShopItemTransfer> inputs,
            List<String> violations
    ) {
        if (!intent.listing().outputs().equals(
                selection.outputComponents())) {
            violations.add(
                    "Offer output evidence does not match the quote");
        }
        validateTemplateQuantities(selection.outputComponents(),
                outputs, intent.requestedUnits(),
                "Offer output", violations);
        validateTemplateQuantities(selection.inputComponents(),
                inputs, intent.requestedUnits(),
                "Offer item cost", violations);
    }

    private static void validateOfferSellComponents(
            PlayerShopEscrowIntent intent,
            PlayerShopOfferSelection selection,
            List<PlayerShopItemTransfer> inputs,
            List<String> violations
    ) {
        if (!intent.listing().outputs().equals(
                selection.inputComponents())) {
            violations.add(
                    "Sell input evidence does not match the quote");
        }
        validateTemplateQuantities(selection.inputComponents(),
                inputs, intent.requestedUnits(),
                "Sell input", violations);
    }

    private static void validateTemplateQuantities(
            List<PlayerShopListingSnapshot.ItemTemplate> templates,
            List<PlayerShopItemTransfer> transfers,
            int quantity,
            String label,
            List<String> violations
    ) {
        for (PlayerShopListingSnapshot.ItemTemplate template
                : templates) {
            int expected = Math.multiplyExact(
                    template.unitsPerPurchase(), quantity);
            int actual = transfers.stream()
                    .filter(value -> matchesTemplate(
                            value.lot(), template))
                    .mapToInt(value -> value.lot().quantity())
                    .sum();
            if (actual != expected) {
                violations.add(label
                        + " quantity is not conserved");
            }
        }
        for (PlayerShopItemTransfer transfer : transfers) {
            if (templates.stream().noneMatch(template ->
                    matchesTemplate(transfer.lot(), template))) {
                violations.add(label
                        + " contains an unquoted component");
            }
        }
    }

    private static void validateInputQuantities(
            PlayerShopEscrowIntent intent,
            List<PlayerShopItemTransfer> inputs,
            List<String> violations
    ) {
        for (PlayerShopListingSnapshot.ItemTemplate template
                : intent.listing().outputs()) {
            int expected = Math.multiplyExact(template.unitsPerPurchase(),
                    intent.requestedUnits());
            int actual = inputs.stream()
                    .filter(value -> matchesTemplate(value.lot(), template))
                    .mapToInt(value -> value.lot().quantity()).sum();
            if (actual != expected) {
                violations.add(
                        "Server shop sell input quantity is not conserved");
            }
        }
        for (PlayerShopItemTransfer input : inputs) {
            if (intent.listing().outputs().stream().noneMatch(template ->
                    matchesTemplate(input.lot(), template))) {
                violations.add(
                        "Server shop sell contains an unquoted input");
            }
        }
    }

    private static void validateClaims(PlayerShopEscrowIntent intent,
                                       List<String> violations) {
        for (PlayerShopClaimPlan claim : intent.claims()) {
            if (claim.kind() == PlayerShopClaimPlan.Kind.MONEY
                    && intent.moneyTransfers().stream().noneMatch(value ->
                    value.destination().reference().equals(
                            claim.claimId().toString()))) {
                violations.add("Money claim has no transfer");
            }
            if (claim.kind() == PlayerShopClaimPlan.Kind.EXACT_ITEM
                    && intent.itemTransfers().stream().noneMatch(value ->
                    value.destination().reference().equals(
                            claim.claimId().toString()))) {
                violations.add("Item claim has no transfer");
            }
        }
    }

    private static void validateStorageCoverage(
            PlayerShopEscrowIntent intent,
            List<String> violations
    ) {
        Map<UUID, PlayerShopStorageMutationPlan> byTransfer = new HashMap<>();
        for (PlayerShopStorageMutationPlan mutation : intent.storageMutations()) {
            if (byTransfer.put(mutation.itemTransferId(), mutation) != null) {
                violations.add("Item transfer has multiple storage mutations");
            }
        }
        for (PlayerShopItemTransfer transfer : intent.itemTransfers()) {
            boolean needsExtraction = transfer.source().kind()
                    == PlayerShopAssetEndpoint.Kind.LINKED_STOCK;
            boolean needsInsertion = transfer.destination().kind()
                    == PlayerShopAssetEndpoint.Kind.ITEM_CLAIM
                    && transfer.destination().participantId()
                    .equals(intent.ownerId())
                    && intent.operation() != PlayerShopOperation.SETTLEMENT_CLAIM;
            PlayerShopStorageMutationPlan mutation =
                    byTransfer.get(transfer.transferId());
            if (needsExtraction && (mutation == null || mutation.direction()
                    != PlayerShopStorageMutationPlan.Direction.EXTRACT)) {
                violations.add("Linked stock extraction is not durable");
            }
            if (needsInsertion && (mutation == null || mutation.direction()
                    != PlayerShopStorageMutationPlan.Direction.INSERT
                    || !mutation.claimId().toString().equals(
                            transfer.destination().reference()))) {
                violations.add("Owner item claim insertion is not durable");
            }
            if (!needsExtraction && !needsInsertion && mutation != null) {
                violations.add("Storage mutation has no conserved item route");
            }
        }
    }

    private static void validateOutputQuantities(
            PlayerShopEscrowIntent intent,
            List<PlayerShopItemTransfer> outputs,
            List<String> violations
    ) {
        for (PlayerShopListingSnapshot.ItemTemplate template
                : intent.listing().outputs()) {
            int expected = Math.multiplyExact(template.unitsPerPurchase(),
                    intent.requestedUnits());
            int actual = outputs.stream()
                    .filter(value -> matchesTemplate(value.lot(), template))
                    .mapToInt(value -> value.lot().quantity()).sum();
            if (actual != expected) {
                violations.add("Purchase output quantity is not conserved");
            }
        }
        for (PlayerShopItemTransfer output : outputs) {
            if (intent.listing().outputs().stream().noneMatch(template ->
                    matchesTemplate(output.lot(), template))) {
                violations.add("Purchase contains an unquoted output");
            }
        }
    }

    private static boolean matchesTemplate(
            PlayerShopItemLot lot,
            PlayerShopListingSnapshot.ItemTemplate template
    ) {
        return lot.itemId().equals(template.itemId())
                && lot.matchMode() == template.matchMode()
                && Arrays.equals(lot.canonicalOneCountTemplate(),
                        template.canonicalOneCountTemplate());
    }

    private static boolean methodAllowed(
            PlayerShopListingSnapshot.ConfiguredTradeMode configured,
            PlayerShopTradeMethod selected
    ) {
        return switch (configured) {
            case MONEY -> selected == PlayerShopTradeMethod.MONEY;
            case BARTER -> selected == PlayerShopTradeMethod.BARTER;
            case BOTH -> selected == PlayerShopTradeMethod.MONEY
                    || selected == PlayerShopTradeMethod.BARTER;
            case MONEY_AND_BARTER -> selected
                    == PlayerShopTradeMethod.MONEY_AND_BARTER;
        };
    }

    public record Report(boolean conserved, List<String> violations) {
        public Report {
            violations = List.copyOf(Objects.requireNonNull(violations,
                    "violations"));
            if (conserved != violations.isEmpty()) {
                throw new IllegalArgumentException("Player shop conservation report is invalid");
            }
        }
    }
}

package com.enviouse.futureshops.server.market.bazaar;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

final class BazaarIdentityRegistry {
    private static final UUID ZERO = new UUID(0L, 0L);

    private final Map<UUID, Binding> bindings;

    BazaarIdentityRegistry() {
        this.bindings = new LinkedHashMap<>();
    }

    private BazaarIdentityRegistry(Map<UUID, Binding> bindings) {
        this.bindings = new LinkedHashMap<>(bindings);
    }

    BazaarIdentityRegistry copy() {
        return new BazaarIdentityRegistry(bindings);
    }

    void claimRequest(UUID requestId, UUID orderId,
                      BazaarOperationType operation) {
        claim(requestId, new Binding(Role.REQUEST, orderId,
                Objects.requireNonNull(operation, "operation").name()));
    }

    void claimOrder(BazaarOrder order) {
        Objects.requireNonNull(order, "order");
        claim(order.orderId(), new Binding(Role.ORDER,
                order.orderId(), ""));
        claim(order.activationTransactionId(), new Binding(
                Role.ACTIVATION_TRANSACTION, order.orderId(), ""));
        order.moneyHoldAccountId().ifPresent(identity -> claim(identity,
                new Binding(Role.MONEY_HOLD_ACCOUNT,
                        order.orderId(), "")));
        order.custodyLotId().ifPresent(identity -> claim(identity,
                new Binding(Role.ITEM_CUSTODY_LOT,
                        order.orderId(), "")));
    }

    void claimCreate(CreateBazaarOrderCommand command) {
        Objects.requireNonNull(command, "command");
        claimRequest(command.requestId(), command.orderId(),
                BazaarOperationType.CREATE);
        claim(command.orderId(), new Binding(Role.ORDER,
                command.orderId(), ""));
        claim(command.activationTransactionId(), new Binding(
                Role.ACTIVATION_TRANSACTION, command.orderId(), ""));
        command.moneyHoldAccountId().ifPresent(identity -> claim(identity,
                new Binding(Role.MONEY_HOLD_ACCOUNT,
                        command.orderId(), "")));
        command.custodyLotId().ifPresent(identity -> claim(identity,
                new Binding(Role.ITEM_CUSTODY_LOT,
                        command.orderId(), "")));
    }

    void claimFill(BazaarFill fill) {
        Objects.requireNonNull(fill, "fill");
        claim(fill.fillId(), new Binding(Role.FILL,
                fill.takerOrderId(), fill.makerOrderId().toString()));
        claim(fill.settlementTransactionId(), new Binding(
                Role.FILL_SETTLEMENT_TRANSACTION, fill.takerOrderId(),
                fill.fillId().toString()));
    }

    void claimTerminal(UUID transactionId) {
        claim(transactionId, new Binding(
                Role.TERMINAL_TRANSACTION, null, ""));
    }

    void claimLifecycle(UUID mutationId) {
        claim(mutationId, new Binding(Role.LIFECYCLE_MUTATION,
                null, ""));
    }

    private void claim(UUID identity, Binding binding) {
        UUID value = Objects.requireNonNull(identity, "identity");
        if (ZERO.equals(value)) {
            throw new IllegalArgumentException(
                    "Bazaar identity cannot be zero");
        }
        Binding previous = bindings.putIfAbsent(value,
                Objects.requireNonNull(binding, "binding"));
        if (previous != null && !previous.equals(binding)) {
            throw new IllegalArgumentException(
                    "Bazaar identity has conflicting roles");
        }
    }

    private enum Role {
        REQUEST,
        ORDER,
        ACTIVATION_TRANSACTION,
        MONEY_HOLD_ACCOUNT,
        ITEM_CUSTODY_LOT,
        FILL,
        FILL_SETTLEMENT_TRANSACTION,
        TERMINAL_TRANSACTION,
        LIFECYCLE_MUTATION
    }

    private record Binding(Role role, UUID orderId, String context) {
        private Binding {
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(context, "context");
        }
    }
}

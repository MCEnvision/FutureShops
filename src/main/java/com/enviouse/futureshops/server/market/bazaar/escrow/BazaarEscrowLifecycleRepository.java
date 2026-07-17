package com.enviouse.futureshops.server.market.bazaar.escrow;

import com.enviouse.futureshops.server.market.bazaar.BazaarMutation;
import com.enviouse.futureshops.server.market.bazaar.BazaarOrder;
import com.enviouse.futureshops.server.market.bazaar.BazaarOrderBookSnapshot;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class BazaarEscrowLifecycleRepository {
    private BazaarEscrowLifecycleRepository() {
    }

    public static ApplyResult apply(
            BazaarOrderBookSnapshot orderBook,
            BazaarEscrowLifecycleState lifecycle,
            BazaarEscrowLifecycleEvent event
    ) {
        Objects.requireNonNull(orderBook, "orderBook");
        Objects.requireNonNull(lifecycle, "lifecycle");
        Objects.requireNonNull(event, "event");
        try {
            if (event instanceof BazaarEscrowLifecycleEvent.Prepare value) {
                return prepare(orderBook, lifecycle, value.intent());
            }
            if (event instanceof BazaarEscrowLifecycleEvent.Resolve value) {
                return resolve(orderBook, lifecycle,
                        value.expectedIntent(), value.resolvedIntent());
            }
            if (event instanceof BazaarEscrowLifecycleEvent.Commit value) {
                return commit(orderBook, lifecycle, value);
            }
            throw conflict("Bazaar lifecycle event type is unknown");
        } catch (BazaarEscrowLifecycleConflictException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw conflict("Bazaar lifecycle apply failed", exception);
        }
    }

    public static void validateAgainst(
            BazaarOrderBookSnapshot orderBook,
            BazaarEscrowLifecycleState lifecycle
    ) {
        Objects.requireNonNull(orderBook, "orderBook");
        Objects.requireNonNull(lifecycle, "lifecycle");
        Map<UUID, BazaarOrder> orders = indexOrders(orderBook);
        for (BazaarEscrowOrderBacking backing
                : lifecycle.activeBackings().values()) {
            BazaarOrder order = orders.get(backing.orderId());
            if (order == null) {
                throw conflict(
                        "Bazaar active backing has no materialized order");
            }
            backing.requireMatches(order);
        }
        for (BazaarOrder order : orderBook.orders()) {
            if (!order.state().terminal()
                    && !lifecycle.activeBackings().containsKey(
                    order.orderId())) {
                throw conflict(
                        "Bazaar active order has no escrow backing");
            }
        }
    }

    private static ApplyResult prepare(
            BazaarOrderBookSnapshot orderBook,
            BazaarEscrowLifecycleState lifecycle,
            BazaarCreateEscrowIntent intent
    ) {
        BazaarCreateEscrowIntent existing = lifecycle.createIntents().get(
                intent.requestId());
        if (existing != null) {
            if (!existing.intentFingerprint().equals(
                    intent.intentFingerprint())) {
                throw conflict(
                        "Bazaar creation request conflicts with its intent");
            }
            return new ApplyResult(orderBook, lifecycle, true);
        }
        if (lifecycle.commitFingerprints().containsKey(intent.requestId())
                || lifecycle.createIntents().size()
                >= BazaarEscrowLifecycleState.MAX_CREATE_INTENTS) {
            throw conflict(
                    "Bazaar creation intent capacity or identity conflicts");
        }
        Map<UUID, BazaarCreateEscrowIntent> intents = new HashMap<>(
                lifecycle.createIntents());
        intents.put(intent.requestId(), intent);
        return new ApplyResult(orderBook, new BazaarEscrowLifecycleState(
                intents, lifecycle.commitFingerprints(),
                lifecycle.activeBackings()), false);
    }

    private static ApplyResult resolve(
            BazaarOrderBookSnapshot orderBook,
            BazaarEscrowLifecycleState lifecycle,
            BazaarCreateEscrowIntent expected,
            BazaarCreateEscrowIntent resolved
    ) {
        BazaarCreateEscrowIntent existing = lifecycle.createIntents().get(
                expected.requestId());
        if (resolved.equals(existing)) {
            return new ApplyResult(orderBook, lifecycle, true);
        }
        if (!expected.equals(existing)
                || lifecycle.commitFingerprints().containsKey(
                expected.requestId())) {
            throw conflict(
                    "Bazaar creation resolution ancestry conflicts");
        }
        Map<UUID, BazaarCreateEscrowIntent> intents = new HashMap<>(
                lifecycle.createIntents());
        intents.put(resolved.requestId(), resolved);
        return new ApplyResult(orderBook, new BazaarEscrowLifecycleState(
                intents, lifecycle.commitFingerprints(),
                lifecycle.activeBackings()), false);
    }

    private static ApplyResult commit(
            BazaarOrderBookSnapshot orderBook,
            BazaarEscrowLifecycleState lifecycle,
            BazaarEscrowLifecycleEvent.Commit event
    ) {
        BazaarEscrowCommit commit = event.commit();
        String fingerprint = commit.fingerprint();
        String existingFingerprint = lifecycle.commitFingerprints().get(
                commit.requestId());
        if (existingFingerprint != null) {
            if (!existingFingerprint.equals(fingerprint)) {
                throw conflict(
                        "Bazaar request conflicts with its escrow commit");
            }
            return new ApplyResult(orderBook, lifecycle, true);
        }
        if (lifecycle.commitFingerprints().size()
                >= BazaarEscrowLifecycleState.MAX_COMMITS) {
            throw conflict("Bazaar escrow commit capacity is exhausted");
        }
        Map<UUID, BazaarCreateEscrowIntent> intents = new HashMap<>(
                lifecycle.createIntents());
        if (event.completedIntent().isPresent()) {
            BazaarCreateEscrowIntent completed = event.completedIntent()
                    .orElseThrow();
            BazaarCreateEscrowIntent prepared = intents.get(
                    completed.requestId());
            if (prepared == null || prepared.terminal()
                    || !prepared.intentFingerprint().equals(
                    completed.intentFingerprint())
                    || completed.revision()
                    != Math.addExact(prepared.revision(), 1L)) {
                throw conflict(
                        "Bazaar creation commit has no prepared intent");
            }
            intents.put(completed.requestId(), completed);
        }

        Map<UUID, BazaarOrder> beforeOrders = indexOrders(orderBook);
        Map<UUID, BazaarEscrowOrderBacking> backings = new HashMap<>(
                lifecycle.activeBackings());
        for (BazaarEscrowOrderTransition transition
                : commit.orderTransitions()) {
            BazaarOrder before = beforeOrders.get(transition.orderId());
            if (transition.beforeOrder().isEmpty()) {
                if (before != null || transition.beforeBacking().isPresent()
                        || backings.containsKey(transition.orderId())) {
                    throw conflict(
                            "Bazaar incoming order ancestry conflicts");
                }
            } else if (before == null
                    || !transition.beforeOrder().orElseThrow().equals(
                    BazaarEscrowOrderView.from(before))
                    || !Objects.equals(backings.get(transition.orderId()),
                    transition.beforeBacking().orElseThrow())) {
                throw conflict(
                        "Bazaar order backing ancestry conflicts");
            }
        }

        BazaarMutation.ApplyResult applied = commit.bookMutation().apply(
                orderBook);
        if (applied.replayed()) {
            throw conflict(
                    "Bazaar book mutation exists without its escrow receipt");
        }
        BazaarOrderBookSnapshot next = applied.snapshot();
        Map<UUID, BazaarOrder> afterOrders = indexOrders(next);
        for (BazaarEscrowOrderTransition transition
                : commit.orderTransitions()) {
            BazaarOrder after = afterOrders.get(transition.orderId());
            if (after == null || !transition.afterOrder().orElseThrow()
                    .equals(BazaarEscrowOrderView.from(after))) {
                throw conflict(
                        "Bazaar order transition result conflicts");
            }
            if (transition.afterBacking().isPresent()) {
                backings.put(transition.orderId(),
                        transition.afterBacking().orElseThrow());
            } else {
                backings.remove(transition.orderId());
            }
        }
        Map<UUID, String> commits = new HashMap<>(
                lifecycle.commitFingerprints());
        commits.put(commit.requestId(), fingerprint);
        BazaarEscrowLifecycleState updated =
                new BazaarEscrowLifecycleState(intents, commits, backings);
        validateAgainst(next, updated);
        return new ApplyResult(next, updated, false);
    }

    private static Map<UUID, BazaarOrder> indexOrders(
            BazaarOrderBookSnapshot snapshot
    ) {
        Map<UUID, BazaarOrder> result = new HashMap<>();
        for (BazaarOrder order : snapshot.orders()) {
            if (result.put(order.orderId(), order) != null) {
                throw conflict("Bazaar order identity is duplicated");
            }
        }
        return result;
    }

    private static BazaarEscrowLifecycleConflictException conflict(
            String message
    ) {
        return new BazaarEscrowLifecycleConflictException(message);
    }

    private static BazaarEscrowLifecycleConflictException conflict(
            String message,
            Throwable cause
    ) {
        return new BazaarEscrowLifecycleConflictException(message, cause);
    }

    public record ApplyResult(
            BazaarOrderBookSnapshot orderBook,
            BazaarEscrowLifecycleState lifecycle,
            boolean replayed
    ) {
        public ApplyResult {
            orderBook = Objects.requireNonNull(orderBook, "orderBook");
            lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        }
    }
}

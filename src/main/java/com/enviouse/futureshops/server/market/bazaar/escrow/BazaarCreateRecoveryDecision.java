package com.enviouse.futureshops.server.market.bazaar.escrow;

import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationReceipt;

import java.util.Objects;
import java.util.Optional;

public record BazaarCreateRecoveryDecision(
        Action action,
        Optional<BazaarCreateEscrowIntent> terminalIntent,
        String detail
) {
    public BazaarCreateRecoveryDecision {
        action = Objects.requireNonNull(action, "action");
        terminalIntent = Objects.requireNonNull(terminalIntent,
                "terminalIntent");
        detail = Objects.requireNonNull(detail, "detail").strip();
        if (detail.isEmpty() || detail.length() > 256
                || action == Action.ABORT_SAFE
                != terminalIntent.filter(value -> value.status()
                == BazaarCreateEscrowIntent.Status.ABORTED).isPresent()) {
            throw new IllegalArgumentException(
                    "Bazaar creation recovery decision is invalid");
        }
    }

    public static BazaarCreateRecoveryDecision inspect(
            BazaarCreateEscrowIntent intent,
            Optional<ItemInventoryMutationReceipt> committedReceipt
    ) {
        Objects.requireNonNull(intent, "intent");
        committedReceipt = Objects.requireNonNull(committedReceipt,
                "committedReceipt");
        if (intent.terminal()) {
            return new BazaarCreateRecoveryDecision(Action.ALREADY_TERMINAL,
                    Optional.empty(), "The creation intent is terminal");
        }
        if (intent.command().side()
                == com.enviouse.futureshops.server.market.bazaar
                .BazaarOrderSide.BUY) {
            return new BazaarCreateRecoveryDecision(Action.RESUME_COMMIT,
                    Optional.empty(), "The wallet hold is decided atomically");
        }
        if (committedReceipt.isEmpty()) {
            return new BazaarCreateRecoveryDecision(Action.ABORT_SAFE,
                    Optional.of(intent.transition(
                            BazaarCreateEscrowIntent.Status.ABORTED)),
                    "The item extraction was not committed");
        }
        if (committedReceipt.orElseThrow().equals(
                intent.sellCustody().orElseThrow().receipt())) {
            return new BazaarCreateRecoveryDecision(Action.RESUME_COMMIT,
                    Optional.empty(), "The exact item custody is committed");
        }
        return new BazaarCreateRecoveryDecision(Action.MANUAL_REVIEW,
                Optional.empty(), "The item custody receipt conflicts");
    }

    public enum Action {
        RESUME_COMMIT,
        ABORT_SAFE,
        MANUAL_REVIEW,
        ALREADY_TERMINAL
    }
}

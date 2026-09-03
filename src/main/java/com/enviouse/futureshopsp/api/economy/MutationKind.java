package com.enviouse.futureshopsp.api.economy;

/** Semantic role of one provider value leg. */
public enum MutationKind {
    WITHDRAW,
    DEPOSIT,
    TRANSFER_DEBIT,
    TRANSFER_CREDIT,
    FEE,
    REFUND,
    COMPENSATION
}

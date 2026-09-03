package com.pixelmonmod.pixelmon.api.economy;

import java.math.BigDecimal;
import java.util.UUID;

public interface BankAccount {
    UUID getIdentifier();

    BigDecimal getBalance();

    boolean hasBalance(BigDecimal amount);

    boolean take(BigDecimal amount);

    boolean add(BigDecimal amount);
}

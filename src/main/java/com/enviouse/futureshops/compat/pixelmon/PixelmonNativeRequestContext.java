package com.enviouse.futureshops.compat.pixelmon;

import com.enviouse.futureshops.api.economy.BindingV1;
import com.enviouse.futureshops.api.economy.LegId;
import com.enviouse.futureshops.api.economy.RootId;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/** Server thread context carried through one exact Pixelmon account mutation. */
public final class PixelmonNativeRequestContext {
    private static final ThreadLocal<Request> CURRENT = new ThreadLocal<>();

    private PixelmonNativeRequestContext() {
    }

    public static Scope open(BindingV1 binding, RootId rootId, LegId legId,
                             String operation, long amountMinorUnits, boolean debit) {
        return open(binding, rootId, legId, operation, amountMinorUnits, debit,
                binding.artifactFingerprint());
    }

    public static Scope open(BindingV1 binding, RootId rootId, LegId legId,
                             String operation, long amountMinorUnits, boolean debit,
                             String payloadFingerprint) {
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(rootId, "rootId");
        Objects.requireNonNull(legId, "legId");
        if (operation == null || operation.isBlank() || operation.length() > 64
                || amountMinorUnits <= 0L || payloadFingerprint == null
                || !payloadFingerprint.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("native request identity is invalid");
        }
        if (CURRENT.get() != null) {
            throw new IllegalStateException("nested Pixelmon mutation is refused");
        }
        Request request = new Request(binding, rootId, legId, operation,
                amountMinorUnits, debit, payloadFingerprint);
        CURRENT.set(request);
        return () -> {
            if (CURRENT.get() == request) {
                CURRENT.remove();
            }
        };
    }

    public static Optional<Request> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }

    public static final class Request {
        private final BindingV1 binding;
        private final RootId rootId;
        private final LegId legId;
        private final String operation;
        private final long amountMinorUnits;
        private final boolean debit;
        private final String payloadFingerprint;
        private BigDecimal resultingBalance;
        private String failure;

        private Request(BindingV1 binding, RootId rootId, LegId legId, String operation,
                        long amountMinorUnits, boolean debit, String payloadFingerprint) {
            this.binding = binding;
            this.rootId = rootId;
            this.legId = legId;
            this.operation = operation;
            this.amountMinorUnits = amountMinorUnits;
            this.debit = debit;
            this.payloadFingerprint = payloadFingerprint;
        }

        public BindingV1 binding() {
            return binding;
        }

        public RootId rootId() {
            return rootId;
        }

        public LegId legId() {
            return legId;
        }

        public String operation() {
            return operation;
        }

        public long amountMinorUnits() {
            return amountMinorUnits;
        }

        public boolean debit() {
            return debit;
        }

        public String payloadFingerprint() {
            return payloadFingerprint;
        }

        public Optional<BigDecimal> resultingBalance() {
            return Optional.ofNullable(resultingBalance);
        }

        public Optional<String> failure() {
            return Optional.ofNullable(failure);
        }

        void recordResult(BigDecimal balance) {
            resultingBalance = Objects.requireNonNull(balance, "balance");
            failure = null;
        }

        void recordFailure(String reason) {
            failure = Objects.requireNonNull(reason, "reason");
            resultingBalance = null;
        }
    }
}

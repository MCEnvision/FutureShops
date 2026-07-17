package com.enviouse.futureshops.server.escrow.runtime;

public final class EscrowMutationPermit {
    private static final ThreadLocal<Activation> ACTIVE = new ThreadLocal<>();

    EscrowMutationPermit() {
    }

    public Scope activate() {
        Activation current = ACTIVE.get();
        if (current == null) {
            ACTIVE.set(new Activation(this, 1));
        } else if (current.permit() == this) {
            ACTIVE.set(new Activation(this, Math.addExact(current.depth(), 1)));
        } else {
            throw new IllegalStateException("A different escrow mutation permit is active");
        }
        return new Scope(this, Thread.currentThread());
    }

    public boolean isActive() {
        Activation current = ACTIVE.get();
        return current != null && current.permit() == this && current.depth() > 0;
    }

    public static final class Scope implements AutoCloseable {
        private final EscrowMutationPermit permit;
        private final Thread ownerThread;
        private boolean closed;

        private Scope(EscrowMutationPermit permit, Thread ownerThread) {
            this.permit = permit;
            this.ownerThread = ownerThread;
        }

        @Override
        public void close() {
            if (closed) {
                throw new IllegalStateException("Escrow mutation permit scope is already closed");
            }
            if (Thread.currentThread() != ownerThread) {
                throw new IllegalStateException(
                        "Escrow mutation permit scope closed on a different thread");
            }
            Activation current = ACTIVE.get();
            if (current == null || current.permit() != permit || current.depth() < 1) {
                throw new IllegalStateException("Escrow mutation permit scope is not active");
            }
            if (current.depth() == 1) {
                ACTIVE.remove();
            } else {
                ACTIVE.set(new Activation(permit, current.depth() - 1));
            }
            closed = true;
        }
    }

    private record Activation(EscrowMutationPermit permit, int depth) {
    }
}

package com.enviouse.futureshopsp.server.economy;

import com.enviouse.futureshopsp.api.economy.ProviderLifecycle;

/**
 * Serializes provider lifecycle transitions on the logical server boundary.
 * Monetary admission is closed unless the lifecycle is ready.
 */
public final class EconomyLifecycleController {
    private final Object lock = new Object();
    private final String providerId;
    private ProviderLifecycle lifecycle = ProviderLifecycle.UNRESOLVED;
    private String diagnostic = "";
    private boolean cleanMarkerWritten;

    public EconomyLifecycleController(String providerId) {
        if (providerId == null || providerId.isBlank()) {
            throw new IllegalArgumentException("providerId must not be blank");
        }
        this.providerId = providerId;
    }

    public EconomyLifecycleSnapshot snapshot() {
        synchronized (lock) {
            return snapshotLocked();
        }
    }

    public void resolve(ProviderLifecycle resolvedLifecycle, String resolvedDiagnostic,
                        boolean cleanMarkerValid, boolean integrityValid, boolean hasIncompleteRecords) {
        synchronized (lock) {
            if (!cleanMarkerValid || !integrityValid || hasIncompleteRecords) {
                lifecycle = ProviderLifecycle.RECOVERING;
                diagnostic = sanitize("startup recovery is required");
                cleanMarkerWritten = false;
                return;
            }
            lifecycle = resolvedLifecycle == null ? ProviderLifecycle.INCOMPATIBLE : resolvedLifecycle;
            diagnostic = sanitize(resolvedDiagnostic);
            cleanMarkerWritten = false;
        }
    }

    public boolean admitQuery() {
        synchronized (lock) {
            return lifecycle == ProviderLifecycle.READY;
        }
    }

    public boolean admitMutation() {
        synchronized (lock) {
            return lifecycle == ProviderLifecycle.READY;
        }
    }

    public void markFailed(String message) {
        transition(ProviderLifecycle.FAILED, message);
    }

    public void markAmbiguous(String message) {
        transition(ProviderLifecycle.FROZEN, message);
    }

    public void beginDraining() {
        synchronized (lock) {
            if (lifecycle == ProviderLifecycle.READY) {
                lifecycle = ProviderLifecycle.DRAINING;
                diagnostic = "server shutdown is draining economy work";
                cleanMarkerWritten = false;
            }
        }
    }

    public boolean writeCleanMarkerLast(boolean journalFlushed, boolean custodyFlushed,
                                        boolean claimsFlushed, boolean checkpointFlushed) {
        synchronized (lock) {
            if (lifecycle != ProviderLifecycle.DRAINING
                    || !journalFlushed || !custodyFlushed || !claimsFlushed || !checkpointFlushed) {
                cleanMarkerWritten = false;
                return false;
            }
            cleanMarkerWritten = true;
            lifecycle = ProviderLifecycle.STOPPED;
            diagnostic = "clean shutdown marker written";
            return true;
        }
    }

    public void markUncleanStart() {
        synchronized (lock) {
            lifecycle = ProviderLifecycle.RECOVERING;
            diagnostic = "previous shutdown was unclean";
            cleanMarkerWritten = false;
        }
    }

    public void markRecovered() {
        synchronized (lock) {
            if (lifecycle == ProviderLifecycle.RECOVERING) {
                lifecycle = ProviderLifecycle.READY;
                diagnostic = "";
            }
        }
    }

    public boolean cleanMarkerWritten() {
        synchronized (lock) {
            return cleanMarkerWritten;
        }
    }

    private void transition(ProviderLifecycle next, String message) {
        synchronized (lock) {
            if (lifecycle == ProviderLifecycle.STOPPED) {
                return;
            }
            lifecycle = next;
            diagnostic = sanitize(message);
        }
    }

    private EconomyLifecycleSnapshot snapshotLocked() {
        boolean ready = lifecycle == ProviderLifecycle.READY;
        return new EconomyLifecycleSnapshot(providerId, lifecycle, diagnostic, ready, ready);
    }

    private static String sanitize(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        String singleLine = message.replace('\n', ' ').replace('\r', ' ').trim();
        return singleLine.length() <= 256 ? singleLine : singleLine.substring(0, 256);
    }
}

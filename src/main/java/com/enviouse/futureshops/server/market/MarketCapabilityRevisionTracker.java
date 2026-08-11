package com.enviouse.futureshops.server.market;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class MarketCapabilityRevisionTracker {
    public static final int MAXIMUM_SUBJECTS = 100000;
    public static final int MAXIMUM_FINGERPRINT_LENGTH = 1024;

    private static final UUID ZERO = new UUID(0L, 0L);

    private final int maximumSubjects;
    private final LinkedHashMap<UUID, RevisionState> states =
            new LinkedHashMap<>();
    private long nextRevision;

    public MarketCapabilityRevisionTracker(int maximumSubjects) {
        if (maximumSubjects <= 0
                || maximumSubjects > MAXIMUM_SUBJECTS) {
            throw new IllegalArgumentException(
                    "Market capability revision subject limit is invalid");
        }
        this.maximumSubjects = maximumSubjects;
    }

    public synchronized long revision(
            UUID subjectId,
            String stateFingerprint
    ) {
        UUID subject = requireId(subjectId);
        String fingerprint = requireFingerprint(stateFingerprint);
        RevisionState current = states.get(subject);
        if (current != null
                && current.fingerprint().equals(fingerprint)) {
            touch(subject, current);
            return current.revision();
        }
        long revision = Math.incrementExact(nextRevision);
        nextRevision = revision;
        touch(subject, new RevisionState(fingerprint, revision));
        trim();
        return revision;
    }

    public synchronized int size() {
        return states.size();
    }

    public synchronized void clear() {
        states.clear();
        nextRevision = 0L;
    }

    private void touch(UUID subject, RevisionState state) {
        states.remove(subject);
        states.put(subject, state);
    }

    private void trim() {
        while (states.size() > maximumSubjects) {
            Iterator<Map.Entry<UUID, RevisionState>> iterator =
                    states.entrySet().iterator();
            iterator.next();
            iterator.remove();
        }
    }

    private static UUID requireId(UUID value) {
        UUID result = Objects.requireNonNull(value, "subjectId");
        if (ZERO.equals(result)) {
            throw new IllegalArgumentException(
                    "Market capability revision subject is invalid");
        }
        return result;
    }

    private static String requireFingerprint(String value) {
        String result = Objects.requireNonNull(
                value, "stateFingerprint");
        if (result.isEmpty()
                || result.length() > MAXIMUM_FINGERPRINT_LENGTH) {
            throw new IllegalArgumentException(
                    "Market capability revision fingerprint is invalid");
        }
        return result;
    }

    private record RevisionState(String fingerprint, long revision) {
        private RevisionState {
            Objects.requireNonNull(fingerprint, "fingerprint");
            if (revision <= 0L) {
                throw new IllegalArgumentException(
                        "Market capability revision is invalid");
            }
        }
    }
}

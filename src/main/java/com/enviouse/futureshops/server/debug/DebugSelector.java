package com.enviouse.futureshops.server.debug;

import java.util.Objects;
import java.util.UUID;

/** A mutually exclusive capture selector. */
public record DebugSelector(Type type, String value, UUID actor) {
    public enum Type { NONE, REQUEST, ACTOR }

    public DebugSelector {
        Objects.requireNonNull(type, "type");
        if (type == Type.REQUEST && (value == null || value.isBlank())) {
            throw new IllegalArgumentException("request selector requires a value");
        }
        if (type == Type.ACTOR && actor == null) {
            throw new IllegalArgumentException("actor selector requires a player");
        }
        if (type != Type.REQUEST) {
            value = null;
        }
        if (type != Type.ACTOR) {
            actor = null;
        }
    }

    public static DebugSelector none() {
        return new DebugSelector(Type.NONE, null, null);
    }

    public static DebugSelector request(String rootRef) {
        return new DebugSelector(Type.REQUEST, rootRef, null);
    }

    public static DebugSelector actor(UUID player) {
        return new DebugSelector(Type.ACTOR, null, player);
    }

    boolean matches(String rootRef, UUID actorRef) {
        return switch (type) {
            case NONE -> true;
            case REQUEST -> Objects.equals(value, rootRef);
            case ACTOR -> Objects.equals(actor, actorRef);
        };
    }

    String display(UUID captureId) {
        return switch (type) {
            case NONE -> "none";
            case REQUEST -> "request:" + DebugDiagnostics.pseudonym(captureId, value);
            case ACTOR -> "actor:" + DebugDiagnostics.pseudonym(captureId, actor.toString());
        };
    }
}

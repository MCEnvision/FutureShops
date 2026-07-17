package com.enviouse.futureshops.server.security;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

final class ServerRequestSecurityLifecycle<T> {
    private Object owner;
    private T value;

    synchronized T initialize(
            Object nextOwner,
            Supplier<? extends T> factory
    ) {
        Objects.requireNonNull(nextOwner, "owner");
        Objects.requireNonNull(factory, "factory");
        if (value != null || owner != null) {
            throw new IllegalStateException(
                    "Server request security is already initialized");
        }

        owner = nextOwner;
        try {
            value = Objects.requireNonNull(factory.get(), "value");
            return value;
        } catch (RuntimeException | Error exception) {
            owner = null;
            value = null;
            throw exception;
        }
    }

    synchronized Optional<T> find(Object expectedOwner) {
        Objects.requireNonNull(expectedOwner, "owner");
        if (owner != expectedOwner || value == null) {
            return Optional.empty();
        }
        return Optional.of(value);
    }

    synchronized Optional<T> clear(Object expectedOwner) {
        Objects.requireNonNull(expectedOwner, "owner");
        if (value == null && owner == null) {
            return Optional.empty();
        }
        if (owner != expectedOwner) {
            throw new IllegalStateException(
                    "Server request security belongs to another server");
        }

        T previous = value;
        owner = null;
        value = null;
        return Optional.of(previous);
    }

    synchronized boolean active() {
        return owner != null && value != null;
    }
}

# phase 008 build and artifact evidence

Observation date: 2026-09-24  
Phase: CORE-PHASE-008  
Tasks: P008-TASK-004 and P008-TASK-005  
Loader: Forge 1.20.1 47.4.20  
Java: 17.0.19

## result

The complete Gradle verification command `./gradlew --no-daemon test build
--console=plain` completed successfully. The Java test reports contain 2,024
tests, zero failures, zero errors, and zero skipped tests across 380 report
files. The Forge GameTest server gate separately passed all nine required
server tests, including the real stale buy, cart, and sell packet handlers.

The resulting candidate is:

`build/libs/futureshops-3.0.0-beta.2.jar`

Its observed SHA256 is
`998b71ae4abe996dabf748c577ed344132aeb31fb7325ca8061cc8e0335cf2f8`.

The artifact was built twice from the same checkout. Both archives contained
the same entries. Every payload entry matched byte for byte. The only change
was the generated `Implementation-Timestamp` manifest value, and the
manifests matched after removing that timestamp. This confirms that the
compiled payload is stable across the repeated build; the raw archive hash is
expected to vary because the manifest records build time.

## boundaries

This evidence covers the Java build, unit suite, package assembly, and server
GameTest path. The required laptop client gate remains unverified because the
authorized `envision` host was not reachable from the execution host. No
client rendering, input, reconnect, or audio result is claimed here.

## cleanup

The disposable comparison copy in `/tmp/futureshops-phase008-before.jar` was
removed after the comparison. No disposable server runtime was retained.
The preexisting repository `run` directory and shared Gradle caches were left
untouched.

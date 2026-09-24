# Phase 001 diagnostics and dependency evidence

Observation date: September 24, 2026.

## Identity

The source checkout is the Forge 1.20.1 beta 3 phase branch at commit `5e89a08` before this
phase packet commit. The runtime uses Java 17.0.19, Minecraft 1.20.1, Forge 47.4.20, and the
Gradle wrapper in this checkout. The donor NeoForge branch was not changed.

## Diagnostics route

The bounded diagnostic component is default off and uses the fixed `DiagnosticEventV2` schema.
`ServerShopOfferService.execute` records the real service outcome and
`C2SServerShopOfferPacket` records the decoded packet dispatch. References are pseudonymous per
capture and the output worker is bounded and asynchronous. The unit suite covers selectors,
redaction, disabled cost, idempotent disable, and the per second cap. The source wiring test
guards both production call sites. The Forge GameTest batch exercises the actual server offer
service with a request selector.

The headless Forge GameTest command was:

```text
JAVA_HOME=/usr/lib/jvm/temurin-17-jdk-amd64 bash ./gradlew --no-daemon --console=plain runGameTestServer
```

The run started the game test server, loaded FutureShops, ran six tests in the
`futureshops.offer_service` batch, emitted one scoped `schema=2` shop event with pseudonymous
root, leg, and actor references, reported all six required tests passed, and shut down cleanly.
No raw UUID, balance, item NBT, token, or private path was retained in the diagnostic sample.

## Dependency graph

The Java 17 runtime graph was generated with:

```text
JAVA_HOME=/usr/lib/jvm/temurin-17-jdk-amd64 bash ./gradlew --no-daemon --console=plain dependencies --configuration runtimeClasspath
JAVA_HOME=/usr/lib/jvm/temurin-17-jdk-amd64 bash ./gradlew --no-daemon --console=plain dependencyInsight --dependency netty-handler --configuration runtimeClasspath
JAVA_HOME=/usr/lib/jvm/temurin-17-jdk-amd64 bash ./gradlew --no-daemon --console=plain dependencyInsight --dependency jline-reader --configuration runtimeClasspath
```

The graph resolves Netty 4.1.82.Final, JLine 3.12.1, Log4j 2.19.0, Commons IO 2.11.0, Commons
Compress 1.21, Commons Lang 3.12.0, Guava 31.1 jre, and Plexus Utils 3.3.0. GitHub currently
reports alerts 1 through 29. Every alert is classified in
`docs/security/dependency-alerts-3.0-beta.3.md` with its resolved version, first patched version,
platform owner, and bounded FutureShops reachability result.

The affected libraries are supplied by the Forge or Minecraft launcher graph and are not bundled
by FutureShops. No direct constraint or copied library was added because it would change only a
development classpath or create split packages instead of fixing player installations. Issue 9
therefore remains open for a compatible platform upgrade. This is an explicit upstream boundary,
not a security dismissal or a claim that the platform libraries are patched.

## Automation

The quality workflow push filter now targets `1.20.1/3.0.0-beta.2`, the actual Forge default
branch. The Gradle wrapper executable bit is restored so repository hooks and CI can invoke it
directly. The latest successful quality run before this phase was run `36032091762` for the phase
000 pull request. Dependabot has an active Gradle and GitHub Actions configuration and recent
successful grouped execution is recorded by pull request 28. The phase pull request will provide
fresh checks for the corrected versioned branch filter.

## Cleanup

The disposable `run/` GameTest runtime was created for this evidence and is not source data. The
server process exited and no Gradle or Forge test process remains. Raw logs, generated configs,
world data, and copied fixtures will be removed after this packet is committed. Shared Gradle
caches and tracked source are retained.

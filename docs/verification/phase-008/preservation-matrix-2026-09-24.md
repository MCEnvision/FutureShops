# phase 008 forge preservation matrix

Observation date: 2026-09-24
Phase: CORE-PHASE-008
Task: P008-TASK-004
Artifact: `build/libs/futureshops-3.0.0-beta.2.jar`
Artifact sha256: `90de004da0f098adb96ccbdb814578c7fc4edab9a880ffde9a409306ffa021d5`

## deterministic matrix

The complete Forge test suite was run with Java 17 through `./gradlew test build`.
It reported 2,024 tests with zero failures or errors. The suite includes the
existing preservation coverage for shop arithmetic and bounds, packet and cart
limits, NBT and deep identity matching, duplicate identifiers, atomic delivery,
barter rollback, quantity and payment entitlements, protected coin rescue and
mint handling, pricing, franchises and limits, registry and history, analytics,
storage, Bazaar, Auction House, player shops, escrow, recovery, and persistence.
The phase 008 codec, client state, server admission, session, and ledger closure
tests ran in the same suite.

## package and resource inspection

The candidate archive was inspected with `jar tf`. No NeoForge, Fabric, donor
loader, unsupported provider, or nested dependency entries were present. Source
inspection found only the existing unsupported provider identifier declaration
and the existing dependency boundary assertion. No donor protocol number or
donor store was copied into the Forge archive.

## boundary

This matrix proves deterministic Forge preservation and archive inspection. It
does not replace a dedicated server stale packet fixture, split host reconnect
proof, or laptop rendering and input evidence. Those gates remain open under
P008-TASK-002 and the phase completion packet.

## cleanup

The test reused the preexisting Gradle dependency and build directories. The
candidate artifact and sanitized evidence were retained for the phase gate.
The disposable server runtime used for the separate startup attempt was removed;
the preexisting repository `run` directory remains protected.

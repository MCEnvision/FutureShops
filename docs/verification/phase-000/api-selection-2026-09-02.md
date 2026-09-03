# Phase 000 API and Selection Verification

Date: 2026-09-02

This record covers the completed local implementation work for `CORE-REQ-001`, `CORE-REQ-002`, and `CORE-REQ-003` on branch `envy/plan-2.3.0-external-economy`. It does not claim Pixelmon or hybrid runtime compatibility.

## Revision and pins

The starting revision was `ed41bf304a80d2c8c29f27e36e6e1fd83d8c644a`. The implementation changes are uncommitted at the time this record is authored and will be bound to the implementation commit in the completion packet.

The project now declares:

- FutureShops `2.3.0`
- Minecraft `1.21.1`
- NeoForge `21.1.248`
- Java `21`
- Gradle wrapper `8.8`
- GeckoLib `4.8.4`

Only the requested mod and NeoForge version pins changed. No Pixelmon, Bukkit, Vault, bridge, or hybrid classes are on the standard compile or runtime path.

## Public provider contract

The new provider-neutral API is under `com.enviouse.futureshopsp.api.economy`. Compatibility version `1` defines:

- six independent capabilities, including balance query, precheck, withdraw, deposit, receipt lookup, and idempotent retry
- validated currency metadata and signed long minor-unit values
- request identity, mutation kind, typed receipts, lifecycle readiness, and typed result status
- explicit unavailable, ambiguous, and recovery-required outcomes
- deterministic registration and provider factory boundaries

The API package imports only the server context type from Minecraft. Source checks reject dependencies on FutureShops internal economy classes, Pixelmon, Bukkit, Vault, or bridge classes.

## Registry and selection

`EconomyProviderRegistry` validates lowercase identifiers, reserves `internal` and `vault`, rejects duplicate and late registration, verifies compatibility and readiness, and exposes an immutable sorted snapshot after freeze. Factory exceptions and metadata or capability mismatches resolve to typed failures.

`ProviderSelectionManager` stages reload values and activates one value at startup. An absent, blank, or null value selects `internal`. A changed value remains staged until restart. Unknown or missing providers never fall back to `internal`.

The legacy `BalanceManager` boundary now uses an unavailable provider for unresolved external selections. Balance reads fail closed and mutations cannot report success. Full transaction gating, journal, custody, claims, and recovery remain Phase 001 work.

## Deterministic verification

The following checks passed after the implementation and version pin changes.

```text
bash ./gradlew test --no-daemon
bash ./gradlew build --no-daemon
```

The test suite completed with 35 tests. Build completed successfully with only existing deprecation warnings. Task discovery confirms `test`, `build`, `runData`, `runGameTestServer`, `runServer`, and `runClient` tasks. No generated resources changed, so `runData` was not required. No new GameTest was added; the provider and selection fixtures are JUnit tests.

The dedicated server smoke used `bash ./gradlew runServer --no-daemon` with a bounded timeout. NeoForge loaded Minecraft `1.21.1`, NeoForge `21.1.248`, GeckoLib `4.8.4`, and FutureShops `2.3.0`; FutureShops common setup and server start completed, followed by clean bounded shutdown. No optional external provider was installed.

The client smoke used `xvfb-run -a timeout 60s bash ./gradlew runClient --no-daemon -Dorg.gradle.jvmargs=-Xmx4G`. GLFW initialized under Xvfb, FutureShops common setup completed, resource reload and texture atlas creation completed, and no FutureShops exception was recorded before the expected bounded timeout exit `124`. A direct headless run is not viable on this workstation because GLFW reports `glfwInit failed` and the launcher also reports Java heap exhaustion.

The built jar is `build/libs/futureshops-2.3.0.jar` and contains the API, registry, selection, and fail-closed boundary classes. Its hashes are:

```text
sha256 f23cb5985281a4f5eb06c3290a0317df04793ed6cbf74cf6bbdfc20de1873e61
sha512 252ad41491cc8beb12054691d0f40b992bc3a30144a1e2989dd3eb6b25464f5b300585026cd17452e32c3c7b743eaab9ddaf4867adcfc1975a419db0c2fef966
```

The rendered NeoForge metadata reports version `2.3.0`. Build output, run directories, caches, logs, and the external Pixelmon jar remain untracked.

## External evidence and blockers

The exact Pixelmon `1.21.1-9.4.0` artifact and its query and precheck surface are recorded in [baseline-2026-09-02.md](baseline-2026-09-02.md). The reviewed API has no durable request receipt lookup or idempotent retry contract, so direct production mutation remains unsupported under `DEC-018`. No unsafe boolean mutation adapter was added.

Development access, the exact hybrid stack, and owner terms acceptance remain external blockers. The disposable environment remains at `eula=false`; no full launch, mutation, restart, recovery, or plugin registration claim was made. Issue 66 remains frozen for Phase 003 and was not queried or changed during execution.

## Remaining phase work

Phase 001 must add the central gate, write-ahead journal, custody, claims, draining, frozen, recovery, and restart protections before any external mutation can be considered. Phase 002 owns the separately versioned Pixelmon and Vault adapters. Phase 003 owns artifact validation and the sole permitted update and readback of issue 66.

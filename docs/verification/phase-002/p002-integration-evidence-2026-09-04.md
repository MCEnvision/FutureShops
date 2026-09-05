# Phase 002 integration evidence

This record covers the native Pixelmon transaction path, the Vault proof boundary, and the headless first diagnostics procedure for FutureShops `2.3.0` on Minecraft `1.21.1` and NeoForge `21.1.248`.

## Reproducibility manifest

| Field | Value |
| --- | --- |
| Source revision | `a7fef2f` |
| FutureShops artifact | `build/libs/futureshops-2.3.0.jar` |
| FutureShops SHA 256 | `e25fd666a88a8fbabd6d8f844ef8af8cd947c13f27a32c77b4a07a84a92e02a4` |
| FutureShops SHA 512 | `86ea2169aae6f9d9ddd8d80ee6ad48f8f8aae4e9608edf103c160ee3cf5300798c633d610ace7e5eaa8e131db248e2e6a0403f58943ff450f25833534ba00f97` |
| Pixelmon artifact | `/tmp/Pixelmon-1.21.1-9.4.0-universal.jar` |
| Pixelmon SHA 256 | `9020393f98382ae8794ef2694e7bec1984c1a0eca735ea3eea06e0cb151c61f2` |
| Minecraft | `1.21.1` |
| NeoForge | `21.1.248` |
| Java | `21.0.11` |
| Host | `node-1`, Linux amd64, dedicated server runtime |

The Pixelmon jar was used only as an external development and runtime input. It is not bundled, modified, or redistributed by FutureShops.

## Native Pixelmon GameTest

Command:

```text
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 bash ./gradlew runGameTestServer --no-daemon
```

The disposable Gradle GameTest runtime loaded the exact Pixelmon jar and selected `pixelmon` in the test configuration. Sixteen required tests passed. The native test created a live `PlayerPartyStorage`, seeded `100` PokéDollars, withdrew `25` with a FutureShops request UUID, replayed that UUID, and observed a single debit with balance `75`. The mixin wrote `FutureShopsReceipts` beside Pixelmon's `pixelDollars` data. A new storage instance reloaded the receipt and returned `CONFIRMED`. An injected unknown receipt record returned `RECOVERY_REQUIRED` and was retained for later reconciliation.

Sanitized server evidence:

```text
futureshops.pixelmon.gametest account=com.pixelmonmod.pixelmon.api.storage.PlayerPartyStorage native_access=true preflight=NONE
futureshops.pixelmon.gametest native mutation confirmed request=64e304a1-6470-4efd-a098-91b0a935b6cf replay=64e304a1-6470-4efd-a098-91b0a935b6cf balance=75 receipt_nbt=true reload=CONFIRMED unknown_recovery=RECOVERY_REQUIRED
All 16 required tests passed :)
```

The dedicated server stopped and saved its disposable world after the run. The repository test configuration was restored to `provider = "internal"`.

## Vault bridge and backend proof

`EconomyProviderRegistry.registerVault` is the only public registration boundary for the reserved `vault` provider. `VaultTransactionProofTest` registers a separate test provider through that boundary and exercises a durable backend fixture outside the production jar. The fixture writes the new balance and the provider receipt to one forced temporary file, then uses an atomic replacement. A reopened backend confirms the receipt and balance. An injected interruption before replacement leaves the previous balance and no receipt visible, and a retry commits once with the same request identity.

Commands:

```text
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 bash ./gradlew test --tests com.enviouse.futureshopsp.api.economy.VaultTransactionProofTest --no-daemon
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 bash ./gradlew build --no-daemon
```

The fixture proves the required bridge contract and is not a claim that the reviewed PixelmonEconomyBridge or FinalEconomy stack is transaction aware. That unmodified hybrid stack remains safely refused for `vault` mutation until a separately installed bridge and backend provide the same request receipt and recovery guarantees.

## Headless debug command evidence

The exact disposable profile had `eula=true`, Pixelmon `9.4.0`, GeckoLib `4.8.4`, NeoForge `21.1.248`, and the candidate artifact installed. The server was launched with no client connection and received:

```text
futureshops debug on pixelmon
futureshops debug status
futureshops debug off
futureshops debug status
stop
```

The sanitized log records used the named `futureshops.debug` category, session correlation, source and artifact fields, Minecraft and loader versions, module, operation, lifecycle, capability, validation, receipt, custody, claim, error, elapsed time, server side, thread, and next action fields. The session was ephemeral and status returned `debug=off` after disable. Raw profile output was not retained in the repository. The temporary log SHA 256 was `2dbb979797c038df4b6a8c7a7c1fd2efa674e48c3bc6675e4fe257cdc1855a68`.

No laptop client run was needed. The phase acceptance criteria exercised server authority, persistence, provider receipts, retry behavior, and recovery, all represented by deterministic tests and dedicated server logs. A laptop remains reserved for a later client only criterion such as rendered UI, real input, client classloading, or visual synchronization.

## Cleanup

The exact server process exited, the provider configuration in the repository runtime was restored to `internal`, the temporary runtime probe jar was removed from the disposable Pixelmon profile, and the profile's previous FutureShops jar was restored. Only this sanitized evidence record and the requested source changes remain.

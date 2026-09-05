# Phase 002 integration evidence

This record covers the native Pixelmon transaction path, the Vault proof boundary, and the headless first diagnostics procedure for FutureShops `2.3.0` on Minecraft `1.21.1` and NeoForge `21.1.248`.

## Reproducibility manifest

| Field | Value |
| --- | --- |
| Source revision | `6138eb8d6c7217d425f3840f5dae362ca2db27f0` |
| FutureShops artifact | `build/libs/futureshops-2.3.0.jar` |
| FutureShops SHA 256 | `5122aa663537e179abdab7bf30efda4c09f080cb9fcc7328c0eb2e4d8650b59c` |
| FutureShops SHA 512 | `e5d004902837bbe96078cebf590f1c0df2c556c6f272ec42fddc131eb14bd68e62c6a0e73f5c934870167a9e8812b93fd1efc1191ae9085a6abe11bf0e1b2cba` |
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

The disposable Gradle GameTest runtime loaded the exact Pixelmon jar and selected `pixelmon` in the test configuration. The source revision passed all twenty required tests. The native tests exercised the live `PlayerPartyStorage` path, cart purchase, `/pay` transfer, server shop sell, admin player shop buy, public withdraw and deposit, refund, compensation, physical money refusal, and direct mutation recovery. The mixin wrote `FutureShopsReceipts` beside Pixelmon's `pixelDollars` data. A replayed request UUID produced one debit, a reopened storage instance returned `CONFIRMED`, and injected unknown compound, non compound entry, and non compound root records returned `RECOVERY_REQUIRED` and remained available for reconciliation.

Sanitized server evidence from the current artifact bound restart run:

```text
FutureShops Pixelmon mixin target com.pixelmonmod.pixelmon.api.storage.PlayerPartyStorage apply true
futureshops.pixelmon.gametest native mutation confirmed balance=75 receipt_nbt=true reload=CONFIRMED reconnect_replay=CONFIRMED reloaded_balance=75 unknown_recovery=RECOVERY_REQUIRED wrong_type_recovery=RECOVERY_REQUIRED wrong_root_recovery=RECOVERY_REQUIRED
futureshops.pixelmon.gametest process_restart phase=FIRST request=00000000-0000-0000-0000-000000000241 balance=75 receipt=COMPLETED
futureshops.pixelmon.gametest process_restart phase=SECOND request=00000000-0000-0000-0000-000000000241 replay=CONFIRMED balance=75
All 20 required tests passed :)
```

The sanitized source revision GameTest evidence SHA 256 is `403e9a9f01ace4d209f4d81a35b01c6dc875d5e5092111a276a3ce8f3f57db57`.

The test also reads the Pixelmon save adapter file with `NbtIo.read` after the mutation and asserts that the completed request UUID and state are present on disk. The save boundary forces the file before this check. This is the durable receipt proof for the native mixin path.

The dedicated server stopped and saved its disposable world after the run. The repository test configuration was restored to `provider = "internal"`.

The same headless GameTest launcher was run from a fresh temporary game directory with no Pixelmon jar and `provider = "internal"`. The standard NeoForge environment loaded FutureShops, skipped the optional Pixelmon target, reached `FutureShops server starting`, and passed the required tests. The sanitized absence log SHA 256 is `ec61fb40021a76e1505c3a1819facd2ad84911531bf3195176a99c6d1a4733c0`. The exact Pixelmon jar remained external and was not copied into the temporary absence directory.

## Wrong root recovery follow up on 2026-09-05

The GameTest was rerun after adding an explicit non compound `FutureShopsReceipts` root case, a native request replay after storage reload, and a two pass process restart probe. The exact Pixelmon `9.4.0` jar was loaded from `/tmp/Pixelmon-1.21.1-9.4.0-universal.jar` with SHA 256 `9020393f98382ae8794ef2694e7bec1984c1a0eca735ea3eea06e0cb151c61f2`. The FutureShops source revision was `1378859c6d8ecf63731e0e7a0ed858c2131ab83a`.

The disposable launcher used the checked in GameTest argument files and the merged NeoForge development artifact. The classpath was derived with:

```text
sed 's#build/moddev/artifacts/neoforge-21.1.248.jar#build/moddev/artifacts/neoforge-21.1.248-merged.jar#' build/moddev/gameTestServerRunClasspath.txt > <temporary classpath file>
```

It then launched `net.neoforged.devlaunch.Main` from a fresh temporary game directory containing only the exact Pixelmon, GeckoLib, configuration, and FutureShops test jar inputs. EULA was verified as `eula=true` before launch.

Sanitized evidence:

```text
FutureShops Pixelmon mixin target com.pixelmonmod.pixelmon.api.storage.PlayerPartyStorage apply true
futureshops.pixelmon.gametest native mutation confirmed request=cd8c5440-ff5b-41e7-af92-2801f3554d5c replay=cd8c5440-ff5b-41e7-af92-2801f3554d5c balance=75 receipt_nbt=true reload=CONFIRMED reconnect_replay=CONFIRMED reloaded_balance=75 unknown_recovery=RECOVERY_REQUIRED wrong_type_recovery=RECOVERY_REQUIRED wrong_root_recovery=RECOVERY_REQUIRED
All 20 required tests passed :)
Game test server shutting down
```

The sanitized evidence SHA 256 is `403e9a9f01ace4d209f4d81a35b01c6dc875d5e5092111a276a3ce8f3f57db57`. It combines the first and second process logs and proves the same request completed once before restart and replayed as `CONFIRMED` afterward. The rebuilt artifact SHA 256 is `93df52f3232a38a5f3007a98a51d551d905aac82e56602c0c657f0bbf836c915`, and its SHA 512 is `bf1e621057a166adf02c761553916b76b31ca28d65fa054e267d96b7929b58b5d26b4aae883553dc670397bfefdbde3ec97021758271c4dec3d501df6a857cbb`. The jar manifest records source revision `1378859c6d8ecf63731e0e7a0ed858c2131ab83a`.

The packaged artifact was separately started in a fresh exact Pixelmon dedicated server. It loaded the same mixin target, reached `Done (4.827s)`, logged `FutureShops server starting.`, and stopped cleanly. The sanitized packaged server evidence SHA 256 is `5353736683b24a72305944600ef4030e30258f340dcabdfe11edc3c0e233eced`.

The prior packaged jar was launched in a fresh disposable exact Pixelmon dedicated server with the `forgeserver` target. FutureShops 2.3.0, Pixelmon 9.4.0, and the mixin target loaded successfully before the server reached `Done` and stopped cleanly. The prior artifact-bound packaged server evidence SHA 256 is `77f85dbac94dc7995566f9e0bb24b5151cd3279f7eb66170bbfa0e4e447cd1ff`. The artifact manifest recorded source revision `c56f79f7a67c2a6c2cf2c3f2264e20e96f646e50`. Existing Pixelmon world warnings about a missing spawning tag and `Not a map: END` were absent from the retained evidence subset and are not FutureShops failures. The current artifact-bound hybrid server evidence is recorded below.

The same final artifact then ran in one disposable GameTest world across two fresh server processes. The first process wrote the completed native receipt and a restart marker. After that process shut down, the second process loaded the saved Pixelmon storage, replayed the same request UUID, and confirmed the persisted balance without a second debit.

Sanitized process restart evidence:

```text
FutureShops Pixelmon mixin target com.pixelmonmod.pixelmon.api.storage.PlayerPartyStorage apply true
futureshops.pixelmon.gametest process_restart phase=FIRST request=00000000-0000-0000-0000-000000000241 balance=75 receipt=COMPLETED
All 20 required tests passed :)
Game test server shutting down
FutureShops Pixelmon mixin target com.pixelmonmod.pixelmon.api.storage.PlayerPartyStorage apply true
futureshops.pixelmon.gametest process_restart phase=SECOND request=00000000-0000-0000-0000-000000000241 replay=CONFIRMED balance=75
All 20 required tests passed :)
Game test server shutting down
```

The sanitized two process evidence SHA 256 is `403e9a9f01ace4d209f4d81a35b01c6dc875d5e5092111a276a3ce8f3f57db57`. The disposable runtime and marker were removed after hashing.

## Artifact bound native GameTest rerun on 2026-09-05

The current production artifact from source revision `6138eb8d6c7217d425f3840f5dae362ca2db27f0` was launched from a fresh temporary game directory with the exact Pixelmon `1.21.1-9.4.0` jar, GeckoLib `4.8.4`, the merged NeoForge `21.1.248` development artifact, and `provider = "pixelmon"`. The launcher verified `eula=true` and supplied `server.properties` before startup. This run used the artifact itself rather than a development classes directory.

Sanitized evidence:

```text
FutureShops Pixelmon mixin target com.pixelmonmod.pixelmon.api.storage.PlayerPartyStorage apply true
FutureShops server starting.
futureshops.pixelmon.gametest native mutation confirmed request=1143c38b-7995-414b-8c69-1f5a7e7e8bcd replay=1143c38b-7995-414b-8c69-1f5a7e7e8bcd balance=75 receipt_nbt=true reload=CONFIRMED reconnect_replay=CONFIRMED reloaded_balance=75 unknown_recovery=RECOVERY_REQUIRED wrong_type_recovery=RECOVERY_REQUIRED wrong_root_recovery=RECOVERY_REQUIRED
futureshops.pixelmon.gametest process_restart phase=FIRST request=00000000-0000-0000-0000-000000000241 balance=75 receipt=COMPLETED
All 20 required tests passed :)
Game test server shutting down
```

The first process log SHA 256 is `203ff647978cab9807e42afb81aad4b7867232619a639bd3d435826dd47f107f`. A second process reused the same disposable world and proved the persisted restart replay:

```text
FutureShops Pixelmon mixin target com.pixelmonmod.pixelmon.api.storage.PlayerPartyStorage apply true
futureshops.pixelmon.gametest native mutation confirmed request=a00b33c3-a59e-46c6-8206-54079327b18d replay=a00b33c3-a59e-46c6-8206-54079327b18d balance=75 receipt_nbt=true reload=CONFIRMED reconnect_replay=CONFIRMED reloaded_balance=75 unknown_recovery=RECOVERY_REQUIRED wrong_type_recovery=RECOVERY_REQUIRED wrong_root_recovery=RECOVERY_REQUIRED
futureshops.pixelmon.gametest process_restart phase=SECOND request=00000000-0000-0000-0000-000000000241 replay=CONFIRMED balance=75
All 20 required tests passed :)
Game test server shutting down
```

The second process log SHA 256 is `12e8c58609c1e2401edc349d8caece4ef130c1a91b55ccb11e21b470e2e95091`. Both processes exited with code `0`. The runtime emitted an existing Pixelmon missing spawning tag warning, with no FutureShops exception. The runs stopped cleanly and their temporary runtime, classpath file, worlds, and logs were removed after hashing. These reruns bind the native mixin and all twenty tests to the current production jar whose SHA 256 is `5122aa663537e179abdab7bf30efda4c09f080cb9fcc7328c0eb2e4d8650b59c`.

## Vault bridge and backend proof

`EconomyProviderRegistry.registerVault` is the only public registration boundary for the reserved `vault` provider. `VaultTransactionProofTest` registers a separate test provider through that boundary and exercises a durable SQLite backend fixture outside the production jar. The fixture writes the new balance and provider receipt in one forced SQLite transaction. A reopened backend confirms the receipt and balance. Injected interruptions before commit roll back both rows, while an interruption after commit leaves a lookupable receipt and a retry commits no second effect. Reuse with a conflicting amount, mutation kind, or actor is rejected as `INVALID_REQUEST`. Insufficient funds remains `INSUFFICIENT_FUNDS` across retry, and concurrent identical requests converge on one durable receipt and one balance delta. Receipt lookup scans persisted state before a new mutation, so a request cannot create a second effect under another account state.

The proof backend now uses SQLite with `journal_mode=DELETE`, `synchronous=FULL`, a primary key on the request UUID, and one database transaction containing both the balance update and receipt insert. It injects interruption after the balance update, after receipt insertion, before commit, and after commit. A fresh backend instance looks up the committed receipt after the ambiguous post commit result and retry does not debit again. The exact hybrid server also loaded the separately packaged registrant beside the unmodified Pixelmon, Vault, FinalEconomy, EverNifeCore, and PixelmonEconomyBridge jars. The registrant registered `vault` through the public API and executed one precheck, withdraw, lookup, and duplicate retry at server start. See [exact hybrid Vault proof](vault-hybrid-proof-2026-09-05.md) for hashes, sanitized logs, database rows, and cleanup evidence.

Commands:

```text
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 bash ./gradlew test --tests com.enviouse.futureshopsp.api.economy.VaultTransactionProofTest --no-daemon
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 bash ./gradlew build --no-daemon
```

The fixture proves the required bridge contract and is not a claim that the reviewed PixelmonEconomyBridge or FinalEconomy stack is transaction aware. That unmodified hybrid stack remains safely refused for `vault` mutation until a separately installed bridge and backend provide the same request receipt and recovery guarantees. The exact hybrid proof registrant is a disposable test component and is not part of the FutureShops production jar.

## Headless debug command evidence

The exact disposable profile had `eula=true`, Pixelmon `9.4.0`, GeckoLib `4.8.4`, NeoForge `21.1.248`, and the rebuilt artifact installed. The server was launched with no client connection and received:

```text
futureshops debug on pixelmon
futureshops debug status
futureshops debug off
futureshops debug status
stop
```

The sanitized log records used the named `futureshops.debug` category, session correlation, source and artifact fields, Minecraft and loader versions, module, operation, lifecycle, capability, validation, receipt, custody, claim, error, elapsed time, server side, thread, and next action fields. The source commit and artifact SHA were discovered from the packaged manifest and loaded mod file, not supplied by the operator. The session was ephemeral and status returned `debug=off` after disable. Raw profile output was not retained in the repository. The sanitized server debug log SHA 256 is `c8ec4f3d273207598c8ed181cd8077d9d3b7a0ba9c305cc5e67fe0e3f618bcf5`. That earlier session records source commit `da4028ca23baf34c4058ea5f32b8e1c0b10051ca` and its artifact hash. The debug command procedure remains unchanged and current artifact binding is recorded in the reproducibility manifest above.

No laptop client run was needed. The phase acceptance criteria exercised server authority, persistence, provider receipts, retry behavior, and recovery, all represented by deterministic tests and dedicated server logs. A laptop remains reserved for a later client only criterion such as rendered UI, real input, client classloading, or visual synchronization.

## Cleanup

The exact server process exited, the provider configuration in the repository runtime was restored to `internal`, the temporary runtime probe jar was removed from the disposable Pixelmon profile, and the profile's previous FutureShops jar was restored. Only this sanitized evidence record and the requested source changes remain.

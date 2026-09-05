# Phase 002 integration evidence

This record covers the native Pixelmon transaction path, the Vault proof boundary, and the headless first diagnostics procedure for FutureShops `2.3.0` on Minecraft `1.21.1` and NeoForge `21.1.248`.

## Reproducibility manifest

| Field | Value |
| --- | --- |
| Source revision | `6bbaf9156bcd8d79bee274717f2ae67d4db6f69e` |
| FutureShops artifact | `build/libs/futureshops-2.3.0.jar` |
| FutureShops SHA 256 | `945d175c363ec06f6b0e965161cff081c5deebf1b1ed899e605b48890fc69563` |
| FutureShops SHA 512 | `0e38cc66eaaf739413f5a8b2f193d97aca40ea4e8c5be18c4f9f29999ccbc6a1a8055045028a796183b623bf6e4d49478134683b23dbe31445e0db96fc02bae2` |
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

Sanitized server evidence from the earlier artifact bound restart run:

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

The current production artifact from source revision `490f78d3374d663d17b1be608e1cafc91f0ca840` was launched from a fresh temporary game directory with the exact Pixelmon `1.21.1-9.4.0` jar, GeckoLib `4.8.4`, the merged NeoForge `21.1.248` development artifact, and `provider = "pixelmon"`. The launcher verified `eula=true` and supplied `server.properties` before startup. This run used the artifact itself rather than a development classes directory.

Sanitized evidence:

```text
FutureShops Pixelmon mixin target com.pixelmonmod.pixelmon.api.storage.PlayerPartyStorage apply true
FutureShops server starting.
futureshops.pixelmon.gametest native mutation confirmed request=d4a0c5d5-7e98-41e1-9a6e-2639f245843e replay=d4a0c5d5-7e98-41e1-9a6e-2639f245843e balance=75 receipt_nbt=true reload=CONFIRMED reconnect_replay=CONFIRMED reloaded_balance=75 unknown_recovery=RECOVERY_REQUIRED wrong_type_recovery=RECOVERY_REQUIRED wrong_root_recovery=RECOVERY_REQUIRED
futureshops.pixelmon.gametest process_restart phase=FIRST request=00000000-0000-0000-0000-000000000241 balance=75 receipt=COMPLETED
All 20 required tests passed :)
Game test server shutting down
```

The first process log SHA 256 is `512976106c5f973fade79206bcd9721d847763968f90f218df00d89d88a80c16`. A second process reused the same disposable world and proved the persisted restart replay:

```text
FutureShops Pixelmon mixin target com.pixelmonmod.pixelmon.api.storage.PlayerPartyStorage apply true
futureshops.pixelmon.gametest native mutation confirmed request=2b0ce758-123a-4a1b-8d1c-47e760892915 replay=2b0ce758-123a-4a1b-8d1c-47e760892915 balance=75 receipt_nbt=true reload=CONFIRMED reconnect_replay=CONFIRMED reloaded_balance=75 unknown_recovery=RECOVERY_REQUIRED wrong_type_recovery=RECOVERY_REQUIRED wrong_root_recovery=RECOVERY_REQUIRED
futureshops.pixelmon.gametest process_restart phase=SECOND request=00000000-0000-0000-0000-000000000241 replay=CONFIRMED balance=75
All 20 required tests passed :)
Game test server shutting down
```

The second process log SHA 256 is `7eb49c64fb39d36d8fdc9676b53cb2f88f725d48a5dadc9cf36d5e83a7d1cd66`. Both processes exited with code `0`. The runtime emitted an existing Pixelmon missing spawning tag warning, with no FutureShops exception. The runs stopped cleanly and their temporary runtime, classpath file, worlds, and logs were removed after hashing. These reruns bind the native mixin and all twenty tests to the previous production artifact whose SHA 256 is `b56fd75fc96968eac8c05c50d6ff71be24e2c8472b43ad26fe4208535c2aa145`.

## Historical artifact bound rerun on 2026-09-05

The service loss regression coverage was added after the previous artifact evidence. That earlier exact production artifact was `d7d2e14b192644859a276114508ceb2c5aed8991931aab523b899ffa9d0e4ad3`; its evidence is retained as historical input. The current packaged artifact rerun is recorded below.

The earlier exact run used the `d7d2e14b192644859a276114508ceb2c5aed8991931aab523b899ffa9d0e4ad3` artifact. Its first process log SHA 256 is `8e0548e72f7f714717f7ac08ef0b6d21bfa40f91f3b35747d11702540cfd861c`, and its second process log SHA 256 is `68a231774fd9a26e6eeedc287968d25b5eca0e4b3a5be653697a63b8a6057c7d`.

The current packaged artifact from source revision `5bb0199b355d12b6671a310cf8acd3857c67f77d` has SHA 256 `f97805026224e435d00ed6478f6d122313bc99d44628fa9033602fd15d36173d` and SHA 512 `e319285ac9069b12c3b12701deba8c92cd430dcf6c9b12e7a038886799f1d924a732d164400992307b70ee64ed06cf4bd74faee8971d15587856b80b4eea42cf`. It was loaded as the mod file in a fresh exact Pixelmon GameTest runtime. The first packaged process passed all twenty tests and recorded a completed receipt with log SHA 256 `581766494f275acde701caf26a5fe0a605004bccb3ce4c83f3099256a5fe4f24`. The second process reused the world, replayed the stable request as `CONFIRMED`, and passed all twenty tests with log SHA 256 `a1ae7dfa69e5fd9b9b0360b70c45dd70de214a6528990f5edfadc3577a8ff4bc`. Both packaged processes exited with code `0`, the mixin target applied, and the disposable runtime was removed after hashing.

This historical rerun binds the native mixin, request replay, receipt reload, wrong root recovery, and every routed native surface to that packaged artifact. The exact standard packaged server also reached `FutureShops common setup complete`, `FutureShops server starting`, and `Done` with Pixelmon absent and `provider = "internal"`. Its sanitized log SHA 256 is `12640336ff4cf3ff399aea0933c106bcedf1f0b43ed98abe869439aa572381aa`.

The coordinator unit suite also covers provider service loss. Loss before intent returns `UNAVAILABLE` with no journal entry or provider mutation. Loss after intent returns `AMBIGUOUS`, freezes lifecycle, records `UNCERTAIN`, and does not fall back to the internal wallet.

## Current artifact bound rerun on 2026-09-05

The current packaged artifact from source revision `6bbaf9156bcd8d79bee274717f2ae67d4db6f69e` has SHA 256 `945d175c363ec06f6b0e965161cff081c5deebf1b1ed899e605b48890fc69563` and SHA 512 `0e38cc66eaaf739413f5a8b2f193d97aca40ea4e8c5be18c4f9f29999ccbc6a1a8055045028a796183b623bf6e4d49478134683b23dbe31445e0db96fc02bae2`. The exact Pixelmon `9.4.0` GameTest passed all twenty tests with log SHA 256 `dcc938fbb0cff3cc487f2f573fc6087023c727da511fadc398b98aa675cfcb4c`. A separate restart process passed all twenty tests with log SHA 256 `d78bbea1e02f0ac119adbcfaaa3d3e208d57d2e432650d108c521a37091d869a`. The mixin target applied and both processes exited with code `0`.

The same current artifact and separately installed proof registrant passed all twenty five exact NeoForge Vault tests in one packaged runtime. The combined route and failure log SHA 256 is `3eb85c4e24ee3a7b66469b6796e3ac720350477731595bf85cece3a6688bd4fc`. It confirms server shop sell, player shop buy, cart buy, pay transfer, physical money refusal, typed service loss, interrupted commits, durable lookup, retry deduplication, late registration, and missing provider refusal with no incomplete custody. Full assertions are recorded in [exact Vault surface GameTest](vault-surface-gametest-2026-09-05.md) and [exact Vault failure and recovery matrix](vault-failure-matrix-2026-09-05.md).

The same current artifact also passed the exact Vault failure and recovery matrix in that combined run. All twenty five required tests passed, including typed service loss, each interrupted SQLite boundary, authoritative receipt lookup after a post commit acknowledgement loss, retry deduplication, duplicate and late registration refusal, missing provider resolution, and final balance conservation. See [exact Vault failure and recovery matrix](vault-failure-matrix-2026-09-05.md).

## Exact hybrid refusal without proof bridge

A separate fresh exact hybrid runtime omitted the proof registrant and kept the unmodified PixelmonEconomyBridge, FinalEconomy, EverNifeCore, and Vault stack. With `provider = "vault"`, FutureShops reached `Done` without registering a provider. The bounded debug command reported `provider=none`, `lifecycle=RECOVERING`, and `observed_capabilities=none`, then the server stopped cleanly. No provider mutation was attempted and no proof jar was present in the runtime. The sanitized refusal and debug log SHA 256 is `425463560881196d018416bab76203b1aff786b20e2e05e5769df7d7c75ab41c`.

The current production artifact was then exercised in the exact reviewed hybrid stack with the separate proof registrant. The first process log SHA 256 is `da65861f3fe7d3b0fb53cf00bc832884339106cc494b19a67a58398f77f4bca6`, and the restart process log SHA 256 is `2e1a5d09df895135d4683e6832fce0e6230feb0734bf3ca00c57c758f7298d86`. Both processes reached `Done` and stopped. The first proof transaction confirmed all provider and coordinator routes. The restart replayed stable requests, reported `transfer=REPLAYED`, and retained balance `89` without a second effect. Component hashes remain bound by [exact hybrid Vault proof](vault-hybrid-proof-2026-09-05.md).

## Vault bridge and backend proof

`EconomyProviderRegistry.registerVault` is the only public registration boundary for the reserved `vault` provider. `VaultTransactionProofTest` registers a separate test provider through that boundary and exercises a durable SQLite backend fixture outside the production jar. The fixture writes the new balance and provider receipt in one forced SQLite transaction. A reopened backend confirms the receipt and balance. Injected interruptions before commit roll back both rows, while an interruption after commit leaves a lookupable receipt and a retry commits no second effect. Reuse with a conflicting amount, mutation kind, or actor is rejected as `INVALID_REQUEST`. Insufficient funds remains `INSUFFICIENT_FUNDS` across retry, and concurrent identical requests converge on one durable receipt and one balance delta. Receipt lookup scans persisted state before a new mutation, so a request cannot create a second effect under another account state.

The proof backend now uses SQLite with `journal_mode=DELETE`, `synchronous=FULL`, a primary key on the request UUID, and one database transaction containing both the balance update and receipt insert. It injects interruption after the balance update, after receipt insertion, before commit, and after commit. A fresh backend instance looks up the committed receipt after the ambiguous post commit result and retry does not debit again. The exact hybrid server also loaded the separately packaged registrant beside the unmodified Pixelmon, Vault, FinalEconomy, EverNifeCore, and PixelmonEconomyBridge jars. The registrant registered `vault` through the public API and executed coordinator precheck, withdrawal, deposit, refund, compensation, custodied deposit, custody claim transitions, transfer debit and credit, provider lookup, and duplicate retry. A second server process replayed all stable request IDs, skipped the already applied synthetic transfer, and retained the proof account balance `89` without another effect. The updated proof registrant was built from source revision `5471b8f1c10e8cd3eb79dc49f91f1b0f1bd2c89b` and has SHA 256 `df91158865e7c75b80bfb5eea4d07478f41b6b18f54d1740274a86d95e29826b`. See [exact hybrid Vault proof](vault-hybrid-proof-2026-09-05.md) for hashes, sanitized logs, database rows, and cleanup evidence.

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

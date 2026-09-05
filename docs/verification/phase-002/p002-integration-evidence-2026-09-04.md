# Phase 002 integration evidence

This record covers the native Pixelmon transaction path, the Vault proof boundary, and the headless first diagnostics procedure for FutureShops `2.3.0` on Minecraft `1.21.1` and NeoForge `21.1.248`.

## Reproducibility manifest

| Field | Value |
| --- | --- |
| Source revision | `e97f04df315ca6886b897e5b43bb1df0fb116062` |
| FutureShops artifact | `build/libs/futureshops-2.3.0.jar` |
| FutureShops SHA 256 | `b83c9b22bbb92f872c31ae7329fff4368d2a2efb549e10b8174ce31dc4909ef2` |
| FutureShops SHA 512 | `f96903d411458a7b04be60752c9a133c99876dd62441588ce5d44c0c43c4fe61144db3c0b8bbb9af359b6c83dce3323f2c3f74a01156cf25c430ed8b06ab9f15` |
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

The disposable Gradle GameTest runtime loaded the exact Pixelmon jar and selected `pixelmon` in the test configuration. The source revision passed all nineteen required tests. The native tests exercised the live `PlayerPartyStorage` path, cart purchase, `/pay` transfer, server shop sell, admin player shop buy, public withdraw and deposit, refund, compensation, physical money refusal, and direct mutation recovery. The mixin wrote `FutureShopsReceipts` beside Pixelmon's `pixelDollars` data. A replayed request UUID produced one debit, a reopened storage instance returned `CONFIRMED`, and injected unknown compound and non compound receipt records both returned `RECOVERY_REQUIRED` and remained available for reconciliation.

Sanitized server evidence:

```text
futureshops.pixelmon.gametest native cart state diamonds_before=0 diamonds_after=1 balance=500
futureshops.pixelmon.gametest native pay state same_account=false payer_preflight=NONE recipient_preflight=NONE success=true error=OK payer_result=875 payer_live=875 recipient_live=125
futureshops.pixelmon.gametest native server sell state items_before=1 items_after=0 balance=25 stock_before=100 stock_after=101
futureshops.pixelmon.gametest native mutation confirmed request=979325f2-5dbd-442e-b1e2-450378f4fda5 replay=979325f2-5dbd-442e-b1e2-450378f4fda5 balance=75 receipt_nbt=true reload=CONFIRMED unknown_recovery=RECOVERY_REQUIRED wrong_type_recovery=RECOVERY_REQUIRED
futureshops.pixelmon.gametest native admin shop buy state diamonds_before=0 diamonds_after=1 balance=99 escrow_before=0 escrow_after=1
futureshops.pixelmon.gametest native public mutations withdrawal=true deposit=true refund=CONFIRMED compensation=CONFIRMED balance=185
All 19 required tests passed :)
```

The sanitized source revision GameTest evidence SHA 256 is `8f204619413118b1598cb345f33552c89c8c25f2a08e05579649f5dd31743ddb`.

The test also reads the Pixelmon save adapter file with `NbtIo.read` after the mutation and asserts that the completed request UUID and state are present on disk. The save boundary forces the file before this check. This is the durable receipt proof for the native mixin path.

The dedicated server stopped and saved its disposable world after the run. The repository test configuration was restored to `provider = "internal"`.

The same headless GameTest launcher was run from a fresh temporary game directory with no Pixelmon jar and `provider = "internal"`. The standard NeoForge environment loaded FutureShops, skipped the optional Pixelmon target, reached `FutureShops server starting`, and passed all nineteen required tests. The sanitized absence log SHA 256 is `ec61fb40021a76e1505c3a1819facd2ad84911531bf3195176a99c6d1a4733c0`. The exact Pixelmon jar remained external and was not copied into the temporary absence directory.

## Wrong root recovery follow up on 2026-09-05

The GameTest was rerun after adding an explicit non compound `FutureShopsReceipts` root case, a native request replay after storage reload, and a two pass process restart probe. The exact Pixelmon `9.4.0` jar was loaded from `/tmp/Pixelmon-1.21.1-9.4.0-universal.jar` with SHA 256 `9020393f98382ae8794ef2694e7bec1984c1a0eca735ea3eea06e0cb151c61f2`. The FutureShops source revision was `e97f04df315ca6886b897e5b43bb1df0fb116062`.

The disposable launcher used the checked in GameTest argument files and the merged NeoForge development artifact. The classpath was derived with:

```text
sed 's#build/moddev/artifacts/neoforge-21.1.248.jar#build/moddev/artifacts/neoforge-21.1.248-merged.jar#' build/moddev/gameTestServerRunClasspath.txt > <temporary classpath file>
```

It then launched `net.neoforged.devlaunch.Main` from a fresh temporary game directory containing only the exact Pixelmon, GeckoLib, configuration, and FutureShops test jar inputs. EULA was verified as `eula=true` before launch.

Sanitized evidence:

```text
FutureShops Pixelmon mixin target com.pixelmonmod.pixelmon.api.storage.PlayerPartyStorage apply true
futureshops.pixelmon.gametest native mutation confirmed request=cd8c5440-ff5b-41e7-af92-2801f3554d5c replay=cd8c5440-ff5b-41e7-af92-2801f3554d5c balance=75 receipt_nbt=true reload=CONFIRMED reconnect_replay=CONFIRMED reloaded_balance=75 unknown_recovery=RECOVERY_REQUIRED wrong_type_recovery=RECOVERY_REQUIRED wrong_root_recovery=RECOVERY_REQUIRED
All 19 required tests passed :)
Game test server shutting down
```

The sanitized evidence SHA 256 is `7232136da6e02d0bd6be34a28208089c55c16f4488742545a7ffde4252f5ce06`. It combines the first and second process logs and proves the same request completed once before restart and replayed as `CONFIRMED` afterward. The rebuilt artifact SHA 256 is `b83c9b22bbb92f872c31ae7329fff4368d2a2efb549e10b8174ce31dc4909ef2`, and its SHA 512 is `f96903d411458a7b04be60752c9a133c99876dd62441588ce5d44c0c43c4fe61144db3c0b8bbb9af359b6c83dce3323f2c3f74a01156cf25c430ed8b06ab9f15`. The jar manifest records source revision `e97f04df315ca6886b897e5b43bb1df0fb116062`.

The packaged artifact was separately started in a fresh exact Pixelmon dedicated server. It loaded the same mixin target, reached `Done (4.827s)`, logged `FutureShops server starting.`, and stopped cleanly. The sanitized packaged server evidence SHA 256 is `5353736683b24a72305944600ef4030e30258f340dcabdfe11edc3c0e233eced`.

The rebuilt packaged jar was also launched in a fresh disposable exact Pixelmon dedicated server with the `forgeserver` target. FutureShops 2.3.0, Pixelmon 9.4.0, and the mixin target loaded successfully before the server reached `Done` and stopped cleanly. The current artifact-bound packaged server evidence SHA 256 is `627df50f69243ea97abb8a299e8a714439fc94e440e316f76fdcf5504b185071`. Existing Pixelmon world warnings about a missing spawning tag and `Not a map: END` were absent from the retained evidence subset and are not FutureShops failures.

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

The sanitized two process evidence SHA 256 is `7232136da6e02d0bd6be34a28208089c55c16f4488742545a7ffde4252f5ce06`. The disposable runtime and marker were removed after hashing.

## Vault bridge and backend proof

`EconomyProviderRegistry.registerVault` is the only public registration boundary for the reserved `vault` provider. `VaultTransactionProofTest` registers a separate test provider through that boundary and exercises a durable backend fixture outside the production jar. The fixture writes the new balance and the provider receipt to one forced temporary file, then uses an atomic replacement. A reopened backend confirms the receipt and balance. An injected interruption before replacement leaves the previous balance and no receipt visible, and a retry commits once with the same request identity.

Commands:

```text
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 bash ./gradlew test --tests com.enviouse.futureshopsp.api.economy.VaultTransactionProofTest --no-daemon
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 bash ./gradlew build --no-daemon
```

The fixture proves the required bridge contract and is not a claim that the reviewed PixelmonEconomyBridge or FinalEconomy stack is transaction aware. That unmodified hybrid stack remains safely refused for `vault` mutation until a separately installed bridge and backend provide the same request receipt and recovery guarantees.

## Headless debug command evidence

The exact disposable profile had `eula=true`, Pixelmon `9.4.0`, GeckoLib `4.8.4`, NeoForge `21.1.248`, and the rebuilt artifact installed. The server was launched with no client connection and received:

```text
futureshops debug on pixelmon
futureshops debug status
futureshops debug off
futureshops debug status
stop
```

The sanitized log records used the named `futureshops.debug` category, session correlation, source and artifact fields, Minecraft and loader versions, module, operation, lifecycle, capability, validation, receipt, custody, claim, error, elapsed time, server side, thread, and next action fields. The source commit and artifact SHA were discovered from the packaged manifest and loaded mod file, not supplied by the operator. The session was ephemeral and status returned `debug=off` after disable. Raw profile output was not retained in the repository. The sanitized server debug log SHA 256 is `c8ec4f3d273207598c8ed181cd8077d9d3b7a0ba9c305cc5e67fe0e3f618bcf5`. That earlier session records source commit `da4028ca23baf34c4058ea5f32b8e1c0b10051ca` and its artifact hash. The current commit-bound artifact is `8eb43362d48fc6ea5962e747029714fd875f2c44cfe97ec12370efe1ace8a23e`; the debug command procedure remains unchanged.

No laptop client run was needed. The phase acceptance criteria exercised server authority, persistence, provider receipts, retry behavior, and recovery, all represented by deterministic tests and dedicated server logs. A laptop remains reserved for a later client only criterion such as rendered UI, real input, client classloading, or visual synchronization.

## Cleanup

The exact server process exited, the provider configuration in the repository runtime was restored to `internal`, the temporary runtime probe jar was removed from the disposable Pixelmon profile, and the profile's previous FutureShops jar was restored. Only this sanitized evidence record and the requested source changes remain.

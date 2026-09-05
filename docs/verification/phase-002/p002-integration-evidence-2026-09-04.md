# Phase 002 integration evidence

This record covers the native Pixelmon transaction path, the Vault proof boundary, and the headless first diagnostics procedure for FutureShops `2.3.0` on Minecraft `1.21.1` and NeoForge `21.1.248`.

## Reproducibility manifest

| Field | Value |
| --- | --- |
| Source revision | `31fb88a0fd9fefd8855f9b3bfba9df3e185bfad5` |
| FutureShops artifact | `build/libs/futureshops-2.3.0.jar` |
| FutureShops SHA 256 | `8eb43362d48fc6ea5962e747029714fd875f2c44cfe97ec12370efe1ace8a23e` |
| FutureShops SHA 512 | `09db2cfc2a134105eca726ab5bbf01edd58eda6856287372e84037ea9e31c0fa18ef5d0d5eefc8236000497e84c72bd8255c43a16140488c4dd04968408fec4e` |
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

The rebuilt packaged jar was also launched in a fresh disposable exact Pixelmon dedicated server with the `forgeserver` target. FutureShops 2.3.0, Pixelmon 9.4.0, and the mixin target loaded successfully before the server reached `Done` and stopped cleanly. The current artifact-bound packaged server evidence SHA 256 is `4d321708476d2c95102fa97574f142001fdfb74d13388c0277e7f03c2c598773`. Existing Pixelmon world warnings about a missing spawning tag and `Not a map: END` were absent from the retained evidence subset and are not FutureShops failures.

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

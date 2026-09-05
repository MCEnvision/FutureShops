# Phase 002 integration evidence

This record covers the native Pixelmon transaction path, the Vault proof boundary, and the headless first diagnostics procedure for FutureShops `2.3.0` on Minecraft `1.21.1` and NeoForge `21.1.248`.

## Reproducibility manifest

| Field | Value |
| --- | --- |
| Source revision | `3b9abd35dceb1171bf98d4015f490dc9dbb282d1` |
| FutureShops artifact | `build/libs/futureshops-2.3.0.jar` |
| FutureShops SHA 256 | `dc33fa50171b7df1d749765a111877bc410d0691ea117b7a16245b5dddd30606` |
| FutureShops SHA 512 | `2e6987b3f7d1e465d8e21f3d2cd93a9610f92c9d5b50dc55f3f6d04c25f1b8f046fcfc0a88cd3f9992feeb4490d66391edd9fdb7902e84a99e5b0a8e60a34274` |
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

The disposable Gradle GameTest runtime loaded the exact Pixelmon jar and selected `pixelmon` in the test configuration. The packaged artifact run passed all nineteen required tests. The native tests exercised the live `PlayerPartyStorage` path, cart purchase, `/pay` transfer, server shop sell, admin player shop buy, public withdraw and deposit, refund, compensation, physical money refusal, and direct mutation recovery. The mixin wrote `FutureShopsReceipts` beside Pixelmon's `pixelDollars` data. A replayed request UUID produced one debit, a reopened storage instance returned `CONFIRMED`, and an injected unknown receipt record returned `RECOVERY_REQUIRED` and remained available for reconciliation.

Sanitized server evidence:

```text
futureshops.pixelmon.gametest native cart state diamonds_before=0 diamonds_after=1 balance=500
futureshops.pixelmon.gametest native pay state same_account=false payer_preflight=NONE recipient_preflight=NONE success=true error=OK payer_result=875 payer_live=875 recipient_live=125
futureshops.pixelmon.gametest native server sell state items_before=1 items_after=0 balance=25 stock_before=100 stock_after=101
futureshops.pixelmon.gametest native mutation confirmed request=306070ff-5e7a-4349-afa5-c07eac06eb7f replay=306070ff-5e7a-4349-afa5-c07eac06eb7f balance=75 receipt_nbt=true reload=CONFIRMED unknown_recovery=RECOVERY_REQUIRED
futureshops.pixelmon.gametest native admin shop buy state diamonds_before=0 diamonds_after=1 balance=99 escrow_before=0 escrow_after=1
futureshops.pixelmon.gametest native public mutations withdrawal=true deposit=true refund=CONFIRMED compensation=CONFIRMED balance=185
All 19 required tests passed :)
```

The packaged exact Pixelmon log SHA 256 is `2a08502fc0f47e20dd310af009d121616b0631817da31a1ab953840448ea7e49`. The corresponding sanitized server log SHA 256 is `8dfd6798b4a3fd161dbe17d881580cf32f8b0b3e2f6f427a4ffff94a0084977b`.

The dedicated server stopped and saved its disposable world after the run. The repository test configuration was restored to `provider = "internal"`.

The same headless GameTest launcher was run from a fresh temporary game directory with no Pixelmon jar. The standard NeoForge environment loaded FutureShops, skipped the optional Pixelmon target, reached `FutureShops server starting`, and passed all nineteen required tests. The sanitized absence log SHA 256 is `54e3b3d1cfacc87b32e7680e9d007ba61d288a9098f0ae30885ccec61b6a8455`. The exact Pixelmon jar remained external and was not copied into the temporary absence directory.

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

The sanitized log records used the named `futureshops.debug` category, session correlation, source and artifact fields, Minecraft and loader versions, module, operation, lifecycle, capability, validation, receipt, custody, claim, error, elapsed time, server side, thread, and next action fields. The source commit and artifact SHA were discovered from the packaged manifest and loaded mod file, not supplied by the operator. The session was ephemeral and status returned `debug=off` after disable. Raw profile output was not retained in the repository. The console capture SHA 256 is `c480f1a0574ac63f90aa4a94c1d101948cde13f32e6f40ac8e9f67b042559d95` and the sanitized server log SHA 256 is `93e27cf42ebfca90f1a7209cdd025e62a49532c01717632abe321361129e2327`.

No laptop client run was needed. The phase acceptance criteria exercised server authority, persistence, provider receipts, retry behavior, and recovery, all represented by deterministic tests and dedicated server logs. A laptop remains reserved for a later client only criterion such as rendered UI, real input, client classloading, or visual synchronization.

## Cleanup

The exact server process exited, the provider configuration in the repository runtime was restored to `internal`, the temporary runtime probe jar was removed from the disposable Pixelmon profile, and the profile's previous FutureShops jar was restored. Only this sanitized evidence record and the requested source changes remain.

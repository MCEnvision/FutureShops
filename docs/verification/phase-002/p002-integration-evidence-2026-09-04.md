# Phase 002 integration evidence

This record covers the native Pixelmon transaction path, the Vault proof boundary, and the headless first diagnostics procedure for FutureShops `2.3.0` on Minecraft `1.21.1` and NeoForge `21.1.248`.

## Reproducibility manifest

| Field | Value |
| --- | --- |
| Source revision | `694d7886b7253d25d94b5260b26b3a70576dbb82` |
| FutureShops artifact | `build/libs/futureshops-2.3.0.jar` |
| FutureShops SHA 256 | `306bcdb2febaa3fbbb7b93af50fe7d0ef030e0953df90ce45db65ff145635e90` |
| FutureShops SHA 512 | `113bf340f396a3fa8eb4a3d706ef36ec03db9f03e27cf6e78b465e10ff8423f1abee23150b2dc65efdf9dc95945fa97148351ffe349e271b008c78c5bfd577a8` |
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

The disposable Gradle GameTest runtime loaded the exact Pixelmon jar and selected `pixelmon` in the test configuration. The packaged artifact run passed all eighteen required tests. The native tests exercised the live `PlayerPartyStorage` path, cart purchase, `/pay` transfer, server shop sell, admin player shop buy, physical money refusal, and direct mutation recovery. The mixin wrote `FutureShopsReceipts` beside Pixelmon's `pixelDollars` data. A replayed request UUID produced one debit, a reopened storage instance returned `CONFIRMED`, and an injected unknown receipt record returned `RECOVERY_REQUIRED` and remained available for reconciliation.

Sanitized server evidence:

```text
futureshops.pixelmon.gametest native cart state diamonds_before=0 diamonds_after=1 balance=500
futureshops.pixelmon.gametest native pay state same_account=false payer_preflight=NONE recipient_preflight=NONE success=true error=OK payer_result=875 payer_live=875 recipient_live=125
futureshops.pixelmon.gametest native server sell state items_before=1 items_after=0 balance=25 stock_before=100 stock_after=101
futureshops.pixelmon.gametest native mutation confirmed request=306070ff-5e7a-4349-afa5-c07eac06eb7f replay=306070ff-5e7a-4349-afa5-c07eac06eb7f balance=75 receipt_nbt=true reload=CONFIRMED unknown_recovery=RECOVERY_REQUIRED
futureshops.pixelmon.gametest native admin shop buy state diamonds_before=0 diamonds_after=1 balance=99 escrow_before=0 escrow_after=1
All 18 required tests passed :)
```

The packaged exact Pixelmon log SHA 256 is `e2cfc1e7460fd52435f4ebba64e0af2ca21c9aa1f4d3b8f0cd2f96dc686e6159`.

The dedicated server stopped and saved its disposable world after the run. The repository test configuration was restored to `provider = "internal"`.

The same headless GameTest launcher was run from a fresh temporary game directory with no Pixelmon jar. The standard NeoForge environment loaded FutureShops, skipped the optional Pixelmon target, reached `FutureShops server starting`, and passed all eighteen required tests. The sanitized absence log SHA 256 is `3b827fd0dcf6b78316956dfd5bc5603183823b6667fa9d07f0cd09fd219ea81d`. The exact Pixelmon jar remained external and was not copied into the temporary absence directory.

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

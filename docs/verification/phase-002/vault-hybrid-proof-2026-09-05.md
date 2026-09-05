# Exact hybrid Vault proof

This record covers the separately installed Vault proof registrant and its durable SQLite backend in the exact disposable hybrid runtime. It does not certify the unmodified PixelmonEconomyBridge or FinalEconomy implementation for FutureShops mutations.

## Runtime manifest

| Field | Value |
| --- | --- |
| Host | `node-1`, Linux amd64, headless dedicated server |
| Minecraft | `1.21.1` |
| NeoForge | `21.1.248` |
| Java | `21.0.11` |
| Youer | `1.21.1-d4a204a0` |
| FutureShops source | `6138eb8d6c7217d425f3840f5dae362ca2db27f0` |
| FutureShops artifact SHA 256 | `5122aa663537e179abdab7bf30efda4c09f080cb9fcc7328c0eb2e4d8650b59c` |
| FutureShops artifact SHA 512 | `e5d004902837bbe96078cebf590f1c0df2c556c6f272ec42fddc131eb14bd68e62c6a0e73f5c934870167a9e8812b93fd1efc1191ae9085a6abe11bf0e1b2cba` |
| Proof registrant SHA 256 | `0807e1a846119dc3408809a619b5e9369c711e8a0c1ca98c77896e1619576842` |
| SQLite JDBC SHA 256 | `e697df15be3f95219d80773c5f1002030e33e932adda186c1c86fd51df6691a9` |
| EULA | `eula=true` |
| selected provider | `vault` |

The external component identities used by the runtime were:

| Component | SHA 256 |
| --- | --- |
| Pixelmon `9.4.0` | `9020393f98382ae8794ef2694e7bec1984c1a0eca735ea3eea06e0cb151c61f2` |
| GeckoLib `4.8.4` | `a1b6ce25e8627aa7e748672eedb6b71af68e0993462313649c259f38e42bcac9` |
| Vault `1.7.3` | `a6b5ed97f43a5cf5bbaf00a7c8cd23c5afc9bd003f849875af8b36e6cf77d01d` |
| FinalEconomy `1.0.9` | `4cc7ba1aab02fffd86d2aa009a51ac4e6ca8590776ce9a13c8a2f45fdf01f529` |
| EverNifeCore `2.0.4.4` | `15585a223a76c7bf18b311aa3e07db71ef4a1969837608a9db1d27e99c52f6e3` |
| PixelmonEconomyBridge `1.1.6` | `409896ee42f4163b616c5ab0964c220fc0a1c910ce8c3e2a0c05c4d78bd21da6` |

The SQLite JDBC dependency is test and proof fixture input only. It is not present in the production FutureShops jar. Its Maven POM declares the Apache 2.0 license.

## Exact hybrid launch

The runtime was assembled in a fresh temporary directory from the exact Youer profile, with a fresh world, the exact external jars above, `futureshops-2.3.0.jar`, and the separately packaged `futureshops-vault-proof-1.0.0.jar`. The production jar contains no proof classes, SQLite classes, Bukkit classes, Vault classes, or Pixelmon classes.

The server reached `Done`, loaded all four Bukkit plugins, loaded Pixelmon `9.4.0`, applied the FutureShops native Pixelmon mixin target, and stopped through the server `stop` command. The registrant registered `vault` through `EconomyProviderRegistry.registerVault` before the registry froze. FutureShops resolved that provider for the selected restart configuration. The hybrid stack emitted an asynchronous EverNifeCore configuration save `ConcurrentModificationException` warning during startup. It did not prevent FutureShops startup or the proof transaction, and no FutureShops exception was observed.

Sanitized server evidence:

```text
FutureShops Pixelmon mixin target com.pixelmonmod.pixelmon.api.storage.PlayerPartyStorage apply true
Bukkit plugins (4): EverNifeCore (2.0.4.4), FinalEconomy (1.0.9), PixelmonEconomyBridge (1.1.6), Vault (1.7.3-b131)
NeoForge mod loading, version 21.1.248, for MC 1.21.1
FutureShops Vault proof registration status=ACCEPTED provider=vault
Loading Pixelmon version 9.4.0
FutureShops server starting.
FutureShops Vault proof transaction provider_precheck=CONFIRMED coordinator_precheck=CONFIRMED withdrawal=CONFIRMED lookup=CONFIRMED retry=CONFIRMED balance=75
Done (30.560s)! For help, type "help"
FutureShops server stopping.
```

The complete disposable launch log SHA 256 is `c4d8dd960401cd20a7233a43c98060f029b042d3ca25111437033de93f69b760`.

The registrant startup callback used request `00000000-0000-0000-0000-000000000510`, actor `00000000-0000-0000-0000-000000000410`, kind `WITHDRAW`, and amount `25`. It called the public provider precheck, the FutureShops coordinator preflight, and the FutureShops coordinator withdrawal once, then looked up the request and retried the same request identity through the provider. Every result was `CONFIRMED`; the resulting balance was `75`.

The SQLite file was queried after shutdown. Its durable rows were:

```text
request_id=00000000-0000-0000-0000-000000000510 actor=00000000-0000-0000-0000-000000000410 kind=WITHDRAW amount=25 external_id=vault:00000000-0000-0000-0000-000000000510 resulting_balance=75
account_id=00000000-0000-0000-0000-000000000410 balance=75
```

The durable database SHA 256 is `014f5cdc1b439478b73e66febde181ac8cd1233c1de21ccf4f042a94dfe29de6`. The backend uses one SQLite transaction for the balance row and receipt row, `journal_mode=DELETE`, `synchronous=FULL`, a primary key on `request_id`, and a bounded busy timeout.

The same run persisted the FutureShops receipt audit under `world/data/futureshops/receipts`. It retained `PREPARED`, `EXTERNAL_PENDING`, `EXTERNAL_CONFIRMED`, and `RESOLVED` records for request `00000000-0000-0000-0000-000000000510`, followed by a checksummed `.clean` marker. The coordinator therefore left a local recovery lineage while the SQLite provider receipt remained authoritative for retry.

## Boundary proof

`VaultTransactionProofTest` runs against the same public provider contract and SQLite backend. It proves:

* registration through the reserved `vault` boundary,
* exact integer balance conversion and deterministic insufficient funds,
* one transaction for balance and receipt,
* rollback after balance update, receipt insert, and before commit,
* fresh process lookup after a committed but unacknowledged result,
* stable request lookup and idempotent retry,
* conflicting actor, kind, or amount rejection, and
* four concurrent identical requests producing one receipt and one balance delta.

The focused proof command was:

```text
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 bash ./gradlew clean test --tests com.enviouse.futureshopsp.api.economy.VaultTransactionProofTest vaultProofJar --rerun-tasks --no-daemon
```

It passed. The separate registrant jar is a first party proof component. It does not add a production Vault adapter, modify any external jar, or make the current PixelmonEconomyBridge and FinalEconomy stack transaction aware. That legacy stack remains refused by FutureShops unless a bridge and backend provide this same request receipt, lookup, retry, conversion, and recovery contract.

## Cleanup

The dedicated server exited cleanly. The temporary hybrid runtime, world, logs, SQLite database, and launcher output were removed after the sanitized evidence and hashes were recorded. The exact external jars and the shared Gradle dependency cache were left in their pre existing locations.

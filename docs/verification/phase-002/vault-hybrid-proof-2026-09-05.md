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
| FutureShops source | `c56f79f7a67c2a6c2cf2c3f2264e20e96f646e50` |
| FutureShops artifact SHA 256 | `85c6bfae68020ef98bacf509dd9999bf03f913b2bdd895eb5a082bdf2e62d5f6` |
| FutureShops artifact SHA 512 | `51775974d3885ea98eb61d25fe9d8d1e3740d99ea94f898f23a3e7140d82c0dd821cd51d946df31bc710713a7f722577e498bafb597c48ab376c6f1d0f2d732b` |
| Proof registrant SHA 256 | `c3ddf897bfdfd3f9b77a07a9d8f9aaa190f2f17308b99359e06b2a043375fc06` |
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

The server reached `Done`, loaded all four Bukkit plugins, loaded Pixelmon `9.4.0`, applied the FutureShops native Pixelmon mixin target, and stopped through the server `stop` command. The registrant registered `vault` through `EconomyProviderRegistry.registerVault` before the registry froze. FutureShops resolved that provider for the selected restart configuration.

Sanitized server evidence:

```text
FutureShops Pixelmon mixin target com.pixelmonmod.pixelmon.api.storage.PlayerPartyStorage apply true
Bukkit plugins (4): EverNifeCore (2.0.4.4), FinalEconomy (1.0.9), PixelmonEconomyBridge (1.1.6), Vault (1.7.3-b131)
NeoForge mod loading, version 21.1.248, for MC 1.21.1
FutureShops Vault proof registration status=ACCEPTED provider=vault
Loading Pixelmon version 9.4.0
FutureShops server starting.
FutureShops Vault proof transaction precheck=CONFIRMED withdrawal=CONFIRMED lookup=CONFIRMED retry=CONFIRMED balance=75
Done (30.560s)! For help, type "help"
FutureShops server stopping.
```

The complete disposable launch log SHA 256 is `387ea732efe6f19964a9f7d3cfb590652e844cb5fefc5075f4af4df3f09d0ac6`.

The registrant startup callback used request `00000000-0000-0000-0000-000000000510`, actor `00000000-0000-0000-0000-000000000410`, kind `WITHDRAW`, and amount `25`. It called the public provider precheck, withdrew once, looked up the request, and retried the same request identity. Every result was `CONFIRMED`; the resulting balance was `75`.

The SQLite file was queried after shutdown. Its durable rows were:

```text
request_id=00000000-0000-0000-0000-000000000510 actor=00000000-0000-0000-0000-000000000410 kind=WITHDRAW amount=25 external_id=vault:00000000-0000-0000-0000-000000000510 resulting_balance=75
account_id=00000000-0000-0000-0000-000000000410 balance=75
```

The durable database SHA 256 is `014f5cdc1b439478b73e66febde181ac8cd1233c1de21ccf4f042a94dfe29de6`. The backend uses one SQLite transaction for the balance row and receipt row, `journal_mode=DELETE`, `synchronous=FULL`, a primary key on `request_id`, and a bounded busy timeout.

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

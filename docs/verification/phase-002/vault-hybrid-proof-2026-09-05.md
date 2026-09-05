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
| FutureShops source | `490f78d3374d663d17b1be608e1cafc91f0ca840` |
| FutureShops artifact SHA 256 | `b56fd75fc96968eac8c05c50d6ff71be24e2c8472b43ad26fe4208535c2aa145` |
| FutureShops artifact SHA 512 | `f0d6eb7b7660506816131184c5ce55f5edbc4853284321f16fd41bf5740ebd1ac3a2d542a397e52ccc707d2c5717356f89390e8ca5f05a4a2f6c346cfbda8442` |
| Proof registrant SHA 256 | `46d8daa58a6019c6bac166ffbe684c2f018d88665df21f49c88e6b022ef84336` |
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

The server reached `Done`, loaded all four Bukkit plugins, loaded Pixelmon `9.4.0`, applied the FutureShops native Pixelmon mixin target, and stopped through the server `stop` command. The registrant registered `vault` through `EconomyProviderRegistry.registerVault` before the registry froze. FutureShops resolved that provider for the selected restart configuration. The hybrid stack emitted unrelated warnings for absent Create classes, and the restart pass emitted six `Not a map: END` parser errors from the external stack. They did not prevent FutureShops startup or the proof transaction, and no FutureShops exception was observed.

Sanitized server evidence:

```text
FutureShops Pixelmon mixin target com.pixelmonmod.pixelmon.api.storage.PlayerPartyStorage apply true
Bukkit plugins (4): EverNifeCore (2.0.4.4), FinalEconomy (1.0.9), PixelmonEconomyBridge (1.1.6), Vault (1.7.3-b131)
NeoForge mod loading, version 21.1.248, for MC 1.21.1
FutureShops Vault proof registration status=ACCEPTED provider=vault
Loading Pixelmon version 9.4.0
FutureShops server starting.
FutureShops Vault proof transaction provider_precheck=CONFIRMED coordinator_precheck=CONFIRMED withdrawal=CONFIRMED lookup=CONFIRMED retry=CONFIRMED deposit=CONFIRMED refund=CONFIRMED compensation=CONFIRMED custody=CONFIRMED custody_state=CLAIMED claim_state_initial=PENDING claim_state_delivered=DELIVERED claim_state_resolved=RESOLVED balance=89
Done (28.428s)! For help, type "help"
FutureShops server stopping.
```

The complete first launch log SHA 256 is `7f3fa82b5c9027e35e5b7249b32065b05aa9c174e872f2c96b2fb5960395493b`.

The registrant startup callback used stable requests `00000000-0000-0000-0000-000000000510` through `00000000-0000-0000-0000-000000000514` for withdrawal, deposit, refund, compensation, and a custodied deposit. It called the public provider precheck, the FutureShops coordinator preflight, every coordinator mutation route, provider lookup, and duplicate retry. Every route returned `CONFIRMED`; custody reached `CLAIMED`, the claim reached `RESOLVED`, and the resulting balance was `89`.

The SQLite file was queried after shutdown. Its durable rows were:

```text
request_id=00000000-0000-0000-0000-000000000510 actor=00000000-0000-0000-0000-000000000410 kind=WITHDRAW amount=25 external_id=vault:00000000-0000-0000-0000-000000000510 resulting_balance=75
request_id=00000000-0000-0000-0000-000000000511 actor=00000000-0000-0000-0000-000000000410 kind=DEPOSIT amount=5 external_id=vault:00000000-0000-0000-0000-000000000511 resulting_balance=80
request_id=00000000-0000-0000-0000-000000000512 actor=00000000-0000-0000-0000-000000000410 kind=REFUND amount=3 external_id=vault:00000000-0000-0000-0000-000000000512 resulting_balance=83
request_id=00000000-0000-0000-0000-000000000513 actor=00000000-0000-0000-0000-000000000410 kind=COMPENSATION amount=2 external_id=vault:00000000-0000-0000-0000-000000000513 resulting_balance=85
request_id=00000000-0000-0000-0000-000000000514 actor=00000000-0000-0000-0000-000000000410 kind=DEPOSIT amount=4 external_id=vault:00000000-0000-0000-0000-000000000514 resulting_balance=89
account_id=00000000-0000-0000-0000-000000000410 balance=89
```

The durable database SHA 256 is `1642650526be49fa36aaa9656e24ebac5cff2e0ab37272dbf762035e02d8d9f7`. The backend uses one SQLite transaction for the balance row and receipt row, `journal_mode=DELETE`, `synchronous=FULL`, a primary key on `request_id`, and a bounded busy timeout. The file is `world/data/futureshops-vault-proof.sqlite/vault-proof.sqlite` inside the disposable world.

The same run persisted twenty FutureShops receipt audit records under `world/data/futureshops/receipts`, four transitions for each of the five request IDs, followed by a checksummed `.clean` marker. The coordinator therefore left a local recovery lineage while the SQLite provider receipts remained authoritative for retry.

The runtime was started a second time without changing its world, provider database, or request IDs. The restart log SHA 256 is `df38bcea97c0f8c3aea290636118e79a132adc0f26370be6ba192233bb55aced`. It reported `withdrawal=CONFIRMED`, `deposit=CONFIRMED`, `refund=CONFIRMED`, `compensation=CONFIRMED`, `custody=CONFIRMED`, `claim_state_initial=RESOLVED`, and `balance=89`. The SQLite hash after restart remained the same logical receipt set, and no second balance effect occurred.

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

Both dedicated server processes exited with code `0`. The temporary hybrid runtime, world, logs, SQLite database, and launcher output were removed after the sanitized evidence and hashes were recorded. The exact external jars and the shared Gradle dependency cache were left in their pre existing locations.

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
| FutureShops source | `5bb0199b355d12b6671a310cf8acd3857c67f77d` |
| Proof registrant source | `5471b8f1c10e8cd3eb79dc49f91f1b0f1bd2c89b` |
| FutureShops artifact SHA 256 | `f97805026224e435d00ed6478f6d122313bc99d44628fa9033602fd15d36173d` |
| FutureShops artifact SHA 512 | `e319285ac9069b12c3b12701deba8c92cd430dcf6c9b12e7a038886799f1d924a732d164400992307b70ee64ed06cf4bd74faee8971d15587856b80b4eea42cf` |
| Proof registrant SHA 256 | `df91158865e7c75b80bfb5eea4d07478f41b6b18f54d1740274a86d95e29826b` |
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
FutureShops Vault proof transaction provider_precheck=CONFIRMED coordinator_precheck=CONFIRMED withdrawal=CONFIRMED lookup=CONFIRMED retry=CONFIRMED deposit=CONFIRMED refund=CONFIRMED compensation=CONFIRMED custody=CONFIRMED custody_state=CLAIMED claim_state_initial=PENDING claim_state_delivered=DELIVERED claim_state_resolved=RESOLVED transfer=CONFIRMED transfer_source_before=100 transfer_source_after=94 transfer_target_after=106 balance=89
Done (25.238s)! For help, type "help"
FutureShops server stopping.
```

The complete first launch log SHA 256 for the earlier `d7d2e14b192644859a276114508ceb2c5aed8991931aab523b899ffa9d0e4ad3` artifact is `80092816a0fda9361f710bdedb8cd9ec1cb9f2d4b6e2c083ecfe70b3a2667aab`.

The registrant startup callback used stable requests `00000000-0000-0000-0000-000000000510` through `00000000-0000-0000-0000-000000000514` for withdrawal, deposit, refund, compensation, and a custodied deposit. It called the public provider precheck, the FutureShops coordinator preflight, every coordinator mutation route, provider lookup, and duplicate retry. It also exercised the coordinator transfer route from synthetic account `00000000-0000-0000-0000-000000000415` to `00000000-0000-0000-0000-000000000416`. Every first-run route returned `CONFIRMED`; custody reached `CLAIMED`, the claim reached `RESOLVED`, the transfer changed balances from `100` and `100` to `94` and `106`, and the FutureShops proof account remained `89`.

The SQLite file was queried after shutdown. Its durable rows were:

```text
request_id=00000000-0000-0000-0000-000000000510 actor=00000000-0000-0000-0000-000000000410 kind=WITHDRAW amount=25 external_id=vault:00000000-0000-0000-0000-000000000510 resulting_balance=75
request_id=00000000-0000-0000-0000-000000000511 actor=00000000-0000-0000-0000-000000000410 kind=DEPOSIT amount=5 external_id=vault:00000000-0000-0000-0000-000000000511 resulting_balance=80
request_id=00000000-0000-0000-0000-000000000512 actor=00000000-0000-0000-0000-000000000410 kind=REFUND amount=3 external_id=vault:00000000-0000-0000-0000-000000000512 resulting_balance=83
request_id=00000000-0000-0000-0000-000000000513 actor=00000000-0000-0000-0000-000000000410 kind=COMPENSATION amount=2 external_id=vault:00000000-0000-0000-0000-000000000513 resulting_balance=85
request_id=00000000-0000-0000-0000-000000000514 actor=00000000-0000-0000-0000-000000000410 kind=DEPOSIT amount=4 external_id=vault:00000000-0000-0000-0000-000000000514 resulting_balance=89
request_id=d64391ac-09fa-38a0-a7b0-f46a7b76055a actor=00000000-0000-0000-0000-000000000415 kind=TRANSFER_DEBIT amount=6 external_id=vault:d64391ac-09fa-38a0-a7b0-f46a7b76055a resulting_balance=94
request_id=e9c967d7-30ff-3120-9bf5-286ca90d338f actor=00000000-0000-0000-0000-000000000416 kind=TRANSFER_CREDIT amount=6 external_id=vault:e9c967d7-30ff-3120-9bf5-286ca90d338f resulting_balance=106
account_id=00000000-0000-0000-0000-000000000410 balance=89
account_id=00000000-0000-0000-0000-000000000415 balance=94
account_id=00000000-0000-0000-0000-000000000416 balance=106
```

The two transfer receipt rows were `TRANSFER_DEBIT` for account `00000000-0000-0000-0000-000000000415`, resulting balance `94`, and `TRANSFER_CREDIT` for account `00000000-0000-0000-0000-000000000416`, resulting balance `106`. The durable database SHA 256 is `093147cc406fd86187722bc29819f6b3670e53671bc9a413a3567c297f61008b`. The backend uses one SQLite transaction for each balance and receipt pair, `journal_mode=DELETE`, `synchronous=FULL`, a primary key on `request_id`, and a bounded busy timeout. The file is `world/data/futureshops-vault-proof.sqlite/vault-proof.sqlite` inside the disposable world.

The same run persisted twenty-eight FutureShops receipt audit records under `world/data/futureshops/receipts`, four transitions for each of the seven provider request IDs, followed by a checksummed `.clean` marker. The coordinator therefore left a local recovery lineage while the SQLite provider receipts remained authoritative for retry. In the packaged artifact rerun recorded here, the first process log SHA 256 is `85da87db12821a1a43676274377aed3d40f53ec5934e87431c5626499b19920a`, the restart log SHA 256 is `7d2b2245d22f44be8ad9eb12799421b038995b13237fa4f414b7eae82c2d6bbc`, and the post restart database SHA 256 is `73430219698eb89e8fc8325af954cdf27f229fb1698e1de346fed4165e438de6`. The receipt directory contained twenty-eight transition records and the `.clean` marker before cleanup.

The earlier artifact runtime was started a second time without changing its world or provider database. Its restart log SHA 256 is `8b736245a2eba3cc8cb4f5d62f2be58c9162ea31262a9f7bac4685286d80bb15`. It reported `withdrawal=CONFIRMED`, `deposit=CONFIRMED`, `refund=CONFIRMED`, `compensation=CONFIRMED`, `custody=CONFIRMED`, `claim_state_initial=RESOLVED`, `transfer=REPLAYED`, and `balance=89`. The SQLite hash after restart remained `093147cc406fd86187722bc29819f6b3670e53671bc9a413a3567c297f61008b`, with seven receipt rows and three account rows, and no second balance effect occurred. The transfer replay guard observed the already debited synthetic source account and did not issue a new transfer leg.

That artifact run also preserved the known external stack parser warnings, including `Not a map: END` during restart. They are emitted by the unmodified external stack, not FutureShops, and did not prevent startup, proof completion, or clean shutdown. No FutureShops exception was present.

A separate fresh exact hybrid runtime omitted the proof registrant and kept the unmodified PixelmonEconomyBridge, FinalEconomy, EverNifeCore, and Vault stack. With `provider = "vault"`, FutureShops reached `Done` without registering a provider. The bounded debug command reported `provider=none`, `lifecycle=RECOVERING`, and `observed_capabilities=none`, then the server stopped cleanly. No provider mutation was attempted and no proof jar was present in the runtime. The sanitized refusal and debug log SHA 256 is `425463560881196d018416bab76203b1aff786b20e2e05e5769df7d7c75ab41c`.

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

The focused coordinator regression suite also passed after adding service loss coverage. A missing provider before intent returned `UNAVAILABLE` with an empty journal and no provider call. A provider loss after intent returned `AMBIGUOUS`, left the lifecycle `FROZEN`, and recorded an `UNCERTAIN` journal state without internal fallback or blind retry.

```text
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 bash ./gradlew test --tests com.enviouse.futureshopsp.api.economy.VaultTransactionProofTest --no-daemon
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 bash ./gradlew test --tests com.enviouse.futureshopsp.server.economy.EconomyTransactionCoordinatorTest --no-daemon
```

Both focused suites passed on source revision `1f02bf46da4724369676617959fb1b1ac982e286`. The complete `test` and `build` checks then passed on source revision `5bb0199b355d12b6671a310cf8acd3857c67f77d`; the packaged artifact hashes and current two process evidence are recorded above.

## Cleanup

Both dedicated server processes exited with code `0`. The temporary hybrid runtime, world, logs, SQLite database, and launcher output were removed after the sanitized evidence and hashes were recorded. The exact external jars and the shared Gradle dependency cache were left in their pre existing locations.

# phase 002 bridge review

## Disposable artifacts

The available bridge files were inspected outside the repository.

| Artifact | SHA 256 | SHA 512 | Relevant identity |
| --- | --- | --- | --- |
| `/tmp/PixelmonEconomyBridge-1.1.6.jar` | `409896ee42f4163b616c5ab0964c220fc0a1c910ce8c3e2a0c05c4d78bd21da6` | `c4fb7cda655b5e63d485fef9e44bfa3a2b0d421ebccb458c15cb002942ef4f7f73c2a80b99c6ac41d7d744dff4aab4788f02e3e81ed55a3b5e0fdd77c4369adb` | Bukkit plugin `PixelmonEconomyBridge`, version `1.1.6`, depends on Vault and EverNifeCore |
| `/tmp/Vault-1.7.3.jar` | `a6b5ed97f43a5cf5bbaf00a7c8cd23c5afc9bd003f849875af8b36e6cf77d01d` | `aa02af3c9770249bda77b91058ce97240d4fd4cba3f07918534127acace297feb05445122b499c2623123dfde49670e9a763221e0f41ef03f51e6880ea8f6647` | Bukkit plugin Vault, version `1.7.3-b131` |
| `/tmp/FinalEconomy-1.0.9.jar` | `4cc7ba1aab02fffd86d2aa009a51ac4e6ca8590776ce9a13c8a2f45fdf01f529` | `8d5b75a993c0fa6ca5c07a3d796a431f18b18d96239a9bb73dc5de1ab93c405cf52bf5fe40a47b5b9ad720da32666bf426f70b62a5a75a5c176c061502ef8be4` | Bukkit plugin FinalEconomy, version `1.0.9` |
| `/tmp/futureshops-youer-pixelmon-248.zhVzs4/youer.jar` | `47ff03d9c26e40eac38ff5bbc1108f170d4b1649dfcc74488b696546f5807006` | `5e3022d7bcc23f762c7ac383f8162b3abd615e0453571f02073f2878eeaf6441165637b5b361fb7c6c4bbb3b5a60531a8501836838ae3861fa05cdf85373e7e7` | Youer `1.21.1-d4a204a0`, NeoForge `21.1.248`, CI run `33260344889` |
| `/tmp/futureshops-youer-pixelmon-248.zhVzs4/plugins/EverNifeCore-2.0.4.4.jar` | `15585a223a76c7bf18b311aa3e07db71ef4a1969837608a9db1d27e99c52f6e3` | `c6de341d973e910322cd3051f1a617bf2878984eee11c8312e5832beed02885fec8e76518221d4efa45a0b3827d00664b658089932cc1516b2b6a92bfa2e75c1` | Bukkit plugin EverNifeCore, version `2.0.4.4` |

The bridge contains `v1_21_R1` classes and can wire Pixelmon to Vault on a hybrid server. It is still a Bukkit plugin stack, not a NeoForge provider API. The exact profile below includes its required EverNifeCore artifact.

The authoritative [EverNife PixelmonEconomyBridge source](https://github.com/EverNife/PixelmonEconomyBridge) was inspected at commit `3290c81d248ed1241792e4c857b86f98f344bd08`. The repository exposes only tag `1.1.6`; its `v1_21_1` `VaultBankAccount` delegates `take` and `add` directly to Vault and provides no request identity, durable receipt, receipt lookup, or retry operation.

The public EverNife repositories for PixelmonEconomyBridge, FinalEconomy, and EverNifeCore expose no GitHub license metadata and no root license file. Their separately installed artifacts therefore remain license provenance unresolved for this phase. The Youer repository declares a `LICENSE.md`, but its GitHub license metadata is `NOASSERTION`.

## Exact hybrid profile

The disposable profile `/tmp/futureshops-youer-pixelmon-248.zhVzs4` contains the exact FutureShops 2.3.0, Pixelmon 9.4.0, GeckoLib 4.8.4, Youer `1.21.1-d4a204a0`, NeoForge 21.1.248, Vault 1.7.3, FinalEconomy 1.0.9, PixelmonEconomyBridge 1.1.6, and EverNifeCore 2.0.4.4 artifacts above. Its FutureShops configuration selects `pixelmon` and its current `eula.txt` is `eula=true`.

An earlier assembly check intentionally used `eula=false` and stopped at the EULA gate in `/tmp/futureshops-youer-pixelmon-248-java21-eula-false-20260903.log`. After owner authorization, the same exact bytes were rerun with `eula=true`; that launch reached plugin loading and FutureShops startup as recorded in `pixelmon-refusal-2026-09-03.md`. The historical gate check proves only byte assembly and loader discovery. The authorized rerun is the applicable startup evidence, while economy mutation, restart, and recovery remain separate capability gates.

## API and capability result

The bridge's `v1_21_R1` `VaultBankAccount` implements the Pixelmon `BankAccount` interface with `getBalance`, `hasBalance`, boolean `take`, boolean `add`, and `setBalance`. Its bytecode calls `VaultIntegration.ecoGet`, `ecoHasEnough`, `ecoTake`, and `ecoGive`. `getBalance` converts the Vault `double` through `Math.floor`, and `add` has no boolean or receipt result.

Targeted `javap` inspection of `FinalEconomyAPI`, `IFinalEconomy`, `VaultEconomy`, `FEBankAccount`, and `VaultBankAccount` confirms that the available calls are `getBalance`, `has`, `withdrawPlayer`, `depositPlayer`, boolean `take`, boolean `add`, and `setBalance`. The inspected FinalEconomy and Vault APIs expose `double` balances and `EconomyResponse` values, but no FutureShops request UUID, durable receipt store, receipt lookup, or idempotent retry keyed by a request identity. The bridge also owns no transaction journal that FutureShops can query. A successful boolean or `EconomyResponse` cannot prove the outcome after a crash between the external effect and local persistence.

| Required capability | Result |
| --- | --- |
| Balance query | Present, but bridge conversion is lossy because it floors a double |
| Precheck | Present through `ecoHasEnough` |
| Withdraw | Boolean result only, not safe for strict mutation |
| Deposit | Void bridge path, not safe for strict mutation |
| Durable receipt lookup | Absent |
| Idempotent retry | Absent |

The bridge therefore does not satisfy `CORE-REQ-018`. FutureShops does not add a bridge dependency, does not use reflection or service lookup for Bukkit or Vault, and does not register `vault` for this stack. The Pixelmon adapter remains query and precheck only, and all mutation surfaces remain refused before journal or custody effects.

## Additional NeoForge candidate review

The public [DanConomy](https://github.com/Dandelion1608/danconomy) repository was inspected at commit `63aecdac12e437ae1f3de2801cdea0105b3d7e06`. It targets NeoForge 21.1.209 and implements an optional Pixelmon mirrored currency. Its source falls back to a local ledger when Pixelmon reads or writes fail and mirrors observed Pixelmon balances into `data/danconomy_ledger.dat`. This is explicitly outside the FutureShops contract because it creates a second ledger and can report local success without a proven external outcome. It is not a bridge candidate for `CORE-REQ-018`.

An older public [pixelmon ecobridge](https://github.com/nkomarn/pixelmon-ecobridge) repository was also checked at commit `3d33d5e451b8e0536584fdbde8aaa2f159ee84d3`. Its build targets Pixelmon `1.12.2-8.2.0` and the source uses the removed `IPixelmonBankAccount` API. Its Vault implementation calls direct `changeMoney` methods, truncates primitive amounts, returns null `EconomyResponse` values, and has no receipt or retry journal. It cannot be used for the 1.21.1 target or the strict mutation contract.

The public [NeoEcoBridge](https://github.com/Neovitalism/NeoEcoBridge) repository was checked at commit `2040ccaa49d67c02712c5f7c2eb239526b7afebf`. Its build targets Forge `1.16.5`, Pixelmon `9.1.12`, and Java 8, while its account adapter calls Vault `withdrawPlayer` and `depositPlayer` with primitive doubles and returns only `transactionSuccess`. It has no request identity, durable receipt, receipt lookup, or idempotent retry, and its source repository exposes no license metadata or root license file. The [Modrinth project metadata](https://modrinth.com/plugin/neoecobridge) labels the project MIT, but it is not an exact 1.21.1 candidate.

The public [SynxGames PixelmonEconomyBridge](https://github.com/SynxGames/PixelmonEconomyBridge) repository was checked at commit `3ba43dec193d1adc98b0f8608c9741334517422b`. It also targets Forge `1.16.5`, Pixelmon `9.1.x`, and Java 8. Its `VaultBankAccount` floors balances, converts through primitive doubles, and delegates `take` and `add` to Vault `EconomyResponse.transactionSuccess`; it has no request identity, durable receipt, receipt lookup, or idempotent retry. The repository exposes no license metadata or root license file, so it is neither an exact 1.21.1 candidate nor a strict mutation bridge.

The public [Youer](https://github.com/MohistMC/Youer) `1.21.1` branch was checked at commit `d15aeebe02cd818dc7ba11952771ef071674707b`. It targets NeoForge `21.1.249`, not the pinned `21.1.248`. Its unmodified `:youer:createLauncherProfile` task failed because the declared `bungeecord-chat` `1.20-R0.2-deprecated+build.18` artifact was unavailable from the configured repositories. A disposable resolver-only patch allowed profile generation, but the subsequent `build` failed during production patch generation and `compileJava` with missing `net.minecraft` classes. No exact hybrid runtime server jar was produced from this candidate.

## Reclassification gate

Vault mutation support can be reconsidered only with an exact separately installed bridge that provides a stable request identity, durable receipt lookup, idempotent retry, exact integer conversion, and crash recovery evidence for every enabled route. Phase 003 owns any later issue 66 update and final artifact validation.

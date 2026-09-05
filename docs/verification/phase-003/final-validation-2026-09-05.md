# Phase 003 final validation packet

## Scope and source freeze

This packet records the final validation pass for the unpublished FutureShops 2.3.0 NeoForge 1.21.1 candidate. Phase 002 was integrated through pull request 71 into the `1.21.1` branch at merge commit `c2a24295cfcfeb05aa87935b02fb51f290ecd9b3`. The signed phase tag `2.3.0-phase-002-danconomy` points to that merge commit. The implementation artifact is built from and identifies that exact source revision. Phase 003 changes only tracked verification and release readiness documentation, so they do not change the implementation binding.

The candidate remains unpublished. No 3.0.0 implementation was added. Live issue 66 access was deferred until the artifact and requirement trace passed, as required by the phase contract.

## Artifact manifest

| Artifact | Value |
| --- | --- |
| Candidate | `build/libs/futureshops-2.3.0.jar` |
| Candidate size | `1327757` bytes |
| Source commit in manifest | `c2a24295cfcfeb05aa87935b02fb51f290ecd9b3` |
| Candidate SHA 256 | `82cae819eb9e628fbfd2b40bd4028db9ee28f952d4c3de7c4f57a94073d21780` |
| Candidate SHA 512 | `66bad71c1ccd547a33a00a67aff5f40994fbb179666434f4566c620adb196b1a1964ca84c93ce988a1c8b888767fabacc03946800f7d66a56f992f02a3a6410d` |
| Vault proof registrant | `build/vault-proof/futureshops-vault-proof-1.0.0.jar` |
| Proof registrant size | `28369` bytes |
| Proof registrant SHA 256 | `ab578f60f8302f304000ee6d0b401ec36bbb93589357ac6dce3f75cc7539bb30` |
| Proof registrant SHA 512 | `b24883d97c8f82963909699da70889bcc2d863d7f1be70cabe1a1f4087a2865837398eceffc93ec17fcef7f85bfc29804e8080b1c3336b5db845e55ea01bfc38` |
| Minecraft | `1.21.1` |
| NeoForge | `21.1.248` |
| Java | `21` |
| GeckoLib | `4.8.4` |
| Pixelmon | `9.4.0` |
| DanConomy | `1.2.1` |

The candidate metadata declares FutureShops `2.3.0`, NeoForge loader `21` or newer, and Minecraft `1.21.1` up to but excluding `1.22`. Its manifest contains the source commit above. The archive contains the public economy API, the original FutureShops Pixelmon and DanConomy adapters, both optional mixins, and both mixin plugins.

The candidate passed `unzip -t`. Its archive contains no Pixelmon, DanConomy, Bukkit, Vault, bridge, SQLite, proof registrant, or nested jar bytes. The source namespace scan found no third party package declarations in production source. The candidate contents report SHA 256 is `9d0b87d45810dfe8a768830da4ae742834a5fb9e59a344bcd07bb84196793cc4`, and the archive test report SHA 256 is `58388eef0f4bba4d2a85b74277fc7f658d6177a0831cb6c188df9b39b96b95ff`.

The exact optional inputs remained separately installed and unmodified:

| Input | SHA 256 |
| --- | --- |
| Pixelmon 9.4.0 | `9020393f98382ae8794ef2694e7bec1984c1a0eca735ea3eea06e0cb151c61f2` |
| DanConomy 1.2.1 | `61d3eb69a3a235929ac2376d151130e61ea4fe65c2f84990618c79e27e954b72` |
| GeckoLib 4.8.4 | `a1b6ce25e8627aa7e748672eedb6b71af68e0993462313649c259f38e42bcac9` |
| Youer 1.21.1 hybrid runtime | `47ff03d9c26e40eac38ff5bbc1108f170d4b1649dfcc74488b696546f5807006` |
| Vault 1.7.3 | `a6b5ed97f43a5cf5bbaf00a7c8cd23c5afc9bd003f849875af8b36e6cf77d01d` |
| FinalEconomy 1.0.9 | `4cc7ba1aab02fffd86d2aa009a51ac4e6ca8590776ce9a13c8a2f45fdf01f529` |
| PixelmonEconomyBridge 1.1.6 | `409896ee42f4163b616c5ab0964c220fc0a1c910ce8c3e2a0c05c4d78bd21da6` |
| EverNifeCore 2.0.4.4 | `15585a223a76c7bf18b311aa3e07db71ef4a1969837608a9db1d27e99c52f6e3` |

Exact artifact inspection was limited to interoperability research. FutureShops did not alter, rebuild, copy, bundle, or redistribute an external jar or external source.

## Deterministic verification

The focused Java 21 suite covered the debug surface, selection, lifecycle, coordinator, receipt audit, recovery, custody, claims, Pixelmon, DanConomy, Vault proof, and all monetary routes. It passed with log SHA 256 `b23368e3b663395e2ae97306458afb208508c837d5b133f4e4002c338ec07dcd`.

The complete uncached test suite passed with log SHA 256 `65bd465cf362ea9763fdf016b94da6f6436aa2ebf4f022556e9da66ecd16a273`.

The final clean build used the exact DanConomy input and executed `clean`, `build`, and `vaultProofJar` with all tasks rerun. It passed with log SHA 256 `b0ecf2788a301aa58db60f8bbc7202c974a252a4f7fe400425df8df00b8fb320`. A second jar task produced byte for byte identical candidate bytes. The reproducibility log SHA 256 is `58e8049c66ee9abf011292c18f8793068b794c0f5c301dc4b27f20e725946af0`.

The runtime dependency report passed with SHA 256 `a779611868ca8225e900cde9327255075ed9a4c8c06edf7c340d91dbbb648923`. Pixelmon and DanConomy are absent from the normal runtime dependency graph. DanConomy is accepted only as a separately supplied, hash checked development input. `jdeps --multi-release 21 --ignore-missing-deps --recursive` completed successfully. Its report SHA 256 is `7d91dc778ebd1d17c5d269f992696180b45d048f16bb2d72487b2dd327bac766`; unresolved entries are expected Minecraft, NeoForge, GeckoLib, and other runtime supplied types.

## Data generation invalidation record

The first data generation command used the repository's preexisting `run/mods` directory. Its unrelated installed Pixelmon jar initialized a client only Pixelmon path during data generation and terminated the process. The failed log SHA 256 is `c1efbb30769f11bc791ee27ea8df27331b275175110b594419280f54947672b6`. No source or tracked generated resource was changed, and the preexisting runtime was preserved.

The command was rerun in a new isolated data directory with the exact DanConomy input and without Pixelmon. `eula=true` was verified before launch. Data generation passed with log SHA 256 `c77251cc0ba4b4dd6d24b7e94bbc83ecb47f271e323adcbc1fa8e53c3d4c151a`. The isolated passing result supersedes the contaminated first attempt and the failure remains recorded instead of hidden.

## Dedicated GameTest matrix

The standard headless NeoForge GameTest runtime passed all 32 required tests and stopped cleanly. Its log SHA 256 is `98f4c057379c9f1f778fdeb29b617167f62cdc18ef2e6c34bf375fa3e0f30d69`.

The exact Pixelmon runtime applied the FutureShops mixin to `com.pixelmonmod.pixelmon.api.storage.PlayerPartyStorage`. The first process and same world restart each passed all 32 required tests. Their log SHA 256 values are `273f4b257d1a7b36467c238868ee9a2690b20c4dfe855f0372f45c6071fba568` and `256a03c8a14fa5d4f4a620e041ff5f0621e2c9110458866ddbdc4da70c911c01`.

The exact DanConomy runtime applied the FutureShops mixin to `com.danners45.danconomy.data.LedgerData`. The first process and same world restart each passed all 32 required tests. Their log SHA 256 values are `e5f121e0825c2be4edfbd5d2b1b3378494f49ffe2987a06b657fcc43e87f4080` and `ce89f123b24821689d741e2c5286223fa0efdf7cdf2234cc1ed077c3f3d8ed12`.

The matrices cover exact request identity, confirmed receipt lookup, identical retry deduplication, conflicting reuse rejection, interrupted durability, unknown record recovery, restart recovery, checked values, server thread enforcement, ordinary external call preservation, provider capability gating, every monetary surface, item custody, sale and barter escrow, durable claims, and frozen ambiguity.

## Packaged candidate server matrix

Every packaged runtime installed the exact candidate SHA 256 before startup and verified `eula=true`. Each server bound to `127.0.0.1` on a unique private test port.

The standard NeoForge runtime used `provider = "internal"`, reached `Done`, emitted the bounded debug procedure, and stopped cleanly. Its log SHA 256 is `26062f5ff27682be47a7837a5fa2dd03e7b6605237e0192c916cece69fbbc35e`.

The exact Pixelmon runtime used `provider = "pixelmon"`, applied the native mixin, reached `READY`, and stopped cleanly. The first process log SHA 256 is `08a87eb478f8c1add3cf793ce11db8a0c7fefd5cf823ef3ccdac106cdc53fea3`. The same world restart again applied the mixin, reached `READY`, and stopped cleanly with log SHA 256 `c80df08fee682e5bfa60f616fd824f4cb419decfe67a06b4fd609bcdd5a067f8`.

The first exact DanConomy packaged run intentionally retained DanConomy's blank default currency. FutureShops classified the selected `danconomy` provider as `INCOMPATIBLE`, did not fall back, kept the server online, and stopped cleanly. Its log SHA 256 is `7de422c06521117454ff49160d7c539c6bec1c48aa0884bfcd32280b2225944a`. After the required explicit `defaultCurrency = "dollar"` ledger configuration was applied, the same candidate and world reached `READY` and stopped cleanly. That log SHA 256 is `b073e14f898dade3940c94d4c89311dc1f9ad4cda198a48bb32075f76e427b70`.

## Vault proof and exact hybrid recovery

The exact hybrid profile loaded the candidate beside Pixelmon 9.4.0, Vault 1.7.3, FinalEconomy 1.0.9, PixelmonEconomyBridge 1.1.6, EverNifeCore 2.0.4.4, and the separate test only Vault proof registrant. The registrant supplied the public FutureShops `vault` provider through a SQLite backend that stores the balance effect and immutable request receipt in one transaction.

The first process registered `vault`, reached `Done`, and confirmed provider precheck, coordinator precheck, withdraw, lookup, identical retry, deposit, refund, compensation, item custody, claim delivery and resolution, and transfer. It recorded proof balance `89`, transfer source `94`, and transfer target `106`. Its log SHA 256 is `fc13457c6c02014cdf34a0ec1a052683b208db61f35fdb0880b2d08c1c9a8efb`.

The second process reopened the same world and database. All stable requests returned their existing confirmed outcomes. The transfer reported `REPLAYED`, the balances remained `89`, `94`, and `106`, and no duplicate value effect occurred. Its log SHA 256 is `0965b132c0650c7aa8bf78ea0c0d752805aa922fd558cdacd03836d4ab55bfc7`.

A separate fresh copy removed only the test registrant while retaining the exact candidate and unmodified hybrid plugins. The server reached `Done`, FutureShops classified selected `vault` as `MISSING`, did not fall back to internal, and stopped cleanly. Its log SHA 256 is `e76b926f94e6dcb6517b964363fa419cd745bc7e0f4a344b384e0e8d94a700f7`. This proves the current PixelmonEconomyBridge and FinalEconomy stack does not silently become transaction safe. Production use still requires a separately installed bridge or backend that implements the request receipt contract.

## Headless debug procedure

The exact console sequence was exercised without a client connection:

```text
futureshops debug status
futureshops debug on all
futureshops debug status
futureshops debug on provider
futureshops debug status
futureshops debug off
futureshops debug status
stop
```

Provider specific `pixelmon`, `danconomy`, and `vault` sessions were also enabled and disabled in their packaged runtimes. Debug was off before each enable and after each disable. Records included the source commit, candidate SHA 256, Minecraft and loader versions, lifecycle, module, operation, server side, thread, and next safe action. The standard packaged log above is the canonical debug evidence. Server logs and dedicated GameTests were sufficient because this phase changed no client rendering, screen, input, or synchronization behavior. No laptop client launch or screenshot processing was required.

## Cleanup and host evidence

All verification ran headlessly on node 1. Every Minecraft runtime used Java 21, a fresh test owned directory, localhost binding where a normal server port existed, and a verified `eula=true`. No graphical client was started on node 1. The exact external source profiles and user supplied Pixelmon jar were preserved unchanged. Disposable runtimes, worlds, logs, downloaded hybrid libraries, and temporary configuration were removed after their hashes and sanitized outcomes were recorded. No test server or test owned Java process remained.

## Exit result

This packet supplies `EVD-VER-001` for deterministic and runtime validation and `EVD-ART-001` for the final candidate manifest and isolation audit. The [requirement trace](requirement-trace-2026-09-05.md) supplies `EVD-VER-002`. The post artifact GitHub readback is recorded as `EVD-GH-001` in [the issue 66 evidence packet](github-issue-66-2026-09-05.md).

The candidate is technically validated and remains unpublished pending owner acceptance. No release upload, public artifact publication, or 3.0.0 code change was performed.

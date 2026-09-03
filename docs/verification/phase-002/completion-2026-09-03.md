# Phase 002 completion packet

## Scope and source identity

This packet records the current Phase 002 result for FutureShops 2.3.0 on Minecraft 1.21.1 and NeoForge 21.1.248. The implementation source baseline is revision `e63c91bdb991766bb4666350ea3c9ea850e41eb6` on `envy/phase-002-pixelmon-vault`. The current evidence packet revision is `1fa19cb`, twenty three commits ahead of the integrated `1.21.1` revision `b591413cc88590dd70caca3e23aad5ee5e6f8406`.

Phase 002 is not closed. Its independent Pixelmon query and refusal work is complete, but exact external runtime authorization and a mutation capable bridge remain unresolved. No pull request, phase integration, tag, release, upload, or issue 66 mutation is authorized by this packet.

GitHub milestone `2.3.0 phase 002 external integrations` is open as milestone `6`. Its description records the exact Pixelmon query and bridge verification scope, with durable external receipts and owner terms authorization as the remaining blockers. Issue 66 remains untouched while this phase is open, as required by the phase contract.

## Pixelmon result

The exact reviewed runtime is `/tmp/Pixelmon-1.21.1-9.4.0-universal.jar` with SHA 256 `9020393f98382ae8794ef2694e7bec1984c1a0eca735ea3eea06e0cb151c61f2` and SHA 512 `b1485031c27cbe0dd7125f11d3b003954e654f66c102479d443841071a37131067371bfc5e1fc2d8bf96a7195afa3ca02fc1525d343fc096d5bc598680bccafe`.

The official downloads page links a public MDK, but its current main revision is configured for Pixelmon 9.3.1 and NeoForge 21.1.170 rather than the exact 9.4.0 target. The exact 9.4.0 interface injection file was downloaded separately with SHA 256 `79bc83342ba0a3ee170c2883dbe30910adcb13fb6c73743ab70180ea30f9e666`. A disposable Java probe compiled against the exact universal artifact and exercised the reviewed `BankAccount` query and precheck surface. This narrows the development input gap but does not replace the exact authorized runtime environment.

The exact `BankAccount` surface supports UUID lookup, integer compatible `BigDecimal` balance reads, and `hasBalance`. Its `add` and `take` methods are boolean mutations. `BankAccountManager` only supplies synchronous and asynchronous lookup. The public `PreTransaction`, `PostTransaction`, and `SetBalance` events provide cancellation and balance observations, but no operation UUID, receipt, lookup, or retry handle. The concrete `PlayerPartyStorage` implementation persists the balance as `pixelDollars` and marks ordinary player data dirty after mutation, without an operation journal. Native Pixelmon command and shop consumers call the same direct methods and discard their boolean result. The reviewed classes expose no durable request identity, receipt lookup, idempotent retry, or outcome journal. The adapter therefore declares balance query and precheck only. All mutation required operations return typed capability refusal before transaction intent, custody, inventory, claims, analytics, events, or Pixelmon mutation.

`PixelmonEconomyProviderTest` proves exact conversion, missing and unavailable accounts, implementation readiness, insufficient funds, malformed values, and mutation refusal. The fixture records zero `BankAccount.add` and `BankAccount.take` calls. Coordinator coverage proves an empty journal and custody store when the capability gate refuses the request.

## Bridge result

The reviewed separately installed candidate is `PixelmonEconomyBridge` 1.1.6 with SHA 256 `409896ee42f4163b616c5ab0964c220fc0a1c910ce8c3e2a0c05c4d78bd21da6` and SHA 512 `c4fb7cda655b5e63d485fef9e44bfa3a2b0d421ebccb458c15cb002942ef4f7f73c2a80b99c6ac41d7d744dff4aab4788f02e3e81ed55a3b5e0fdd77c4369adb`. Its companion artifacts are Vault 1.7.3 with SHA 256 `a6b5ed97f43a5cf5bbaf00a7c8cd23c5afc9bd003f849875af8b36e6cf77d01d` and SHA 512 `aa02af3c9770249bda77b91058ce97240d4fd4cba3f07918534127acace297feb05445122b499c2623123dfde49670e9a763221e0f41ef03f51e6880ea8f6647`, and FinalEconomy 1.0.9 with SHA 256 `4cc7ba1aab02fffd86d2aa009a51ac4e6ca8590776ce9a13c8a2f45fdf01f529` and SHA 512 `8d5b75a993c0fa6ca5c07a3d796a431f18b18d96239a9bb73dc5de1ab93c405cf52bf5fe40a47b5b9ad720da32666bf426f70b62a5a75a5c176c061502ef8be4`.

The bridge is a Bukkit plugin. Its v1_21_R1 implementation delegates to Vault double based calls, floors balance values, returns boolean or void mutation results, and exposes no FutureShops request identity, durable receipt store, receipt lookup, or idempotent retry. EverNifeCore 2.0.4.4 is present in the exact hybrid profile as a required dependency. The EverNife repositories expose no GitHub license metadata or root license file, so license provenance remains unresolved for this separately installed stack. This candidate does not satisfy CORE-REQ-018 and is not registered as `vault`.

## Verification evidence

The complete unit suite passed with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 bash ./gradlew test --no-daemon`. The complete build passed with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 bash ./gradlew build --no-daemon`.

The same test and build commands were revalidated at evidence revision `d798018`; both completed successfully with all tasks up to date.

The real NeoForge GameTest server passed all eleven economy tests in `/tmp/futureshops-phase002-gametest-20260903-v2.log`. The bounded standard dedicated server reached common setup and server start in `/tmp/futureshops-phase002-server-20260903-v3.log`. The bounded Xvfb client reached common setup in `/tmp/futureshops-phase002-client-20260903-v3.log`. Their timeout exits were expected bounded smoke termination, not startup failures.

The rebuilt `build/libs/futureshops-2.3.0.jar` passed `unzip -tq`. Its SHA 256 is `30b02e65ecd2dce47accdfa0bfbbb4e1464c43f25d7774eaab8b0bcecec2b6ba` and its SHA 512 is `1ab37a9ed664ce57d547a699d9d9a16d50d833296e0a3d18725d29232034723546e4e9e878cbb23f967cef334b53287869734f843030df7abcd472c8b03d9896`. The jar contains no Pixelmon, Bukkit, Spigot, Vault, bridge, or test fixture classes. The optional adapter has no forbidden external dependency according to the dependency and `jdeps` scans.

## Exact environment assembly

The disposable profile `/tmp/futureshops-pixelmon-exact.xIDOL4` contains the exact FutureShops, Pixelmon, and GeckoLib jars above plus the NeoForge 21.1.248 server installation. The NeoForge installer SHA 256 is `68eeab77059ba53df1812f1afa5bf530ab2566a3cdcd5f924aa6e71be42e410c`; the installed NeoForge server jar SHA 256 is `1808fab692dc44b2d474295d1cdd9f1fe8a7dceab4f594210873646fafdf1359`. The profile config selects `pixelmon` and keeps `eula=false`.

The bounded command `timeout 60 bash ./run.sh` from that profile exited zero after discovering FutureShops 2.3.0, GeckoLib 4.8.4, Minecraft 1.21.1, NeoForge 21.1.248, and Pixelmon 9.4.0. The server then stopped at the EULA gate as required. The sanitized log is `/tmp/futureshops-pixelmon-exact-eula-false-20260903.log`. No Pixelmon query, mutation, restart, or recovery behavior was claimed from this preacceptance run.

The exact hybrid profile `/tmp/futureshops-youer-pixelmon-248.zhVzs4` adds the reviewed Youer `1.21.1-d4a204a0` server jar, Vault 1.7.3, FinalEconomy 1.0.9, PixelmonEconomyBridge 1.1.6, and EverNifeCore 2.0.4.4. Its Java 21 bounded launch reached NeoForge 21.1.248 discovery and stopped at `eula=false` in `/tmp/futureshops-youer-pixelmon-248-java21-eula-false-20260903.log`. This proves reproducible hybrid byte assembly and loader discovery only. The bridge capability and mutation recovery gates remain failed because its external calls have no durable request receipt or idempotent retry.

The security review found no exploitable repository defect. The residual risk is the intentionally unsupported direct Pixelmon mutation path and the unproven bridge mutation path. Detailed evidence is in [Pixelmon API evidence](pixelmon-api-2026-09-03.md), [Pixelmon refusal evidence](pixelmon-refusal-2026-09-03.md), [bridge review](bridge-review-2026-09-03.md), and [security review](security-2026-09-03.md).

## Open gates and next action

The exact universal artifact, interface injection file, public MDK compile path, and exact disposable Pixelmon and hybrid profiles are now available. Full Pixelmon and hybrid runtime workflows remain unverified under the required owner authorization record. The reviewed bridge lacks the strict mutation and recovery capabilities required for `vault`. These gates keep CORE-REQ-018 open and prevent Phase 002 pull request integration.

The next safe action is to obtain the exact authorized development and runtime inputs, or a separately installed bridge that provides stable request identity, durable receipts, exact integer conversion, and idempotent retry. Reclassify and rerun only the affected capability, crash, recovery, and surface matrices when those inputs exist. Issue 66 remains frozen until Phase 003 validates the final artifact.

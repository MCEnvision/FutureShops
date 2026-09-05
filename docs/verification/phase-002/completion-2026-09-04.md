# Phase 002 completion packet update

## Current status

Phase 002 remains open. The native Pixelmon workflow implementation and headless evidence remain valid. The current exact packaged artifact is `build/libs/futureshops-2.3.0.jar` from source revision `1f02bf46da4724369676617959fb1b1ac982e286`, with SHA 256 `d7d2e14b192644859a276114508ceb2c5aed8991931aab523b899ffa9d0e4ad3` and SHA 512 `704c3495f1fca5ca2015ac4320e705b8e83d5dfc0b12c5fa2edccb85f12d0ce4a84d2ea4fb782760abca0beb1ea91047475e61698f441e7537984d9041980b23`.

No pull request, phase integration, tag, release, upload, or issue 66 mutation was performed. Issue 66 remains frozen for Phase 003.

The artifact manifest binds this jar to the same source revision. The separate Vault proof fixture was extended at that revision to exercise every coordinator mutation route, custody, and claims without changing production integration classes.

## New native Pixelmon evidence

The exact Pixelmon `9.4.0` headless run loaded the native mixin and passed all twenty required dedicated server GameTests. The tests now cover native cart purchase, native `/pay` transfer, native server shop sell, native admin player shop buy with claimed sale escrow, public withdraw and deposit, refund, compensation, physical money refusal, request receipt replay, durable receipt reload, request replay after storage reload, persisted balance after reload, unknown compound receipt recovery, wrong entry type recovery, wrong root type recovery, and a two process native restart replay. The native mutation test also rereads the Pixelmon save adapter file and verifies the completed receipt UUID on disk. Sanitized evidence is in [p002 integration evidence](p002-integration-evidence-2026-09-04.md). The current artifact bound native GameTest log SHA 256 values are `8e0548e72f7f714717f7ac08ef0b6d21bfa40f91f3b35747d11702540cfd861c` and `68a231774fd9a26e6eeedc287968d25b5eca0e4b3a5be653697a63b8a6057c7d`. The current artifact bound hybrid server log hashes are recorded below.

The same source passed the required tests in a fresh temporary standard NeoForge directory with Pixelmon absent and `provider = "internal"`. The absence log SHA 256 is `ec61fb40021a76e1505c3a1819facd2ad84911531bf3195176a99c6d1a4733c0`. The exact Pixelmon jar was not copied into that directory.

The current artifact was launched in a fresh exact hybrid Pixelmon dedicated server twice. The mixin target and Pixelmon `9.4.0` loaded, the server reached `Done`, the proof registrant completed all coordinator routes including transfer debit and credit, and the second process replayed the stable requests without another balance effect. The first and restart log hashes are `80092816a0fda9361f710bdedb8cd9ec1cb9f2d4b6e2c083ecfe70b3a2667aab` and `8b736245a2eba3cc8cb4f5d62f2be58c9162ea31262a9f7bac4685286d80bb15`. The proof registrant SHA 256 is `df91158865e7c75b80bfb5eea4d07478f41b6b18f54d1740274a86d95e29826b`. Exact hybrid registration and mutation proof with the separate SQLite registrant is recorded in [exact hybrid Vault proof](vault-hybrid-proof-2026-09-05.md).

A second exact hybrid runtime omitted the proof registrant. The unmodified bridge stack did not register `vault`; the server still reached `Done` and the bounded debug log reported `provider=none`, `lifecycle=RECOVERING`, and `observed_capabilities=none`. No provider mutation was attempted. The refusal log SHA 256 is `425463560881196d018416bab76203b1aff786b20e2e05e5769df7d7c75ab41c`.

The provider mutation router now maps transfer credit, refund, compensation, and deposit requests to the native add path, and transfer debit and withdrawal requests to the native take path. This preserves the request identity and durable receipt checks while allowing the coordinator to complete native transfers.

## New Vault proof evidence

The separate SQLite proof backend and NeoForge registrant are now built outside the production jar. Focused tests prove one transaction for balance and receipt, rollback at each pre commit boundary, fresh process lookup after an ambiguous post commit result, conflicting request rejection, deterministic insufficient funds, and concurrent duplicate deduplication. The exact hybrid runtime loaded the unmodified Pixelmon `9.4.0`, Vault `1.7.3`, FinalEconomy `1.0.9`, EverNifeCore `2.0.4.4`, and PixelmonEconomyBridge `1.1.6` stack beside the proof registrant. It registered `vault` through the public FutureShops API and logged confirmed coordinator precheck, withdrawal, deposit, refund, compensation, custodied deposit, custody and claim transitions, provider lookup, and retry with a resulting balance of `89`. A second process replayed the stable request IDs and retained that balance without another effect. Evidence and hashes are in [exact hybrid Vault proof](vault-hybrid-proof-2026-09-05.md). The proof registrant does not certify the existing legacy bridge for production mutation, which remains refused without its own transaction aware backend.

## Remaining gates

The current unmodified PixelmonEconomyBridge and FinalEconomy stack remains safely refused for `vault` mutation because it has no FutureShops request identity, durable provider receipt, lookup, or idempotent retry contract. The separate proof fixture passes the public registration and one transaction receipt contract, but it is not a claim about that legacy stack.

The complete Phase 002 surface, crash, restart, reconnect, and recovery matrices against every enabled external surface remain open. Provider service loss is now covered by focused coordinator regressions, with pre intent loss failing closed and post intent loss freezing unknown outcome. Native Pixelmon storage reload and request replay pass in the exact artifact bound GameTest. The exact hybrid bridge and backend mutation proof must either pass the remaining matrices or remain explicitly refused. Phase 003 cannot begin until the Phase 002 completion packet is accepted through the sequential integration workflow.

## Verification commands

```text
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 bash ./gradlew test --no-daemon
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 bash ./gradlew build --no-daemon
```

Both commands passed at the source revision above. Focused Vault and coordinator tests also passed. The packaged jar passed `unzip -tq`, and its contents contain no Pixelmon, Bukkit, Vault, bridge, or external test fixture classes. The current artifact bound exact server and GameTest runs reached `Done` or passed all twenty tests with the mixin target applied and stopped cleanly. The debug session also proved that production logs can discover and print the source commit and artifact SHA without a client connection. The exact GameTest and packaged server runtimes stopped cleanly with `eula=true` verified; their disposable worlds, log directories, and temporary evidence files were removed after hashing.

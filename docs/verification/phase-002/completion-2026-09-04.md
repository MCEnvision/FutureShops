# Phase 002 completion packet update

## Current status

Phase 002 remains open. The native Pixelmon workflow implementation and headless evidence remain valid. The current exact packaged artifact is `build/libs/futureshops-2.3.0.jar` from source revision `6bbaf9156bcd8d79bee274717f2ae67d4db6f69e`, with SHA 256 `945d175c363ec06f6b0e965161cff081c5deebf1b1ed899e605b48890fc69563` and SHA 512 `0e38cc66eaaf739413f5a8b2f193d97aca40ea4e8c5be18c4f9f29999ccbc6a1a8055045028a796183b623bf6e4d49478134683b23dbe31445e0db96fc02bae2`.

No pull request, phase integration, tag, release, upload, or issue 66 mutation was performed. Issue 66 remains frozen for Phase 003.

The artifact manifest binds this jar to the same source revision. The separate Vault proof fixture was extended at that revision to exercise every coordinator mutation route, custody, and claims without changing production integration classes.

## New native Pixelmon evidence

The exact Pixelmon `9.4.0` headless run loaded the native mixin and passed all twenty required dedicated server GameTests. The tests now cover native cart purchase, native `/pay` transfer, native server shop sell, native admin player shop buy with claimed sale escrow, public withdraw and deposit, refund, compensation, physical money refusal, request receipt replay, durable receipt reload, request replay after storage reload, persisted balance after reload, unknown compound receipt recovery, wrong entry type recovery, wrong root type recovery, and a two process native restart replay. The native mutation test also rereads the Pixelmon save adapter file and verifies the completed receipt UUID on disk. Sanitized evidence is in [p002 integration evidence](p002-integration-evidence-2026-09-04.md). Earlier packaged native GameTest log SHA 256 values are `581766494f275acde701caf26a5fe0a605004bccb3ce4c83f3099256a5fe4f24` and `a1ae7dfa69e5fd9b9b0360b70c45dd70de214a6528990f5edfadc3577a8ff4bc`. The current artifact revalidation is recorded in the linked integration evidence.

The same source passed the required tests in a fresh temporary standard NeoForge directory with Pixelmon absent and `provider = "internal"`. The absence log SHA 256 is `ec61fb40021a76e1505c3a1819facd2ad84911531bf3195176a99c6d1a4733c0`. The exact Pixelmon jar was not copied into that directory.

The current packaged artifact was separately rerun in a fresh copied standard profile with Pixelmon absent and `provider = "internal"`. It reached `FutureShops common setup complete`, `FutureShops server starting`, and `Done`, then stopped cleanly. The sanitized log SHA 256 is `530572c3496d55adc50ff66b539843c3fdc064b8aa1720d493a703b2f6b2d155`. The copied profile verified `eula=true` and was removed after hashing.

An earlier packaged artifact was launched in a fresh exact hybrid Pixelmon dedicated server twice. The mixin target and Pixelmon `9.4.0` loaded, the server reached `Done`, the proof registrant completed all coordinator routes including transfer debit and credit, and the second process replayed the stable requests without another balance effect. The first and restart log hashes are `85da87db12821a1a43676274377aed3d40f53ec5934e87431c5626499b19920a` and `7d2b2245d22f44be8ad9eb12799421b038995b13237fa4f414b7eae82c2d6bbc`. The resulting SQLite database SHA 256 is `73430219698eb89e8fc8325af954cdf27f229fb1698e1de346fed4165e438de6`. Exact hybrid registration and mutation proof with the separate SQLite registrant is recorded in [exact hybrid Vault proof](vault-hybrid-proof-2026-09-05.md).

A second exact hybrid runtime omitted the proof registrant. The unmodified bridge stack did not register `vault`; the server still reached `Done` and the bounded debug log reported `provider=none`, `lifecycle=RECOVERING`, and `observed_capabilities=none`. No provider mutation was attempted. The refusal log SHA 256 is `425463560881196d018416bab76203b1aff786b20e2e05e5769df7d7c75ab41c`.

The current packaged artifact was also rerun with the exact external Pixelmon `9.3.1` jar and `provider = "pixelmon"` in a fresh copied profile. The server reached `FutureShops common setup complete`, logged `Pixelmon economy adapter unavailable, pixelmon version is unsupported`, reached `Done`, and stopped cleanly. The sanitized log SHA 256 is `b57b25128654bb084f6d1228a449442cb726f6b7bd66bc183a95da1eb6c82df5`. Pixelmon emitted unrelated legacy artifact errors; no FutureShops error or exception occurred. The copied profile verified `eula=true` and was removed after hashing.

The provider mutation router now maps transfer credit, refund, compensation, and deposit requests to the native add path, and transfer debit and withdrawal requests to the native take path. This preserves the request identity and durable receipt checks while allowing the coordinator to complete native transfers.

The current packaged artifact was revalidated with a pure exact NeoForge Vault surface and failure GameTest matrix. All twenty seven required tests passed. Route diagnostics confirmed server shop sell, server shop buy, player shop buy, public API withdrawal and deposit, cart buy, pay transfer, and physical money refusal. Failure diagnostics confirmed typed service loss, every SQLite interruption boundary, durable lookup, retry deduplication, late registration, and missing provider refusal. The current combined log SHA 256 is `fc96646872f1a0cfcb49af9db3d678a55b1feca5f07ddef6c204eff4fa4947fd`, and the proof registrant SHA 256 is `ab578f60f8302f304000ee6d0b401ec36bbb93589357ac6dce3f75cc7539bb30`. See [exact Vault surface GameTest](vault-surface-gametest-2026-09-05.md) and [exact Vault failure and recovery matrix](vault-failure-matrix-2026-09-05.md) for current hashes and logs.

## New Vault proof evidence

The separate SQLite proof backend and NeoForge registrant are now built outside the production jar. Focused tests prove one transaction for balance and receipt, rollback at each pre commit boundary, fresh process lookup after an ambiguous post commit result, conflicting request rejection, deterministic insufficient funds, and concurrent duplicate deduplication. The exact hybrid runtime loaded the unmodified Pixelmon `9.4.0`, Vault `1.7.3`, FinalEconomy `1.0.9`, EverNifeCore `2.0.4.4`, and PixelmonEconomyBridge `1.1.6` stack beside the proof registrant. It registered `vault` through the public FutureShops API and logged confirmed coordinator precheck, withdrawal, deposit, refund, compensation, custodied deposit, custody and claim transitions, provider lookup, and retry with a resulting balance of `89`. A second process replayed the stable request IDs and retained that balance without another effect. Evidence and hashes are in [exact hybrid Vault proof](vault-hybrid-proof-2026-09-05.md). The proof registrant does not certify the existing legacy bridge for production mutation, which remains refused without its own transaction aware backend.

The current production artifact was rerun in that exact hybrid stack in two fresh processes. The first log SHA 256 is `da65861f3fe7d3b0fb53cf00bc832884339106cc494b19a67a58398f77f4bca6` and the restart log SHA 256 is `2e1a5d09df895135d4683e6832fce0e6230feb0734bf3ca00c57c758f7298d86`. Both processes reached `Done` and stopped after the proof routes. The restart reported `transfer=REPLAYED` and balance `89` without a second effect. The current artifact and proof registrant hashes are recorded in [exact hybrid Vault proof](vault-hybrid-proof-2026-09-05.md).

## Remaining gates

The current unmodified PixelmonEconomyBridge and FinalEconomy stack remains safely refused for `vault` mutation because it has no FutureShops request identity, durable provider receipt, lookup, or idempotent retry contract. The separate proof fixture passes the public registration and one transaction receipt contract, but it is not a claim about that legacy stack.

The complete Phase 002 surface, crash, restart, reconnect, and recovery matrices against every enabled external surface remain open. Provider service loss is now covered by focused coordinator regressions, with pre intent loss failing closed and post intent loss freezing unknown outcome. Native Pixelmon storage reload and request replay pass in the exact artifact bound GameTest. The exact hybrid bridge and backend mutation proof must either pass the remaining matrices or remain explicitly refused. Phase 003 cannot begin until the Phase 002 completion packet is accepted through the sequential integration workflow.

## Verification commands

```text
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 bash ./gradlew test --no-daemon
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 bash ./gradlew build --no-daemon
```

Both commands passed at the source revision above. Focused Vault and coordinator tests also passed. The packaged jar passed `unzip -tq`, and its contents contain no Pixelmon, Bukkit, Vault, bridge, or external test fixture classes. The current artifact bound exact server and GameTest runs reached `Done` or passed all twenty tests with the mixin target applied and stopped cleanly. The debug session also proved that production logs can discover and print the source commit and artifact SHA without a client connection. The exact GameTest and packaged server runtimes stopped cleanly with `eula=true` verified; their disposable worlds, log directories, and temporary evidence files were removed after hashing.

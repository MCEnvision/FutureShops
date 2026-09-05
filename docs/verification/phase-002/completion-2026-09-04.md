# Phase 002 completion packet update

## Current status

Phase 002 remains open. The native Pixelmon workflow implementation and headless evidence remain valid. The current exact packaged artifact is `build/libs/futureshops-2.3.0.jar` from source revision `490f78d3374d663d17b1be608e1cafc91f0ca840`, with SHA 256 `b56fd75fc96968eac8c05c50d6ff71be24e2c8472b43ad26fe4208535c2aa145` and SHA 512 `f0d6eb7b7660506816131184c5ce55f5edbc4853284321f16fd41bf5740ebd1ac3a2d542a397e52ccc707d2c5717356f89390e8ca5f05a4a2f6c346cfbda8442`.

No pull request, phase integration, tag, release, upload, or issue 66 mutation was performed. Issue 66 remains frozen for Phase 003.

The artifact manifest binds this jar to the same source revision. The separate Vault proof fixture was extended at that revision to exercise every coordinator mutation route, custody, and claims without changing production integration classes.

## New native Pixelmon evidence

The exact Pixelmon `9.4.0` headless run loaded the native mixin and passed all twenty required dedicated server GameTests. The tests now cover native cart purchase, native `/pay` transfer, native server shop sell, native admin player shop buy with claimed sale escrow, public withdraw and deposit, refund, compensation, physical money refusal, request receipt replay, durable receipt reload, request replay after storage reload, persisted balance after reload, unknown compound receipt recovery, wrong entry type recovery, wrong root type recovery, and a two process native restart replay. The native mutation test also rereads the Pixelmon save adapter file and verifies the completed receipt UUID on disk. Sanitized evidence is in [p002 integration evidence](p002-integration-evidence-2026-09-04.md). The current artifact bound native GameTest log SHA 256 values are `512976106c5f973fade79206bcd9721d847763968f90f218df00d89d88a80c16` and `7eb49c64fb39d36d8fdc9676b53cb2f88f725d48a5dadc9cf36d5e83a7d1cd66`. The current artifact bound hybrid server log hashes are recorded below.

The same source passed the required tests in a fresh temporary standard NeoForge directory with Pixelmon absent and `provider = "internal"`. The absence log SHA 256 is `ec61fb40021a76e1505c3a1819facd2ad84911531bf3195176a99c6d1a4733c0`. The exact Pixelmon jar was not copied into that directory.

The current artifact was launched in a fresh exact hybrid Pixelmon dedicated server twice. The mixin target and Pixelmon `9.4.0` loaded, the server reached `Done`, the proof registrant completed all coordinator routes, and the second process replayed them without another balance effect. The first and restart log hashes are `7f3fa82b5c9027e35e5b7249b32065b05aa9c174e872f2c96b2fb5960395493b` and `df38bcea97c0f8c3aea290636118e79a132adc0f26370be6ba192233bb55aced`. Exact hybrid registration and mutation proof with the separate SQLite registrant is recorded in [exact hybrid Vault proof](vault-hybrid-proof-2026-09-05.md).

The provider mutation router now maps transfer credit, refund, compensation, and deposit requests to the native add path, and transfer debit and withdrawal requests to the native take path. This preserves the request identity and durable receipt checks while allowing the coordinator to complete native transfers.

## New Vault proof evidence

The separate SQLite proof backend and NeoForge registrant are now built outside the production jar. Focused tests prove one transaction for balance and receipt, rollback at each pre commit boundary, fresh process lookup after an ambiguous post commit result, conflicting request rejection, deterministic insufficient funds, and concurrent duplicate deduplication. The exact hybrid runtime loaded the unmodified Pixelmon `9.4.0`, Vault `1.7.3`, FinalEconomy `1.0.9`, EverNifeCore `2.0.4.4`, and PixelmonEconomyBridge `1.1.6` stack beside the proof registrant. It registered `vault` through the public FutureShops API and logged confirmed coordinator precheck, withdrawal, deposit, refund, compensation, custodied deposit, custody and claim transitions, provider lookup, and retry with a resulting balance of `89`. A second process replayed the stable request IDs and retained that balance without another effect. Evidence and hashes are in [exact hybrid Vault proof](vault-hybrid-proof-2026-09-05.md). The proof registrant does not certify the existing legacy bridge for production mutation, which remains refused without its own transaction aware backend.

## Remaining gates

The current unmodified PixelmonEconomyBridge and FinalEconomy stack remains safely refused for `vault` mutation because it has no FutureShops request identity, durable provider receipt, lookup, or idempotent retry contract. The separate proof fixture passes the public registration and one transaction receipt contract, but it is not a claim about that legacy stack.

The complete Phase 002 surface, crash, service loss, restart, reconnect, and recovery matrices against every enabled external surface remain open. Native Pixelmon storage reload and request replay now pass in the exact GameTest. The exact hybrid bridge and backend mutation proof must either pass those matrices or remain explicitly refused. Phase 003 cannot begin until the Phase 002 completion packet is accepted through the sequential integration workflow.

## Verification commands

```text
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 bash ./gradlew test --no-daemon
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 bash ./gradlew build --no-daemon
```

Both commands passed at the source revision above. The packaged jar passed `unzip -tq`, and its contents contain no Pixelmon, Bukkit, Vault, bridge, or external test fixture classes. The current artifact-bound exact server smoke reached `Done` with the mixin target applied and stopped cleanly. The debug session also proved that production logs can discover and print the source commit and artifact SHA without a client connection. The exact GameTest and packaged server runtimes stopped cleanly with `eula=true` verified; their disposable worlds, log directories, and temporary evidence files were removed after hashing.

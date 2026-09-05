# Phase 002 completion packet update

## Current status

Phase 002 remains open. The native Pixelmon workflow implementation and headless evidence remain valid from source revision `1378859c6d8ecf63731e0e7a0ed858c2131ab83a`. The current exact packaged artifact is `build/libs/futureshops-2.3.0.jar` from source revision `c56f79f7a67c2a6c2cf2c3f2264e20e96f646e50`, with SHA 256 `85c6bfae68020ef98bacf509dd9999bf03f913b2bdd895eb5a082bdf2e62d5f6` and SHA 512 `51775974d3885ea98eb61d25fe9d8d1e3740d99ea94f898f23a3e7140d82c0dd821cd51d946df31bc710713a7f722577e498bafb597c48ab376c6f1d0f2d732b`.

No pull request, phase integration, tag, release, upload, or issue 66 mutation was performed. Issue 66 remains frozen for Phase 003.

The artifact manifest binds this jar to the same source revision.

## New native Pixelmon evidence

The exact Pixelmon `9.4.0` headless run loaded the native mixin and passed all twenty required dedicated server GameTests. The tests now cover native cart purchase, native `/pay` transfer, native server shop sell, native admin player shop buy with claimed sale escrow, public withdraw and deposit, refund, compensation, physical money refusal, request receipt replay, durable receipt reload, request replay after storage reload, persisted balance after reload, unknown compound receipt recovery, wrong entry type recovery, wrong root type recovery, and a two process native restart replay. The native mutation test also rereads the Pixelmon save adapter file and verifies the completed receipt UUID on disk. Sanitized evidence is in [p002 integration evidence](p002-integration-evidence-2026-09-04.md). The latest artifact bound native GameTest log SHA 256 is `7ea3e90f198de7529e83870054d3e0e44b20b51ca73cf033bd7f0fcc42e7eb51`; the latest artifact bound packaged server evidence SHA 256 is `77f85dbac94dc7995566f9e0bb24b5151cd3279f7eb66170bbfa0e4e447cd1ff`. Both runs use the current production artifact from source revision `c56f79f7a67c2a6c2cf2c3f2264e20e96f646e50`.

The same source passed the required tests in a fresh temporary standard NeoForge directory with Pixelmon absent and `provider = "internal"`. The absence log SHA 256 is `ec61fb40021a76e1505c3a1819facd2ad84911531bf3195176a99c6d1a4733c0`. The exact Pixelmon jar was not copied into that directory.

The current artifact was also launched in a fresh exact Pixelmon dedicated server. The mixin target and Pixelmon `9.4.0` loaded, the server reached `Done`, and it stopped cleanly. The current packaged server evidence SHA 256 is `77f85dbac94dc7995566f9e0bb24b5151cd3279f7eb66170bbfa0e4e447cd1ff`. Exact hybrid registration and mutation proof with the separate SQLite registrant is recorded in [exact hybrid Vault proof](vault-hybrid-proof-2026-09-05.md).

The provider mutation router now maps transfer credit, refund, compensation, and deposit requests to the native add path, and transfer debit and withdrawal requests to the native take path. This preserves the request identity and durable receipt checks while allowing the coordinator to complete native transfers.

## New Vault proof evidence

The separate SQLite proof backend and NeoForge registrant are now built outside the production jar. Focused tests prove one transaction for balance and receipt, rollback at each pre commit boundary, fresh process lookup after an ambiguous post commit result, conflicting request rejection, deterministic insufficient funds, and concurrent duplicate deduplication. The exact hybrid runtime loaded the unmodified Pixelmon `9.4.0`, Vault `1.7.3`, FinalEconomy `1.0.9`, EverNifeCore `2.0.4.4`, and PixelmonEconomyBridge `1.1.6` stack beside the proof registrant. It registered `vault` through the public FutureShops API and logged confirmed precheck, withdrawal, lookup, and retry with a resulting balance of `75`. Evidence and hashes are in [exact hybrid Vault proof](vault-hybrid-proof-2026-09-05.md). The proof registrant does not certify the existing legacy bridge for production mutation, which remains refused without its own transaction aware backend.

## Remaining gates

The current unmodified PixelmonEconomyBridge and FinalEconomy stack remains safely refused for `vault` mutation because it has no FutureShops request identity, durable provider receipt, lookup, or idempotent retry contract. The separate proof fixture passes the public registration and one transaction receipt contract, but it is not a claim about that legacy stack.

The complete Phase 002 surface, crash, service loss, restart, reconnect, and recovery matrices against every enabled external surface remain open. Native Pixelmon storage reload and request replay now pass in the exact GameTest. The exact hybrid bridge and backend mutation proof must either pass those matrices or remain explicitly refused. Phase 003 cannot begin until the Phase 002 completion packet is accepted through the sequential integration workflow.

## Verification commands

```text
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 bash ./gradlew test --no-daemon
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 bash ./gradlew build --no-daemon
```

Both commands passed at the source revision above. The packaged jar passed `unzip -tq`, and its contents contain no Pixelmon, Bukkit, Vault, bridge, or external test fixture classes. The current artifact-bound exact server smoke reached `Done` with the mixin target applied and stopped cleanly. The debug session also proved that production logs can discover and print the source commit and artifact SHA without a client connection. The exact GameTest and packaged server runtimes stopped cleanly with `eula=true` verified; their disposable worlds, log directories, and temporary evidence files were removed after hashing.

# Phase 002 completion packet update

## Current status

Phase 002 remains open. The native Pixelmon workflow implementation and headless evidence were extended at source revision `b80bf15bd096c039b32cebeaf511730714463dfd`. The exact packaged artifact is `build/libs/futureshops-2.3.0.jar` with SHA 256 `0d43b9bcf47b3eacf69a87cc7facf3ff7f189eb18f4bca8d1e7649adbedef63a` and SHA 512 `2d4afd36ac804150de116efd4cb671abcb8bab799d1cc31071930ff8fec26b5e2eba36ad28e92fba34c2f121bad0687a1f1123b379f65eb1f1cb14701060c942`.

No pull request, phase integration, tag, release, upload, or issue 66 mutation was performed. Issue 66 remains frozen for Phase 003.

The artifact manifest binds this jar to the same source revision.

## New native Pixelmon evidence

The exact Pixelmon `9.4.0` headless run loaded the native mixin and passed all nineteen required dedicated server GameTests. The tests now cover native cart purchase, native `/pay` transfer, native server shop sell, native admin player shop buy with claimed sale escrow, public withdraw and deposit, refund, compensation, physical money refusal, request receipt replay, durable receipt reload, request replay after storage reload, persisted balance after reload, unknown compound receipt recovery, wrong entry type recovery, and wrong root type recovery. The native mutation test also rereads the Pixelmon save adapter file and verifies the completed receipt UUID on disk. Sanitized evidence is in [p002 integration evidence](p002-integration-evidence-2026-09-04.md). The latest source revision GameTest evidence SHA 256 is `7ce6bb3a42b1397f4378b4d02b5bce1a4b17a91aee090e178f3110a45908cd30`; the latest artifact-bound packaged server evidence SHA 256 is `962a786ffbac992b74ea1f10c47e1b2777989862bfebb8409c5a858fe0fe1248`.

The same source passed nineteen required tests in a fresh temporary standard NeoForge directory with Pixelmon absent and `provider = "internal"`. The absence log SHA 256 is `ec61fb40021a76e1505c3a1819facd2ad84911531bf3195176a99c6d1a4733c0`. The exact Pixelmon jar was not copied into that directory.

The provider mutation router now maps transfer credit, refund, compensation, and deposit requests to the native add path, and transfer debit and withdrawal requests to the native take path. This preserves the request identity and durable receipt checks while allowing the coordinator to complete native transfers.

## Remaining gates

The current unmodified PixelmonEconomyBridge and FinalEconomy stack remains safely refused for `vault` mutation because it has no FutureShops request identity, durable provider receipt, lookup, or idempotent retry contract. The separate proof fixture passes the public registration and one transaction receipt contract, but it is not a claim about that legacy stack.

The complete Phase 002 surface, crash, service loss, restart, reconnect, and recovery matrices against every enabled external surface remain open. Native Pixelmon storage reload and request replay now pass in the exact GameTest. The exact hybrid bridge and backend mutation proof must either pass those matrices or remain explicitly refused. Phase 003 cannot begin until the Phase 002 completion packet is accepted through the sequential integration workflow.

## Verification commands

```text
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 bash ./gradlew test --no-daemon
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 bash ./gradlew build --no-daemon
```

Both commands passed at the source revision above. The packaged jar passed `unzip -tq`, and its contents contain no Pixelmon, Bukkit, Vault, bridge, or external test fixture classes. The current artifact-bound exact server smoke reached `Done` with the mixin target applied and stopped cleanly. The debug session also proved that production logs can discover and print the source commit and artifact SHA without a client connection. The exact GameTest and packaged server runtimes stopped cleanly with `eula=true` verified; their disposable worlds, log directories, and temporary evidence files were removed after hashing.

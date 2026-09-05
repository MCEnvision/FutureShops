# Phase 002 completion packet update

## Current status

Phase 002 remains open. The native Pixelmon workflow implementation and headless evidence were extended at source revision `da4028ca23baf34c4058ea5f32b8e1c0b10051ca`. The exact packaged artifact is `build/libs/futureshops-2.3.0.jar` with SHA 256 `0fd5b2a259d1369a4f1fa751a9eb5391e1c513939c20d3a51757f61b26287d97` and SHA 512 `5a4820d85ae508fde5aa6a0df85dc810dcfaa22a6c9ee70a4e5be9e4b7bdff8cc9351b9437887e83d991803dd45573fabd7c4fe5c4d51abcd3e6fd0ef278ec33`.

No pull request, phase integration, tag, release, upload, or issue 66 mutation was performed. Issue 66 remains frozen for Phase 003.

## New native Pixelmon evidence

The exact Pixelmon `9.4.0` headless run loaded the native mixin and passed all nineteen required dedicated server GameTests. The tests now cover native cart purchase, native `/pay` transfer, native server shop sell, native admin player shop buy with claimed sale escrow, public withdraw and deposit, refund, compensation, physical money refusal, request receipt replay, durable receipt reload, and unknown receipt recovery. The native mutation test also rereads the Pixelmon save adapter file and verifies the completed receipt UUID on disk. Sanitized evidence is in [p002 integration evidence](p002-integration-evidence-2026-09-04.md). The GameTest log SHA 256 is `9efe8de551f6526bf83232c5e05ce284e731257f033c256abf741c47527d4d50`; the packaged server smoke log SHA 256 is `c0e0441699ac8e3dd72abc3348e2ae5951ceef2310e8cef593468f1ef4eb18b8`.

The same source passed nineteen required tests in a fresh temporary standard NeoForge directory with Pixelmon absent and `provider = "internal"`. The absence log SHA 256 is `ec61fb40021a76e1505c3a1819facd2ad84911531bf3195176a99c6d1a4733c0`. The exact Pixelmon jar was not copied into that directory.

The provider mutation router now maps transfer credit, refund, compensation, and deposit requests to the native add path, and transfer debit and withdrawal requests to the native take path. This preserves the request identity and durable receipt checks while allowing the coordinator to complete native transfers.

## Remaining gates

The current unmodified PixelmonEconomyBridge and FinalEconomy stack remains safely refused for `vault` mutation because it has no FutureShops request identity, durable provider receipt, lookup, or idempotent retry contract. The separate proof fixture passes the public registration and one transaction receipt contract, but it is not a claim about that legacy stack.

The complete Phase 002 surface, crash, service loss, restart, reconnect, and recovery matrices against every enabled external surface remain open. The exact hybrid bridge and backend mutation proof must either pass those matrices or remain explicitly refused. Phase 003 cannot begin until the Phase 002 completion packet is accepted through the sequential integration workflow.

## Verification commands

```text
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 bash ./gradlew test --no-daemon
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 bash ./gradlew build --no-daemon
```

Both commands passed at the source revision above. The packaged jar passed `unzip -tq`, and its contents contain no Pixelmon, Bukkit, Vault, bridge, or external test fixture classes. The packaged exact server smoke reached `Done` with the mixin target applied. The debug session also proved that production logs can discover and print the source commit and artifact SHA without a client connection. The exact profile was restored after the run, with `eula=true` verified and no disposable world or log directory left behind.

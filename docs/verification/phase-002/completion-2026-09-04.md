# Phase 002 completion packet update

## Current status

Phase 002 remains open. The native Pixelmon workflow implementation and headless evidence were extended at source revision `31fb88a0fd9fefd8855f9b3bfba9df3e185bfad5`. The exact packaged artifact is `build/libs/futureshops-2.3.0.jar` with SHA 256 `8eb43362d48fc6ea5962e747029714fd875f2c44cfe97ec12370efe1ace8a23e` and SHA 512 `09db2cfc2a134105eca726ab5bbf01edd58eda6856287372e84037ea9e31c0fa18ef5d0d5eefc8236000497e84c72bd8255c43a16140488c4dd04968408fec4e`.

No pull request, phase integration, tag, release, upload, or issue 66 mutation was performed. Issue 66 remains frozen for Phase 003.

The latest follow up is source revision `e1161ac1471fb16a9708dbad8b09c2238b32595c`. Its rebuilt `build/libs/futureshops-2.3.0.jar` has SHA 256 `0845013874976182c5372d3e63a27622b6f0780d61e610621d37d9e75ed8456a` and SHA 512 `1186714b6b7815c8c3ad433a7e4c5b00ce09a48b612cfd59d0256663c991b6c7b67f7be552be47bbc1bc55b7be47f41dcd690ca8212c78266e7d31b3f794caf2`. The artifact manifest binds that jar to the same source revision.

## New native Pixelmon evidence

The exact Pixelmon `9.4.0` headless run loaded the native mixin and passed all nineteen required dedicated server GameTests. The tests now cover native cart purchase, native `/pay` transfer, native server shop sell, native admin player shop buy with claimed sale escrow, public withdraw and deposit, refund, compensation, physical money refusal, request receipt replay, durable receipt reload, unknown compound receipt recovery, wrong entry type recovery, and wrong root type recovery. The native mutation test also rereads the Pixelmon save adapter file and verifies the completed receipt UUID on disk. Sanitized evidence is in [p002 integration evidence](p002-integration-evidence-2026-09-04.md). The latest source revision GameTest evidence SHA 256 is `e9c4bbf16fef7a76930a99599042d98423b457edde2ba568c17a8a99b61eaa75`; the latest artifact-bound packaged server evidence SHA 256 is `5353736683b24a72305944600ef4030e30258f340dcabdfe11edc3c0e233eced`.

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

Both commands passed at the source revision above. The packaged jar passed `unzip -tq`, and its contents contain no Pixelmon, Bukkit, Vault, bridge, or external test fixture classes. The current artifact-bound exact server smoke reached `Done` with the mixin target applied and stopped cleanly. The debug session also proved that production logs can discover and print the source commit and artifact SHA without a client connection. The exact GameTest and packaged server runtimes stopped cleanly with `eula=true` verified; their disposable worlds, log directories, and temporary evidence files were removed after hashing.

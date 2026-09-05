# Phase 002 completion packet update

## Current status

Phase 002 remains open. The native Pixelmon workflow implementation and headless evidence were extended at source revision `3b9abd35dceb1171bf98d4015f490dc9dbb282d1`. The exact packaged artifact is `build/libs/futureshops-2.3.0.jar` with SHA 256 `dc33fa50171b7df1d749765a111877bc410d0691ea117b7a16245b5dddd30606` and SHA 512 `2e6987b3f7d1e465d8e21f3d2cd93a9610f92c9d5b50dc55f3f6d04c25f1b8f046fcfc0a88cd3f9992feeb4490d66391edd9fdb7902e84a99e5b0a8e60a34274`.

No pull request, phase integration, tag, release, upload, or issue 66 mutation was performed. Issue 66 remains frozen for Phase 003.

## New native Pixelmon evidence

The exact Pixelmon `9.4.0` packaged run loaded the candidate jar and passed all nineteen required dedicated server GameTests. The tests now cover native cart purchase, native `/pay` transfer, native server shop sell, native admin player shop buy with claimed sale escrow, public withdraw and deposit, refund, compensation, physical money refusal, request receipt replay, durable receipt reload, and unknown receipt recovery. Sanitized evidence is in [p002 integration evidence](p002-integration-evidence-2026-09-04.md). The packaged log SHA 256 is `2a08502fc0f47e20dd310af009d121616b0631817da31a1ab953840448ea7e49`; the corresponding server log SHA 256 is `8dfd6798b4a3fd161dbe17d881580cf32f8b0b3e2f6f427a4ffff94a0084977b`.

The same source passed nineteen required tests in a fresh temporary standard NeoForge directory with Pixelmon absent. The absence log SHA 256 is `54e3b3d1cfacc87b32e7680e9d007ba61d288a9098f0ae30885ccec61b6a8455`. The exact Pixelmon jar was not copied into that directory.

The provider mutation router now maps transfer credit, refund, compensation, and deposit requests to the native add path, and transfer debit and withdrawal requests to the native take path. This preserves the request identity and durable receipt checks while allowing the coordinator to complete native transfers.

## Remaining gates

The current unmodified PixelmonEconomyBridge and FinalEconomy stack remains safely refused for `vault` mutation because it has no FutureShops request identity, durable provider receipt, lookup, or idempotent retry contract. The separate proof fixture passes the public registration and one transaction receipt contract, but it is not a claim about that legacy stack.

The complete Phase 002 surface, crash, service loss, restart, reconnect, and recovery matrices against every enabled external surface remain open. The exact hybrid bridge and backend mutation proof must either pass those matrices or remain explicitly refused. Phase 003 cannot begin until the Phase 002 completion packet is accepted through the sequential integration workflow.

## Verification commands

```text
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 bash ./gradlew test --no-daemon
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 bash ./gradlew build --no-daemon
```

Both commands passed at the source revision above. The packaged jar passed `unzip -tq`, and its contents contain no Pixelmon, Bukkit, Vault, bridge, or external test fixture classes. The debug session also proved that production logs can discover and print the source commit and artifact SHA without a client connection. The exact profile was restored after the run, with `eula=true` verified and no disposable world or log directory left behind.

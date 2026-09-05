# phase 002 isolation and packaging report

This report binds the current unpublished FutureShops `2.3.0` artifact to the optional integration isolation checks required by `P002-TASK-010`.

## artifact

The artifact is `build/libs/futureshops-2.3.0.jar` from source revision `5bb0199b355d12b6671a310cf8acd3857c67f77d`.

| Check | Result |
| --- | --- |
| SHA 256 | `f97805026224e435d00ed6478f6d122313bc99d44628fa9033602fd15d36173d` |
| SHA 512 | `e319285ac9069b12c3b12701deba8c92cd430dcf6c9b12e7a038886799f1d924a732d164400992307b70ee64ed06cf4bd74faee8971d15587856b80b4eea42cf` |
| `unzip -tq` | passed |
| forbidden external archive matches | `0` |
| nested jar entries | `0` |
| service provider entries | `0` |
| bundled license or notice entries | `0` |
| forbidden `jdeps` references | `0` |
| tracked credential pattern matches | `0` |
| tracked generated or runtime output | only the checked in Gradle wrapper jar |

The archive scan covered Bukkit, Spigot, Vault, Pixelmon, SQLite, bridge, economy plugin, proof fixture, and nested dependency names. The production jar contains the FutureShops optional Pixelmon namespace and no external Pixelmon bytes. The SQLite driver and Vault proof registrant remain separate test fixtures and are not production dependencies.

## classpath and runtime isolation

The same packaged FutureShops jar started in a fresh exact Pixelmon `9.4.0` GameTest runtime with the native mixin applied. The first process passed all twenty tests and produced log SHA 256 `581766494f275acde701caf26a5fe0a605004bccb3ce4c83f3099256a5fe4f24`. The second process reused the world, replayed the request as `CONFIRMED`, and passed all twenty tests with log SHA 256 `a1ae7dfa69e5fd9b9b0360b70c45dd70de214a6528990f5edfadc3577a8ff4bc`.

The same packaged jar also started in a fresh standard NeoForge server directory with Pixelmon absent and `provider = "internal"`. It reached `FutureShops common setup complete`, `FutureShops server starting`, and `Done`, then stopped cleanly. The sanitized log SHA 256 is `12640336ff4cf3ff399aea0933c106bcedf1f0b43ed98abe869439aa572381aa`.

The same packaged jar started in the exact hybrid Youer runtime beside the external Pixelmon, GeckoLib, Vault, FinalEconomy, EverNifeCore, and PixelmonEconomyBridge jars. The separate proof registrant registered `vault`, completed the proof routes, and replayed them after restart. The first and restart log hashes are `85da87db12821a1a43676274377aed3d40f53ec5934e87431c5626499b19920a` and `7d2b2245d22f44be8ad9eb12799421b038995b13237fa4f414b7eae82c2d6bbc`. The post restart proof database SHA 256 is `73430219698eb89e8fc8325af954cdf27f229fb1698e1de346fed4165e438de6`.

All runtimes used Java `21.0.11` on the headless `node-1` Linux host. Every disposable runtime, world, log directory, database, and generated classpath file was removed after hashing. The pre existing external artifacts and shared Gradle cache were not modified.

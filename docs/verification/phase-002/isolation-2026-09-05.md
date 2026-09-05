# phase 002 isolation and packaging report

This report binds the current unpublished FutureShops `2.3.0` artifact to the optional integration isolation checks required by `P002-TASK-010`.

## artifact

The artifact is `build/libs/futureshops-2.3.0.jar` from source revision `a8523e1e15cf5a4db79812ca6581fba25339ce67`.

| Check | Result |
| --- | --- |
| SHA 256 | `ab1284d23159d4e5fddacc7740ad13db433a8c2d37a67ceac7fcde291ee45247` |
| SHA 512 | `972baa653876716a8f2a1dee5340237687710261299d22b7ed329e773ed4dc0e9aa411c74ee947a01abcf90104b888f30ce64cb6aab0dfffa39fd761c949dae4` |
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

An earlier packaged FutureShops artifact started in a fresh exact Pixelmon `9.4.0` GameTest runtime with the native mixin applied. Its first and replay logs remain recorded as `581766494f275acde701caf26a5fe0a605004bccb3ce4c83f3099256a5fe4f24` and `a1ae7dfa69e5fd9b9b0360b70c45dd70de214a6528990f5edfadc3577a8ff4bc`.

The current packaged artifact was revalidated in a fresh exact Pixelmon `9.4.0` GameTest runtime. It passed all twenty tests with log SHA 256 `5bd44830fc864bd78120c2a9500caad8cc2e1f7c916761241f40267d24e083ad`. A separate two process replay run passed all twenty tests in both processes, with first log SHA 256 `6b4696fb9f9a14954d50c038371cf2be9101a8b3bd29fcf5b21b61befaf80564` and restart log SHA 256 `60b5d3e6873c9285f205b7530e75d7774468977f5d1de6d2a6da1546ed05c3e2`.

The earlier packaged artifact also started in a fresh standard NeoForge server directory with Pixelmon absent and `provider = "internal"`. It reached `FutureShops common setup complete`, `FutureShops server starting`, and `Done`, then stopped cleanly. The sanitized log SHA 256 is `12640336ff4cf3ff399aea0933c106bcedf1f0b43ed98abe869439aa572381aa`.

The earlier packaged artifact started in the exact hybrid Youer runtime beside the external Pixelmon, GeckoLib, Vault, FinalEconomy, EverNifeCore, and PixelmonEconomyBridge jars. The separate proof registrant registered `vault`, completed the proof routes, and replayed them after restart. The first and restart log hashes are `85da87db12821a1a43676274377aed3d40f53ec5934e87431c5626499b19920a` and `7d2b2245d22f44be8ad9eb12799421b038995b13237fa4f414b7eae82c2d6bbc`. The post restart proof database SHA 256 is `73430219698eb89e8fc8325af954cdf27f229fb1698e1de346fed4165e438de6`.

The current artifact was revalidated with the separate proof registrant in a pure exact NeoForge `21.1.248` GameTest runtime. The Vault surface log SHA 256 is `fb6456f6bc72ea47f8c20b7ea97021d0640aba1a55ade645ef0f79fab6c3a96e`, and all twenty four registered tests passed. The route log covers server shop sell, player shop buy, cart buy, pay transfer, and physical money refusal. The current hybrid proof fixture remains separate from production packaging.

All runtimes used Java `21.0.11` on the headless `node-1` Linux host. Every disposable runtime, world, log directory, database, and generated classpath file was removed after hashing. The pre existing external artifacts and shared Gradle cache were not modified.

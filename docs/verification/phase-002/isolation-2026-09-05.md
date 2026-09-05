# phase 002 isolation and packaging report

This report binds the current unpublished FutureShops `2.3.0` artifact to the optional integration isolation checks required by `P002-TASK-010`.

## artifact

The artifact is `build/libs/futureshops-2.3.0.jar` from source revision `6bbaf9156bcd8d79bee274717f2ae67d4db6f69e`.

| Check | Result |
| --- | --- |
| SHA 256 | `945d175c363ec06f6b0e965161cff081c5deebf1b1ed899e605b48890fc69563` |
| SHA 512 | `0e38cc66eaaf739413f5a8b2f193d97aca40ea4e8c5be18c4f9f29999ccbc6a1a8055045028a796183b623bf6e4d49478134683b23dbe31445e0db96fc02bae2` |
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

The current artifact was rerun in a fresh copied standard profile with Pixelmon absent and `provider = "internal"`. It reached `FutureShops common setup complete`, `FutureShops server starting`, and `Done`, then stopped cleanly. The sanitized log SHA 256 is `530572c3496d55adc50ff66b539843c3fdc064b8aa1720d493a703b2f6b2d155`. The copied profile verified `eula=true` and was removed after hashing.

The earlier packaged artifact started in the exact hybrid Youer runtime beside the external Pixelmon, GeckoLib, Vault, FinalEconomy, EverNifeCore, and PixelmonEconomyBridge jars. The separate proof registrant registered `vault`, completed the proof routes, and replayed them after restart. The first and restart log hashes are `85da87db12821a1a43676274377aed3d40f53ec5934e87431c5626499b19920a` and `7d2b2245d22f44be8ad9eb12799421b038995b13237fa4f414b7eae82c2d6bbc`. The post restart proof database SHA 256 is `73430219698eb89e8fc8325af954cdf27f229fb1698e1de346fed4165e438de6`.

The current artifact was rerun in a fresh copy of the same exact hybrid profile. The first process log SHA 256 is `da65861f3fe7d3b0fb53cf00bc832884339106cc494b19a67a58398f77f4bca6`, and the restart process log SHA 256 is `2e1a5d09df895135d4683e6832fce0e6230feb0734bf3ca00c57c758f7298d86`. Both processes loaded the reviewed component hashes, reached `Done`, and stopped. The proof registrant confirmed every route on the first process and replayed stable requests without a second transfer effect on restart.

The current artifact was revalidated with the separate proof registrant in a pure exact NeoForge `21.1.248` GameTest runtime. The combined surface and failure log SHA 256 is `fc96646872f1a0cfcb49af9db3d678a55b1feca5f07ddef6c204eff4fa4947fd`, the proof registrant SHA 256 is `ab578f60f8302f304000ee6d0b401ec36bbb93589357ac6dce3f75cc7539bb30`, and all twenty seven registered tests passed. The route log covers server shop sell, server shop buy, player shop buy, public API withdrawal and deposit, cart buy, pay transfer, and physical money refusal. It also covers the Vault failure and recovery matrix. The current hybrid proof fixture remains separate from production packaging.

The current artifact was also run with exact external Pixelmon `9.3.1` in a fresh copied profile. The adapter refused registration with `Pixelmon economy adapter unavailable, pixelmon version is unsupported`, while the server reached `Done` and stopped cleanly. The sanitized log SHA 256 is `b57b25128654bb084f6d1228a449442cb726f6b7bd66bc183a95da1eb6c82df5`. The copied profile verified `eula=true` and was removed after hashing. Pixelmon emitted unrelated legacy artifact errors, with no FutureShops error or exception.

The production artifact was rebuilt from source revision `6bbaf9156bcd8d79bee274717f2ae67d4db6f69e` and rerun in fresh exact Pixelmon `9.4.0` GameTest processes. The artifact SHA 256 is `945d175c363ec06f6b0e965161cff081c5deebf1b1ed899e605b48890fc69563`. The first process log SHA 256 is `dcc938fbb0cff3cc487f2f573fc6087023c727da511fadc398b98aa675cfcb4c`, and the restart process log SHA 256 is `d78bbea1e02f0ac119adbcfaaa3d3e208d57d2e432650d108c521a37091d869a`.

The current separate Vault proof registrant was also rerun in a fresh exact hybrid profile with the unmodified Pixelmon, Vault, FinalEconomy, EverNifeCore, PixelmonEconomyBridge, and Youer components. Its SHA 256 is `ab578f60f8302f304000ee6d0b401ec36bbb93589357ac6dce3f75cc7539bb30`. The first process log SHA 256 is `a01c6428cba7b62e83643058af0a1841acd07014fc4c1691f7133a0f44d96ff4`; the restart process log SHA 256 is `71c75f6f5fc5d9390e4f67547c8aa37e69cc38866fa4dcbd6f83a975733ca68c`. Both processes reached `Done`, the restart reported `transfer=REPLAYED` and a resolved claim, and the disposable runtime was removed after hashing.

All runtimes used Java `21.0.11` on the headless `node-1` Linux host. Every disposable runtime, world, log directory, database, and generated classpath file was removed after hashing. The pre existing external artifacts and shared Gradle cache were not modified.

# Phase 002 DanConomy runtime evidence

This record covers `P002-TASK-016` and `P002-TASK-017` implementation, runtime behavior, refusal, crash recovery, diagnostics, and isolation for the exact DanConomy 1.2.1 profile.

## Runtime profile

| Field | Value |
| --- | --- |
| Host | `node-1`, Linux amd64, headless dedicated GameTest server |
| Java | `21.0.11` |
| Minecraft | `1.21.1` |
| NeoForge | `21.1.248` |
| FutureShops | `2.3.0` development classes |
| DanConomy | `1.2.1` |
| DanConomy SHA 256 | `61d3eb69a3a235929ac2376d151130e61ea4fe65c2f84990618c79e27e954b72` |
| Provider | `danconomy` |
| Default currency | `dollar` |
| Backing type | `LEDGER` |
| EULA | Read back as `eula=true` before every fresh runtime launch |

All launches used unmodified external jars as ordinary runtime mods. No graphical client, renderer, virtual display, or laptop connection was required for these server authority, persistence, and recovery assertions.

## Exact ledger suite

Command shape:

```text
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 bash ./gradlew runGameTestServer --no-daemon -PdanconomyJar=/tmp/exact/danconomy-1.2.1.jar -PverificationGameDirectory=/tmp/disposable-runtime
```

Two fresh server processes over the same disposable world each passed all 32 required GameTests. The first process stored request `00000000-0000-0000-0000-000000000322`, resulting balance `10011`, and a completed provider receipt. The second process loaded that receipt, returned `CONFIRMED` for lookup and duplicate retry, and retained balance `10011`. The second process log SHA 256 is `9f059dbdf0f979fc9bc7620248778190b7d165cb8023d43f7b562eeb00f56565`.

The suite proves:

* Exact mixin application to `LedgerData` and ready lifecycle with all six capabilities.
* Balance query, nonmutating precheck, withdrawal, deposit, transfer debit, transfer credit, fee, refund, and compensation routing.
* Same image balance and receipt persistence in `danconomy_ledger.dat`.
* Exact duplicate deduplication and amount, actor, and mutation kind conflict refusal.
* Receipt lookup and deduplication after a fresh process restart.
* Ordinary DanConomy deposit and withdrawal behavior with no FutureShops receipt minted.
* Unknown receipt entries and invalid receipt checksums returning `RECOVERY_REQUIRED`.
* Checked overflow refusal with no balance change.
* Off thread balance and mutation refusal before SavedData access.
* Coordinator deposit routing through the selected DanConomy provider.
* Server shop buy and sell, cart checkout, player shop buy, multiplayer pay, public API withdraw and deposit, reconnect replay, delivered claim resolution, and physical money refusal while the external provider is active.
* Canonical server command diagnostics using `futureshops debug on danconomy`, `futureshops debug off`, and `futureshops debug status`.

## Operator debug path

The exact GameTest invokes the registered server command path, not the diagnostic helper directly.

```text
futureshops debug on danconomy
futureshops debug off
```

While enabled, one real request aware DanConomy deposit emitted a correlated `futureshops.debug` record with module `danconomy`, lifecycle `READY`, exact runtime versions, request UUID, pseudonymous actor reference, `ledger:dollar` account class, required and observed capabilities, accepted validation, `CONFIRMED`, server side, and `Server thread`. The test asserted the active status and then asserted that the debug session was absent after the off command. No client connection was used.

## Crash and recovery matrix

The controlled crash hook exists only in the GameTest and is disabled when FML reports a production runtime. Each crash launch used a fresh disposable runtime. The intended crash process ended with Java exit code `86`, which Gradle correctly reported as a failed run. A second process then performed recovery.

| Boundary | Crash evidence | Fresh process result |
| --- | --- | --- |
| Before provider mutation | Process halted before the call. | Balance and provider receipt were absent. Stable retry confirmed once and duplicate retry returned the same receipt. |
| After in memory mutation, before durable replacement | The data directory was made read only for the bounded call. Provider returned `RECOVERY_REQUIRED`, provider readiness became `RECOVERING`, a different request was refused with `RECOVERY_REQUIRED`, then the process halted. | No balance or receipt effect survived. Stable retry confirmed once and deduplicated. |
| After durable provider commit, before coordinator acknowledgement | Provider returned `CONFIRMED`, the balance and receipt were durable, and the wrapper halted before coordinator finalization. | The local audit was still pending. Coordinator recovery found the authoritative provider receipt, wrote a terminal local audit record, returned lifecycle to `READY`, and preserved one balance effect. |

The current in memory interruption record is `22f396e237a3c71c8f33583fac13f04b8d1b2e5339162ff41a93c9bc437c2f7`, and its fresh recovery record is `d5c47c482edf3ec8d618e1d98874871a151af5379d88383f2ca0364374fc49bf`. Recovery passed all 32 tests. The existing before mutation and post commit recovery records remain valid because the pending receipt admission change does not alter those boundaries.

## Refusal profiles

The published DanConomy 1.2.0 artifact was installed in a fresh profile while the supported 1.2.1 compile input remained unchanged. FutureShops logged `danconomy version is unsupported`, did not apply the mixin, kept the selected provider fail closed, refused the public balance path, and passed all 32 required tests. The 1.2.0 jar SHA 256 is `b9f8b200fe4ca56b41260cea891e6f36a9ade5443af0e6ab270187daefba4de5`. The runtime log SHA 256 is `a7447fc00b26b7147fa560d9061ca11e4c55ceaebda136671f7762ef74967fff`.

An exact DanConomy runtime with no explicit default kept provider `danconomy`, resolved lifecycle `INCOMPATIBLE`, advertised no mutation or recovery capabilities, and passed all 32 required tests. Its diagnostic was `danconomy default currency is missing or ambiguous`. The log SHA 256 is `6e16d9a4578d8bcc05aacc6627c18163ec8ded41bb247e7428de1525548d3ff7`.

An exact DanConomy 1.2.1 and Pixelmon 9.4.0 runtime selected a `PIXELMON_MIRRORED` default. Both optional mixins applied, but the DanConomy provider stayed `INCOMPATIBLE`, advertised no mutation or recovery capabilities, and passed all 32 required tests. Its diagnostic was `danconomy default currency is not ledger backed`. The log SHA 256 is `fe7ffb0113fc901aa61f97af622a04339ab6860fd387f89c0cc7573407d05e86`.

Neither refusal profile fell back to the internal wallet or attempted an external mutation.

## Isolation and packaging state

Unit tests load without DanConomy on the test runtime classpath and confirm a safely unavailable adapter. A fresh standard NeoForge runtime without DanConomy or Pixelmon passed all 32 required GameTests. Its log SHA 256 is `92f49128d5145d60139d3123fcb579f8d7695d99f4280a042b69996e318ee638`. The packaged server and client results below bind that class level proof to the built artifact.

The packaged `futureshops-2.3.0.jar` built from signed source commit `6725ca6dc93a30aab8df6fd704ed5f2a7b30cfa3` has SHA 256 `93efafd2c71c75b7a9dc622fc912b57f535969767245cba72443cabdf24eabc9` and SHA 512 `c983510dd10473d40f81f9f82ce48cdf78034337f578cb859e50ab7502567782668d19f29925866771bfaf31f103191e1508f80d2f9297c338e88d8f4c254ee9`. Archive inspection found the two optional mixin configurations and original FutureShops adapter classes, no nested jar, no external namespace, and no Pixelmon, DanConomy, Vault, or Bukkit artifact bytes.

The exact packaged jar reached `Done` and shut down cleanly in two installed NeoForge server profiles. The DanConomy profile contained only the exact FutureShops jar, DanConomy 1.2.1, and GeckoLib 4.8.4. Its log SHA 256 is `4e5e611be332462711f6b28f32e465ddcd623559ca55e473ed19be00c298e066`. The standard profile omitted Pixelmon and DanConomy and used the same FutureShops jar. Its log SHA 256 is `45f5e2742bd5159556f488e26d9abde1015cf3698dc27618715d331231545eff`.

A fresh isolated Prism Launcher client profile on the authorized `envision` laptop loaded Minecraft 1.21.1, NeoForge 21.1.248, GeckoLib 4.8.4, and that same FutureShops jar with Pixelmon and DanConomy absent. The owned `Minecraft NeoForge* 1.21.1` window belonged to PID `1997422`, the process was recorded by NVIDIA as a graphics workload on the RTX 5090 laptop GPU, resources reloaded with `mod/futureshops`, and the sound engine reached ready. The isolated profile set `soundCategory_master:0.0` before launch. No PipeWire output stream existed for that exact process at acceptance, while the preexisting Minecraft process was never selected for audio control. The client log SHA 256 is `acfb7e5b26948103d4e2b25c6a7b0a175abe346599b12e6ae98ce19a8987b573`. The owned client and launcher exited, and the isolated profile was registered for cleanup.

The final security pass verified checked integer arithmetic, server thread ownership, immutable request binding, bounded receipt and NBT decoding, normalized world data paths, no followed ledger file symlink, file and directory force calls, persisted balance and receipt readback, exact version gating, pending receipt admission blocking, and typed failure containment. The runtime dependency report contains no DanConomy, Pixelmon, Vault, or Bukkit dependency. Jdeps reports no hard link to those namespaces. The changed source secret pattern scan was clean. The wrong DanConomy compile input failed the exact SHA 256 gate before compilation.

The reviewed DanConomy jar, source checkout, disposable worlds, logs, crash markers, generated configuration, and runtime copies remain outside tracked source and are removed after their last verification consumer.

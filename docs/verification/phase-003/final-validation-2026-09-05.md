# Phase 003 final validation packet

## Scope and source freeze

This packet records the final validation pass for the unpublished FutureShops 2.3.0 NeoForge 1.21.1 candidate. Phase 002 was integrated through pull request 69 into the `1.21.1` branch at merge commit `6346e0ad156472a7c2f8b5d34ec96f7891ef80b9`. The signed phase tag `2.3.0-phase-002` points to that merge commit. The implementation artifact remains bound to that exact source revision. Documentation changes in this phase do not change the implementation binding.

The candidate remains unpublished. No 3.0.0 implementation was added. Issue 66 is intentionally handled only after artifact validation, as required by the phase contract.

## Artifact manifest

| Artifact | Value |
| --- | --- |
| Candidate | `build/libs/futureshops-2.3.0.jar` |
| Source commit in manifest | `6346e0ad156472a7c2f8b5d34ec96f7891ef80b9` |
| Candidate SHA 256 | `75ea7deb671133ecc4205a776d80dc60976b69d0bfa9e1ccaa5b4ac690fb9cf5` |
| Candidate SHA 512 | `ba174f809e9d8a33e7c7f97f13cdc2bfe1adbad7675d0578d16216217576a12a7714222a64de373a0a81a0ffef9df935e173998622f94e7168021ac8345ffbe5` |
| Vault proof registrant | `build/vault-proof/futureshops-vault-proof-1.0.0.jar` |
| Proof registrant SHA 256 | `ab578f60f8302f304000ee6d0b401ec36bbb93589357ac6dce3f75cc7539bb30` |
| Proof registrant SHA 512 | `b24883d97c8f82963909699da70889bcc2d863d7f1be70cabe1a1f4087a2865837398eceffc93ec17fcef7f85bfc29804e8080b1c3336b5db845e55ea01bfc38` |
| Minecraft | `1.21.1` |
| NeoForge | `21.1.248` |
| Java | `21` |
| GeckoLib | `4.8.4` |
| Pixelmon validation jar | `Pixelmon-1.21.1-9.4.0` |
| Pixelmon refusal jar | `Pixelmon-1.21.1-9.3.1` |

The candidate passed `unzip -tq`. An archive scan found no Pixelmon, Bukkit, Spigot, Vault, SQLite, bridge, or proof fixture classes in the production jar. `jdeps --multi-release 21 --ignore-missing-deps` reported only expected optional references and missing Minecraft or NeoForge runtime types. The jdeps report SHA 256 is `5c70ffc106346bd8abe33a2aedf4c4aaa516897552bf7ed6e9e582f51f0c3208`.

## Build and deterministic checks

The following commands passed with Java 21 and the checked in Gradle wrapper.

```text
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 bash ./gradlew test --no-daemon
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 bash ./gradlew runData --no-daemon
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 bash ./gradlew build --no-daemon
```

The final build log SHA 256 is `44e40755f2f1efbd92db6e27a02c4229d3fdfe74c5cc64f0b61dd0c09110c948`. All focused tests passed, all project tests passed, data generation completed, and the final build completed. The generated output was inspected and no unrelated tracked changes remain.

## Exact Pixelmon native validation

The exact Pixelmon 9.4.0 jar was loaded in a fresh disposable dedicated server runtime. The native FutureShops mixin target applied and all twenty required GameTests passed. The first process log SHA 256 is `a6264b87165a9c84d4afa8637a6c0f604c3ea8fa3051b83771261f8e145b0fde`.

The same world was then reopened in a second fresh process. Both processes exited with status 0, applied the mixin target, and reported `All 20 required tests passed`. The first replay log SHA 256 is `0694095db427197a89b0508209c634ffdedb9539e5e8f394e55e2b1bb2bc727c`. The restart replay log SHA 256 is `516e08a0b52ff0ee4d18b16e1bf40edda19b28a74b410a23ee7e0bd1d142c769`.

The native coverage includes cart purchase, `/pay`, server shop sell, player shop buy with claimed sale escrow, public withdraw and deposit, refund, compensation, physical money refusal, request receipt replay, durable receipt reload, request replay after storage reload, persisted balance after reload, unknown compound recovery, wrong entry type recovery, wrong root type recovery, and two process restart replay.

## Standard and incompatible environments

The final candidate ran in a fresh standard NeoForge server directory with Pixelmon absent and `provider = "internal"`. The server reached `FutureShops common setup complete`, `FutureShops server starting`, and `All 20 required tests passed`, then stopped cleanly. The log SHA 256 is `f1b59521813a6d6b8c1d2de9198a7108f9a0e0fdf2653bc99d0c861e8b8a094f`.

The final candidate also ran in a fresh plain dedicated server with Pixelmon 9.3.1 and `provider = "pixelmon"`. FutureShops logged `Pixelmon economy adapter unavailable, pixelmon version is unsupported`, refused the provider, reached `Done`, and stopped cleanly. The log SHA 256 is `fb5d59b0b91c0c17b225abe3cb543b5ae6912a41df4e6cc4beef5e5359410f89`. Pixelmon emitted unrelated malformed item warnings. No FutureShops error or exception occurred.

## Vault surface and failure matrix

The pure exact Vault surface runtime loaded the final candidate, the proof registrant, Pixelmon 9.4.0, GeckoLib, and SQLite JDBC. The registrant was accepted as `provider=vault`. Provider transactions, route diagnostics, failure states, custody transitions, and claim transitions were exercised in one fresh process. All twenty seven required tests passed. The log SHA 256 is `1d962bc0d6a713feff15d889586622d87bf954c54f0dc00fae88c655bc727013`.

The exercised routes include server shop sell, the public API, physical money refusal, player shop buy, server shop buy, cart and `/pay`, insufficient funds, provider failure, ambiguous outcome, retry, and frozen recovery. The production jar still contains no proof backend classes. The proof registrant is a separate test and integration surface.

## Exact hybrid validation and restart recovery

The exact Youer hybrid profile was copied to a disposable directory. The final candidate and proof registrant loaded beside Pixelmon 9.4.0, Vault 1.7.3, FinalEconomy 1.0.9, EverNifeCore 2.0.4.4, and PixelmonEconomyBridge 1.1.6. The copied profile used a fresh world, port 25583, `eula=true`, and `provider = "vault"`.

The first process registered the proof provider, reached `Done`, confirmed provider withdrawal and deposit, and recorded `claim_state_initial=PENDING`, `transfer=CONFIRMED`, and `balance=89`. The first log SHA 256 is `0ee7ac7c6c641237c384f7313836df811a799985fe24acd94b7bcc556b87210b`.

The second process reopened the same world and replayed the stable requests. It reported `claim_state_initial=RESOLVED`, `transfer=REPLAYED`, source balance `94`, target balance `106`, and proof balance `89`. No second balance effect occurred. The restart log SHA 256 is `0187a587cc5e9abdcbea0b93da7eb0839aa4f7e0c77036555fa3fd8550e0d795`.

The unmodified legacy bridge remains refused for production mutation unless it supplies the transaction aware backend contract. The proof registrant demonstrates the required public API and receipt behavior without claiming that the legacy bridge is transaction safe.

## Headless debug procedure

The server debug command was exercised without a client connection in a fresh standard runtime. The exact console sequence was:

```text
futureshops debug status
futureshops debug on all
futureshops debug status
futureshops debug on provider
futureshops debug status
futureshops debug off
futureshops debug status
stop
```

The log SHA 256 is `09893d66473d4882e7150069885cff9c753311ecfafd5805c1f7e759d1399c4a`. The output proved that debug is off by default, that module sessions can be enabled and disabled, and that the bounded diagnostic record includes source commit, artifact SHA 256, provider, lifecycle, operation, request, actor, capabilities, journal, receipt, custody, claim, error, and next action. Server logs and dedicated GameTests are the primary evidence surface. A client connection is only needed for a client only visual or input assertion.

## Cleanup and host evidence

All disposable runtimes used for the Pixelmon, replay, standard, incompatible, Vault, hybrid, and debug runs were stopped, hashed where evidence required it, removed, and verified absent. The runs used the headless node 1 dedicated server runtime. No client or graphical assertion was required by this phase packet. The authorized `eula=true` setting was verified in each disposable runtime and no runtime data was copied into tracked source.

## Evidence identifiers and exit result

This packet supplies `EVD-VER-001` for deterministic and runtime validation and `EVD-ART-001` for the final candidate manifest and isolation audit. The requirement trace in [requirement trace](requirement-trace-2026-09-05.md) supplies `EVD-VER-002`. The GitHub issue readback is recorded as `EVD-GH-001` in [the issue 66 evidence packet](github-issue-66-2026-09-05.md).

The candidate is technically validated and remains unpublished pending owner acceptance. No release upload, public publication, or 3.0.0 implementation was performed.

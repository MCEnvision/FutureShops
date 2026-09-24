# Dynamic pricing candidate verification

Date: 2026-09-24. Target: FutureShops 2.5.0, Minecraft 1.21.1, NeoForge 21.1.248, Java 21, GeckoLib 4.8.4. Base: `cc2d69425beea2c93a4000e44d49381deba8eed2` on `1.21.1/2.4.1`. Implementation branch: `envy/2.5.0-dynamic-pricing`.

## Reproduction and verification

The first four pricing GameTests were run against the unchanged 2.4.1 production sources after correcting the fixture JSON keys and configuring NeoForge mock connections. All four failed at the intended assertions: adjusted purchase debit, adjusted sell credit, adjusted catalog prices, and the first purchase after demand recalculation. The remaining 33 existing tests completed without reported failure.

After the implementation, Java 21 unit verification passed 261 tests in 65 suites, with zero failures, errors, or skips. The full Gradle build passed. The dedicated GameTest suite passed all 40 reported required tests in two consecutive final runs. Seven pricing tests exercised the internal provider; some older integration tests intentionally return success when their optional provider is absent, so this total is not proof of external provider compatibility.

Commands:

```sh
./gradlew --no-daemon runGameTestServer --dry-run
./gradlew --no-daemon test build runGameTestServer -PverificationGameDirectory=.gradle/verification/fixed
git diff --check
```

The task graph launches `GameTestServer`, with no client or renderer. Runs occurred on the headless `node-1` host in an isolated checkout, using dedicated disposable runtimes bound to loopback with ephemeral ports. Each runtime's `eula.txt` was verified as `eula=true`. No laptop client was launched. No configured formatter or separate lint task exists in this release branch; whitespace validation passed. No data generation was needed because resources were unchanged.

Covered behavior includes buy and cart debits, sell payouts, inventory effects, catalog values, cart price warnings, promotion ordering, the 400 tick recalculation interval, activity counters, affected session refresh, stale snapshot refusal, distinct listing keys, sell only listings, disabled pricing, rounding, overflow, configured bounds, nonmutating queries, persistence, and catalog packet round trips. Source guards also require confirmation dialogs to retain the quoted revision and cart contents.

## Remaining acceptance

Client rendering, mouse interaction, live multiplayer synchronization, reconnect, and full process restart acceptance remain pending. Existing external economy integrations were not revalidated with optional mods. Keep the pull request in draft and do not enable automatic merge. The exact procedure is in [local acceptance](../test/dynamic-pricing-2.5.0.md#required-local-acceptance-before-merge).

The temporary checkout, build output, worlds, raw logs, and index are disposable after the signed branch is pushed and the candidate jar and checksum are preserved for local testing. Historical branches and tags and the owner's active 1.20.1 checkout must remain untouched.

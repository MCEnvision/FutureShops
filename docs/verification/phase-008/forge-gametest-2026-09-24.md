# phase 008 forge gametest startup evidence

Observation date: 2026-09-24
Phase: CORE-PHASE-008
Task: P008-TASK-004
Runtime: `.phase008-gametest-verify-20260924`
Loader: Forge 1.20.1 47.4.20
Java: 17.0.19

## result

The server only `runGameTestServer` task launched with the FutureShops game test
namespace and exited normally. A Gradle init hook set the `gameTestServer`
`RunConfig.workingDirectory` before task preparation, and the log proved that
Forge resolved `FMLPaths GAMEDIR` to the disposable runtime above. The run
completed the `futureshops.offer_service:1` batch with nine tests and reported
all nine required tests passed. FutureShops loaded its default shop, initialized
the physical currency provider, loaded the Bazaar catalog, and completed the
legacy wallet migration before the tests ran. The passing fixtures included
stale buy, stale sell, stale cart, claim, free offer, diagnostics, finite offer,
cart offer, and multiplayer ATM coverage.

This is server startup and gameplay regression evidence. It does not claim the
dedicated stale request ordering fixture or laptop client rendering and input gates.

## cleanup

The server process exited normally. The exact disposable runtime and temporary
init script are removed after this evidence is committed. The earlier failed
rerun that resolved the preexisting repository `run` directory remains recorded
as a protected cleanup boundary. That preexisting `run` directory was preserved
and not reverted or deleted, but its logs, configuration, world data, and
FutureShops state may have been refreshed by that earlier failed attempt.

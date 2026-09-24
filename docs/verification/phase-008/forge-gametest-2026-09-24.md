# phase 008 forge gametest startup evidence

Observation date: 2026-09-24
Phase: CORE-PHASE-008
Task: P008-TASK-004
Runtime: `.phase008-gametest-runtime`
Loader: Forge 1.20.1 47.4.20
Java: 17.0.19

## result

The server only `runGameTestServer` task launched with the FutureShops game test
namespace and exited normally. Forge resolved `FMLPaths GAMEDIR` to the disposable
runtime above. The run completed the `futureshops.offer_service:1` batch with six
required tests and reported all six passed. FutureShops loaded its default shop,
initialized the physical currency provider, loaded the Bazaar catalog, and completed
the legacy wallet migration before the tests ran.

This is server startup and gameplay regression evidence. It does not claim the
dedicated stale request ordering fixture or laptop client rendering and input gates.

## cleanup

The server process exited normally. The runtime is removed after this evidence is
committed. The preexisting repository `run` directory and Gradle caches are not test
owned and remain untouched.

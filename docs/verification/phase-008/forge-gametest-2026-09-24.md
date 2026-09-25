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
committed. A later bounded rerun exposed that the ForgeGradle `MinecraftRunTask`
ignored the temporary init script working directory override and resolved
`FMLPaths GAMEDIR` to the preexisting repository `run` directory. That owned
process was stopped as soon as the mismatch was confirmed. The exact temporary
`.phase008-gametest-20260924` directory and init script were removed. The
preexisting `run` directory was preserved and not reverted or deleted, but its
logs, configuration, world data, and FutureShops state may have been refreshed
by that failed attempt. This leaves the rerun cleanup and disposable directory
gate open until a task configuration that proves the exact game directory is
used.

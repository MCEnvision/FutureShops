# phase 008 server startup attempt

Observation date: 2026-09-24
Phase: CORE-PHASE-008
Task: P008-TASK-002

## bounded attempt

The Forge 1.20.1 `runServer` task was started from `node-1` with the phase 008
candidate source and Java 17. An initial disposable runtime attempt exposed that
the run configuration still selected the repository's existing `run` directory.
The exact owned Java process was stopped after that mismatch was confirmed.

A second bounded attempt used a task-local `user.dir` override and a fresh
disposable runtime. Forge reported `FMLPaths GAMEDIR is
/mnt/hermes/projects/FutureShops/.phase008-server-gamedir`, loaded FutureShops
3.0.0-beta.2, and reached its normal dedicated server readiness line. This is
startup evidence only. No client or stale packet fixture was connected, so it is
not the phase server behavior gate.

## cleanup and boundary

The disposable directory, generated configuration, world lock, init script, and
path marker were removed after the owned process exited.
The repository `run` directory and its world and configuration files are
preexisting user-owned data. The attempted startup may have refreshed those
files, including `run/world/level.dat` and the existing FutureShops TOML files,
so they were preserved and not reverted or deleted. This is a cleanup leftover
and invalidates the first attempt for the dedicated server gate.

No client, audio stream, watcher, public endpoint, or additional world was
created. The dedicated server stale ordering and laptop client gates remain
unverified and must be rerun with a task configuration that proves the exact
disposable game directory before launch.

# Phase 000 Forge baseline

Captured 2026-09-01 in clean worktree `/tmp/futureshops-forge-baseline.4PlFvM` at `origin/1.20.1` commit `c6709e12ca7084ee068b2497a577b8d47c12f6fd`. The baseline used Java 17 from `/usr/lib/jvm/temurin-17-jdk-amd64`, Gradle wrapper 8.14.4, and an independent Gradle user home.

## Results

| command | result | decisive evidence |
| --- | --- | --- |
| `bash ./gradlew tasks --all --no-daemon` | pass | task graph lists `runData`, `runGameTestServer`, `runServer`, `runClient`, `verifyBetaReleaseIdentity`, and `verifyPackagedDependencyBoundary` |
| `bash ./gradlew test --no-daemon` | pass | build successful in 2m 14s |
| `bash ./gradlew runData --no-daemon` | pass | build successful in 30s |
| `bash ./gradlew runGameTestServer --no-daemon` | pass | five tests completed and all five required tests passed |
| `bash ./gradlew build --no-daemon` | pass | verification tasks ran and build successful in 16s |
| `bash ./gradlew runServer --no-daemon` | pass | readiness sentinel reached `Done (6.332s)! For help, type help`; an RCON readiness-aware launcher issued `stop`, the log recorded `FutureShops server stopping`, players and worlds saved, RCON stopped, and Gradle returned exit code 0 |
| `xvfb-run ... bash ./gradlew runClient --no-daemon` | pass | readiness sentinel reached `FutureShops client setup complete`; the launcher dismissed the first-run accessibility screen, selected `Quit Game` from the title screen, the log recorded `Minecraft Stopping!`, and Gradle returned exit code 0 |

The first server preflight without an isolated EULA marker stopped at the normal EULA gate. The later run used only the disposable baseline worktree and port 25566. No legal or runtime files were changed in the planning worktree.

## Artifact and diff

The produced artifact is `futureshops-3.0.0-beta.1.jar` with SHA256 `5a3f6c03bc2e92960e9d6523dfc0d44a90867397f038f644f1660d8ad15cf52e`. `META-INF/mods.toml` declares Forge `47` and version `3.0.0-beta.1`, and the archive contains `futureshops.png` and the expected assets and classes. The dependency boundary verification passed.

The isolated worktree has no tracked changes. `run-data/` is generated untracked output and was not copied or staged.

## Baseline classification

Forge is green at the exact approved support ref. The server and client launchers stop through their normal control paths after readiness and return success. No Forge repository defect was opened by this task.

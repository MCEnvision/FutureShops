# Phase 000 NeoForge baseline

Captured 2026-09-01 in clean worktree `/tmp/futureshops-neoforge-baseline.0nMvlX` at `origin/1.21.1` commit `247d8f6842bfa1f586e5b18a9aab67cabd3db89f`. The baseline used Java 21 from `/usr/lib/jvm/java-21-openjdk-amd64`, Gradle wrapper 8.8, and an independent Gradle user home.

## Results

| command | result | decisive evidence |
| --- | --- | --- |
| `bash ./gradlew tasks --all --no-daemon` | pass | task graph lists `runData`, `runGameTestServer`, `runServer`, and `runClient` |
| `bash ./gradlew test --no-daemon` | pass | build successful in 1m 47s |
| `bash ./gradlew runData --no-daemon` | pass | build successful in 10s |
| `bash ./gradlew runGameTestServer --no-daemon` | invalid proof | Gradle reports success, but the server log reports `no test functions were given` and a fatal game test server startup failure |
| `bash ./gradlew build --no-daemon` | pass | build successful in 4s |
| `bash ./gradlew runServer --no-daemon` | pass | readiness sentinel reached `Done (0.898s)! For help, type help`; an RCON readiness-aware launcher issued `stop`, the log recorded normal server shutdown, and Gradle returned exit code 0 |
| `xvfb-run ... bash ./gradlew runClient --no-daemon` | pass | readiness sentinel reached `FutureShops common setup complete`; the launcher dismissed the first-run accessibility screen, selected `Quit Game` from the title screen, the log recorded `Minecraft Stopping!`, and Gradle returned exit code 0 |

The initial server attempt used the default port and encountered an address already in use from an unrelated process. The rerun used only the disposable baseline worktree and port 25567. No live server process was stopped.

## Artifact and diff

The produced artifact is `futureshops-2.2.0.jar` with SHA256 `033e11625096968259ff8b0b3ca1a4008540a21ba5b213794e5150cbad3f2d55`. `META-INF/neoforge.mods.toml` declares NeoForge `21` and version `2.2.0`. The isolated worktree has no tracked changes.

The NeoForge game test task is present but no test functions are registered. The finding was searched before creation and was recorded as issue 35, `https://github.com/MCEnvision/FutureShops/issues/35`. Source review confirmed that this support line has no NeoForge GameTest source or registered test function, so the task is not applicable at this exact baseline. The issue was closed as not planned after recording the source backed disposition. Its two automatic intake comments were created at 2026-09-02T05:00:26Z and 2026-09-02T05:01:18Z. The disposition is not accepted as GameTest proof. Later phases must record the exact source backed not applicable result whenever this task has no tests.

## Candidate inventory

The issue 22 candidate was independently checked in `/tmp/futureshops-neoforge-candidate.sFQ6pj` at `bfba91f7b0c51b03d07117c4f1851c38a98f6186`. It declares version `2.2.1`, is one commit ahead of the captured `origin/1.21.1` head, and changes only the recorded NeoForge documentation, build metadata, client screen package, and background policy test. Its independent `test` and `build` commands passed. Candidate SHA256 is `d9a9b5129751dbaa14ceed138b1aba4d6f13b31af0ea3f7e144f3ed3e44a0387`. CORE-PHASE-001 still owns full regression, client, JAR, pull request, merge, and issue closure proof.

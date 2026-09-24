# Phase 000 host and cleanup readiness

Observation date: 2026-09-24
Phase: CORE-PHASE-000
Task: P000-TASK-004
Requirement: CORE-REQ-001
External prerequisite: EXT-002

## Headless node discovery

| Capability | Observation |
| --- | --- |
| Host | `node-1` |
| Project anchor | `/mnt/hermes/projects/FutureShops` |
| Current Java | OpenJDK `25.0.3` |
| Minecraft client tools | No `nvidia-smi`, `hyprctl`, `wpctl`, or `pactl` executable on this host |
| Allowed use | Headless Gradle, static analysis, unit tests, data generation, dedicated server, and server-only GameTest only |

The current checkout is Forge 1.20.1 and the plan requires Java 17. The current node default is Java 25, so later Java-sensitive work must select and record a Java 17 toolchain before build or runtime acceptance. No client or display task was started on this host.

## Gradle task graph discovery

`bash ./gradlew --no-daemon --console=plain tasks --all` completed with Gradle `8.14.4`. The wrapper file mode is `664`, so direct execution as `./gradlew` is denied and the checked wrapper must be invoked through `bash` or have its mode repaired in a later scoped task. The task graph exposed these relevant classes.

| Task class | Observed tasks | Later classification |
| --- | --- | --- |
| Build and checks | `assemble`, `build`, `check`, `test`, `jar`, `jarJar`, `verifyBetaReleaseIdentity`, `verifyPackagedDependencyBoundary` | Headless node after Java 17 selection |
| Main and test compilation | `compileJava`, `compileTestJava`, `compileGameTestJava`, `testClasses`, `gameTestClasses` | Headless node |
| Forge server preparation | `prepareRunServer`, `prepareRunServerCompile`, `runServer` | Headless node dedicated server only |
| Forge GameTest preparation | `prepareRunGameTestServer`, `prepareRunGameTestServerCompile`, `runGameTestServer` | Headless node only when task graph proves no client or display dependency |
| Forge client preparation | `prepareRunClient`, `prepareRunClientCompile`, `runClient` | Authorized `envision` laptop only |
| Data preparation | `prepareRunData`, `prepareRunDataCompile`, `runData` | Headless node if task graph remains server or data only |

The task graph itself proves task names and classification inputs only. It does not prove that a task remains headless after dependency resolution. Every later launch must refresh the graph and retain the exact invocation with its runtime evidence.

The wrapper mode finding is tracked as GitHub issue `79`. No mode fix was made in this readiness phase. The later CI task owns the fix and must prove direct wrapper execution on the Forge beta branch.

The task graph discovery produced a temporary Gradle problems report under the ignored build directory. That exact report was removed after inspection. The existing ignored `.gradle` and `build` directories were retained because their prior ownership and contents were not established by this read only task. No process, runtime, world, stream, or client remained owned by this task.

## Authorized laptop discovery

The authorized graphical host was reached read only as `envy@100.125.83.96`.

| Capability | Observation |
| --- | --- |
| Host | `envision` |
| Kernel | Linux `7.2.0-1-cachyos` |
| Discrete renderer | NVIDIA GeForce RTX 5090 Laptop GPU |
| Window compositor | Hyprland process PID `1823`, instance socket `wayland-1` |
| Window control | `/home/envy/.local/bin/hyprctl` |
| Audio controls | `/usr/bin/wpctl` and `/usr/bin/pactl` |
| Java 17 candidate | `/home/envy/.jdks/temurin-17/bin/java` |
| Other discovered Java candidates | Temurin 8, 16, 21, and 25 |

The candidate project path `/home/envy/Documents/Codex/2026-07-22/if/FutureShops` exists but is empty and has no Git metadata. A mirrored path `/home/envy/Remote/node-1-mnt/hermes/projects/FutureShops` contains a checkout, but a read only Git status query exceeded the bounded SSH observation window. Neither path was changed. Phase 010 must refresh the laptop checkout, exact artifact transfer, branch revision, renderer, window identity, and joined-world proof immediately before graphical acceptance. This discovery limitation does not waive any later client gate.

## Disposable resource registry

| Resource ID | Intended owner | State after discovery |
| --- | --- | --- |
| `P000-PIXELMON-SCRATCH` | Pixelmon artifact intake | Temporary files deleted, directory removed |
| `P000-GRADLE-SCRATCH` | Isolated Gradle task graph attempt | Temporary task output deleted, directory removed |
| `P000-GRADLE-PROJECT-REPORT` | Gradle task graph command | Exact generated problems report removed |
| `P000-MINECRAFT-SERVER` | Later runtime phases | Not created |
| `P000-MINECRAFT-CLIENT` | Later runtime phases | Not created |
| `P000-AUDIO-WATCHER` | Later laptop acceptance | Not created |
| `P000-WORLD` | Later runtime phases | Not created |

No Minecraft client, dedicated server, GameTest, watcher, renderer, audio stream, world, or login session was launched during this task. The pre-existing ignored Gradle and build directories are preserved, not treated as phase-owned disposable output.

## Proof limit

This report proves host anchors, available controls, Java candidates, task names, task classification inputs, and cleanup feasibility. It does not prove Java 17 compilation, headless server behavior, client rendering, input, synchronization, reconnect, audio mute, or final artifact acceptance. Those remain explicit later gates.

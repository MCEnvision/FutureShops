# Phase 006 backend integration evidence

## Scope

This packet records the Phase 006 Forge verification at the Phase 005 merged input
`bdd7af9e0c4d592104c72f486a89c0f95adf8a9d`. The phase branch is
`envy/phase-006-backend-integration`, based on `origin/1.20.1`.

The integration audit found one repository owned presentation defect. `MarketModuleScreen`
advertised optional navigation tabs before the first capability response by treating a missing
capability as visible. The authoritative open packet already carries the configured module flags.
The repair uses those flags until the capability snapshot arrives, while the server continues to
choose the route and claims remain reachable through an explicitly opened claims route. GitHub
issue [#55](https://github.com/MCEnvision/FutureShops/issues/55) was created before the repair.

## Source and regression evidence

The repair is limited to `MarketModuleScreen.moduleVisible`. A missing capability now falls back to
`packetConfigured(target)` instead of `true`. The focused source contract
`MarketCapabilityIntegrationSourceTest.optionalTabsUseAuthoritativePacketFlagsBeforeCapabilitiesArrive`
asserts this boundary.

The following commands passed with Java 17 and one Gradle worker.

```text
JAVA_HOME=/usr/lib/jvm/temurin-17-jdk-amd64 bash ./gradlew test --tests com.enviouse.futureshops.client.market.MarketCapabilityIntegrationSourceTest --no-daemon --max-workers=1
JAVA_HOME=/usr/lib/jvm/temurin-17-jdk-amd64 bash ./gradlew test --no-daemon --max-workers=1
JAVA_HOME=/usr/lib/jvm/temurin-17-jdk-amd64 bash ./gradlew runData --no-daemon --max-workers=1
JAVA_HOME=/usr/lib/jvm/temurin-17-jdk-amd64 bash ./gradlew runGameTestServer --no-daemon --max-workers=1
```

The focused and complete unit suites passed. Data generation completed with no tracked resource
drift. GameTest reported `All 5 required tests passed :)` after the dedicated server released its
world lock. A previous GameTest launch was rejected by that active lock and was not counted as
evidence.

## Dedicated server and multiplayer evidence

An isolated Forge 1.20.1 dedicated server used a disposable world and the phase configuration.
It reached `Done` and loaded FutureShops. Read only RCON checks reported:

* escrow runtime `READY`;
* recovery clear and conservation verified;
* all module control records enabled;
* stock migration complete with 17 of 17 entries;
* no open listings, orders, or pending recovery.

Two independent Forge clients used separate profiles, game directories, ports, and logs. Both
completed FutureShops client setup, completed the modded handshake, and joined the same dedicated
server. The server list command observed both players concurrently. The clients later timed out
their disposable smoke sessions after joining; the resulting in game death screen was unrelated
to FutureShops and no FutureShops client error was logged.

The first client launch used a shared launcher argument that duplicated `gameDir` and was rejected
by the launcher parser. The corrected runs used separate worktrees and game directories. The
launcher harness error was not used as product evidence.

## Security and ownership review

The integration review confirmed that module visibility is presentation only. Server module access,
session validation, route nonce validation, permissions, readiness, maintenance, replay identity,
escrow custody, claims, and configuration generation remain server authoritative. The repair does
not add a packet, command, persistent field, bypass, production fault injector, or client authority.
No credentials, private player data, raw logs, or generated runtime files are included in this
packet.

## Merged revision verification

Pull request #56 merged the repair into `1.20.1` at commit
`28cee8e94002508432df416d0277d6cc1df7ab35`. The exact merged revision reran the focused source
contract, complete unit suite, data generation, GameTest server, and Gradle build. All commands
completed successfully. GameTest again reported `All 5 required tests passed :)`.

The merged JAR is `futureshops-3.0.0-beta.2.jar`, 5,851,374 bytes, with SHA 256
`c7869fedf07a76bc3f070d9e42a2778a987cb06649a08f611f6e1ff774e52601`. JAR inspection confirmed
`META-INF/mods.toml`, the FutureShops assets, and the FutureShops data resources are packaged.

The dedicated server smoke at the merged revision reached `Done` and loaded FutureShops. Read only
RCON checks reported escrow runtime `READY`, recovery clear, conservation verified, all module
control records enabled, stock migration complete with 17 of 17 entries, and zero listings, orders,
or pending recovery. Two clean Forge clients at the same merged revision joined the server at the
same time. The server observed both `phase_six_e` and `phase_six_f` online, and both clients logged
successful FutureShops setup and modded handshake completion before their disposable sessions ended.

The phase pull request checks passed for Gradle, dependency review, secret scan, Java and Kotlin
CodeQL, Python CodeQL, and the aggregate CodeQL gate. Documentation, Node, and dependency
submission checks were skipped by the repository workflow because their applicable inputs were
absent. The final branch diff passed `git diff --check` and contains no credentials, private player
data, or generated runtime files.

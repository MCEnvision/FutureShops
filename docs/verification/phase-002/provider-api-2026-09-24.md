# phase 002 provider api evidence

candidate branch: `envy/3.0.0-beta.3-phase-002`
loader: forge 47.4.20
minecraft: 1.20.1
java: 17

## implementation

The public economy package now exposes the frozen v1 provider method projections, immutable
account, root, leg, currency, binding, and bound request types, typed query, readiness, mutation,
and receipt outcomes, and deterministic registration lifecycle. External mutation remains closed
until the durable coordinator phase. The existing shop data packet carries the active provider id
alongside the existing currency metadata. Compatibility constructors continue to default to the
internal provider. Leaderboard projection is typed and reports capability missing for providers
that do not support it, rather than returning an empty success.

## checks

The focused api, wire, protocol, and unavailable leaderboard suite passed with:

```text
./gradlew --no-daemon --console=plain test --tests com.enviouse.futureshops.api.economy.* --tests com.enviouse.futureshops.server.economy.UnavailablePublicEconomyProviderTest --tests com.enviouse.futureshops.WireRoundTripTest --tests com.enviouse.futureshops.ProtocolVersionConstantTest
```

The complete Forge test suite passed with:

```text
./gradlew --no-daemon --console=plain test
```

An independently compiled fixture addon was compiled with `javac --release 17` against a temporary
jar containing only `com.enviouse.futureshops.api.economy` classes plus a local server context stub.
The fixture registered through `EconomyProviderRegistry`, created a binding, returned a distinct
confirmed query balance of `777` minor units, and returned `UNAVAILABLE CAPABILITY_MISSING` for
the unsupported leaderboard projection. Its source, classes, jars, stub, and empty parent
directories were removed after verification.

No graphical client was required for this phase. The client proof is the existing Forge shop data
packet and handler path, with the provider id validated before it enters client state. The exact
Gradle owned report paths were removed after the final test consumer and verified absent:
`build/reports/tests/test`, `build/test-results/test`, and `build/reports/problems`.

## dedicated server gate

The Forge `runServer` task was run on `node-1` with Java 17 and a disposable `run` directory.
After moving the owned server to port `25566` to avoid an unrelated listener on `25565`, the
server reached `Done`, loaded FutureShops 3.0.0-beta.2, initialized the internal economy and
catalog paths, and logged the FutureShops server start and stop lifecycle. The server process,
world, generated configs, logs, crash output, and exact `run` directory were stopped and removed
after the gate. `eula=true` was written and read back before launch.

## boundary scans

The production archive contained 45 public API economy entries and no `bukkit`, `vault`, `spigot`,
`sqlite`, `pixelmon`, or `danconomy` archive entries. The resolved runtime dependency report had
no matching optional provider or SQLite dependency. The public API source package had no Bukkit,
Vault, Spigot, SQLite, reflection, or service lookup references. Existing Refined Storage 2
reflection remains outside the provider API package and is an unrelated optional integration.

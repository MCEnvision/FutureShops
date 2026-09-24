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
internal provider.

## checks

The focused api, wire, protocol, and client state suite passed with:

```text
./gradlew --no-daemon --console=plain test --tests com.enviouse.futureshops.api.economy.* --tests com.enviouse.futureshops.WireRoundTripTest --tests com.enviouse.futureshops.ProtocolVersionConstantTest --tests com.enviouse.futureshops.client.ShopClientStateCartPolicyTest
```

The complete Forge test suite passed with:

```text
./gradlew --no-daemon --console=plain test
```

An independently compiled fixture addon was compiled with `javac --release 17` against only
`build/classes/java/main`, packaged as a temporary jar, and verified with `jar tf`. The fixture
registered through `EconomyProviderRegistry` and used only public `api.economy` types. Its source,
classes, jar, and empty parent directories were removed after verification.

No graphical client was required for this phase. The client proof is the existing Forge shop data
packet and handler path, with the provider id validated before it enters client state. Temporary
test reports remain owned by the current Gradle test run until the phase packet's final consumer;
they must be removed during the phase cleanup gate.

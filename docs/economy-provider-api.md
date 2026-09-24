# Economy provider API v1

FutureShops exposes a server only economy provider API for integrations that need to provide
authoritative balances and currency metadata. The API is additive to the existing FutureShops
wallet and public shop API. An integration must be compiled against the published API package and
must not depend on server implementation classes.

## Registration

Register a provider before the server lifecycle freezes the registry.

```java
EconomyProviderRegistry.register(
        new ProviderId("example"),
        context -> new ExampleProvider(context));
```

Provider identifiers are lowercase letters, digits, and underscores, begin with a letter, and are
between two and sixty four characters. Registration returns a typed `RegistrationResult`. Duplicate,
invalid, reserved, late, incompatible, or factory failures do not change the registry or active
selection. Registration is server side and must run on the server lifecycle thread.

## Provider contract

Providers expose currency metadata, capabilities, readiness, authoritative balance queries, and
typed request outcomes. Amounts are positive checked `long` values in minor currency units. A
non confirmed result is never interpreted as a zero balance or a successful mutation.

`BindingV1` records the provider, adapter, backend, artifact, account, currency, generation, and
receipt protocol identities. `BoundRequestV1` adds the immutable root, leg, operation, amount, and
payload fingerprint. Changed identity or payload is refused before any effect.

The frozen v1 method projections are `bind`, `query`, `precheck`, `mutate`, and `lookup`. The
default mutation projection remains closed until the durable coordinator and a verified adapter
are available in later releases. Implementing the API or advertising capabilities does not by
itself prove an adapter or permit external value movement.

## Selection and restart behavior

The default provider is `internal`. Set `economy.provider` in
`config/futureshops/futureshops-common.toml` to request a registered provider. A valid reload only
stages the requested provider and reports that a restart is required. The active provider remains
unchanged until the server restarts. Missing, invalid, incompatible, or unavailable providers keep
the server online but refuse monetary operations with a typed unavailable result. FutureShops does
not fall back to the internal wallet, copy balances, convert currencies, or reconcile accounts.

The active provider id and currency metadata are sent through the existing shop data packet. The
client treats those fields as server owned display metadata and never selects a provider locally.

## Compatibility boundary

No Bukkit, Vault, Spigot, reflection, service lookup, bundled bridge, or third party provider binary
is included in this API. A separately installed provider must supply its own implementation and
durable receipt proof. Browse and pure barter remain available when a selected monetary provider is
unavailable.

# Phase 004 bridge conformance

The independently compiled transactional provider fixture uses only the FutureShops public economy API. Its backend commits the minor unit effect and immutable receipt together and resolves that receipt from a fresh provider instance.

`TransactionalProviderFixtureTest` passed with `./gradlew test --no-daemon --tests com.enviouse.futureshops.api.economy.TransactionalProviderFixtureTest`.

The fixture is test scoped and is not included in the production jar. Legacy boolean providers, missing lookup contracts, changed bindings, duplicate identities, and unavailable registrations remain refused by the public API boundary. The production source does not import or bundle the fixture backend.

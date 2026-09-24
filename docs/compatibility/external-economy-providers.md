# External economy provider compatibility

FutureShops 3.0.0 beta 3 on Forge 1.20.1 accepts only provider paths that preserve the server owned coordinator contract. A provider must expose an immutable account binding, minor unit precision, a durable effect and receipt in one commit, and lookup by the original request identity after a fresh process.

| provider path | state | exact boundary |
| --- | --- | --- |
| Pixelmon Forge 1.20.1 9.2.3 | supported when the exact artifact and account target are present | native `PlayerPartyStorage` balance mutation and `writeToNBT` save image |
| Pixelmon absent | refused before dispatch | no optional class or mixin target is loaded |
| Pixelmon other version, altered artifact, custom account, or hybrid account | refused before dispatch | exact version, descriptor, and binding gate |
| independently registered transactional provider | supported only as API v1 conformance | public registration, durable effect plus receipt, and lookup after restart |
| absent or legacy boolean provider | unavailable and refused | no durable receipt or restart lookup contract |
| DanConomy on this Forge line | excluded | no provider or fallback is shipped |

The Pixelmon candidate is the unmodified Forge 1.20.1 9.2.3 universal artifact from [Modrinth](https://cdn.modrinth.com/data/59ZceYlU/versions/KjmzoXMR/Pixelmon-1.20.1-9.2.3-universal.jar). Verify its SHA 512 as `3a9c6f375214c6d93c6cce8235e8a206e8f9731be8e168a254e78539087080796d97f0b22cb9a6db09d901c72e2e1ae53b9f2484761fb310479ce9f84ac9b145` and its SHA 256 as `8f2777f5a7cd2b5fc48ac3f5531d434af4120c7d40adc9929d5a3737c404246a`. Do not redistribute the provider jar or copy its balance store.

Mutation admission remains in the FutureShops coordinator. The native account image carries the immutable root, leg, operation, amount, payload fingerprint, debit direction, and resulting balance receipt. A missing, malformed, contradictory, or changed receipt is refused or frozen. A retry reuses the original root and leg and never creates a second request identity.

An addon may register a provider through the public API v1 registry. The addon owns its backend and must commit the balance effect and immutable receipt atomically before returning confirmed. FutureShops does not discover providers through reflection, service lookup, Bukkit, Vault, Spigot, or a bundled bridge. See [economy debugging](../economy-debugging.md) for the bounded support packet and recovery procedure.

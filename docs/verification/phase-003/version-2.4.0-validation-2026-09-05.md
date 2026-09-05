# FutureShops 2.4.0 version validation

This addendum records the version identity change from 2.3.0 to 2.4.0 for the existing Minecraft 1.21.1 and NeoForge 21.1.248 implementation. It does not add 3.0.0 code or publish the artifact.

## Source and artifact

| Field | Value |
| --- | --- |
| Source commit | `ec43c71e4a9c433b222189862b2dda8bdd115137` |
| Minecraft | `1.21.1` |
| NeoForge | `21.1.248` |
| Java | `21.0.11` |
| GeckoLib | `4.8.4` |
| Candidate | `build/libs/futureshops-2.4.0.jar` |
| Size | `1327757` bytes |
| SHA 256 | `f28de22b0ab56b8a38eb5ec538394b5a6df26c128253748a46680a5cd95563fd` |
| SHA 512 | `e2a90b4bed21150a244c255a9d3f3f3bb6cd40e1188aee4e1b3516b2fbf836fbf45479f5c3ec05fa13019fd2f6f901d3043cc847f645cc4a743715e4280af4c2` |

The jar manifest identifies `futureshops` version `2.4.0`. The Minecraft range is `[1.21.1,1.22)`, and the NeoForge range is `[21,)`. `unzip -tq` reports no archive errors. The archive contains only FutureShops classes and resources. Pixelmon, DanConomy, Vault, Bukkit, Spigot, bridge, SQLite, proof, and nested jar bytes are absent.

## Verification commands

The following checks passed after changing only the product version and the matching current documentation and fixture metadata.

```text
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 bash ./gradlew test --no-daemon
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 bash ./gradlew build --no-daemon
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 bash ./gradlew runGameTestServer -PverificationGameDirectory=<disposable directory> --no-daemon
unzip -tq build/libs/futureshops-2.4.0.jar
```

The complete unit suite passed. The dedicated headless NeoForge GameTest server loaded FutureShops as version `2.4.0` and all 32 required tests passed. The disposable runtime used `eula=true`, was isolated from the repository run directory, and was removed after verification.

## Implementation basis for future ports

The 2.4.0 behavior is the same validated implementation previously exercised on the exact 1.21.1 stack. The completed integration matrix covers the public economy provider API, restart only provider selection, strict capability gates, request aware write ahead journal, receipt audit under `world/data/futureshops/receipts`, item custody, claims, clean marker handling, `READY`, `DRAINING`, `RECOVERING`, and `FROZEN` lifecycle states, fail closed provider errors, and no internal fallback.

The Pixelmon path targets the exact native `PlayerPartyStorage` mutation boundary. The FutureShops mixin carries a stable request UUID, stores and looks up an immutable provider receipt, deduplicates identical retries, forces the Pixelmon save before confirmation, and refuses custom, hybrid, mirrored, or unsupported account paths. The exact Pixelmon 9.4.0 runtime and restart matrix passed previously and remains the compatibility reference for a future port.

The DanConomy path targets exact `LedgerData` for DanConomy 1.2.1 on NeoForge 1.21.1. The mixin stores the balance effect and immutable request receipt in the same `danconomy_ledger` saved data image, forces durable replacement before acknowledgement, deduplicates retries, rejects conflicting request reuse, preserves ordinary calls without a FutureShops request context, and refuses mirrored currencies. DanConomy remains a separately installed unmodified runtime.

The `vault` path remains a separately installed bridge contract. Mutation is enabled only when a bridge and backend accept a stable request UUID, persist the balance effect and receipt in one durable transaction, expose receipt lookup after restart, and deduplicate retries. The exact proof registrant and SQLite backend passed the transaction, interruption, lookup, retry, custody, claim, and compensation checks. The unmodified legacy PixelmonEconomyBridge and FinalEconomy stack correctly fails closed because it does not provide that contract.

All optional dependencies remain outside the FutureShops jar. FutureShops ships only its original adapters, mixins, provider API, journal, escrow, custody, claims, debug, and recovery code. Exact external jars may be inspected and run unchanged for interoperability verification, but are not copied, altered, rebuilt, bundled, or redistributed.

## Future 3.0.0 work

Issue 66 is the continuation basis for Forge 1.20.1 and the future NeoForge 1.21.1 port. Each line must revalidate its exact loader, Pixelmon, DanConomy, Vault, bridge, and economy backend artifacts. It must reapply the request receipt and durable save contracts at that line's native mutation boundary, rerun every monetary surface, restart and crash recovery, claims, backups, debug evidence, and jar isolation checks, and keep unsupported or unproven paths refused. The 2.4.0 artifact and this addendum are implementation evidence only, not a claim that either 3.0.0 line is implemented.

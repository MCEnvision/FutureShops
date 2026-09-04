# FutureShops for NeoForge 1.21.1

FutureShops is a server authoritative economy and marketplace mod with server shops, player owned shop blocks, physical currency, barter trades, transaction history, departments, franchises, and administrative catalog tools. This branch ports the established FutureShops runtime namespace to Minecraft 1.21.1 and NeoForge while preserving existing registry identifiers and saved data compatibility.

## Status

This branch targets the following runtime.

| Component | Version |
| --- | --- |
| FutureShops | 2.3.0 |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.248 |
| Java | 21 |
| Parchment | 2024.11.17 |
| GeckoLib | 4.8.4 |

Client and server should use the same FutureShops build. This `2.3.0` NeoForge candidate is prepared locally and remains unpublished. Phases 000 and 001 are integrated, and the current branch is phase 002. The provider API, deterministic registry, restart only selection contract, checksummed transaction journal, durable receipt audit directory, durable custody and claims, lifecycle gate, clean marker handling, and the exact Pixelmon 9.4.0 query and precheck adapter are present. Pixelmon direct mutations remain refused because its reviewed API has no durable receipts or idempotent retry. Back up the complete world and configuration before replacing an older installation. The runtime mod identifier and all resource namespaces remain `futureshops`, while the Java package for this port is `com.enviouse.futureshopsp`.

## Installation

1. Install Minecraft 1.21.1 and NeoForge 21.1.248.
2. Install GeckoLib 4.8.4 for NeoForge 1.21.1.
3. Place the same FutureShops JAR in the client and server `mods` directories.
4. Back up the world, player data, and the complete `config/futureshops` directory before upgrading.
5. Start the game or server once, then review the generated FutureShops configuration and shop catalogs.

The generated `economy.provider` setting defaults to `internal`. Set it to `pixelmon` only with Pixelmon 9.4.0 installed. Pixelmon balance queries and prechecks are authoritative, but all mutation surfaces remain unavailable until a provider proves durable receipts and idempotent retry. Provider changes are restart only and do not migrate balances. Transaction transitions are also recorded in the local audit directory at `world/data/futureshops/receipts`; this is FutureShops evidence and does not make an external operation safe to replay. See [Pixelmon economy integration](docs/integrations/pixelmon-economy.md) and the [backup and restore runbook](docs/operations/backup-restore.md).

## Main features

* Server shops with buy, sell, barter, cart, category, and item detail workflows.
* Player shop blocks with stock, pricing, barter, promotion, settlement, and local browsing tools.
* Minted physical currency protected by a persistent redemption ledger.
* Internal balances, payments, balance leaderboards, franchises, and transaction history.
* Exact item variant matching and lazy migration of legacy listing data.
* Responsive custom screens that preserve FutureShops layout and backdrop behavior on NeoForge 1.21.1.

Common entry points include `/shop`, `/playershops`, `/balance`, `/baltop`, `/pay`, and the administrative shop commands provided by the installed build. Command availability and permissions remain server authoritative.

## Development

Use the checked in Gradle Wrapper and Java 21.

Linux and macOS.

```text
./gradlew test
./gradlew build
```

Windows.

```text
gradlew.bat test
gradlew.bat build
```

Development launch tasks include `runClient`, `runServer`, `runGameTestServer`, and `runData`. Build artifacts are written to `build/libs`.

The implementation and compatibility decisions are documented in [Porting notes](PORTING_NOTES.md). The original migration inventory and risk analysis are in [Port audit](FutureShopsAudit.md).

The public provider contract is documented in [Economy provider API](docs/api/economy-provider.md).

The maintained documentation index is in [Documentation](docs/README.md).

## Known boundaries

The Refined Storage 2 integration remains guarded until its supported API artifact is available for verification. Live owner skin fetching also remains deferred and uses the safe default skin fallback. Review the deferred section in the porting notes before enabling optional integrations or approving a production migration.

## Support

Bug reports should include the exact FutureShops JAR version, Minecraft and NeoForge versions, Java version, operating system, reproduction steps, relevant sanitized logs, and screenshots when the problem is visual. Never publish tokens, private server addresses, personal paths, or unrelated player information.

## License

All rights reserved.

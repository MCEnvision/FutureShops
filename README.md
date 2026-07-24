# FutureShops

FutureShops is a Minecraft Forge economy and marketplace mod for server shops, player owned shop blocks, physical currency, barter trades, an Auction House, and a Bazaar order book. Version 3.0 routes value movement through durable escrow so interrupted trades recover or become claims instead of losing money or items.

## Status

The 3.0 markets implementation is in beta pending owner approval. Active phase branches are test builds. Use matching FutureShops builds on the client and server.

Supported runtime:

| Component | Version |
| --- | --- |
| Minecraft | 1.20.1 |
| Forge | 47.4.20 |
| Java | 17 |
| GeckoLib | 4.8.3 or newer compatible 4.x release |
| Network protocol | 51 |

## Features

* Infinite stock server shops with categories, search, carts, sales, barter, and transaction history.
* Player shop blocks with linked storage, money and barter listings, promotions, stock alerts, and owner settlement tools.
* Wallet balances, payments, leaderboards, deposits, withdrawals, ATM controls, and protected FutureShops currency.
* Auction House listings, bids, buy now sales, watchlists, history, anti sniping rules, and exact item custody.
* Bazaar products, instant orders, limit orders, partial fills, price time priority, fees, price history, and circuit breakers.
* Durable claims for undeliverable money and items.
* Responsive market screens with accessibility, density, scale, and theme settings.

FutureShops currently assumes one authoritative server and world. It does not provide a shared order book across multiple live servers. Direct listing from external storage is outside the current market contract unless an integration can provide deterministic transaction receipts.

## Installation

1. Install Minecraft Forge 47.4.20 for Minecraft 1.20.1.
2. Install a compatible GeckoLib 4.x build.
3. Place the same FutureShops jar in the client and server `mods/` directories.
4. Back up the complete world and configuration before upgrading an existing server.
5. Start the server once to create the TOML files and product directories.
6. Review module, economy, escrow, Auction House, and Bazaar settings before opening the server.

For 3.0 upgrades, follow [Backup and restore](docs/backup-restore.md). Do not delete escrow files to resolve a recovery failure.

## Usage

Common commands include:

| Command | Purpose |
| --- | --- |
| `/shop` | Open the server shop |
| `/playershops` | Browse player shops |
| `/ah` | Open the Auction House |
| `/bz` | Open the Bazaar |
| `/claims` | Open durable market claims |
| `/balance` | Open the player marketplace profile |
| `/pay` | Transfer wallet funds |
| `/baltop` | Open economy leaderboards |
| `/atm` | Open physical currency controls |
| `/marketadmin` | Inspect and control market runtime state |

See [Auction House and Bazaar guide](docs/markets-guide.md) for player and administrator workflows.

## Configuration

FutureShops creates these files:

* `config/futureshops-common.toml` for module toggles, economy, currency, permissions, and shop behavior.
* `config/futureshops-escrow.toml` for recovery, claims, checkpoints, request protection, and asset limits.
* `config/futureshops-auction-house.toml` for listing, bid, fee, duration, and lifecycle rules.
* `config/futureshops-bazaar.toml` for catalog control, order matching, fees, limits, and lifecycle rules.
* `config/futureshops-client.toml` for presentation and accessibility.

Server shop catalogs live in `config/futureshops/shops/`. Bazaar products live in `config/futureshops/bazaar/products/`.

See [Configuration examples](docs/config-3.0-examples.md) and [Bazaar product definitions](docs/bazaar-products.md).

## Development

Clone the repository, install Java 17, and use the checked in Gradle Wrapper.

Linux and macOS:

```text
bash ./gradlew test
bash ./gradlew build
```

Windows:

```text
gradlew.bat test
gradlew.bat build
```

The reobfuscated jar is written to `build/libs/`. Development launches use `run/`.

Useful run tasks are `runClient`, `runServer`, `runGameTestServer`, and `runData`. Detailed architecture, verification, persistence, and recovery information is in [DOCUMENTATION.md](DOCUMENTATION.md).

## Compatibility and support

Read [Compatibility matrix](docs/compatibility-matrix.md) before using custom currency, permission plugins, external storage, or restored world data.

When reporting a problem, include the FutureShops jar version, Minecraft and Forge versions, client and server logs, relevant sanitized TOML files, the command or screen involved, and whether the world was new or upgraded. For market availability failures, include the output of `/marketadmin status`.

## License

All rights reserved.

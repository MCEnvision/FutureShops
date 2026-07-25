# FutureShops

FutureShops is a Minecraft Forge economy and marketplace mod for server shops, player owned shop blocks, physical currency, barter trades, an Auction House, and a Bazaar order book. Version 3.1 adds one server authoritative trade offer model for free, money, barter, compound, alternative, Sell to Shop, and bundle exchanges. Value movement remains protected by the durable 3.0 escrow foundation.

## Status

The 3.1 trade offer implementation is in beta on the active phase branch. Active phase branches remain test builds until the complete automated, client, dedicated server, multiplayer, reconnect, restart, migration, and recovery acceptance run is complete. Use matching FutureShops builds on the client and server.

Supported runtime:

| Component | Version |
| --- | --- |
| FutureShops | 3.1.0 beta 1 |
| Minecraft | 1.20.1 |
| Forge | 47.4.20 |
| Java | 17 |
| GeckoLib | 4.8.3 or newer compatible 4.x release |
| Network protocol | 55 |

## Features

* Server shops with explicit free offers, money, barter, money plus barter, alternative payments, Sell to Shop, multi item bundles, validated savings, categories, search, mixed carts, and transaction history.
* Player shop blocks with versioned offers, legacy listing migration, physical storage, free and paid acquisition, multiple barter components, alternative options, Sell to Shop input bundles, promotions, stock alerts, and owner settlement tools.
* A guided four step offer editor opened after item selection or through Edit, then New Offer, for money, free, barter, Sell Only, Buy and Sell, and bundles, with advanced settings available when needed.
* New generated Server Shop catalogs include working free, Sell Only, and discounted bundle examples without replacing an existing `admin.json`.
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

The Bazaar and Auction House are disabled on a new installation. Enable either module in `futureshops-common.toml` after reviewing its rules. Existing configuration files keep their configured values. Disabled modules are omitted from the marketplace header. An accepted server configuration change appears on an open marketplace screen within five seconds without requiring a reconnect.

For 3.0 or 3.1 upgrades, follow [Backup and restore](docs/backup-restore.md). Do not delete escrow files to resolve a recovery failure.

Marketplace screens opened while escrow is recovering refresh automatically when the runtime becomes ready. Reconnecting or reopening the screen should not be necessary.

Interrupted normalized Server Shop offers recover from exact persisted evidence when the player logs in and through bounded background retries while the escrow runtime is ready. A retry never reconstructs a trade from current client state.

Pending escrow money and exact item claims are delivered automatically while the beneficiary is online. A full inventory or temporary delivery failure leaves the durable claim available instead of discarding value.

Opening the ATM and starting a new deposit both attempt bounded automatic reconciliation of safe pending deposit evidence. This behavior is server authoritative and identical for singleplayer and connected dedicated server players. Conflicting or corrupt evidence remains protected for administrator inspection.

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
| `/marketadmin inspect <transactionId>` | Inspect one escrow recovery handle without changing it |

See [Auction House and Bazaar guide](docs/markets-guide.md) for player and administrator workflows.

## Configuration

FutureShops creates these files:

* `config/futureshops-common.toml` for module toggles, economy, currency, permissions, and shop behavior.
* `config/futureshops-escrow.toml` for recovery, claims, checkpoints, request protection, and asset limits.
* `config/futureshops-auction-house.toml` for listing, bid, fee, duration, and lifecycle rules.
* `config/futureshops-bazaar.toml` for catalog control, order matching, fees, limits, and lifecycle rules.
* `config/futureshops-client.toml` for presentation and accessibility.

Server shop catalogs live in `config/futureshops/shops/`. Bazaar products live in `config/futureshops/bazaar/products/`.

See [3.1 trade offer configuration](docs/config-3.1-offers.md), [Configuration examples](docs/config-3.0-examples.md), and [Bazaar product definitions](docs/bazaar-products.md).

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

Read [Compatibility matrix](docs/compatibility-matrix.md) before using custom currency, permission plugins, external storage, advanced trade offers, or restored world data. The player and administrator changes for this beta are summarized in [3.1 release notes](docs/release-notes-3.1.md).

When reporting a problem, include the FutureShops jar version, Minecraft and Forge versions, client and server logs, relevant sanitized TOML files, the command or screen involved, and whether the world was new or upgraded. For market availability failures, include the output of `/marketadmin status`. For a deposit recovery, copy the complete ATM recovery handle and include `/marketadmin inspect <transactionId>`.

## License

All rights reserved.

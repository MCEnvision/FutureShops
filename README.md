# FutureShops

FutureShops is a Minecraft Forge economy and marketplace mod for server shops, player owned shop blocks, physical currency, barter trades, an Auction House, and a Bazaar order book. The current 3.0.0 beta includes one server authoritative trade offer model for free, money, barter, compound, alternative, Sell to Shop, and bundle exchanges. Value movement remains protected by the durable 3.0 escrow foundation.

## Status

The 3.0.0 implementation is in beta on the active phase branch. Active phase branches remain test builds until the complete automated, client, dedicated server, multiplayer, reconnect, restart, migration, and recovery acceptance run is complete. Use matching FutureShops builds on the client and server.

Supported runtime:

| Component | Version |
| --- | --- |
| FutureShops | 3.0.0 beta 6 |
| Minecraft | 1.20.1 |
| Forge | 47.4.20 |
| Java | 17 |
| GeckoLib | 4.8.3 or newer compatible 4.x release |
| Network protocol | 57 |

## Features

* Server shops with explicit free offers, money, barter, money plus barter, alternative payments, Sell to Shop, multi item bundles, validated savings, categories, search, mixed carts, and transaction history.
* Player shop blocks with versioned offers, legacy listing migration, physical storage, free and paid acquisition, multiple barter components, alternative options, Sell to Shop input bundles, promotions, stock alerts, and owner settlement tools.
* Server authoritative bulk inventory selling to the active Server Shop or nearby Player Shops, with exact quotes, item deselection, total payout confirmation, tagged and damaged item support for general offers, and a fast confirmed command path.
* A contextual Server Shop quick add grid for Buy, Sell, Barter, and Bundles, with one Base Price field, direct Buy and Sell creation, and progressively disclosed Simple and Advanced editors. Every simple trade mode remains selectable for every item, and rejected drafts recover as soon as the administrator edits them.
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
5. Start the game or dedicated server once to create the TOML files and editable catalog directories.
6. Review module, economy, escrow, Auction House, and Bazaar settings before opening the server.

The Bazaar and Auction House are disabled on a new installation. Enable either module in `config/futureshops/futureshops-common.toml` after reviewing its rules. Existing configuration files keep their configured values. Disabled modules are omitted from the marketplace header. An enabled module remains visible while escrow or its lifecycle control is recovering and shows a recovery state instead of claiming that the module is disabled. An accepted server configuration change appears on an open marketplace screen within five seconds without requiring a reconnect.

For 3.0 beta upgrades, follow [Backup and restore](docs/backup-restore.md). Do not delete escrow files to resolve a recovery failure.

The issue 23 development artifact is `futureshops-3.0.0-beta.9.jar`. It includes the Server Shop sell
payout and timed out cart recovery fixes from beta 4. It also completes the dependency alert audit,
uses fixed MixinGradle and Foojay resolver releases, aligns the JUnit 6 test runtime, and rejects any
build that bundles launcher supplied libraries inside the FutureShops JAR. Gradle remains pinned to
8.14.4 because ForgeGradle does not support Gradle 9. Beta 7 also makes escrow lifecycle checks and
their dependent value or replay operation explicitly atomic on the owning logical server thread.
Beta 9 bounds exact item durability work. It remains a development artifact until the approved beta
8 recovery repair is integrated and the combined branch is rebuilt and retested.

Worlds previously opened with the incorrectly labeled `3.1.0-beta.1` build can report a Forge
version difference when first opened with `3.0.0-beta.3`. This corrects the public artifact label
and does not roll back the FutureShops data schema. Keep the normal complete backup before opening
the world.

Marketplace screens opened while escrow is recovering refresh automatically when the runtime becomes ready. Reconnecting or reopening the screen should not be necessary.

Interrupted normalized Server Shop offers recover from exact persisted evidence when the player logs in and through bounded background retries while the escrow runtime is ready. A retry never reconstructs a trade from current client state.

Pending escrow money and exact item claims are delivered automatically while the beneficiary is online. A full inventory or temporary delivery failure leaves the durable claim available instead of discarding value.

Exact item claims are delivered incrementally. At most one exact item delivery operation runs in a server tick because each successful operation saves and forces player data before the claim is committed. Large purchases can therefore continue appearing over several delivery intervals instead of causing one burst of durable saves. The general claim scan budget does not bypass this safety limit.

Opening the ATM and starting a new deposit both attempt bounded automatic reconciliation of safe pending deposit evidence. This behavior is server authoritative and identical for singleplayer and connected dedicated server players. Conflicting or corrupt evidence remains protected for administrator inspection.

## Usage

Common commands include:

| Command | Purpose |
| --- | --- |
| `/shop` | Open the server shop |
| `/playershops` | Browse player shops |
| `/sellall adminshop` | Review eligible inventory sales to the Server Shop |
| `/sellall adminshop confirm` | Sell every eligible inventory item to the Server Shop immediately |
| `/sellall playershops` | Review eligible inventory sales to nearby Player Shops |
| `/sellall playershops confirm` | Sell every eligible inventory item to nearby Player Shops immediately |
| `/ah` | Open the Auction House |
| `/bz` | Open the Bazaar |
| `/claims` | Open durable market claims |
| `/balance` | Open the player marketplace profile |
| `/pay` | Transfer wallet funds |
| `/baltop` | Open economy leaderboards |
| `/atm` | Open physical currency controls |
| `/marketadmin` | Inspect and control market runtime state |
| `/marketadmin inspect <transactionId>` | Inspect one escrow recovery handle without changing it |

See [Bulk inventory selling](docs/bulk-selling.md) and the [Auction House and Bazaar guide](docs/markets-guide.md) for player and administrator workflows.

## Configuration

FutureShops creates these files:

* `config/futureshops/futureshops-common.toml` for module toggles, economy, currency, permissions, and shop behavior.
* `config/futureshops/futureshops-escrow.toml` for recovery, claims, checkpoints, request protection, and asset limits.
* `config/futureshops/futureshops-auction-house.toml` for listing, bid, fee, duration, and lifecycle rules.
* `config/futureshops/futureshops-bazaar.toml` for catalog control, order matching, fees, limits, and lifecycle rules.
* `config/futureshops/futureshops-client.toml` for presentation and accessibility.

Server shop catalogs live in `config/futureshops/shops/`. Bazaar products live in `config/futureshops/bazaar/products/`.

FutureShops creates `config/futureshops/shops/admin.json` during common mod setup, before a client opens a singleplayer world. Modpack developers can start the client once, edit that file, and ship the complete `config/futureshops/` directory as the pack's singleplayer Server Shop template. An integrated server reads this global client configuration. A remote multiplayer server ignores the client's copy and uses its own authoritative `config/futureshops/shops/` directory.

Recognized loose FutureShops TOML files and their Forge backups are moved into
`config/futureshops/` before configuration loading. Existing nested files remain authoritative, and
conflicting loose copies are preserved under `config/futureshops/migration-backups/`. The migration
does not rewrite values or move unrelated Forge and mod configuration.

See [Advanced trade offer configuration](docs/config-3.1-offers.md), [Configuration examples](docs/config-3.0-examples.md), and [Bazaar product definitions](docs/bazaar-products.md).

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

Read [Compatibility matrix](docs/compatibility-matrix.md) before using custom currency, permission plugins, external storage, advanced trade offers, or restored world data. The player and administrator changes for this beta are summarized in [3.0.0 beta release notes](docs/release-notes-3.0-beta.md).

When reporting a problem, include the FutureShops jar version, Minecraft and Forge versions, client and server logs, relevant sanitized TOML files, the command or screen involved, and whether the world was new or upgraded. For market availability failures, include the output of `/marketadmin status`. For a deposit recovery, copy the complete ATM recovery handle and include `/marketadmin inspect <transactionId>`.

## License

All rights reserved.

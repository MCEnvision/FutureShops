# FutureShops technical documentation

## Scope and sources

FutureShops is a Forge 1.20.1 mod that owns a server authoritative economy, shop catalogs, player shop blocks, physical currency, an escrow protected Auction House, and a Bazaar order book.

The 3.0 implementation is in beta. Approval is blocked by the ATM deposit recovery and refund access defects in [Section 20 of the FutureShops 3.0 plan](FutureShops3-0Plan.MD#20-release-blocker-atm-deposit-recovery-and-refund-access). That plan remains the source of truth for unfinished 3.0 requirements. The [FutureShops 3.1 advanced trade offers plan](FutureShops3-1TradeOffersPlan.MD) is the source of truth for the planned expansion. Current code and tests establish implemented behavior. Focused operator documentation is available in:

* [Auction House and Bazaar guide](docs/markets-guide.md)
* [Configuration examples](docs/config-3.0-examples.md)
* [Bazaar product definitions](docs/bazaar-products.md)
* [Backup and restore](docs/backup-restore.md)
* [Compatibility matrix](docs/compatibility-matrix.md)
* [Physical currency and ATM](docs/physical-currency-atm.md)

## Runtime and toolchain

| Component | Pinned value |
| --- | --- |
| Java | 17 |
| Gradle Wrapper | 8.14.4 |
| Minecraft | 1.20.1 |
| Forge | 47.4.20 |
| Mappings | Official 1.20.1 |
| ForgeGradle | 6.0 line |
| MixinGradle | 0.7 snapshot |
| Mixin processor | 0.8.5 |
| GeckoLib Forge | 4.8.3 |
| mclib | 20 |
| JUnit Jupiter | 5.10.2 |
| JUnit Platform | 1.10.2 |
| Network protocol | 51 |

The repository uses one Gradle module named `futureshops`. Java sources use UTF 8. Runtime and data generation launches are defined in `build.gradle`.

## Setup and commands

Install Java 17 and keep `libs/geckolib-forge-1.20.1-4.8.3.jar` available. Use the checked in wrapper. Do not replace dependency or wrapper versions to work around a local setup problem.

Linux and macOS:

```text
bash ./gradlew test
bash ./gradlew build
bash ./gradlew runServer
bash ./gradlew runClient
bash ./gradlew runGameTestServer
bash ./gradlew runData
```

Windows:

```text
gradlew.bat test
gradlew.bat build
gradlew.bat runServer
gradlew.bat runClient
gradlew.bat runGameTestServer
gradlew.bat runData
```

`runClient`, `runServer`, and `runGameTestServer` use `run/`. `runData` uses `run-data/` and writes generated resources into `src/generated/resources/`.

The build has no formatter or static analysis task. `test` runs JUnit source contracts and pure unit tests. `build` compiles, tests, packages, and reobfuscates the mod jar under `build/libs/`.

## Package map

The root package is `com.enviouse.futureshops`.

| Package | Responsibility |
| --- | --- |
| `api` | Public extension points and shared contracts |
| `init` | Forge registration and mod initialization |
| `config` | Validated common, escrow, market, and client settings |
| `catalog` | Server shop catalog loading and item definitions |
| `block` and `item` | Shop block entities, interactions, and registered items |
| `money` | Wallet provider boundary, physical currency, deposits, and withdrawals |
| `server/economy` | Economy coordination and administrative balance operations |
| `server/escrow` | Journal, checkpoints, ledger, custody, claims, replay protection, recovery, and migrations |
| `server/market` | Market capabilities, module lifecycle, Auction House, Bazaar, query, and action services |
| `server/shop` | Server shop and player shop services, analytics, stock, settlement, and persistence |
| `server/session` | Server owned navigation and route validation |
| `network` and `network/packets` | Protocol registration, packet validation, and client or server dispatch |
| `client/market` | Client navigation coordinator, capability snapshots, layout models, and response tracking |
| `client/screen` | Shop, market, profile, history, ATM, and administration screens |
| `compat` | Optional mod integrations, including Refined Storage |
| `mixin` | Narrow hooks not supplied by Forge events |

Client classes must never be loaded by common or dedicated server initialization. The logical server is authoritative for every balance, item, listing, order, permission, route, and lifecycle decision.

## Initialization and runtime readiness

Forge registers blocks, items, menus, packets, commands, configurations, and event handlers during normal mod startup. Server startup opens the escrow runtime for the active world, loads checkpoints and persistent stores, replays the journal, runs domain recovery, loads market control state, migrates legacy wallet state when needed, and then marks escrow ready.

During recovery, value mutations fail closed. Claims remain the safety route. Screens may render read only information, but no client snapshot can authorize a mutation.

Market capability requests project current server configuration, runtime readiness, module control status, claim counts, branding, currency metadata, and a display balance. The client uses the snapshot to present availability. During recovery, screens retry capability requests. A correlated response with a newer server revision is accepted even if another retry is already outstanding, which prevents a slow response from leaving the client stuck on the recovery snapshot. Equal revision conflicts and older revisions still fail closed. Navigation remains server authoritative because a capability response can become stale immediately after it is sent. The server resolves an attempted route to the requested view, a safe fallback, or claims.

If escrow remains in recovery or maintenance, run `/marketadmin status` and inspect `run/logs/latest.log` or the dedicated server log. Do not delete journal, checkpoint, ledger, claim, or custody files.

## Market navigation and data flow

A client market route contains a module, view, route nonce, search state, category, sort order, page, scroll offset, and optional selection. The navigation coordinator tracks history and response families. Opening a module or local tab sends a request to the server. The server validates the current session, permission, configured module toggle, lifecycle state, escrow readiness, ownership requirements, and route availability before returning an open screen packet.

Page queries and mutations carry request UUIDs. Responses are accepted only for the active route and expected response family. Economic retries reuse the original request UUID so the server can replay a stored result instead of applying a second transaction.

The Shop, Bazaar, and Auction House share a responsive shell. Shop data supplies a safe display balance even while escrow migration or recovery is completing. Market capability and profile paths must use the same display balance boundary. Live economy provider calls are reserved for ready, authoritative operations.

## Escrow, persistence, and recovery

Escrow owns all durable value movement. Its main invariants are:

1. A request UUID identifies one economic intent.
2. Journal state is forced before externally visible commit.
3. Money uses a double entry ledger.
4. Items use exact serialized custody with configured limits.
5. Delivery failure creates a durable claim.
6. Recovery can resume or compensate interrupted operations without charging twice.
7. Persistent state from a newer or unverifiable lineage fails closed.

Persistent data spans normal world saved data and the FutureShops escrow directory. Checkpoints, journal segments, ledger state, custody, replay records, claims, market contracts, player data, and configuration must be backed up as one consistent generation.

For recovery:

1. Stop the server.
2. Preserve the current complete world.
3. Restore one complete matching world and configuration snapshot.
4. Start with a compatible FutureShops build.
5. Watch checkpoint, replay, migration, and recovery messages.
6. Verify balances, claims, listings, and orders before reopening trading.

See [Backup and restore](docs/backup-restore.md) for the full procedure.

## Economy and currency

Authoritative money values are `long` minor units. The configured decimal count defines display formatting. Totals use checked arithmetic and never use floating point for storage or settlement.

`BalanceManager` selects the configured economy provider and exposes display and mutation boundaries. A display read may fall back to safe stored or default state while escrow is not ready. A value mutation must use the ready escrow wallet service.

The built in `futureshops:money` item uses mint identifiers, checksums, reservations, and spent mint tracking. Custom or third party currency remains protected by FutureShops request idempotency and escrow, but FutureShops cannot prove the origin or scarcity of items created by another mod.

## Auction House

Auction listings move the exact item from player inventory into custody before becoming visible. The service validates ownership, listing type, duration, price, buyout, item restrictions, inventory slot, permission, module state, and request identity.

Bids reserve money immediately. Outbid money becomes a claim when direct delivery is unavailable. Buy now settlement transfers the item and proceeds through escrow. Expiry returns unsold custody or settles the accepted winner. Anti sniping, fees, taxes, limits, and duration rules are snapshotted into contracts where required.

## Bazaar

The Bazaar supports an administrator JSON catalog or player selected registered commodities. Admin definitions live in `config/futureshops/bazaar/products/` and are validated as one atomic catalog.

Buy orders reserve money. Sell orders move matching inventory into custody. Matching uses price time priority, partial fills, checked fee calculations, configured price and quantity bounds, and circuit breaker state. Product identity and versions are durable. Removing a used product retires it instead of deleting history or custody.

## Shops and player shops

The server shop catalog is loaded from `config/futureshops/shops/`. Purchases validate the live catalog, price, quantity, promotion, payment source, inventory delivery, and request identity.

Player shop blocks persist owner, name, listings, trade modes, storage link, and promotional settings. Server services validate block existence, dimension, ownership, stock, linked storage, price or barter inputs, settlement, and permissions. Optional Refined Storage access remains behind its compatibility boundary.

## Configuration

| File | Responsibility |
| --- | --- |
| `futureshops-common.toml` | Modules, navigation, economy, currency, permissions, and shop behavior |
| `futureshops-escrow.toml` | Recovery, checkpoints, claims, request limits, and asset bounds |
| `futureshops-auction-house.toml` | Listing, bidding, settlement, fee, duration, and lifecycle rules |
| `futureshops-bazaar.toml` | Catalog control, matching, order, fee, limit, and lifecycle rules |
| `futureshops-client.toml` | Layout, scale, density, accessibility, theme, and presentation |

Module enablement lives only in `futureshops-common.toml`. Escrow has no disable switch. Some validated settings reload immediately, contract rules apply only to new contracts, and identity or persistence settings require restart or migration. An invalid reload preserves the last valid snapshot and logs the rejected field.

Exact defaults, examples, validation behavior, and reload boundaries are documented in [Configuration examples](docs/config-3.0-examples.md).

## Commands and permissions

Player entry points include `/shop`, `/playershops`, `/ah`, `/bz`, `/claims`, `/balance`, `/pay`, `/baltop`, `/atm`, `/deposit`, and `/withdraw`.

Administrative entry points include `/shopadmin` and `/marketadmin`. `/marketadmin status` reports module control state, escrow runtime state, open contract counts, and pending recovery. Mutating administrative operations require the configured operator level or Forge permission node, a bounded reason where configured, confirmation where configured, and an audit record.

Auction House permission nodes use the `futureshops.auction` prefix. Bazaar nodes use `futureshops.bazaar`. Escrow nodes use `futureshops.escrow`. Claims remain available when ordinary trading permission is revoked.

## Networking and security

The packet channel is a strict client and server compatibility boundary. Every server bound packet must validate:

* Logical direction and active server player.
* Permission and operator fallback.
* Session route and route nonce where applicable.
* Request UUID and replay state.
* Module configuration, lifecycle, and escrow readiness.
* Ownership, entity, level, block position, item identity, NBT, registry identifier, string length, page size, quantity, price, and arithmetic bounds.
* Thread handoff before world mutation.

Never trust a client capability snapshot, displayed balance, selected item, price, or enabled control. Rate limits and bounded serialization protect server memory and disk.

Do not log credentials, tokens, private configuration, full player inventories, or unbounded NBT. Do not follow symbolic links when loading administrator product files.

## Verification by change type

For all source changes:

```text
bash ./gradlew test
bash ./gradlew build
```

Also run:

* `runData` for generated resources or providers.
* `runGameTestServer` for world dependent behavior covered by GameTests.
* `runServer` for common initialization, networking, config, persistence, escrow, economy, shops, or markets.
* `runClient` for screens, assets, input, rendering, client events, or synchronization.
* Multiplayer and reconnect checks for state that crosses the network.

For readiness changes, verify both the recovery window and the ready transition. A screen opened during recovery must refresh without reconnecting. Navigation requests must remain server authorized. Currency and profile reads may use the safe display balance, while mutations remain blocked until ready.

After packaging, inspect the manifest, expanded `META-INF/mods.toml`, mixin configuration and refmap, assets, data, dependency metadata, and the complete Git diff. Build output, run directories, logs, crash reports, local configs, caches, IDE files, and `AGENTS.md` must not be committed.

## Troubleshooting

### Module disabled although TOML enables it

Run `/marketadmin status`. Confirm the configured toggle, market control status, and escrow runtime state separately. A configured module can still be claims only, frozen, draining, recovering, or in maintenance.

Check the server log for checkpoint, journal, migration, catalog, or market control failures. If recovery becomes ready shortly after the screen opens, the client must refresh capabilities automatically. Reopening the screen should not be the required recovery mechanism.

### Marketplace profile does not open

Check the server log for an exception while reading the balance dashboard. Profile presentation must use the safe display balance path and must not require a live mutation provider during startup recovery.

### Client launch fails before a window appears

Confirm a graphical session and working OpenGL environment. A headless environment can verify compilation and dedicated server startup but cannot complete a client rendering smoke test.

### Bazaar catalog reload fails

Read the first validation error in the server log. The loader rejects the whole new catalog and keeps the last valid snapshot. Check schema version, duplicate fields, product version conflicts, item identifiers, NBT, limits, UTF 8, and symbolic links.

### Escrow enters maintenance

Preserve the world and logs. Read the first causal recovery error. Do not delete state files. Restore one complete consistent backup if lineage or schema validation cannot be resolved in place.

## Release procedure

1. Confirm the target phase and version.
2. Update user documentation, technical documentation, compatibility notes, and changelog.
3. Run focused tests, `test`, required data or GameTests, `build`, dedicated server smoke, client smoke, and multiplayer checks.
4. Inspect the reobfuscated jar and calculate its checksum.
5. Inspect the complete diff and Git status.
6. Commit and push the phase branch with EnVy as sole author and committer.
7. Publish only with explicit authorization.
8. After explicit approval, fast forward `main` and create the lightweight phase tag.

Do not publish, tag, or approve a beta solely because it builds successfully.

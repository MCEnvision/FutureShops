# FutureShops technical documentation

## Scope and sources

FutureShops is a Forge 1.20.1 mod that owns a server authoritative economy, shop catalogs, player shop blocks, physical currency, an escrow protected Auction House, and a Bazaar order book.

The 3.1 trade offer implementation is in beta on the active phase branch. It extends the durable 3.0 market foundation with one normalized offer contract for Server Shops and Player Shops. The [FutureShops 3.1 advanced trade offers plan](FutureShops3-1TradeOffersPlan.MD) remains the acceptance source of truth. Current code and tests establish implemented behavior. Release approval still requires the complete automated, client, dedicated server, multiplayer, reconnect, restart, migration, and recovery acceptance run. Focused operator documentation is available in:

* [Auction House and Bazaar guide](docs/markets-guide.md)
* [Configuration examples](docs/config-3.0-examples.md)
* [Bazaar product definitions](docs/bazaar-products.md)
* [Backup and restore](docs/backup-restore.md)
* [Compatibility matrix](docs/compatibility-matrix.md)
* [Physical currency and ATM](docs/physical-currency-atm.md)
* [3.1 trade offer configuration](docs/config-3.1-offers.md)
* [3.1 release notes](docs/release-notes-3.1.md)

## Runtime and toolchain

| Component | Pinned value |
| --- | --- |
| FutureShops | 3.1.0 beta 1 |
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
| Network protocol | 57 |

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
| `catalog` and `catalog/offer` | Server shop loading, immutable normalized offers, validation, migration, pricing comparisons, and atomic administrator writes |
| `block` and `item` | Shop block entities, interactions, and registered items |
| `money` | Wallet provider boundary, physical currency, deposits, and withdrawals |
| `server/economy` | Economy coordination and administrative balance operations |
| `server/escrow` | Journal, checkpoints, ledger, custody, claims, replay protection, recovery, and migrations |
| `server/market` | Market capabilities, module lifecycle, Auction House, Bazaar, query, and action services |
| `server/shop` | Server shop and player shop services, analytics, stock, settlement, and persistence |
| `server/session` | Server owned navigation and route validation |
| `network` and `network/packets` | Protocol registration, packet validation, and client or server dispatch |
| `client/market` | Client navigation coordinator, capability snapshots, layout models, and response tracking |
| `client/editor` and `client/screen` | Persistent offer drafts, validation, shop, offer chooser, visitor preview, market, profile, history, ATM, and administration screens |
| `compat` | Optional mod integrations, including Refined Storage |
| `mixin` | Narrow hooks not supplied by Forge events |

Client classes must never be loaded by common or dedicated server initialization. The logical server is authoritative for every balance, item, listing, order, permission, route, and lifecycle decision.

## Initialization and runtime readiness

Forge registers blocks, items, menus, packets, commands, configurations, and event handlers during normal mod startup. Server startup opens the escrow runtime for the active world, loads checkpoints and persistent stores, replays the journal, runs domain recovery, loads market control state, migrates legacy wallet state when needed, and then marks escrow ready.

During recovery, value mutations fail closed. Claims remain the safety route. Screens may render read only information, but no client snapshot can authorize a mutation.

Market capability requests project current server configuration, runtime readiness, module control status, claim counts, branding, currency metadata, and a display balance. The client uses the snapshot to present availability. During recovery, screens retry capability requests every second. Ready screens refresh capabilities every five seconds so an accepted module configuration change appears without reconnecting. A module disabled in `config/futureshops/futureshops-common.toml` is omitted from the marketplace header. Claims only and unavailable module states are also omitted, while their durable claims remain reachable through the shared claims route. A correlated response with a newer server revision is accepted even if another retry is already outstanding, which prevents a slow response from leaving the client stuck on the recovery snapshot. Equal revision conflicts and older revisions still fail closed. Navigation remains server authoritative because a capability response can become stale immediately after it is sent. The server resolves an attempted route to the requested view, a safe fallback, or claims.

Protocol 56 includes the ATM recovery contract, normalized Server Shop catalogs, Player Shop offer snapshots, typed offer execution, correlated owner and administrator saves, and the bulk sell quote and commit contract. Before sending authoritative ATM data, the server attempts bounded reconciliation of safe persisted deposit evidence and then reads the resulting balance. The same reconciliation runs before a different new deposit is accepted. This removes completed evidence, resumes an interrupted transaction, or follows its durable refund path without asking a multiplayer user to create another request. Conflicting identities, corrupt evidence, and transactions already in manual review are never guessed or discarded. ATM data projects any remaining deposit recovery summary containing the original request UUID, deterministic transaction UUID, amount, and one of `RECOVERY_PENDING`, `MANUAL_REVIEW`, `COMPLETED`, or `REFUNDED`. The client adopts server pending state, blocks retries during manual review, and clears matching local recovery state only after the server proves a terminal result or no active recovery. A recovery check sends only that request and transaction pair. It cannot submit a currency source or amount and therefore cannot create or consume a second deposit. Retryable or blocked deposit recovery does not disable ATM tab navigation or committed cash claim collection. A refunded terminal response reports the exact value and `ORIGINAL_INVENTORY` destination.

If escrow remains in recovery or maintenance, run `/marketadmin status` and inspect `run/logs/latest.log` or the dedicated server log. Do not delete journal, checkpoint, ledger, claim, or custody files.

## Market navigation and data flow

A client market route contains a module, view, route nonce, search state, category, sort order, page, scroll offset, and optional selection. The navigation coordinator tracks history and response families. Opening a module or local tab sends a request to the server. The server validates the current session, permission, configured module toggle, lifecycle state, escrow readiness, ownership requirements, and route availability before returning an open screen packet.

Top level tabs replace the current route within the module. Detail pages retain one return route. Escape therefore closes a top level market screen in one action and returns from a detail page in one action without accumulating a tab history stack.

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

When `claims.automatic_delivery` is enabled, the server attempts bounded delivery for pending public money claims and exact item claims while the owner is online. Money goes to the wallet. Items go to player inventory. Capacity failure leaves the remaining claim pending. Automatic retries use stable request identities derived from the claim and remaining amount, so partial money delivery can continue without duplicating an earlier settlement.

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

Cancellation creates the exact item return claim in the same durable commit that closes the listing. Automatic claim delivery then returns the item immediately when inventory capacity permits. If capacity is unavailable, the item remains visible in Claims and does not require administrator review.

## Bazaar

The Bazaar supports an administrator JSON catalog or player selected registered commodities. Admin definitions live in `config/futureshops/bazaar/products/` and are validated as one atomic catalog.

Buy orders reserve money. Sell orders move matching inventory into custody. Matching uses price time priority, partial fills, checked fee calculations, configured price and quantity bounds, and circuit breaker state. Product identity and versions are durable. Removing a used product retires it instead of deleting history or custody.

## Shops and player shops

The server shop catalog is loaded from `config/futureshops/shops/`. Purchases validate the live catalog, price, quantity, promotion, payment source, inventory delivery, and request identity.

Player shop blocks persist owner, name, listings, trade modes, storage link, and promotional settings. Server services validate block existence, dimension, ownership, stock, linked storage, price or barter inputs, settlement, and permissions. Optional Refined Storage access remains behind its compatibility boundary.

## Normalized trade offers

Schema version 2 represents one listing as immutable outputs plus alternative acquire options and alternative Sell to Shop options. Components inside one option are an atomic AND requirement. Options are OR choices. An acquire option can be explicit free, money only, item only, or money plus items. A sell option consumes every configured input component and pays one checked money amount. Listings may contain several outputs, input bundles, both directions, per request and lifetime limits, rolling periods, cooldowns, schedules, permission nodes, stock policy, and validated bundle comparisons.

Free is an explicit trusted flag. Zero legacy prices remain disabled. Client labels, prices, savings, stock hints, and option identifiers never authorize settlement. The server rebinds every request to the live shop, listing, revision, action, option, quantity, permission, schedule, usage state, exact NBT templates, and payment source before creating escrow intent evidence.

Server Shop acquire carts capture the selected listing and option revisions for every line. Free, money, item, and compound lines can share one atomic plan when compatible. Money lines share the selected payment source. Every item input and output is normalized into exact custody evidence. A stale or conflicting request fails without partial fulfillment.

Player Shop normalized offers keep the shop block and linked storage authoritative. Old listing fields migrate in memory into a normalized offer and are written through the versioned block entity codec only after validation. Owner edits use the same persistent draft model as the Server Shop editor, but the save packet is bound to the block position, source listing index, owner, dimension, distance, current listing identity, and expected revision. Visitor option selection uses the typed Player Shop offer packet and the existing player shop escrow journal. Exact owner stock, barter sinks, proceeds, buyback capacity, claims, break protection, and recovery remain enforced.

Bulk inventory selling is a separate quoted batch and never enters the acquire cart. The server scans the player main inventory and offhand while excluding worn armor, resolves every active Sell to Shop option, and sends only a bounded presentation quote to the client. A general item requirement accepts matching stacks regardless of damage or NBT, while an exact NBT requirement accepts only its configured identity. Exact requirements reserve first so a general requirement cannot consume their only matching stack. Quote creation is read only and does not require a preexisting interactive shop session. The confirmation screen may begin with every eligible line selected or with nothing selected. The commit packet contains the quote UUID and selected line UUIDs only. The server retains the authoritative listing, option, revision, quantity, target, and payout binding.

Server Shop bulk lines use the existing normalized Server Shop offer service. Player Shop lines use the existing player shop escrow service and only consider shops in the configured nearby scan radius, current dimension, and loaded chunks. Quote preflight checks current usage limits, buyback capacity, stock insertion space, owner funds, player balance limits, permissions, and schedules without firing transaction events or creating escrow evidence. A bounded binary search reduces a line to the largest currently executable quantity when the complete inventory quantity cannot settle. Competing destinations are ordered by effective payout, complete exchange payout, distance, and stable identity. A confirmed line is never allowed to settle below its quoted payout. A changed revision expires the quote before mutation and the screen requests a replacement quote.

The batch is best effort across independent shops. Every successful line has a deterministic child request UUID and therefore executes once. A rejected line does not roll back an already committed line at another shop. Recovery or claim handling remains owned by the underlying escrow transaction. The result reports committed lines, failed lines, recovery lines, and the full settled payout, including value routed into a durable claim. Quotes are player bound, single use, memory only, expire after 60 seconds, and are cleared on logout and server stop. See [Bulk inventory selling](docs/bulk-selling.md).

Interrupted normalized Server Shop single and cart requests are recovered from their exact persisted prepared evidence. A player login attempts at most 16 recoveries. While escrow is ready, a round robin background pass runs every 40 server ticks, attempts at most eight recoveries globally, and attempts at most two for each examined player. The recovery entry point bypasses visitor session, module availability, and ordinary request rate gates only after resolving trusted persisted evidence. It does not rebuild price, option, quantity, stock, or payment identity from the client. Disabled modules therefore cannot strand already prepared value, while new trades remain disabled.

Normalized Server Shop and Player Shop settlement preserves the public `ShopTransactionEvent` and `BarterTradeEvent` integration points when `events.transaction_events` is enabled. Pre events run only while creating a new prepared request. Cancellation prevents preparation. A positive trusted money leg may be changed only to another positive value, explicit free remains zero, and item only barter retains an absent money leg. Item and compound acquire options report every required item component through the barter event. The legacy event fields expose the first output or input item for compatibility; exact bundle and option identity remains in durable evidence and transaction history. Post events run after the first durable outer commit and do not repeat during request replay. Transaction history uses idempotency markers per component and records every exact bundle output, barter input, Sell to Shop input, selected option, and validated bundle comparison revision.

Edit mode keeps New Offer and Add Items visible in every filter. Add Items opens a searchable registry grid with at most 21 columns and 8 visible rows on wide screens. The active All or Buy, Sell, Barter, or Bundles filter determines the draft type, so one field never changes meaning because of a hidden entry mode. Buy and Sell require one item and one Base Price and may save directly from the grid. Typing `1` or `1.00` means one major currency unit. Barter selects its output and Bundle selects several outputs, then both continue to the Simple Editor because their remaining requirements cannot be guessed safely.

Open Simple Editor preserves the selected items, category, Base Price, stock, and trade direction. The Simple Editor exposes the common controls for money, free, barter, compound, alternative, Sell Only, Buy and Sell, and bundle offers. Every mode remains clickable for every supported item. The selected mode uses the primary selected style instead of the disabled style. Advanced Editor exposes limits, schedules, permissions, exact NBT, stock controls, arbitrary option structures, and manual bundle comparisons. Review uses the visitor projection and can match bundle outputs to standalone money offers for verified savings. Advanced field labels occupy a separate column and validation remains in the help surface so fields do not overlap.

Every editor and picker action uses the FutureShops Nocturne button renderer while retaining standard focus, keyboard activation, narration, tooltips, and disabled state behavior. Fields reserve a dedicated label row and scrolling clips content above the persistent footer. Apply and Save and Close remain visible. Their disabled help shows the exact first blocking issue and counts only additional issues. Editing a rejected draft clears its stale save response immediately. Apply waits for the matching successful acknowledgement and stays open. Save and Close waits for the same acknowledgement before returning. Stale revisions must be reviewed or reloaded and cannot silently overwrite a newer listing. Catalog saves validate the complete candidate, write a temporary sibling, preserve a bounded backup, atomically replace the target when supported, and restore the last valid file if reload fails. Catalog scoped server validation paths are reduced to the edited listing before they reach the editor, while unrelated invalid listings produce a Shop Catalog error. Internal listing, option, and component identifiers are never presented as Display Name failures.

During common mod setup on both physical clients and dedicated servers, FutureShops creates `config/futureshops/shops/` and writes `admin.json` when it is absent. This happens before a singleplayer world opens, allowing a modpack developer to prepare the global client catalog and include it in the pack. The integrated server reads this global catalog. A remote server never reads the client's catalog and remains authoritative from its own installation. Existing `admin.json` files are never replaced. A legacy `default.json` is moved to `admin.json` only when the new path is absent.

The generated schema version 2 catalog includes a one claim free cookie, a Sell Only rotten flesh example, and an iron tool bundle priced below its three standalone offers. Every FutureShops managed TOML file and catalog lives below `config/futureshops/`.

See [3.1 trade offer configuration](docs/config-3.1-offers.md) for the schema and administrator workflow.

## Configuration

| File | Responsibility |
| --- | --- |
| `config/futureshops/futureshops-common.toml` | Modules, navigation, economy, currency, permissions, and shop behavior |
| `config/futureshops/futureshops-escrow.toml` | Recovery, checkpoints, claims, request limits, and asset bounds |
| `config/futureshops/futureshops-auction-house.toml` | Listing, bidding, settlement, fee, duration, and lifecycle rules |
| `config/futureshops/futureshops-bazaar.toml` | Catalog control, matching, order, fee, limit, and lifecycle rules |
| `config/futureshops/futureshops-client.toml` | Layout, scale, density, accessibility, theme, and presentation |

The mod creates `config/futureshops/` before Forge registers any FutureShops specification.
Recognized loose `futureshops-*.toml` files and Forge generated backups are moved into the directory
without rewriting their contents. The migration runs on clients, integrated servers, and dedicated
servers and is idempotent. When a canonical nested file already exists, it remains authoritative and
the conflicting loose file is preserved under `config/futureshops/migration-backups/` with a unique
name. Unsafe symbolic links, directories using a recognized file name, and incomplete moves stop
configuration startup instead of silently replacing operator settings with defaults. Forge owned
files such as `fml.toml` and `forge-client.toml` remain at the configuration root.

Module enablement lives only in `config/futureshops/futureshops-common.toml`. Escrow has no disable switch. A disabled module is not projected as a header tab. An enabled module is projected while escrow or lifecycle control is recovering, keeps claims reachable, and blocks new mutations with a recovery reason. It must not be represented as disabled merely because it is not ready. Open marketplace screens poll the server supplied projection every five seconds and promote a recovering module to its persisted lifecycle state when readiness returns. Some validated settings reload immediately, contract rules apply only to new contracts, and identity or persistence settings require restart or migration. An invalid reload preserves the last valid snapshot and logs the rejected field.

Exact defaults, examples, validation behavior, and reload boundaries are documented in [Configuration examples](docs/config-3.0-examples.md).

## Commands and permissions

Player entry points include `/shop`, `/playershops`, `/sellall adminshop`, `/sellall playershops`, `/ah`, `/bz`, `/claims`, `/balance`, `/pay`, `/baltop`, `/atm`, `/deposit`, and `/withdraw`. Appending `confirm` to either `/sellall` target skips the review screen and submits every eligible quote line.

Administrative entry points include `/shopadmin` and `/marketadmin`. `/marketadmin status` reports module control state, escrow runtime state, open contract counts, and pending recovery. `/marketadmin inspect <transactionId>` is a read only escrow inspection for the complete recovery handle. It reports operation and state, request identity, participants, currency provider, durable evidence phase, value, claims, retry schedule, last error, and the safe next action. Mutating administrative operations require the configured operator level or Forge permission node, a bounded reason where configured, confirmation where configured, and an audit record.

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

Normalized catalog packets have both per listing and aggregate byte limits. Offer packets bound identifier lengths, quantity, action, revision, payment source presence, request UUID, and response token before server dispatch. Rate limiting runs before replay lookup and expensive storage work. Economic retries reuse the original request UUID. Reuse with different listing, revision, option, action, quantity, actor, shop, or payment source is a conflict.

Successful normalized Server Shop requests write one immutable receipt to `<world>/futureshops/escrow/offer_replay/`. Receipt files are sharded by the first four hexadecimal request UUID characters and discovered through the forced append only `index.wal`. Each receipt retains the exact request fingerprint, terminal outcome, and usage evidence needed to replay the result, reject conflicting identity reuse, and rebuild limits after restart. Receipt decoding is bounded, existing identities must match exactly, symlinks are rejected, and a new receipt becomes visible through an atomic move.

The prepared and commit SavedData stores keep bounded live windows. Their finite replay archives remain only as migration caches for worlds written before the filesystem ledger. A live prepared or commit row is compacted only after the exact ledger receipt exists. There is no global or per player lifetime cap on successful replay identities. Disk exhaustion, an unsafe path, a conflicting receipt, or corrupt discovery evidence fails closed before a new stock or value mutation.

`futureshops_server_shop_offer_usage` persists its byte cursor into the replay discovery index. Usage recovery consumes at most 1,024 receipts per batch and applies their evidence idempotently. This closes the period between a durable value commit and a failed usage projection without requiring an unbounded startup scan.

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

After packaging, inspect the manifest, expanded `META-INF/mods.toml`, mixin configuration and refmap, assets, data, dependency metadata, and the complete Git diff. Version `3.1.0-beta.1` must expand into the mod metadata, and `logoFile = "futureshops.png"` must resolve to the 400 by 400 project logo at the jar root. Build output, run directories, logs, crash reports, local configs, caches, IDE files, and `AGENTS.md` must not be committed.

## Troubleshooting

### Module disabled although TOML enables it

Run `/marketadmin status`. Confirm the configured toggle, market control status, and escrow runtime state separately. A configured module can still be claims only, frozen, draining, recovering, or in maintenance.

Check that the value was changed in the server copy of `config/futureshops/futureshops-common.toml`, then check the server log for an accepted reload or a rejected field. Also check for checkpoint, journal, migration, catalog, or market control failures. Open screens refresh the server projection every second during recovery and every five seconds while ready. Reopening the screen should not be required.

### Marketplace profile does not open

Check the server log for an exception while reading the balance dashboard. Profile presentation must use the safe display balance path and must not require a live mutation provider during startup recovery.

### ATM deposit recovery remains pending

Opening the ATM first attempts bounded automatic reconciliation with the original server supplied identity. Starting a different deposit attempts the same safe reconciliation before accepting new value. If recovery remains pending, use **Check recovery**. Do not remove player data, escrow files, claims, journals, or checkpoints. Withdrawal and Deposit tabs remain navigable, and a committed physical cash claim remains collectible while the deposit recovery is retryable.

Use **Copy** to obtain the full transaction UUID, then run `/marketadmin inspect <transactionId>`. `AUTOMATIC_RECOVERY` means the exact transaction remains eligible for bounded recovery. `ADMIN_REVIEW` means an operator must inspect the reported evidence and last error before any administrative action. `NO_ACTION` means the transaction is terminal. A `REFUNDED` deposit returned value through its durable cancellation path. Bazaar buyer order refunds are money claims in the market Claims view, not ATM physical cash claims.

### Client launch fails before a window appears

Confirm a graphical session and working OpenGL environment. A headless environment can verify compilation and dedicated server startup but cannot complete a client rendering smoke test.

### Bazaar catalog reload fails

Read the first validation error in the server log. The loader rejects the whole new catalog and keeps the last valid snapshot. Check schema version, duplicate fields, product version conflicts, item identifiers, NBT, limits, UTF 8, and symbolic links.

### Escrow enters maintenance

Preserve the world and logs. Read the first causal recovery error. Do not delete state files. Restore one complete consistent backup if lineage or schema validation cannot be resolved in place.

On Windows, directory synchronization is not exposed through the Java file channel API. FutureShops treats only that platform specific directory operation as best effort after each replay file has already been written, forced, and atomically moved. A Windows `AccessDeniedException` for the `offer_replay` directory should no longer leave escrow in recovery. File write, file force, atomic replacement, and non Windows directory synchronization failures still fail closed.

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

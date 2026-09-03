# FutureShops technical documentation

This document is the maintainer overview for the FutureShops 2.3.0 NeoForge 1.21.1 line. The active product contract is [the strict external economy plan](plan.md). The public provider details are in [the economy provider API guide](../api/economy-provider.md), and phase evidence is indexed from [the documentation index](../README.md).

## Runtime and build

The line targets Minecraft 1.21.1, NeoForge 21.1.248, Java 21, Gradle through the checked in wrapper, and GeckoLib 4.8.4. Use `bash ./gradlew test` and `bash ./gradlew build` on Linux or macOS. The equivalent Windows commands are `gradlew.bat test` and `gradlew.bat build`. Development launch tasks are `runClient`, `runServer`, `runGameTestServer`, and `runData`.

The same FutureShops jar is installed on the client and server. Common and dedicated server code must not load client classes. The server owns provider selection, balances, transaction state, custody, claims, and permissions. Client screens receive presentation data and typed outcomes only.

## Module boundaries

The Java package is `com.enviouse.futureshopsp` and the runtime namespace is `futureshops`.

* `api` contains the public shop and economy entry points.
* `api.economy` contains the provider contract, capability declaration, metadata, request identity, and typed result model.
* `server.economy` contains the internal provider, lifecycle controller, transaction coordinator, journal, custody, claims, and provider selection bridge.
* `server.shop`, `server.transaction`, and `server.market` contain server authoritative shop, transaction, and market state.
* `network` contains custom payloads and packet validation.
* `client` contains screens, presentation snapshots, and client navigation.
* `compat` contains optional integrations and must remain safe when their dependencies are absent.

Only `InternalEconomyProvider` owns the internal balance saved data. External providers own their balances. FutureShops records requests, provider outcomes, market state, custody, claims, and confirmed analytics facts, but never maintains an external balance mirror.

## Economy lifecycle

`BalanceManager.initialize` freezes provider registration, resolves the restart selected provider, loads the transaction journal, custody index, and claim index, and marks each index unclean before readiness. The lifecycle controller admits queries and mutations only in `READY`. `DRAINING` rejects new mutations while allowing the shutdown checkpoint. `RECOVERING` permits only safe reconciliation. `FROZEN` preserves evidence when a provider outcome cannot be proven. `MISSING`, `INCOMPATIBLE`, and `FAILED` remain unavailable until a corrected restart. `STOPPED` is terminal for the current server lifecycle.

`BalanceManager.clear` begins draining, flushes every economy index, and writes clean markers only after the complete flush gate succeeds. A missing marker or invalid checksum causes recovery on the next start. The server never falls back to the internal wallet during an external provider failure.

## Transaction ordering

`EconomyTransactionCoordinator` is the only typed mutation boundary. It validates lifecycle, immutable capabilities, amount, request identity, and provider precheck before writing intent. It writes `PREPARED` before creating custody, then writes `EXTERNAL_PENDING`, invokes one provider leg, validates the receipt against the request, and writes `EXTERNAL_CONFIRMED` followed by `RESOLVED`. Confirmed balance change events are published only after the resolved journal record is durable, and their reason matches the mutation kind. Duplicate request identities replay the recorded result. Exceptions, missing acknowledgements, mismatched receipts, and unknown lookup outcomes become `UNCERTAIN` and freeze external mutation.

The public provider contract requires balance query, precheck, withdraw, deposit, durable receipt lookup, and idempotent retry capabilities for a mutation. A provider that cannot prove a capability is refused before journal intent and before custody. A receipt resulting balance is evidence for the caller and is not copied into a FutureShops ledger.

## Durable records

`EconomyJournalSavedData` stores versioned, checksummed request and outcome records under `futureshops_economy_journal`. It stores no external balance field. `EconomyCustodySavedData` stores bounded item identity, owner, quantity, content hash, and `HELD`, `DELIVERED`, `CLAIMED`, or `RELEASED` state under `futureshops_economy_custody`. `EconomyClaimSavedData` stores claimant, exact amount, description, and non expiring `PENDING`, `DELIVERED`, or `RESOLVED` state under `futureshops_economy_claims`.

`PlayerShopBarterEscrowSavedData` stores the exact serialized item stacks for each player shop barter payment under `futureshops_player_shop_barter_escrow`. A payment is persisted as `PREPARED` before inventory removal, advanced to `REMOVED` only after the collected stacks match the persisted bytes, then to `STORED` after the configured barter storage accepts them, and finally to `COMPLETE` after sale delivery. Proven in process rollback uses the same exact stacks. Any non terminal record survives restart and keeps the economy in recovery until the record is resolved from evidence, so an interrupted storage write cannot be retried blindly or silently discard the buyer's items.

Physical currency commands and right click deposits check the selected provider and lifecycle before validating or consuming a bill. They are active only for a ready internal provider. Bills remain registered and decodable, but are inert when an external provider or any unsafe lifecycle state is selected.

All new records use explicit schema versions, bounded fields, defensive enum and identifier decoding, and SHA 256 checksums. Unknown newer versions, malformed entries, duplicate identities, or checksum failures are read only recovery blockers. In memory stores are used only by ephemeral unit test servers without a world.

Custody and claims are separate from provider balances. A held item cannot be claimed or released through an invalid transition. A pending claim remains durable while the owner is offline or the lifecycle is frozen. Recovery must use the originating provider and request identity, and it must never guess an external balance or create an automatic refund for an unknown effect.

Player shop settlement claims reserve a request UUID and amount in settlement SavedData before
crediting the owner. The coordinator records the same request in the durable claim index and
provider journal. Pending proceeds recorded while a claim is in flight remain available. The
settlement amount is reduced only after a confirmed provider result, and retries reuse the same
request identity.

## Existing surfaces and current limits

Legacy `EconomyProvider` calls are presented through the coordinator backed view, so existing commands and public APIs receive fail closed behavior when a selected provider is unavailable. `ShopModAPI.queryBalance` exposes the typed result for integrations that need to distinguish confirmed, rejected, unavailable, ambiguous, and recovery required outcomes. Balance commands and administrative checks show an unavailable error instead of formatting zero, while administrative set and reset adjustments are durable coordinator legs limited to ready internal mode. Shop, dashboard, and balance leaderboard packets carry typed balance availability and the selected provider lifecycle so clients never render an unavailable balance as a confirmed zero. The balance leaderboard is available only for the internal provider until a selected external provider declares a reviewed ranking capability, and its UI labels that limitation separately from provider readiness. Complete checkout custody ordering, player shop multi leg settlement, external Pixelmon and Vault adapters, and the full restart and GameTest matrix are still owned by later tasks in the active phase plan.

Internal provider receipts are stored in checksummed `futureshops_internal_economy_receipts` SavedData and participate in clean marker validation. A restart can therefore look up ordinary completed internal legs without relying on process memory. An interrupted provider effect remains subject to the journal and recovery rules and is never inferred from a balance snapshot.

Physical coin deposits reserve a durable custody record before consuming mint authorization or crediting the internal provider. A proven rejection restores consumed mint counts and stack quantities, while an ambiguous provider or custody result remains held for recovery. Right click deposits and `/deposit` use the same internal coordinator boundary.

Physical withdrawals also hold a durable custody record before debiting the internal provider. Bills are inserted before their mint authorization is recorded, and delivery finalizes custody only after all bill stacks are present. An inventory failure leaves the debit and held custody for recovery instead of issuing an unjournaled compensating balance edit.

Physical money items retain their existing identifiers and anti replay data. They are valid for the ready internal provider only and remain inert, but are not deleted, when an external provider or a recovery state is selected. No ATM path is added by this line.

## Persistence and recovery operations

Back up the complete world, `config/futureshops`, the FutureShops jar, and every external provider data directory before changing the provider selection. Stop the server before restoring. Restore one complete matching snapshot rather than deleting journal, custody, claims, balances, listings, or world data. If the provider is missing or its exact version changes, keep the lifecycle blocked and restore the originating provider or use an evidence backed operator resolution.

Do not downgrade a world after new economy schemas have been written unless migration compatibility has been proven on a disposable copy. Keep backup hashes and sanitized logs with the verification packet. Never include credentials, private player data, or raw provider logs in repository evidence.

## Verification

Run focused economy tests, then `bash ./gradlew test --no-daemon`, `bash ./gradlew build --no-daemon`, and the applicable data, GameTest, dedicated server, client, multiplayer, reconnect, restart, and jar inspection checks from the active phase plan. Review `git diff --check` and the complete diff before committing. Build output, run directories, logs, downloaded external jars, and CodeGraph state are generated or disposable and must remain untracked.

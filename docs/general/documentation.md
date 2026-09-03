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

`EconomyTransactionCoordinator` is the only typed mutation boundary. It validates lifecycle, immutable capabilities, amount, request identity, and provider precheck before writing intent. It writes `PREPARED`, then `EXTERNAL_PENDING`, invokes one provider leg, validates the receipt against the request, and writes `EXTERNAL_CONFIRMED` followed by `RESOLVED`. Duplicate request identities replay the recorded result. Exceptions, missing acknowledgements, mismatched receipts, and unknown lookup outcomes become `UNCERTAIN` and freeze external mutation.

The public provider contract requires balance query, precheck, withdraw, deposit, durable receipt lookup, and idempotent retry capabilities for a mutation. A provider that cannot prove a capability is refused before journal intent and before custody. A receipt resulting balance is evidence for the caller and is not copied into a FutureShops ledger.

## Durable records

`EconomyJournalSavedData` stores versioned, checksummed request and outcome records under `futureshops_economy_journal`. It stores no external balance field. `EconomyCustodySavedData` stores bounded item identity, owner, quantity, content hash, and `HELD`, `DELIVERED`, `CLAIMED`, or `RELEASED` state under `futureshops_economy_custody`. `EconomyClaimSavedData` stores claimant, exact amount, description, and non expiring `PENDING`, `DELIVERED`, or `RESOLVED` state under `futureshops_economy_claims`.

All new records use explicit schema versions, bounded fields, defensive enum and identifier decoding, and SHA 256 checksums. Unknown newer versions, malformed entries, duplicate identities, or checksum failures are read only recovery blockers. In memory stores are used only by ephemeral unit test servers without a world.

Custody and claims are separate from provider balances. A held item cannot be claimed or released through an invalid transition. A pending claim remains durable while the owner is offline or the lifecycle is frozen. Recovery must use the originating provider and request identity, and it must never guess an external balance or create an automatic refund for an unknown effect.

## Existing surfaces and current limits

Legacy `EconomyProvider` calls are presented through the coordinator backed view, so existing commands and public APIs receive fail closed behavior when a selected provider is unavailable. Complete checkout custody ordering, player shop multi leg settlement, external Pixelmon and Vault adapters, and the full restart and GameTest matrix are still owned by later tasks in the active phase plan.

Physical money items retain their existing identifiers and anti replay data. They are valid for the ready internal provider only and remain inert, but are not deleted, when an external provider or a recovery state is selected. No ATM path is added by this line.

## Persistence and recovery operations

Back up the complete world, `config/futureshops`, the FutureShops jar, and every external provider data directory before changing the provider selection. Stop the server before restoring. Restore one complete matching snapshot rather than deleting journal, custody, claims, balances, listings, or world data. If the provider is missing or its exact version changes, keep the lifecycle blocked and restore the originating provider or use an evidence backed operator resolution.

Do not downgrade a world after new economy schemas have been written unless migration compatibility has been proven on a disposable copy. Keep backup hashes and sanitized logs with the verification packet. Never include credentials, private player data, or raw provider logs in repository evidence.

## Verification

Run focused economy tests, then `bash ./gradlew test --no-daemon`, `bash ./gradlew build --no-daemon`, and the applicable data, GameTest, dedicated server, client, multiplayer, reconnect, restart, and jar inspection checks from the active phase plan. Review `git diff --check` and the complete diff before committing. Build output, run directories, logs, downloaded external jars, and CodeGraph state are generated or disposable and must remain untracked.

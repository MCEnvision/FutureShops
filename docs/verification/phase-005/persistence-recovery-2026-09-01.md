# Phase 005 persistence and recovery evidence

## Scope

This packet covers `CORE-PHASE-005`, `CORE-REQ-012`, and `CORE-REQ-014` on the
Forge support line. The branch started from merged `origin/1.20.1` commit
`c555d17e25bc87b84507b452c7d73c2bedcef6a9`.

## Inventory and database result

The complete concrete `SavedData` inventory and file backed state map is in
[Persistence and database audit](../../persistence-database-audit.md). Source,
build metadata, resource, and dependency scans found no embedded or remote
database driver. State is world `SavedData`, block and player NBT, bounded escrow
files, JSON catalogs, and Forge TOML configuration.

## Findings and traceability

| Issue | Finding | Repair status |
| --- | --- | --- |
| [48](https://github.com/MCEnvision/FutureShops/issues/48) | Admin category collections and identifiers decoded without complete bounds | Filed before repair. Repair is on this phase branch. |
| [49](https://github.com/MCEnvision/FutureShops/issues/49) | Shop block listings, bundle output, and listing NBT decode needed bounded validation | Filed before repair. Repair is on this phase branch. |
| [50](https://github.com/MCEnvision/FutureShops/issues/50) | Remaining world stores accepted unbounded collections or nested snapshots | Filed before repair. Repair is on this phase branch. |
| [51](https://github.com/MCEnvision/FutureShops/issues/51) | Bounded replay and market profile lists could hide wrong element types as empty lists | Filed before repair. Repair is on this phase branch. |
| [52](https://github.com/MCEnvision/FutureShops/issues/52) | Player inventory evidence accepted malformed list element types during escrow verification | Filed before repair. Repair is on this phase branch. |

The repairs reject oversized or malformed state before live collection mutation,
enforce the same limits at save time, preserve supported legacy fields, and keep
unknown newer escrow state fail closed. No player or world data was deleted or
selectively restored.

## Deterministic corruption corpus

`PersistenceSavedDataBoundsTest` covers duplicate mint identities, oversized
department collections, oversized franchise membership, oversized transaction
history, oversized seller settlement history, oversized saved shop snapshots,
invalid shop limit values, oversized stock refresh keys, and negative dynamic
pricing state. It also rejects wrong outer and nested list element types in
administrator bulk replay and market profile state. `AdminCategorySavedDataBoundsTest` covers valid round trip,
oversized categories, and malformed assignments. `PlayerShopOfferPersistenceTest`
covers malformed bundle entries and oversized listing identifiers.

The existing escrow test corpus covers journal truncation and crash cuts,
checkpoint manifests and force ordering, ledger conservation, custody and claim
delivery, player inventory slot proof, replay receipts, migration, stock
reservation, auction and Bazaar escrow, ATM recovery, and maintenance recovery.
Issue 32 successor fixtures remain synthetic and include modded item NBT and
unrelated player NBT sentinels. Player inventory readers now reject a raw
`Inventory` list with a non compound element type before hashing or delivery,
and the focused delivery and protected cash tests cover that failure mode.

## Local command evidence

The focused and complete Forge unit suites pass with Java 17 and one Gradle
worker.

```text
JAVA_HOME=/usr/lib/jvm/temurin-17-jdk-amd64 bash ./gradlew test --tests com.enviouse.futureshops.server.shop.PersistenceSavedDataBoundsTest --tests com.enviouse.futureshops.catalog.AdminBulkListingPlannerTest --tests com.enviouse.futureshops.server.market.profile.MarketProfileSavedDataTest --tests com.enviouse.futureshops.server.escrow.inventory.PlayerInventoryDeliveryTest --tests com.enviouse.futureshops.server.escrow.redemption.ProtectedCashRedemptionEvidenceTest --no-daemon --max-workers=1
BUILD SUCCESSFUL

JAVA_HOME=/usr/lib/jvm/temurin-17-jdk-amd64 bash ./gradlew test --no-daemon --max-workers=1
BUILD SUCCESSFUL
```

The issue 32 corpus also passes.

```text
JAVA_HOME=/usr/lib/jvm/temurin-17-jdk-amd64 bash ./gradlew test --tests com.enviouse.futureshops.server.escrow.inventory.Issue32PlayerStateCorpusTest --no-daemon --max-workers=1
BUILD SUCCESSFUL
```

The resource, game test, and packaging gates pass.

```text
JAVA_HOME=/usr/lib/jvm/temurin-17-jdk-amd64 bash ./gradlew runData --no-daemon --max-workers=1
BUILD SUCCESSFUL
JAVA_HOME=/usr/lib/jvm/temurin-17-jdk-amd64 bash ./gradlew runGameTestServer --no-daemon --max-workers=1
All 5 required tests passed :)
BUILD SUCCESSFUL
JAVA_HOME=/usr/lib/jvm/temurin-17-jdk-amd64 bash ./gradlew build --no-daemon --max-workers=1
BUILD SUCCESSFUL
```

A dedicated Forge server reached `Done`, initialized FutureShops, loaded the
default shop and 12 Bazaar products, and stopped through RCON. With the test
server in offline mode, two independent Forge clients completed the modded
handshake and joined the same world as `phaseclientone` and `phaseclienttwo`.
Both clients disconnected cleanly before the server stopped through RCON.

The deterministic backup rehearsal hashed 135 files in a cold world, escrow,
playerdata, and configuration cohort. Its manifest digest was
`f5174a8b1fc873062e3dfdf9f3c8ae3e0e87d0c45dbba743caab54702a4fdcc6`. A copied
damaged cohort changed the balances file and produced digest
`e5ffefe41e8b694f39daf5be3ec646f79cd10d70795a0f9ad237b25f703438a0`; the
untouched cohort restored with the original manifest and balances digest
`05559d42b575e94777a1ac578a7e4330fec1f6901a41656b4e3988ec3a61ef39`. The
restored cohort then started a disposable dedicated server and stopped cleanly.

The final phase packet will append the exact merged revision rerun, jar digest,
pull request checks, and signed phase tag after the phase branch is integrated.

## Recovery boundary

Recovery uses one complete matching world, escrow, playerdata, and configuration
cohort. Verification runs `/marketadmin maintenance status` and
`/marketadmin maintenance verify`; resume is allowed only through
`/marketadmin maintenance resume confirm <reason>` after lineage and conservation
are clear. Failed verification leaves maintenance active. No evidence step
deletes journals, checkpoints, ledgers, custody, claims, player data, migration
markers, or receipts.

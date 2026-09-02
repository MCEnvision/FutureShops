# Phase 000 owner disposition and local campaigns

Captured 2026-09-01 for P000-TASK-005 after the frozen issue snapshot. This packet preserves the owner decisions in the active plan and turns each scoped report into executable local work.

## Historical prerequisite reconciliation

`EXT-001` is the historical issue 22 reporter acceptance gate. It is superseded by the owner accepted root cause and candidate evidence recorded in issue 22. Fresh independent verification and merge proof remain required in CORE-PHASE-001, but reporter confirmation is not an endpoint dependency.

`EXT-002` is the historical issue 25 reporter gate. It is superseded by the owner compatibility disposition and the local beta transition matrix. The frozen issue comments remain evidence. Reporter retest is not a blocker.

`EXT-003` is the historical issue 32 external evidence gate. It is superseded by a generated local corruption and recovery campaign. No player data file is deleted or overwritten during testing.

`EXT-004` is the historical controlled multiplayer environment gate. It is resolved by the owner supplied local capacity. The 64 GB workstation is the default isolated server and multiple client host. The 96 GB node1 host is an authorized temporary isolated server fallback.

`EXT-005` is the available authenticated GitHub access boundary. The active identity is `EnVisione` with administrator access to `MCEnvision/FutureShops`.

No active task depends on `EXT-001` through `EXT-004`, and no evidence request is sent for those historical records.

## Issue 22 verification handoff

The frozen issue 22 comments map to the following required proof.

| source | mapped proof |
| --- | --- |
| initial Windows 11 blur report | isolated NeoForge 1.21.1 client screen lifecycle reproduction |
| owner root cause comment | background policy and vanilla blur suppression inspection |
| candidate commit `bfba91f7b0c51b03d07117c4f1851c38a98f6186` | exact ancestry, changed path, version, and candidate JAR inspection |
| candidate test claim | independent Java 21 unit, build, client smoke, and rendering regression rerun |
| owner acceptance | pull request integration into `1.21.1`, merged revision verification, and issue closure |

CORE-PHASE-001 owns the first action. It must requery `origin/1.21.1` and the candidate branch, then run the independent checks before integration. No Forge files may be transferred.

## Issue 25 compatibility matrix contract

CORE-PHASE-002 will execute these synthetic, sanitized rows on isolated copies.

| row | fixture | action | expected proof |
| --- | --- | --- | --- |
| current valid catalog | complete catalog and stock with valid migration metadata | startup, open shop, buy, direct sell, sell inventory | one ready authoritative snapshot and conserved value |
| existing complete stock with missing migration metadata | complete durable stock and absent or old migration marker | startup and reload | verified stock adoption, no false unavailable state |
| malformed catalog entry | invalid item identifier or field | startup and edit attempt | last known good state preserved and precise diagnostic |
| removed registry item | listing references a missing item | startup, browse, and edit | fail closed with listing path, quarantine or repair route |
| reload and restart | valid catalog across save and reload boundaries | restart server, reopen client, reconnect | readiness converges and exact entries persist |
| unsupported intermediate beta | sanitized version transition fixture | migration and recovery | explicit compatibility disposition and non destructive recovery |

Every row records first divergence, catalog digest, stock, readiness state, request identity, and recovery result.

## Issue 32 corruption and recovery campaign contract

CORE-PHASE-002 will generate isolated player and world copies from a valid fixture. Each seed is recorded with an input digest and an output digest. The campaign covers malformed, truncated, oversized, old, newer, unknown, duplicate, cross mod, partial write, and crash cut state. Modded NBT sentinel fields are included outside FutureShops ownership.

For every seed, the campaign asserts that FutureShops owned fields are either recovered deterministically or exposed as a durable claim, unrelated modded fields remain byte equivalent, no item or balance is created or lost, restart and reconnect converge, and recovery never requires deleting player data. Any unexpected checksum, ownership, or conservation change stops the run and preserves the complete fixture.

## Issue 34 finite stock campaign contract

CORE-PHASE-002 will run an isolated Forge 1.20.1 dedicated server with at least two independent clients. The matrix covers finite stock success, infinite stock success, insufficient funds, stale stock, concurrent buyers, full inventory, provider failure, disconnect, retry, restart, and reconnect. Each case records item identity, price, stock before and after, balance before and after, inventory before and after, escrow and claim state, request UUID, server and client diagnostics, and the conservation equation.

The default host is the local `node-1` workstation. If a repeatable run needs more capacity, the authorized 96 GB node1 temporary server fallback is used with isolated credentials, world digest, configuration, logs, and host role. The live economy and any unbacked world are never used.

## Capacity record

At capture time the local host reported `node-1`, 20 processors, 94 GiB total memory, 36 GiB available memory, 48 GiB swap, and 840 GiB free on the project filesystem. This confirms sufficient local capacity for isolated server and multiple client scheduling. The owner supplied 96 GB node1 fallback remains authorized by DEC-007. Java 25 is installed as the shell default; line specific baselines must select the pinned Java 17 and Java 21 runtimes before execution.

## Status

This packet completes the Phase 000 contract definition only. The issue campaigns remain unfinished until CORE-PHASE-002 executes their runtime proof.

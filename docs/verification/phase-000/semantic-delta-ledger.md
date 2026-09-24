# Phase 000 semantic delta coverage

Observation date: 2026-09-24
Phase: CORE-PHASE-000
Task: P000-TASK-002
Requirement: CORE-REQ-001
Interface: CORE-IF-001

## Pinned comparison

The Forge baseline is commit `78fad4069d778996c24ecf5acc5cbe0e1edea7a` with tree `c75785f68567cc1aa44be53ef3a58733fbdfb075`. The read only NeoForge donor is commit `cc2d69425beea2c93a4000e44d49381deba8eed2` with tree `ab6887cd27c38d21894c16c2ad41da4e1aed9271`. The source comparison uses `git diff --name-status --find-renames` over those exact objects. The raw per path ledger is `semantic-delta-ledger.tsv`, and the donor history ledger is `history-delta-ledger.tsv`.

The Forge tree contains 1,624 files and 1,525 Java files. The donor tree contains 433 files and 336 Java files. The history objects contain 68 Forge commits and 525 donor commits. The path comparison contains 1,923 records, with 300 donor additions, 1,491 Forge only paths, 20 common path changes, and 112 renames. The history ledger contains all 525 donor commits, not a sample.

| Path category | Records |
| --- | ---: |
| Production | 1,290 |
| Test and GameTest | 439 |
| Documentation | 27 |
| Resource | 17 |
| Build | 10 |
| Configuration | 5 |
| Dependency | 4 |
| Donor evidence or plan classification | 131 |

## Disposition reconciliation

| Disposition | Path records | Meaning |
| --- | ---: | --- |
| Forge only preservation | 1,422 | Existing Forge capability or supporting artifact is absent from the donor and must remain in the Forge line. |
| Adapted later | 370 | A production, test, resource, build, or applicable documentation delta is assigned to a later Forge task for implementation or equivalence proof. |
| Rejected non scope item | 131 | Donor planning, verification, or evidence material is retained as provenance only and is not copied as product behavior. |

The TSV is the authoritative row set. Every row has all `DeltaEntry` fields: donor commit, donor path, semantic change, Forge path, applicability, disposition, requirement, phase, task, regression identity, and reproducible evidence command. `history-delta-ledger.tsv` gives each donor commit the same ownership and proof treatment. No applicable row is left unknown.

The automated row classifier only assigns ownership. It does not assert that a donor implementation is compatible with Forge. Later phase tasks must inspect the actual hunk, adapt loader and API details, and add falsifiable regression evidence. In particular, donor client, networking, data component, and provider code never authorizes a direct copy into Forge.

## Ownership mapping

| Phase | Assigned semantic area | Canonical task |
| --- | --- | --- |
| CORE-PHASE-001 | Build, dependency, diagnostics, and quality workflow deltas | P001-TASK-003 and P001-TASK-004 |
| CORE-PHASE-003 | Escrow, receipts, custody, recovery, and durable wallet deltas | P003-TASK-001 through P003-TASK-004 |
| CORE-PHASE-004 | Exact Pixelmon and compatibility boundary deltas | P004-TASK-001 through P004-TASK-004 |
| CORE-PHASE-005 | Economy API, commands, money, events, and shared effects | P005-TASK-001 through P005-TASK-004 |
| CORE-PHASE-006 | Catalog, offers, shops, blocks, and bulk settlement | P006-TASK-001 through P006-TASK-004 |
| CORE-PHASE-007 | Bazaar, Auction House, market, funding, and expiration | P007-TASK-001 through P007-TASK-004 |
| CORE-PHASE-008 | Client, packet, synchronization, and remaining applicable donor changes | P008-TASK-001 through P008-TASK-004 |
| CORE-PHASE-010 | Product documentation and support material that describes delivered behavior | P010-TASK-001 through P010-TASK-003 |

History rows whose subjects are evidence, merge receipts, validation records, or plans are explicitly marked non product. This prevents a donor evidence commit from being mistaken for a Forge implementation. History subjects that describe behavior are marked applicable and remain assigned to the same later semantic owner as the relevant path rows.

## Forge only preservation checklist

The 1,423 preservation rows include the existing Forge catalog and offer system, player shops, physical money and ATM paths, Bazaar and Auction House, direct escrow and WAL services, Refined Storage adapter, client screens and custom buttons, commands, packet codecs, migration code, and their tests and resources. These are not deleted or replaced because the donor does not contain them. The route inventory records every value affecting path separately, including direct `EscrowRuntimeService` and `WalletLedgerBackend` lanes that do not call `getProvider`.

## CodeGraph coverage and limits

CodeGraph was queried against the current Forge checkout before ledger closure. It resolved the authoritative monetary service classes and their callers, including `EscrowWalletService`, `InternalEconomyProvider`, `PlayerPaymentService`, `ServerShopOfferService`, `BulkSellService`, `PlayerShopLiveEscrowService`, `BazaarActionService`, and `AuctionActionService`. It reported 51 `getProvider` leads. A source call-site scan reports 58 `getProvider` call lines across 28 files and 39 direct ledger or WAL call lines. The difference is retained rather than normalized away because CodeGraph reports symbol-level leads while the source scan reports call sites. Direct WAL lanes are independently listed in the route inventory.

CodeGraph has no correctness oracle and reports no covering tests for several market and claim services. Those gaps are carried as later regression obligations, not treated as equivalence. The phase proves coverage and ownership only. It does not prove provider mutation, item conservation, restart behavior, or client rendering.

## Proof limit

This ledger proves that the pinned trees, path statuses, donor history, Forge-only surfaces, and later owners are complete. It does not prove any implementation, provider support, persistence atomicity, or runtime behavior. Those claims remain open until their named phase produces actual tests and dedicated server or silent laptop evidence.
